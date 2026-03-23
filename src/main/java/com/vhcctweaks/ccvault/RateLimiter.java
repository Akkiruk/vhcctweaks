package com.vhcctweaks.ccvault;

import com.vhcctweaks.config.ModConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter for CCVault operations.
 * Tracks requests per terminal and per player in sliding windows.
 */
public class RateLimiter {

    // computerId → window tracker
    private static final Map<Integer, SlidingWindow> terminalWindows = new ConcurrentHashMap<>();
    // playerUUID → window tracker
    private static final Map<UUID, SlidingWindow> playerWindows = new ConcurrentHashMap<>();

    /**
     * Check if a transfer is allowed under rate limits.
     * Returns null if allowed, or a rejection reason string if blocked.
     */
    public static String checkLimit(int computerId, UUID playerUuid) {
        long now = System.currentTimeMillis();

        SlidingWindow termWindow = terminalWindows.computeIfAbsent(computerId, k -> new SlidingWindow());
        if (termWindow.isOverLimit(now, ModConfig.CCVAULT_MAX_TRANSFERS_PER_MIN_TERMINAL.get())) {
            return "terminal rate limit exceeded";
        }

        SlidingWindow playerWindow = playerWindows.computeIfAbsent(playerUuid, k -> new SlidingWindow());
        if (playerWindow.isOverLimit(now, ModConfig.CCVAULT_MAX_TRANSFERS_PER_MIN_PLAYER.get())) {
            return "player rate limit exceeded";
        }

        return null;
    }

    /**
     * Get the number of remaining transfers allowed for a terminal in the current window.
     */
    public static int getRemainingTerminalTransfers(int computerId) {
        long now = System.currentTimeMillis();
        SlidingWindow window = terminalWindows.get(computerId);
        if (window == null) return ModConfig.CCVAULT_MAX_TRANSFERS_PER_MIN_TERMINAL.get();
        return Math.max(0, ModConfig.CCVAULT_MAX_TRANSFERS_PER_MIN_TERMINAL.get() - window.getCount(now));
    }

    /**
     * Get the number of remaining transfers allowed for a player in the current window.
     */
    public static int getRemainingPlayerTransfers(UUID playerUuid) {
        long now = System.currentTimeMillis();
        SlidingWindow window = playerWindows.get(playerUuid);
        if (window == null) return ModConfig.CCVAULT_MAX_TRANSFERS_PER_MIN_PLAYER.get();
        return Math.max(0, ModConfig.CCVAULT_MAX_TRANSFERS_PER_MIN_PLAYER.get() - window.getCount(now));
    }

    /**
     * Record that a transfer was executed (call after successful validation).
     */
    public static void recordTransfer(int computerId, UUID playerUuid) {
        long now = System.currentTimeMillis();
        terminalWindows.computeIfAbsent(computerId, k -> new SlidingWindow()).record(now);
        playerWindows.computeIfAbsent(playerUuid, k -> new SlidingWindow()).record(now);
    }

    /**
     * Simple sliding window counter.
     * Tracks events in the current 60-second window.
     * All methods are synchronized to prevent race conditions during window resets.
     */
    private static class SlidingWindow {
        private long windowStart;
        private int count;

        synchronized boolean isOverLimit(long now, int maxPerMinute) {
            rollWindow(now);
            return count >= maxPerMinute;
        }

        synchronized void record(long now) {
            rollWindow(now);
            count++;
        }

        synchronized int getCount(long now) {
            rollWindow(now);
            return count;
        }

        private void rollWindow(long now) {
            if (now - windowStart >= 60_000) {
                windowStart = now;
                count = 0;
            }
        }
    }
}
