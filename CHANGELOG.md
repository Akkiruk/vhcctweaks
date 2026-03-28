# Changelog

All notable changes to VH CC Tweaks are documented here.

## [Unreleased]

### Changed
- Moved CCVault terminal authorization fully into `VHCCTweaks` with server-side persistence keyed by computer ID.
- Approved CCVault auth now lasts up to 30 real-time minutes and expires after 10 minutes of inactivity instead of clearing immediately on logout.
- `ccvault.requestAuth()` now treats an already-valid grant as success instead of forcing a fresh approval prompt.

## [2.2.7] - 2026-03-25

### Changed
- Removed CCVault transfer rate limits for players and computers.
## [2.2.6] - 2026-03-25

### Added
- Claimed pocket computer hosts during installer setup so deployed pocket workflows inherit the correct ownership state.

### Changed
- Added CCVault pocket-computer interaction tracking and related placement handling updates.
- Automated patch-version bumps from tracked mod source changes so release numbering stays aligned with the shipped code.

### Security
- Added computer filesystem lockdown support.

## [2.2.4] - 2026-03-24

### Changed
- Raised CC:Tweaked monitor max size to 16x16 by automatically patching ComputerCraft server configs.

## [2.2.3] - 2026-03-23

### Fixed
- Closed the CCVault WAL to ledger crash window by reconstructing missing ledger entries from CREDITED recovery data and persisting the metadata needed to do it safely.
- Added participant-specific ledger history files and exact transaction ID matching for faster history lookups and stricter verification.
- Guarded Advanced Peripherals CraftTweaker recipe overrides so they no-op cleanly when the mod is absent.

## [2.2.2] - 2026-03-23

### Security
- **CRITICAL**: Enforced a single authenticated CCVault session principal per computer. Approving a new player on a terminal now replaces any previous authenticated player for that computer.
- **HIGH**: Hardened CCVault auth checks to require that the current interacting player matches the terminal's authenticated principal before any economy operation.

### Changed
- `SessionAuthManager` now tracks current authenticated player per computer and revokes stale principals safely.
- `CCVaultAPI.requireAuth()` now fails closed when session principal and interaction player do not match.

## [2.2.1] - 2026-03-23

### Changed
- Removed CCVault escrow runtime and Lua API methods (scrow, 
esolveEscrow, cancelEscrow, getEscrowInfo).
- Migrated economy flow to transfer-at-end settlement across integrated game scripts and references.
- Removed escrow-related config keys and server tick/recovery wiring.
- Updated API documentation to reflect transfer-at-end model.

## [2.2.0] - 2026-03-23

### Security
- **CRITICAL**: Fixed race condition where escrow hold file was written before debit confirmation - crash between write and debit would create phantom refund (tokens from nothing). Now uses two-phase PENDING->HELD status.
- **CRITICAL**: Fixed 
esolve() expired-path ignoring DogBridge.add() return value - failed refund would destroy tokens silently. Now preserves escrow for retry on failure.
- **CRITICAL**: Fixed WAL DEBITED status write being best-effort - disk failure after debit but before status update would cause recovery to discard the intent, destroying tokens. Now reverses debit immediately on write failure.
- **CRITICAL**: Fixed crash window between successful credit and WAL deletion - recovery would re-credit, creating tokens from nothing. Added CREDITED status to make WAL recovery idempotent.
- **CRITICAL**: All escrow resolve/cancel/tick paths now write COMPLETED status before deleting hold file - prevents double-payout/double-refund if crash occurs between credit and file deletion.
- **HIGH**: Fixed player-swap exploit where escrow recipient identity could change between creation and resolution. Recipient is now verified against original source/host UUIDs stored in the escrow.
- **MEDIUM**: DogBridge.add()/remove() now check boolean return values from Dog's VaultTokenAPI instead of ignoring them.

### Changed
- Escrow lifecycle statuses: PENDING -> HELD -> COMPLETED
- Transfer WAL lifecycle statuses: PENDING -> DEBITED -> CREDITED -> (deleted)
- Recovery logic updated for all new statuses

## [2.1.0] - 2026-06-14

