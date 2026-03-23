package com.vhcctweaks.ccvault;

import com.vhcctweaks.config.ModConfig;
import com.vhcctweaks.handler.ComputerInteractionTracker;
import com.vhcctweaks.handler.ComputerPlacementTracker;
import dan200.computercraft.api.lua.ILuaAPI;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CCVault Lua API exposed as "ccvault" to all CC:Tweaked computers.
 *
 * Security model:
 * - Scripts can only reference "player" (interacting player) or "host" (computer owner).
 * - No raw UUID targeting. No add/remove/set. Only balanced transfers.
 * - Requires per-session authentication via clickable chat nonce.
 *
 * Lua usage:
 *   ccvault.requestAuth()                           -- sends auth prompt to player
 *   ccvault.isAuthenticated()                       -- check if session is active
 *   ccvault.getBalance("player")                    -- interacting player's balance
 *   ccvault.getBalance("host")                      -- computer owner's balance
 *   ccvault.transfer("player", "host", 100, "shop") -- balanced transfer
 *   ccvault.getPlayerName()                         -- interacting player's name
 *   ccvault.getHostName()                           -- computer owner's name
 *   ccvault.isAvailable()                           -- is the economy system loaded?
 */
public class CCVaultAPI implements ILuaAPI {

    private static final int MAX_REASON_LENGTH = 64;

    private final int computerId;

    public CCVaultAPI(int computerId) {
        this.computerId = computerId;
    }

    @Override
    public String[] getNames() {
        return new String[]{"ccvault"};
    }

    // ===== Availability =====

    /**
     * Check if the CCVault economy system is available on this server.
     * Lua: if ccvault.isAvailable() then ... end
     */
    @LuaFunction
    public final boolean isAvailable() {
        return DogBridge.isAvailable();
    }

    // ===== Authentication =====

    /**
     * Request authentication for the current interacting player.
     * Sends a clickable chat message to the player. They must click [APPROVE].
     * Auth is per-session (clears on disconnect).
     *
     * Lua: local ok, err = ccvault.requestAuth()
     * Returns: true if sent, or nil + error message
     */
    @LuaFunction
    public final Object[] requestAuth() throws Exception {
        ServerPlayer player = getInteractingPlayerOrThrow();
        String nonce = SessionAuthManager.requestAuth(player, computerId);
        if (nonce != null) {
            return new Object[]{true};
        } else {
            // Already has a pending request — tell the script so it can wait
            return new Object[]{null, "auth request already pending — check your chat"};
        }
    }

    /**
     * Check if the current interacting player has authenticated this session.
     * Lua: if ccvault.isAuthenticated() then ... end
     */
    @LuaFunction
    public final boolean isAuthenticated() {
        ServerPlayer player = ComputerInteractionTracker.getPlayer(computerId);
        if (player == null) return false;
        return SessionAuthManager.isAuthenticated(player.getUUID(), computerId);
    }

    // ===== Balance =====

    /**
     * Get the token balance for "player" (interacting player) or "host" (computer owner).
     * Requires authentication.
     *
     * Lua: local bal, err = ccvault.getBalance("player")
     *      local bal, err = ccvault.getBalance("host")
     */
    @LuaFunction
    public final Object[] getBalance(String target) throws Exception {
        requireAuth();
        UUID uuid = resolveTarget(target);
        long balance = DogBridge.getBalance(uuid);
        if (balance < 0) {
            return new Object[]{null, "could not read balance"};
        }
        return new Object[]{balance};
    }

    // ===== Transfer =====

    /**
     * Execute a balanced transfer between "player" and "host".
     * Requires authentication. Amount must be positive.
     *
     * Lua: local result, err = ccvault.transfer("player", "host", 100, "shop purchase")
     *      if result then print("TX: " .. result.txId) end
     *
     * @param from   "player" or "host"
     * @param to     "player" or "host"
     * @param amount positive integer (vault tokens)
     * @param reason description string (max 64 chars, logged)
     */
    @LuaFunction
    public final Object[] transfer(String from, String to, long amount, String reason) throws Exception {
        requireAuth();

        if (from == null || to == null) {
            return new Object[]{null, "from and to are required"};
        }
        if (from.equals(to)) {
            return new Object[]{null, "cannot transfer to self"};
        }
        if (amount <= 0) {
            return new Object[]{null, "amount must be positive"};
        }
        if (reason == null || reason.isBlank()) {
            return new Object[]{null, "reason is required"};
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            return new Object[]{null, "reason too long (max " + MAX_REASON_LENGTH + " chars)"};
        }

        UUID fromUuid = resolveTarget(from);
        UUID toUuid = resolveTarget(to);

        if (fromUuid.equals(toUuid)) {
            return new Object[]{null, "cannot transfer to yourself — player and host are the same account"};
        }

        UUID playerUuid = getInteractingPlayerOrThrow().getUUID();
        UUID hostUuid = ComputerPlacementTracker.getOwner(computerId);

        TransferService.TransferResult result = TransferService.execute(
                fromUuid, toUuid, amount, reason, computerId, playerUuid, hostUuid);

        if (result.success()) {
            Map<String, Object> response = new HashMap<>();
            response.put("txId", result.txId());
            response.put("success", true);
            return new Object[]{response};
        } else {
            return new Object[]{null, result.error()};
        }
    }

