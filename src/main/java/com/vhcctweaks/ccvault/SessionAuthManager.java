package com.vhcctweaks.ccvault;

import com.vhcctweaks.VHCCTweaks;
import com.vhcctweaks.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /** An authenticated session for a computer. */
    private record AuthSession(long lastActivityAt) {}

    /** A pending auth request waiting for the player to click approve. */
    private record PendingAuth(UUID playerUuid, int computerId, long expiresAt) {}

    /** Clear obsolete persisted auth state from earlier builds. */
    public static void init(Path dataDir) {
        Path legacyPath = dataDir.resolve("ccvault").resolve("auth_sessions.json");
        if (!Files.exists(legacyPath)) {
            return;
        }

        try {
            Files.deleteIfExists(legacyPath);
            VHCCTweaks.LOGGER.info("CCVault: Removed obsolete auth_sessions.json after auth model rollback");
        } catch (IOException e) {
            VHCCTweaks.LOGGER.warn("CCVault: Failed to remove obsolete auth_sessions.json", e);
        }
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

        for (PendingAuth pending : pendingNonces.values()) {
            if (pending.playerUuid().equals(player.getUUID())
                    && pending.computerId() == computerId
                    && pending.expiresAt() > now) {
                return RequestAuthResult.PENDING;
            }
        }

        String nonce = generateNonce();
        long expiryMs = ModConfig.CCVAULT_NONCE_EXPIRY_SECONDS.get() * 1000L;
        pendingNonces.put(nonce, new PendingAuth(player.getUUID(), computerId, now + expiryMs));

        MutableComponent msg = new TextComponent("")
                .append(new TextComponent("[CCVault] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(new TextComponent("Computer #" + computerId + " is requesting access to your wallet. ").withStyle(ChatFormatting.YELLOW))
                .append(new TextComponent("[APPROVE]")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.GREEN)
                                .withBold(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ccvault approve " + nonce))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        new TextComponent("Click to authorize this terminal")))));

        PlayerMessageQueue.send(player, msg);

        VHCCTweaks.LOGGER.debug("CCVault: Auth nonce sent to {} for computer {}", player.getName().getString(), computerId);
        return RequestAuthResult.SENT;
    }

    /**
     * Attempt to approve a nonce (called from /ccvault approve command).
     * Returns true if auth was granted.
     */
    public static boolean approveNonce(UUID playerUuid, String nonce) {
        long now = System.currentTimeMillis();
        PendingAuth pending = pendingNonces.get(nonce);
        if (pending == null) return false;
        if (!pending.playerUuid().equals(playerUuid)) return false;
        if (pending.expiresAt() < now) {
            pendingNonces.remove(nonce, pending);
            return false;
        }
        if (!pendingNonces.remove(nonce, pending)) {
            return false;
        }

        computerSessions.computeIfAbsent(pending.computerId(), id -> new ConcurrentHashMap<>()).put(playerUuid, new AuthSession(now));
        VHCCTweaks.LOGGER.debug("CCVault: Player {} authenticated for computer {}", playerUuid, pending.computerId());
        return true;
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
