package com.vhcctweaks.ccvault;

import com.vhcctweaks.VHCCTweaks;
import com.vhcctweaks.config.ModConfig;
import com.vhcctweaks.handler.ComputerPlacementTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-based authentication for CCVault.
 *
 * Flow:
 * 1. CC script calls ccvault.requestAuth()
 * 2. Server sends the interacting player a clickable chat message with a nonce
 * 3. Player clicks -> runs /ccvault approve <nonce>
 * 4. Session created: computerID -> authenticated player session
 * 5. Session expires after inactivity or when the player disconnects
 *
 * Becoming a host uses the same flow, from ccvault.claimHost or from right-clicking an unowned computer:
 * it only happens once the player clicks.
 * Each nonce is single-use and expires after a configurable timeout.
 */
public class SessionAuthManager {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String NONCE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int NONCE_LENGTH = 12;

    // Authenticated players per computer. Several players can sit at one table,
    // and one player approving never ends another player's session.
    private static final Map<Integer, Map<UUID, AuthSession>> computerSessions = new ConcurrentHashMap<>();

    // Pending nonces: nonce string -> PendingAuth
    private static final Map<String, PendingAuth> pendingNonces = new ConcurrentHashMap<>();

    public enum RequestAuthResult {
        SENT,
        PENDING,
        ALREADY_AUTHENTICATED
    }

    public enum Approval {
        INVALID,
        SESSION,
        HOST,
        TAKEN
    }

    /** What a click grants: a session, the host role from ccvault.claimHost, or an unowned computer offered on right-click. */
    enum Request {
        SESSION,
        CLAIM,
        OFFER
    }

    /** An authenticated session for a computer. */
    private record AuthSession(long lastActivityAt) {}

    /** A pending request waiting for the player to click. */
    private record PendingAuth(UUID playerUuid, int computerId, long expiresAt, Request request) {}

    /** Computer IDs restart with each world, so nothing here may carry over to the next one. */
    public static void reset() {
        computerSessions.clear();
        pendingNonces.clear();
    }

    /**
     * Check if a player is authenticated for a specific computer.
     */
    public static boolean isAuthenticated(UUID playerUuid, int computerId) {
        Map<UUID, AuthSession> sessions = computerSessions.get(computerId);
        if (sessions == null) return false;
        AuthSession session = sessions.get(playerUuid);
        if (session == null) return false;
        if (sessionExpired(session, System.currentTimeMillis())) {
            sessions.remove(playerUuid, session);
            return false;
        }
        return true;
    }

    /**
     * Refresh session activity for an already authenticated player.
     */
    public static void touchSession(UUID playerUuid, int computerId) {
        Map<UUID, AuthSession> sessions = computerSessions.get(computerId);
        if (sessions == null) return;
        long now = System.currentTimeMillis();
        sessions.computeIfPresent(playerUuid, (id, session) -> sessionExpired(session, now) ? null : new AuthSession(now));
    }

    /**
     * Create a pending auth request and send a clickable message to the player.
     * Returns the request state for the player/computer pair.
     */
    public static RequestAuthResult requestAuth(ServerPlayer player, int computerId) {
        long now = System.currentTimeMillis();
        cleanupPendingNonces(now);
        purgeExpiredSessions(now);

        if (isAuthenticated(player.getUUID(), computerId)) {
            touchSession(player.getUUID(), computerId);
            return RequestAuthResult.ALREADY_AUTHENTICATED;
        }

        if (hasPending(player.getUUID(), computerId, Request.SESSION, now)) {
            return RequestAuthResult.PENDING;
        }

        sendPrompt(player, computerId, Request.SESSION, "Computer #" + computerId + " is requesting access to your wallet. ",
                "[APPROVE]", "Click to authorize this terminal");

        VHCCTweaks.LOGGER.debug("CCVault: Auth nonce sent to {} for computer {}", player.getName().getString(), computerId);
        return RequestAuthResult.SENT;
    }

    /**
     * Ask the player to confirm in chat that they want to become this computer's host.
     * Returns SENT or PENDING: the claim itself happens when they click.
     */
    public static RequestAuthResult requestHostClaim(ServerPlayer player, int computerId) {
        long now = System.currentTimeMillis();
        cleanupPendingNonces(now);

        if (hasPending(player.getUUID(), computerId, Request.CLAIM, now)) {
            return RequestAuthResult.PENDING;
        }

        sendPrompt(player, computerId, Request.CLAIM, "Computer #" + computerId + " wants to make you its host. Scripts on it can spend from your wallet. ",
                "[CONFIRM]", "Click to become this computer's host");
        return RequestAuthResult.SENT;
    }

