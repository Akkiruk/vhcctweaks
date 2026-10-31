package com.vhcctweaks.ccvault;

import com.vhcctweaks.VHCCTweaks;
import com.vhcctweaks.config.ModConfig;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * Executes transfers through the ParadiseCore economy, which applies them atomically.
 */
public class TransferService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Versions before 2.2.34 debited and credited separately and left intent files to finish a transfer
     * cut off between the two. 2.2.34 finished them at startup. Anything still there was never finished,
     * so it is kept and reported for an admin to settle by hand.
     */
    public static void init(Path dataDir) {
        Path walDir = dataDir.resolve("ccvault").resolve("wal");
        if (!Files.isDirectory(walDir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(walDir, "*.json")) {
            for (Path file : stream) {
                VHCCTweaks.LOGGER.error("CCVault: unfinished transfer from an old version needs settling by hand: {}", file);
            }
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("CCVault: Could not check {} for old transfers", walDir, e);
        }
    }

    /**
     * Execute a balanced transfer between two different accounts.
     *
     * @return TransferResult with success/failure and details
     */
    public static TransferResult execute(UUID fromUuid, UUID toUuid, long amount,
                                          String reason, int computerId,
                                          UUID playerUuid, UUID hostUuid) {
        // Validate amount
        if (amount <= 0) {
            return TransferResult.fail("amount must be positive");
        }
        long maxAmount = ModConfig.CCVAULT_MAX_TRANSFER_AMOUNT.get();
        if (amount > maxAmount) {
            return TransferResult.fail("amount exceeds maximum (" + maxAmount + ")");
        }

        // Check the economy is up
        if (!EconomyBridge.isAvailable()) {
            return TransferResult.fail("economy system not available");
        }

        // Check source has enough balance
        long sourceBalance = EconomyBridge.getBalance(fromUuid);
        if (sourceBalance < 0) {
            return TransferResult.fail("could not read source balance");
        }
        if (sourceBalance < amount) {
            return TransferResult.fail("insufficient balance");
        }

        String txId = generateTxId();

        // The economy moves both balances in one transaction, so there is no half-done state to recover from
        if (!EconomyBridge.transfer(fromUuid, toUuid, amount)) {
            TransactionLedger.logRejection(txId, fromUuid, toUuid, amount, reason, computerId,
                    playerUuid, "economy refused the transfer");
            return TransferResult.fail("transfer failed");
        }

        TransactionLedger.logTransfer(txId, fromUuid, toUuid, amount, reason, computerId,
                playerUuid, hostUuid);

        BalanceNotifier.notifyDebit(fromUuid, amount, reason);
        BalanceNotifier.notifyCredit(toUuid, amount, reason);

        return TransferResult.success(txId);
    }

    private static String generateTxId() {
        // Timestamp prefix for sortability + secure random suffix for uniqueness
        byte[] randomBytes = new byte[6];
        SECURE_RANDOM.nextBytes(randomBytes);
        StringBuilder hex = new StringBuilder(12);
        for (byte b : randomBytes) {
            hex.append(String.format("%02x", b & 0xFF));
        }
        return Long.toHexString(System.currentTimeMillis()) + "-" + hex;
    }

    public record TransferResult(boolean success, String txId, String error) {
        static TransferResult success(String txId) {
            return new TransferResult(true, txId, null);
        }
        static TransferResult fail(String error) {
            return new TransferResult(false, null, error);
        }
    }
}
