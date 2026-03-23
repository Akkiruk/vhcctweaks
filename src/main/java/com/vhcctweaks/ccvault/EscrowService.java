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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side escrow service for CCVault.
 *
 * Escrow flow:
 * 1. Script calls ccvault.escrow(amount, reason) →  tokens deducted from player, held in escrow
 * 2. On resolution: ccvault.resolveEscrow(escrowId, "host", reason) → tokens go to host (player lost)
 *                   ccvault.resolveEscrow(escrowId, "player", reason) → tokens return to player (push/refund)
 * 3. On timeout: auto-refund to source player (crash protection)
 *
 * Persistence: each escrow is a JSON file in the WAL directory so it survives server restarts.
 * A periodic tick checks for expired escrows and auto-refunds them.
 */
public class EscrowService {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static Path escrowDir;

    // Active escrows indexed by escrowId for fast lookup
    private static final Map<String, EscrowHold> activeEscrows = new ConcurrentHashMap<>();

    public static void init(Path dataDir) {
        escrowDir = dataDir.resolve("ccvault").resolve("escrow");
        try {
            Files.createDirectories(escrowDir);
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("CCVault: Failed to create escrow directory", e);
        }
    }

    /**
     * Recover escrow holds on server start. Auto-refund any expired ones.
     */
    public static void recover() {
        if (escrowDir == null || !Files.isDirectory(escrowDir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(escrowDir, "*.json")) {
            for (Path file : stream) {
                recoverHold(file);
            }
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("CCVault: Escrow recovery scan failed", e);
        }
    }

    private static void recoverHold(Path file) {
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            EscrowHold hold = GSON.fromJson(json, EscrowHold.class);
            if (hold == null) {
                Files.deleteIfExists(file);
                return;
            }

            // Check if expired
            if (System.currentTimeMillis() > hold.expiresAt) {
                VHCCTweaks.LOGGER.warn("CCVault: Escrow {} expired — auto-refunding {} tokens to {}",
                        hold.escrowId, hold.amount, hold.sourceUuid);
                UUID sourceUuid = UUID.fromString(hold.sourceUuid);
                if (DogBridge.add(sourceUuid, hold.amount)) {
                    TransactionLedger.logTransfer(
                            hold.escrowId + "-refund", sourceUuid, sourceUuid,
                            hold.amount, "escrow auto-refund: " + hold.reason,
                            hold.computerId, UUID.fromString(hold.playerUuid),
                            hold.hostUuid != null ? UUID.fromString(hold.hostUuid) : null);
                    BalanceNotifier.notifyEscrowRefund(sourceUuid, hold.amount, "escrow auto-refund (recovery)");
                    VHCCTweaks.LOGGER.info("CCVault: Escrow {} auto-refunded successfully", hold.escrowId);
                    Files.deleteIfExists(file);
                } else {
                    VHCCTweaks.LOGGER.error("CCVault: Escrow {} auto-refund FAILED — file kept for retry", hold.escrowId);
                }
            } else {
                // Still valid — reload into active map
                activeEscrows.put(hold.escrowId, hold);
                VHCCTweaks.LOGGER.info("CCVault: Reloaded active escrow {} ({} tokens, expires in {}s)",
                        hold.escrowId, hold.amount, (hold.expiresAt - System.currentTimeMillis()) / 1000);
            }
        } catch (Exception e) {
            VHCCTweaks.LOGGER.error("CCVault: Escrow recovery failed for file {}", file.getFileName(), e);
        }
    }

