package com.vhcctweaks.inventory;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.NotAttachedException;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * inventory_changed events. A watch is one block, seen by one computer through one attachment, so the event carries
 * the name that computer uses: "left", "minecraft:chest_3" or "5:left".
 *
 * A change marks the watch pending, at most once; the server tick's end sends one event per pending watch. So at most
 * one event goes out per inventory per computer per tick, and with nothing watched a change costs one volatile read.
 * Blocks are keys by identity (BlockEntity does not override equals).
 */
public final class InventoryWatch {

    public static final String EVENT = "inventory_changed";

    /** True while anything is watched. Read on every setChanged in the game, so it is the only thing checked first. */
    public static volatile boolean active;

    private static final Map<Object, List<Watch>> BY_BLOCK = new ConcurrentHashMap<>();
    private static final Map<Integer, Set<Watch>> BY_COMPUTER = new ConcurrentHashMap<>();
    private static final Queue<Watch> PENDING = new ConcurrentLinkedQueue<>();

    static final class Watch {
        final Object block;
        final IComputerAccess access;
        final int computerId;
        final AtomicBoolean pending = new AtomicBoolean();
        volatile boolean dead;

        Watch(Object block, IComputerAccess access, int computerId) {
            this.block = block;
            this.access = access;
            this.computerId = computerId;
        }
    }

    private InventoryWatch() {}

    public static synchronized void reset() {
        BY_BLOCK.clear();
        BY_COMPUTER.clear();
        PENDING.clear();
        active = false;
    }

    /**
     * Start watching. Watching the same block through the same attachment again changes nothing.
     * Watches that can no longer fire are dropped before counting against the limit.
     */
    public static synchronized void watch(Object block, IComputerAccess access, int computerId, int max,
                                          Predicate<Object> blockGone) throws LuaException {
        List<Watch> onBlock = BY_BLOCK.get(block);
        if (onBlock != null) {
            for (Watch watch : onBlock) if (watch.access == access) return;
        }

        Set<Watch> mine = BY_COMPUTER.get(computerId);
        if (mine != null) {
            for (Watch watch : new ArrayList<>(mine)) {
                if (blockGone.test(watch.block) || !isAttached(watch.access)) remove(watch);
            }
            mine = BY_COMPUTER.get(computerId);
        }
        if (mine != null && mine.size() >= max) throw new LuaException("too many watched inventories (max " + max + ")");

        Watch watch = new Watch(block, access, computerId);
        BY_BLOCK.computeIfAbsent(block, b -> new CopyOnWriteArrayList<>()).add(watch);
        BY_COMPUTER.computeIfAbsent(computerId, id -> ConcurrentHashMap.newKeySet()).add(watch);
        active = true;
    }

    /** Stop watching. Returns whether there was a watch. */
    public static synchronized boolean unwatch(Object block, IComputerAccess access) {
        List<Watch> onBlock = BY_BLOCK.get(block);
        if (onBlock == null) return false;
        for (Watch watch : onBlock) {
            if (watch.access == access) {
                remove(watch);
                return true;
            }
        }
        return false;
    }

    /** A computer rebooted or shut down: every watch it made ends. */
    public static synchronized void clearComputer(int computerId) {
        Set<Watch> mine = BY_COMPUTER.get(computerId);
        if (mine == null) return;
        for (Watch watch : new ArrayList<>(mine)) remove(watch);
    }

    /** A block's contents changed. Only called while something is watched. */
    public static void onChanged(Object block) {
        List<Watch> onBlock = BY_BLOCK.get(block);
        if (onBlock == null) return;
        for (Watch watch : onBlock) {
            if (!watch.dead && watch.pending.compareAndSet(false, true)) PENDING.add(watch);
        }
    }

    /** A block was removed or unloaded: its watches end. */
    public static void onRemoved(Object block) {
        if (!BY_BLOCK.containsKey(block)) return;
        synchronized (InventoryWatch.class) {
            List<Watch> onBlock = BY_BLOCK.get(block);
            if (onBlock == null) return;
            for (Watch watch : new ArrayList<>(onBlock)) remove(watch);
        }
    }

    /** Sends one event per pending watch. Watches whose block or attachment has gone are dropped instead. */
    public static int flush(Predicate<Object> blockGone) {
        int sent = 0;
        Watch watch;
        while ((watch = PENDING.poll()) != null) {
            watch.pending.set(false);
            if (watch.dead) continue;
            if (blockGone.test(watch.block)) {
                drop(watch);
                continue;
            }
            try {
                watch.access.queueEvent(EVENT, watch.access.getAttachmentName());
                sent++;
            } catch (NotAttachedException e) {
                drop(watch);
            }
        }
        return sent;
    }

    public static synchronized int count(int computerId) {
        Set<Watch> mine = BY_COMPUTER.get(computerId);
        return mine == null ? 0 : mine.size();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        flush(InventoryWatch::isGoneBlock);
    }

    static boolean isGoneBlock(Object block) {
        return block instanceof BlockEntity blockEntity && (blockEntity.isRemoved() || blockEntity.getLevel() == null);
    }

    private static boolean isAttached(IComputerAccess access) {
        try {
            access.getAttachmentName();
            return true;
        } catch (NotAttachedException e) {
            return false;
        }
    }

    private static void drop(Watch watch) {
        synchronized (InventoryWatch.class) {
            remove(watch);
        }
    }

    /** Caller holds the class lock. */
    private static void remove(Watch watch) {
        watch.dead = true;
        List<Watch> onBlock = BY_BLOCK.get(watch.block);
        if (onBlock != null) {
            onBlock.remove(watch);
            if (onBlock.isEmpty()) BY_BLOCK.remove(watch.block);
        }
        Set<Watch> mine = BY_COMPUTER.get(watch.computerId);
        if (mine != null) {
            mine.remove(watch);
            if (mine.isEmpty()) BY_COMPUTER.remove(watch.computerId);
        }
        active = !BY_BLOCK.isEmpty();
    }
}
