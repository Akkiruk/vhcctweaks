# VH CC Tweaks

AForge mod that balances [CC:Tweaked](https://modrinth.com/mod/cc-tweaked) and [Advanced Peripherals](https://modrinth.com/mod/advanced-peripherals) for **Vault Hunters 3rd Edition**. Drop it in your `mods/` folder — all configuration is fully automatic.

## Why This Mod Exists

CC:Tweaked and Advanced Peripherals are powerful automation mods, but their default capabilities break Vault Hunters' progression in several ways:

- Players can download exploit scripts from the internet via HTTP
- Turtles can autocraft without any research investment
- Environment Detectors can reverse-engineer the world seed via slime chunk mapping
- ChatBox peripherals can spoof server messages and create hidden communication channels
- Overpowered peripherals (chunk loading, x-ray scanning, remote inventory access) bypass VH balance
- CC/AP items can be brought into vaults, breaking the dimension's intended difficulty

VH CC Tweaks surgically addresses every one of these issues while keeping normal CC/AP gameplay fully functional.

## Features

### Research Gates
- Adds **CC: Tweaked** and **Advanced Peripherals** as separate researches in the VH **Handling** group
- Each costs **2 Knowledge Stars** and gates crafting, placement, and interaction
- Custom icons and styled descriptions appear in the VH research GUI
- Research entries are auto-injected into VH config files on first launch

### Turtle Autocrafting Lock
- `turtle.craft()` is gated behind the **Automatic Genius** research
- **Primary enforcement**: Mixin on `TurtleCraftCommand.execute()` — blocks the call before any crafting logic runs
- **Backup enforcement**: `ItemCraftedEvent` handler catches CC's FakePlayer crafting, and a periodic inventory sweep strips the crafting upgrade from turtle items
- Regular turtle features (mining, building, moving, fuel) are **not** restricted
- Checks the turtle owner's VH research progress via reflection (supports offline owners)

### Vault Dimension Protection
- Blocks **placement**, **interaction**, **use**, **mining**, and **breaking** of all CC:Tweaked and Advanced Peripherals blocks inside vaults
- Fires at `EventPriority.HIGHEST` to intercept before any other mod
- Adds wildcard entries (`computercraft:*`, `advancedperipherals:*`) to VH's own item/block blacklists
- Players receive an actionbar notification when attempting blocked actions

### Security Mixins
| Mixin | Target | Purpose |
|-------|--------|---------|
| `TurtleCraftCommandMixin` | CC:Tweaked | Blocks `turtle.craft()` without Automatic Genius research |
| `EnvironmentDetectorMixin` | Advanced Peripherals | Forces `isSlimeChunk()` to always return `false` — prevents world seed reverse-engineering |
| `ChatBoxEventsMixin` | Advanced Peripherals | Removes the hidden `$` chat channel that suppresses messages from normal chat |
| `ChatBoxPeripheralMixin` | Advanced Peripherals | Blocks `sendFormattedMessage` / `sendFormattedMessageToPlayer` — prevents JSON-based message spoofing |

All AP-targeting mixins use string `targets` (not class references) so they gracefully no-op if AP is not installed.

### Recipe Overrides (via CraftTweaker)
All CC:Tweaked and AP crafting recipes are replaced with Vault-tier materials using CraftTweaker scripts in the `scripts/` folder. A JEI mixin suppresses CC's built-in impostor recipes so JEI only shows the VH versions.

**CC:Tweaked (23 recipes — `scripts/ComputerCraft.zs`)**
| Tier | Material | Items |
|------|----------|-------|
| Basic | Chromatic Iron + Larimar | Computer, Monitor, Speaker, Printer, Disk Drive, Wired Modem, Wireless Modem, Cable (6×), Turtle, Pocket Computer, Floppy Disk, Printed Page/Pages/Book, Wired Modem Full conversions |
| Advanced | Chromatic Steel + Perfect Larimar | Advanced Computer (direct + upgrade), Advanced Monitor, Advanced Wireless Modem, Advanced Turtle (direct + upgrade), Advanced Pocket Computer (direct + upgrade) |

**Advanced Peripherals (20 recipes — `scripts/AdvancedPeripherals.zs`)**
| Tier | Material | Items |
|------|----------|-------|
| Base | Iron + Chromatic Iron | Peripheral Casing |
| Basic | Chromatic Iron + Larimar | Chat Box, Player Detector, Redstone Integrator, Environment Detector, Computer Tool, AR Goggles, Memory Card |
| Mid | Chromatic Iron + Perfect Larimar | Inventory Manager, NBT Storage, AR Controller |
| Powerful | Chromatic Steel + Perfect Larimar | Energy Detector, Block Reader, Geo Scanner, Chunk Controller |
| Bridges | Chromatic Steel + Mod Components | ME Bridge (AE2), RS Bridge (Refined Storage) |
| Automata | Chromatic Steel + Vault Diamond | Weak Automata Core, Overpowered Weak/End/Husbandry Automata Cores |

### HTTP & Network Lockdown
- HTTP API and WebSockets **disabled** in both `defaultconfigs/` and all existing world `serverconfig/` directories
- Command computers **disabled**
- Wireless modem range **reduced** (32/64 blocks normal, 16/32 in storms)
- Patches applied idempotently with marker comments to prevent re-patching

### Advanced Peripherals Config Patching
On startup, the mod patches AP config files to disable overpowered features:

| Config File | Setting | Reason |
|-------------|---------|--------|
| `peripherals.toml` | `enableChunkyTurtle = false` | Prevents chunk loading |
| `peripherals.toml` | `enableBlockReader = false` | Prevents full NBT data exposure |
| `peripherals.toml` | `enableInventoryManager = false` | Prevents remote inventory access |
| `peripherals.toml` | `enableGeoScanner = false` | Prevents x-ray ore scanning |
| `metaphysics.toml` | `enableEndAutomataCore = false` | Prevents turtle teleportation |
| `metaphysics.toml` | `enableHusbandryAutomataCore = false` | Prevents animal capture |
| `metaphysics.toml` | `overpoweredAutomataBreakChance = 0.05` | 5% break chance on OP automata |
| `world.toml` | `givePlayerBookOnJoin = false` | Removes AP guidebook spam |

### Custom Lua API (`vhcc`)
A sandboxed filesystem API exposed to all CC:Tweaked computers as the `vhcc` global:

**Server-side operations** (read/write to `<instance>/vhcc_data/`):
```lua
vhcc.write("path/file.txt", "content")     -- create/overwrite
vhcc.append("path/file.txt", "more\n")     -- append
local text = vhcc.read("path/file.txt")    -- read (nil if missing)
local items = vhcc.list("path")            -- list directory
vhcc.makeDir("path/subdir")               -- create directory
vhcc.delete("path/file.txt")              -- delete file/empty dir
vhcc.move("old.txt", "new.txt")           -- rename/move
vhcc.copy("src.txt", "dst.txt")           -- copy file
vhcc.exists("path"), vhcc.isDir("path"), vhcc.getSize("path")
vhcc.isAvailable(), vhcc.getBasePath()
```

**Client-side operations** (writes to the player's local `vhcc_data/` via network packets):
```lua
vhcc.clientWrite("file.txt", "content")
vhcc.clientAppend("file.txt", "line\n")
vhcc.clientMakeDir("subdir")
vhcc.clientDelete("file.txt")
```

**Security**: Strict path validation (no `..`, no absolute paths, no symlink traversal), 1 MB write limit, 16-level depth limit, allowlist character set. Client packets re-validate independently.

## Installation

1. Download `vhcctweaks-2.0.0.jar` from the [Releases](../../releases) page
2. Drop it into your Vault Hunters instance `mods/` folder
3. Copy `scripts/ComputerCraft.zs` and `scripts/AdvancedPeripherals.zs` from this repo into your instance's `scripts/` folder
4. Launch the game — all config patching happens automatically on first startup

> **Note:** The `.zs` scripts require [CraftTweaker](https://modrinth.com/mod/crafttweaker) and [JEITweaker](https://modrinth.com/mod/jeitweaker) (both included in Vault Hunters). The scripts are fully self-contained — each one removes the default recipes and adds VH replacements, so no edits to other files are needed.

**Requirements**: CC:Tweaked must be installed. Advanced Peripherals is optional — AP-related features activate only if AP is present.

## Configuration

A config file is generated at `serverconfig/vhcctweaks-server.toml` after first world load:

| Setting | Default | Description |
|---------|---------|-------------|
| `vault.blockCCInVault` | `true` | Block all CC/AP blocks and items inside the Vault dimension |
| `vault.vaultDimension` | `the_vault:vault` | Resource location of the Vault dimension |
| `autocrafting.lockCraftyTurtles` | `true` | Gate crafting turtles behind VH research |
| `autocrafting.autocraftingResearchName` | `Automatic Genius` | Which VH research unlocks autocrafting |

## Testing

A comprehensive Lua test suite is included (`vhcctweaks_test.lua`) with 12 test groups:

0. Environment detection
1. HTTP API disabled
2. Turtle craft research gate
3. Vault dimension protection
4. isSlimeChunk blocked
5. ChatBox hidden `$` channel
6. sendFormattedMessage blocked
7. AP disabled peripherals (config)
8. Recipe overrides (manual JEI check)
9. VH research entries
10. Allowed features verification
11. Client-side file writing

Run it on a CC computer or turtle in-game. Results are saved to both the CC filesystem and the real filesystem via the `vhcc` API.

## Building from Source

Requires **JDK 17** and **Gradle 7.6+**.

```bash
./gradlew build
```

Output: `build/libs/vhcctweaks-2.0.0.jar`

## Project Structure

```
src/main/java/com/vhcctweaks/
├── VHCCTweaks.java              # Mod entry point
├── api/VHCCTweaksAPI.java       # Lua filesystem API (sandboxed)
├── config/ModConfig.java        # Forge config spec
├── handler/
│   ├── ComputerInteractionTracker.java  # Player↔Computer mapping
│   ├── CraftingLockHandler.java         # Crafty turtle research gate
│   └── VaultProtectionHandler.java      # Vault dimension block
├── mixin/
│   ├── TurtleCraftCommandMixin.java     # turtle.craft() block
│   ├── ChatBoxEventsMixin.java          # $ channel removal
│   ├── ChatBoxPeripheralMixin.java      # Formatted message block
│   └── EnvironmentDetectorMixin.java    # Slime chunk block
├── network/
│   ├── ClientFilePacket.java    # Client-side file operations
│   └── VHCCNetwork.java        # Network channel registration
└── patcher/
    └── VaultConfigPatcher.java  # Auto-patches VH + CC + AP configs
```

## Compatibility

| Component | Version |
|-----------|---------|
| Minecraft | 1.18.2 |
| Forge | 40.3.11+ |
| CC:Tweaked | 1.101.3+ (required) |
| Advanced Peripherals | 0.7.31r+ (optional) |
| Vault Hunters 3rd Edition | Any build |

## License

[MIT](LICENSE) — Copyright (c) 2026 Akkiruk
