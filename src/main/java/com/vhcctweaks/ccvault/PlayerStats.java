package com.vhcctweaks.ccvault;

import com.vhcctweaks.util.Safety;
import iskallia.vault.gear.attribute.VaultGearAttribute;
import iskallia.vault.gear.attribute.type.VaultGearAttributeTypeMerger;
import iskallia.vault.gear.power.vigor.VigorModifier;
import iskallia.vault.gear.reader.IncreasedPercentageReader;
import iskallia.vault.gear.reader.VaultGearModifierReader;
import iskallia.vault.init.ModGearAttributes;
import iskallia.vault.mana.Mana;
import iskallia.vault.snapshot.AttributeSnapshot;
import iskallia.vault.snapshot.AttributeSnapshotHelper;
import iskallia.vault.util.calc.AbilityPowerHelper;
import iskallia.vault.util.calc.AreaOfEffectHelper;
import iskallia.vault.util.calc.AttributeLimitHelper;
import iskallia.vault.util.calc.BlockChanceHelper;
import iskallia.vault.util.calc.CooldownHelper;
import iskallia.vault.util.calc.CopiousHelper;
import iskallia.vault.util.calc.DoubleHitHelper;
import iskallia.vault.util.calc.DurabilityWearReductionHelper;
import iskallia.vault.util.calc.LuckyHitHelper;
import iskallia.vault.util.calc.MagnetRangeHelper;
import iskallia.vault.util.calc.ResistanceHelper;
import iskallia.vault.util.calc.SoulChanceHelper;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * An online player's stat totals, for ccvault.getPlayerStats (2.2.37): the rows of Vault Hunters' stat screen, worked
 * out with the same helpers the screen and Vault Hunters' own web stats page (iskallia.vault.web.WebPlayerStats) call,
 * in the same way. Each row is read on its own, so one that fails is left out. Server thread only.
 */
final class PlayerStats {

    static final String NOT_AVAILABLE = "stats not available";

    private PlayerStats() {}

