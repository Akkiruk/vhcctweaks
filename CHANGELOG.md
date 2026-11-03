# Changelog

All notable changes to VH CC Tweaks are documented here.

## [Unreleased]

Bigger one. Computers can finally reach each other's stuff without running cable across the map.

### Linked ender modems

- Ender modems have `link(id)`, `unlink(id)` and `getLinks()`. Link to another computer you own in the same dimension, and its peripherals show up as `<id>:<name>`, like `5:left` or `5:minecraft:chest_3`. Any distance.
- Both computers need an ender modem. A plain wireless modem can't link and doesn't get these methods.
- It works like a wired modem: `peripheral.getNames()`, `peripheral.wrap()` and `pushItems`/`pullItems` all see linked peripherals, local to remote or remote to remote. Ender modems now also report the `peripheral_hub` type.
- A touch on a linked monitor sends `monitor_touch` with the linked name, and counts as a touch on both computers.
- If the other computer unloads, its peripherals detach and calls fail with "computer 5 is not loaded". They come back when it loads. Nothing gets chunk loaded.
- Links aren't saved. Set them again on boot. The limit is `links.maxLinksPerComputer` (64).

### Inventories

- `listDetailed()` gets `getItemDetail` for every slot in one go.
- `moveMany(moves)` does a whole list of `pushItems` in one call, up to `inventory.maxBatchMoves` (256). It stops early once it has used `inventory.batchTimeBudgetMicros` of server time and tells you how many it got through, so call it again with the rest.
- `watch()` and `unwatch()` send `inventory_changed` when a chest's contents change, from a player, a hopper or a computer. One event per tick at most.

### CCVault

- `ccvault.requestAllowance(player, maxTokens, minutes)` asks a player once in chat. If they approve, this computer can charge them up to that much with no fresh touch until it runs out, runs out of time, they leave, or they `/ccvault revoke`. The answer comes back as `ccvault_allowance`. There's also `getAllowance` and `endAllowance`.
- `/ccvault revoke` with no number revokes everything you've approved.
- `ccvault.getPlayerVaultLevel(player)` gives a player's Vault Hunters level by name or UUID, even when they're offline. No approval needed.
- `ccvault.getPlayerEquipment(player)` shows what an online player is wearing and holding, Curios slots too, with the same item details `getItemDetail` gives.
- The host paying a player with `transfer("host", "player:Name", ...)` no longer needs that player to have touched the computer recently, so a winner who walked off still gets paid. Charging a player still needs the touch.
- `interactionStaleSeconds` can go up to an hour now (was 5 minutes).

## [2.2.35] - 2026-10-31

- `ccvault.claimHost()` now takes a chat click: it sends a `[CONFIRM]` prompt and the claim happens when the player clicks, so it returns before the claim lands. Scripts that expected an immediate result need updating.
- Added `ccvault.forgetHost()`, which clears a computer's host and lifts its terminal lock.
- Computers are owned by whoever places them. The first click no longer takes ownership, and tapping a monitor never does.
- A placed computer with no host offers itself in chat with a `[CLAIM]` button on every right-click, until someone takes it. An offer never takes a computer that already has a host.
- `vhcc_data` now lives in the world folder, and owners, locks, sessions and ledgers no longer leak between worlds whose computer IDs overlap. The first world started after updating takes over the old instance-wide folder.
- Each computer now has a private `vhcc` folder at `vhcc_data/computers/<id>`, with a disk allowance set by `vhccFiles.maxBytesPerComputer` (16 MB by default). Files written to the shared folder by older versions stay on disk but are no longer visible to scripts.
- `getPlayerBalance` no longer blocks computers on a Mojang lookup. The first call for an unseen name can answer "try again" while it resolves in the background.
- Monitor clicks and `vhcc_touch` now reach every computer attached to the monitor, including through wired modems, instead of guessing from adjacent blocks.
- Leaving a terminal open no longer takes the "player" role from someone tapping that computer's monitor.
- `owners.json` and `locks.json` are saved atomically, and a damaged one is set aside instead of stopping the mod from loading.
- A line cut short by a crash no longer breaks ledger lookups, startup or the next entry.
- `verifyTransaction` reads the player's own history instead of the whole ledger, and both sides of a player-to-player transfer can now see it.
- `/vhcc pc put` refuses to run without an upload instead of emptying the target file.
- Removed the `remoteControl` config. Any player can now use `/vhcc pc`, on any server. Every call is still audited, and a remotely controlled computer still refuses transfers for anyone but the controller until it reboots.
- Removed the write-ahead log recovery, which nothing has written since 2.2.34. Any intent file still present is reported in the log at startup for an admin to settle.
- Removed the legacy research cleanup and the reflection helper, and corrected docs that described a rate limiter and self-play behaviour the mod does not have.
- Documented the `computerLock.enabled` config, added in 2.2.34. Turning it off lets everyone in without forgetting which computers are locked.
- Added unit tests, run by `gradlew build`.
## [2.2.34] - 2026-10-30