    /**
     * Create a new escrow hold. Deducts tokens from the source immediately.
     *
     * @param sourceUuid  UUID of the player providing funds (usually "player")
     * @param amount      positive token amount
     * @param reason      audit reason
     * @param computerId  computer initiating the escrow
     * @param playerUuid  interacting player's UUID
     * @param hostUuid    computer owner's UUID
     * @return EscrowResult with escrowId on success, or error on failure
     */
    public static EscrowResult create(UUID sourceUuid, long amount, String reason,
                                       int computerId, UUID playerUuid, UUID hostUuid) {
        if (amount <= 0) {
            return EscrowResult.fail("amount must be positive");
        }
        long maxAmount = ModConfig.CCVAULT_MAX_TRANSFER_AMOUNT.get();
        if (amount > maxAmount) {
            return EscrowResult.fail("amount exceeds maximum (" + maxAmount + ")");
        }

        // Check rate limits (escrows count toward transfer limits)
        String rateLimitMsg = RateLimiter.checkLimit(computerId, playerUuid);
        if (rateLimitMsg != null) {
            return EscrowResult.fail(rateLimitMsg);
        }

        // Check per-computer escrow cap
        int maxEscrows = ModConfig.CCVAULT_MAX_ESCROWS_PER_COMPUTER.get();
        long activeCount = activeEscrows.values().stream()
                .filter(h -> h.computerId == computerId)
                .count();
        if (activeCount >= maxEscrows) {
            return EscrowResult.fail("too many active escrows on this computer (max " + maxEscrows + ")");
        }

        if (!DogBridge.isAvailable()) {
            return EscrowResult.fail("economy system not available");
        }

        // Check source balance
        long sourceBalance = DogBridge.getBalance(sourceUuid);
        if (sourceBalance < 0) {
            return EscrowResult.fail("could not read source balance");
        }
        if (sourceBalance < amount) {
            return EscrowResult.fail("insufficient balance");
        }

        String escrowId = generateEscrowId();
        long timeoutMs = ModConfig.CCVAULT_ESCROW_TIMEOUT_SECONDS.get() * 1000L;
        long expiresAt = System.currentTimeMillis() + timeoutMs;

        // Write escrow to disk BEFORE deducting (so crash before debit = no hold file = safe)
        EscrowHold hold = new EscrowHold(escrowId, sourceUuid.toString(), amount, reason,
                computerId, playerUuid.toString(),
                hostUuid != null ? hostUuid.toString() : null,
                expiresAt, "HELD");

        if (!writeHold(hold)) {
            return EscrowResult.fail("internal error: could not write escrow hold");
        }

        // Deduct from source
        if (!DogBridge.remove(sourceUuid, amount)) {
            deleteHold(escrowId);
            return EscrowResult.fail("debit failed");
        }

        // Record in active map
        activeEscrows.put(escrowId, hold);
        RateLimiter.recordTransfer(computerId, playerUuid);

        // Notify player of escrow hold
        BalanceNotifier.notifyEscrowHold(sourceUuid, amount, reason);

        VHCCTweaks.LOGGER.info("CCVault: Escrow {} created — {} tokens held from {} (computer {}, timeout {}s)",
                escrowId, amount, sourceUuid, computerId, ModConfig.CCVAULT_ESCROW_TIMEOUT_SECONDS.get());

        return EscrowResult.success(escrowId);
    }

