package com.vhcctweaks.handler;

import com.vhcctweaks.VHCCTweaks;
import com.vhcctweaks.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Prevents all CC:Tweaked block placement, interaction, and item use
 * inside the Vault dimension.
 */
public class VaultProtectionHandler {

    private static boolean isVaultDimension(Level level) {
        if (!ModConfig.BLOCK_CC_IN_VAULT.get()) return false;
        String vaultDim = ModConfig.VAULT_DIMENSION.get();
        return level.dimension().location().toString().equals(vaultDim);
    }

    private static boolean isCCBlock(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null && id.getNamespace().equals("computercraft");
    }

    private static boolean isCCItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && id.getNamespace().equals("computercraft");
    }

    private static void notifyPlayer(Player player, String message) {
        player.displayClientMessage(
                new TextComponent(ChatFormatting.RED + "[VH] " + message), true);
    }

    // --- Block Placement ---
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player) {
            Level level = player.level;
            if (isVaultDimension(level) && isCCBlock(event.getPlacedBlock())) {
                event.setCanceled(true);
                notifyPlayer(player, "CC:Tweaked blocks are disabled inside the Vault!");
            }
        }
    }

    // --- Right-click block interaction ---
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getWorld();
        if (isVaultDimension(level)) {
            // Block interacting with CC blocks
            BlockState state = level.getBlockState(event.getPos());
            if (isCCBlock(state)) {
                event.setCanceled(true);
                notifyPlayer(event.getPlayer(), "CC:Tweaked blocks are disabled inside the Vault!");
                return;
            }
            // Block using CC items
            if (isCCItem(event.getItemStack())) {
                event.setCanceled(true);
                notifyPlayer(event.getPlayer(), "CC:Tweaked items are disabled inside the Vault!");
            }
        }
    }

    // --- Right-click item use ---
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isVaultDimension(event.getWorld()) && isCCItem(event.getItemStack())) {
            event.setCanceled(true);
            notifyPlayer(event.getPlayer(), "CC:Tweaked items are disabled inside the Vault!");
        }
    }

    // --- Left-click block (prevent breaking CC blocks in vault) ---
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Level level = event.getWorld();
        if (isVaultDimension(level)) {
            BlockState state = level.getBlockState(event.getPos());
            if (isCCBlock(state)) {
                event.setCanceled(true);
            }
        }
    }

    // --- Block break (backup: if someone somehow has a CC block in vault) ---
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() != null) {
            Level level = event.getPlayer().level;
            if (isVaultDimension(level) && isCCBlock(event.getState())) {
                event.setCanceled(true);
            }
        }
    }
}
