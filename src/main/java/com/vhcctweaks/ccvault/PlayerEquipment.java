package com.vhcctweaks.ccvault;

import dan200.computercraft.api.detail.DetailRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What an online player wears and holds, for ccvault.getPlayerEquipment. Server thread only.
 * Every item goes through DetailRegistries.ITEM_STACK.getDetails, the same path as getItemDetail, so Vault Hunters
 * data is identical. Curios slots come from the Curios API, when Curios is installed.
 */
final class PlayerEquipment {

    private static final EquipmentSlot[] WORN = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private PlayerEquipment() {}

    static Map<String, Object> read(ServerPlayer player) {
        Map<String, Map<String, ?>> worn = new HashMap<>();
        for (int i = 0; i < WORN.length; i++) {
            worn.put(EquipmentShape.SLOTS[i], detail(player.getItemBySlot(WORN[i])));
        }
        Map<String, List<Map<String, ?>>> curios = ModList.get().isLoaded("curios") ? Curios.read(player) : Map.of();
        return EquipmentShape.build(worn, curios);
    }

    static Map<String, ?> detail(ItemStack stack) {
        return stack.isEmpty() ? null : DetailRegistries.ITEM_STACK.getDetails(stack);
    }

    /** Only loaded when Curios is present, so the rest works without it. */
    private static final class Curios {
        static Map<String, List<Map<String, ?>>> read(ServerPlayer player) {
            Map<String, List<Map<String, ?>>> out = new HashMap<>();
            ICuriosItemHandler handler = CuriosApi.getCuriosHelper().getCuriosHandler(player).resolve().orElse(null);
            if (handler == null) return out;
            for (Map.Entry<String, ICurioStacksHandler> entry : handler.getCurios().entrySet()) {
                IDynamicStackHandler stacks = entry.getValue().getStacks();
                List<Map<String, ?>> slots = new ArrayList<>(stacks.getSlots());
                for (int i = 0; i < stacks.getSlots(); i++) slots.add(detail(stacks.getStackInSlot(i)));
                out.put(entry.getKey(), slots);
            }
            return out;
        }
    }
}