    // ===== Info =====

    /**
     * Get the interacting player's username, or nil if nobody has interacted recently.
     * Lua: local name = ccvault.getPlayerName()
     */
    @LuaFunction
    public final Object[] getPlayerName() {
        ServerPlayer player = ComputerInteractionTracker.getPlayer(computerId);
        if (player == null) {
            return new Object[]{null};
        }
        return new Object[]{player.getName().getString()};
    }

    /**
     * Get the computer owner's username (the player who placed this computer).
     * Returns nil if the owner is unknown or offline.
     * Lua: local name = ccvault.getHostName()
     */
    @LuaFunction
    public final Object[] getHostName() {
        UUID owner = ComputerPlacementTracker.getOwner(computerId);
        if (owner == null) {
            return new Object[]{null, "computer has no registered owner"};
        }
        // Try to get the player name from the server
        ServerPlayer player = getServerPlayerByUuid(owner);
        if (player != null) {
            return new Object[]{player.getName().getString()};
        }
        // Owner is offline — return UUID string as fallback
        return new Object[]{owner.toString()};
    }

    /**
     * Get this computer's ID (useful for display / revoke commands).
     * Lua: local id = ccvault.getComputerId()
     */
    @LuaFunction
    public final int getComputerId() {
        return computerId;
    }

    // ===== Session Info =====

    /**
     * Get information about the current CCVault session.
     * Includes player/host names, self-play detection, and rate limit budget.
     * Does not require authentication.
     *
     * Lua: local info = ccvault.getSessionInfo()
     *      if info.isSelfPlay then print("Test mode!") end
     *      print("Transfers left: " .. info.transfersRemaining)
     */
    @LuaFunction
    public final Object[] getSessionInfo() {
        Map<String, Object> info = new HashMap<>();

        info.put("computerId", computerId);

        // Player info
        ServerPlayer player = ComputerInteractionTracker.getPlayer(computerId);
        String playerName = player != null ? player.getName().getString() : null;
        info.put("playerName", playerName);

        // Host info
        UUID hostUuid = ComputerPlacementTracker.getOwner(computerId);
        String hostName = null;
        if (hostUuid != null) {
            ServerPlayer hostPlayer = getServerPlayerByUuid(hostUuid);
            hostName = hostPlayer != null ? hostPlayer.getName().getString() : hostUuid.toString();
        }
        info.put("hostName", hostName);

        // Self-play detection (server-authoritative)
        boolean isSelfPlay = false;
        if (player != null && hostUuid != null) {
            isSelfPlay = player.getUUID().equals(hostUuid);
        }
        info.put("isSelfPlay", isSelfPlay);

        // Auth status
        boolean authenticated = player != null && SessionAuthManager.isAuthenticated(player.getUUID(), computerId);
        info.put("authenticated", authenticated);

        // Rate limit budget
        if (player != null) {
            int terminalRemaining = RateLimiter.getRemainingTerminalTransfers(computerId);
            int playerRemaining = RateLimiter.getRemainingPlayerTransfers(player.getUUID());
            info.put("transfersRemaining", Math.min(terminalRemaining, playerRemaining));
            info.put("terminalTransfersRemaining", terminalRemaining);
            info.put("playerTransfersRemaining", playerRemaining);
        } else {
            info.put("transfersRemaining", 0);
            info.put("terminalTransfersRemaining", 0);
            info.put("playerTransfersRemaining", 0);
        }

        return new Object[]{info};
    }

    // ===== Self-Transfer (Test Mode) =====