    /**
     * Resolve an escrow hold. Sends the held tokens to the specified recipient.
     *
     * @param escrowId    the escrow to resolve
     * @param recipientUuid UUID to receive the held tokens
     * @param reason      audit reason for resolution
     * @param computerId  must match the computer that created the escrow
     * @param playerUuid  interacting player UUID (for ledger)
     * @return EscrowResult with the generated txId, or error
     */
    public static EscrowResult resolve(String escrowId, UUID recipientUuid, String reason,
                                        int computerId, UUID playerUuid) {
        // Atomic claim: remove returns null if another thread already claimed this escrow
        EscrowHold hold = activeEscrows.remove(escrowId);
        if (hold == null) {
            return EscrowResult.fail("escrow not found or already resolved");
        }

        // Security: only the computer that created the escrow can resolve it
        if (hold.computerId != computerId) {
            activeEscrows.put(escrowId, hold); // put back — wrong computer
            return EscrowResult.fail("escrow belongs to a different computer");
        }

        // Check if expired
        if (System.currentTimeMillis() > hold.expiresAt) {
            // Auto-refund instead of resolving (escrow already removed from map)
            UUID sourceUuid = UUID.fromString(hold.sourceUuid);
            DogBridge.add(sourceUuid, hold.amount);
            deleteHold(escrowId);
            BalanceNotifier.notifyEscrowRefund(sourceUuid, hold.amount, "escrow expired");
            return EscrowResult.fail("escrow expired — tokens auto-refunded to source");
        }

        if (!DogBridge.isAvailable()) {
            activeEscrows.put(escrowId, hold); // put back — can't process now
            return EscrowResult.fail("economy system not available");
        }

        // Credit the recipient
        if (!DogBridge.add(recipientUuid, hold.amount)) {
            activeEscrows.put(escrowId, hold); // put back — credit failed
            VHCCTweaks.LOGGER.error("CCVault: Escrow {} resolve — credit to {} FAILED. Hold preserved.", escrowId, recipientUuid);
            return EscrowResult.fail("credit failed — escrow preserved, contact admin");
        }

        // Success — clean up (escrow already removed from map above)
        deleteHold(escrowId);

        // Log the resolution to ledger
        String txId = escrowId + "-resolve";
        UUID sourceUuid = UUID.fromString(hold.sourceUuid);
        UUID hostUuid = hold.hostUuid != null ? UUID.fromString(hold.hostUuid) : null;
        TransactionLedger.logTransfer(txId, sourceUuid, recipientUuid, hold.amount,
                "escrow resolve: " + reason, computerId, playerUuid, hostUuid);

        // Notify the recipient
        BalanceNotifier.notifyCredit(recipientUuid, hold.amount, reason);

        VHCCTweaks.LOGGER.info("CCVault: Escrow {} resolved — {} tokens to {}", escrowId, hold.amount, recipientUuid);
        return EscrowResult.success(txId);
    }

    /**
     * Cancel an escrow and refund to source. Can only be called by the originating computer.
     */
    public static EscrowResult cancel(String escrowId, String reason, int computerId, UUID playerUuid) {
        // Atomic claim: remove returns null if another thread already claimed this escrow
        EscrowHold hold = activeEscrows.remove(escrowId);
        if (hold == null) {
            return EscrowResult.fail("escrow not found or already resolved");
        }
        if (hold.computerId != computerId) {
            activeEscrows.put(escrowId, hold); // put back — wrong computer
            return EscrowResult.fail("escrow belongs to a different computer");
        }

        UUID sourceUuid = UUID.fromString(hold.sourceUuid);
        if (!DogBridge.add(sourceUuid, hold.amount)) {
            activeEscrows.put(escrowId, hold); // put back — refund failed
            VHCCTweaks.LOGGER.error("CCVault: Escrow {} cancel — refund FAILED. Hold preserved.", escrowId);
            return EscrowResult.fail("refund failed — escrow preserved, contact admin");
        }

        // Success — clean up (escrow already removed from map above)
        deleteHold(escrowId);

        String txId = escrowId + "-cancel";
        UUID hostUuid = hold.hostUuid != null ? UUID.fromString(hold.hostUuid) : null;
        TransactionLedger.logTransfer(txId, sourceUuid, sourceUuid, hold.amount,
                "escrow cancel: " + reason, computerId, playerUuid, hostUuid);

        // Notify player of refund
        BalanceNotifier.notifyEscrowRefund(sourceUuid, hold.amount, reason);

        VHCCTweaks.LOGGER.info("CCVault: Escrow {} cancelled — {} tokens refunded to {}", escrowId, hold.amount, sourceUuid);
        return EscrowResult.success(txId);
    }

    /**
     * Get info about an active escrow (for scripts to check status).
     */
    public static EscrowHold getHold(String escrowId) {
        return activeEscrows.get(escrowId);
    }