- Moved `ccvault` from the removed PlayerShops `VaultTokenAPI` to the ParadiseCore economy. On the current pack the old class no longer exists, so `ccvault.isAvailable()` was false and every transfer failed.
- Transfers now use the economy's single database transaction instead of a separate debit and credit. New transfers write no WAL intents; intents left by older versions are still recovered at startup.
- `ccvault.enabled` now actually turns the API off.
- Added `/vhcc pc` remote control for the client debug tool: read terminals and monitors, send input, manage files and run code. Allowed for the singleplayer owner and for players named in `remoteControl.allowedPlayers`, and nobody else. Set `remoteControl.enabled` to false to turn it off. Every call is audited to `vhcc_data/remote/audit.log`, and the server log states the setting at every startup.
- A remotely controlled computer refuses CCVault transfers for anyone but the controller until it reboots.
- Added `vhcc.rcReply`, which the Roger supervisor uses to answer remote code requests.
- Closed a hole in the `vhcc` file API: scripts could write the mod's own `ccvault` records, including a forged WAL intent that would mint tokens at the next restart. `ccvault` and `remote` are now reserved.
- Added the `vhcc_touch` event, which names the player behind each monitor touch.
- Sessions are now per player per computer, and `getBalance` and `transfer` accept `"player:Name"` for a named player at the table. One player approving no longer ends another's session.
- Added `ccvault.setTerminalLock` and `ccvault.isTerminalLocked` for owner-only computers.
- Added `/vhcc pc <seq> ledger <player> <limit>`, `peripherals <id>`, `monitorof <id> <side>` and `serverlog <lines>` for admins. `serverlog` returns only lines about computers or this mod.
- Fixed `vaultData` for crystals: it read lowercase NBT keys the game has never written, so crystals only ever returned `itemType`.
- `vaultData` now covers items in the `woldsvaults` and `vaultadditions` namespaces, and every item with data includes `rawNbt`, so Lua can read fields this mod has no parser for. Wold's gear registers under `the_vault` and already worked. Etchings report their attributes, including the greed tier.
- Removed the RS Bridge recipe, matching the server's build: Refined Storage is not in the pack.
- Builds are always UTF-8, so Windows builds no longer mangle em dashes in Lua-facing messages.
- Deploy helper and docs point at the `Vaulters-Paradise` instance.
## [2.2.23] - 2026-04-10

- Removed the `Advanced Peripherals` Vault research injection so startup cleanup now strips both legacy CC and AP research entries, GUI styles, and skill descriptions.
## [2.2.22] - 2026-04-09

- Removed the artifact projector autofill helper from vhcctweaks; it now lives in a separate standalone mod.
## [2.2.21] - 2026-04-09

- Added a client-side artifact projector autofill helper that batch-places artifacts from your inventory by right-clicking your own incomplete projector.
## [2.2.19] - 2026-04-07

- Removed the `CC: Tweaked` Vault research injection and the `Automatic Genius` crafty-turtle research gate.
- Existing installs now clean previously injected `CC: Tweaked` research entries, GUI styles, and skill descriptions from Vault config JSON on startup.
## [2.2.17] - 2026-04-04

- Increased monitor interaction traversal to support full 16x16 ComputerCraft monitors and resolved CCVault interaction players from the live server player list instead of cached `ServerPlayer` instances.
## [2.2.16] - 2026-04-04

- Moved the `CC: Tweaked` and `Advanced Peripherals` researches into Vault's Addons section and corrected their GUI positions so they no longer overlap existing Handling researches.
## [2.2.15] - 2026-04-04

- Fixed dedicated-server startup patching so vhcctweaks now scans dedicated world `serverconfig` locations for ComputerCraft monitor size updates and auto-syncs its managed CraftTweaker recipe scripts into the root `scripts/` folder.
## [2.2.14] - 2026-04-04

- Updated the Vault compile target to `the_vault-1.18.2-3.21.0.jar` and verified vhcctweaks builds cleanly against Vault 3.21.
## [2.2.13] - 2026-04-04

- Fixed Vault item detail serialization to avoid client-only dynamic model lookups on the server, returning the model resource location instead.
## [2.2.12] - 2026-04-02

- Hardened `ccvault` economy availability checks so balance reads fail closed until PlayerShops token data is initialized.
- Expanded `ccvault.getPlayerBalance(playerName)` to accept UUID strings and fall back to a profile repository lookup when a player is not currently online or cached.
## [2.2.11] - 2026-03-30

### Added
- Added `ccvault.getPlayerBalance(playerName)` for read-only balance checks on any known player without requiring terminal authorization.
## [2.2.10] - 2026-03-29

### Changed
- Rolled CCVault authorization back to a session-style grant that clears on disconnect or after 10 minutes of inactivity.
- Removed the 30-minute max-lifetime model and disk-backed auth session persistence.
## [2.2.8] - 2026-03-27

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
