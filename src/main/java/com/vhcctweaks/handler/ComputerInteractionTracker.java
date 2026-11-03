package com.vhcctweaks.handler;

import com.vhcctweaks.VHCCTweaks;
import com.vhcctweaks.ccvault.SessionAuthManager;
import com.vhcctweaks.config.ModConfig;
import com.vhcctweaks.mixin.TileMonitorAccessor;
import dan200.computercraft.shared.computer.blocks.TileComputerBase;
import dan200.computercraft.shared.computer.inventory.ContainerComputerBase;
import dan200.computercraft.shared.peripheral.monitor.TileMonitor;
import dan200.computercraft.shared.pocket.items.ItemPocketComputer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which player last interacted with each CC:Tweaked computer,
 * including placed blocks, monitors, and pocket computers.
 *
 * A monitor click counts for every computer attached to that monitor, however it is attached.
 * Pocket computers are registered on item use, and any computer menu is refreshed while it is open
 * so long-lived sessions do not go stale mid-interaction.
 */
public class ComputerInteractionTracker {

    // computer ID → player UUID
    private static final Map<Integer, UUID> computerToPlayer = new ConcurrentHashMap<>();
    // computer ID → interaction timestamp (epoch millis)
    private static final Map<Integer, Long> interactionTimestamps = new ConcurrentHashMap<>();
    // computer ID → player UUID → that player's own last interaction, for several players at one table
    private static final Map<Integer, Map<UUID, Long>> playerTimestamps = new ConcurrentHashMap<>();

    /** Computer IDs restart with each world, so nothing here may carry over to the next one. */
    public static void reset() {
        computerToPlayer.clear();
        interactionTimestamps.clear();
        playerTimestamps.clear();
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        try {
            if (event.getWorld().isClientSide()) return;
            if (!(event.getPlayer() instanceof ServerPlayer player)) return;

            BlockEntity be = event.getWorld().getBlockEntity(event.getPos());
            if (be instanceof TileComputerBase computer) {
                recordInteraction(player, computer.getComputerID());
                offerIfUnowned(player, computer, event.getHand());
            } else if (be instanceof TileMonitor monitor) {
                // Queued before CC queues monitor_touch, so a script always sees who before where.
                // These are CC's own conditions for a touch, so the two events always come as a pair.
                boolean touch = event.getHand() == InteractionHand.MAIN_HAND
                        && !player.isCrouching() && event.getFace() == monitor.getFront();
                ((TileMonitorAccessor) (Object) monitor).vhcctweaks$eachComputer(computer -> {
                    // One attachment going away mid-touch (a linked computer unloading) must not cost the others their touch
                    try {
                        recordInteraction(player, computer.getID());
                        if (touch) computer.queueEvent("vhcc_touch", player.getGameProfile().getName());
                    } catch (RuntimeException e) {
                        VHCCTweaks.LOGGER.debug("ComputerInteractionTracker: skipped a detached computer: {}", e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            VHCCTweaks.LOGGER.debug("ComputerInteractionTracker: error processing right-click: {}", e.getMessage());
        }
    }

    /** The event fires once per hand, and a crouching click places a block instead of using the computer. */
    private static void offerIfUnowned(ServerPlayer player, TileComputerBase computer, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || player.isCrouching() || player instanceof FakePlayer) return;

        // A computer placed by a command has no ID until something needs one
        int computerId = computer.getComputerID() >= 0 ? computer.getComputerID() : computer.createServerComputer().getID();
        if (ComputerPlacementTracker.getOwner(computerId) == null) {
            SessionAuthManager.offerHost(player, computerId);
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

            recordInteraction(player, computerId);
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
            int computerId = menu.getComputer().getID();
            UUID last = computerToPlayer.get(computerId);
            // Leaving a terminal open must not take the computer from someone tapping its monitor
            if (last == null || last.equals(player.getUUID()) || !isRecent(interactionTimestamps.get(computerId))) {
                recordInteraction(player, computerId);
            } else {
                refreshPlayer(player.getUUID(), computerId);
            }
        } catch (Exception e) {
            VHCCTweaks.LOGGER.debug("ComputerInteractionTracker: error refreshing menu session: {}", e.getMessage());
        }
    }

    /**
     * Get the last player who interacted with a computer, or null if none / offline.
     */
    public static ServerPlayer getPlayer(int computerId) {
        UUID uuid = computerToPlayer.get(computerId);
        if (uuid == null) return null;
        // Called from ccvault on computer threads: the thread-safe lookup, and only a player on this server
        return com.vhcctweaks.util.Safety.here(ServerLifecycleHooks.getCurrentServer(), uuid);
    }

    /**
     * Get the player only if their interaction is recent (within configured staleness window).
     * Used by CCVault for financial operations where a stale interaction is a security risk.
     */
    public static ServerPlayer getFreshPlayer(int computerId) {
        return isRecent(interactionTimestamps.get(computerId)) ? getPlayer(computerId) : null;
    }

    /** Whether this player touched this computer within the staleness window, whoever touched it last. */
    public static boolean isFresh(int computerId, UUID playerUuid) {
        Map<UUID, Long> players = playerTimestamps.get(computerId);
        return players != null && isRecent(players.get(playerUuid));
    }

    private static boolean isRecent(Long timestamp) {
        if (timestamp == null) return false;
        return System.currentTimeMillis() - timestamp <= ModConfig.CCVAULT_INTERACTION_STALE_SECONDS.get() * 1000L;
    }

    private static void recordInteraction(ServerPlayer player, int computerId) {
        if (computerId < 0) return;

        computerToPlayer.put(computerId, player.getUUID());
        interactionTimestamps.put(computerId, System.currentTimeMillis());
        refreshPlayer(player.getUUID(), computerId);
    }

    private static void refreshPlayer(UUID playerUuid, int computerId) {
        playerTimestamps.computeIfAbsent(computerId, id -> new ConcurrentHashMap<>()).put(playerUuid, System.currentTimeMillis());
        SessionAuthManager.touchSession(playerUuid, computerId);
    }
}