    /**
     * Execute a self-transfer for testing when player and host are the same account.
     * The transfer goes through the full pipeline (WAL, ledger, rate limiter) but
     * is tagged as a test transfer. Net balance effect is zero (debit + credit same account).
     *
     * Requires authentication. Only works when player == host.
     *
     * Lua: local result, err = ccvault.transferSelf(100, "blackjack bet test")
     *      if result then print("TX: " .. result.txId .. " test=" .. tostring(result.testMode)) end
     */
    @LuaFunction
    public final Object[] transferSelf(long amount, String reason) throws Exception {
        requireAuth();

        if (amount <= 0) {
            return new Object[]{null, "amount must be positive"};
        }
        if (reason == null || reason.isBlank()) {
            return new Object[]{null, "reason is required"};
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            return new Object[]{null, "reason too long (max " + MAX_REASON_LENGTH + " chars)"};
        }

        ServerPlayer player = getInteractingPlayerOrThrow();
        UUID playerUuid = player.getUUID();
        UUID hostUuid = ComputerPlacementTracker.getOwner(computerId);

        if (hostUuid == null) {
            return new Object[]{null, "computer has no registered owner — place it to register"};
        }
        if (!playerUuid.equals(hostUuid)) {
            return new Object[]{null, "transferSelf is only for same-person sessions (player must be host)"};
        }

        // Full pipeline execution: debit + credit same UUID
        TransferService.TransferResult result = TransferService.execute(
                playerUuid, playerUuid, amount, "[test] " + reason, computerId, playerUuid, hostUuid);

        if (result.success()) {
            Map<String, Object> response = new HashMap<>();
            response.put("txId", result.txId());
            response.put("success", true);
            response.put("testMode", true);
            return new Object[]{response};
        } else {
            return new Object[]{null, result.error()};
        }
    }

    // ===== Transaction Verification =====

    /**
     * Verify whether a specific transaction was completed by looking it up in the ledger.
     * Only returns transactions where the current player was involved.
     * Requires authentication.
     *
     * Lua: local tx = ccvault.verifyTransaction("18f3a2b0-4c7e...")
     *      if tx then print("Confirmed: " .. tx.amount .. " at " .. tx.timestamp) end
     */
    @LuaFunction
    public final Object[] verifyTransaction(String txId) throws Exception {
        requireAuth();

        if (txId == null || txId.isBlank()) {
            return new Object[]{null, "txId is required"};
        }

        ServerPlayer player = getInteractingPlayerOrThrow();
        Map<String, Object> entry = TransactionLedger.findTransaction(txId, player.getUUID());
        if (entry == null) {
            return new Object[]{null};
        }
        return new Object[]{entry};
    }

    // ===== Transaction History =====

    /**
     * Get the most recent transactions involving the current player on this computer.
     * Returns up to `limit` entries (capped by server config). Requires authentication.
     *
     * Lua: local txs = ccvault.getTransactionHistory(10)
     *      for i, tx in ipairs(txs) do print(tx.txId, tx.amount, tx.reason) end
     */
    @LuaFunction
    public final Object[] getTransactionHistory(int limit) throws Exception {
        requireAuth();

        int maxResults = ModConfig.CCVAULT_MAX_HISTORY_RESULTS.get();
        if (limit <= 0) limit = 10;
        if (limit > maxResults) limit = maxResults;

        ServerPlayer player = getInteractingPlayerOrThrow();
        List<Map<String, Object>> history = TransactionLedger.getHistory(player.getUUID(), limit);

        // Convert to Lua-friendly indexed table (1-based array mapped by HashMap keys)
        Map<Integer, Object> luaTable = new HashMap<>();
        for (int i = 0; i < history.size(); i++) {
            luaTable.put(i + 1, history.get(i));
        }
        return new Object[]{luaTable};
    }

    // ===== Escrow =====

    /**
     * Create an escrow hold. Deducts tokens from "player" and holds them server-side.
     * The tokens are not given to the host until resolveEscrow() is called.
     * If the server crashes or the escrow times out, tokens auto-refund to the player.
     *
     * Requires authentication.
     *
     * Lua: local result, err = ccvault.escrow(100, "blackjack bet")
     *      if result then
     *        print("Escrow ID: " .. result.escrowId)
     *      end
     */
    @LuaFunction
    public final Object[] escrow(long amount, String reason) throws Exception {
        requireAuth();

        if (amount <= 0) {
            return new Object[]{null, "amount must be positive"};
        }
        if (reason == null || reason.isBlank()) {
            return new Object[]{null, "reason is required"};
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            return new Object[]{null, "reason too long (max " + MAX_REASON_LENGTH + " chars)"};
        }

        ServerPlayer player = getInteractingPlayerOrThrow();
        UUID playerUuid = player.getUUID();
        UUID hostUuid = ComputerPlacementTracker.getOwner(computerId);

        // For self-play, escrow from and to the same person — still useful for testing
        UUID sourceUuid = playerUuid;

        EscrowService.EscrowResult result = EscrowService.create(
                sourceUuid, amount, reason, computerId, playerUuid, hostUuid);

        if (result.success()) {
            Map<String, Object> response = new HashMap<>();
            response.put("escrowId", result.id());
            response.put("success", true);
            response.put("amount", amount);
            response.put("timeoutSeconds", ModConfig.CCVAULT_ESCROW_TIMEOUT_SECONDS.get());
            return new Object[]{response};
        } else {
            return new Object[]{null, result.error()};
        }
    }

