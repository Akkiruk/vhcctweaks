# CCVault API — Developer Documentation

> **Server-authoritative economy API for CC:Tweaked computers on Vault Hunters**
> Part of the **vhcctweaks** mod

---

## New in 2.2.37

The whole contract is in `docs/SPEC_2_2_37.md`. Built against Vault Hunters 3.21.74.

- `ccvault.getPlayerGearPower(player)`: Gear Score, Gear Power, the multipliers behind it, the six slot scores and the peak.
- `ccvault.getPlayerTemper(player)`: Dust, and the level and multiplier of Might, Magic, Vigor and Tool.
- `ccvault.getPlayerReputation(player)`: reputation with each vault god.
- `ccvault.getLimits()`: the server's limits, so a program does not have to guess them.
- `ccvault.getPlayerSkills(player)`: abilities with their specialization and level, talents, expertises and prestige powers. Offline from the saved profile.
- `ccvault.getPlayerStats(player)`: an online player's stat screen totals, with each capped stat's cap. `getLimits().statCaps` has the coded caps.
- `getPlayerEquipment` also says which hotbar weapon Gear Power counts (`weapon`, `weaponHotbarSlot`) and gives the whole `hotbar`.
- Inventories can `identify(slot, player [, innerSlot])` Vault Hunters gear the way the Identification Stand does for that player. The player has to be online and have approved the computer with `ccvault.requestAuth()`, the same approval `"player:Name"` uses. See `docs/SPEC_2_2_37.md` section 8.
- Inventories can look into backpacks and shulker boxes in their slots with `listContents(slot)` and move things out with `pushContents` or a `moveMany` move with `inner`. Section 7 of the spec.
- See [Player Vault Data](#player-vault-data) below. Item details gained a per-piece `gearScore`; see `docs/VAULT_ITEM_API.md`.

## New in 2.2.36

The whole contract is in `docs/SPEC_2_2_36.md`. For money calls:

- `ccvault.transfer`, and `ccvault.getBalance` or `ccvault.getPlayerBalance` for a player who is not on this server, run their database part on one economy thread, in the order they were made, and **yield** until it answers. The answer arrives as the event `vhcc_economy` with `(ticket, results...)`; the call takes its own ticket's event and returns the results. Other coroutines (for example in `parallel`) get their events as usual while it waits; the waiting coroutine itself sees no other events, exactly as during `getItemDetail`.
- `terminate` while waiting raises `Terminated` in the caller. A transfer that had started still completes, and an allowance it drew on is charged.
- A database that does not answer within 20 seconds gives `nil, "economy timed out - the transfer may still complete, check verifyTransaction"`.
- Checks and refusals (`not authenticated`, `amount must be positive`, a missing touch, and the like) still answer at once without yielding.
- `ccvault.transfer("host", "player:Name", ...)` needs no fresh touch from that player. Spending allowances: `requestAllowance`, `getAllowance`, `endAllowance`. A player's level: `getPlayerVaultLevel`. What they wear: `getPlayerEquipment`.

## New in 2.2.24

- `vhcc_touch` event: `local _, playerName = os.pullEvent("vhcc_touch")`. It arrives immediately before the `monitor_touch` from that player's touch on an adjacent monitor.
- `"player:Name"` target for `getBalance` and `transfer`: a named player at this table. They must be online and have approved this computer; the approval lasts until they disconnect or go idle, with no fresh touch needed for each transfer (since 2.2.37). Several players can hold sessions on one computer at once. `"player"` still means whoever touched last.
- `ccvault.setTerminalLock(locked)`, `ccvault.isTerminalLocked()`: the owner can make the terminal and block owner-only. Monitors still work for everyone, and server admins always get in.

## Overview

CCVault lets ComputerCraft scripts move **Vault Tokens** between two parties:

- **`"player"`** — the person currently using the terminal (right-clicked a block or used a pocket computer)
- **`"host"`** — the computer's owner: whoever placed it, or whoever confirmed `ccvault.claimHost()`. This is permanent until `ccvault.forgetHost()` is called or another player confirms a claim

That's it. No raw UUIDs, no arbitrary targeting, no creating or destroying tokens. Every debit has an equal credit. The server controls everything — your script just asks nicely.

---

## Quick Start

```lua
-- 1. Check the economy is loaded
if not ccvault.isAvailable() then
    print("Economy offline") return
end

-- 2. Wait for someone to use the terminal
while not ccvault.getPlayerName() do os.sleep(1) end

-- 3. Authenticate (player clicks [APPROVE] in chat)
if not ccvault.isAuthenticated() then
    ccvault.requestAuth()
    while not ccvault.isAuthenticated() do os.sleep(0.5) end
end

-- 4. Do stuff
local bal = ccvault.getBalance("player")
print("Your balance: " .. bal)

local result, err = ccvault.transfer("player", "host", 50, "shop purchase")
if result then
    print("Paid! TX: " .. result.txId)
else
    print("Failed: " .. err)
end
```

---

## How Authentication Works

1. Player **interacts** with the computer, monitor, or pocket computer
2. Your script calls **`ccvault.requestAuth()`**
3. The **server** sends a clickable chat message:

   > **[CCVault]** Computer #5 is requesting access to your wallet. **[APPROVE]**

4. Player clicks **[APPROVE]** → the mod authorizes that player on that computer
5. Auth lasts until the player disconnects or goes **10 minutes** without activity

⚠️ **This chat message cannot be faked by scripts.** It's sent directly by the server mod.

---

## API Reference

### Availability

| Function | Returns | Auth Required |
|----------|---------|:---:|
| `ccvault.isAvailable()` | `boolean` | No |

Returns `true` if ParadiseCore is loaded and its economy is active.

---

### Authentication

| Function | Returns | Auth Required |
|----------|---------|:---:|
| `ccvault.requestAuth()` | `true` \| `nil, error` | No |
| `ccvault.isAuthenticated()` | `boolean` | No |

**`requestAuth()`** sends the clickable approval message to the player's chat. If the current player is already authorized on this computer, it succeeds without sending a fresh prompt. Throws if no player is currently interacting with the computer yet.

**`isAuthenticated()`** returns whether the current interacting player has an active auth session for this terminal.

---

### Balance

| Function | Returns | Auth Required |
|----------|---------|:---:|
| `ccvault.getBalance(target)` | `number` \| `nil, error` | **Yes** |
| `ccvault.getPlayerBalance(playerName)` | `number` \| `nil, error` | No |

`target` must be `"player"` or `"host"`.

```lua
local bal, err = ccvault.getBalance("player")
if bal then
    print(bal .. " tokens")
end
```

`getPlayerBalance(playerName)` is read-only and resolves an online player name, a cached/offline profile name, or a direct UUID string without requiring terminal auth. It can inspect balances, but it cannot move tokens or modify any wallet state.

It never waits on the network. The first call for an offline player can return `nil, "looking up player 'Name' - try again"` while the name is resolved in the background, so call it again a moment later.

```lua
local bal, err = ccvault.getPlayerBalance("Akkiruk")
if bal then
    print("Akkiruk has " .. bal .. " tokens")
end
```

---

### Player Vault Data

| Function | Returns | Auth Required |
|----------|---------|:---:|
| `ccvault.getPlayerVaultLevel(player)` | `number` \| `nil, error` | No |
| `ccvault.getPlayerEquipment(player)` | `table` \| `nil, error` | No |
| `ccvault.getPlayerGearPower(player)` | `table` \| `nil, error` | No |
| `ccvault.getPlayerTemper(player)` | `table` \| `nil, error` | No |
| `ccvault.getPlayerReputation(player)` | `table` \| `nil, error` | No |
| `ccvault.getPlayerSkills(player)` | `table` \| `nil, error` | No |
| `ccvault.getPlayerStats(player)` | `table` \| `nil, error` | No |

`player` is a name or a UUID string, resolved like `getPlayerBalance`. All of these are read-only and need no approval, touch or range. The first call for a name the server has not seen can return `nil, "looking up player 'Name' - try again"`. A name nobody has is `nil, "unknown player"`.

A player counts as online only when they are on **this** server. Someone on another server of the network is read from what this server saved, like an offline player.

**Vault level.** `getPlayerVaultLevel` works online and offline. Since Vault Hunters 3.21.74 levels are not capped, so it can be above 100. Gear and crystals still stop at 100: past 100 the level is a progress number that unlocks Gear Power and Temper at 100 and nothing after.

**Gear Power** (Vault Hunters 3.21.74). For a player on this server at level 100 or above:

```lua
local power, err = ccvault.getPlayerGearPower("Akkiruk")
-- power.online         = true
-- power.shown          = true            (false below minLevel: then there are no scores)
-- power.level          = 104             (vault level, can be above 100)
-- power.minLevel       = 100             (the level Gear Power starts at)
-- power.greedTier      = 2
-- power.gearScore      = 765             (as the game shows it)
-- power.gearScoreExact = 765.083         (the mean of the six slot scores)
-- power.gearPower      = 1339            (round(gearScoreExact * temper))
-- power.temper         = 1.75            ((max(might, magic) + vigor) / 2)
-- power.might, power.magic, power.vigor  (the multipliers; 1 with no Temper)
-- power.slots          = { head = 900, chest = 850.5, legs = 0, feet = 1000, weapon = 1200, offhand = 640 }
-- power.slotDetails    = { weapon = { status = "COUNTED", score = 1200, item = "the_vault:sword",
--                                     name = "Blade", hotbarSlot = 3, hotbarWeapons = 2 }, ... }
-- power.peakGearPower  = 1400            (the highest Gear Power ever reached)
```

- `weapon` is the best weapon anywhere on the hotbar, not the held item. `slotDetails.weapon.hotbarSlot` (1 to 9) says which one counts.
- `status` is `COUNTED`, `EMPTY`, `NO_WEAPON`, `NOT_VAULT_GEAR`, `NOT_COMBAT_GEAR`, `INERT` (wrong slot, broken or above the player's level), `GREED_TIER_LOCKED` or `NO_TIER_CONFIG`.
- Below `minLevel` the table has `shown = false` and no scores.
- A player who is not on this server gets `{ online = false, shown = false, level =, peakGearPower = }`: Vault Hunters only saves the peak.
- Errors of its own: `"no vault data"`, `"gear power not available"` (the server's Vault Hunters is older than 3.21.74).

**Temper and Dust** (Vault Hunters 3.21.74):

```lua
local temper, err = ccvault.getPlayerTemper("Akkiruk")
-- temper.online   = true
-- temper.dust     = 12345
-- temper.might    = { level = 40, multiplier = 2.0, power = 2.5 }
-- temper.magic    = { level = 10, multiplier = 1.25, power = 1.3 }
-- temper.vigor    = { level = 25, multiplier = 1.6, power = 1.8 }
-- temper.tool     = { level = 3,  multiplier = 1.1, power = 1.05 }   (does not count for Gear Power)
-- temper.gearPowerMultiplier = 1.8      ((max(might, magic) + vigor) / 2, from the multipliers)
-- temper.peakGearPower = 1400
-- temper.trialsRecord  = 17
-- temper.bossesBeaten  = 2              (online only)
-- temper.favorsUsed    = 1              (online only)
```

- `level` is the levels bought (0 to 400), `multiplier` what the category multiplies Gear Power by, `power` what it multiplies its effect by inside a vault.
- It works for a player who is not on this server, from the saved profile, with `online = false`.
- Errors of its own: `"no vault data"`, `"temper not available"`.

**God reputation.** `getPlayerReputation` returns `{ velara =, tenos =, wendarr =, idona = }` for an online player. Errors of its own: `"player offline"`, `"reputation not available"`.

**Skills** (2.2.37). `getPlayerSkills` works online and offline (offline from the saved profile, `online = false`):

```lua
local skills = ccvault.getPlayerSkills("Akkiruk")
-- skills.abilities[1]  = { id = <ability id>, name = <shown name>, specialization = <chosen spec id>,
--                          specializationName = <its name>, specializationIndex = 2,   -- 0 is the plain ability
--                          level = 5, learned = 4, maxLevel = 5, selected = true }
-- skills.talents[1]    = { id =, name =, level =, learned =, maxLevel = }
-- skills.expertises, skills.prestige: the same shape
-- skills.spentPoints   = { abilities =, talents =, expertises =, prestige = }
```

`level` includes gear bonuses (only known online; offline it equals `learned`). Errors of its own: `"no vault data"`, `"skills not available"`.

**Stat totals** (2.2.37). `getPlayerStats` gives an online player's stat screen, keyed by attribute id:

```lua
local stats = ccvault.getPlayerStats("Akkiruk")
local cdr = stats["the_vault:cooldown_reduction"]
-- cdr = { name = "Cooldown Reduction", value = 0.8, display = "80%", limit = 0.8, uncapped = 0.87, hitLimit = true }
```

`limit`, `uncapped` and `hitLimit` are only on capped stats. Errors of its own: `"player offline"`, `"stats not available"`.

**The counted weapon** (2.2.37). `getPlayerEquipment` also has `hotbar = { [1..9] = detail }`, `selectedHotbarSlot`, and `weapon` with `weaponHotbarSlot`: the hotbar weapon Gear Power counts, which isn't always the one in hand.

The contract for these is `docs/SPEC_2_2_37.md` section 9.

---

### Server Limits

| Function | Returns | Auth Required |
|----------|---------|:---:|
| `ccvault.getLimits()` | `table` | No |

```lua
local limits = ccvault.getLimits()
-- limits.maxTransferAmount, limits.interactionStaleSeconds, limits.nonceExpirySeconds,
-- limits.authIdleTimeoutMinutes, limits.maxHistoryResults, limits.maxAllowanceMinutes,
-- limits.maxLinksPerComputer, limits.maxBatchMoves, limits.batchTimeBudgetMicros,
-- limits.maxWatchesPerComputer, limits.vhccMaxBytesPerComputer,
-- limits.gearScoreInDetails, limits.gearScoreLinesInDetails,
-- limits.containerItems, limits.identify,
-- limits.statCaps = { ["the_vault:cooldown_reduction"] = { base = 0.8, max = 0.95 }, ... }
```

These are the server config values. A value that cannot be read is absent, so give each a fallback.

---

### Transfer

| Function | Returns | Auth Required |
|----------|---------|:---:|
| `ccvault.transfer(from, to, amount, reason)` | `{success, txId}` \| `nil, error` | **Yes** |

The core of the API. Moves tokens between `"player"` and `"host"`.

| Parameter | Type | Rules |
|-----------|------|-------|
| `from` | `string` | `"player"` or `"host"` |
| `to` | `string` | `"player"` or `"host"` (must differ from `from`) |
| `amount` | `number` | Positive integer, max 1,000,000 (configurable) |
| `reason` | `string` | 1–64 characters, logged permanently |

```lua
-- Player pays the host (shop purchase)
local result, err = ccvault.transfer("player", "host", 50, "diamond pickaxe")

-- Host pays the player (casino payout)
local result, err = ccvault.transfer("host", "player", 200, "blackjack win")
```

On success:
```lua
result.success  -- true
result.txId     -- "18f3a2b0-4c7e..."  (unique transaction ID)
```

---

### Info

| Function | Returns | Auth Required |
|----------|---------|:---:|
| `ccvault.getPlayerName()` | `string` \| `nil` | No |
| `ccvault.getHostName()` | `string` \| `nil, error` | No |
| `ccvault.getComputerId()` | `number` | No |

- **`getPlayerName()`** — username of whoever last interacted. `nil` if nobody has.
- **`getHostName()`** — the computer owner's username. Returns UUID string if owner is offline. `nil` if there is no owner.
- **`getComputerId()`** — this computer's CC ID. Show this to players so they can run `/ccvault revoke <id>` if needed.

### Host Ownership

| Function | Returns | Auth Required |
|----------|---------|:---:|
| `ccvault.claimHost()` | `{success, hostName, computerId}` \| `nil, message` | No |
| `ccvault.forgetHost()` | `boolean` | No |

Whoever places a computer becomes its host, and ownership is permanent. A labelled computer keeps its host wherever it is placed next. Pocket computers have no host until they are claimed.

A placed computer with no host, such as one placed by a command or after `forgetHost()`, offers itself in chat with a `[CLAIM]` button every time someone right-clicks it. The first player to click becomes the host. An offer only ever claims a computer with no host: once one is set, older offers silently do nothing and the offers stop.

**`claimHost()`** asks the interacting player to become the host. It sends them a `[CONFIRM]` chat message and returns `nil, "confirm the host claim in chat"`: the claim happens when they click, not when the call returns. It can take a computer from its current host, and that host is not asked. Watch `getSessionInfo().isSelfPlay` to see the claim land. If the player is already the host it returns a table straight away.

```lua
local result, message = ccvault.claimHost()
while not result and not ccvault.getSessionInfo().isSelfPlay do
    print(message)
    sleep(1)
    message = "Waiting for you to confirm in chat..."
end
```

**`forgetHost()`** clears the host and lifts any terminal lock, and returns whether there was a host. Any script on the computer can call it. The next player to place the computer or confirm a claim becomes the host.

---

## Player Commands

| Command | What it does |
|---------|-------------|
| `/ccvault approve <code>` | Approves a pending auth request (just click the chat button) |
| `/ccvault revoke <computerId>` | Revokes your session with a specific terminal |

Players never need to type the approve command — they click **[APPROVE]** in chat.

---

### Session Info

| Function | Returns | Auth Required |
|----------|---------|:---:|
| `ccvault.getSessionInfo()` | `table` | No |

Returns a table with information about the current session:

```lua
local info = ccvault.getSessionInfo()
-- info.computerId            number   this computer's ID
-- info.playerName            string?  interacting player's name (nil if nobody)
-- info.hostName              string?  computer owner's name (nil if unregistered)
-- info.isSelfPlay            boolean  true if player == host (test mode)
-- info.authenticated         boolean  whether current session is authenticated
```

Useful for:
- Detecting test/self-play mode server-authoritatively
- Display UI without needing auth first

---

### Self-Transfer (Test Mode)

| Function | Returns | Auth Required |
|----------|---------|:---:|
| `ccvault.transferSelf(amount, reason)` | `{success, txId, testMode}` \| `nil, error` | **Yes** |

Simulates a transfer where player and host are the same account. It checks the balance and writes a ledger entry tagged `[self-play]`, but no tokens move.

**Only works when player == host.** Returns an error otherwise.

```lua
-- Owner testing their own game
local result, err = ccvault.transferSelf(50, "blackjack bet")
if result then
    print("TX: " .. result.txId)
    print("Test mode: " .. tostring(result.testMode))  -- always true
end
```

---

### Transaction Verification

| Function | Returns | Auth Required |
|----------|---------|:---:|
| `ccvault.verifyTransaction(txId)` | `table` \| `nil` | **Yes** |

Looks up a specific transaction by ID in the permanent ledger. Only returns transactions where the current player was involved (as player or host). Returns `nil` if not found.

```lua
-- After a charge, verify it went through
local ok, txId = charge(100, "bet")
-- ... later, or after crash recovery:
local tx = ccvault.verifyTransaction(txId)
if tx then
    print("Confirmed: " .. tx.amount .. " tokens at " .. tx.timestamp)
else
    print("Transaction not found — safe to retry")
end
```

---

### Transaction History

| Function | Returns | Auth Required |
|----------|---------|:---:|
| `ccvault.getTransactionHistory(limit)` | `table` (array of entries) | **Yes** |

Returns the most recent transactions involving the current player, capped by server config (default 50 max).

```lua
local txs = ccvault.getTransactionHistory(10)
for i, tx in ipairs(txs) do
    print(tx.txId, tx.amount, tx.reason, tx.timestamp)
end
```

Each entry contains: `txId`, `from`, `to`, `amount`, `reason`, `computerId`, `playerUuid`, `hostUuid`, `timestamp`.

---

### Transfer-at-End Pattern

CCVault now uses transfer-at-end for game economies.

- Keep bet state local while the round is in progress.
- On round end, perform exactly one settlement transfer:
    - player wins -> `ccvault.transfer("host", "player", amount, reason)`
    - player loses -> `ccvault.transfer("player", "host", amount, reason)`
    - push -> no transfer

#### Round Settlement Example (Blackjack)

```lua
local bet = 100
local result = "player_win" -- player_win | player_loss | push

if result == "player_win" then
        local tx, err = ccvault.transfer("host", "player", bet, "blackjack payout")
        if not tx then error(err) end
elseif result == "player_loss" then
        local tx, err = ccvault.transfer("player", "host", bet, "blackjack loss")
        if not tx then error(err) end
end
```

---

## Error Messages

| Error | Meaning |
|-------|---------|
| `no player interacting with this computer` | Nobody has interacted yet |
| `not authenticated — call ccvault.requestAuth() first` | Player hasn't approved this terminal |
| `computer has no registered owner - right-click it and claim it in chat, or call ccvault.claimHost()` | Nobody owns this computer yet |
| `insufficient balance` | Source doesn't have enough tokens |
| `amount must be positive` | You passed 0 or negative |
| `cannot transfer to self` | `from` and `to` are the same |
| `transferSelf is only for same-person sessions` | Player must be host to use transferSelf |
| `reason is required` | Must provide a reason string |
| `reason too long (max 64 chars)` | Shorten the reason |
| `amount exceeds maximum (1000000)` | Over the per-transfer cap |
| `economy system not available` | ParadiseCore not installed, or its database is down |
| `this computer was remotely controlled - reboot it before moving tokens` | An admin drove this computer through `/vhcc pc`; a reboot clears it |

---

## Error Handling Pattern

All functions that can fail return `nil, errorString`. Some throw on critical issues (no player present). Use `pcall` for bulletproof code:

```lua
local ok, result, err = pcall(ccvault.transfer, "player", "host", 100, "purchase")
if not ok then
    print("Exception: " .. tostring(result))
elseif result then
    print("TX: " .. result.txId)
else
    print("Error: " .. err)
end
```

---

## Server Defaults

| Setting | Default | Description |
|---------|---------|-------------|
| Auth nonce expiry | 60 seconds | How long the [APPROVE] button stays valid |
| Max transfer amount | 1,000,000 | Per-transaction cap |
| Interaction staleness | 30 seconds | A recent interaction or pocket use must still be fresh for financial ops |
| Max history results | 50 | Max entries returned by getTransactionHistory |

Server admins can change all of these in the vhcctweaks server config.

---

## Security Model

**Your script CANNOT:**

- ❌ Send tokens to/from arbitrary UUIDs or player names
- ❌ Create tokens from nothing
- ❌ Destroy tokens
- ❌ Forge the authentication prompt
- ❌ Become host for a player without their chat confirmation
- ❌ Bypass the transfer cap
- ❌ Operate on a player who hasn't recently interacted

**The server guarantees:**

- ✅ Every debit has an equal credit: the economy moves both balances in one database transaction
- ✅ All transfers are permanently logged with TX ID, amounts, parties, reason, and timestamp
- ✅ Auth prompts are unforgeable server chat messages with clickable approve buttons
- ✅ Transaction history is filtered to only show the requesting player's own data
- ✅ Self-transfers are explicitly tagged as test mode in the ledger

---

## Full Example: Shop Terminal

```lua
local ITEMS = {
    { name = "Diamond Pickaxe", price = 50  },
    { name = "Golden Apple",    price = 25  },
    { name = "Netherite Ingot", price = 200 },
}

local function waitForAuth()
    while not ccvault.getPlayerName() do
        term.clear(); term.setCursorPos(1,1)
        print("Right-click to begin.")
        os.sleep(1)
    end
    if not ccvault.isAuthenticated() then
        ccvault.requestAuth()
        term.clear(); term.setCursorPos(1,1)
        print("Click [APPROVE] in your chat.")
        local t = os.startTimer(60)
        while not ccvault.isAuthenticated() do
            local ev, id = os.pullEvent()
            if ev == "timer" and id == t then return false end
            os.sleep(0.5)
        end
    end
    return true
end

while true do
    if not ccvault.isAvailable() then
        print("Economy offline."); os.sleep(5)
    elseif waitForAuth() then
        term.clear(); term.setCursorPos(1,1)
        print("=== TOKEN SHOP ===\n")
        for i, item in ipairs(ITEMS) do
            print(i .. ". " .. item.name .. " — " .. item.price .. " tokens")
        end
        print("\nBalance: " .. (ccvault.getBalance("player") or "?"))
        print("\nType number to buy, q to quit.")

        local input = read()
        if input == "q" then break end

        local choice = tonumber(input)
        if choice and ITEMS[choice] then
            local item = ITEMS[choice]
            local res, err = ccvault.transfer("player", "host", item.price, item.name)
            if res then
                print("Purchased " .. item.name .. "!")
            else
                print("Failed: " .. (err or "unknown"))
            end
            os.sleep(2)
        end
    end
end
```
