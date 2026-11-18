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

            List<GearScoreShape.Line> lines = new ArrayList<>(breakdown.lines().size());
            for (GearPowerBreakdown.LineScore line : breakdown.lines()) {
                NaturalRange range = line.naturalRange();
                lines.add(new GearScoreShape.Line(line.affixType().name(), line.ratio(),
                        range == null ? null : range.best(), range == null ? null : range.worst()));
            }
            return new GearScoreShape.Piece(breakdown.status().name(), breakdown.score(), breakdown.expectedLineCount(),
                    ModConfigs.GEAR_POWER.getPerfectSlotScore(), lines);
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
