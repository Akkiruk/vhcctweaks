package com.vhcctweaks.ccvault;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The table ccvault.getPlayerEquipment returns, built from details already read:
 * { head=, chest=, legs=, feet=, mainhand=, offhand=, curios = { [slotType] = { [index] = detail } } }.
 * Empty slots are left out, Curios indexes start at 1, and a Curios slot type with nothing in it is left out too.
 * Kept free of the game so it can be tested on its own.
 */
final class EquipmentShape {

    static final String[] SLOTS = {"head", "chest", "legs", "feet", "mainhand", "offhand"};

    private EquipmentShape() {}

    /**
     * @param worn   slot name to detail, null for an empty slot
     * @param curios slot type to its slots in order, null for an empty slot
     */
    static Map<String, Object> build(Map<String, Map<String, ?>> worn, Map<String, List<Map<String, ?>>> curios) {
        Map<String, Object> result = new HashMap<>();
        for (String slot : SLOTS) {
            Map<String, ?> detail = worn.get(slot);
            if (detail != null) result.put(slot, detail);
        }
        Map<String, Object> byType = new HashMap<>();
        curios.forEach((type, slots) -> {
            Map<Integer, Object> filled = new HashMap<>();
            for (int i = 0; i < slots.size(); i++) {
                if (slots.get(i) != null) filled.put(i + 1, slots.get(i));
            }
            if (!filled.isEmpty()) byType.put(type, filled);
        });
        result.put("curios", byType);
        return result;
    }
}
