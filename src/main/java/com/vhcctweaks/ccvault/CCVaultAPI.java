package com.vhcctweaks.ccvault;

import com.mojang.authlib.GameProfile;
import com.vhcctweaks.config.ModConfig;
import com.vhcctweaks.handler.ComputerInteractionTracker;
import com.vhcctweaks.handler.ComputerLock;
import com.vhcctweaks.handler.ComputerPlacementTracker;
import com.vhcctweaks.link.ComputerIndex;
import com.vhcctweaks.remote.RemoteControl;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.api.lua.ILuaAPI;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * CCVault Lua API exposed as "ccvault" to all CC:Tweaked computers.
 *
 * Security model:
 * - Scripts can only reference "player" (interacting player) or "host" (computer owner).
 * - No raw UUID targeting. Whoever places a computer is its host; becoming host any other way takes a chat click.
 * - Requires server-side authentication via clickable chat nonce.
 *
 * Lua usage:
 *   ccvault.requestAuth()                           -- sends auth prompt to player
 *   ccvault.isAuthenticated()                       -- check if session is active
 *   ccvault.getBalance("player")                    -- interacting player's balance
 *   ccvault.getBalance("host")                      -- computer owner's balance
 *   ccvault.getBalance("player:Name")               -- a named player at this table
 *   ccvault.getPlayerBalance("SomePlayer")          -- resolve a player name/UUID and read their balance
 *   ccvault.transfer("player", "host", 100, "shop") -- balanced transfer
 *   ccvault.getPlayerName()                         -- interacting player's name
 *   ccvault.getHostName()                           -- computer owner's name
 *   ccvault.transfer("player:Name", "host", 100, "ante") -- charge one named player at this table
 *   ccvault.claimHost()                             -- asks the interacting player to confirm in chat
 *   ccvault.forgetHost()                            -- this computer has no host until someone claims it
 *   ccvault.setTerminalLock(true)                   -- owner only: nobody else can open or break this computer
 *   ccvault.isAvailable()                           -- is the economy system loaded?
 */
public class CCVaultAPI implements ILuaAPI {

