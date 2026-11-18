package com.vhcctweaks.detail;

import com.vhcctweaks.config.ModConfig;
import com.vhcctweaks.util.Safety;
import iskallia.vault.gear.item.VaultGearItem;
import iskallia.vault.gear.power.GearPowerBreakdown;
import iskallia.vault.gear.power.NaturalRange;
import iskallia.vault.init.ModConfigs;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One piece's Gear Score, from Vault Hunters' own scorer (3.21.74). Vault Hunters only scores a player's worn gear,
 * so this asks its private per-stack explanation by reflection, as a player of any level and greed tier would wear
 * the piece: gear never goes above level 100, so that is the score the piece has for anyone who can use it.
 *
 * A Vault Hunters without the scorer, a scorer that changed shape, or any failure inside it means no gearScore
 * fields. Nothing here throws, and the stack is never changed (the scorer reads it and keeps a copy).
 */
final class GearScores {

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static volatile boolean unavailable;

    private GearScores() {}

    /** Adds the gearScore fields to an identified piece's details, when the config asks for them and they can be had. */
    static void put(Map<String, Object> gear, ItemStack stack) {
        if (unavailable) return;
        try {
            if (!ModConfig.DETAILS_GEAR_SCORE.get()) return;
            boolean withLines = ModConfig.DETAILS_GEAR_SCORE_LINES.get();
            GearScoreShape.put(gear, Scorer.explain(stack), withLines);
        } catch (LinkageError | ReflectiveOperationException e) {
            // This Vault Hunters has no scorer, or not this one: stop asking
            unavailable = true;
            Safety.logOnce("gear score (not available in this Vault Hunters)", e);
        } catch (RuntimeException e) {
            Safety.logOnce("gear score", e);
        }
    }

    /** Everything that names the 3.21.74 classes, so an older Vault Hunters fails here and not in the provider. */
    private static final class Scorer {

        private static Method explainStack;

        static GearScoreShape.Piece explain(ItemStack stack) throws ReflectiveOperationException {
            VaultGearItem item = VaultGearItem.of(stack);
            EquipmentSlot slot = null;
            for (EquipmentSlot candidate : SLOTS) {
                if (item.isIntendedForSlot(stack, candidate)) {
                    slot = candidate;
                    break;
                }
            }
            if (slot == null) return null;

            Object explained;
            try {
                explained = method().invoke(null, slot, stack, Integer.MAX_VALUE, Integer.MAX_VALUE, ModConfigs.GEAR_POWER);
            } catch (java.lang.reflect.InvocationTargetException e) {
                // The scorer itself failed on this piece: that piece gets no score, the next one is still asked
                throw new IllegalStateException("Vault Hunters could not score this piece", e.getCause());
            }
            if (!(explained instanceof GearPowerBreakdown.SlotBreakdown breakdown)) return null;

            AtLevel atLevel = null;
            try {
                atLevel = AtLevel.of(stack);
            } catch (ReflectiveOperationException | RuntimeException e) {
                Safety.logOnce("gear score ranges at level", e);
            }
            List<GearScoreShape.Line> lines = new ArrayList<>(breakdown.lines().size());
            for (GearPowerBreakdown.LineScore line : breakdown.lines()) {
                NaturalRange range = line.naturalRange();
                NaturalRange here = range == null || atLevel == null ? null : atLevel.find(line.affixType(), line.attribute());
                lines.add(new GearScoreShape.Line(line.affixType().name(), line.ratio(),
                        range == null ? null : range.best(), range == null ? null : range.worst(),
                        here == null ? null : here.best(), here == null ? null : here.worst()));
            }
            return new GearScoreShape.Piece(breakdown.status().name(), breakdown.score(), breakdown.expectedLineCount(),
                    ModConfigs.GEAR_POWER.getPerfectSlotScore(), lines);
        }

        /**
         * The natural ranges of a gear type at one item level (2.2.37), built the way Vault Hunters' GearTypeReference
         * builds them at its reference level 100, with its own private readNaturalRange, and kept per tier config,
         * Gear Power config and level.
         */
        private record AtLevel(Map<String, NaturalRange> byAffix, Map<net.minecraft.resources.ResourceLocation, NaturalRange> byAttribute) {

            /** Per tier config (weakly, so a config reload lets the old one go): the Gear Power config it was built with, and the ranges by level. */
            private record Built(Object power, Map<Integer, AtLevel> byLevel) {}

            private static final Map<Object, Built> CACHE = new java.util.WeakHashMap<>();
            private static Method readNaturalRange;

