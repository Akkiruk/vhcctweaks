package com.vhcctweaks.detail;

import com.vhcctweaks.VHCCTweaks;
import dan200.computercraft.api.detail.IDetailProvider;
import iskallia.vault.config.gear.VaultGearTierConfig;
import iskallia.vault.gear.attribute.type.VaultGearAttributeTypeMerger;
import iskallia.vault.gear.VaultGearRarity;
import iskallia.vault.gear.VaultGearState;
import iskallia.vault.gear.attribute.VaultGearAttributeInstance;
import iskallia.vault.gear.attribute.VaultGearModifier;
import iskallia.vault.gear.attribute.VaultGearModifier.AffixType;
import iskallia.vault.gear.data.AttributeGearData;
import iskallia.vault.gear.data.VaultGearData;
import iskallia.vault.gear.item.VaultGearItem;
import iskallia.vault.init.ModGearAttributes;
import iskallia.vault.item.AugmentItem;
import iskallia.vault.item.CardItem;
import iskallia.vault.item.InfusedCatalystItem;
import iskallia.vault.item.InscriptionItem;
import iskallia.vault.item.VaultDollItem;
import iskallia.vault.item.crystal.CrystalData;
import iskallia.vault.item.crystal.VaultCrystalItem;
import iskallia.vault.item.data.InscriptionData;
import iskallia.vault.item.gear.EtchingItem;
import iskallia.vault.item.gear.TrinketItem;
import iskallia.vault.item.gear.VaultCharmItem;
import iskallia.vault.item.tool.JewelItem;
import iskallia.vault.item.tool.ToolItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CC:Tweaked IDetailProvider that enriches getItemDetail() for any Vault Hunters item
 * in any inventory — no custom block required.
 *
 * Register once via: DetailRegistries.ITEM_STACK.addProvider(new VaultItemDetailProvider());
 *
 * Lua usage:
 *   local chest = peripheral.wrap("minecraft:chest_0")
 *   local detail = chest.getItemDetail(1)
 *   if detail.vaultData then
 *       print("VH item type: " .. detail.vaultData.itemType)
 *   end
 */
public class VaultItemDetailProvider implements IDetailProvider<ItemStack> {

    private static final Set<String> VAULT_NAMESPACES = Set.of("the_vault", "woldsvaults", "vaultadditions");
    private static final int MAX_RAW_NBT_CHARS = 32 * 1024;

    @Override
    public void provideDetails(@Nonnull Map<? super String, Object> data, @Nonnull ItemStack stack) {
        if (stack.isEmpty()) return;

        // Fast namespace check — addon mods add their own gear and trinkets on top of Vault's
        ResourceLocation itemId = stack.getItem().getRegistryName();
        if (itemId == null || !VAULT_NAMESPACES.contains(itemId.getNamespace())) return;

        try {
            Map<String, Object> vaultData = buildVaultData(stack);
            if (vaultData != null) {
                // Only items this provider does not understand carry their raw NBT: parsed ones already say
                // everything readable, and gear's NBT is mostly an opaque bit buffer
                if (GENERIC.equals(vaultData.get("itemType")) && stack.hasTag()) {
                    String nbt = stack.getTag().toString();
                    if (nbt.length() <= MAX_RAW_NBT_CHARS) vaultData.put("rawNbt", nbt);
                }
                data.put("vaultData", vaultData);
            }
        } catch (Exception e) {
            // An unreadable item gets no vaultData; the first one is logged so a broken parser does not go unseen
            if (!loggedFailure) {
                loggedFailure = true;
                VHCCTweaks.LOGGER.debug("Vault item details failed for {} (later failures are not logged)", itemId, e);
            }
        }
    }

