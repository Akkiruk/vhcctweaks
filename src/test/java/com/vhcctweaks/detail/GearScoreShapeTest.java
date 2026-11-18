package com.vhcctweaks.detail;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A piece's Gear Score in its item details, and the labels on heal pairs. */
class GearScoreShapeTest {

    private static Map<String, Object> modifier(String name) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        return map;
    }

    private static Map<String, Object> gear() {
        Map<String, Object> gear = new HashMap<>();
        gear.put("implicits", new ArrayList<>(List.of(modifier("Armor"))));
        gear.put("prefixes", new ArrayList<>(List.of(modifier("Health"), modifier("Heal on Kill"))));
        gear.put("suffixes", new ArrayList<>(List.of(modifier("Leech"))));
        return gear;
    }

    private static final List<GearScoreShape.Line> LINES = List.of(
            new GearScoreShape.Line("IMPLICIT", 0.8, 100.0, 20.0),
            new GearScoreShape.Line("PREFIX", 1.5, 30.0, 4.0),
            new GearScoreShape.Line("PREFIX", 1.0, null, null),
            new GearScoreShape.Line("SUFFIX", 0.5, 0.05, 0.01));

    @SuppressWarnings("unchecked")
    private static Map<String, Object> at(Map<String, Object> gear, String key, int index) {
        return ((List<Map<String, Object>>) gear.get(key)).get(index);
    }

    @Test
    void aCountedPieceGetsItsScoreAndLineCounts() {
        Map<String, Object> gear = gear();
        GearScoreShape.put(gear, new GearScoreShape.Piece("COUNTED", 633.4, 6, 1000.0, LINES), true);
        assertEquals("COUNTED", gear.get("gearScoreStatus"));
        assertEquals(633, gear.get("gearScore"));
        assertEquals(633.4, gear.get("gearScoreExact"));
        Map<?, ?> counts = (Map<?, ?>) gear.get("gearScoreLines");
        assertEquals(6, counts.get("expected"));
        assertEquals(4, counts.get("actual"));
    }

    @Test
    void eachModifierGetsItsShareInItsOwnList() {
        Map<String, Object> gear = gear();
        GearScoreShape.put(gear, new GearScoreShape.Piece("COUNTED", 633.4, 6, 1000.0, LINES), true);

        Map<String, Object> armor = at(gear, "implicits", 0);
        assertEquals(0.8, armor.get("scoreRatio"));
        assertEquals(1000.0 * 0.8 / 6, (double) armor.get("scorePoints"), 1e-9);
        assertEquals(100.0, armor.get("naturalBest"));
        assertEquals(20.0, armor.get("naturalWorst"));

        assertEquals(1.5, at(gear, "prefixes", 0).get("scoreRatio"));
        Map<String, Object> heal = at(gear, "prefixes", 1);
        assertEquals(1.0, heal.get("scoreRatio"));
        assertFalse(heal.containsKey("naturalBest"));
        assertEquals(0.5, at(gear, "suffixes", 0).get("scoreRatio"));

        double points = 0;
        for (String key : List.of("implicits", "prefixes", "suffixes")) {
            for (Object m : (List<?>) gear.get(key)) points += (double) ((Map<?, ?>) m).get("scorePoints");
        }
        assertEquals(1000.0 * (0.8 + 1.5 + 1.0 + 0.5) / 6, points, 1e-9);
    }

    @Test
    void morePiecesThanExpectedDivideByTheActualCount() {
        Map<String, Object> gear = gear();
        GearScoreShape.put(gear, new GearScoreShape.Piece("COUNTED", 950.0, 3, 1000.0, LINES), true);
        assertEquals(1000.0 * 0.8 / 4, (double) at(gear, "implicits", 0).get("scorePoints"), 1e-9);
    }

    @Test
    void withLinesOffTheModifiersAreLeftAlone() {
        Map<String, Object> gear = gear();
        GearScoreShape.put(gear, new GearScoreShape.Piece("COUNTED", 633.4, 6, 1000.0, LINES), false);
        assertEquals(633, gear.get("gearScore"));
        assertFalse(at(gear, "implicits", 0).containsKey("scoreRatio"));
    }

    @Test
    void aPieceThatDoesNotCountGetsOnlyItsStatus() {
        Map<String, Object> gear = gear();
        GearScoreShape.put(gear, new GearScoreShape.Piece("INERT", 0.0, 0, 1000.0, List.of()), true);
        assertEquals("INERT", gear.get("gearScoreStatus"));
        assertFalse(gear.containsKey("gearScore"));
        assertFalse(gear.containsKey("gearScoreLines"));
    }

    @Test
    void nothingToSayAddsNothing() {
        Map<String, Object> gear = gear();
        GearScoreShape.put(gear, null, true);
        GearScoreShape.put(gear, new GearScoreShape.Piece(null, 1.0, 1, 1000.0, List.of()), true);
        assertFalse(gear.containsKey("gearScoreStatus"));
        GearScoreShape.put(gear, new GearScoreShape.Piece("COUNTED", Double.NaN, 1, 1000.0, List.of()), true);
        assertFalse(gear.containsKey("gearScore"));
    }

    @Test
    void linesThatDoNotMatchTheModifierListsAreSkipped() {
        Map<String, Object> gear = new HashMap<>();
        gear.put("implicits", new ArrayList<>());
        gear.put("prefixes", "not a list");
        List<GearScoreShape.Line> lines = List.of(
                new GearScoreShape.Line("IMPLICIT", 1.0, null, null),
                new GearScoreShape.Line("PREFIX", 1.0, null, null),
                new GearScoreShape.Line("SUFFIX", 1.0, null, null),
                new GearScoreShape.Line("SOMETHING_NEW", 1.0, null, null));
        GearScoreShape.put(gear, new GearScoreShape.Piece("COUNTED", 500.0, 4, 1000.0, lines), true);
        assertEquals(500, gear.get("gearScore"));
        assertTrue(((List<?>) gear.get("implicits")).isEmpty());
    }

    // ===== Heal pairs =====

    @Test
    void healPairsSayWhichNumberIsTheChance() {
        Map<String, Object> kill = PairLabels.pair("the_vault:on_kill_heal", 0.25f, 3.0f);
        assertEquals(0.25f, kill.get("first"));
        assertEquals(3.0f, kill.get("second"));
        assertEquals(0.25f, kill.get("chance"));
        assertEquals(3.0f, kill.get("amount"));
        assertEquals(0.1f, PairLabels.pair("the_vault:block_heal", 0.1f, 2.0f).get("chance"));
    }

    @Test
    void otherPairsKeepOnlyFirstAndSecond() {
        Map<String, Object> other = PairLabels.pair("the_vault:something_else", 1, 2);
        assertEquals(2, other.size());
        assertEquals(1, other.get("first"));
        assertEquals(2, PairLabels.pair(null, 1, 2).size());
    }
}