            NaturalRange find(iskallia.vault.gear.attribute.VaultGearModifier.AffixType affix, net.minecraft.resources.ResourceLocation attribute) {
                if (attribute == null) return null;
                NaturalRange range = byAffix.get(affix.name() + "|" + attribute);
                return range != null ? range : byAttribute.get(attribute);
            }

            static AtLevel of(ItemStack stack) throws ReflectiveOperationException {
                net.minecraft.resources.ResourceLocation key = stack.getItem().getRegistryName();
                if (key == null || ModConfigs.VAULT_GEAR_CONFIG == null) return null;
                Object config = ((Map<?, ?>) ModConfigs.VAULT_GEAR_CONFIG).get(key);
                iskallia.vault.config.GearPowerConfig power = ModConfigs.GEAR_POWER;
                if (!(config instanceof iskallia.vault.config.gear.VaultGearTierConfig tiers) || power == null) return null;
                int level = iskallia.vault.gear.data.VaultGearData.read(stack).getItemLevel();
                synchronized (CACHE) {
                    Built built = CACHE.get(tiers);
                    AtLevel hit = built == null || built.power() != power ? null : built.byLevel().get(level);
                    if (hit != null) return hit;
                }
                AtLevel made = build(key, tiers, power, level);
                synchronized (CACHE) {
                    Built built = CACHE.get(tiers);
                    if (built == null || built.power() != power) {
                        built = new Built(power, new java.util.HashMap<>());
                        CACHE.put(tiers, built);
                    }
                    built.byLevel().put(level, made);
                }
                return made;
            }

            private static AtLevel build(net.minecraft.resources.ResourceLocation key, iskallia.vault.config.gear.VaultGearTierConfig tiers,
                                         iskallia.vault.config.GearPowerConfig power, int level) throws ReflectiveOperationException {
                Map<String, NaturalRange> byAffix = new java.util.HashMap<>();
                Map<net.minecraft.resources.ResourceLocation, NaturalRange> byAttribute = new java.util.HashMap<>();
                Object[][] groups = {
                        {iskallia.vault.gear.attribute.VaultGearModifier.AffixType.IMPLICIT, iskallia.vault.config.gear.VaultGearTierConfig.ModifierAffixTagGroup.IMPLICIT},
                        {iskallia.vault.gear.attribute.VaultGearModifier.AffixType.PREFIX, iskallia.vault.config.gear.VaultGearTierConfig.ModifierAffixTagGroup.PREFIX},
                        {iskallia.vault.gear.attribute.VaultGearModifier.AffixType.SUFFIX, iskallia.vault.config.gear.VaultGearTierConfig.ModifierAffixTagGroup.SUFFIX}};
                for (Object[] pair : groups) {
                    var affix = (iskallia.vault.gear.attribute.VaultGearModifier.AffixType) pair[0];
                    var group = tiers.getModifierGroup((iskallia.vault.config.gear.VaultGearTierConfig.ModifierAffixTagGroup) pair[1]);
                    if (group == null) continue;
                    for (iskallia.vault.config.gear.VaultGearTierConfig.ModifierTierGroup tierGroup : group) {
                        net.minecraft.resources.ResourceLocation attribute = tierGroup.getAttribute();
                        Object read = reader().invoke(null, key, tierGroup, level, power.isLowerBetter(attribute), power.getValueOffset(attribute));
                        if (read instanceof java.util.Optional<?> optional && optional.orElse(null) instanceof NaturalRange range) {
                            byAffix.merge(affix.name() + "|" + attribute, range, NaturalRange::union);
                            byAttribute.merge(attribute, range, NaturalRange::union);
                        }
                    }
                }
                return new AtLevel(byAffix, byAttribute);
            }

            private static synchronized Method reader() throws ReflectiveOperationException {
                if (readNaturalRange == null) {
                    Method found = iskallia.vault.gear.power.GearTypeReference.class.getDeclaredMethod("readNaturalRange",
                            net.minecraft.resources.ResourceLocation.class, iskallia.vault.config.gear.VaultGearTierConfig.ModifierTierGroup.class,
                            int.class, boolean.class, double.class);
                    found.setAccessible(true);
                    readNaturalRange = found;
                }
                return readNaturalRange;
            }
        }

        private static synchronized Method method() throws ReflectiveOperationException {
            if (explainStack == null) {
                Method found = Class.forName("iskallia.vault.gear.power.GearPowerScorer").getDeclaredMethod("explainStack",
                        EquipmentSlot.class, ItemStack.class, int.class, int.class, iskallia.vault.config.GearPowerConfig.class);
                found.setAccessible(true);
                explainStack = found;
            }
            return explainStack;
        }
    }
}
