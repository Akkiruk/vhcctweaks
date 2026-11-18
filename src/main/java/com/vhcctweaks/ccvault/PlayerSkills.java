package com.vhcctweaks.ccvault;

import com.vhcctweaks.util.Safety;
import iskallia.vault.core.data.adapter.Adapters;
import iskallia.vault.init.ModConfigs;
import iskallia.vault.skill.base.LearnableSkill;
import iskallia.vault.skill.base.Skill;
import iskallia.vault.skill.base.SpecializedSkill;
import iskallia.vault.skill.base.TieredSkill;
import iskallia.vault.skill.tree.AbilityTree;
import iskallia.vault.skill.tree.SkillTree;
import iskallia.vault.world.data.PlayerAbilitiesData;
import iskallia.vault.world.data.PlayerExpertisesData;
import iskallia.vault.world.data.PlayerPrestigePowersData;
import iskallia.vault.world.data.PlayerTalentsData;
import iskallia.vault.world.data.SkillTreeStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A player's abilities, talents, expertises and prestige powers, for ccvault.getPlayerSkills (2.2.37).
 *
 * - On this server: Vault Hunters' live trees, gear bonus levels included.
 * - Anyone else: the trees in this server's saved profile, rebuilt from the file the way Vault Hunters rebuilds them
 *   when it loads a player, but into a new tree that nothing else sees. The player is never loaded.
 *
 * Each tree is read on its own; one that fails is left out. Server thread only.
 */
final class PlayerSkills {

    static final String NOT_AVAILABLE = "skills not available";

    /** The profile sections, and the keys they have in the result. */
    static final String[][] TREES = {
            {"abilities", "abilities"}, {"talents", "talents"}, {"expertises", "expertises"}, {"prestige", "prestige"}};

    private PlayerSkills() {}

    static Object[] read(MinecraftServer server, UUID uuid) {
        ServerPlayer here = Safety.here(server, uuid);
        Map<String, Object> out = new HashMap<>();
        Map<String, Object> spent = new HashMap<>();
        if (here != null) {
            out.put("online", true);
            tree(out, spent, "abilities", () -> PlayerAbilitiesData.get(server).getAbilities(here), true);
            tree(out, spent, "talents", () -> PlayerTalentsData.get(server).getTalents(here), true);
            tree(out, spent, "expertises", () -> PlayerExpertisesData.get(server).getExpertises(here), true);
            tree(out, spent, "prestige", () -> PlayerPrestigePowersData.get(server).getPowers(here), true);
        } else {
            CompoundTag profile = VaultLevels.savedTag(server, uuid);
            if (profile == null) return new Object[]{null, VaultPlayers.NO_DATA};
            out.put("online", false);
            for (String[] tree : TREES) {
                if (!profile.contains(tree[0], Tag.TAG_COMPOUND)) continue;
                CompoundTag section = profile.getCompound(tree[0]);
                tree(out, spent, tree[1], () -> saved(tree[0], section), false);
            }
        }
        if (out.size() == 1) return new Object[]{null, NOT_AVAILABLE};
        out.put("spentPoints", spent);
        return new Object[]{out};
    }

    /** A saved tree rebuilt into a new object, like PlayerXData.loadPlayer but kept to itself. */
    private static Skill saved(String key, CompoundTag section) {
        if (section.isEmpty()) return null;
        CompoundTag stored = section.copy();
        Skill config;
        switch (key) {
            case "abilities" -> config = ModConfigs.ABILITIES.get().orElse(null);
            case "talents" -> config = ModConfigs.TALENTS.get().orElse(null);
            case "expertises" -> config = ModConfigs.EXPERTISES.getAll();
            default -> {
                // Prestige keeps its tree under "Tree"; before version 5 Vault Hunters resets it on load
                if (stored.getInt("prestige_version") < 5) return null;
                stored = stored.getCompound("Tree");
                config = ModConfigs.PRESTIGE_POWERS.get().orElse(null);
            }
        }
        Optional<Skill> tree = Adapters.SKILL.readNbt(SkillTreeStorage.expand(key, stored, config));
        return tree.orElse(null);
    }

    private static void tree(Map<String, Object> out, Map<String, Object> spent, String key, Supplier<Skill> read, boolean live) {
        try {
            Skill tree = read.get();
            if (tree == null) return;
            out.put(key, key.equals("abilities") ? abilities(tree, live) : tiered(tree, live));
            if (tree instanceof SkillTree skills) spent.put(key, skills.getSpentLearnPoints());
        } catch (RuntimeException | LinkageError e) {
            Safety.logOnce("PlayerSkills " + key, e);
        }
    }

    private static List<Map<String, Object>> abilities(Skill tree, boolean live) {
        SpecializedSkill selected = live && tree instanceof AbilityTree abilities ? abilities.getSelected() : null;
        List<Map<String, Object>> out = new ArrayList<>();
        for (SpecializedSkill ability : tree.getAll(SpecializedSkill.class, Skill::isUnlocked)) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("id", ability.getId());
            entry.put("name", ability.getName());
            LearnableSkill spec = ability.getSpecialization();
            if (spec != null) {
                entry.put("specialization", spec.getId());
                entry.put("specializationName", spec.getName());
            }
            entry.put("specializationIndex", ability.getIndex());
            if (spec instanceof TieredSkill tiers) levels(entry, tiers, live);
            if (live) entry.put("selected", ability == selected);
            out.add(entry);
        }
        return out;
    }

    private static List<Map<String, Object>> tiered(Skill tree, boolean live) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TieredSkill skill : tree.getAll(TieredSkill.class, TieredSkill::isUnlocked)) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("id", skill.getId());
            entry.put("name", skill.getName());
            levels(entry, skill, live);
            out.add(entry);
        }
        return out;
    }

    /** level counts gear bonuses, which only a live tree knows; a saved one gives what was learned. */
    private static void levels(Map<String, Object> entry, TieredSkill skill, boolean live) {
        int learned = skill.getUnmodifiedTier();
        entry.put("learned", learned);
        entry.put("level", live ? skill.getActualTier() : learned);
        entry.put("maxLevel", skill.getMaxLearnableTier());
    }
}