    /**
     * Offer an unowned computer to a player who right-clicked it. Every click offers again, and the newest
     * link replaces the last. An offer never takes a computer from a host: if someone gets there first it does nothing.
     */
    public static void offerHost(ServerPlayer player, int computerId) {
        UUID playerUuid = player.getUUID();
        pendingNonces.values().removeIf(p -> p.request() == Request.OFFER && p.computerId() == computerId && p.playerUuid().equals(playerUuid));

        sendPrompt(player, computerId, Request.OFFER, "Computer #" + computerId + " has no host. Claim it? Scripts on it can spend from your wallet. ",
                "[CLAIM]", "Click to become this computer's host");
    }

    private static boolean hasPending(UUID playerUuid, int computerId, Request request, long now) {
        for (PendingAuth pending : pendingNonces.values()) {
            if (pending.playerUuid().equals(playerUuid)
                    && pending.computerId() == computerId
                    && pending.request() == request
                    && pending.expiresAt() > now) {
                return true;
            }
        }
        return false;
    }

    static String addPending(UUID playerUuid, int computerId, long expiresAt, Request request) {
        String nonce = generateNonce();
        pendingNonces.put(nonce, new PendingAuth(playerUuid, computerId, expiresAt, request));
        return nonce;
    }

    private static void sendPrompt(ServerPlayer player, int computerId, Request request, String text, String button, String hover) {
        long expiryMs = ModConfig.CCVAULT_NONCE_EXPIRY_SECONDS.get() * 1000L;
        String nonce = addPending(player.getUUID(), computerId, System.currentTimeMillis() + expiryMs, request);

        MutableComponent msg = new TextComponent("")
                .append(new TextComponent("[CCVault] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(new TextComponent(text).withStyle(ChatFormatting.YELLOW))
                .append(new TextComponent(button)
                        .withStyle(style -> style
                                .withColor(ChatFormatting.GREEN)
                                .withBold(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ccvault approve " + nonce))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent(hover)))));

        PlayerMessageQueue.send(player, msg);
    }

    /**
     * Attempt to approve a nonce (called from /ccvault approve command).
     * Returns what was granted, or INVALID.
     */
    public static Approval approveNonce(UUID playerUuid, String nonce) {
        long now = System.currentTimeMillis();
        PendingAuth pending = pendingNonces.get(nonce);
        if (pending == null) return Approval.INVALID;
        if (!pending.playerUuid().equals(playerUuid)) return Approval.INVALID;
        if (pending.expiresAt() < now) {
            pendingNonces.remove(nonce, pending);
            return Approval.INVALID;
        }
        if (!pendingNonces.remove(nonce, pending)) {
            return Approval.INVALID;
        }

        if (pending.request() == Request.OFFER && !ComputerPlacementTracker.setOwner(pending.computerId(), playerUuid)) {
            return Approval.TAKEN;
        }
        if (pending.request() != Request.SESSION) {
            if (pending.request() == Request.CLAIM) ComputerPlacementTracker.replaceOwner(pending.computerId(), playerUuid);
            VHCCTweaks.LOGGER.info("CCVault: Player {} claimed computer {} as host", playerUuid, pending.computerId());
            return Approval.HOST;
        }

        computerSessions.computeIfAbsent(pending.computerId(), id -> new ConcurrentHashMap<>()).put(playerUuid, new AuthSession(now));
        VHCCTweaks.LOGGER.debug("CCVault: Player {} authenticated for computer {}", playerUuid, pending.computerId());
        return Approval.SESSION;
    }

    /**
     * Revoke a player's session for a specific computer.
     */
    public static void revokeSession(UUID playerUuid, int computerId) {
        Map<UUID, AuthSession> sessions = computerSessions.get(computerId);
        if (sessions != null) sessions.remove(playerUuid);
    }

    /**
     * Clear all auth state for a player when they disconnect.
     */
    public static void clearPlayerSessions(UUID playerUuid) {
        computerSessions.values().forEach(sessions -> sessions.remove(playerUuid));
        pendingNonces.values().removeIf(p -> p.playerUuid().equals(playerUuid));
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            clearPlayerSessions(player.getUUID());
            VHCCTweaks.LOGGER.debug("CCVault: Cleared all auth state for {}", player.getName().getString());
        }
    }

    private static String generateNonce() {
        StringBuilder sb = new StringBuilder(NONCE_LENGTH);
        for (int i = 0; i < NONCE_LENGTH; i++) {
            sb.append(NONCE_CHARS.charAt(RANDOM.nextInt(NONCE_CHARS.length())));
        }
        return sb.toString();
    }

    private static void purgeExpiredSessions(long now) {
        computerSessions.values().forEach(sessions -> sessions.values().removeIf(session -> sessionExpired(session, now)));
    }

    private static boolean sessionExpired(AuthSession session, long now) {
        long idleTimeoutMs = ModConfig.CCVAULT_AUTH_IDLE_TIMEOUT_MINUTES.get() * 60_000L;
        return now - session.lastActivityAt() > idleTimeoutMs;
    }

    private static void cleanupPendingNonces(long now) {
        pendingNonces.values().removeIf(p -> p.expiresAt() < now);
    }
}
