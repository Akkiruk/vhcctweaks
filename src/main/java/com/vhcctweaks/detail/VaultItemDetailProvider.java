package com.vhcctweaks.detail;

import dan200.computercraft.api.detail.IDetailProvider;
import iskallia.vault.gear.VaultGearRarity;
import iskallia.vault.gear.VaultGearState;
import iskallia.vault.gear.attribute.VaultGearAttributeInstance;
import iskallia.vault.gear.attribute.VaultGearModifier;
import iskallia.vault.gear.attribute.VaultGearModifier.AffixType;
import iskallia.vault.gear.data.AttributeGearData;
import iskallia.vault.gear.data.VaultGearData;
import iskallia.vault.gear.item.VaultGearItem;
import iskallia.vault.init.ModDynamicModels;
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

    @Override
    public void provideDetails(@Nonnull Map<? super String, Object> data, @Nonnull ItemStack stack) {
        if (stack.isEmpty()) return;

        // Fast namespace check — skip all non-VH items without any reflection or instanceof
        ResourceLocation itemId = stack.getItem().getRegistryName();
        if (itemId == null || !"the_vault".equals(itemId.getNamespace())) return;

        try {
            Map<String, Object> vaultData = buildVaultData(stack);
            if (vaultData != null) {
                data.put("vaultData", vaultData);
            }
        } catch (Exception ignored) {
            // Silently ignore — VH data is unreadable for this item
        }
    }

    private Map<String, Object> buildVaultData(ItemStack stack) {
        Item item = stack.getItem();

        if (item instanceof VaultGearItem) {
            // VaultCharmItem implements VaultGearItem, need to check first
            if (item instanceof VaultCharmItem) return parseCharm(stack);
            if (item instanceof EtchingItem)    return parseEtching(stack);
            return parseGear(stack);
        }
        if (item instanceof ToolItem)             return parseTool(stack);
        if (item instanceof JewelItem)            return parseJewel(stack);
        if (item instanceof TrinketItem)          return parseTrinket(stack);
        if (item instanceof InscriptionItem)      return parseInscription(stack);
        if (item instanceof InfusedCatalystItem)  return parseCatalyst(stack);
        if (item instanceof VaultCrystalItem)     return parseCrystal(stack);
        if (item instanceof VaultDollItem)        return parseDoll(stack);
        if (item instanceof CardItem)             return parseCard(stack);
        if (item instanceof AugmentItem)          return parseAugment(stack);

        // Check if any VH item with AttributeGearData on it (fallback)
        ResourceLocation regName = item.getRegistryName();
        if (regName != null && "the_vault".equals(regName.getNamespace())) {
            if (AttributeGearData.hasData(stack)) {
                return parseGenericVaultItem(stack, regName);
            }
        }

        return null;
    }

    // ================== Gear (Armor, Swords, Axes, Shields, Wands, etc.) ==================

    private Map<String, Object> parseGear(ItemStack stack) {
        Map<String, Object> gear = new LinkedHashMap<>();
        gear.put("itemType", "Gear");

        VaultGearData data = VaultGearData.read(stack);
        gear.put("name", stack.getDisplayName().getString());
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
        gear.put("durability", buildDurability(stack));

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
        data.getFirstValue(ModGearAttributes.GEAR_MODEL).ifPresent(loc -> {
            try {
                var model = ModDynamicModels.REGISTRIES.getModelByResourceLocation(loc);
                gear.put("model", model.isPresent() ? model.get().getDisplayName() : loc.toString());
            } catch (Exception e) {
                gear.put("model", loc.toString());
            }
        });

        // Modifiers
        gear.put("implicits", VaultModifierHelper.parseModifiers(stack, data, AffixType.IMPLICIT));
        gear.put("prefixes", VaultModifierHelper.parseModifiers(stack, data, AffixType.PREFIX));
        gear.put("suffixes", VaultModifierHelper.parseModifiers(stack, data, AffixType.SUFFIX));

        // Attributes (base properties like durability, soulbound, etc.)
        List<Map<String, Object>> attributes = new ArrayList<>();
        data.getAllAttributes().forEach(instance -> {
            // Skip attributes already represented elsewhere
            if (isSkippedAttribute(instance)) return;
            attributes.add(VaultModifierHelper.parseAttributeInstance(stack, instance, data));
        });
        if (!attributes.isEmpty()) {
            gear.put("attributes", attributes);
        }

        return gear;
    }

    // ================== Tool ==================

    private Map<String, Object> parseTool(ItemStack stack) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("itemType", "Tool");

        VaultGearData data = VaultGearData.read(stack);
        tool.put("name", stack.getDisplayName().getString());
        tool.put("level", data.getItemLevel());
        tool.put("rarity", data.getRarity().name());
        tool.put("repairSlots", buildRepairSlots(data));
        tool.put("durability", buildDurability(stack));
        tool.put("implicits", VaultModifierHelper.parseModifiers(stack, data, AffixType.IMPLICIT));
        tool.put("prefixes", VaultModifierHelper.parseModifiers(stack, data, AffixType.PREFIX));
        tool.put("suffixes", VaultModifierHelper.parseModifiers(stack, data, AffixType.SUFFIX));

        return tool;
    }

    // ================== Jewel ==================

    private Map<String, Object> parseJewel(ItemStack stack) {
        Map<String, Object> jewel = new LinkedHashMap<>();
        jewel.put("itemType", "Jewel");

        VaultGearData data = VaultGearData.read(stack);
        jewel.put("name", stack.getDisplayName().getString());
        jewel.put("level", data.getItemLevel());
        jewel.put("rarity", data.getRarity().name());
        jewel.put("implicits", VaultModifierHelper.parseModifiers(stack, data, AffixType.IMPLICIT));
        jewel.put("prefixes", VaultModifierHelper.parseModifiers(stack, data, AffixType.PREFIX));
        jewel.put("suffixes", VaultModifierHelper.parseModifiers(stack, data, AffixType.SUFFIX));

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
        trinket.put("name", stack.getDisplayName().getString());

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

        charm.put("name", stack.getDisplayName().getString());
        charm.put("rarity", data.getRarity().name());

        VaultCharmItem charmItem = (VaultCharmItem) stack.getItem();
        charm.put("uses", charmItem.getUses(stack));

        VaultCharmItem.getGod(stack).ifPresent(god -> charm.put("god", god.getName()));
        charm.put("godReputation", VaultCharmItem.getGodReputation(stack));

        charm.put("prefixes", VaultModifierHelper.parseModifiers(stack, data, AffixType.PREFIX));

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
                // Objective
                if (nbt.contains("objective")) {
                    var objTag = nbt.getCompound("objective");
                    if (objTag.contains("type")) {
                        crystal.put("objective", objTag.getString("type"));
                    }
                }
                // Theme
                if (nbt.contains("theme")) {
                    var themeTag = nbt.getCompound("theme");
                    if (themeTag.contains("type")) {
                        crystal.put("theme", themeTag.getString("type"));
                    }
                    if (themeTag.contains("id")) {
                        crystal.put("themeId", themeTag.getString("id"));
                    }
                }
                // Layout
                if (nbt.contains("layout")) {
                    var layoutTag = nbt.getCompound("layout");
                    if (layoutTag.contains("type")) {
                        crystal.put("layout", layoutTag.getString("type"));
                    }
                }
                // Time
                if (nbt.contains("time")) {
                    var timeTag = nbt.getCompound("time");
                    if (timeTag.contains("type")) {
                        crystal.put("timeType", timeTag.getString("type"));
                    }
                    if (timeTag.contains("value")) {
                        crystal.put("time", timeTag.getInt("value"));
                    }
                }
                // Modifiers
                if (nbt.contains("modifiers")) {
                    var modsTag = nbt.getCompound("modifiers");
                    if (modsTag.contains("type")) {
                        crystal.put("modifierType", modsTag.getString("type"));
                    }
                    // Extract modifier list if present
                    if (modsTag.contains("entries")) {
                        var entriesTag = modsTag.getList("entries", 10); // CompoundTag list
                        List<Map<String, Object>> modList = new ArrayList<>();
                        for (int i = 0; i < entriesTag.size(); i++) {
                            var entryTag = entriesTag.getCompound(i);
                            Map<String, Object> mod = new LinkedHashMap<>();
                            if (entryTag.contains("id")) mod.put("id", entryTag.getString("id"));
                            if (entryTag.contains("count")) mod.put("count", entryTag.getInt("count"));
                            modList.add(mod);
                        }
                        if (!modList.isEmpty()) crystal.put("modifiers", modList);
                    }
                }
                // Properties (instability, capacity)
                if (nbt.contains("properties")) {
                    var propsTag = nbt.getCompound("properties");
                    if (propsTag.contains("instability")) {
                        crystal.put("instability", propsTag.getFloat("instability"));
                    }
                    if (propsTag.contains("capacity")) {
                        crystal.put("capacity", propsTag.getInt("capacity"));
                    }
                }
                // Level
                if (nbt.contains("level")) {
                    crystal.put("level", nbt.getInt("level"));
                }
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

        doll.put("name", stack.getDisplayName().getString());

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
        card.put("name", stack.getDisplayName().getString());

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
        augment.put("name", stack.getDisplayName().getString());

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
        etching.put("name", stack.getDisplayName().getString());

        VaultGearData data = VaultGearData.read(stack);
        etching.put("level", data.getItemLevel());
        etching.put("rarity", data.getRarity().name());
        etching.put("state", data.getState().name());

        if (data.getState() == VaultGearState.IDENTIFIED) {
            etching.put("identified", true);
            etching.put("implicits", VaultModifierHelper.parseModifiers(stack, data, AffixType.IMPLICIT));
            etching.put("prefixes", VaultModifierHelper.parseModifiers(stack, data, AffixType.PREFIX));
            etching.put("suffixes", VaultModifierHelper.parseModifiers(stack, data, AffixType.SUFFIX));
        } else {
            etching.put("identified", false);
        }

        return etching;
    }

    // ================== Generic VH Item Fallback ==================

    private Map<String, Object> parseGenericVaultItem(ItemStack stack, ResourceLocation regName) {
        Map<String, Object> generic = new LinkedHashMap<>();
        generic.put("itemType", "VaultItem");
        generic.put("name", stack.getDisplayName().getString());
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

    private Map<String, Object> buildDurability(ItemStack stack) {
        Map<String, Object> dur = new LinkedHashMap<>();
        try {
            VaultGearItem vgi = VaultGearItem.of(stack);
            int max = vgi.getMaxDamage(stack);
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