    private static final int MAX_REASON_LENGTH = 64;
    private static final String NAMED_PLAYER_PREFIX = "player:";
    private static final String OWNER_REGISTRATION_MESSAGE =
            "computer has no registered owner - right-click it and claim it in chat, or call ccvault.claimHost()";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final PlayerLookup PLAYER_LOOKUP = new PlayerLookup(CCVaultAPI::lookUpProfile,
            Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "VHCC-PlayerLookup");
                thread.setDaemon(true);
                return thread;
            }));

    private final int computerId;

    public CCVaultAPI(int computerId) {
        this.computerId = computerId;
    }

    @Override
    public String[] getNames() {
        return new String[]{"ccvault"};
    }

    /** A reboot ends remote control of this computer. */
    @Override
    public void startup() {
        RemoteControl.clearControlled(computerId);
        AllowanceManager.restartIds(computerId);
    }

    /** Allowance requests still waiting die with the program that asked; allowances already granted stay. */
    @Override
    public void shutdown() {
        SessionAuthManager.dropPendingAllowances(computerId);
    }

    // ===== Availability =====

    /**
     * Check if the CCVault economy system is available on this server.
     * Lua: if ccvault.isAvailable() then ... end
     */
    @LuaFunction
    public final boolean isAvailable() {
        return EconomyBridge.isAvailable();
    }

    // ===== Authentication =====

    /**
     * Request authentication for the current interacting player.
     * Sends a clickable chat message to the player. They must click [APPROVE].
     * Auth is tracked server-side for the player/computer session.
     *
     * Lua: local ok, err = ccvault.requestAuth()
     * Returns: true if sent or already authenticated, or nil + error message
     */
    @LuaFunction
    public final Object[] requestAuth() throws LuaException {
        ServerPlayer player = getInteractingPlayerOrThrow();
        SessionAuthManager.RequestAuthResult result = SessionAuthManager.requestAuth(player, computerId);
        return switch (result) {
            case SENT -> new Object[]{true};
            case ALREADY_AUTHENTICATED -> new Object[]{true, "already authenticated"};
            case PENDING -> new Object[]{null, "auth request already pending — check your chat"};
        };
    }

    /**
     * Check if the current interacting player has authenticated this computer.
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
    public final Object[] getBalance(String target) throws LuaException {
        authorize(target);
        UUID uuid = resolveTarget(target);
        long balance = EconomyBridge.getBalance(uuid);
        if (balance < 0) {
            return new Object[]{null, "could not read balance"};
        }
        return new Object[]{balance};
    }

    /**
        * Get the current token balance for a player resolved by username or UUID string.
     * Read-only: does not require authentication and cannot change balances.
     * The first call for a player who is offline can answer "try again" while the name is looked up.
     *
     * Lua: local bal, err = ccvault.getPlayerBalance("SomePlayer")
     */
    @LuaFunction
    public final Object[] getPlayerBalance(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return new Object[]{null, "playerName is required"};
        }

        String normalizedPlayerName = playerName.trim();
        PlayerLookup.Result lookup = resolvePlayer(normalizedPlayerName);
        if (lookup.status() == PlayerLookup.Status.PENDING) {
            return new Object[]{null, "looking up player '" + normalizedPlayerName + "' - try again"};
        }
        if (lookup.status() == PlayerLookup.Status.UNKNOWN) {
            return new Object[]{null, "unknown player or UUID '" + normalizedPlayerName + "'"};
        }

        long balance = EconomyBridge.getBalance(lookup.uuid());
        if (balance < 0) {
            return new Object[]{null, "could not read balance"};
        }
        return new Object[]{balance};
    }

    /**
     * A player's Vault Hunters level, online or offline, resolved like getPlayerBalance. Read-only, no auth.
     * Returns the level, or nil and "unknown player" or "no vault data".
     *
     * Lua: local level, err = ccvault.getPlayerVaultLevel("SomePlayer")
     */
    @LuaFunction(mainThread = true)
    public final Object[] getPlayerVaultLevel(String playerName) {
        if (playerName == null || playerName.isBlank()) return new Object[]{null, VaultLevelReply.UNKNOWN_PLAYER};
        String name = playerName.trim();
        PlayerLookup.Result lookup = resolvePlayer(name);
        var server = ServerLifecycleHooks.getCurrentServer();
        Integer level = lookup.status() == PlayerLookup.Status.FOUND && server != null ? VaultLevels.read(server, lookup.uuid()) : null;
        return VaultLevelReply.answer(lookup.status(), name, level);
    }

    /**
     * What an online player is wearing and holding, with getItemDetail's data for each item, Curios slots included.
     * Read-only, no auth, touch or range. Returns the table, or nil and "unknown player" or "player offline".
     *
     * Lua: local gear, err = ccvault.getPlayerEquipment("SomePlayer")
     */
    @LuaFunction(mainThread = true)
    public final Object[] getPlayerEquipment(String playerName) {
        if (playerName == null || playerName.isBlank()) return new Object[]{null, VaultLevelReply.UNKNOWN_PLAYER};
        String name = playerName.trim();
        PlayerLookup.Result lookup = resolvePlayer(name);
        if (lookup.status() == PlayerLookup.Status.PENDING) {
            return new Object[]{null, "looking up player '" + name + "' - try again"};
        }
        if (lookup.status() == PlayerLookup.Status.UNKNOWN) return new Object[]{null, VaultLevelReply.UNKNOWN_PLAYER};
        ServerPlayer player = getServerPlayerByUuid(lookup.uuid());
        if (player == null) return new Object[]{null, "player offline"};
        return new Object[]{PlayerEquipment.read(player)};
    }

    // ===== Transfer =====

    /**
     * Execute a balanced transfer between "player" and "host".
     * Requires authentication. Amount must be positive.
     *
     * When player and host are the same account the transfer is refused: use transferSelf.
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
    public final Object[] transfer(String from, String to, long amount, String reason) throws LuaException {
        if (from == null || to == null) {
            return new Object[]{null, "from and to are required"};
        }
        authorize(from, to);

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

        // The host paying a named player costs that player nothing, so it needs no fresh touch from them
        UUID toUuid = from.equalsIgnoreCase("host") && isNamed(to) ? resolveNamedPayee(to) : resolveTarget(to);

        // A named player who gave this computer an allowance can be charged by it with no fresh touch, up to what is
        // left. Reserved here, before anything else can fail, and settled after the transfer.
        UUID allowancePlayer = isNamed(from) && to.equalsIgnoreCase("host") ? reserveAllowance(from, amount) : null;
        UUID fromUuid = allowancePlayer != null ? allowancePlayer : resolveTarget(from);

        if (fromUuid.equals(toUuid)) {
            if (allowancePlayer != null) AllowanceManager.settle(computerId, allowancePlayer, amount, false);
            return new Object[]{null, "cannot transfer to yourself — player and host are the same account"};
        }

        UUID playerUuid = isNamed(from) ? fromUuid : isNamed(to) ? toUuid : getInteractingPlayerOrThrow().getUUID();
        UUID hostUuid = ComputerPlacementTracker.getOwner(computerId);

        if (RemoteControl.blocksTransfer(computerId, playerUuid)) {
            if (allowancePlayer != null) AllowanceManager.settle(computerId, allowancePlayer, amount, false);
            return new Object[]{null, "this computer was remotely controlled - reboot it before moving tokens"};
        }

        TransferService.TransferResult result;
        boolean charged = false;
        try {
            result = TransferService.execute(fromUuid, toUuid, amount, reason, computerId, playerUuid, hostUuid);
            charged = result.success();
        } finally {
            // A refused or crashed transfer gives the reserved amount back; a used-up allowance ends
            if (allowancePlayer != null) AllowanceManager.settle(computerId, allowancePlayer, amount, charged);
        }

        if (result.success()) {
            Map<String, Object> response = new HashMap<>();
            response.put("txId", result.txId());
            response.put("success", true);
            return new Object[]{response};
        } else {
            return new Object[]{null, result.error()};
        }
    }

    // ===== Spending allowance =====

    /**
     * Ask a player, in one chat prompt, to let this computer charge them up to maxTokens over the next minutes with
     * no fresh touch. Returns a request id at once. The answer comes as ccvault_allowance(requestId, playerName,
     * approved): true when they click, false when the prompt expires or they leave first.
     *
     * Lua: local id = ccvault.requestAllowance("Steve", 500, 60)
     */
    @LuaFunction(mainThread = true)
    public final Object[] requestAllowance(String playerName, long maxTokens, int minutes) {
        if (playerName == null || playerName.isBlank()) return new Object[]{null, "player is required"};
        if (maxTokens <= 0) return new Object[]{null, "maxTokens must be positive"};
        int maxMinutes = ModConfig.CCVAULT_MAX_ALLOWANCE_MINUTES.get();
        if (minutes < 1 || minutes > maxMinutes) return new Object[]{null, "minutes must be between 1 and " + maxMinutes};

        ServerPlayer player = onlinePlayer(playerName.trim());
        if (player == null) return new Object[]{null, "player '" + playerName.trim() + "' is not online"};
        UUID owner = ComputerPlacementTracker.getOwner(computerId);
        if (owner == null) return new Object[]{null, OWNER_REGISTRATION_MESSAGE};

        int requestId = SessionAuthManager.requestAllowance(player, computerId, computerName(), nameOf(owner), maxTokens, minutes);
        if (requestId < 0) {
            return new Object[]{null, "an allowance request to '" + player.getGameProfile().getName() + "' is already waiting"};
        }
        return new Object[]{requestId};
    }

    /**
     * What is left of a player's allowance here: remaining tokens and seconds left, or nil if there is none.
     * Lua: local remaining, secondsLeft = ccvault.getAllowance("Steve")
     */
    @LuaFunction
    public final Object[] getAllowance(String playerName) {
        ServerPlayer player = playerName == null ? null : onlinePlayer(playerName.trim());
        long[] allowance = player == null ? null : AllowanceManager.get(computerId, player.getUUID(), System.currentTimeMillis());
        return allowance == null ? new Object[]{null} : new Object[]{allowance[0], allowance[1]};
    }

    /**
     * End a player's allowance here. Returns whether there was one.
     * Lua: ccvault.endAllowance("Steve")
     */
    @LuaFunction
    public final boolean endAllowance(String playerName) {
        ServerPlayer player = playerName == null ? null : onlinePlayer(playerName.trim());
        return player != null && AllowanceManager.end(computerId, player.getUUID());
    }

    /** The label players see in an allowance prompt. */
    private String computerName() {
        ServerComputer computer = ComputerIndex.get(computerId);
        String label = computer == null ? null : computer.getLabel();
        return label != null && !label.isBlank() ? label : "Computer #" + computerId;
    }

    private static String nameOf(UUID uuid) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return uuid.toString();
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().getName();
        return server.getProfileCache().get(uuid).map(GameProfile::getName).orElse(uuid.toString());
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
     * Get the computer owner's username. Returns the owner's UUID if they are offline, and nil if there is no owner.
     * Lua: local name = ccvault.getHostName()
     */
    @LuaFunction
    public final Object[] getHostName() {
        UUID owner = ComputerPlacementTracker.getOwner(computerId);
        if (owner == null) {
            return new Object[]{null, OWNER_REGISTRATION_MESSAGE};
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
     * Ask the current interacting player to become this computer's host. They confirm with a click in chat,
     * so this returns before the claim happens: check getSessionInfo().isSelfPlay to see it land.
     * Returns a table straight away only when they are already the host.
     *
     * Lua: local result, err = ccvault.claimHost()
     *      if result then print(result.hostName) else print(err) end
     */
    @LuaFunction
    public final Object[] claimHost() throws LuaException {
        ServerPlayer player = getInteractingPlayerOrThrow();
        if (player.getUUID().equals(ComputerPlacementTracker.getOwner(computerId))) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("hostName", player.getName().getString());
            response.put("computerId", computerId);
            return new Object[]{response};
        }

        return switch (SessionAuthManager.requestHostClaim(player, computerId)) {
            case PENDING -> new Object[]{null, "host claim already pending — check your chat"};
            default -> new Object[]{null, "confirm the host claim in chat"};
        };
    }

    /**
     * Forget this computer's host and lift its terminal lock. Afterwards the next player to place it,
     * or to confirm ccvault.claimHost, becomes the host.
     *
     * Lua: local hadHost = ccvault.forgetHost()
     */
    @LuaFunction
    public final boolean forgetHost() {
        return ComputerPlacementTracker.clearOwner(computerId);
    }

    /**
     * Get this computer's ID (useful for display / revoke commands).
     * Lua: local id = ccvault.getComputerId()
     */
    @LuaFunction
    public final int getComputerId() {
        return computerId;
    }

    // ===== Terminal Lock =====

    /**
     * Lock this computer so only its owner and server admins can open its terminal or break it.
     * Monitors keep working for everyone. Only the owner can change it, while using the computer.
     *
     * Lua: local ok, err = ccvault.setTerminalLock(true)
     */
    @LuaFunction
    public final Object[] setTerminalLock(boolean locked) throws LuaException {
        ServerPlayer player = getInteractingPlayerOrThrow();
        if (!player.getUUID().equals(ComputerPlacementTracker.getOwner(computerId))) {
            return new Object[]{null, "only this computer's owner can change its lock"};
        }
        ComputerLock.setLocked(computerId, locked);
        return new Object[]{true};
    }

    /**
     * Whether this computer's terminal is locked.
     * Lua: if ccvault.isTerminalLocked() then ... end
     */
    @LuaFunction
    public final boolean isTerminalLocked() {
        return ComputerLock.isLocked(computerId);
    }

    // ===== Session Info =====

    /**
     * Get information about the current CCVault session.
     * Includes player/host names, self-play detection, and auth state.
     * Does not require authentication.
     *
     * Lua: local info = ccvault.getSessionInfo()
     *      if info.isSelfPlay then print("Test mode!") end
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

        return new Object[]{info};
    }

    // ===== Self-Transfer (Test Mode) =====

    /**
     * Execute a simulated self-transfer for testing when player and host are the same account.
     * No real money moves — validates balance and logs what WOULD happen.
     * Returns success with a simulated txId.
     *
     * Requires authentication. Only works when player == host.
     *
     * Lua: local result, err = ccvault.transferSelf(100, "blackjack bet test")
     *      if result then print("TX: " .. result.txId .. " test=" .. tostring(result.testMode)) end
     */
    @LuaFunction
    public final Object[] transferSelf(long amount, String reason) throws LuaException {
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
            return new Object[]{null, OWNER_REGISTRATION_MESSAGE};
        }
        if (!playerUuid.equals(hostUuid)) {
            return new Object[]{null, "transferSelf is only for same-person sessions (player must be host)"};
        }

        // Self-play simulation: validate balance but don't move money
        long balance = EconomyBridge.getBalance(playerUuid);
        if (balance < 0) {
            return new Object[]{null, "could not read balance"};
        }
        if (balance < amount) {
            return new Object[]{null, "insufficient balance"};
        }

        String txId = generateSimulatedTxId();
        TransactionLedger.logTransfer(txId, playerUuid, playerUuid, amount,
                "[self-play] " + reason, computerId, playerUuid, hostUuid);
        BalanceNotifier.notifySelfPlay(playerUuid,
                "Self-transfer: " + amount + " tokens | " + reason);

        Map<String, Object> response = new HashMap<>();
        response.put("txId", txId);
        response.put("success", true);
        response.put("testMode", true);
        response.put("selfPlay", true);
        return new Object[]{response};
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
    public final Object[] verifyTransaction(String txId) throws LuaException {
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
    public final Object[] getTransactionHistory(int limit) throws LuaException {
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

    // ===== Internal helpers =====

    private String generateSimulatedTxId() {
        byte[] randomBytes = new byte[4];
        SECURE_RANDOM.nextBytes(randomBytes);
        StringBuilder hex = new StringBuilder(8);
        for (byte b : randomBytes) {
            hex.append(String.format("%02x", b & 0xFF));
        }
        return "sim-" + Long.toHexString(System.currentTimeMillis()) + "-" + hex;
    }

    private void requireAuth() throws LuaException {
        ServerPlayer player = getInteractingPlayerOrThrow();
        if (!SessionAuthManager.isAuthenticated(player.getUUID(), computerId)) {
            throw new LuaException("not authenticated — call ccvault.requestAuth() first");
        }
        SessionAuthManager.touchSession(player.getUUID(), computerId);
    }

    private static boolean isNamed(String target) {
        return target.toLowerCase(Locale.ROOT).startsWith(NAMED_PLAYER_PREFIX);
    }

    /** "player" means whoever touched last and needs their session. "player:Name" is checked in resolveTarget. */
    private void authorize(String... targets) throws LuaException {
        boolean anyNamed = false;
        boolean anyLastPlayer = false;
        for (String target : targets) {
            if (target == null) throw new LuaException("target is required");
            anyNamed |= isNamed(target);
            anyLastPlayer |= target.equalsIgnoreCase("player");
        }
        if (anyLastPlayer || !anyNamed) requireAuth();
    }

    private ServerPlayer approvedPlayer(String target) throws LuaException {
        String name = target.substring(NAMED_PLAYER_PREFIX.length());
        ServerPlayer player = onlinePlayer(name);
        if (player == null) {
            throw new LuaException("player '" + name + "' is not online");
        }
        if (!SessionAuthManager.isAuthenticated(player.getUUID(), computerId)) {
            throw new LuaException("player '" + name + "' has not approved this computer");
        }
        return player;
    }

    private static ServerPlayer onlinePlayer(String name) {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayerByName(name);
    }

    /** The host paying a player costs that player nothing, so they only have to have approved this computer: a winner who stepped away still gets paid. */
    private UUID resolveNamedPayee(String target) throws LuaException {
        return approvedPlayer(target).getUUID();
    }

    /** The named player, if they are online and their allowance here covers amount, which it now holds for this charge. */
    private UUID reserveAllowance(String target, long amount) {
        ServerPlayer player = onlinePlayer(target.substring(NAMED_PLAYER_PREFIX.length()));
        if (player == null) return null;
        return AllowanceManager.reserve(computerId, player.getUUID(), amount, System.currentTimeMillis()) ? player.getUUID() : null;
    }

    /** A named player must be at this table: online, approved here, and touching it within the staleness window. */
    private UUID resolveNamedPlayer(String target) throws LuaException {
        String name = target.substring(NAMED_PLAYER_PREFIX.length());
        ServerPlayer player = approvedPlayer(target);
        if (!ComputerInteractionTracker.isFresh(computerId, player.getUUID())) {
            throw new LuaException("player '" + name + "' has not touched this computer recently");
        }
        SessionAuthManager.touchSession(player.getUUID(), computerId);
        return player.getUUID();
    }

    private ServerPlayer getInteractingPlayerOrThrow() throws LuaException {
        ServerPlayer player = ComputerInteractionTracker.getFreshPlayer(computerId);
        if (player == null) {
            throw new LuaException("no player interacting with this computer (or interaction expired)");
        }
        return player;
    }

    private UUID resolveTarget(String target) throws LuaException {
        if (isNamed(target)) {
            return resolveNamedPlayer(target);
        }
        return switch (target.toLowerCase()) {
            case "player" -> {
                ServerPlayer p = getInteractingPlayerOrThrow();
                yield p.getUUID();
            }
            case "host" -> {
                UUID owner = ComputerPlacementTracker.getOwner(computerId);
                if (owner == null) {
                    throw new LuaException(OWNER_REGISTRATION_MESSAGE);
                }
                yield owner;
            }
            default -> throw new LuaException("invalid target '" + target + "' — use 'player', 'player:Name' or 'host'");
        };
    }

    private ServerPlayer getServerPlayerByUuid(UUID uuid) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.getPlayerList().getPlayer(uuid);
    }

    private PlayerLookup.Result resolvePlayer(String playerName) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return new PlayerLookup.Result(PlayerLookup.Status.UNKNOWN, null);

        try {
            return new PlayerLookup.Result(PlayerLookup.Status.FOUND, UUID.fromString(playerName));
        } catch (IllegalArgumentException ignored) {
            // Not a UUID string — continue with name-based lookup.
        }

        ServerPlayer onlinePlayer = server.getPlayerList().getPlayerByName(playerName);
        if (onlinePlayer != null) {
            return new PlayerLookup.Result(PlayerLookup.Status.FOUND, onlinePlayer.getUUID());
        }
        return PLAYER_LOOKUP.lookup(playerName);
    }

    /** Runs on the lookup thread: for a name the server has not seen this is a web request. */
    private static Optional<UUID> lookUpProfile(String playerName) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return Optional.empty();
        return server.getProfileCache().get(playerName).map(GameProfile::getId);
    }
}
