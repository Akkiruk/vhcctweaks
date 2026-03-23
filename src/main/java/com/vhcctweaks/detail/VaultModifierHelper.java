package com.vhcctweaks.detail;

import iskallia.vault.config.gear.VaultGearTierConfig;
import iskallia.vault.gear.attribute.VaultGearAttributeInstance;
import iskallia.vault.gear.attribute.VaultGearModifier;
import iskallia.vault.gear.attribute.VaultGearModifier.AffixCategory;
import iskallia.vault.gear.attribute.VaultGearModifier.AffixCategorySet;
import iskallia.vault.gear.attribute.VaultGearModifier.AffixType;
import com.mojang.datafixers.util.Pair;
import iskallia.vault.gear.attribute.ability.AbilityAreaOfEffectFlatAttribute;
import iskallia.vault.gear.attribute.ability.AbilityAreaOfEffectPercentAttribute;
import iskallia.vault.gear.attribute.ability.AbilityCooldownFlatAttribute;
import iskallia.vault.gear.attribute.ability.AbilityCooldownPercentAttribute;
import iskallia.vault.gear.attribute.ability.AbilityLevelAttribute;
import iskallia.vault.gear.attribute.ability.AbilityManaCostFlatAttribute;
import iskallia.vault.gear.attribute.ability.AbilityManaCostPercentAttribute;
import iskallia.vault.gear.attribute.ability.AbilityFloatValueAttribute;
import iskallia.vault.gear.attribute.ability.special.base.SpecialAbilityGearAttribute;
import iskallia.vault.gear.attribute.config.IntegerAttributeGenerator;
import iskallia.vault.gear.attribute.custom.RandomGodVaultModifierAttribute;
import iskallia.vault.gear.attribute.custom.RelentlessStrikeAttribute;
import iskallia.vault.gear.attribute.custom.ability.AbilityTriggerOnDamageAttribute;
import iskallia.vault.gear.attribute.custom.ability.ArcaneNovaOnHitAttribute;
import iskallia.vault.gear.attribute.custom.effect.EffectAvoidanceGearAttribute;
import iskallia.vault.gear.attribute.custom.effect.EffectAvoidanceListGearAttribute;
import iskallia.vault.gear.attribute.custom.effect.EffectCloudAttribute;
import iskallia.vault.gear.attribute.custom.effect.EffectGearAttribute;
import iskallia.vault.gear.attribute.custom.effect.EffectTrialAttribute;
import iskallia.vault.gear.attribute.custom.loot.AbilityCastOnLootAttribute;
import iskallia.vault.gear.attribute.custom.loot.ManaPerLootAttribute;
import iskallia.vault.gear.attribute.talent.RandomVaultModifierAttribute;
import iskallia.vault.gear.attribute.talent.TalentLevelAttribute;
import iskallia.vault.gear.data.VaultGearData;
import iskallia.vault.gear.item.VaultGearItem;
import iskallia.vault.init.ModDynamicModels;
import iskallia.vault.init.ModGearAttributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Converts Vault Hunters gear modifiers and attributes into CC-friendly Map structures.
 */
public final class VaultModifierHelper {

    private VaultModifierHelper() {}

    // --- Modifier list builders ---

    public static List<Map<String, Object>> parseModifiers(ItemStack stack, VaultGearData data, AffixType type) {
        return data.getModifiers(type).stream()
                .map(mod -> parseModifier(stack, mod, data.getItemLevel()))
                .collect(Collectors.toList());
    }

    // --- Single modifier ---

