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
import java.util.Objects;
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

    // Current authenticated principal per computer.
    // Exactly one player may hold an active auth session for a computer at a time.
    private static final Map<Integer, AuthSession> computerSessions = new ConcurrentHashMap<>();

    // Pending nonces: nonce string -> PendingAuth
    private static final Map<String, PendingAuth> pendingNonces = new ConcurrentHashMap<>();

    public enum RequestAuthResult {
        SENT,
        PENDING,
        ALREADY_AUTHENTICATED
    }

    /** An authenticated session for a computer. */
    private record AuthSession(UUID playerUuid, long lastActivityAt) {}

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
        AuthSession session = getActiveSession(computerId);
        return session != null && Objects.equals(session.playerUuid(), playerUuid);
    }

    /**
     * Get the UUID currently authenticated for a computer, or null if none.
     */
    public static UUID getAuthenticatedPlayer(int computerId) {
        AuthSession session = getActiveSession(computerId);
        return session != null ? session.playerUuid() : null;
    }

    /**
     * Refresh session activity for an already authenticated player.
     */
    public static void touchSession(UUID playerUuid, int computerId) {
        long now = System.currentTimeMillis();
        computerSessions.computeIfPresent(computerId, (id, session) -> {
            if (sessionExpired(session, now)) {
                return null;
            }
            if (!Objects.equals(session.playerUuid(), playerUuid)) {
                return session;
            }
            return new AuthSession(playerUuid, now);
        });
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

        computerSessions.put(pending.computerId(), new AuthSession(playerUuid, now));
        VHCCTweaks.LOGGER.debug("CCVault: Player {} authenticated for computer {}", playerUuid, pending.computerId());
        return true;
    }

    /**
     * Revoke a player's session for a specific computer.
     */
    public static void revokeSession(UUID playerUuid, int computerId) {
        computerSessions.computeIfPresent(computerId, (id, session) ->
                Objects.equals(session.playerUuid(), playerUuid) ? null : session);
    }

    /**
     * Clear all auth state for a player when they disconnect.
     */
    public static void clearPlayerSessions(UUID playerUuid) {
        computerSessions.entrySet().removeIf(entry -> Objects.equals(entry.getValue().playerUuid(), playerUuid));
        pendingNonces.values().removeIf(p -> Objects.equals(p.playerUuid(), playerUuid));
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

    private static AuthSession getActiveSession(int computerId) {
        long now = System.currentTimeMillis();
        AuthSession session = computerSessions.get(computerId);
        if (session == null) return null;
        if (sessionExpired(session, now)) {
            computerSessions.remove(computerId, session);
            return null;
        }
        return session;
    }

    private static void purgeExpiredSessions(long now) {
        computerSessions.entrySet().removeIf(entry -> sessionExpired(entry.getValue(), now));
    }

    private static boolean sessionExpired(AuthSession session, long now) {
        long idleTimeoutMs = ModConfig.CCVAULT_AUTH_IDLE_TIMEOUT_MINUTES.get() * 60_000L;
        return now - session.lastActivityAt() > idleTimeoutMs;
    }

    private static void cleanupPendingNonces(long now) {
        pendingNonces.values().removeIf(p -> p.expiresAt() < now);
    }
}
