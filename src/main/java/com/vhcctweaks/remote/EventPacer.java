package com.vhcctweaks.remote;

import com.vhcctweaks.util.Safety;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.shared.computer.core.ServerComputer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Keeps the mod's own bursts of events small. CC holds at most 256 events per computer and drops the rest, including
 * the task_complete a main-thread call waits for, which would hang that call. So:
 *
 * - /vhcc pc type sends at most 32 char events per computer per tick.
 * - A link sends at most 64 peripheral or peripheral_detach events per linking computer per tick.
 *
 * Up to the limit an event goes out at once, as before. Past it, events wait in order and go out at the end of each
 * following server tick, up to the limit again. Nothing is dropped unless the computer itself goes away.
 */
public final class EventPacer {

    public static final int TYPE_CHARS_PER_TICK = 32;
    public static final int LINK_EVENTS_PER_TICK = 64;

    private static final class Lane {
        final int perTick;
        final BiConsumer<String, Object[]> send;
        final Deque<Object[]> waiting = new ArrayDeque<>();
        int sentThisTick;

        Lane(int perTick, BiConsumer<String, Object[]> send) {
            this.perTick = perTick;
            this.send = send;
        }
    }

    private static final Map<Object, Lane> LANES = new ConcurrentHashMap<>();

    private EventPacer() {}

    public static void reset() {
        LANES.clear();
    }

    /** Types text into a computer, one char event per character. */
    public static void typeText(ServerComputer computer, String text) {
        text.codePoints().forEach(cp -> offer(computer, TYPE_CHARS_PER_TICK, computer::queueEvent,
                "char", new Object[]{new String(Character.toChars(cp))}));
    }

    /** A link's peripheral or peripheral_detach event for the linking computer. */
    public static void linkEvent(IComputerAccess host, String event, String name) {
        offer(host, LINK_EVENTS_PER_TICK, host::queueEvent, event, new Object[]{name});
    }

    /** How many events wait for this key, for tests and reports. */
    static int waiting(Object key) {
        Lane lane = LANES.get(key);
        if (lane == null) return 0;
        synchronized (lane) {
            return lane.waiting.size();
        }
    }

    static void offer(Object key, int perTick, BiConsumer<String, Object[]> send, String event, Object[] args) {
        Lane lane = LANES.computeIfAbsent(key, k -> new Lane(perTick, send));
        synchronized (lane) {
            if (lane.waiting.isEmpty() && lane.sentThisTick < lane.perTick) {
                lane.sentThisTick++;
                deliver(lane, event, args);
            } else {
                lane.waiting.add(new Object[]{event, args});
            }
        }
    }

    /** End of a tick: each lane's count starts again, and waiting events go out up to the limit. */
    public static void endTick() {
        if (LANES.isEmpty()) return;
        for (Iterator<Lane> it = LANES.values().iterator(); it.hasNext(); ) {
            Lane lane = it.next();
            synchronized (lane) {
                if (lane.waiting.isEmpty() && lane.sentThisTick == 0) {
                    it.remove();
                    continue;
                }
                lane.sentThisTick = 0;
                while (!lane.waiting.isEmpty() && lane.sentThisTick < lane.perTick) {
                    Object[] next = lane.waiting.poll();
                    lane.sentThisTick++;
                    deliver(lane, (String) next[0], (Object[]) next[1]);
                }
            }
        }
    }

    /** A computer that has gone away takes its waiting events with it. */
    private static void deliver(Lane lane, String event, Object[] args) {
        try {
            lane.send.accept(event, args);
        } catch (RuntimeException e) {
            lane.waiting.clear();
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || LANES.isEmpty()) return;
        Safety.guard("event pacer tick", EventPacer::endTick);
    }
}
