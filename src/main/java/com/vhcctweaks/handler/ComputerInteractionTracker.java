package com.vhcctweaks.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;
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

        int computerId = getComputerIdFromBlockEntity(be);
        if (computerId >= 0) {
            computerToPlayer.put(computerId, player.getUUID());
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

    // ---- reflection helpers ----

    private static int getComputerIdFromBlockEntity(BlockEntity be) {
        for (String methodName : new String[]{"getComputerID", "getID"}) {
            try {
                Method m = findDeclaredMethod(be.getClass(), methodName);
                if (m != null) {
                    m.setAccessible(true);
                    Object result = m.invoke(be);
                    if (result instanceof Number) {
                        return ((Number) result).intValue();
                    }
                }
            } catch (Exception ignored) {
                // Not a CC computer or method inaccessible — skip
            }
        }
        return -1;
    }

    private static Method findDeclaredMethod(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredMethod(name);
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
