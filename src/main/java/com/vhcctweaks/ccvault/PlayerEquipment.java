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
        Map<String, Object> result = EquipmentShape.build(worn, curios);
        hotbar(player, result);
        return result;
    }

    /**
     * 2.2.37: the hotbar, which slot is held, and the hotbar weapon Gear Power counts, which is not always the held
     * one. The weapon is Vault Hunters' own pick, from its Gear Power explanation.
     */
    private static void hotbar(ServerPlayer player, Map<String, Object> result) {
        Map<Integer, Object> hotbar = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            Map<String, ?> detail = detail(player.getInventory().getItem(i));
            if (detail != null) hotbar.put(i + 1, detail);
        }
        result.put("hotbar", hotbar);
        int selected = player.getInventory().selected;
        if (selected >= 0 && selected < 9) result.put("selectedHotbarSlot", selected + 1);
        Integer weapon = VaultPlayers.attempt("counted weapon", () -> CountedWeapon.hotbarSlot(player));
        if (weapon != null && weapon >= 0 && weapon < 9) {
            Map<String, ?> detail = detail(player.getInventory().getItem(weapon));
            if (detail != null) {
                result.put("weapon", detail);
                result.put("weaponHotbarSlot", weapon + 1);
            }
        }
    }

    /** Names the 3.21.74 classes, so an older Vault Hunters fails inside attempt. */
    private static final class CountedWeapon {
        static Integer hotbarSlot(ServerPlayer player) {
            for (iskallia.vault.gear.power.GearPowerBreakdown.SlotBreakdown slot : iskallia.vault.gear.power.GearPower.explain(player).slots()) {
                if (slot.slot() == EquipmentSlot.MAINHAND) return slot.hotbarSlot() >= 0 ? slot.hotbarSlot() : null;
            }
            return null;
        }
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