    /** A copy of a Vault Hunters stack, for building its details without touching the live item. Anything else as is. */
    public static Object copyIfVault(Object object) {
        try {
            if (object instanceof ItemStack stack && !stack.isEmpty()) {
                ResourceLocation id = stack.getItem().getRegistryName();
                if (id != null && VAULT_NAMESPACES.contains(id.getNamespace())) return stack.copy();
            }
        } catch (RuntimeException e) {
            com.vhcctweaks.util.Safety.logOnce("item detail copy", e);
        }
        return object;
    }

    private static final String GENERIC = "VaultItem";
    private static volatile boolean loggedFailure;

    private Map<String, Object> buildVaultData(ItemStack stack) {
        Item item = stack.getItem();

        // Tools and jewels are VaultGearItems too, so they are checked before the gear branch catches them
        if (item instanceof ToolItem)             return parseTool(stack);
        if (item instanceof JewelItem)            return parseJewel(stack);
        if (item instanceof VaultGearItem) {
            // VaultCharmItem implements VaultGearItem, need to check first
            if (item instanceof VaultCharmItem) return parseCharm(stack);
            if (item instanceof EtchingItem)    return parseEtching(stack);
            return parseGear(stack);
        }
        if (item instanceof TrinketItem)          return parseTrinket(stack);
        if (item instanceof InscriptionItem)      return parseInscription(stack);
        if (item instanceof InfusedCatalystItem)  return parseCatalyst(stack);
        if (item instanceof VaultCrystalItem)     return parseCrystal(stack);
        if (item instanceof VaultDollItem)        return parseDoll(stack);
        if (item instanceof CardItem)             return parseCard(stack);
        if (item instanceof AugmentItem)          return parseAugment(stack);

        // Everything else that carries data: attributes if it has them, and always its raw NBT
        ResourceLocation regName = item.getRegistryName();
        if (regName != null && (stack.hasTag() || AttributeGearData.hasData(stack))) {
            return parseGenericVaultItem(stack, regName);
        }

        return null;
    }

    // ================== Gear (Armor, Swords, Axes, Shields, Wands, etc.) ==================

    private Map<String, Object> parseGear(ItemStack stack) {
        Map<String, Object> gear = new LinkedHashMap<>();
        gear.put("itemType", "Gear");

        VaultGearData data = VaultGearData.read(stack);
        gear.put("name", displayName(stack));
        gear.put("level", data.getItemLevel());
        gear.put("rarity", data.getRarity().name());
        gear.put("state", data.getState().name());

        // Gear type and slot
        try {
            VaultGearItem vgi = VaultGearItem.of(stack);
            gear.put("gearType", vgi.getGearType(stack).name());
        } catch (Exception ignored) {}

        // Equipment slot from the item's registry (mainhand, head, chest, legs, feet, offhand)
        try {
            var slot = ((Item) stack.getItem()).getEquipmentSlot(stack);
            if (slot != null) gear.put("equipmentSlot", slot.getName());
        } catch (Exception ignored) {}

        // If not identified, return early with minimal info
        if (data.getState() != VaultGearState.IDENTIFIED) {
            gear.put("identified", false);
            return gear;
        }
        gear.put("identified", true);

        // Repair slots
        gear.put("repairSlots", buildRepairSlots(data));

        // Durability
        gear.put("durability", buildDurability(stack, data));

        // Crafting potential (not present on UNIQUE rarity)
        if (data.getRarity() != VaultGearRarity.UNIQUE) {
            gear.put("craftingPotential", buildCraftingPotential(data));
            data.getFirstValue(ModGearAttributes.PREFIXES).ifPresent(v -> gear.put("prefixSlots", v));
            data.getFirstValue(ModGearAttributes.SUFFIXES).ifPresent(v -> gear.put("suffixSlots", v));
        }

        // Special flags
        data.getFirstValue(ModGearAttributes.IS_LEGENDARY).ifPresent(v -> { if (v) gear.put("isLegendary", true); });
        data.getFirstValue(ModGearAttributes.SOULBOUND).ifPresent(v -> { if (v) gear.put("isSoulbound", true); });
        data.getFirstValue(ModGearAttributes.UNIQUE_ITEM_KEY).ifPresent(v -> gear.put("uniqueKey", v.toString()));
        data.getFirstValue(ModGearAttributes.GEAR_NAME).ifPresent(v -> gear.put("gearName", v.toString()));

        // Model
        data.getFirstValue(ModGearAttributes.GEAR_MODEL).ifPresent(loc -> gear.put("model", loc.toString()));

        // Modifiers, each parsed once against one tier config, and the real base attributes
        putModifiers(gear, stack, data);
        putBaseAttributes(gear, stack, data);

        return gear;
    }

