# VH CC Tweaks

![Build](https://github.com/Akkiruk/vhcctweaks/actions/workflows/build.yml/badge.svg)
![Release](https://img.shields.io/github/v/release/Akkiruk/vhcctweaks?label=release)
[![Download Latest](https://img.shields.io/github/downloads/Akkiruk/vhcctweaks/latest/total?label=downloads)](https://github.com/Akkiruk/vhcctweaks/releases/latest)
![License](https://img.shields.io/github/license/Akkiruk/vhcctweaks)
![Minecraft](https://img.shields.io/badge/Minecraft-1.18.2-green)
![Forge](https://img.shields.io/badge/Forge-40.3.11+-orange)

A Forge mod that balances [CC:Tweaked](https://modrinth.com/mod/cc-tweaked) and [Advanced Peripherals](https://modrinth.com/mod/advanced-peripherals) for **Vault Hunters 3rd Edition**. Drop it in your `mods/` folder - all configuration is fully automatic.

## Why This Mod Exists

CC:Tweaked and Advanced Peripherals are powerful automation mods, but their default capabilities break Vault Hunters' progression in several ways:

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
- **Primary enforcement**: Mixin on `TurtleCraftCommand.execute()` - blocks the call before any crafting logic runs
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
| `EnvironmentDetectorMixin` | Advanced Peripherals | Forces `isSlimeChunk()` to always return `false` - prevents world seed reverse-engineering |
| `ChatBoxEventsMixin` | Advanced Peripherals | Removes the hidden `$` chat channel that suppresses messages from normal chat |
| `ChatBoxPeripheralMixin` | Advanced Peripherals | Blocks `sendFormattedMessage` / `sendFormattedMessageToPlayer` - prevents JSON-based message spoofing |

All AP-targeting mixins use string `targets` (not class references) so they gracefully no-op if AP is not installed.

### Recipe Overrides (via CraftTweaker)
All CC:Tweaked and AP crafting recipes are replaced with Vault-tier materials using CraftTweaker scripts in the `scripts/` folder. A JEI mixin suppresses CC's built-in impostor recipes so JEI only shows the VH versions.

**CC:Tweaked (23 recipes - `scripts/ComputerCraft.zs`)**
| Tier | Material | Items |
|------|----------|-------|
| Basic | Chromatic Iron + Larimar | Computer, Monitor, Speaker, Printer, Disk Drive, Wired Modem, Wireless Modem, Cable (6x), Turtle, Pocket Computer, Floppy Disk, Printed Page/Pages/Book, Wired Modem Full conversions |
| Advanced | Chromatic Steel + Perfect Larimar | Advanced Computer (direct + upgrade), Advanced Monitor, Advanced Wireless Modem, Advanced Turtle (direct + upgrade), Advanced Pocket Computer (direct + upgrade) |

**Advanced Peripherals (20 recipes - `scripts/AdvancedPeripherals.zs`)**
| Tier | Material | Items |
|------|----------|-------|
| Base | Iron + Chromatic Iron | Peripheral Casing |
| Basic | Chromatic Iron + Larimar | Chat Box, Player Detector, Redstone Integrator, Environment Detector, Computer Tool, AR Goggles, Memory Card |
| Mid | Chromatic Iron + Perfect Larimar | Inventory Manager, NBT Storage, AR Controller |
| Powerful | Chromatic Steel + Perfect Larimar | Energy Detector, Block Reader, Geo Scanner, Chunk Controller |
| Bridges | Chromatic Steel + Mod Components | ME Bridge (AE2), RS Bridge (Refined Storage) |
| Automata | Chromatic Steel + Vault Diamond | Weak Automata Core, Overpowered Weak/End/Husbandry Automata Cores |

### Network Configuration
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

**Security**: Strict path validation (no `..`, no absolute paths, no symlink traversal), 1 MB write limit, 16-level depth limit, allowlist character set.

### CCVault Economy API (`ccvault`)
A server-authoritative economy API that lets CC:Tweaked computers move **Vault Tokens** (from [Dog's PlayerShops](https://modrinth.com/mod/dogs-playershops)) between players. Designed for in-game shops, casinos, and trading terminals.

**Core concepts:**
- **`"player"`** - the person currently using the terminal (right-clicked it)
- **`"host"`** - the person who placed the computer block
- Every debit has an equal credit - no tokens are created or destroyed
- All transfers are permanently logged with TX IDs, amounts, parties, reason, and timestamp

**Key features:**
- **Click-to-approve authentication** - server sends an unforgeable `[APPROVE]` chat message; scripts cannot fake it
- **Session-style auth grants** - approvals stay active for a player/computer pair until disconnect or 10 minutes idle
- **Read-only public balance checks** - scripts can inspect any known player's current balance without auth, while transfers remain auth-gated
- **Rate limiting** - per-terminal (10/min) and per-player (20/min) caps prevent abuse
- **Crash-safe transfers** - Write-Ahead Log ensures incomplete transfers auto-recover on restart
- **Double-entry ledger** - all transactions are permanently recorded
- **Player commands** - `/ccvault approve <code>` and `/ccvault revoke <computerId>`

```lua
-- Quick example: charge 50 tokens
if ccvault.isAvailable() and ccvault.isAuthenticated() then
    local result, err = ccvault.transfer("player", "host", 50, "shop purchase")
    if result then print("TX: " .. result.txId) end
end
```

Full API reference: [`docs/CCVAULT_API.md`](docs/CCVAULT_API.md)

## Installation

1. Download `vhcctweaks-2.2.14.jar` from the [Releases](../../releases) page
2. Drop it into your Vault Hunters instance `mods/` folder
3. Copy `scripts/ComputerCraft.zs` and `scripts/AdvancedPeripherals.zs` from this repo into your instance's `scripts/` folder
4. Launch the game - all config patching happens automatically on first startup

> **Note:** The `.zs` scripts require [CraftTweaker](https://modrinth.com/mod/crafttweaker) and [JEITweaker](https://modrinth.com/mod/jeitweaker) (both included in Vault Hunters). The scripts are fully self-contained - each one removes the default recipes and adds VH replacements, so no edits to other files are needed.

**Requirements**: CC:Tweaked must be installed. Advanced Peripherals is optional - AP-related features activate only if AP is present.

## Configuration

A config file is generated at `serverconfig/vhcctweaks-server.toml` after first world load:

| Setting | Default | Description |
|---------|---------|-------------|
| `vault.blockCCInVault` | `true` | Block all CC/AP blocks and items inside the Vault dimension |
| `vault.vaultDimension` | `the_vault:vault` | Resource location of the Vault dimension |
| `autocrafting.lockCraftyTurtles` | `true` | Gate crafting turtles behind VH research |
| `autocrafting.autocraftingResearchName` | `Automatic Genius` | Which VH research unlocks autocrafting |
| `ccvault.authIdleTimeoutMinutes` | `10` | Inactivity timeout before a CCVault approval expires |
| `ccvault.nonceExpirySeconds` | `60` | How long the clickable approval prompt stays valid |
| `ccvault.interactionStaleSeconds` | `30` | How recent player interaction must be for financial operations |

## Testing

A comprehensive Lua test suite is included (`vhcctweaks_test.lua`) with 11 test groups:

0. Environment detection
1. Turtle craft research gate
2. Vault dimension protection
3. isSlimeChunk blocked
4. ChatBox hidden `$` channel
5. sendFormattedMessage blocked
6. AP disabled peripherals (config)
7. Recipe overrides (manual JEI check)
8. VH research entries
9. Allowed features verification
Run it on a CC computer or turtle in-game. Results are saved to both the CC filesystem and the real filesystem via the `vhcc` API.

## Building from Source

Requires **JDK 17** and **Gradle 7.6+**.

```bash
./gradlew build
```

Output: `build/libs/vhcctweaks-2.2.14.jar`

## Maintainer Release Workflow

1. Add release notes under `## [Unreleased]` in `CHANGELOG.md` as you work.
2. Run `.\.vscode\release.ps1` from a clean `master` branch checkout that matches `origin/master`.
3. The release script builds under Java 17, freezes `[Unreleased]` into the next versioned changelog section, updates README version references, deploys locally, and pushes `master` plus the new `vX.Y.Z` tag.
4. Pushing that tag triggers [`.github/workflows/release.yml`](.github/workflows/release.yml), which creates or updates the matching GitHub Release and attaches the built JAR automatically.

For quick local verification without cutting a GitHub release, use `.\scripts\build-and-deploy-vhcctweaks.ps1`.

## Project Structure

```
src/main/java/com/vhcctweaks/
|-- VHCCTweaks.java                      # Mod entry point
|-- api/VHCCTweaksAPI.java               # Lua filesystem API (sandboxed)
|-- ccvault/
|   |-- CCVaultAPI.java                  # Lua economy API (ccvault global)
|   |-- DogBridge.java                   # Dog's PlayerShops integration
|   |-- RateLimiter.java                 # Per-terminal & per-player rate limits
|   |-- SessionAuthManager.java          # Click-to-approve auth sessions
|   |-- TransactionLedger.java           # Double-entry transaction log
|   `-- TransferService.java             # Crash-safe token transfers (WAL)
|-- command/
|   `-- CCVaultCommand.java              # /ccvault approve & revoke commands
|-- config/ModConfig.java                # Forge config spec
|-- handler/
|   |-- ComputerInteractionTracker.java  # Player->Computer right-click mapping
|   |-- ComputerPlacementTracker.java    # Computer->Owner placement mapping
|   |-- ComputerReflectionHelper.java    # Shared CC reflection utilities
|   |-- CraftingLockHandler.java         # Crafty turtle research gate
|   `-- VaultProtectionHandler.java      # Vault dimension block
|-- mixin/
|   |-- TurtleCraftCommandMixin.java     # turtle.craft() block
|   |-- RecipeResolverMixin.java         # JEI impostor recipe suppression
|   |-- ChatBoxEventsMixin.java          # $ channel removal
|   |-- ChatBoxPeripheralMixin.java      # Formatted message block
|   `-- EnvironmentDetectorMixin.java    # Slime chunk block
`-- patcher/
    `-- VaultConfigPatcher.java          # Auto-patches VH + CC + AP configs

docs/
|-- CCVAULT_API.md                       # Full CCVault API reference
`-- ccvault_api_reference.lua            # Lua API stub for IDE autocompletion
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

[MIT](LICENSE) - Copyright (c) 2026 Akkiruk
