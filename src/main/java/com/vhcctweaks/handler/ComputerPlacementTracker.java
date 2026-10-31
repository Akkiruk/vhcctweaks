package com.vhcctweaks.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.vhcctweaks.VHCCTweaks;
import com.vhcctweaks.util.AtomicFiles;
import dan200.computercraft.shared.computer.blocks.TileComputerBase;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the permanent host owner for each CC:Tweaked computer ID. Whoever places a computer owns it,
 * and a labelled computer keeps its owner wherever it is placed next. Anything without an owner is
 * offered in chat whenever someone right-clicks it. Pocket computers are claimed with ccvault.claimHost,
 * which the player also confirms in chat.
 * Persists to vhcc_data/ccvault/owners.json.
 */
public class ComputerPlacementTracker {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    // computerID (String for JSON compat) -> owner UUID
    private static final Map<Integer, UUID> computerOwners = new ConcurrentHashMap<>();
    private static Path persistPath;

    /** Loads this world's owners. Must be called before any events fire. */
    public static void init(Path dataDir) {
        computerOwners.clear();
        persistPath = dataDir.resolve("ccvault").resolve("owners.json");
        load();
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getWorld().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (!(event.getWorld().getBlockEntity(event.getPos()) instanceof TileComputerBase computer)) return;

        // A new computer has no ID until its first tick, so give it one now
        int computerId = computer.getComputerID() >= 0 ? computer.getComputerID() : computer.createServerComputer().getID();
        if (setOwner(computerId, player.getUUID())) {
            VHCCTweaks.LOGGER.info("CCVault: Computer {} placed by owner {}", computerId, player.getName().getString());
        }
    }

    /** Get the UUID of the player who owns the computer, or null if unknown. */
    public static UUID getOwner(int computerId) {
        return computerOwners.get(computerId);
    }

    /**
     * Set owner for a computer ID, only if it has no existing owner.
     * @return true if ownership was assigned, false if already owned
     */
    public static boolean setOwner(int computerId, UUID ownerUuid) {
        if (computerOwners.putIfAbsent(computerId, ownerUuid) != null) {
            return false;
        }
        save();
        return true;
    }

    /**
     * Force the owner for a computer ID to a specific player. Only reached once that player has
     * confirmed the claim in chat.
     *
     * @return true if the stored owner changed, false if it was already ownerUuid
     */
    public static boolean replaceOwner(int computerId, UUID ownerUuid) {
        UUID previous = computerOwners.put(computerId, ownerUuid);
        if (ownerUuid.equals(previous)) {
            return false;
        }
        save();
        return true;
    }

    /**
     * Forget a computer's owner, and its lock with it: a locked computer with no owner would only open for admins.
     *
     * @return true if it had an owner
     */
    public static boolean clearOwner(int computerId) {
        if (computerOwners.remove(computerId) == null) {
            return false;
        }
        save();
        ComputerLock.setLocked(computerId, false);
        return true;
    }

    // ---- persistence ----

    private static void load() {
        if (persistPath == null || !Files.exists(persistPath)) return;
        try {
            String json = Files.readString(persistPath, StandardCharsets.UTF_8);
            Map<String, String> raw = GSON.fromJson(json, MAP_TYPE);
            if (raw == null) throw new JsonParseException("file is empty");
            raw.forEach((k, v) -> {
                try {
                    computerOwners.put(Integer.parseInt(k), UUID.fromString(v));
                } catch (Exception e) {
                    VHCCTweaks.LOGGER.warn("CCVault: Invalid owner entry: {}={}", k, v);
                }
            });
            VHCCTweaks.LOGGER.info("CCVault: Loaded {} computer owners", computerOwners.size());
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("CCVault: Failed to load owners.json", e);
        } catch (JsonParseException e) {
            VHCCTweaks.LOGGER.error("CCVault: owners.json is damaged, every computer is now unowned", e);
            AtomicFiles.setAside(persistPath);
        }
    }

    private static synchronized void save() {
        if (persistPath == null) return;
        try {
            Map<String, String> raw = new ConcurrentHashMap<>();
            computerOwners.forEach((k, v) -> raw.put(k.toString(), v.toString()));
            AtomicFiles.writeString(persistPath, GSON.toJson(raw));
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("CCVault: Failed to save owners.json", e);
        }
    }
}
