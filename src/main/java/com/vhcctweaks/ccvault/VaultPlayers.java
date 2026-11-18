package com.vhcctweaks.ccvault;

import com.vhcctweaks.util.Safety;
import iskallia.vault.core.vault.influence.VaultGod;
import iskallia.vault.gear.power.GearPower;
import iskallia.vault.gear.power.GearPowerBreakdown;
import iskallia.vault.gear.power.GearPowerResult;
import iskallia.vault.gear.power.GearPowerTracker;
import iskallia.vault.gear.power.temper.TemperCategory;
import iskallia.vault.gear.power.temper.TemperLadder;
import iskallia.vault.gear.power.temper.TemperView;
import iskallia.vault.init.ModConfigs;
import iskallia.vault.util.SidedHelper;
import iskallia.vault.world.data.PlayerReputationData;
import iskallia.vault.world.data.TemperData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * What Vault Hunters 3.21.74 knows about a player beyond their level: Gear Power, Temper and god reputation.
 * Read-only, server thread only.
 *
 * - A player on this server: Vault Hunters' live data.
 * - Anyone else, including a player another server of the network calls online: what this server saved for them,
 *   read from the file and never loaded into Vault Hunters.
 *
 * Every call into Vault Hunters is made on its own behind {@link #attempt}: one that fails, or a Vault Hunters without
 * these classes, drops that field or answers nil and a reason. Nothing here throws.
 */
final class VaultPlayers {

    static final String NO_DATA = VaultLevelReply.NO_DATA;
    static final String OFFLINE = "player offline";
    static final String NO_GEAR_POWER = "gear power not available";
    static final String NO_TEMPER = "temper not available";
    static final String NO_REPUTATION = "reputation not available";

    private VaultPlayers() {}

    /** For ccvault.getPlayerGearPower. */
    static Object[] gearPower(MinecraftServer server, UUID uuid) {
        ServerPlayer here = Safety.here(server, uuid);
        if (here == null) {
            VaultLevels.SavedProfile saved = VaultLevels.savedProfile(server, uuid);
            if (saved == null || (saved.level() == null && saved.temper() == null)) return new Object[]{null, NO_DATA};
            return new Object[]{GearPowerShape.offline(saved.level(), saved.temper() == null ? null : saved.temper().peak())};
        }
        Map<String, Object> live = attempt("gear power", () -> Live.gearPower(server, here));
        return live == null ? new Object[]{null, NO_GEAR_POWER} : new Object[]{live};
    }

    /** For ccvault.getPlayerTemper. */
    static Object[] temper(MinecraftServer server, UUID uuid) {
        ServerPlayer here = Safety.here(server, uuid);
        if (here == null) {
            VaultLevels.SavedProfile saved = VaultLevels.savedProfile(server, uuid);
            if (saved == null || (saved.level() == null && saved.temper() == null)) return new Object[]{null, NO_DATA};
            SavedTemper temper = saved.temper() != null ? saved.temper()
                    : new SavedTemper(0L, new int[TemperShape.CATEGORIES.length], 0, 0);
            double[] multipliers = attempt("temper ladder", () -> Live.multipliers(temper.levels()));
            double[] powers = multipliers == null ? null : attempt("temper powers", () -> Live.powers(multipliers));
            return new Object[]{TemperShape.build(false, temper.dust(), temper.levels(), multipliers, powers,
                    temper.peak(), null, temper.trialsRecord(), null)};
        }
        Map<String, Object> live = attempt("temper", () -> Live.temper(server, here));
        return live == null ? new Object[]{null, NO_TEMPER} : new Object[]{live};
    }

    /** For ccvault.getPlayerReputation. Vault Hunters only has this for a player it has loaded. */
    static Object[] reputation(MinecraftServer server, UUID uuid) {
        ServerPlayer here = Safety.here(server, uuid);
        if (here == null) return new Object[]{null, OFFLINE};
        Map<String, Object> live = attempt("reputation", () -> Live.reputation(here.getUUID()));
        return live == null ? new Object[]{null, NO_REPUTATION} : new Object[]{live};
    }

    /** One call into Vault Hunters. A failure is logged once per place and becomes null. */
    static <T> T attempt(String what, Supplier<T> call) {
        try {
            return call.get();
        } catch (RuntimeException | LinkageError e) {
            Safety.logOnce("VaultPlayers " + what, e);
            return null;
        }
    }

    /** Everything that names a Vault Hunters class, so a Vault Hunters without them fails inside attempt. */
    private static final class Live {

        static Map<String, Object> gearPower(MinecraftServer server, ServerPlayer player) {
            UUID uuid = player.getUUID();
            GearPowerResult result = GearPowerTracker.peek(uuid).orElseGet(() -> GearPower.get(player));
            Integer level = attempt("vault level", () -> SidedHelper.getVaultLevel(player));
            Integer minLevel = attempt("gear power min level", () -> ModConfigs.GEAR_POWER.getMinLevel());
            Integer greedTier = attempt("greed tier", () -> SidedHelper.getGreedTier(player));
            Integer peak = attempt("peak gear power", () -> TemperData.get(server).peakGearPower(uuid));
            List<GearPowerShape.Slot> slots = result.shown() ? attempt("gear power slots", () -> slots(player)) : null;
            return GearPowerShape.online(result.shown(), result.slotScores(), result.gearScore(),
                    result.roundedGearScore(), result.gearPower(), result.temper(),
                    result.might(), result.magic(), result.vigor(), level, minLevel, greedTier, peak, slots);
        }

        /** Vault Hunters' own explanation, for which item counts in each slot and why a slot scores nothing. */
        private static List<GearPowerShape.Slot> slots(ServerPlayer player) {
            GearPowerBreakdown breakdown = GearPower.explain(player);
            List<GearPowerShape.Slot> out = new ArrayList<>();
            for (GearPowerBreakdown.SlotBreakdown slot : breakdown.slots()) {
                ItemStack stack = slot.stack();
                boolean empty = stack == null || stack.isEmpty();
                ResourceLocation id = empty ? null : stack.getItem().getRegistryName();
                out.add(new GearPowerShape.Slot(
                        slot.status().name(),
                        slot.score(),
                        id == null ? null : id.toString(),
                        empty ? null : stack.getHoverName().getString(),
                        slot.hotbarSlot() >= 0 ? slot.hotbarSlot() + 1 : null,
                        slot.hotbarWeapons()));
            }
            return out;
        }

        static Map<String, Object> temper(MinecraftServer server, ServerPlayer player) {
            UUID uuid = player.getUUID();
            TemperData data = TemperData.get(server);
            TemperView view = data.view(uuid);
            int count = TemperShape.CATEGORIES.length;
            int[] levels = new int[count];
            double[] multipliers = new double[count];
            double[] powers = new double[count];
            for (int i = 0; i < count; i++) {
                TemperCategory category = TemperCategory.byId(TemperShape.CATEGORIES[i]);
                if (category == null) {
                    multipliers[i] = 1.0;
                    powers[i] = 1.0;
                    continue;
                }
                levels[i] = view.levels(category);
                multipliers[i] = view.label(category);
                powers[i] = view.power(category);
            }
            Integer peak = attempt("peak gear power", () -> data.peakGearPower(uuid));
            return TemperShape.build(true, view.dust(), levels, multipliers, powers, peak,
                    view.bossesBeaten(), view.trialsRecord(), view.favorsUsed());
        }

        static double[] multipliers(int[] levels) {
            TemperLadder ladder = TemperData.ladder();
            double[] out = new double[levels.length];
            for (int i = 0; i < levels.length; i++) out[i] = ladder.lineMultiplier(levels[i]);
            return out;
        }

        static double[] powers(double[] multipliers) {
            TemperLadder ladder = TemperData.ladder();
            double[] out = new double[multipliers.length];
            for (int i = 0; i < multipliers.length; i++) {
                TemperCategory category = TemperCategory.byId(TemperShape.CATEGORIES[i]);
                out[i] = category == null ? 1.0 : ladder.effectMultiplierAtLabel(category, multipliers[i]);
            }
            return out;
        }

        static Map<String, Object> reputation(UUID uuid) {
            Map<String, Object> out = new HashMap<>();
            for (VaultGod god : VaultGod.values()) {
                out.put(god.name().toLowerCase(Locale.ROOT), PlayerReputationData.getReputation(uuid, god));
            }
            return out;
        }
    }
}
