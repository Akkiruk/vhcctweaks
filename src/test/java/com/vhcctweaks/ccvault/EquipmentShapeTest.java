package com.vhcctweaks.ccvault;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class EquipmentShapeTest {

    private static Map<String, ?> item(String name) {
        return Map.of("name", name, "count", 1);
    }

    @Test
    void wornItemsGoUnderTheirSlotAndEmptySlotsAreLeftOut() {
        Map<String, Map<String, ?>> worn = new HashMap<>();
        Map<String, ?> helmet = item("the_vault:helmet");
        worn.put("head", helmet);
        worn.put("chest", null);
        worn.put("mainhand", item("the_vault:sword"));

        Map<String, Object> result = EquipmentShape.build(worn, Map.of());
        assertSame(helmet, result.get("head"));
        assertEquals(item("the_vault:sword"), result.get("mainhand"));
        assertFalse(result.containsKey("chest"));
        assertFalse(result.containsKey("legs"));
        assertEquals(Map.of(), result.get("curios"));
    }

    @Test
    void curiosAreByTypeThenOneBasedIndexSkippingEmptySlotsAndEmptyTypes() {
        Map<String, List<Map<String, ?>>> curios = new HashMap<>();
        curios.put("ring", Arrays.asList(null, item("the_vault:ring")));
        curios.put("necklace", Arrays.asList(item("the_vault:necklace")));
        curios.put("belt", Arrays.asList((Map<String, ?>) null));

        Map<?, ?> byType = (Map<?, ?>) EquipmentShape.build(Map.of(), curios).get("curios");
        assertEquals(Map.of(2, item("the_vault:ring")), byType.get("ring"));
        assertEquals(Map.of(1, item("the_vault:necklace")), byType.get("necklace"));
        assertFalse(byType.containsKey("belt"));
    }

    @Test
    void nothingWornIsJustAnEmptyCuriosTable() {
        assertEquals(Map.of("curios", Map.of()), EquipmentShape.build(Map.of(), Map.of()));
    }
}
