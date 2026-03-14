# VH CC Tweaks

A companion mod for **Vault Hunters 3rd Edition** that balances [CC:Tweaked](https://modrinth.com/mod/cc-tweaked) to fit the modpack's progression system. Drop it in your `mods/` folder and everything is configured automatically.

## Features

### Research Gate
- Adds **CC: Tweaked** as a research in the **Handling** group (costs 2 Knowledge Stars)
- Shows up in the research GUI with an icon and description, just like other mods
- All CC blocks and items are locked behind this research (crafting, placement, interaction)

### Turtle Autocrafting Lock
- `turtle.craft()` is gated behind the **Automatic Genius** research
- Uses a Mixin on `TurtleCraftCommand.execute()` — catches all paths including runtime `turtle.equipRight()` with a crafting table
- Regular turtle features (mining, building, moving, fuel) are **not** restricted
- Checks the turtle owner's VH research progress via reflection

### Vault Dimension Protection
- **Blocks placement** of all CC:Tweaked blocks inside vaults
- **Blocks interaction** with CC blocks/items inside vaults
- Players get a notification when attempting blocked actions

### Recipe Overrides
All CC:Tweaked recipes are replaced to require Vault-tier materials:
- **Basic items** (computers, modems, monitors, etc.) → **Chromatic Iron Ingots**
- **Advanced items** (advanced computers/turtles) → **Chromatic Steel Ingots**

### HTTP Lockdown
- HTTP API and websockets disabled in both `defaultconfigs/` and existing world `serverconfig/` directories
- Command computers disabled
- Wireless modem range reduced

### Auto-Config Patching
On startup, the mod automatically patches VH config files:
- `vault_general.json` — adds `computercraft:*` to item/block blacklists
- `researches.json` — adds CC: Tweaked research entry
- `researches_groups.json` — adds CC: Tweaked to the Handling group
- `researches_gui_styles.json` — adds icon and position for the research GUI
- `skill_descriptions.json` — adds the research description
- `computercraft-server.toml` — disables HTTP in default and existing world configs

## Installation

1. Download `vhcctweaks-1.0.0.jar` from the [Releases](../../releases) page
2. Drop it into your Vault Hunters instance `mods/` folder
3. Launch the game — all config patching happens automatically

## Configuration

A config file is generated at `serverconfig/vhcctweaks-server.toml` after first launch:
- Toggle vault protection on/off
- Toggle crafty turtle lock on/off
- Change the vault dimension ID
- Change which research unlocks turtle autocrafting (default: `Automatic Genius`)

## Building from Source

Requires **JDK 17**.

```bash
./gradlew build
```

The built jar will be at `build/libs/vhcctweaks-1.0.0.jar`.

## Compatibility

- Minecraft 1.18.2
- Forge 40.3.11+
- CC:Tweaked 1.101.3+
- Vault Hunters 3rd Edition

## License

MIT
