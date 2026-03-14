# Changelog

All notable changes to VH CC Tweaks are documented here.

## [2.0.0] - 2026-03-14

### Added
- **Advanced Peripherals support**: Full exploit coverage for AP alongside CC:Tweaked
  - Research gate for AP in the VH Handling skill group (2 Knowledge Stars)
  - AP blocks/items blocked inside the Vault dimension
  - 5 AP recipe overrides requiring Chromatic Iron/Steel
  - AP config patching (disables chunk loading, geo scanning, block reading, inventory management, end automata, husbandry automata)
  - Increased overpowered automata break chance to 5%
- **Security mixins for Advanced Peripherals**
  - `EnvironmentDetectorMixin`: Blocks `isSlimeChunk()` to prevent world seed reverse-engineering
  - `ChatBoxEventsMixin`: Removes hidden `$` chat channel that suppresses messages from normal chat
  - `ChatBoxPeripheralMixin`: Blocks `sendFormattedMessage` / `sendFormattedMessageToPlayer` to prevent message spoofing
- **Custom Lua API** (`vhcc`): Sandboxed filesystem API exposed to all CC computers
  - Server-side: read, write, append, list, delete, move, copy, exists, isDir, getSize, makeDir
  - Client-side: clientWrite, clientAppend, clientMakeDir, clientDelete (via network packets to player's local disk)
  - Strict path validation, 1 MB write limit, 16-level depth limit
- **Network system** for client-side file operations (`VHCCNetwork`, `ClientFilePacket`)
- **Computer interaction tracker**: Maps computers to the last player who right-clicked them
- **Comprehensive Lua test suite** (`vhcctweaks_test.lua`) with 12 test groups

### Fixed
- **CRITICAL**: `ChatBoxEventsMixin` used `Object` parameter types instead of `ServerChatEvent`/`CommandEvent`, causing a hard crash (`InvalidInjectionException`) that prevented the game from launching when Advanced Peripherals was installed
- `ChatBoxEventsMixin` `onCommand` handler now only targets `/say $...` commands instead of any command containing `$`
- Removed unnecessary reflection in `ChatBoxEventsMixin` — now uses direct Forge event type methods
- `VaultConfigPatcher` HTTP disabling now scoped to the `[http]` section instead of matching any `enabled = true` line
- `vhcc.getBasePath()` no longer exposes the full server filesystem path (returns folder name only)

### Changed
- Version bumped to 2.0.0 to reflect scope expansion
- `the_vault` dependency version range changed to `[0,)` (fixes crash with VH's non-standard version format)
- `advancedperipherals` added as optional dependency in `mods.toml`

## [1.0.1] - 2026-03-01

### Fixed
- Dedicated server support: patches `world/serverconfig/` path in addition to `saves/*/serverconfig/`

## [1.0.0] - 2026-02-28

### Added
- Initial release
- CC:Tweaked research gate in VH Handling group (2 Knowledge Stars)
- Turtle autocrafting lock behind Automatic Genius research (mixin + event + inventory sweep)
- Vault dimension protection (blocks all CC blocks/items in vaults)
- 15 CC:Tweaked recipe overrides requiring Chromatic Iron/Steel
- HTTP API and WebSocket lockdown
- Auto-config patching for VH JSON files and CC server config
- Reduced wireless modem range, disabled command computers
