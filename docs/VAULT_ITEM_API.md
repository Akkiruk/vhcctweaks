# Vault Item Detail API — Developer Documentation

> **Automatic Vault Hunters item inspection for CC:Tweaked — works with any inventory**
> Part of the **vhcctweaks** mod

---

## Overview

The Vault Item Detail API automatically enriches CC:Tweaked's `getItemDetail()` with comprehensive Vault Hunters item data. **No special block or peripheral is required** — it works with any inventory (chests, barrels, shulker boxes, modded inventories, turtle inventories, etc.).

When you call `getItemDetail()` on a slot containing a VH item, the returned table will include a `vaultData` key with all the item's VH-specific data — gear modifiers, trinket effects, crystal objectives, jewel stats, and more.

---

## Quick Start

```lua
local chest = peripheral.wrap("minecraft:chest_0")

for slot, basic in pairs(chest.list()) do
    if basic.name:find("the_vault:") then
        local detail = chest.getItemDetail(slot)
        if detail.vaultData then
            print(slot .. ": " .. detail.vaultData.itemType)

            if detail.vaultData.rarity then
                print("  Rarity: " .. detail.vaultData.rarity)
            end

            if detail.vaultData.prefixes then
                for _, mod in ipairs(detail.vaultData.prefixes) do
                    print("  [PREFIX] " .. mod.name .. " = " .. tostring(mod.value))
                end
            end
        end
    end
end
```

---

## How It Works

The mod registers a **CC:Tweaked Detail Provider** (`IDetailProvider<ItemStack>`) that intercepts every `getItemDetail()` call. When the item is from the Vault Hunters mod, it reads the item's internal data and converts it into a Lua-friendly table under the `vaultData` key.

This means:
- **Any inventory peripheral** works (chess, barrels, modded storage, turtle slots)
- **No API calls to learn** — just use `getItemDetail()` as normal
- **Backward compatible** — non-VH items are unaffected
- **Safe** — if VH data can't be read, `vaultData` is simply `nil`

---

## Supported Item Types

| `vaultData.itemType` | VH Item | Key Data |
|---|---|---|
| `"Gear"` | Armor, Swords, Axes, Shields, Wands, Focuses | Rarity, level, state, modifiers (implicit/prefix/suffix), crafting potential, gear model, durability, repair slots |
| `"Tool"` | Pickaxes, Shovels, Magnets, etc. | Level, rarity, durability, repair slots, modifiers |
| `"Jewel"` | Jewels (socketable) | Level, rarity, modifiers |
| `"Trinket"` | Trinkets (curios slot items) | Identified status, uses, slot, effect |
| `"Charm"` | Charms | Rarity, uses, god, god reputation, prefixes |
| `"Inscription"` | Inscriptions (vault room plans) | Size, room names |
| `"Catalyst"` | Infused Catalysts | Size, modifier list, isSuper |
| `"VaultCrystal"` | Vault Crystals | Objective, theme, layout, time, modifiers, instability, capacity, level |
| `"VaultDoll"` | Vault Dolls | Player name, UUID, experience |
| `"Card"` | Cards | Card data |
| `"Augment"` | Augments | Theme |
| `"Etching"` | Etchings | Level, rarity, state, modifiers |
| `"VaultItem"` | Any other `the_vault:` item with attribute data | Registry name, attributes |

---

## Data Reference by Item Type

### Gear

```lua
local detail = chest.getItemDetail(slot)
local gear = detail.vaultData
-- gear.itemType       = "Gear"
-- gear.name           = "Velvet Netherite Sword"     (display name)
-- gear.level          = 75                            (item level)
-- gear.rarity         = "RARE"                        (SCRAPPY|COMMON|RARE|EPIC|OMEGA|UNIQUE|SPECIAL|CHAOTIC)
-- gear.state          = "IDENTIFIED"                  (UNIDENTIFIED|ROLLING|IDENTIFIED)
-- gear.identified     = true
-- gear.gearType       = "SWORD"                       (gear class name)
-- gear.equipmentSlot  = "mainhand"                    (MC equipment slot name, may be nil)
--
-- If identified:
-- gear.repairSlots    = { total = 3, used = 1 }
-- gear.durability     = { total = 2048, current = 1995 }
-- gear.craftingPotential = { current = 12, max = 45 } (absent on UNIQUE rarity)
-- gear.prefixSlots    = 2                             (max prefix modifier slots)
-- gear.suffixSlots    = 2                             (max suffix modifier slots)
-- gear.isLegendary    = true                          (only present if true)
-- gear.isSoulbound    = true                          (only present if true)
-- gear.uniqueKey      = "the_vault:gear/unique_key"   (only on UNIQUE items)
-- gear.gearName       = "Sword of the Ancients"       (custom name, if set)
-- gear.model          = "Warrior's Blade"             (transmog model display name)
--
-- gear.implicits = { ... }    (see Modifier format below)
-- gear.prefixes  = { ... }
-- gear.suffixes  = { ... }
-- gear.attributes = { ... }   (base attributes not covered above)
```

