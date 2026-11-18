package com.vhcctweaks.detail;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A modifier whose value is a pair. Every pair keeps first and second; the heal pairs also say which is which.
 * Vault Hunters builds Heal on Kill and Heal on Block with pairGenerator(..., "chance", "amount"): the first number
 * is the chance and the second the amount healed.
 */
final class PairLabels {

    private static final Set<String> CHANCE_THEN_AMOUNT = Set.of("the_vault:on_kill_heal", "the_vault:block_heal");

    private PairLabels() {}

    static Map<String, Object> pair(String attributeId, Object first, Object second) {
        Map<String, Object> sub = new LinkedHashMap<>();
        sub.put("first", first);
        sub.put("second", second);
        if (attributeId != null && CHANCE_THEN_AMOUNT.contains(attributeId)) {
            sub.put("chance", first);
            sub.put("amount", second);
        }
        return sub;
    }
}