    /**
     * Called periodically (e.g. every 30s from a server tick handler) to auto-refund expired escrows.
     */
    public static void tickExpired() {
        long now = System.currentTimeMillis();
        for (var entry : activeEscrows.entrySet()) {
            EscrowHold hold = entry.getValue();
            if (now > hold.expiresAt) {
                String escrowId = entry.getKey();
                // Atomic claim: only process if we successfully remove it
                // (resolve/cancel on another thread may have already claimed it)
                if (activeEscrows.remove(escrowId, hold)) {
                    VHCCTweaks.LOGGER.warn("CCVault: Escrow {} expired — auto-refunding {} tokens", escrowId, hold.amount);
                    UUID sourceUuid = UUID.fromString(hold.sourceUuid);
                    if (DogBridge.add(sourceUuid, hold.amount)) {
                        deleteHold(escrowId);
                        UUID playerUuid = UUID.fromString(hold.playerUuid);
                        UUID hostUuid = hold.hostUuid != null ? UUID.fromString(hold.hostUuid) : null;
                        TransactionLedger.logTransfer(
                                escrowId + "-expired", sourceUuid, sourceUuid, hold.amount,
                                "escrow expired: " + hold.reason, hold.computerId, playerUuid, hostUuid);
                        BalanceNotifier.notifyEscrowRefund(sourceUuid, hold.amount, "escrow expired");
                        VHCCTweaks.LOGGER.info("CCVault: Escrow {} auto-refund complete", escrowId);
                    } else {
                        // Put it back so we can retry next tick
                        activeEscrows.put(escrowId, hold);
                        VHCCTweaks.LOGGER.error("CCVault: Escrow {} auto-refund FAILED — will retry next tick", escrowId);
                    }
                }
            }
        }
    }

    // ---- File operations ----

    private static boolean writeHold(EscrowHold hold) {
        if (escrowDir == null) return false;
        try {
            Path file = escrowDir.resolve(hold.escrowId + ".json");
            String json = GSON.toJson(hold);
            Files.writeString(file, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("CCVault: Failed to write escrow hold {}", hold.escrowId, e);
            return false;
        }
    }

    private static void deleteHold(String escrowId) {
        if (escrowDir == null) return;
        try {
            Files.deleteIfExists(escrowDir.resolve(escrowId + ".json"));
        } catch (IOException e) {
            VHCCTweaks.LOGGER.warn("CCVault: Failed to delete escrow hold {} (harmless)", escrowId, e);
        }
    }

    private static String generateEscrowId() {
        byte[] randomBytes = new byte[6];
        SECURE_RANDOM.nextBytes(randomBytes);
        StringBuilder hex = new StringBuilder(12);
        for (byte b : randomBytes) {
            hex.append(String.format("%02x", b & 0xFF));
        }
        return "esc-" + Long.toHexString(System.currentTimeMillis()) + "-" + hex;
    }

    // ---- Data types ----

    public static class EscrowHold {
        public String escrowId;
        public String sourceUuid;
        public long amount;
        public String reason;
        public int computerId;
        public String playerUuid;
        public String hostUuid;
        public long expiresAt;
        public String status; // HELD

        public EscrowHold() {} // For Gson

        public EscrowHold(String escrowId, String sourceUuid, long amount, String reason,
                          int computerId, String playerUuid, String hostUuid,
                          long expiresAt, String status) {
            this.escrowId = escrowId;
            this.sourceUuid = sourceUuid;
            this.amount = amount;
            this.reason = reason;
            this.computerId = computerId;
            this.playerUuid = playerUuid;
            this.hostUuid = hostUuid;
            this.expiresAt = expiresAt;
            this.status = status;
        }
    }

    public record EscrowResult(boolean success, String id, String error) {
        static EscrowResult success(String id) {
            return new EscrowResult(true, id, null);
        }
        static EscrowResult fail(String error) {
            return new EscrowResult(false, null, error);
        }
    }
}