    /** implicits, prefixes and suffixes, with the item's tier config looked up once. */
    private static void putModifiers(Map<String, Object> out, ItemStack stack, VaultGearData data) {
        Optional<VaultGearTierConfig> tierCfg = VaultGearTierConfig.getConfig(stack);
        out.put("implicits", VaultModifierHelper.parseModifiers(stack, data, AffixType.IMPLICIT, tierCfg));
        out.put("prefixes", VaultModifierHelper.parseModifiers(stack, data, AffixType.PREFIX, tierCfg));
        out.put("suffixes", VaultModifierHelper.parseModifiers(stack, data, AffixType.SUFFIX, tierCfg));
    }

    /**
     * The item's base attributes (durability, repair slots and the like), not its modifiers. Before 2.2.36 this list
     * repeated every modifier, because Vault Hunters' getAllAttributes returns the modifiers again. Attributes already
     * reported under their own keys are left out.
     */
    private void putBaseAttributes(Map<String, Object> out, ItemStack stack, VaultGearData data) {
        List<Map<String, Object>> attributes = new ArrayList<>();
        for (VaultGearAttributeInstance<?> instance : data.getAttributes()) {
            if (isSkippedAttribute(instance)) continue;
            attributes.add(VaultModifierHelper.parseAttributeInstance(stack, instance, data));
        }
        if (!attributes.isEmpty()) out.put("attributes", attributes);
    }

    /** The item's own name, as CC's displayName gives it: getDisplayName would wrap it in square brackets. */
    private static String displayName(ItemStack stack) {
        return stack.getHoverName().getString();
    }

    // ================== Tool ==================

    private Map<String, Object> parseTool(ItemStack stack) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("itemType", "Tool");

        VaultGearData data = VaultGearData.read(stack);
        tool.put("name", displayName(stack));
        tool.put("level", data.getItemLevel());
        tool.put("rarity", data.getRarity().name());
        tool.put("repairSlots", buildRepairSlots(data));
        tool.put("durability", buildDurability(stack, data));
        putModifiers(tool, stack, data);

