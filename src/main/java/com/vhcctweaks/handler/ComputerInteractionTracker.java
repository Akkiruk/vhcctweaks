package com.vhcctweaks.handler;

import com.vhcctweaks.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which player last interacted with each CC:Tweaked computer.
 * Uses the right-click event on computercraft blocks and reads the
 * computer ID from the block entity via reflection.
 */
public class ComputerInteractionTracker {

    // computer ID → player UUID
    private static final Map<Integer, UUID> computerToPlayer = new ConcurrentHashMap<>();
    // computer ID → interaction timestamp (epoch millis)
    private static final Map<Integer, Long> interactionTimestamps = new ConcurrentHashMap<>();
    // player UUID → live ServerPlayer reference (cleaned up on logout)
    private static final Map<UUID, ServerPlayer> onlinePlayers = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getWorld().isClientSide()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        BlockPos pos = event.getPos();
        Level level = event.getWorld();

        // Only care about CC:Tweaked blocks
        ResourceLocation blockId = level.getBlockState(pos).getBlock().getRegistryName();
        if (blockId == null || !blockId.getNamespace().equals("computercraft")) return;

        // Read the computer ID from the block entity via reflection
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;

        int computerId = ComputerReflectionHelper.getComputerIdFromBlockEntity(be);
        if (computerId >= 0) {
            computerToPlayer.put(computerId, player.getUUID());
            interactionTimestamps.put(computerId, System.currentTimeMillis());
            onlinePlayers.put(player.getUUID(), player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            onlinePlayers.remove(player.getUUID());
        }
    }

    /**
     * Get the last player who interacted with a computer, or null if none / offline.
     */
    public static ServerPlayer getPlayer(int computerId) {
        UUID uuid = computerToPlayer.get(computerId);
        if (uuid == null) return null;
        ServerPlayer player = onlinePlayers.get(uuid);
        if (player == null || player.isRemoved()) {
            onlinePlayers.remove(uuid);
            return null;
        }
        return player;
    }

    /**
     * Get the player only if their interaction is recent (within configured staleness window).
     * Used by CCVault for financial operations where a stale interaction is a security risk.
     */
    public static ServerPlayer getFreshPlayer(int computerId) {
        Long timestamp = interactionTimestamps.get(computerId);
        if (timestamp == null) return null;
        long staleMs = ModConfig.CCVAULT_INTERACTION_STALE_SECONDS.get() * 1000L;
        if (System.currentTimeMillis() - timestamp > staleMs) return null;
        return getPlayer(computerId);
    }

    /** Get the UUID of the player who last interacted, regardless of staleness. */
    public static UUID getPlayerUuid(int computerId) {
        return computerToPlayer.get(computerId);
    }
}
