package com.vhcctweaks.ccvault;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The table ccvault.getPlayerGearPower returns. */
class GearPowerShapeTest {

    private static final List<Double> SCORES = List.of(900.0, 850.5, 0.0, 1000.0, 1200.0, 640.0);

    private static Map<String, Object> shown(List<GearPowerShape.Slot> slots) {
        return GearPowerShape.online(true, SCORES, 765.083, 765, 1339, 1.75, 2.0, 1.2, 1.5, 104, 100, 2, 1400, slots);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> table(Object value) {
        return (Map<String, Object>) value;
    }

    @Test
    void anOnlinePlayerGetsTheScoresTheMultipliersAndThePeak() {
        Map<String, Object> out = shown(null);
        assertEquals(true, out.get("online"));
        assertEquals(true, out.get("shown"));
        assertEquals(765, out.get("gearScore"));
        assertEquals(765.083, out.get("gearScoreExact"));
        assertEquals(1339, out.get("gearPower"));
        assertEquals(1.75, out.get("temper"));
        assertEquals(2.0, out.get("might"));
        assertEquals(1.2, out.get("magic"));
        assertEquals(1.5, out.get("vigor"));
        assertEquals(104, out.get("level"));
        assertEquals(100, out.get("minLevel"));
        assertEquals(2, out.get("greedTier"));
        assertEquals(1400, out.get("peakGearPower"));
    }

    @Test
    void theSixSlotScoresAreNamedInVaultHuntersOrder() {
        Map<String, Object> slots = table(shown(null).get("slots"));
        assertEquals(6, slots.size());
        assertEquals(900.0, slots.get("head"));
        assertEquals(850.5, slots.get("chest"));
        assertEquals(0.0, slots.get("legs"));
        assertEquals(1000.0, slots.get("feet"));
        assertEquals(1200.0, slots.get("weapon"));
        assertEquals(640.0, slots.get("offhand"));
        assertFalse(shown(null).containsKey("slotDetails"));
    }

    @Test
    void theWeaponSlotSaysWhichHotbarWeaponCounts() {
        List<GearPowerShape.Slot> slots = Arrays.asList(
                new GearPowerShape.Slot("COUNTED", 900.0, "the_vault:helmet", "Helm", null, 0),
                null,
                new GearPowerShape.Slot("EMPTY", 0.0, null, null, null, 0),
                new GearPowerShape.Slot("INERT", 0.0, "the_vault:boots", "Boots", null, 0),
                new GearPowerShape.Slot("COUNTED", 1200.0, "the_vault:sword", "Blade", 3, 2),
                new GearPowerShape.Slot("GREED_TIER_LOCKED", 0.0, "the_vault:shield", "Wall", null, 0));
        Map<String, Object> details = table(shown(slots).get("slotDetails"));
        assertEquals(5, details.size());
        assertFalse(details.containsKey("chest"));

        Map<String, Object> weapon = table(details.get("weapon"));
        assertEquals("COUNTED", weapon.get("status"));
        assertEquals(1200.0, weapon.get("score"));
        assertEquals("the_vault:sword", weapon.get("item"));
        assertEquals("Blade", weapon.get("name"));
        assertEquals(3, weapon.get("hotbarSlot"));
        assertEquals(2, weapon.get("hotbarWeapons"));

        Map<String, Object> head = table(details.get("head"));
        assertFalse(head.containsKey("hotbarSlot"));
        assertFalse(head.containsKey("hotbarWeapons"));

        Map<String, Object> legs = table(details.get("legs"));
        assertEquals("EMPTY", legs.get("status"));
        assertFalse(legs.containsKey("item"));
        assertEquals("GREED_TIER_LOCKED", table(details.get("offhand")).get("status"));
    }

    @Test
    void belowTheStartingLevelNoScoresAreGiven() {
        Map<String, Object> out = GearPowerShape.online(false, List.of(), 0.0, 0, 0, 1.0, 1.0, 1.0, 1.0, 62, 100, 0, 0, null);
        assertEquals(true, out.get("online"));
        assertEquals(false, out.get("shown"));
        assertEquals(62, out.get("level"));
        assertEquals(100, out.get("minLevel"));
        assertFalse(out.containsKey("gearScore"));
        assertFalse(out.containsKey("gearPower"));
        assertFalse(out.containsKey("slots"));
    }

    @Test
    void whatVaultHuntersCouldNotGiveIsLeftOut() {
        Map<String, Object> out = GearPowerShape.online(true, List.of(1.0, 2.0), 0.5, 1, 1, 1.0, 1.0, 1.0, 1.0,
                null, null, null, null, null);
        assertFalse(out.containsKey("level"));
        assertFalse(out.containsKey("minLevel"));
        assertFalse(out.containsKey("greedTier"));
        assertFalse(out.containsKey("peakGearPower"));
        assertEquals(2, table(out.get("slots")).size());

        assertTrue(table(GearPowerShape.online(true, null, 0.5, 1, 1, 1.0, 1.0, 1.0, 1.0,
                null, null, null, null, null).get("slots")).isEmpty());
    }

    @Test
    void anOfflinePlayerGetsThePeakAndSaysSo() {
        Map<String, Object> out = GearPowerShape.offline(104, 1400);
        assertEquals(false, out.get("online"));
        assertEquals(false, out.get("shown"));
        assertEquals(104, out.get("level"));
        assertEquals(1400, out.get("peakGearPower"));
        assertNull(out.get("gearScore"));

        Map<String, Object> bare = GearPowerShape.offline(null, null);
        assertEquals(0, bare.get("peakGearPower"));
        assertFalse(bare.containsKey("level"));
    }
}