        return tool;
    }

    // ================== Jewel ==================

    private Map<String, Object> parseJewel(ItemStack stack) {
        Map<String, Object> jewel = new LinkedHashMap<>();
        jewel.put("itemType", "Jewel");

        VaultGearData data = VaultGearData.read(stack);
        jewel.put("name", displayName(stack));
        jewel.put("level", data.getItemLevel());
        jewel.put("rarity", data.getRarity().name());
        putModifiers(jewel, stack, data);

        return jewel;
    }

    // ================== Trinket ==================

    private Map<String, Object> parseTrinket(ItemStack stack) {
        Map<String, Object> trinket = new LinkedHashMap<>();
        trinket.put("itemType", "Trinket");

        if (!TrinketItem.isIdentified(stack)) {
            trinket.put("identified", false);
            return trinket;
        }

        trinket.put("identified", true);
        trinket.put("name", displayName(stack));

        TrinketItem trinketItem = (TrinketItem) stack.getItem();
        trinket.put("uses", trinketItem.getUses(stack));

        TrinketItem.getSlotIdentifier(stack).ifPresent(slot -> trinket.put("slot", slot));

        TrinketItem.getTrinket(stack).ifPresent(effect -> {
            trinket.put("effect", effect.getRegistryName() != null
                    ? effect.getRegistryName().toString()
                    : effect.toString());
        });

        return trinket;
    }

    // ================== Charm ==================

    private Map<String, Object> parseCharm(ItemStack stack) {
        Map<String, Object> charm = new LinkedHashMap<>();
        charm.put("itemType", "Charm");

        VaultGearData data = VaultGearData.read(stack);

        switch (data.getState()) {
            case UNIDENTIFIED:
            case ROLLING:
                charm.put("identified", false);
                charm.put("state", data.getState().name());
                return charm;
            default:
                charm.put("identified", true);
                break;
        }

        charm.put("name", displayName(stack));
        charm.put("rarity", data.getRarity().name());

        VaultCharmItem charmItem = (VaultCharmItem) stack.getItem();
        charm.put("uses", charmItem.getUses(stack));

        VaultCharmItem.getGod(stack).ifPresent(god -> charm.put("god", god.getName()));
        charm.put("godReputation", VaultCharmItem.getGodReputation(stack));

        charm.put("prefixes", VaultModifierHelper.parseModifiers(stack, data, AffixType.PREFIX, VaultGearTierConfig.getConfig(stack)));

        return charm;
    }

    // ================== Inscription ==================

    private Map<String, Object> parseInscription(ItemStack stack) {
        Map<String, Object> inscription = new LinkedHashMap<>();
        inscription.put("itemType", "Inscription");

        InscriptionData inscData = InscriptionData.from(stack);
        inscription.put("size", inscData.getSize());
        inscription.put("rooms", inscData.getEntries().stream()
                .map(r -> r.toRoomEntry().getName().getString())
                .collect(Collectors.toList()));

        return inscription;
    }

    // ================== Catalyst ==================

    private Map<String, Object> parseCatalyst(ItemStack stack) {
        Map<String, Object> catalyst = new LinkedHashMap<>();
        catalyst.put("itemType", "Catalyst");

        InfusedCatalystItem.getSize(stack).ifPresent(size -> catalyst.put("size", size));
        catalyst.put("modifiers", InfusedCatalystItem.getModifiers(stack).stream()
                .map(ResourceLocation::toString)
                .collect(Collectors.toList()));
        catalyst.put("isSuper", InfusedCatalystItem.isSuper(stack));

        return catalyst;
    }

    // ================== Vault Crystal ==================

    private Map<String, Object> parseCrystal(ItemStack stack) {
        Map<String, Object> crystal = new LinkedHashMap<>();
        crystal.put("itemType", "VaultCrystal");

        try {
            CrystalData crystalData = CrystalData.read(stack);
            // Extract what we safely can from the crystal data
            // CrystalData uses a complex nested structure; we record the NBT-level info
            crystalData.writeNbt().ifPresent(nbt -> {
                var objective = nbt.getCompound("Objective");
                if (objective.contains("type")) crystal.put("objective", objective.getString("type"));

                var theme = nbt.getCompound("Theme");
                if (theme.contains("type")) crystal.put("theme", theme.getString("type"));
                if (theme.contains("id")) crystal.put("themeId", theme.getString("id"));

                var layout = nbt.getCompound("Layout");
                if (layout.contains("type")) crystal.put("layout", layout.getString("type"));

                var time = nbt.getCompound("Time");
                if (time.contains("type")) crystal.put("timeType", time.getString("type"));

                var modifiers = nbt.getCompound("Modifiers");
                if (modifiers.contains("type")) crystal.put("modifierType", modifiers.getString("type"));
                if (modifiers.contains("Clarity")) crystal.put("clarity", modifiers.getBoolean("Clarity"));
                var entries = modifiers.getList("List", 10); // CompoundTag list
                List<Map<String, Object>> modList = new ArrayList<>();
                for (int i = 0; i < entries.size(); i++) {
                    var entry = entries.getCompound(i);
                    Map<String, Object> mod = new LinkedHashMap<>();
                    if (entry.contains("id")) mod.put("id", entry.getString("id"));
                    if (entry.contains("size")) mod.put("count", entry.getInt("size"));
                    modList.add(mod);
                }
                if (!modList.isEmpty()) crystal.put("modifiers", modList);

                var properties = nbt.getCompound("Properties");
                if (properties.contains("level")) crystal.put("level", properties.getInt("level"));
                if (properties.contains("instability")) crystal.put("instability", properties.getFloat("instability"));
                if (properties.contains("size")) crystal.put("capacity", properties.getInt("size"));
                if (properties.contains("volume")) crystal.put("volume", properties.getInt("volume"));
                if (properties.contains("exhausted")) crystal.put("exhausted", properties.getBoolean("exhausted"));

                if (nbt.contains("Sigil")) crystal.put("sigil", nbt.getString("Sigil"));
            });
        } catch (Exception ignored) {
            // Crystal data might not be parseable; return basic type info
        }

        return crystal;
    }

    // ================== Vault Doll ==================

    private Map<String, Object> parseDoll(ItemStack stack) {
        Map<String, Object> doll = new LinkedHashMap<>();
        doll.put("itemType", "VaultDoll");

        doll.put("name", displayName(stack));

        VaultDollItem.getPlayerGameProfile(stack).ifPresent(profile -> {
            doll.put("playerName", profile.getName());
            if (profile.getId() != null) {
                doll.put("playerUUID", profile.getId().toString());
            }
        });

        // Experience stored in NBT
        if (stack.hasTag()) {
            int xp = VaultDollItem.getExperience(stack.getTag());
            doll.put("experience", xp);
        }

        return doll;
    }

    // ================== Card ==================

    private Map<String, Object> parseCard(ItemStack stack) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("itemType", "Card");
        card.put("name", displayName(stack));

        try {
            var cardObj = CardItem.getCard(stack);
            if (cardObj != null) {
                // Cards use the core vault data system; extract what's readable
                card.put("cardData", cardObj.toString());
            }
        } catch (Exception ignored) {}

        return card;
    }

    // ================== Augment ==================

    private Map<String, Object> parseAugment(ItemStack stack) {
        Map<String, Object> augment = new LinkedHashMap<>();
        augment.put("itemType", "Augment");
        augment.put("name", displayName(stack));

        // Augments store their data in NBT; extract what we can
        if (stack.hasTag()) {
            var tag = stack.getTag();
            if (tag.contains("theme")) augment.put("theme", tag.getString("theme"));
        }

        return augment;
    }

    // ================== Etching ==================

    private Map<String, Object> parseEtching(ItemStack stack) {
        Map<String, Object> etching = new LinkedHashMap<>();
        etching.put("itemType", "Etching");
        etching.put("name", displayName(stack));

        VaultGearData data = VaultGearData.read(stack);
        etching.put("level", data.getItemLevel());
        etching.put("rarity", data.getRarity().name());
        etching.put("state", data.getState().name());

        if (data.getState() == VaultGearState.IDENTIFIED) {
            etching.put("identified", true);
            putBaseAttributes(etching, stack, data);
            putModifiers(etching, stack, data);
        } else {
            etching.put("identified", false);
        }

        return etching;
    }

    // ================== Generic VH Item Fallback ==================

    private Map<String, Object> parseGenericVaultItem(ItemStack stack, ResourceLocation regName) {
        Map<String, Object> generic = new LinkedHashMap<>();
        generic.put("itemType", "VaultItem");
        generic.put("name", displayName(stack));
        generic.put("registryName", regName.toString());

        try {
            AttributeGearData attrData = AttributeGearData.read(stack);
            List<Map<String, Object>> attrs = new ArrayList<>();
            attrData.getAttributes().forEach(instance -> {
                Map<String, Object> attr = new LinkedHashMap<>();
                attr.put("name", instance.getAttribute().getReader().getModifierName());
                attr.put("value", String.valueOf(instance.getValue()));
                attrs.add(attr);
            });
            if (!attrs.isEmpty()) generic.put("attributes", attrs);
        } catch (Exception ignored) {}

        return generic;
    }

    // ================== Shared Helpers ==================

    private Map<String, Object> buildRepairSlots(VaultGearData data) {
        Map<String, Object> repair = new LinkedHashMap<>();
        repair.put("total", data.getRepairSlots());
        repair.put("used", data.getUsedRepairSlots());
        return repair;
    }

    /**
     * For these Vault Hunters classes getMaxDamage is VaultGearData.read(stack).get(DURABILITY, intSum()), checked in
     * 3.21.72's bytecode, so the data already decoded gives the same number without decoding it again. A class that
     * overrides getMaxDamage (an addon's, or a later Vault Hunters) keeps its own answer. Recheck when Vault Hunters updates.
     */
    private static final Set<String> MAX_DAMAGE_IS_GEAR_DURABILITY = Set.of(
            "iskallia.vault.item.gear.VaultSwordItem", "iskallia.vault.item.gear.VaultAxeItem",
            "iskallia.vault.item.gear.VaultArmorItem", "iskallia.vault.item.gear.VaultShieldItem",
            "iskallia.vault.item.gear.WandItem", "iskallia.vault.item.gear.FocusItem",
            "iskallia.vault.item.gear.IdolItem", "iskallia.vault.item.tool.ToolItem");
    private static final ClassValue<Boolean> DURABILITY_FROM_DATA = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return MAX_DAMAGE_IS_GEAR_DURABILITY.contains(type.getMethod("getMaxDamage", ItemStack.class).getDeclaringClass().getName());
            } catch (NoSuchMethodException e) {
                return false;
            }
        }
    };

    private Map<String, Object> buildDurability(ItemStack stack, VaultGearData data) {
        Map<String, Object> dur = new LinkedHashMap<>();
        try {
            VaultGearItem vgi = VaultGearItem.of(stack);
            int max = DURABILITY_FROM_DATA.get(stack.getItem().getClass())
                    ? data.get(ModGearAttributes.DURABILITY, VaultGearAttributeTypeMerger.intSum())
                    : vgi.getMaxDamage(stack);
            dur.put("total", max);
            dur.put("current", max - vgi.getDamage(stack));
        } catch (Exception ignored) {
            dur.put("total", stack.getMaxDamage());
            dur.put("current", stack.getMaxDamage() - stack.getDamageValue());
        }
        return dur;
    }

    private Map<String, Object> buildCraftingPotential(VaultGearData data) {
        Map<String, Object> cp = new LinkedHashMap<>();
        data.getFirstValue(ModGearAttributes.CRAFTING_POTENTIAL).ifPresent(v -> cp.put("current", v));
        data.getFirstValue(ModGearAttributes.MAX_CRAFTING_POTENTIAL).ifPresent(v -> cp.put("max", v));
        return cp;
    }

    private boolean isSkippedAttribute(VaultGearAttributeInstance<?> instance) {
        var attr = instance.getAttribute();
        return attr.equals(ModGearAttributes.CRAFTING_POTENTIAL)
                || attr.equals(ModGearAttributes.MAX_CRAFTING_POTENTIAL)
                || attr.equals(ModGearAttributes.GEAR_MODEL)
                || attr.equals(ModGearAttributes.PREFIXES)
                || attr.equals(ModGearAttributes.SUFFIXES)
                || attr.equals(ModGearAttributes.GEAR_ROLL_TYPE)
                || attr.equals(ModGearAttributes.IS_LEGENDARY)
                || attr.equals(ModGearAttributes.SOULBOUND)
                || attr.equals(ModGearAttributes.UNIQUE_ITEM_KEY)
                || attr.equals(ModGearAttributes.GEAR_NAME)
                || attr.equals(ModGearAttributes.STATE);
    }
}
