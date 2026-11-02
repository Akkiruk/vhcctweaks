package com.vhcctweaks.link;

import dan200.computercraft.shared.computer.core.ServerComputer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every loaded server computer by its computer ID, kept by ServerComputerIndexMixin as CC registers and unloads them.
 * CC's own registry is keyed by instance ID, so finding a computer by its ID there means walking all of them.
 */
public final class ComputerIndex {

    private static final Map<Integer, ServerComputer> BY_ID = new ConcurrentHashMap<>();

    private ComputerIndex() {}

    public static void reset() {
        BY_ID.clear();
    }

    /** The loaded computer with this ID, or null. Two computers sharing an ID resolve to the one loaded last. */
    public static ServerComputer get(int computerId) {
        return BY_ID.get(computerId);
    }

    public static void onRegistered(ServerComputer computer) {
        int id = computer.getID();
        if (id < 0) return;
        BY_ID.put(id, computer);
        LinkManager.onTargetLoaded(id);
    }

    public static void onUnloaded(ServerComputer computer) {
        int id = computer.getID();
        if (id < 0) return;
        if (BY_ID.remove(id, computer)) LinkManager.onTargetUnloaded(id);
    }
}
