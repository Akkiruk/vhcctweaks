package com.vhcctweaks.inventory;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IWorkMonitor;
import dan200.computercraft.api.peripheral.NotAttachedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryWatchTest {

    /** A computer's attachment of one peripheral, recording the events it is sent. */
    static final class FakeAccess implements IComputerAccess {
        final int id;
        final String name;
        boolean attached = true;
        final List<String> events = new ArrayList<>();

        FakeAccess(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String mount(@Nonnull String desiredLocation, @Nonnull IMount mount, @Nonnull String driveName) {
            return null;
        }

        @Override
        public String mountWritable(@Nonnull String desiredLocation, @Nonnull IWritableMount mount, @Nonnull String driveName) {
            return null;
        }

        @Override
        public void unmount(String location) {}

        @Override
        public int getID() {
            return id;
        }

        @Override
        public void queueEvent(@Nonnull String event, Object... arguments) {
            if (!attached) throw new NotAttachedException();
            events.add(event + ":" + arguments[0]);
        }

        @Nonnull
        @Override
        public String getAttachmentName() {
            if (!attached) throw new NotAttachedException();
            return name;
        }

        @Nonnull
        @Override
        public Map<String, IPeripheral> getAvailablePeripherals() {
            return Map.of();
        }

        @Override
        public IPeripheral getAvailablePeripheral(@Nonnull String name) {
            return null;
        }

        @Nonnull
        @Override
        public IWorkMonitor getMainThreadMonitor() {
            throw new UnsupportedOperationException();
        }
    }

    private final Object chest = new Object();
    private final Object barrel = new Object();
    private final Set<Object> gone = new HashSet<>();
    private final Predicate<Object> isGone = gone::contains;

    @BeforeEach
    void reset() {
        InventoryWatch.reset();
    }

    @Test
    void nothingWatchedMeansInactive() {
        assertFalse(InventoryWatch.active);
        InventoryWatch.onChanged(chest);
        assertEquals(0, InventoryWatch.flush(isGone));
    }

    @Test
    void manyChangesInOneTickSendOneEvent() throws LuaException {
        FakeAccess left = new FakeAccess(1, "left");
        InventoryWatch.watch(chest, left, 1, 64, isGone);
        assertTrue(InventoryWatch.active);

        for (int i = 0; i < 50; i++) InventoryWatch.onChanged(chest);
        assertEquals(1, InventoryWatch.flush(isGone));
        assertEquals(List.of("inventory_changed:left"), left.events);

        // Nothing changed in the next tick: no event
        assertEquals(0, InventoryWatch.flush(isGone));

        // A change in a later tick is a new event
        InventoryWatch.onChanged(chest);
        assertEquals(1, InventoryWatch.flush(isGone));
        assertEquals(2, left.events.size());
    }

    @Test
    void everyWatchingComputerGetsItsOwnNameForIt() throws LuaException {
        FakeAccess side = new FakeAccess(1, "left");
        FakeAccess remote = new FakeAccess(2, "1:left");
        InventoryWatch.watch(chest, side, 1, 64, isGone);
        InventoryWatch.watch(chest, remote, 2, 64, isGone);

        InventoryWatch.onChanged(chest);
        InventoryWatch.onChanged(chest);
        assertEquals(2, InventoryWatch.flush(isGone));
        assertEquals(List.of("inventory_changed:left"), side.events);
        assertEquals(List.of("inventory_changed:1:left"), remote.events);
    }

    @Test
    void oneComputerGetsAtMost32EventsATickAndTheRestNextTick() throws LuaException {
        List<FakeAccess> accesses = new ArrayList<>();
        List<Object> blocks = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            Object block = new Object();
            FakeAccess access = new FakeAccess(1, "chest_" + i);
            InventoryWatch.watch(block, access, 1, 64, isGone);
            blocks.add(block);
            accesses.add(access);
        }
        FakeAccess otherComputer = new FakeAccess(2, "1:chest_0");
        InventoryWatch.watch(blocks.get(0), otherComputer, 2, 64, isGone);

        for (Object block : blocks) InventoryWatch.onChanged(block);
        assertEquals(33, InventoryWatch.flush(isGone));
        assertEquals(1, otherComputer.events.size());
        assertEquals(32, accesses.stream().mapToInt(a -> a.events.size()).sum());

        // The other 8 next tick, one each
        assertEquals(8, InventoryWatch.flush(isGone));
        assertEquals(40, accesses.stream().mapToInt(a -> a.events.size()).sum());
        assertTrue(accesses.stream().allMatch(a -> a.events.size() == 1));
    }

    @Test
    void aDeferredWatchThatChangesAgainStillSendsOnce() throws LuaException {
        List<Object> blocks = new ArrayList<>();
        List<FakeAccess> accesses = new ArrayList<>();
        for (int i = 0; i < 33; i++) {
            Object block = new Object();
            FakeAccess access = new FakeAccess(1, "chest_" + i);
            InventoryWatch.watch(block, access, 1, 64, isGone);
            blocks.add(block);
            accesses.add(access);
        }
        for (Object block : blocks) InventoryWatch.onChanged(block);
        assertEquals(32, InventoryWatch.flush(isGone));
        InventoryWatch.onChanged(blocks.get(32));
        assertEquals(1, InventoryWatch.flush(isGone));
        assertEquals(1, accesses.get(32).events.size());
    }

    @Test
    void anotherBlocksChangeSendsNothing() throws LuaException {
        FakeAccess left = new FakeAccess(1, "left");
        InventoryWatch.watch(chest, left, 1, 64, isGone);
        InventoryWatch.onChanged(barrel);
        assertEquals(0, InventoryWatch.flush(isGone));
    }

    @Test
    void watchingTwiceIsOneWatch() throws LuaException {
        FakeAccess left = new FakeAccess(1, "left");
        InventoryWatch.watch(chest, left, 1, 1, isGone);
        InventoryWatch.watch(chest, left, 1, 1, isGone);
        assertEquals(1, InventoryWatch.count(1));
    }

    @Test
    void theLimitIsPerComputer() throws LuaException {
        InventoryWatch.watch(chest, new FakeAccess(1, "left"), 1, 1, isGone);
        LuaException e = assertThrows(LuaException.class,
                () -> InventoryWatch.watch(barrel, new FakeAccess(1, "right"), 1, 1, isGone));
        assertEquals("too many watches (max 1)", e.getMessage());
        InventoryWatch.watch(barrel, new FakeAccess(2, "right"), 2, 1, isGone);
    }

    @Test
    void deadWatchesDoNotCountAgainstTheLimit() throws LuaException {
        FakeAccess detached = new FakeAccess(1, "left");
        InventoryWatch.watch(chest, detached, 1, 1, isGone);
        detached.attached = false;
        InventoryWatch.watch(barrel, new FakeAccess(1, "right"), 1, 1, isGone);
        assertEquals(1, InventoryWatch.count(1));
    }

    @Test
    void unwatchStopsEvents() throws LuaException {
        FakeAccess left = new FakeAccess(1, "left");
        InventoryWatch.watch(chest, left, 1, 64, isGone);
        assertTrue(InventoryWatch.unwatch(chest, left));
        assertFalse(InventoryWatch.unwatch(chest, left));
        assertFalse(InventoryWatch.active);
        InventoryWatch.onChanged(chest);
        assertEquals(0, InventoryWatch.flush(isGone));
    }

    @Test
    void unwatchAfterAChangeInTheSameTickSendsNothing() throws LuaException {
        FakeAccess left = new FakeAccess(1, "left");
        InventoryWatch.watch(chest, left, 1, 64, isGone);
        InventoryWatch.onChanged(chest);
        InventoryWatch.unwatch(chest, left);
        assertEquals(0, InventoryWatch.flush(isGone));
        assertTrue(left.events.isEmpty());
    }

    @Test
    void aRebootEndsThatComputersWatchesOnly() throws LuaException {
        FakeAccess mine = new FakeAccess(1, "left");
        FakeAccess theirs = new FakeAccess(2, "1:left");
        InventoryWatch.watch(chest, mine, 1, 64, isGone);
        InventoryWatch.watch(chest, theirs, 2, 64, isGone);
        InventoryWatch.clearComputer(1);
        InventoryWatch.onChanged(chest);
        assertEquals(1, InventoryWatch.flush(isGone));
        assertTrue(mine.events.isEmpty());
        assertEquals(1, theirs.events.size());
    }

    @Test
    void aRemovedBlockEndsItsWatches() throws LuaException {
        FakeAccess left = new FakeAccess(1, "left");
        InventoryWatch.watch(chest, left, 1, 64, isGone);
        InventoryWatch.onRemoved(chest);
        assertFalse(InventoryWatch.active);
        assertEquals(0, InventoryWatch.count(1));
    }

    @Test
    void aDetachedPeripheralEndsItsWatchInsteadOfSending() throws LuaException {
        FakeAccess left = new FakeAccess(1, "left");
        InventoryWatch.watch(chest, left, 1, 64, isGone);
        left.attached = false;
        InventoryWatch.onChanged(chest);
        assertEquals(0, InventoryWatch.flush(isGone));
        assertEquals(0, InventoryWatch.count(1));
        assertFalse(InventoryWatch.active);
    }

    @Test
    void aBlockFoundGoneAtSendTimeEndsItsWatch() throws LuaException {
        FakeAccess left = new FakeAccess(1, "left");
        InventoryWatch.watch(chest, left, 1, 64, isGone);
        InventoryWatch.onChanged(chest);
        gone.add(chest);
        assertEquals(0, InventoryWatch.flush(isGone));
        assertEquals(0, InventoryWatch.count(1));
    }
}
