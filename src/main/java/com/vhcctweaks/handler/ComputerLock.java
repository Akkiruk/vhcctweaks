package com.vhcctweaks.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.vhcctweaks.VHCCTweaks;
import com.vhcctweaks.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owner-only computers: a locked computer's terminal only opens for its owner and server admins, and
 * nobody else can break it, since a broken computer keeps its ID and its files. Monitors keep working
 * for everyone, so a locked casino table can still be played. Persists to vhcc_data/ccvault/locks.json.
 */
public class ComputerLock {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Integer>>() {}.getType();

    private static final Set<Integer> locked = ConcurrentHashMap.newKeySet();
    private static Path persistPath;

    public static void init(Path dataDir) {
        persistPath = dataDir.resolve("ccvault").resolve("locks.json");
        if (!Files.exists(persistPath)) return;
        try {
            List<Integer> ids = GSON.fromJson(Files.readString(persistPath, StandardCharsets.UTF_8), LIST_TYPE);
            if (ids != null) locked.addAll(ids);
        } catch (Exception e) {
            VHCCTweaks.LOGGER.error("CCVault: Failed to load locks.json", e);
        }
    }

    public static boolean isLocked(int computerId) {
        return locked.contains(computerId);
    }

    public static void setLocked(int computerId, boolean value) {
        boolean changed = value ? locked.add(computerId) : locked.remove(computerId);
        if (changed) save();
    }

    private static synchronized void save() {
        if (persistPath == null) return;
        try {
            Files.createDirectories(persistPath.getParent());
            Files.writeString(persistPath, GSON.toJson(new ArrayList<>(locked)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("CCVault: Failed to save locks.json", e);
        }
    }

    private static boolean refuses(ServerPlayer player, BlockEntity blockEntity) {
        int computerId = ComputerReflectionHelper.getComputerIdFromBlockEntity(blockEntity);
        if (computerId < 0 || !isLocked(computerId) || !ModConfig.COMPUTER_LOCK_ENABLED.get()) return false;
        UUID owner = ComputerPlacementTracker.getOwner(computerId);
        if (player.getUUID().equals(owner) || player.hasPermissions(2)) return false;
        player.displayClientMessage(new TextComponent("This computer is locked by its owner.").withStyle(ChatFormatting.RED), true);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getWorld().isClientSide()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (refuses(player, event.getWorld().getBlockEntity(event.getPos()))) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getWorld().isClientSide()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (refuses(player, event.getWorld().getBlockEntity(event.getPos()))) {
            event.setCanceled(true);
        }
    }
}
