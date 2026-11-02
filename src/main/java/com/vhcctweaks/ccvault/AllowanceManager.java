package com.vhcctweaks.ccvault;

import com.vhcctweaks.link.ComputerIndex;
import dan200.computercraft.shared.computer.core.ServerComputer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spending allowances: a player approves once, in chat, that one computer may charge them up to N tokens over M
 * minutes with no fresh touch. Kept in memory only: an allowance ends on disconnect, expiry, when used up, with
 * ccvault.endAllowance, or with /ccvault revoke.
 *
 * Charging reserves first and refunds a failed transfer, so two charges at once can never spend more than is left.
 */
public final class AllowanceManager {

    public static final String EVENT = "ccvault_allowance";

    /** Where ccvault_allowance goes. Tests replace it. */
    public interface EventSink {
        void queue(int computerId, String event, Object... args);
    }

    private record Key(int computerId, UUID player) {}

    private static final class Allowance {
        long remaining;
        final long expiresAt;

        Allowance(long remaining, long expiresAt) {
            this.remaining = remaining;
            this.expiresAt = expiresAt;
        }
    }

    private static final Map<Key, Allowance> ALLOWANCES = new ConcurrentHashMap<>();
    /** Request ids count 1, 2, ... on each computer, from its boot. */
    private static final Map<Integer, AtomicInteger> NEXT_REQUEST = new ConcurrentHashMap<>();
    private static volatile EventSink sink = AllowanceManager::queueOnComputer;

    private AllowanceManager() {}

    public static void reset() {
        ALLOWANCES.clear();
        NEXT_REQUEST.clear();
    }

    static void setSink(EventSink newSink) {
        sink = newSink;
    }

    static int nextRequestId(int computerId) {
        return NEXT_REQUEST.computeIfAbsent(computerId, id -> new AtomicInteger(1)).getAndIncrement();
    }

    /** A computer booted: its request ids start again at 1. */
    public static void restartIds(int computerId) {
        NEXT_REQUEST.remove(computerId);
    }

    /** An approved request replaces any allowance the player already gave this computer. */
    static void grant(int computerId, UUID player, long maxTokens, long expiresAt) {
        ALLOWANCES.put(new Key(computerId, player), new Allowance(maxTokens, expiresAt));
    }

    /** Takes amount from the allowance if it covers it, before the transfer runs. */
    static boolean reserve(int computerId, UUID player, long amount, long now) {
        Key key = new Key(computerId, player);
        Allowance allowance = ALLOWANCES.get(key);
        if (allowance == null) return false;
        synchronized (allowance) {
            if (now >= allowance.expiresAt) {
                ALLOWANCES.remove(key, allowance);
                return false;
            }
            if (amount <= 0 || allowance.remaining < amount) return false;
            allowance.remaining -= amount;
            return true;
        }
    }

    /** After a reserved charge: a failed transfer gives the amount back, and a used-up allowance ends. */
    static void settle(int computerId, UUID player, long amount, boolean charged) {
        Key key = new Key(computerId, player);
        Allowance allowance = ALLOWANCES.get(key);
        if (allowance == null) return;
        synchronized (allowance) {
            if (!charged) allowance.remaining += amount;
            if (allowance.remaining <= 0) ALLOWANCES.remove(key, allowance);
        }
    }

    /** { remaining, secondsLeft }, or null if there is none. */
    static long[] get(int computerId, UUID player, long now) {
        Key key = new Key(computerId, player);
        Allowance allowance = ALLOWANCES.get(key);
        if (allowance == null) return null;
        synchronized (allowance) {
            if (now >= allowance.expiresAt || allowance.remaining <= 0) {
                ALLOWANCES.remove(key, allowance);
                return null;
            }
            return new long[]{allowance.remaining, (allowance.expiresAt - now) / 1000};
        }
    }

    static boolean end(int computerId, UUID player) {
        return ALLOWANCES.remove(new Key(computerId, player)) != null;
    }

    /** Disconnect, or /ccvault revoke with no computer: every allowance this player gave ends. */
    static void endAll(UUID player) {
        ALLOWANCES.keySet().removeIf(key -> key.player().equals(player));
    }

    static void notify(int computerId, int requestId, String playerName, boolean approved) {
        sink.queue(computerId, EVENT, requestId, playerName, approved);
    }

    private static void queueOnComputer(int computerId, String event, Object... args) {
        ServerComputer computer = ComputerIndex.get(computerId);
        if (computer != null) computer.queueEvent(event, args);
    }
}
