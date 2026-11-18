package com.vhcctweaks.ccvault;

import java.util.HashMap;
import java.util.Map;

/**
 * The table ccvault.getPlayerTemper returns, built from numbers already read from Vault Hunters.
 * Kept free of the game so it can be tested on its own.
 */
final class TemperShape {

    /** The category order Vault Hunters uses (TemperCategory.ALL). Tool does not count for Gear Power. */
    static final String[] CATEGORIES = {"might", "magic", "vigor", "tool"};

    private TemperShape() {}

    /**
     * @param levels      per category, in CATEGORIES order
     * @param multipliers what each category multiplies Gear Power by, or null when the ladder could not be read
     * @param powers      what each category multiplies its in-vault effect by, or null when unknown
     */
    static Map<String, Object> build(boolean online, long dust, int[] levels, double[] multipliers, double[] powers,
                                     Integer peak, Integer bossesBeaten, Integer trialsRecord, Integer favorsUsed) {
        Map<String, Object> out = new HashMap<>();
        out.put("online", online);
        out.put("dust", dust);
        for (int i = 0; i < CATEGORIES.length; i++) {
            Map<String, Object> category = new HashMap<>();
            category.put("level", levels != null && i < levels.length ? levels[i] : 0);
            if (multipliers != null && i < multipliers.length) category.put("multiplier", multipliers[i]);
            if (powers != null && i < powers.length) category.put("power", powers[i]);
            out.put(CATEGORIES[i], category);
        }
        if (multipliers != null && multipliers.length >= 3) {
            out.put("gearPowerMultiplier", gearPowerMultiplier(multipliers[0], multipliers[1], multipliers[2]));
        }
        if (peak != null) out.put("peakGearPower", peak);
        if (bossesBeaten != null) out.put("bossesBeaten", bossesBeaten);
        if (trialsRecord != null) out.put("trialsRecord", trialsRecord);
        if (favorsUsed != null) out.put("favorsUsed", favorsUsed);
        return out;
    }

    /** The rule Vault Hunters uses: the better of Might and Magic, averaged with Vigor. */
    static double gearPowerMultiplier(double might, double magic, double vigor) {
        return (Math.max(might, magic) + vigor) / 2.0;
    }
}
