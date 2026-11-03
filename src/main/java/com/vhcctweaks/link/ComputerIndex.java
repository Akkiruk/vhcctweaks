package com.vhcctweaks.link;

import dan200.computercraft.shared.computer.core.ServerComputer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Every loaded server computer by its computer ID, kept by ServerComputerIndexMixin as CC registers and unloads them.
 * CC's own registry is keyed by instance ID, so finding a computer by its ID there means walking all of them.
 *
 * Two computers can share an ID (a copied computer, a labelled one placed twice). All of them are kept: the one loaded
 * last answers get, and when it unloads the one before it answers again, so unloading one never forgets the other.
 */
public final class ComputerIndex {

    private static final Map<Integer, List<ServerComputer>> BY_ID = new ConcurrentHashMap<>();

    private ComputerIndex() {}

    public static void reset() {
        BY_ID.clear();
    }

    /** The loaded computer with this ID, or null; the one loaded last if several share it. */
    public static ServerComputer get(int computerId) {
        List<ServerComputer> loaded = BY_ID.get(computerId);
        if (loaded == null) return null;
        int size = loaded.size();
        return size == 0 ? null : loaded.get(size - 1);
    }

    /** Every loaded computer with this ID, oldest first. */
    public static List<ServerComputer> all(int computerId) {
        List<ServerComputer> loaded = BY_ID.get(computerId);
        return loaded == null ? List.of() : List.copyOf(loaded);
    }

    public static void onRegistered(ServerComputer computer) {
        int id = computer.getID();
        if (id < 0) return;
        BY_ID.compute(id, (key, loaded) -> {
            List<ServerComputer> list = loaded != null ? loaded : new CopyOnWriteArrayList<>();
            list.remove(computer);
            list.add(computer);
            return list;
        });
        LinkManager.onTargetLoaded(id);
    }

    public static void onUnloaded(ServerComputer computer) {
        int id = computer.getID();
        if (id < 0) return;
        boolean[] gone = {false};
        boolean[] removed = {false};
        BY_ID.computeIfPresent(id, (key, loaded) -> {
            removed[0] = loaded.remove(computer);
            gone[0] = loaded.isEmpty();
            return gone[0] ? null : loaded;
        });
        if (!removed[0]) return;
        if (gone[0]) {
            LinkManager.onTargetUnloaded(id);
        } else {
            // Another computer with this ID is still loaded: links move over to it on the next update
            LinkManager.onTargetUnloaded(id);
            LinkManager.onTargetLoaded(id);
        }
    }
}