### Added
- **CCVault Economy API** (`ccvault` Lua global): Server-authoritative token economy for CC:Tweaked computers
  - Transfer Vault Tokens between `"player"` (terminal user) and `"host"` (computer owner)
  - Click-to-approve authentication via unforgeable server chat messages
  - Per-terminal (10/min) and per-player (20/min) rate limiting
  - Crash-safe transfers with Write-Ahead Log (auto-recovers on restart)
  - Double-entry transaction ledger with permanent TX ID logging
  - Player commands: `/ccvault approve <code>`, `/ccvault revoke <computerId>`
- **Dog's PlayerShops integration** (`DogBridge`): Bridges ccvault to the existing Vault Hunters token economy
- **Computer placement tracker**: Maps computers to the player who placed them (for `"host"` identity)
- **Shared reflection helper** (`ComputerReflectionHelper`): Cached method handles for CC block entity introspection
- **Full API documentation**: `docs/CCVAULT_API.md` with reference tables, examples, error catalog, and security model
- **Lua IDE stub**: `docs/ccvault_api_reference.lua` for autocompletion in editors

### Fixed
- `getServerPlayerByUuid()` stub in `CCVaultAPI` always returned `null` - now resolves via `ServerLifecycleHooks`
- `requestAuth()` now returns distinct errors for pending vs new auth requests
- `RateLimiter` TOCTOU race condition: replaced volatile + AtomicInteger with synchronized sliding window
- Transaction ID generation switched from insecure `Math.random()` to `SecureRandom`
- Financial operations now use fresh player lookups (30s staleness check) instead of cached references

### Changed
- Extracted duplicated reflection code from `ComputerInteractionTracker` and `ComputerPlacementTracker` into shared `ComputerReflectionHelper`
- Version bumped to 2.1.0

## [2.0.0] - 2026-03-14

### Added
- **Advanced Peripherals support**: Full exploit coverage for AP alongside CC:Tweaked
  - Research gate for AP in the VH Handling skill group (2 Knowledge Stars)
  - AP blocks/items blocked inside the Vault dimension
  - AP config patching (disables chunk loading, geo scanning, block reading, inventory management, end automata, husbandry automata)
  - Increased overpowered automata break chance to 5%
- **CraftTweaker recipe scripts** (`scripts/` folder): Self-contained `.zs` files replace all default recipes
  - `ComputerCraft.zs` - 23 recipes covering every craftable CC:Tweaked item
  - `AdvancedPeripherals.zs` - 20 recipes covering every craftable AP item
  - Each script removes default recipes + adds VH versions - just copy to `scripts/`
- **RecipeResolverMixin**: Suppresses CC:Tweaked's JEI impostor recipe plugin so JEI only shows VH recipes
- **Security mixins for Advanced Peripherals**
  - `EnvironmentDetectorMixin`: Blocks `isSlimeChunk()` to prevent world seed reverse-engineering
  - `ChatBoxEventsMixin`: Removes hidden `$` chat channel that suppresses messages from normal chat
  - `ChatBoxPeripheralMixin`: Blocks `sendFormattedMessage` / `sendFormattedMessageToPlayer` to prevent message spoofing
- **Custom Lua API** (`vhcc`): Sandboxed filesystem API exposed to all CC computers
  - Server-side: read, write, append, list, delete, move, copy, exists, isDir, getSize, makeDir
  - Strict path validation, 1 MB write limit, 16-level depth limit
- **Computer interaction tracker**: Maps computers to the last player who right-clicked them
- **Comprehensive Lua test suite** (`vhcctweaks_test.lua`) with 12 test groups

### Fixed
- **CRITICAL**: `ChatBoxEventsMixin` used `Object` parameter types instead of `ServerChatEvent`/`CommandEvent`, causing a hard crash (`InvalidInjectionException`) that prevented the game from launching when Advanced Peripherals was installed
- `ChatBoxEventsMixin` `onCommand` handler now only targets `/say $...` commands instead of any command containing `$`
- Removed unnecessary reflection in `ChatBoxEventsMixin` - now uses direct Forge event type methods
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
- Auto-config patching for VH JSON files
- Reduced wireless modem range, disabled command computers
