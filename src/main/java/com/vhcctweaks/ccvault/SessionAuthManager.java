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

import java.security.SecureRandom;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-based authentication for CCVault.
 *
 * Flow:
 * 1. CC script calls ccvault.requestAuth()
 * 2. Server sends the interacting player a clickable chat message with a nonce
 * 3. Player clicks → runs /ccvault approve <nonce>
 * 4. Session created: (playerUUID, computerID) → authenticated
 * 5. Session cleared on player disconnect
 *
 * Each nonce is single-use and expires after a configurable timeout.
 */
public class SessionAuthManager {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String NONCE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int NONCE_LENGTH = 12;

    // Active authenticated sessions: sessionKey → true
    private static final Set<SessionKey> activeSessions = ConcurrentHashMap.newKeySet();

    // Current authenticated principal per computer.
    // Exactly one player may hold an active auth session for a computer at a time.
    private static final Map<Integer, UUID> computerAuthenticatedPlayer = new ConcurrentHashMap<>();

    // Pending nonces: nonce string → PendingAuth
    private static final Map<String, PendingAuth> pendingNonces = new ConcurrentHashMap<>();

    /** Composite key for an authenticated session. */
    private record SessionKey(UUID playerUuid, int computerId) {}

    /** A pending auth request waiting for the player to click approve. */
    private record PendingAuth(UUID playerUuid, int computerId, long expiresAt) {}

    /**
     * Check if a player is authenticated for a specific computer this session.
     */
    public static boolean isAuthenticated(UUID playerUuid, int computerId) {
        UUID boundPlayer = computerAuthenticatedPlayer.get(computerId);
        if (!Objects.equals(boundPlayer, playerUuid)) return false;
        return activeSessions.contains(new SessionKey(playerUuid, computerId));
    }

    /**
     * Get the UUID currently authenticated for a computer, or null if none.
     */
    public static UUID getAuthenticatedPlayer(int computerId) {
        return computerAuthenticatedPlayer.get(computerId);
    }

    /**
     * Create a pending auth request and send a clickable message to the player.
     * Returns the nonce if sent, or null if the player couldn't be reached.
     */
    public static String requestAuth(ServerPlayer player, int computerId) {
        // Clean expired nonces lazily
        long now = System.currentTimeMillis();
        pendingNonces.values().removeIf(p -> p.expiresAt() < now);

        // Don't spam — check if already pending for this player+computer
        for (PendingAuth pending : pendingNonces.values()) {
            if (pending.playerUuid().equals(player.getUUID())
                    && pending.computerId() == computerId
                    && pending.expiresAt() > now) {
                return null; // Already has a pending request
            }
        }

        String nonce = generateNonce();
        long expiryMs = ModConfig.CCVAULT_NONCE_EXPIRY_SECONDS.get() * 1000L;
        pendingNonces.put(nonce, new PendingAuth(player.getUUID(), computerId, now + expiryMs));

        // Build clickable chat message — impossible for CC scripts to forge
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

        player.sendMessage(msg, player.getUUID());

        VHCCTweaks.LOGGER.debug("CCVault: Auth nonce sent to {} for computer {}", player.getName().getString(), computerId);
        return nonce;
    }

    /**
     * Attempt to approve a nonce (called from /ccvault approve command).
     * Returns true if auth was granted.
     */
    public static boolean approveNonce(UUID playerUuid, String nonce) {
        PendingAuth pending = pendingNonces.remove(nonce);
        if (pending == null) return false;
        if (!pending.playerUuid().equals(playerUuid)) return false;
        if (pending.expiresAt() < System.currentTimeMillis()) return false;

        // Replace any existing auth principal for this computer.
        activeSessions.removeIf(key -> key.computerId() == pending.computerId());
        activeSessions.add(new SessionKey(playerUuid, pending.computerId()));
        computerAuthenticatedPlayer.put(pending.computerId(), playerUuid);
        VHCCTweaks.LOGGER.debug("CCVault: Player {} authenticated for computer {}", playerUuid, pending.computerId());
        return true;
    }

    /**
     * Revoke a player's session for a specific computer.
     */
    public static void revokeSession(UUID playerUuid, int computerId) {
        activeSessions.remove(new SessionKey(playerUuid, computerId));
        UUID bound = computerAuthenticatedPlayer.get(computerId);
        if (Objects.equals(bound, playerUuid)) {
            computerAuthenticatedPlayer.remove(computerId);
        }
    }

    /**
     * Clear all sessions for a player (called on disconnect).
     */
    public static void clearPlayerSessions(UUID playerUuid) {
        activeSessions.removeIf(key -> key.playerUuid().equals(playerUuid));
        computerAuthenticatedPlayer.entrySet().removeIf(e -> e.getValue().equals(playerUuid));
        pendingNonces.values().removeIf(p -> p.playerUuid().equals(playerUuid));
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            clearPlayerSessions(player.getUUID());
            VHCCTweaks.LOGGER.debug("CCVault: Cleared all sessions for {}", player.getName().getString());
        }
    }

    private static String generateNonce() {
        StringBuilder sb = new StringBuilder(NONCE_LENGTH);
        for (int i = 0; i < NONCE_LENGTH; i++) {
            sb.append(NONCE_CHARS.charAt(RANDOM.nextInt(NONCE_CHARS.length())));
        }
        return sb.toString();
    }
}
