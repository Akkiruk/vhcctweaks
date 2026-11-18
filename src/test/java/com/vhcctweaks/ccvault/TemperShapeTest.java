package com.vhcctweaks.ccvault;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The table ccvault.getPlayerTemper returns, the saved Temper it is built from offline, and ccvault.getLimits. */
class TemperShapeTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> table(Object value) {
        return (Map<String, Object>) value;
    }

    @Test
    void anOnlinePlayerGetsDustAndEveryCategory() {
        Map<String, Object> out = TemperShape.build(true, 12345L, new int[]{40, 10, 25, 3},
                new double[]{2.0, 1.25, 1.6, 1.1}, new double[]{2.5, 1.3, 1.8, 1.05}, 1400, 2, 17, 1);
        assertEquals(true, out.get("online"));
        assertEquals(12345L, out.get("dust"));
        assertEquals(40, table(out.get("might")).get("level"));
        assertEquals(2.0, table(out.get("might")).get("multiplier"));
        assertEquals(2.5, table(out.get("might")).get("power"));
        assertEquals(10, table(out.get("magic")).get("level"));
        assertEquals(25, table(out.get("vigor")).get("level"));
        assertEquals(3, table(out.get("tool")).get("level"));
        assertEquals(1.1, table(out.get("tool")).get("multiplier"));
        assertEquals(1400, out.get("peakGearPower"));
        assertEquals(2, out.get("bossesBeaten"));
        assertEquals(17, out.get("trialsRecord"));
        assertEquals(1, out.get("favorsUsed"));
    }

    @Test
    void theGearPowerMultiplierIsTheBetterOfMightAndMagicAveragedWithVigor() {
        assertEquals(1.8, TemperShape.gearPowerMultiplier(2.0, 1.25, 1.6), 1e-9);
        assertEquals(2.3, TemperShape.gearPowerMultiplier(1.0, 3.0, 1.6), 1e-9);
        assertEquals(1.0, TemperShape.gearPowerMultiplier(1.0, 1.0, 1.0), 1e-9);
        Map<String, Object> out = TemperShape.build(true, 0L, new int[4], new double[]{2.0, 1.25, 1.6, 9.0}, null,
                null, null, null, null);
        assertEquals(1.8, (double) out.get("gearPowerMultiplier"), 1e-9);
    }

    @Test
    void withoutTheLadderOnlyLevelsAreGiven() {
        Map<String, Object> out = TemperShape.build(false, 7L, new int[]{1, 2, 3, 4}, null, null, 50, null, 4, null);
        assertEquals(false, out.get("online"));
        assertEquals(2, table(out.get("magic")).get("level"));
        assertFalse(table(out.get("magic")).containsKey("multiplier"));
        assertFalse(table(out.get("magic")).containsKey("power"));
        assertFalse(out.containsKey("gearPowerMultiplier"));
        assertFalse(out.containsKey("bossesBeaten"));
        assertFalse(out.containsKey("favorsUsed"));
        assertEquals(50, out.get("peakGearPower"));
        assertEquals(4, out.get("trialsRecord"));
    }

    @Test
    void missingLevelsCountAsZero() {
        Map<String, Object> out = TemperShape.build(true, 0L, null, null, null, null, null, null, null);
        for (String category : TemperShape.CATEGORIES) assertEquals(0, table(out.get(category)).get("level"));
    }

    // ===== The saved profile =====

    private static CompoundTag category(int level) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("level", level);
        tag.putLong("spent", level * 100L);
        return tag;
    }

    private static CompoundTag profile(CompoundTag temper) {
        CompoundTag section = new CompoundTag();
        section.put("temper", temper);
        CompoundTag stats = new CompoundTag();
        stats.putInt("vaultLevel", 104);
        CompoundTag profile = new CompoundTag();
        profile.put("temper", section);
        profile.put("vault_stats", stats);
        return profile;
    }

    private static CompoundTag savedTemper(String toolId) {
        CompoundTag categories = new CompoundTag();
        categories.put("might", category(40));
        categories.put("magic", category(10));
        categories.put("vigor", category(25));
        categories.put(toolId, category(3));
        CompoundTag gearPower = new CompoundTag();
        gearPower.putInt("peak", 1400);
        CompoundTag trials = new CompoundTag();
        trials.putInt("record", 17);
        CompoundTag temper = new CompoundTag();
        temper.putInt("v", 2);
        temper.putLong("dust", 12345L);
        temper.put("categories", categories);
        temper.put("gear_power", gearPower);
        temper.put("trials", trials);
        return temper;
    }

    @Test
    void theSavedTemperIsRead() {
        SavedTemper saved = SavedTemper.in(profile(savedTemper("tool")));
        assertNotNull(saved);
        assertEquals(12345L, saved.dust());
        assertArrayEquals(new int[]{40, 10, 25, 3}, saved.levels());
        assertEquals(1400, saved.peak());
        assertEquals(17, saved.trialsRecord());
    }

    @Test
    void anOlderSaveCallsTheToolCategoryUtility() {
        assertArrayEquals(new int[]{40, 10, 25, 3}, SavedTemper.in(profile(savedTemper("utility"))).levels());
    }

    @Test
    void aProfileWithoutTemperHasNone() {
        assertNull(SavedTemper.in(null));
        assertNull(SavedTemper.in(new CompoundTag()));
        CompoundTag emptySection = new CompoundTag();
        emptySection.put("temper", new CompoundTag());
        assertNull(SavedTemper.in(emptySection));
        CompoundTag wrongType = new CompoundTag();
        wrongType.putString("temper", "not a compound");
        assertNull(SavedTemper.in(wrongType));
    }

    @Test
    void missingAndNegativeNumbersCountAsZero() {
        CompoundTag temper = new CompoundTag();
        temper.putLong("dust", -5L);
        SavedTemper saved = SavedTemper.in(profile(temper));
        assertEquals(0L, saved.dust());
        assertArrayEquals(new int[]{0, 0, 0, 0}, saved.levels());
        assertEquals(0, saved.peak());
        assertEquals(0, saved.trialsRecord());
    }

    @Test
    void theLevelAndTheTemperAreReadFromOneProfile() {
        VaultLevels.SavedProfile saved = VaultLevels.profileIn(profile(savedTemper("tool")));
        assertEquals(104, saved.level());
        assertEquals(1400, saved.temper().peak());

        VaultLevels.SavedProfile bare = VaultLevels.profileIn(new CompoundTag());
        assertNull(bare.level());
        assertNull(bare.temper());
    }

    // ===== ccvault.getLimits =====

    @Test
    void limitsLeaveOutWhatCannotBeRead() {
        Map<String, Object> limits = new LimitsShape()
                .put("maxBatchMoves", () -> 256)
                .put("maxTransferAmount", () -> 1_000_000L)
                .put("notLoaded", () -> { throw new IllegalStateException("config not loaded"); })
                .put("absent", () -> null)
                .build();
        assertEquals(2, limits.size());
        assertEquals(256, limits.get("maxBatchMoves"));
        assertEquals(1_000_000L, limits.get("maxTransferAmount"));
    }
}
