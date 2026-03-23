--- Vault Item Detail API — Lua Type Reference
--- Part of vhcctweaks mod
---
--- This file provides type stubs for IDE autocompletion.
--- It does NOT need to be loaded at runtime.
---
--- Usage:
---   local detail = chest.getItemDetail(slot)
---   if detail.vaultData then
---       -- detail.vaultData is a VaultData table
---   end

---@class VaultModifier
---@field name string Human-readable modifier name
---@field value number|boolean|string|table The rolled value
---@field tier? number Rolled tier (1-indexed), absent for deterministic modifiers
---@field min? number Minimum possible value at this tier
---@field max? number Maximum possible value at this tier
---@field group? string Modifier group ID
---@field identifier? string Modifier registry identifier
---@field legendary? boolean Part of legendary set
---@field crafted? boolean Player-crafted modifier
---@field frozen? boolean Frozen (cannot be rerolled)
---@field greater? boolean Greater modifier
---@field abyssal? boolean Abyssal modifier
---@field corrupted? boolean Corrupted modifier
---@field imbued? boolean Imbued modifier
---@field abilityEnhancement? boolean Ability enhancement

---@class VaultAttribute
---@field name string Attribute name
---@field value string|number|boolean|table Attribute value

---@class RepairSlots
---@field total number Total repair slots
---@field used number Used repair slots

---@class Durability
---@field total number Max durability
---@field current number Current durability

---@class CraftingPotential
---@field current? number Current crafting potential
---@field max? number Max crafting potential

---@class GearData
---@field itemType "Gear"
---@field name string Display name
---@field level number Item level
---@field rarity string SCRAPPY|COMMON|RARE|EPIC|OMEGA|UNIQUE|SPECIAL|CHAOTIC
---@field state string UNIDENTIFIED|ROLLING|IDENTIFIED
---@field identified boolean
---@field gearType? string Gear class (SWORD, HELMET, CHESTPLATE, etc.)
---@field equipmentSlot? string MC equipment slot name (mainhand, head, chest, legs, feet)
---@field repairSlots? RepairSlots
---@field durability? Durability
---@field craftingPotential? CraftingPotential Absent on UNIQUE rarity
---@field prefixSlots? number Max prefix modifier slots
---@field suffixSlots? number Max suffix modifier slots
---@field isLegendary? boolean Only present if true
---@field isSoulbound? boolean Only present if true
---@field uniqueKey? string Only on UNIQUE items
---@field gearName? string Custom name if set
---@field model? string Transmog model display name
---@field implicits? VaultModifier[]
---@field prefixes? VaultModifier[]
---@field suffixes? VaultModifier[]
---@field attributes? VaultAttribute[] Base attributes not covered above

---@class ToolData
---@field itemType "Tool"
---@field name string
---@field level number
---@field rarity string
---@field repairSlots RepairSlots
---@field durability Durability
---@field implicits VaultModifier[]
---@field prefixes VaultModifier[]
---@field suffixes VaultModifier[]

---@class JewelData
---@field itemType "Jewel"
---@field name string
---@field level number
---@field rarity string
---@field implicits VaultModifier[]
---@field prefixes VaultModifier[]
---@field suffixes VaultModifier[]

---@class TrinketData
---@field itemType "Trinket"
---@field identified boolean
---@field name? string Only when identified
---@field uses? number Remaining uses
---@field slot? string Curios slot identifier (necklace, ring, etc.)
---@field effect? string Registry name of the trinket effect

---@class CharmData
---@field itemType "Charm"
---@field identified boolean
---@field state? string Only when unidentified
---@field name? string
---@field rarity? string
---@field uses? number
---@field god? string God name
---@field godReputation? number
---@field prefixes? VaultModifier[]

---@class InscriptionData
---@field itemType "Inscription"
---@field size number Inscription size
---@field rooms string[] Room names

---@class CatalystModifier
---@field id string Modifier registry ID
---@field count? number

---@class CatalystData
---@field itemType "Catalyst"
---@field size? number Catalyst size
---@field modifiers string[] Modifier registry IDs
---@field isSuper boolean

---@class CrystalModifierEntry
---@field id string Modifier ID
---@field count? number

---@class CrystalData
---@field itemType "VaultCrystal"
---@field objective? string Objective type (kill_boss, etc.)
---@field theme? string Theme type
---@field themeId? string Theme registry ID
---@field layout? string Layout type
---@field timeType? string Time type
---@field time? number Time in ticks
---@field modifiers? CrystalModifierEntry[]
---@field modifierType? string Modifier pool type
---@field instability? number
---@field capacity? number
---@field level? number

---@class DollData
---@field itemType "VaultDoll"
---@field name string Display name
---@field playerName? string Player name
---@field playerUUID? string Player UUID
---@field experience? number Stored experience

---@class CardData
---@field itemType "Card"
---@field name string
---@field cardData? string String representation of card data

---@class AugmentData
---@field itemType "Augment"
---@field name string
---@field theme? string

---@class EtchingData
---@field itemType "Etching"
---@field name string
---@field level number
---@field rarity string
---@field state string
---@field identified boolean
---@field implicits? VaultModifier[]
---@field prefixes? VaultModifier[]
---@field suffixes? VaultModifier[]

---@class GenericVaultItemData
---@field itemType "VaultItem"
---@field name string
---@field registryName string
---@field attributes? VaultAttribute[]

---@alias VaultData GearData|ToolData|JewelData|TrinketData|CharmData|InscriptionData|CatalystData|CrystalData|DollData|CardData|AugmentData|EtchingData|GenericVaultItemData