### Tool

```lua
-- tool.itemType  = "Tool"
-- tool.name      = "Vault Pickaxe"
-- tool.level     = 50
-- tool.rarity    = "COMMON"
-- tool.repairSlots  = { total = 2, used = 0 }
-- tool.durability   = { total = 2048, current = 2048 }
-- tool.implicits = { ... }
-- tool.prefixes  = { ... }
-- tool.suffixes  = { ... }
```

### Jewel

```lua
-- jewel.itemType  = "Jewel"
-- jewel.name      = "Jewel of Fortune"
-- jewel.level     = 60
-- jewel.rarity    = "EPIC"
-- jewel.implicits = { ... }
-- jewel.prefixes  = { ... }
-- jewel.suffixes  = { ... }
```

### Trinket

```lua
-- trinket.itemType    = "Trinket"
-- trinket.identified  = true
-- trinket.name        = "Lucky Horseshoe"
-- trinket.uses        = 5                 (remaining uses)
-- trinket.slot        = "necklace"        (curios slot identifier)
-- trinket.effect      = "the_vault:lucky" (registry name of the trinket effect)
```

### Charm

```lua
-- charm.itemType  = "Charm"
-- charm.identified = true
-- charm.name      = "Idona's Charm"
-- charm.rarity    = "RARE"
-- charm.uses      = 3
-- charm.god       = "Idona"              (god name)
-- charm.godReputation = 100              (reputation value)
-- charm.prefixes  = { ... }
```

### Inscription

```lua
-- inscription.itemType = "Inscription"
-- inscription.size     = 5               (inscription size)
-- inscription.rooms    = { "Treasure Room", "Mob Room", "Challenge Room" }
```

### Catalyst

```lua
-- catalyst.itemType   = "Catalyst"
-- catalyst.size       = 3                         (catalyst size)
-- catalyst.modifiers  = { "the_vault:gilded", "the_vault:chaotic" }
-- catalyst.isSuper    = false
```

### Vault Crystal

```lua
-- crystal.itemType     = "VaultCrystal"
-- crystal.objective    = "kill_boss"      (objective type from NBT)
-- crystal.theme        = "classic"
-- crystal.themeId      = "the_vault:classic"
-- crystal.layout       = "classic"
-- crystal.timeType     = "regular"
-- crystal.time         = 1200             (ticks)
-- crystal.modifiers    = { { id = "the_vault:gilded" }, { id = "the_vault:chaotic", count = 2 } }
-- crystal.modifierType = "pool"
-- crystal.instability  = 0.5
-- crystal.capacity     = 10
-- crystal.level        = 75
```

### Vault Doll

```lua
-- doll.itemType    = "VaultDoll"
-- doll.name        = "Roger's Vault Doll"
-- doll.playerName  = "RogerBN"
-- doll.playerUUID  = "a1b2c3d4-..."
-- doll.experience  = 1500
```

### Card

```lua
-- card.itemType = "Card"
-- card.name     = "Vault Card"
-- card.cardData = "..."    (string representation of card data)
```

### Augment

```lua
-- augment.itemType = "Augment"
-- augment.name     = "Vault Augment"
-- augment.theme    = "the_vault:classic"
```

### Etching

```lua
-- etching.itemType    = "Etching"
-- etching.name        = "Vault Etching"
-- etching.level       = 50
-- etching.rarity      = "RARE"
-- etching.state       = "IDENTIFIED"
-- etching.identified  = true
-- etching.implicits   = { ... }
-- etching.prefixes    = { ... }
-- etching.suffixes    = { ... }
```

### Generic VH Item (fallback)

```lua
-- generic.itemType     = "VaultItem"
-- generic.name         = "Mystery Box"
-- generic.registryName = "the_vault:mystery_box"
-- generic.attributes   = { { name = "...", value = "..." }, ... }
```

---

## Modifier Format

Modifiers (in `implicits`, `prefixes`, `suffixes` arrays) have this structure:

```lua
{
    name  = "+5.2% Reach Distance",      -- human-readable modifier name
    value = 5.2,                          -- the rolled value (number, boolean, string, or table)
    tier  = 3,                            -- rolled tier (1-indexed), absent for deterministic modifiers
    min   = 2.0,                          -- minimum possible value at this tier (if available)
    max   = 8.0,                          -- maximum possible value at this tier (if available)
    group = "the_vault:reach_distance",   -- modifier group ID (if available)
    identifier = "the_vault:reach_dist_t3", -- modifier registry identifier (if available)

    -- Category flags (only present when true):
    legendary = true,                     -- part of legendary set
    crafted = true,                       -- player-crafted modifier
    frozen = true,                        -- frozen (cannot be rerolled)
    greater = true,                       -- greater modifier
    abyssal = true,                       -- abyssal modifier
    corrupted = true,                     -- corrupted modifier
    imbued = true,                        -- imbued modifier
    abilityEnhancement = true,            -- ability enhancement
}
```

