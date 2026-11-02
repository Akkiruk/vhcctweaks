package com.vhcctweaks.inventory;

import dan200.computercraft.api.lua.LuaException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveBatchTest {

    /** A Lua list as CC hands it over: numeric keys are doubles. */
    private static Map<Object, Object> list(Object... items) {
        Map<Object, Object> map = new HashMap<>();
        for (int i = 0; i < items.length; i++) map.put((double) (i + 1), items[i]);
        return map;
    }

    private static Map<Object, Object> named(Object... pairs) {
        Map<Object, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }

    @Test
    void readsNamedMoves() throws LuaException {
        List<MoveBatch.Move> moves = MoveBatch.parse(list(
                named("fromSlot", 3.0, "to", "5:left"),
                named("fromSlot", 4.0, "to", "right", "toSlot", 2.0, "limit", 16.0)), 256);
        assertEquals(new MoveBatch.Move(3, "5:left", null, null), moves.get(0));
        assertEquals(new MoveBatch.Move(4, "right", 2, 16), moves.get(1));
    }

    @Test
    void readsPositionalMovesInTheSpecOrder() throws LuaException {
        List<MoveBatch.Move> moves = MoveBatch.parse(list(list(3.0, "top"), list(1.0, "top", 9.0, 1.0)), 256);
        assertEquals(new MoveBatch.Move(3, "top", null, null), moves.get(0));
        assertEquals(new MoveBatch.Move(1, "top", 9, 1), moves.get(1));
    }

    @Test
    void anEmptyListIsNoMoves() throws LuaException {
        assertTrue(MoveBatch.parse(new HashMap<>(), 256).isEmpty());
    }

    @Test
    void aBatchOverTheLimitRaises() {
        Object[] moves = new Object[257];
        for (int i = 0; i < moves.length; i++) moves[i] = named("fromSlot", 1.0, "to", "top");
        LuaException e = assertThrows(LuaException.class, () -> MoveBatch.parse(list(moves), 256));
        assertEquals("too many moves (max 256)", e.getMessage());
    }

    @Test
    void exactlyTheLimitIsFine() throws LuaException {
        Object[] moves = new Object[256];
        for (int i = 0; i < moves.length; i++) moves[i] = named("fromSlot", 1.0, "to", "top");
        assertEquals(256, MoveBatch.parse(list(moves), 256).size());
    }

    @Test
    void badMovesRaiseWithTheirIndex() {
        assertEquals("bad move #1 (table expected)",
                assertThrows(LuaException.class, () -> MoveBatch.parse(list("nope"), 256)).getMessage());
        assertEquals("bad move #2 (to expected string)",
                assertThrows(LuaException.class, () -> MoveBatch.parse(list(named("fromSlot", 1.0, "to", "a"), named("fromSlot", 1.0)), 256)).getMessage());
        assertEquals("bad move #1 (fromSlot expected number)",
                assertThrows(LuaException.class, () -> MoveBatch.parse(list(named("to", "a")), 256)).getMessage());
        assertEquals("bad move #1 (limit expected number)",
                assertThrows(LuaException.class, () -> MoveBatch.parse(list(named("fromSlot", 1.0, "to", "a", "limit", "x")), 256)).getMessage());
        assertEquals("bad move #1 (fromSlot expected number)",
                assertThrows(LuaException.class, () -> MoveBatch.parse(list(named("fromSlot", Double.NaN, "to", "a")), 256)).getMessage());
    }

    @Test
    void runsEveryMoveWithinBudget() throws LuaException {
        AtomicLong clock = new AtomicLong();
        MoveBatch.Outcome outcome = MoveBatch.run(4, 0, 2_000_000, clock::get, i -> {
            clock.addAndGet(100_000);
            return i * 10;
        });
        assertEquals(4, outcome.done());
        assertArrayEquals(new int[]{0, 10, 20, 30}, outcome.moved());
    }

    @Test
    void stopsOnceTheBudgetIsPassed() throws LuaException {
        AtomicLong clock = new AtomicLong();
        List<Integer> attempted = new ArrayList<>();
        MoveBatch.Outcome outcome = MoveBatch.run(10, 0, 2_000_000, clock::get, i -> {
            attempted.add(i);
            clock.addAndGet(900_000);
            return 1;
        });
        // 0.9, 1.8 are within 2 ms; the third move ends at 2.7 ms, past the budget
        assertEquals(3, outcome.done());
        assertEquals(List.of(0, 1, 2), attempted);
    }

    @Test
    void alwaysAttemptsAtLeastOneMove() throws LuaException {
        AtomicLong clock = new AtomicLong(10_000_000);
        MoveBatch.Outcome outcome = MoveBatch.run(5, 0, 2_000_000, clock::get, i -> 7);
        assertEquals(1, outcome.done());
        assertEquals(7, outcome.moved()[0]);
    }

    @Test
    void theResultIsMovedAndDone() throws LuaException {
        MoveBatch.Outcome outcome = MoveBatch.run(2, 0, Long.MAX_VALUE, () -> 0, i -> i + 5);
        Map<String, Object> lua = outcome.toLua();
        assertEquals(2, lua.get("done"));
        assertEquals(Map.of(1, 5, 2, 6), lua.get("moved"));
    }

    @Test
    void aMoveErrorStopsTheBatch() {
        LuaException e = assertThrows(LuaException.class, () -> MoveBatch.run(3, 0, Long.MAX_VALUE, () -> 0, i -> {
            if (i == 1) throw new LuaException("boom");
            return 1;
        }));
        assertEquals("boom", e.getMessage());
    }

    @Test
    void optionalFieldsMayBeMissing() throws LuaException {
        MoveBatch.Move move = MoveBatch.parse(list(named("fromSlot", 2.0, "to", "x")), 1).get(0);
        assertNull(move.toSlot());
        assertNull(move.limit());
    }
}
