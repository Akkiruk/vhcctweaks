package com.vhcctweaks.ccvault;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vhcctweaks.VHCCTweaks;
import com.vhcctweaks.config.ModConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * Executes transfers through the ParadiseCore economy, which applies them atomically.
 *
 * Older versions debited and credited separately and kept a Write-Ahead Log (WAL) to finish a
 * transfer cut off between the two. New transfers write no intents, but recover() still runs at
 * server start so any intent left behind by those versions is finished:
 *   PENDING  → nothing happened yet, delete the file
 *   DEBITED  → debit happened but credit didn't, complete the credit
 *   CREDITED → credit already happened, just clean up the file
 */
public class TransferService {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static Path walDir;

    public static void init(Path dataDir) {
        walDir = dataDir.resolve("ccvault").resolve("wal");
        try {
            Files.createDirectories(walDir);
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("CCVault: Failed to create WAL directory", e);
        }
    }

    /**
     * Run crash recovery on server start.
     * Must be called before any transfers are processed.
     */
    public static void recover() {
        if (walDir == null || !Files.isDirectory(walDir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(walDir, "*.json")) {
            for (Path file : stream) {
                recoverIntent(file);
            }
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("CCVault: WAL recovery scan failed", e);
        }
    }

    private static void recoverIntent(Path file) {
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            TransferIntent intent = GSON.fromJson(json, TransferIntent.class);
            if (intent == null) {
                Files.deleteIfExists(file);
                return;
            }

            switch (intent.status) {
                case "PENDING":
                    // Debit never happened — safe to discard
                    VHCCTweaks.LOGGER.info("CCVault: WAL recovery — discarding PENDING tx {}", intent.txId);
                    Files.deleteIfExists(file);
                    break;

                case "DEBITED":
                    // Debit happened, credit didn't — complete the credit
                    VHCCTweaks.LOGGER.warn("CCVault: WAL recovery — completing DEBITED tx {} (crediting {} to {})",
                            intent.txId, intent.amount, intent.toUuid);
                    UUID toUuid = UUID.fromString(intent.toUuid);
                    if (EconomyBridge.add(toUuid, intent.amount)) {
                        intent.status = "CREDITED";
                        writeIntent(intent);
                        logTransferIfMissing(intent);
                        VHCCTweaks.LOGGER.info("CCVault: WAL recovery — tx {} completed successfully", intent.txId);
                        BalanceNotifier.notifyCredit(toUuid, intent.amount, "recovery: " + intent.reason);
                    } else {
                        // Economy not yet available (server still starting?) — leave file for next restart
                        VHCCTweaks.LOGGER.error("CCVault: WAL recovery — FAILED to credit tx {}! File kept for retry.", intent.txId);
                        return; // Don't delete the file
                    }
                    Files.deleteIfExists(file);
                    break;

                case "CREDITED":
                    // Credit already happened — ensure the ledger has a record, then clean up
                    logTransferIfMissing(intent);
                    VHCCTweaks.LOGGER.info("CCVault: WAL recovery — tx {} already CREDITED, cleaning up", intent.txId);
                    Files.deleteIfExists(file);
                    break;

                default:
                    VHCCTweaks.LOGGER.warn("CCVault: WAL recovery — unknown status '{}' for tx {}, removing", intent.status, intent.txId);
                    Files.deleteIfExists(file);
                    break;
            }
        } catch (Exception e) {
            VHCCTweaks.LOGGER.error("CCVault: WAL recovery failed for file {}", file.getFileName(), e);
        }
    }

    /**
     * Execute a balanced transfer: debit source, credit destination.
     * Supports same-UUID transfers for self-play test mode.
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

    // ---- WAL file operations ----

    private static boolean writeIntent(TransferIntent intent) {
        if (walDir == null) return false;
        try {
            Path file = walDir.resolve(intent.txId + ".json");
            String json = GSON.toJson(intent);
            Files.writeString(file, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("CCVault: Failed to write WAL intent {}", intent.txId, e);
            return false;
        }
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

    // ---- Data types ----

    private static class TransferIntent {
        String txId;
        String fromUuid;
        String toUuid;
        long amount;
        String reason;
        String status; // PENDING, DEBITED, CREDITED
        int computerId;
        String playerUuid;
        String hostUuid;

        TransferIntent(String txId, String fromUuid, String toUuid, long amount, String reason,
                       String status, int computerId, String playerUuid, String hostUuid) {
            this.txId = txId;
            this.fromUuid = fromUuid;
            this.toUuid = toUuid;
            this.amount = amount;
            this.reason = reason;
            this.status = status;
            this.computerId = computerId;
            this.playerUuid = playerUuid;
            this.hostUuid = hostUuid;
        }
    }

    private static void logTransferIfMissing(TransferIntent intent) {
        if (TransactionLedger.hasTransaction(intent.txId)) {
            return;
        }

        try {
            TransactionLedger.logTransfer(
                    intent.txId,
                    UUID.fromString(intent.fromUuid),
                    UUID.fromString(intent.toUuid),
                    intent.amount,
                    intent.reason,
                    intent.computerId,
                    UUID.fromString(intent.playerUuid),
                    intent.hostUuid != null ? UUID.fromString(intent.hostUuid) : null
            );
        } catch (IllegalArgumentException e) {
            VHCCTweaks.LOGGER.error("CCVault: Could not reconstruct ledger entry for tx {} during recovery", intent.txId, e);
        }
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