    public static Map<String, Object> parseModifier(ItemStack stack, VaultGearModifier<?> modifier, int itemLevel) {
        Map<String, Object> map = new LinkedHashMap<>();

        String name = modifier.getAttribute().getReader().getModifierName();
        Object value = modifier.getValue();
        map.put("name", name);

        // Categories (legendary, crafted, frozen, etc.)
        AffixCategorySet cats = modifier.getCategories();
        addCategoryFlags(map, cats);

        // Modifier group
        String group = modifier.getModifierGroup();
        if (group != null && !group.isEmpty()) {
            map.put("group", group);
        }

        // Modifier identifier
        ResourceLocation modId = modifier.getModifierIdentifier();
        if (modId != null) {
            map.put("identifier", modId.toString());
        }

        // Deterministic modifier (no tier / tier == -1)
        if (modifier.getRolledTier() == -1) {
            putDeterministicValue(map, modifier);
            return map;
        }

        // Charm quirk: tier 0 with no rolled range
        if (stack.getItem() instanceof iskallia.vault.item.gear.VaultCharmItem && modifier.getRolledTier() == 0) {
            putDeterministicValue(map, modifier);
            return map;
        }

        // Rolled modifier with tier + range
        int tier = modifier.getRolledTier() + 1; // 0-indexed → 1-indexed
        map.put("tier", tier);

        // Try to get tier config for min/max range
        try {
            VaultGearTierConfig.ModifierConfigRange config = getConfigRange(stack, modifier, itemLevel);
            putRolledValue(map, value, config);
        } catch (Exception e) {
            // Fallback: value only, no range
            putSimpleValue(map, value);
        }

        return map;
    }

    // --- Attribute instance (from getAllAttributes) ---

    public static Map<String, Object> parseAttributeInstance(ItemStack stack, VaultGearAttributeInstance<?> instance, VaultGearData data) {
        // Handle well-known attribute types that have special display forms
        if (instance.getAttribute().equals(ModGearAttributes.CRAFTING_POTENTIAL)) {
            return simpleEntry("Crafting Potential", instance.getValue());
        }
        if (instance.getAttribute().equals(ModGearAttributes.MAX_CRAFTING_POTENTIAL)) {
            return simpleEntry("Max Crafting Potential", instance.getValue());
        }
        if (instance.getAttribute().equals(ModGearAttributes.GEAR_MODEL)) {
            ResourceLocation loc = (ResourceLocation) instance.getValue();
            String displayName = null;
            try {
                var model = ModDynamicModels.REGISTRIES.getModelByResourceLocation(loc);
                if (model.isPresent()) {
                    displayName = model.get().getDisplayName();
                }
            } catch (Exception ignored) {}
            return simpleEntry("Model", displayName != null ? displayName : loc.toString());
        }
        if (instance.getAttribute().equals(ModGearAttributes.PREFIXES)) {
            return simpleEntry("Prefixes", instance.getValue());
        }
        if (instance.getAttribute().equals(ModGearAttributes.SUFFIXES)) {
            return simpleEntry("Suffixes", instance.getValue());
        }
        if (instance.getAttribute().equals(ModGearAttributes.IS_LEGENDARY)) {
            return simpleEntry("Legendary", true);
        }
        if (instance.getAttribute().equals(ModGearAttributes.SOULBOUND)) {
            return simpleEntry("Soulbound", instance.getValue());
        }
        if (instance.getAttribute().equals(ModGearAttributes.UNIQUE_ITEM_KEY)) {
            return simpleEntry("Unique Key", instance.getValue().toString());
        }
        if (instance.getAttribute().equals(ModGearAttributes.GEAR_NAME)) {
            return simpleEntry("Gear Name", instance.getValue().toString());
        }
        if (instance.getAttribute().equals(ModGearAttributes.GEAR_UNIQUE_POOL)) {
            return simpleEntry("Unique Pool", instance.getValue().toString());
        }
        if (instance.getAttribute().equals(ModGearAttributes.GEAR_ROLL_TYPE)) {
            return simpleEntry("Roll Type", instance.getValue().toString());
        }

        // Try to cast down to a VaultGearModifier (many instances are in fact modifiers
        // that were demoted to the base class in the attribute list)
        try {
            VaultGearModifier<?> modifier = (VaultGearModifier<?>) instance;
            return parseModifier(stack, modifier, data.getItemLevel());
        } catch (ClassCastException e) {
            // Unknown attribute; return basic info
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", instance.getAttribute().getReader().getModifierName());
            map.put("value", String.valueOf(instance.getValue()));
            return map;
        }
    }

    // --- Helpers ---

