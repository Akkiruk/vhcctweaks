package com.vhcctweaks.inventory;

import dan200.computercraft.api.lua.LuaException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * The parts of moveMany that need no game: reading the moves table and running a batch against a time budget.
 *
 * A move is { fromSlot = n, to = "name", toSlot = n, limit = n }. The same four may be given by position instead,
 * { n, "name", n, n }, in that order.
 */
public final class MoveBatch {

    public record Move(int fromSlot, String to, Integer toSlot, Integer limit) {}

    @FunctionalInterface
    public interface Step {
        int move(int index) throws LuaException;
    }

    /** How many items each attempted move moved, and how many moves were attempted. */
    public record Outcome(int[] moved, int done) {
        public Map<String, Object> toLua() {
            Map<Integer, Integer> counts = new HashMap<>();
            for (int i = 0; i < done; i++) counts.put(i + 1, moved[i]);
            Map<String, Object> result = new HashMap<>();
            result.put("moved", counts);
            result.put("done", done);
            return result;
        }
    }

    private MoveBatch() {}

    public static List<Move> parse(Map<?, ?> moves, int maxMoves) throws LuaException {
        int count = 0;
        while (moves.containsKey((double) (count + 1))) {
            count++;
            if (count > maxMoves) throw new LuaException("too many moves (max " + maxMoves + ")");
        }

        List<Move> parsed = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            if (!(moves.get((double) i) instanceof Map<?, ?> move)) {
                throw new LuaException("bad move #" + i + " (table expected)");
            }
            Object fromSlot = field(move, "fromSlot", 1);
            Object to = field(move, "to", 2);
            if (!(to instanceof String toName)) throw new LuaException("bad move #" + i + " (to expected string)");
            parsed.add(new Move(
                    integer(i, "fromSlot", fromSlot),
                    toName,
                    optInteger(i, "toSlot", field(move, "toSlot", 3)),
                    optInteger(i, "limit", field(move, "limit", 4))));
        }
        return parsed;
    }

    /**
     * Runs moves in order, always at least one, and stops once the time since start passes the budget.
     * A move that raises an error stops the batch with that error.
     */
    public static Outcome run(int count, long start, long budgetNanos, LongSupplier clock, Step step) throws LuaException {
        int[] moved = new int[count];
        int done = 0;
        while (done < count) {
            moved[done] = step.move(done);
            done++;
            if (done < count && clock.getAsLong() - start > budgetNanos) break;
        }
        return new Outcome(moved, done);
    }

    private static Object field(Map<?, ?> move, String name, int position) {
        Object value = move.get(name);
        return value != null ? value : move.get((double) position);
    }

    private static int integer(int index, String name, Object value) throws LuaException {
        if (!(value instanceof Number number)) throw new LuaException("bad move #" + index + " (" + name + " expected number)");
        double d = number.doubleValue();
        if (!Double.isFinite(d)) throw new LuaException("bad move #" + index + " (" + name + " expected number)");
        long whole = (long) d;
        return whole > Integer.MAX_VALUE ? Integer.MAX_VALUE : whole < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) whole;
    }

    private static Integer optInteger(int index, String name, Object value) throws LuaException {
        return value == null ? null : integer(index, name, value);
    }
}