### Complex Value Types

Some modifiers have structured values instead of simple numbers:

**Ability Level:**
```lua
{ ability = "the_vault:dash", levelChange = 2 }
```

**Mana Per Loot:**
```lua
{ manaGenerated = 5.0, manaGenerationChance = 0.15 }
```

**Random God Vault Modifier:**
```lua
{ modifier = "gilded", count = 3, time = 600 }
```

**Ability AOE / Cooldown:**
```lua
{ ability = "the_vault:nova", amount = 0.25 }
```

---

## Practical Examples

### Scan a chest for high-rarity gear

```lua
local chest = peripheral.wrap("minecraft:chest_0")
local GOOD_RARITIES = { EPIC = true, OMEGA = true, UNIQUE = true }

for slot, basic in pairs(chest.list()) do
    local detail = chest.getItemDetail(slot)
    local vd = detail.vaultData
    if vd and vd.rarity and GOOD_RARITIES[vd.rarity] then
        print(string.format("Slot %d: %s (%s %s)", slot, vd.name, vd.rarity, vd.itemType))
    end
end
```

### Find all soulbound items

```lua
local chest = peripheral.wrap("minecraft:chest_0")
for slot, basic in pairs(chest.list()) do
    local detail = chest.getItemDetail(slot)
    local vd = detail.vaultData
    if vd and vd.isSoulbound then
        print(string.format("Slot %d: %s (SOULBOUND)", slot, vd.name))
    end
end
```

### List all modifiers on a piece of gear

```lua
local function printMods(label, mods)
    if not mods then return end
    for _, m in ipairs(mods) do
        local extra = ""
        if m.tier then extra = extra .. " T" .. m.tier end
        if m.frozen then extra = extra .. " [FROZEN]" end
        if m.legendary then extra = extra .. " [LEGENDARY]" end
        print(string.format("  [%s] %s = %s%s", label, m.name, tostring(m.value), extra))
    end
end

local detail = chest.getItemDetail(1)
local vd = detail.vaultData
if vd then
    print(vd.name .. " | " .. (vd.rarity or "?") .. " | Lv" .. (vd.level or "?"))
    printMods("IMP", vd.implicits)
    printMods("PRE", vd.prefixes)
    printMods("SUF", vd.suffixes)
end
```

### Count vault crystals by objective

```lua
local chest = peripheral.wrap("minecraft:chest_0")
local counts = {}
for slot, basic in pairs(chest.list()) do
    local detail = chest.getItemDetail(slot)
    local vd = detail.vaultData
    if vd and vd.itemType == "VaultCrystal" and vd.objective then
        counts[vd.objective] = (counts[vd.objective] or 0) + 1
    end
end
for obj, n in pairs(counts) do
    print(obj .. ": " .. n)
end
```

### Check trinket uses remaining

```lua
local chest = peripheral.wrap("minecraft:chest_0")
for slot, basic in pairs(chest.list()) do
    local detail = chest.getItemDetail(slot)
    local vd = detail.vaultData
    if vd and vd.itemType == "Trinket" and vd.identified then
        print(string.format("%s — %d uses left (slot: %s)", vd.name, vd.uses or 0, vd.slot or "?"))
    end
end
```

---

## Rarity Values

| Rarity | Description |
|--------|-------------|
| `SCRAPPY` | Lowest tier |
| `COMMON` | Common quality |
| `RARE` | Rare quality |
| `EPIC` | Epic quality |
| `OMEGA` | Omega quality |
| `UNIQUE` | Unique named items (no crafting potential) |
| `SPECIAL` | Special event items |
| `CHAOTIC` | Chaotic rarity (from chaotic modifier) |

## State Values

| State | Description |
|-------|-------------|
| `UNIDENTIFIED` | Not yet identified — minimal data available |
| `ROLLING` | Currently being rolled (animation state) |
| `IDENTIFIED` | Fully identified — all modifier data available |

---

## Notes

- `vaultData` is only present on Vault Hunters items. Vanilla/other mod items return `nil`.
- Unidentified gear returns minimal data (itemType, name, level, rarity, state, identified=false).
- Modifier `min`/`max` ranges depend on the tier config being available at runtime. If the config can't be read, only `value` is returned.
- Category flags (`legendary`, `frozen`, etc.) are **only present when `true`** — check with `if mod.frozen then` not `if mod.frozen == true`.
- The `attributes` array on gear contains base properties not covered by the top-level keys or modifier lists. It may be empty.
- Crystal data is read from NBT and may vary between VH versions.