    private static void addCategoryFlags(Map<String, Object> map, AffixCategorySet cats) {
        if (cats.contains(AffixCategory.LEGENDARY))           map.put("legendary", true);
        if (cats.contains(AffixCategory.CRAFTED))             map.put("crafted", true);
        if (cats.contains(AffixCategory.FROZEN))              map.put("frozen", true);
        if (cats.contains(AffixCategory.GREATER))             map.put("greater", true);
        if (cats.contains(AffixCategory.ABYSSAL))             map.put("abyssal", true);
        if (cats.contains(AffixCategory.CORRUPTED))           map.put("corrupted", true);
        if (cats.contains(AffixCategory.IMBUED))              map.put("imbued", true);
        if (cats.contains(AffixCategory.ABILITY_ENHANCEMENT)) map.put("abilityEnhancement", true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void putDeterministicValue(Map<String, Object> map, VaultGearModifier<?> modifier) {
        Object value = modifier.getValue();

        if (value instanceof ManaPerLootAttribute mpl) {
            Map<String, Object> sub = new LinkedHashMap<>();
            sub.put("manaGenerated", mpl.getManaGenerated());
            sub.put("manaGenerationChance", mpl.getManaGenerationChance());
            map.put("value", sub);
        } else if (value instanceof RandomGodVaultModifierAttribute rgvm) {
            Map<String, Object> sub = new LinkedHashMap<>();
            sub.put("modifier", rgvm.getModifier());
            sub.put("count", rgvm.getCount());
            sub.put("time", rgvm.getTime());
            map.put("value", sub);
        } else if (value instanceof AbilityLevelAttribute abilityLevel) {
            Map<String, Object> sub = new LinkedHashMap<>();
            sub.put("ability", abilityLevel.getAbility());
            sub.put("levelChange", abilityLevel.getLevelChange());
            map.put("value", sub);
        } else if (value instanceof TalentLevelAttribute talentLevel) {
            Map<String, Object> sub = new LinkedHashMap<>();
            sub.put("talent", talentLevel.getTalent());
            sub.put("levelChange", talentLevel.getLevelChange());
            map.put("value", sub);
        } else if (value instanceof AbilityFloatValueAttribute abilityFloat) {
            Map<String, Object> sub = new LinkedHashMap<>();
            sub.put("ability", abilityFloat.getAbilityKey());
            sub.put("amount", abilityFloat.getAmount());
            map.put("value", sub);
        } else if (value instanceof Pair<?,?> pair) {
            Map<String, Object> sub = new LinkedHashMap<>();
            sub.put("first", pair.getFirst());
            sub.put("second", pair.getSecond());
            map.put("value", sub);
        } else {
            putSimpleValue(map, value);
        }
    }

    private static void putRolledValue(Map<String, Object> map, Object value, VaultGearTierConfig.ModifierConfigRange config) {
        if (value instanceof Integer intVal) {
            map.put("value", intVal);
            try {
                map.put("min", ((IntegerAttributeGenerator.Range) config.minAvailableConfig()).min);
                map.put("max", ((IntegerAttributeGenerator.Range) config.maxAvailableConfig()).max);
            } catch (Exception ignored) {}
        } else if (value instanceof Float fVal) {
            map.put("value", fVal);
            try {
                map.put("min", (Float) FieldUtils.readField(config.minAvailableConfig(), "min", true));
                map.put("max", (Float) FieldUtils.readField(config.maxAvailableConfig(), "max", true));
            } catch (Exception ignored) {}
        } else if (value instanceof Double dVal) {
            map.put("value", dVal);
            try {
                map.put("min", (Double) FieldUtils.readField(config.minAvailableConfig(), "min", true));
                map.put("max", (Double) FieldUtils.readField(config.maxAvailableConfig(), "max", true));
            } catch (Exception ignored) {}
        } else if (value instanceof Boolean bVal) {
            map.put("value", bVal);
        } else if (value instanceof String sVal) {
            map.put("value", sVal);
        } else {
            // Complex attribute types: extract what we can
            putComplexValue(map, value, config);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void putComplexValue(Map<String, Object> map, Object value, VaultGearTierConfig.ModifierConfigRange config) {
        if (value instanceof ManaPerLootAttribute mpl) {
            Map<String, Object> sub = new LinkedHashMap<>();
            sub.put("manaGenerated", mpl.getManaGenerated());
            sub.put("manaGenerationChance", mpl.getManaGenerationChance());
            map.put("value", sub);
        } else if (value instanceof EffectAvoidanceListGearAttribute avoidList) {
            map.put("value", avoidList.toString());
        } else if (value instanceof EffectAvoidanceGearAttribute avoid) {
            map.put("value", avoid.toString());
        } else if (value instanceof AbilityLevelAttribute abilityLevel) {
            Map<String, Object> sub = new LinkedHashMap<>();
            sub.put("ability", abilityLevel.getAbility());
            sub.put("levelChange", abilityLevel.getLevelChange());
            map.put("value", sub);
            try {
                Object minCfg = config.minAvailableConfig();
                Object maxCfg = config.maxAvailableConfig();
                map.put("min", FieldUtils.readField(minCfg, "levelChange", true));
                map.put("max", FieldUtils.readField(maxCfg, "levelChange", true));
            } catch (Exception ignored) {}
        } else if (value instanceof TalentLevelAttribute talentLevel) {
            Map<String, Object> sub = new LinkedHashMap<>();
            sub.put("talent", talentLevel.getTalent());
            sub.put("levelChange", talentLevel.getLevelChange());
            map.put("value", sub);
        } else if (value instanceof RandomVaultModifierAttribute rvm) {
            Map<String, Object> sub = new LinkedHashMap<>();
            sub.put("modifier", rvm.getModifier().toString());
            sub.put("count", rvm.getCount());
            sub.put("time", rvm.getTime());
            map.put("value", sub);
        } else if (value instanceof AbilityCastOnLootAttribute castOnLoot) {
            Map<String, Object> sub = new LinkedHashMap<>();
            sub.put("ability", castOnLoot.getAbilityId());
            sub.put("chance", castOnLoot.getChance());
            sub.put("level", castOnLoot.getLevel());
            map.put("value", sub);
        } else if (value instanceof RelentlessStrikeAttribute relentless) {
            Map<String, Object> sub = new LinkedHashMap<>();
            sub.put("attackDamageIncrease", relentless.getAttackDamageIncrease());
            sub.put("maxStackSize", relentless.getMaxStackSize());
            map.put("value", sub);
        } else if (value instanceof ArcaneNovaOnHitAttribute arcaneNova) {
            Map<String, Object> sub = new LinkedHashMap<>();
            sub.put("radius", arcaneNova.getRadius());
            sub.put("percentAbilityPower", arcaneNova.getPercentAbilityPower());
            map.put("value", sub);
        } else if (value instanceof AbilityFloatValueAttribute abilityFloat) {
            // Covers AbilityCooldownFlatAttribute, AbilityManaCostFlatAttribute,
            // AbilityManaCostPercentAttribute, AbilityAreaOfEffectFlatAttribute, etc.
            Map<String, Object> sub = new LinkedHashMap<>();
            sub.put("ability", abilityFloat.getAbilityKey());
            sub.put("amount", abilityFloat.getAmount());
            map.put("value", sub);
        } else if (value instanceof EffectCloudAttribute cloud) {
            map.put("value", cloud.toString());
        } else if (value instanceof SpecialAbilityGearAttribute special) {
            map.put("value", special.toString());
        } else if (value instanceof AbilityTriggerOnDamageAttribute trigger) {
            map.put("value", trigger.toString());
        } else if (value instanceof EffectGearAttribute effect) {
            map.put("value", effect.toString());
        } else if (value instanceof EffectTrialAttribute trial) {
            map.put("value", trial.toString());
        } else if (value instanceof Pair<?,?> pair) {
            // ON_KILL_HEAL, BLOCK_HEAL_ON_SUCCESS: Pair<Float, Float>
            Map<String, Object> sub = new LinkedHashMap<>();
            sub.put("first", pair.getFirst());
            sub.put("second", pair.getSecond());
            map.put("value", sub);
        } else {
            // Last resort: string representation
            map.put("value", value.toString());
        }
    }

    private static void putSimpleValue(Map<String, Object> map, Object value) {
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            map.put("value", value);
        } else {
            map.put("value", String.valueOf(value));
        }
    }

    private static Map<String, Object> simpleEntry(String name, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            map.put("value", value);
        } else {
            map.put("value", String.valueOf(value));
        }
        return map;
    }

    private static VaultGearTierConfig.ModifierConfigRange getConfigRange(
            ItemStack stack, VaultGearModifier<?> modifier, int level) {
        return VaultGearTierConfig.getConfig(stack)
                .map(tierCfg -> tierCfg.getTierConfigRange(modifier, level))
                .orElse(VaultGearTierConfig.ModifierConfigRange.empty());
    }
}
