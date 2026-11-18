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
---@field attribute? string The attribute's registry id, e.g. "the_vault:summon_damage" (2.2.37)
---@field scoreRatio? number Gear Score ratio of this line: 1 is the best natural roll at level 100, up to 1.5 (2.2.37)
---@field scorePoints? number This line's share of the piece's gearScore (2.2.37)
---@field naturalBest? number Best natural roll at level 100 for this gear type (2.2.37)
---@field naturalWorst? number Worst natural roll at level 100 for this gear type (2.2.37)
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

---@class HealPair Heal on Kill / Heal on Block value (2.2.37 adds chance and amount)
---@field first number
---@field second number
---@field chance number The same number as first
---@field amount number The same number as second

---@class GearScoreLines
---@field expected number Line count of a best natural piece of this type
---@field actual number This piece's line count

---@class Durability
---@field total number Max durability
---@field max number The same number as total (2.2.37)
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
---@field isLegendary? boolean Only present if true: flagged (also before identification) or a modifier is legendary
---@field greedTier? number Greed tier an Etching locked the piece to (2.2.37)
---@field gearScoreStatus? string COUNTED|INERT|NO_TIER_CONFIG|NOT_COMBAT_GEAR (2.2.37, Vault Hunters 3.21.74)
---@field gearScore? number 0 to 1500, only when gearScoreStatus is COUNTED (2.2.37)
---@field gearScoreExact? number gearScore before rounding (2.2.37)
---@field gearScoreLines? GearScoreLines (2.2.37)
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
---@field state string UNIDENTIFIED|ROLLING|IDENTIFIED (2.2.37)
---@field identified boolean (2.2.37)
---@field capacity? number Jewel capacity left (2.2.37)
---@field hammerSize? number (2.2.37)
---@field material? string Tool material, e.g. CHROMATIC_STEEL_INGOT (2.2.37)
---@field isLegendary? boolean Only present if true (2.2.37)
---@field isSoulbound? boolean Only present if true (2.2.37)
---@field craftingPotential? CraftingPotential (2.2.37)
---@field implicits VaultModifier[]
---@field prefixes VaultModifier[]
---@field suffixes VaultModifier[]

---@class JewelData
---@field itemType "Jewel"
---@field name string
---@field level number
---@field rarity string
---@field state string UNIDENTIFIED|ROLLING|IDENTIFIED (2.2.37)
---@field identified boolean (2.2.37)
---@field size? number Jewel size (2.2.37)
---@field isLegendary? boolean Only present if true (2.2.37)
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
