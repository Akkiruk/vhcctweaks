package com.vhcctweaks.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.vhcctweaks.VHCCTweaks;
import com.vhcctweaks.ccvault.EscrowService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
 * Tracks which player placed each CC:Tweaked computer block.
 * This determines the "host" UUID — the account that owns the terminal
 * for CCVault purposes. Persists to vhcc_data/ccvault/owners.json.
 */
public class ComputerPlacementTracker {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    // computerID (String for JSON compat) → placer UUID
    private static final Map<Integer, UUID> computerOwners = new ConcurrentHashMap<>();
    // BlockPos (encoded as long) → placer UUID — temporary hold for computers whose ID isn't assigned yet at placement
    private static final Map<Long, UUID> pendingByPosition = new ConcurrentHashMap<>();
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

        // The block entity is already placed at this point — read computer ID
        Level level = (Level) event.getWorld();
        BlockEntity be = level.getBlockEntity(event.getPos());
        if (be == null) return;

        int computerId = ComputerReflectionHelper.getComputerIdFromBlockEntity(be);
        if (computerId >= 0) {
            computerOwners.put(computerId, player.getUUID());
            save();
            VHCCTweaks.LOGGER.debug("CCVault: Computer {} placed by {}", computerId, player.getName().getString());
        } else {
            // Computer ID not assigned yet — store by position so we can resolve on first interaction
            pendingByPosition.put(event.getPos().asLong(), player.getUUID());
            VHCCTweaks.LOGGER.debug("CCVault: Stored pending owner at pos {} for {}", event.getPos(), player.getName().getString());
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getWorld().isClientSide()) return;

        BlockState state = event.getState();
        ResourceLocation blockId = state.getBlock().getRegistryName();
        if (blockId == null || !blockId.getNamespace().equals("computercraft")) return;

        Level level = (Level) event.getWorld();
        BlockEntity be = level.getBlockEntity(event.getPos());
        if (be == null) return;

        int computerId = ComputerReflectionHelper.getComputerIdFromBlockEntity(be);
        if (computerId < 0) return;

        // Immediately refund any active escrows held by this computer
        int refunded = EscrowService.refundAllForComputer(computerId);
        if (refunded > 0) {
            VHCCTweaks.LOGGER.info("CCVault: Computer {} broken — refunded {} escrow(s)", computerId, refunded);
        }
    }

    /** Get the UUID of the player who placed the computer, or null if unknown. */
    public static UUID getOwner(int computerId) {
        return computerOwners.get(computerId);
    }

    /** Manually set owner (admin command). */
    public static void setOwner(int computerId, UUID ownerUuid) {
        computerOwners.put(computerId, ownerUuid);
        save();
    }

    /**
     * Consume and return the placer UUID stored by block position (from placement time),
     * or null if no pending placement was recorded at that position.
     * Removes the entry so it's only used once.
     */
    public static UUID consumePendingPlacer(long posLong) {
        return pendingByPosition.remove(posLong);
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
