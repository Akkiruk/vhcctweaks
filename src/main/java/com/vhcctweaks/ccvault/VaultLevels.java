package com.vhcctweaks.ccvault;

import com.vhcctweaks.VHCCTweaks;
import com.vhcctweaks.util.Safety;
import iskallia.vault.skill.PlayerVaultStats;
import iskallia.vault.world.data.PlayerVaultStatsData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * A player's Vault Hunters level, for ccvault.getPlayerVaultLevel. Read-only, server thread only.
 *
 * - A player on this server: Vault Hunters' live stats.
 * - Anyone else, including a player another server of the network calls online: this server's saved record, which may
 *   be stale. Vault Hunters 3.21.72 keeps each player in playerdata/&lt;uuid&gt;.the_vault and loads it only while they
 *   are here, so a profile it has not loaded is read straight from that file and Vault Hunters is never asked to load
 *   it (it would write it back on the next save). Older Vault Hunters keeps everyone in memory.
 * - Nothing saved: null, and nothing is created.
 *
 * The 3.21.72 parts are reached by reflection, because this mod compiles against 3.21.0.
 */
public final class VaultLevels {

    private static final String PROFILE_SUFFIX = ".the_vault";
    private static final String STATS_SECTION = "vault_stats";
    private static final String LEVEL_KEY = "vaultLevel";

    private static volatile Field playerMap;
    private static volatile Method isLoaded;
    private static volatile boolean looked;

    private VaultLevels() {}

    /** The player's vault level, or null if this server has nothing saved for them. */
    static Integer read(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) return null;
        PlayerVaultStatsData data = PlayerVaultStatsData.get(server);

        ServerPlayer here = Safety.here(server, uuid);
        if (here != null && data != null) {
            PlayerVaultStats stats = data.getVaultStats(here);
            if (stats != null) return stats.getVaultLevel();
        }

        lookUpInternals();
        try {
            boolean perPlayerFiles = isLoaded != null;
            if (perPlayerFiles && !Boolean.TRUE.equals(isLoaded.invoke(null, uuid))) return readSaved(server, uuid);
            if (data == null || playerMap == null) return perPlayerFiles ? readSaved(server, uuid) : null;
            Object map = playerMap.get(data);
            Object stats = map instanceof Map<?, ?> players ? players.get(uuid) : null;
            return stats instanceof PlayerVaultStats vaultStats ? vaultStats.getVaultLevel() : null;
        } catch (ReflectiveOperationException e) {
            Safety.logOnce("VaultLevels.read", e);
            return null;
        }
    }

    /** This server's saved profile, read without touching Vault Hunters' state. */
    static Integer readSaved(MinecraftServer server, UUID uuid) {
        Path file = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid + PROFILE_SUFFIX);
        if (!Files.isRegularFile(file)) return null;
        try {
            return levelIn(NbtIo.read(file.toFile()));
        } catch (IOException | RuntimeException e) {
            VHCCTweaks.LOGGER.warn("Could not read the saved vault level in {}", file, e);
            return null;
        }
    }

    static Integer levelIn(CompoundTag profile) {
        if (profile == null || !profile.contains(STATS_SECTION, Tag.TAG_COMPOUND)) return null;
        CompoundTag stats = profile.getCompound(STATS_SECTION);
        return stats.contains(LEVEL_KEY, Tag.TAG_ANY_NUMERIC) ? stats.getInt(LEVEL_KEY) : null;
    }

    private static void lookUpInternals() {
        if (looked) return;
        synchronized (VaultLevels.class) {
            if (looked) return;
            try {
                isLoaded = Class.forName("iskallia.vault.world.data.PlayerProfiles").getMethod("isLoaded", UUID.class);
            } catch (ReflectiveOperationException e) {
                // Older Vault Hunters: every player's stats are in memory
            }
            Field found = null;
            for (Field field : PlayerVaultStatsData.class.getDeclaredFields()) {
                if (field.getName().equals("playerMap")) {
                    found = field;
                    break;
                }
                if (found == null && Map.class.isAssignableFrom(field.getType())) found = field;
            }
            if (found != null) found.setAccessible(true);
            playerMap = found;
            looked = true;
        }
    }
}