    static Map<String, Object> read(ServerPlayer p) {
        Map<String, Object> out = new HashMap<>();
        Rows rows = new Rows(out);
        rows.of(() -> ModGearAttributes.ARMOR, p::getArmorValue);
        rows.of(() -> ModGearAttributes.ATTACK_DAMAGE, () -> p.getAttributeValue(Attributes.ATTACK_DAMAGE));
        rows.of(() -> ModGearAttributes.ABILITY_POWER, () -> AbilityPowerHelper.getAbilityPower(p));
        rows.of(() -> ModGearAttributes.ATTACK_SPEED, () -> p.getAttributeValue(Attributes.ATTACK_SPEED) - 4.0);
        rows.of(() -> ModGearAttributes.REACH, p::getReachDistance);
        rows.of(() -> ModGearAttributes.ATTACK_RANGE, p::getAttackRange);
        rows.of(() -> ModGearAttributes.DOUBLE_HIT_CHANCE, () -> DoubleHitHelper.getDoubleHitChance(p));
        rows.of(() -> ModGearAttributes.KNOCKBACK_RESISTANCE, () -> (float) p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        rows.of(() -> ModGearAttributes.HEALTH, () -> VigorModifier.baseMaxHealth(p));
        rows.summed(p, () -> ModGearAttributes.HEALING_EFFECTIVENESS);
        rows.of(() -> ModGearAttributes.MANA_REGEN_ADDITIVE_PERCENTILE, () -> Mana.getRegenPerSecond(p));
        rows.of(() -> ModGearAttributes.MANA_ADDITIVE, () -> Mth.floor(Mana.getMax(p)));
        rows.capped(() -> ModGearAttributes.COOLDOWN_REDUCTION, () -> CooldownHelper.getCooldownMultiplierUnlimited(p),
                () -> AttributeLimitHelper.getCooldownReductionLimit(p));
        rows.summed(p, () -> ModGearAttributes.EFFECT_DURATION);
        rows.capped(() -> ModGearAttributes.RESISTANCE, () -> ResistanceHelper.getResistance(p, null),
                () -> AttributeLimitHelper.getResistanceLimit(p));
        rows.capped(() -> ModGearAttributes.BLOCK, () -> BlockChanceHelper.getBlockChanceUnlimited(p),
                () -> AttributeLimitHelper.getBlockChanceLimit(p));
        rows.summed(p, () -> ModGearAttributes.CRITICAL_HIT_TAKEN_REDUCTION);
        rows.capped(() -> ModGearAttributes.DURABILITY_WEAR_REDUCTION, () -> DurabilityWearReductionHelper.getDurabilityWearReductionUnlimited(p),
                () -> AttributeLimitHelper.getDurabilityWearReductionLimit(p));
        rows.capped(() -> ModGearAttributes.AREA_OF_EFFECT, () -> AreaOfEffectHelper.getAreaOfEffectUnlimited(p),
                () -> AttributeLimitHelper.getAreaOfEffectLimit(p));
        rows.summed(p, () -> ModGearAttributes.THORNS_DAMAGE_FLAT);
        rows.of(() -> ModGearAttributes.ON_HIT_CHAIN,
                () -> snapshot(p).getAttributeValue(ModGearAttributes.ON_HIT_CHAIN, VaultGearAttributeTypeMerger.intSum()));
        rows.summed(p, () -> ModGearAttributes.ON_HIT_STUN);
        rows.summed(p, () -> ModGearAttributes.SHOCKING_HIT_CHANCE);
        rows.summed(p, () -> ModGearAttributes.SWEEPING_HIT_CHANCE);
        rows.summed(p, () -> ModGearAttributes.ITEM_QUANTITY);
        rows.summed(p, () -> ModGearAttributes.ITEM_RARITY);
        rows.summed(p, () -> ModGearAttributes.TRAP_DISARMING);
        rows.of(() -> ModGearAttributes.SOUL_QUANTITY, () -> SoulChanceHelper.getSoulChance(p));
        rows.capped(() -> ModGearAttributes.LUCKY_HIT_CHANCE, () -> LuckyHitHelper.getLuckyHitChanceUnlimited(p),
                () -> LuckyHitHelper.getLuckyHitChance(p));
        rows.of(() -> ModGearAttributes.COPIOUSLY, () -> CopiousHelper.getCopiousChance(p));
        rows.summed(p, () -> ModGearAttributes.MINING_SPEED);
        rows.summed(p, () -> ModGearAttributes.MINING_SPEED_PERCENT);
        rows.summed(p, () -> ModGearAttributes.MOVEMENT_SPEED);
        rows.named("Damage Increase", () -> ModGearAttributes.DAMAGE_INCREASE,
                () -> snapshot(p).getAttributeValue(ModGearAttributes.DAMAGE_INCREASE, VaultGearAttributeTypeMerger.floatSum()));
        rows.summed(p, () -> ModGearAttributes.DAMAGE_CHAMPION);
        rows.summed(p, () -> ModGearAttributes.DAMAGE_ASSASSIN);
        rows.summed(p, () -> ModGearAttributes.DAMAGE_DUNGEON);
        rows.summed(p, () -> ModGearAttributes.DAMAGE_HORDE);
        rows.summed(p, () -> ModGearAttributes.DAMAGE_TANK);
        rows.summed(p, () -> ModGearAttributes.DAMAGE_DWELLER);
        rows.summed(p, () -> ModGearAttributes.HIT_HEARTS);
        rows.summed(p, () -> ModGearAttributes.FRUIT_EFFECTIVENESS);
        rows.named("Pickup Range", () -> ModGearAttributes.RANGE, () -> MagnetRangeHelper.getRange(p));
        rows.summed(p, () -> ModGearAttributes.VELOCITY);
        rows.extras(p);
        return out;
    }

    private static AttributeSnapshot snapshot(ServerPlayer player) {
        return AttributeSnapshotHelper.getInstance().getSnapshot(player);
    }

    /** Builds the rows; every row is its own attempt. */
    private static final class Rows {
        private final Map<String, Object> out;

        Rows(Map<String, Object> out) {
            this.out = out;
        }

        void of(Supplier<VaultGearAttribute<?>> attribute, Supplier<? extends Number> value) {
            named(null, attribute, value);
        }

        void named(String name, Supplier<VaultGearAttribute<?>> attribute, Supplier<? extends Number> value) {
            attempt(() -> {
                VaultGearAttribute<?> attr = attribute.get();
                Number v = value.get();
                put(attr, name, v, null, null, false);
            });
        }

        void summed(ServerPlayer player, Supplier<VaultGearAttribute<Float>> attribute) {
            attempt(() -> {
                VaultGearAttribute<Float> attr = attribute.get();
                put(attr, null, snapshot(player).getAttributeValue(attr, VaultGearAttributeTypeMerger.floatSum()), null, null, false);
            });
        }

        void capped(Supplier<VaultGearAttribute<?>> attribute, Supplier<Float> value, Supplier<Float> cap) {
            attempt(() -> {
                VaultGearAttribute<?> attr = attribute.get();
                float raw = value.get();
                float limit = cap.get();
                float shown = limit < raw ? limit : raw;
                put(attr, null, shown, limit, raw, false);
            });
        }

        /** What the player has from gear that no fixed row covers, summed, as the screen adds them. */
        void extras(ServerPlayer player) {
            attempt(() -> {
                AttributeSnapshot snapshot = snapshot(player);
                for (VaultGearAttribute<?> attribute : snapshot.getAttributes()) {
                    ResourceLocation id = attribute.getRegistryName();
                    if (id == null || out.containsKey(id.toString())) continue;
                    if (attribute == ModGearAttributes.DURABILITY || attribute == ModGearAttributes.JEWEL_SIZE) continue;
                    if (attribute.getReader().getModifierName().isEmpty()) continue;
                    attempt(() -> extra(snapshot, attribute));
                }
            });
        }

        private <T> void extra(AttributeSnapshot snapshot, VaultGearAttribute<T> attribute) {
            List<T> values = snapshot.getAttributeValueList(attribute);
            if (values.isEmpty() || !values.stream().allMatch(v -> v instanceof Number)) return;
            Object first = values.get(0);
            double total = values.stream().mapToDouble(v -> ((Number) v).doubleValue()).sum();
            Number sum;
            if (first instanceof Integer) sum = values.stream().mapToInt(v -> (Integer) v).sum();
            else if (first instanceof Float) sum = (float) total;
            else if (first instanceof Double) sum = total;
            else return;
            VaultGearModifierReader<T> reader = attribute.getReader();
            String name = reader instanceof IncreasedPercentageReader ? "Increased " + reader.getModifierName() : reader.getModifierName();
            put(attribute, name, sum, null, null, true);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void put(VaultGearAttribute<?> attribute, String name, Number value, Float limit, Float uncapped, boolean extra) {
            ResourceLocation id = attribute.getRegistryName();
            if (id == null || value == null) return;
            Map<String, Object> row = new HashMap<>();
            VaultGearModifierReader reader = attribute.getReader();
            row.put("name", name != null ? name : reader.getModifierName());
            row.put("value", value.doubleValue());
            try {
                MutableComponent text = reader.getValueDisplay(value);
                if (text != null) row.put("display", text.getString());
            } catch (RuntimeException e) {
                // A reader that wants another number type: the row stays, without its text
            }
            if (limit != null) {
                row.put("limit", limit.doubleValue());
                row.put("uncapped", uncapped.doubleValue());
                row.put("hitLimit", uncapped > limit);
            }
            if (extra) row.put("extra", true);
            out.put(id.toString(), row);
        }

        private static void attempt(Runnable row) {
            try {
                row.run();
            } catch (RuntimeException | LinkageError e) {
                Safety.logOnce("PlayerStats row", e);
            }
        }
    }

    /** The coded caps of Vault Hunters 3.21.74's AttributeLimitHelper: { [attributeId] = { base, max } }. */
    static Map<String, Object> caps() {
        Map<String, Object> out = new HashMap<>();
        cap(out, () -> ModGearAttributes.COOLDOWN_REDUCTION, 0.8, 0.95);
        cap(out, () -> ModGearAttributes.BLOCK, 0.6, 0.95);
        cap(out, () -> ModGearAttributes.RESISTANCE, 0.5, 0.95);
        cap(out, () -> ModGearAttributes.AREA_OF_EFFECT, 0.8, 0.8);
        cap(out, () -> ModGearAttributes.DURABILITY_WEAR_REDUCTION, 0.5, 1.0);
        cap(out, () -> ModGearAttributes.LUCKY_HIT_CHANCE, 0.562, 1.0);
        return out;
    }

    private static void cap(Map<String, Object> out, Supplier<VaultGearAttribute<?>> attribute, double base, double max) {
        try {
            ResourceLocation id = attribute.get().getRegistryName();
            if (id != null) out.put(id.toString(), Map.of("base", base, "max", max));
        } catch (RuntimeException | LinkageError e) {
            Safety.logOnce("stat caps", e);
        }
    }
}
