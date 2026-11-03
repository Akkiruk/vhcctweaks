package com.vhcctweaks.ccvault;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Reading a level from a saved profile, for a player who is known to this server but not on it. */
class VaultLevelsSavedTest {

    private static CompoundTag profile(int level) {
        CompoundTag stats = new CompoundTag();
        stats.putInt("vaultCompletions", 3);
        stats.putInt("vaultLevel", level);
        CompoundTag profile = new CompoundTag();
        profile.put("vault_stats", stats);
        profile.put("expertises", new CompoundTag());
        return profile;
    }

    @Test
    void theSavedLevelIsRead() {
        assertEquals(42, VaultLevels.levelIn(profile(42)));
        assertEquals(0, VaultLevels.levelIn(profile(0)));
    }

    @Test
    void aProfileWithoutVaultStatsHasNoLevel() {
        CompoundTag profile = new CompoundTag();
        profile.put("expertises", new CompoundTag());
        assertNull(VaultLevels.levelIn(profile));
        assertNull(VaultLevels.levelIn(null));
    }

    @Test
    void vaultStatsWithoutALevelHasNoLevel() {
        CompoundTag profile = new CompoundTag();
        profile.put("vault_stats", new CompoundTag());
        assertNull(VaultLevels.levelIn(profile));
    }

    @Test
    void aWrongTypeIsNoLevelRatherThanAnError() {
        CompoundTag profile = new CompoundTag();
        profile.putString("vault_stats", "not a compound");
        assertNull(VaultLevels.levelIn(profile));
    }
}
