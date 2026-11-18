package com.vhcctweaks.ccvault;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The table ccvault.getPlayerGearPower returns, built from numbers already read from Vault Hunters.
 * Kept free of the game so it can be tested on its own. Anything Vault Hunters could not give is left out.
 */
final class GearPowerShape {

    /** The slot order Vault Hunters uses (GearPower.COMBAT_SLOTS). Its main hand is the best weapon on the hotbar. */
    static final String[] SLOTS = {"head", "chest", "legs", "feet", "weapon", "offhand"};

    /**
     * One slot, as Vault Hunters explains it. hotbarSlot is 1 to 9 for the weapon that counts, null otherwise.
     * hotbarWeapons is how many weapons were on the hotbar to choose from.
     */
    record Slot(String status, Double score, String item, String name, Integer hotbarSlot, Integer hotbarWeapons) {}

    private GearPowerShape() {}

    /**
     * A player on this server.
     *
     * @param shown      false below the level Gear Power starts at: no scores are given then
     * @param slotScores in SLOTS order, or fewer when Vault Hunters gave fewer
     * @param slots      in SLOTS order, null when the explanation could not be read
     */
    static Map<String, Object> online(boolean shown, List<Double> slotScores, double gearScoreExact, int gearScore,
                                      int gearPower, double temper, double might, double magic, double vigor,
                                      Integer level, Integer minLevel, Integer greedTier, Integer peak, List<Slot> slots) {
        Map<String, Object> out = new HashMap<>();
        out.put("online", true);
        out.put("shown", shown);
        putIfKnown(out, "level", level);
        putIfKnown(out, "minLevel", minLevel);
        putIfKnown(out, "greedTier", greedTier);
        putIfKnown(out, "peakGearPower", peak);
        if (!shown) return out;

        out.put("gearScore", gearScore);
        out.put("gearScoreExact", gearScoreExact);
        out.put("gearPower", gearPower);
        out.put("temper", temper);
        out.put("might", might);
        out.put("magic", magic);
        out.put("vigor", vigor);

        Map<String, Object> scores = new HashMap<>();
        if (slotScores != null) {
            for (int i = 0; i < SLOTS.length && i < slotScores.size(); i++) {
                Double score = slotScores.get(i);
                if (score != null) scores.put(SLOTS[i], score);
            }
        }
        out.put("slots", scores);

        if (slots != null) {
            Map<String, Object> details = new HashMap<>();
            for (int i = 0; i < SLOTS.length && i < slots.size(); i++) {
                Slot slot = slots.get(i);
                if (slot != null) details.put(SLOTS[i], detail(slot));
            }
            out.put("slotDetails", details);
        }
        return out;
    }

    /** A player who is not on this server: only what Vault Hunters saved, which is the highest Gear Power reached. */
    static Map<String, Object> offline(Integer level, Integer peak) {
        Map<String, Object> out = new HashMap<>();
        out.put("online", false);
        out.put("shown", false);
        putIfKnown(out, "level", level);
        out.put("peakGearPower", peak == null ? 0 : peak);
        return out;
    }

    private static Map<String, Object> detail(Slot slot) {
        Map<String, Object> out = new HashMap<>();
        putIfKnown(out, "status", slot.status());
        putIfKnown(out, "score", slot.score());
        putIfKnown(out, "item", slot.item());
        putIfKnown(out, "name", slot.name());
        if (slot.hotbarSlot() != null) {
            out.put("hotbarSlot", slot.hotbarSlot());
            putIfKnown(out, "hotbarWeapons", slot.hotbarWeapons());
        }
        return out;
    }

    private static void putIfKnown(Map<String, Object> out, String key, Object value) {
        if (value != null) out.put(key, value);
    }
}
