package com.vhcctweaks.handler;

import com.vhcctweaks.VHCCTweaks;
import com.vhcctweaks.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Prevents crafty turtles (turtles with the crafting table upgrade) from being
 * used until the player has unlocked the required Vault Hunters research.
 *
 * Regular turtles (mining, moving, building, etc.) are completely unaffected.
 *
 * Two layers of enforcement:
 * 1. ItemCraftedEvent: strips the crafting upgrade the moment a crafty turtle is crafted
 * 2. PlayerTickEvent: periodically scans inventory for crafty turtles obtained by other means
 */
public class CraftingLockHandler {
    private static final String CRAFTING_UPGRADE_ID = "computercraft:crafting_table";

    // VH reflection state
    private static boolean reflectionInitialized = false;
    private static boolean reflectionAvailable = false;
    private static Method getDataMethod;
    private static Method getResearchesMethod;
    private static Method isResearchedMethod;
    private static boolean getResearchesTakesUUID = false;

    // --- Crafting Intercept ---

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!ModConfig.LOCK_CRAFTY_TURTLES.get()) return;
        if (event.getPlayer().level.isClientSide()) return;

        Player player = event.getPlayer();
        ItemStack result = event.getCrafting();

        // Block 1: Player crafting a crafty turtle item (strips the upgrade)
        if (hasCraftingUpgrade(result) && !hasAutocraftingUnlocked(player)) {
            removeCraftingUpgrade(result);
            player.displayClientMessage(
                    new TextComponent(ChatFormatting.RED + "[VH] Crafty Turtles require the "
                            + ModConfig.AUTOCRAFTING_RESEARCH_NAME.get() + " research!"),
                    false);
        }

        // Block 2: Turtle using turtle.craft() — CC fires this event with TurtlePlayer (a FakePlayer)
        // The crafter is the TurtlePlayer, not a real player. Check the owner's research.
        if (player instanceof FakePlayer && isCCTurtlePlayer(player)) {
            UUID ownerUUID = player.getGameProfile().getId();
            if (ownerUUID != null && !hasResearchByUUID(ownerUUID, player.level)) {
                // Void the crafting result — the turtle shouldn't be able to craft
                result.setCount(0);
                VHCCTweaks.LOGGER.debug("Blocked turtle.craft() for owner {} — missing {} research",
                        ownerUUID, ModConfig.AUTOCRAFTING_RESEARCH_NAME.get());
            }
        }
    }

    /**
     * Checks if a FakePlayer is CC:Tweaked's TurtlePlayer by class name.
     * We can't import the class directly since it's a CC internal.
     */
    private static boolean isCCTurtlePlayer(Player player) {
        return player.getClass().getName().contains("dan200.computercraft");
    }

    // --- Inventory Sweep (backup for non-crafting acquisition) ---

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!ModConfig.LOCK_CRAFTY_TURTLES.get()) return;
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level.isClientSide()) return;
        if (event.player.tickCount % 100 != 0) return; // every 5 seconds

        if (hasAutocraftingUnlocked(event.player)) return;

        boolean found = false;
        Inventory inventory = event.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (hasCraftingUpgrade(stack)) {
                removeCraftingUpgrade(stack);
                found = true;
            }
        }
        if (found) {
            event.player.displayClientMessage(
                    new TextComponent(ChatFormatting.RED + "[VH] Crafty Turtles require the "
                            + ModConfig.AUTOCRAFTING_RESEARCH_NAME.get()
                            + " research! The crafting upgrade has been removed."),
                    false);
        }
    }

    // --- Detection: is this a turtle with the crafting table upgrade? ---

    public static boolean hasCraftingUpgrade(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !"computercraft".equals(id.getNamespace())) return false;
        String path = id.getPath();
        if (!"turtle_normal".equals(path) && !"turtle_advanced".equals(path)) return false;

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("BlockEntityTag", Tag.TAG_COMPOUND)) return false;
        CompoundTag be = tag.getCompound("BlockEntityTag");

        return CRAFTING_UPGRADE_ID.equals(be.getString("RightUpgrade"))
                || CRAFTING_UPGRADE_ID.equals(be.getString("LeftUpgrade"))
                || CRAFTING_UPGRADE_ID.equals(be.getString("RightUpgradeId"))
                || CRAFTING_UPGRADE_ID.equals(be.getString("LeftUpgradeId"));
    }

    // --- Strip the crafting upgrade from a turtle item in-place ---

    private static void removeCraftingUpgrade(ItemStack turtle) {
        CompoundTag tag = turtle.getTag();
        if (tag == null || !tag.contains("BlockEntityTag")) return;
        CompoundTag be = tag.getCompound("BlockEntityTag");

        for (String key : new String[]{"RightUpgrade", "LeftUpgrade", "RightUpgradeId", "LeftUpgradeId"}) {
            if (CRAFTING_UPGRADE_ID.equals(be.getString(key))) {
                be.remove(key);
                // Remove associated NBT data
                String nbtKey = key.endsWith("Id")
                        ? key.substring(0, key.length() - 2) + "Nbt"
                        : key + "Nbt";
                be.remove(nbtKey);
            }
        }
    }

    // --- Vault Hunters research check via reflection ---

    public static boolean hasAutocraftingUnlocked(Player player) {
        if (player.isCreative()) return true;
        if (!(player instanceof ServerPlayer serverPlayer)) return true;

        if (!reflectionInitialized) {
            initReflection();
        }
        if (!reflectionAvailable) return true; // VH not loaded, don't restrict

        try {
            String researchName = ModConfig.AUTOCRAFTING_RESEARCH_NAME.get();
            ServerLevel overworld = serverPlayer.getServer().overworld();
            Object data = getDataMethod.invoke(null, overworld);
            Object researches = getResearchesTakesUUID
                    ? getResearchesMethod.invoke(data, player.getUUID())
                    : getResearchesMethod.invoke(data, player);
            if (researches == null) return false;
            Object result = isResearchedMethod.invoke(researches, researchName);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            VHCCTweaks.LOGGER.debug("Failed to check VH research: {}", e.getMessage());
            return true; // On error, fail open
        }
    }

    /**
     * UUID-based research check for use from Mixins (turtle owner may not be online).
     * Called by TurtleCraftCommandMixin to check the turtle's owner against VH research.
     */
    public static boolean hasResearchByUUID(UUID playerUUID, Level level) {
        if (level.isClientSide()) return true;

        MinecraftServer server = level.getServer();
        if (server == null) return true;

        if (!reflectionInitialized) {
            initReflection();
        }
        if (!reflectionAvailable) return true; // VH not loaded, don't restrict

        try {
            String researchName = ModConfig.AUTOCRAFTING_RESEARCH_NAME.get();
            ServerLevel overworld = server.overworld();
            Object data = getDataMethod.invoke(null, overworld);

            Object researches;
            if (getResearchesTakesUUID) {
                researches = getResearchesMethod.invoke(data, playerUUID);
            } else {
                // Method takes Player, try to find the player online
                ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
                if (player != null && player.isCreative()) return true;
                if (player == null) return false; // Player offline + method needs Player = deny
                researches = getResearchesMethod.invoke(data, player);
            }
            if (researches == null) return false;
            Object result = isResearchedMethod.invoke(researches, researchName);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            VHCCTweaks.LOGGER.debug("Failed to check VH research by UUID: {}", e.getMessage());
            return true; // On error, fail open
        }
    }

    private static synchronized void initReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;

        String[] classNames = {
                "iskallia.vault.world.data.PlayerResearchesData",
                "iskallia.vault.data.PlayerResearchesData",
                "iskallia.vault.core.data.PlayerResearchesData"
        };

        for (String className : classNames) {
            try {
                Class<?> dataClass = Class.forName(className);

                // Find static get(ServerLevel) method
                getDataMethod = findMethod(dataClass, "get", 1);
                if (getDataMethod == null) continue;

                // Find getResearches - try UUID first, then Player
                getResearchesMethod = findMethodByParam(dataClass, "getResearches", UUID.class);
                if (getResearchesMethod != null) {
                    getResearchesTakesUUID = true;
                } else {
                    getResearchesMethod = findMethodByParam(dataClass, "getResearches", Player.class);
                    getResearchesTakesUUID = false;
                }
                if (getResearchesMethod == null) {
                    getDataMethod = null;
                    continue;
                }

                // Find isResearched(String) on the return type
                Class<?> treeClass = getResearchesMethod.getReturnType();
                isResearchedMethod = findMethodByParam(treeClass, "isResearched", String.class);
                if (isResearchedMethod == null) {
                    isResearchedMethod = findMethodByParam(treeClass, "hasResearch", String.class);
                }
                if (isResearchedMethod == null) {
                    getDataMethod = null;
                    getResearchesMethod = null;
                    continue;
                }

                reflectionAvailable = true;
                VHCCTweaks.LOGGER.info("VH research integration ready ({})", className);
                return;
            } catch (ClassNotFoundException ignored) {
            } catch (Exception e) {
                VHCCTweaks.LOGGER.warn("Error probing VH class {}: {}", className, e.getMessage());
            }
        }

        VHCCTweaks.LOGGER.info("VH research classes not found - crafty turtle lock disabled "
                + "(turtles are still blocked in vaults)");
    }

    private static Method findMethod(Class<?> clazz, String name, int paramCount) {
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) return m;
        }
        return null;
    }

    private static Method findMethodByParam(Class<?> clazz, String name, Class<?> paramType) {
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isAssignableFrom(paramType)) return m;
        }
        return null;
    }
}
