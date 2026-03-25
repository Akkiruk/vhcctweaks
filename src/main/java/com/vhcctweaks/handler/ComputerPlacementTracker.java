package com.vhcctweaks.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.vhcctweaks.VHCCTweaks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the permanent host owner for each CC:Tweaked computer ID.
 * Ownership is assigned on first interaction, which supports both
 * placed computers and pocket computers. Persists to
 * vhcc_data/ccvault/owners.json.
 */
public class ComputerPlacementTracker {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    // computerID (String for JSON compat) -> owner UUID
    private static final Map<Integer, UUID> computerOwners = new ConcurrentHashMap<>();
    private static Path persistPath;

    /** Set the persistence file path. Must be called before any events fire. */
    public static void init(Path dataDir) {
        persistPath = dataDir.resolve("ccvault").resolve("owners.json");
        load();
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getWorld().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation blockId = event.getPlacedBlock().getBlock().getRegistryName();
        if (blockId == null || !blockId.getNamespace().equals("computercraft")) return;

        // Ownership is intentionally NOT assigned on placement.
        // Host is assigned on first interaction with an unowned computer.
        Level level = (Level) event.getWorld();
        BlockEntity be = level.getBlockEntity(event.getPos());
        if (be == null) return;

        int computerId = ComputerReflectionHelper.getComputerIdFromBlockEntity(be);
        if (computerId >= 0) {
            VHCCTweaks.LOGGER.debug("CCVault: Computer {} placed by {} (owner will be set on first interaction)",
                    computerId, player.getName().getString());
        } else {
            VHCCTweaks.LOGGER.debug("CCVault: Placed computer at {} before ID assignment by {}",
                    event.getPos(), player.getName().getString());
        }
    }

    /** Get the UUID of the player who owns the computer, or null if unknown. */
    public static UUID getOwner(int computerId) {
        return computerOwners.get(computerId);
    }

    /**
     * Set owner for a computer ID, only if it has no existing owner.
     * Ownership is permanent and cannot be overwritten.
     * @return true if ownership was assigned, false if already owned
     */
    public static boolean setOwner(int computerId, UUID ownerUuid) {
        if (computerOwners.containsKey(computerId)) {
            return false;
        }
        computerOwners.put(computerId, ownerUuid);
        save();
        return true;
    }

    // ---- persistence ----

    private static void load() {
        if (persistPath == null || !Files.exists(persistPath)) return;
        try {
            String json = Files.readString(persistPath, StandardCharsets.UTF_8);
            Map<String, String> raw = GSON.fromJson(json, MAP_TYPE);
            if (raw != null) {
                raw.forEach((k, v) -> {
                    try {
                        computerOwners.put(Integer.parseInt(k), UUID.fromString(v));
                    } catch (Exception e) {
                        VHCCTweaks.LOGGER.warn("CCVault: Invalid owner entry: {}={}", k, v);
                    }
                });
            }
            VHCCTweaks.LOGGER.info("CCVault: Loaded {} computer owners", computerOwners.size());
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("CCVault: Failed to load owners.json", e);
        }
    }

    private static synchronized void save() {
        if (persistPath == null) return;
        try {
            Files.createDirectories(persistPath.getParent());
            Map<String, String> raw = new ConcurrentHashMap<>();
            computerOwners.forEach((k, v) -> raw.put(k.toString(), v.toString()));
            String json = GSON.toJson(raw);
            Files.writeString(persistPath, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("CCVault: Failed to save owners.json", e);
        }
    }
}
