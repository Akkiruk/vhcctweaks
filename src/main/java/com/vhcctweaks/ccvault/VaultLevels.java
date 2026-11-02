package com.vhcctweaks.ccvault;

import com.vhcctweaks.VHCCTweaks;
import iskallia.vault.world.data.PlayerVaultStatsData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/**
 * A player's Vault Hunters level, for ccvault.getPlayerVaultLevel. Read-only, and only on the server thread:
 * Vault Hunters loads an offline player's saved profile only there, and anywhere else would hand back a blank one.
 *
 * Vault Hunters 3.21.72 keeps each player's stats in their own profile file and loads it on demand
 * (PlayerProfiles.ensureLoaded). Older versions keep every player in memory. Both are handled; the newer parts are
 * reached by reflection because this mod compiles against 3.21.0. A player with no saved stats is never given a
 * blank record, so asking about them changes nothing.
 */
public final class VaultLevels {

    private static volatile Field playerMap;
    private static volatile Method ensureLoaded;
    private static volatile boolean looked;

    private VaultLevels() {}

    /** The player's vault level, or null if Vault Hunters has nothing saved for them. Server thread only. */
    static Integer read(MinecraftServer server, UUID uuid) {
        PlayerVaultStatsData data = PlayerVaultStatsData.get(server);
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return data.getVaultStats(online).getVaultLevel();

        try {
            lookUpInternals();
            if (ensureLoaded != null) ensureLoaded.invoke(null, uuid);
            if (playerMap == null) return null;
            Object stats = ((Map<?, ?>) playerMap.get(data)).get(uuid);
            return stats instanceof iskallia.vault.skill.PlayerVaultStats vaultStats ? vaultStats.getVaultLevel() : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            VHCCTweaks.LOGGER.warn("Could not read the vault level of {}", uuid, e);
            return null;
        }
    }

    private static void lookUpInternals() {
        if (looked) return;
        synchronized (VaultLevels.class) {
            if (looked) return;
            try {
                Class<?> profiles = Class.forName("iskallia.vault.world.data.PlayerProfiles");
                ensureLoaded = profiles.getMethod("ensureLoaded", UUID.class);
            } catch (ReflectiveOperationException e) {
                // Older Vault Hunters: every player's stats are already in memory
            }
            Field found = null;
            try {
                found = PlayerVaultStatsData.class.getDeclaredField("playerMap");
            } catch (NoSuchFieldException e) {
                for (Field field : PlayerVaultStatsData.class.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(field.getType())) {
                        found = field;
                        break;
                    }
                }
            }
            if (found != null) found.setAccessible(true);
            playerMap = found;
            if (found == null) VHCCTweaks.LOGGER.warn("Vault Hunters has no player stats map: offline vault levels are unavailable");
            looked = true;
        }
    }
}
