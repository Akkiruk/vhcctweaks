package com.vhcctweaks.handler;

import com.vhcctweaks.VHCCTweaks;
import com.vhcctweaks.ccvault.SessionAuthManager;
import com.vhcctweaks.config.ModConfig;
import dan200.computercraft.shared.computer.inventory.ContainerComputerBase;
import dan200.computercraft.shared.pocket.items.ItemPocketComputer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which player last interacted with each CC:Tweaked computer,
 * including placed blocks, monitors, and pocket computers.
 *
 * Supports monitor clicks: when a player right-clicks a CC monitor,
 * the tracker walks the multi-block monitor structure and finds the
 * adjacent computer to associate the interaction with. Pocket computers
 * are registered on item use and then refreshed while a computer menu
 * is open so long-lived sessions do not go stale mid-interaction, but
 * their permanent host owner is claimed explicitly by installer flows.
 */
public class ComputerInteractionTracker {

    // computer ID → player UUID
    private static final Map<Integer, UUID> computerToPlayer = new ConcurrentHashMap<>();
    // computer ID → interaction timestamp (epoch millis)
    private static final Map<Integer, Long> interactionTimestamps = new ConcurrentHashMap<>();

    /**
     * Max monitor blocks to traverse in BFS. A full 16x16 monitor is 256 blocks,
     * so leave headroom for large valid multiblocks while still bounding traversal.
     */
    private static final int MAX_MONITOR_BFS = 512;

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

            recordInteraction(player, computerId, true);
        } catch (Exception e) {
            VHCCTweaks.LOGGER.debug("ComputerInteractionTracker: error processing right-click: {}", e.getMessage());
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        try {
            if (event.getWorld().isClientSide()) return;
            if (!(event.getPlayer() instanceof ServerPlayer player)) return;
            if (!(event.getWorld() instanceof ServerLevel level)) return;

            ItemStack stack = event.getItemStack();
            if (!(stack.getItem() instanceof ItemPocketComputer pocketComputer)) return;

            int computerId = pocketComputer.getComputerID(stack);
            if (computerId < 0) {
                computerId = pocketComputer.createServerComputer(level, player, player.getInventory(), stack).getID();
            }

            recordInteraction(player, computerId, false);
        } catch (Exception e) {
            VHCCTweaks.LOGGER.debug("ComputerInteractionTracker: error processing pocket use: {}", e.getMessage());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (!(player.containerMenu instanceof ContainerComputerBase menu)) return;

        try {
            recordInteraction(player, menu.getComputer().getID(), false);
        } catch (Exception e) {
            VHCCTweaks.LOGGER.debug("ComputerInteractionTracker: error refreshing menu session: {}", e.getMessage());
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
        // No-op: getPlayer resolves through the live server player list instead of caching
        // ServerPlayer instances, which avoids stale references across reconnects.
    }

    /**
     * Get the last player who interacted with a computer, or null if none / offline.
     */
    public static ServerPlayer getPlayer(int computerId) {
        UUID uuid = computerToPlayer.get(computerId);
        if (uuid == null) return null;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null || player.isRemoved()) return null;
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

    private static void recordInteraction(ServerPlayer player, int computerId, boolean assignOwnerIfMissing) {
        if (computerId < 0) return;

        computerToPlayer.put(computerId, player.getUUID());
        long now = System.currentTimeMillis();
        interactionTimestamps.put(computerId, now);
        SessionAuthManager.touchSession(player.getUUID(), computerId);

        // Block computers still claim ownership on first interaction.
        if (assignOwnerIfMissing && ComputerPlacementTracker.getOwner(computerId) == null) {
            UUID hostUuid = player.getUUID();
            if (ComputerPlacementTracker.setOwner(computerId, hostUuid)) {
                VHCCTweaks.LOGGER.info("CCVault: Computer {} assigned host {} on first interaction",
                        computerId, hostUuid);
            }
        }
    }
}
