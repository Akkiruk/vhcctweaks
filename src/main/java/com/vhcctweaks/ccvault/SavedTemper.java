package com.vhcctweaks.ccvault;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * A player's Temper as Vault Hunters 3.21.74 saves it in playerdata/&lt;uuid&gt;.the_vault, section "temper":
 * { temper = { v = 2, dust, categories = { might|magic|vigor|tool = { level, spent } }, gear_power = { peak },
 * trials = { record } } }. Read for a player who is not on this server.
 *
 * @param levels in TemperShape.CATEGORIES order
 */
record SavedTemper(long dust, int[] levels, int peak, int trialsRecord) {

    private static final String SECTION = "temper";
    /** The name of the tool category in older saves. */
    private static final String LEGACY_TOOL = "utility";

    /** The saved Temper, or null when the profile has none. */
    static SavedTemper in(CompoundTag profile) {
        if (profile == null || !profile.contains(SECTION, Tag.TAG_COMPOUND)) return null;
        CompoundTag section = profile.getCompound(SECTION);
        if (!section.contains(SECTION, Tag.TAG_COMPOUND)) return null;
        CompoundTag temper = section.getCompound(SECTION);

        int[] levels = new int[TemperShape.CATEGORIES.length];
        CompoundTag categories = temper.getCompound("categories");
        for (int i = 0; i < levels.length; i++) {
            String id = TemperShape.CATEGORIES[i];
            CompoundTag category = categories.contains(id, Tag.TAG_COMPOUND) ? categories.getCompound(id)
                    : id.equals("tool") ? categories.getCompound(LEGACY_TOOL) : new CompoundTag();
            levels[i] = Math.max(0, category.getInt("level"));
        }
        return new SavedTemper(
                Math.max(0L, temper.getLong("dust")),
                levels,
                Math.max(0, temper.getCompound("gear_power").getInt("peak")),
                Math.max(0, temper.getCompound("trials").getInt("record")));
    }
}
