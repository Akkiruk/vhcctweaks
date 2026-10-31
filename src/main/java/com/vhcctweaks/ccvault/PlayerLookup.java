package com.vhcctweaks.ccvault;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Turns player names into UUIDs without blocking the caller. A name the server has not seen can mean a
 * Mojang web request, and computers share one thread by default, so a script must never wait on it.
 * The first call for a name starts the lookup and answers PENDING; later calls get the result.
 */
final class PlayerLookup {

    enum Status { FOUND, UNKNOWN, PENDING }

    record Result(Status status, UUID uuid) {}

    static final int MAX_ENTRIES = 1024;
    static final long RETRY_UNKNOWN_MS = 10 * 60_000L;

    private record Entry(CompletableFuture<Optional<UUID>> future, long startedAt) {
        boolean finishedUnknown() {
            return future.isDone() && future.join().isEmpty();
        }
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Function<String, Optional<UUID>> resolver;
    private final Executor executor;

    PlayerLookup(Function<String, Optional<UUID>> resolver, Executor executor) {
        this.resolver = resolver;
        this.executor = executor;
    }

    Result lookup(String name) {
        long now = System.currentTimeMillis();
        String key = name.toLowerCase(Locale.ROOT);

        Entry entry = entries.get(key);
        if (entry != null && entry.finishedUnknown() && now - entry.startedAt > RETRY_UNKNOWN_MS) {
            entries.remove(key, entry);
            entry = null;
        }
        if (entry == null) {
            if (entries.size() >= MAX_ENTRIES) {
                entries.values().removeIf(e -> e.future.isDone());
                if (entries.size() >= MAX_ENTRIES) return new Result(Status.PENDING, null);
            }
            entry = entries.computeIfAbsent(key, k -> new Entry(start(name), now));
        }

        if (!entry.future.isDone()) return new Result(Status.PENDING, null);
        return entry.future.join()
                .map(uuid -> new Result(Status.FOUND, uuid))
                .orElse(new Result(Status.UNKNOWN, null));
    }

    private CompletableFuture<Optional<UUID>> start(String name) {
        return CompletableFuture.supplyAsync(() -> resolver.apply(name), executor)
                .exceptionally(failure -> Optional.empty());
    }
}
