package com.vhcctweaks.handler;

import com.vhcctweaks.VHCCTweaks;
import com.vhcctweaks.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which player last interacted with each CC:Tweaked computer.
 * Uses the right-click event on computercraft blocks and reads the
 * computer ID from the block entity via reflection.
 *
 * Supports monitor clicks: when a player right-clicks a CC monitor,
 * the tracker walks the multi-block monitor structure and finds the
 * adjacent computer to associate the interaction with.
 */
public class ComputerInteractionTracker {

    // computer ID → player UUID
    private static final Map<Integer, UUID> computerToPlayer = new ConcurrentHashMap<>();
    // computer ID → interaction timestamp (epoch millis)
    private static final Map<Integer, Long> interactionTimestamps = new ConcurrentHashMap<>();
    // player UUID → live ServerPlayer reference (cleaned up on logout)
    private static final Map<UUID, ServerPlayer> onlinePlayers = new ConcurrentHashMap<>();

    /** Max monitor blocks to traverse in BFS (prevents runaway on broken structures). */
    private static final int MAX_MONITOR_BFS = 200;

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        try {
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

            // If the clicked block is a monitor (no computer ID), find the connected computer
            if (computerId < 0 && blockId.getPath().contains("monitor")) {
                computerId = findComputerAdjacentToMonitor(level, pos);
            }

            if (computerId >= 0) {
                computerToPlayer.put(computerId, player.getUUID());
                interactionTimestamps.put(computerId, System.currentTimeMillis());
                onlinePlayers.put(player.getUUID(), player);

                // First interaction with an unowned computer assigns permanent owner
                if (ComputerPlacementTracker.getOwner(computerId) == null) {
                    UUID hostUuid = player.getUUID();
                    ComputerPlacementTracker.setOwner(computerId, hostUuid);
                    VHCCTweaks.LOGGER.info("CCVault: Computer {} assigned host {} on first interaction",
                            computerId, hostUuid);
                }
            }
        } catch (Exception e) {
            VHCCTweaks.LOGGER.debug("ComputerInteractionTracker: error processing right-click: {}", e.getMessage());
        }
    }

    /**
     * BFS through connected monitor blocks, checking all adjacent non-monitor
     * positions for a computer block entity. Handles multi-block monitors of any size.
     *
     * @return the first computer ID found adjacent to the monitor, or -1
     */
    private static int findComputerAdjacentToMonitor(Level level, BlockPos startPos) {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(startPos);
        visited.add(startPos);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (visited.contains(neighbor)) continue;

                BlockEntity neighborBe = level.getBlockEntity(neighbor);
                if (neighborBe == null) continue;

                ResourceLocation neighborId = level.getBlockState(neighbor).getBlock().getRegistryName();
                if (neighborId == null || !neighborId.getNamespace().equals("computercraft")) continue;

                // Check if this neighbor is a computer (has getComputerID)
                int id = ComputerReflectionHelper.getComputerIdFromBlockEntity(neighborBe);
                if (id >= 0) return id;

                // If it's another monitor block, continue BFS through the multi-block
                if (neighborId.getPath().contains("monitor")) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }

            if (visited.size() > MAX_MONITOR_BFS) break;
        }

        return -1;
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