    /**
     * Resolve an escrow hold by sending the held tokens to "player" or "host".
     *
     * Lua: local result, err = ccvault.resolveEscrow(escrowId, "host", "player lost bet")
     *      local result, err = ccvault.resolveEscrow(escrowId, "player", "push / refund")
     */
    @LuaFunction
    public final Object[] resolveEscrow(String escrowId, String recipient, String reason) throws Exception {
        requireAuth();

        if (escrowId == null || escrowId.isBlank()) {
            return new Object[]{null, "escrowId is required"};
        }
        if (recipient == null || recipient.isBlank()) {
            return new Object[]{null, "recipient is required"};
        }
        if (reason == null || reason.isBlank()) {
            return new Object[]{null, "reason is required"};
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            return new Object[]{null, "reason too long (max " + MAX_REASON_LENGTH + " chars)"};
        }

        UUID recipientUuid = resolveTarget(recipient);
        ServerPlayer player = getInteractingPlayerOrThrow();

        EscrowService.EscrowResult result = EscrowService.resolve(
                escrowId, recipientUuid, reason, computerId, player.getUUID());

        if (result.success()) {
            Map<String, Object> response = new HashMap<>();
            response.put("txId", result.id());
            response.put("success", true);
            return new Object[]{response};
        } else {
            return new Object[]{null, result.error()};
        }
    }

    /**
     * Cancel an escrow hold and return the tokens to the player.
     *
     * Lua: local result, err = ccvault.cancelEscrow(escrowId, "game cancelled")
     */
    @LuaFunction
    public final Object[] cancelEscrow(String escrowId, String reason) throws Exception {
        requireAuth();

        if (escrowId == null || escrowId.isBlank()) {
            return new Object[]{null, "escrowId is required"};
        }
        if (reason == null || reason.isBlank()) {
            return new Object[]{null, "reason is required"};
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            return new Object[]{null, "reason too long (max " + MAX_REASON_LENGTH + " chars)"};
        }

        ServerPlayer player = getInteractingPlayerOrThrow();

        EscrowService.EscrowResult result = EscrowService.cancel(
                escrowId, reason, computerId, player.getUUID());

        if (result.success()) {
            Map<String, Object> response = new HashMap<>();
            response.put("txId", result.id());
            response.put("success", true);
            return new Object[]{response};
        } else {
            return new Object[]{null, result.error()};
        }
    }

    /**
     * Get info about an active escrow hold.
     * Lua: local info = ccvault.getEscrowInfo(escrowId)
     *      if info then print(info.amount, info.timeRemaining) end
     */
    @LuaFunction
    public final Object[] getEscrowInfo(String escrowId) throws Exception {
        requireAuth();

        if (escrowId == null || escrowId.isBlank()) {
            return new Object[]{null, "escrowId is required"};
        }

        EscrowService.EscrowHold hold = EscrowService.getHold(escrowId);
        if (hold == null) {
            return new Object[]{null};
        }

        // Security: only show to the same computer
        if (hold.computerId != computerId) {
            return new Object[]{null, "escrow belongs to a different computer"};
        }

        Map<String, Object> info = new HashMap<>();
        info.put("escrowId", hold.escrowId);
        info.put("amount", hold.amount);
        info.put("reason", hold.reason);
        long remaining = Math.max(0, hold.expiresAt - System.currentTimeMillis());
        info.put("timeRemaining", remaining / 1000);
        info.put("status", hold.status);
        return new Object[]{info};
    }

    // ===== Internal helpers =====

    private void requireAuth() throws Exception {
        ServerPlayer player = getInteractingPlayerOrThrow();
        if (!SessionAuthManager.isAuthenticated(player.getUUID(), computerId)) {
            throw new Exception("not authenticated — call ccvault.requestAuth() first");
        }
    }

    private ServerPlayer getInteractingPlayerOrThrow() throws Exception {
        ServerPlayer player = ComputerInteractionTracker.getFreshPlayer(computerId);
        if (player == null) {
            throw new Exception("no player interacting with this computer (or interaction expired)");
        }
        return player;
    }

    private UUID resolveTarget(String target) throws Exception {
        return switch (target.toLowerCase()) {
            case "player" -> {
                ServerPlayer p = getInteractingPlayerOrThrow();
                yield p.getUUID();
            }
            case "host" -> {
                UUID owner = ComputerPlacementTracker.getOwner(computerId);
                if (owner == null) {
                    throw new Exception("computer has no registered owner — place it to register");
                }
                yield owner;
            }
            default -> throw new Exception("invalid target '" + target + "' — use 'player' or 'host'");
        };
    }

    private ServerPlayer getServerPlayerByUuid(UUID uuid) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.getPlayerList().getPlayer(uuid);
    }
}
