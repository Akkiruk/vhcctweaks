# CCVault API — Developer Documentation

> **Server-authoritative economy API for CC:Tweaked computers on Vault Hunters**
> Part of the **vhcctweaks** mod

---

## Overview

CCVault lets ComputerCraft scripts move **Vault Tokens** between two parties:

- **`"player"`** — the person currently using the terminal (right-clicked it)
- **`"host"`** — the person who placed the computer block

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

1. Player **right-clicks** the computer or monitor
2. Your script calls **`ccvault.requestAuth()`**
3. The **server** sends a clickable chat message:

   > **[CCVault]** Computer #5 is requesting access to your wallet. **[APPROVE]**

4. Player clicks **[APPROVE]** → terminal is authorized for this session
5. Auth **clears when the player disconnects** — they must re-approve next login

⚠️ **This chat message cannot be faked by scripts.** It's sent directly by the server mod.

---

## API Reference

### Availability

| Function | Returns | Auth Required |
|----------|---------|:---:|
| `ccvault.isAvailable()` | `boolean` | No |

Returns `true` if Dog's PlayerShops is loaded and the economy system is active.

---

### Authentication

| Function | Returns | Auth Required |
|----------|---------|:---:|
| `ccvault.requestAuth()` | `true` \| `nil, error` | No |
| `ccvault.isAuthenticated()` | `boolean` | No |

**`requestAuth()`** sends the clickable approval message to the player's chat. Throws if no player has right-clicked the computer yet.

**`isAuthenticated()`** returns whether the current interacting player has an active session with this terminal.

---

### Balance

| Function | Returns | Auth Required |
|----------|---------|:---:|
| `ccvault.getBalance(target)` | `number` \| `nil, error` | **Yes** |

`target` must be `"player"` or `"host"`.

```lua
local bal, err = ccvault.getBalance("player")
if bal then
    print(bal .. " tokens")
end
```

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

- **`getPlayerName()`** — username of whoever last right-clicked. `nil` if nobody has.
- **`getHostName()`** — username of whoever placed the computer. Returns UUID string if owner is offline. `nil` if unregistered.
- **`getComputerId()`** — this computer's CC ID. Show this to players so they can run `/ccvault revoke <id>` if needed.

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
-- info.transfersRemaining    number   min of terminal + player budget
-- info.terminalTransfersRemaining  number  terminal rate limit remaining
-- info.playerTransfersRemaining    number  player rate limit remaining
```

Useful for:
- Detecting test/self-play mode server-authoritatively
- Showing rate limit warnings before they hit
- Display UI without needing auth first

---

### Self-Transfer (Test Mode)

| Function | Returns | Auth Required |
|----------|---------|:---:|
| `ccvault.transferSelf(amount, reason)` | `{success, txId, testMode}` \| `nil, error` | **Yes** |

Executes a real transfer where player and host are the same account. Goes through the full pipeline (WAL, ledger, rate limiter) with net-zero balance effect. Tagged as `[test]` in the ledger.

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

### Escrow

The escrow system holds tokens server-side between deduction and resolution. If the game crashes, tokens auto-refund to the player after a configurable timeout.

| Function | Returns | Auth Required |
|----------|---------|:---:|
| `ccvault.escrow(amount, reason)` | `{success, escrowId, amount, timeoutSeconds}` \| `nil, error` | **Yes** |
| `ccvault.resolveEscrow(escrowId, recipient, reason)` | `{success, txId}` \| `nil, error` | **Yes** |
| `ccvault.cancelEscrow(escrowId, reason)` | `{success, txId}` \| `nil, error` | **Yes** |
| `ccvault.getEscrowInfo(escrowId)` | `{escrowId, amount, reason, timeRemaining, status}` \| `nil` | **Yes** |

**`escrow(amount, reason)`** — Deducts tokens from the player and holds them in server-side escrow. Returns an `escrowId` for later resolution.

**`resolveEscrow(escrowId, recipient, reason)`** — Sends held tokens to `"player"` (refund/push) or `"host"` (player lost). This is the normal resolution path.

**`cancelEscrow(escrowId, reason)`** — Cancels the escrow and returns tokens to the original source (player).

**`getEscrowInfo(escrowId)`** — Check the status and remaining time on an active escrow.

#### Escrow Flow Example (Blackjack)

```lua
-- 1. Player places bet → tokens held in escrow
local hold, err = ccvault.escrow(100, "blackjack bet")
if not hold then error("Escrow failed: " .. err) end
local escrowId = hold.escrowId

-- 2. Game plays out...

-- 3a. Player LOSES → host claims the escrow
ccvault.resolveEscrow(escrowId, "host", "player busted")

-- 3b. Player WINS → return bet to player, then pay winnings from host
ccvault.resolveEscrow(escrowId, "player", "player won - returning bet")
ccvault.transfer("host", "player", winnings, "blackjack winnings")

-- 3c. PUSH → return bet to player
ccvault.resolveEscrow(escrowId, "player", "push - returning bet")

-- 3d. If nothing happens (crash), tokens auto-refund after timeout
```

#### Auto-Refund

If an escrow is not resolved within the configured timeout (default: 5 minutes), the server automatically refunds the tokens to the player. This protects against:

- Game script crashes mid-round
- Server restarts during a game
- Scripts that forget to resolve escrows

---

## Error Messages

| Error | Meaning |
|-------|---------|
| `no player interacting with this computer` | Nobody has right-clicked yet |
| `not authenticated — call ccvault.requestAuth() first` | Player hasn't approved this terminal |
| `computer has no registered owner — place it to register` | Break and re-place the computer |
| `insufficient balance` | Source doesn't have enough tokens |
| `amount must be positive` | You passed 0 or negative |
| `cannot transfer to self` | `from` and `to` are the same |
| `transferSelf is only for same-person sessions` | Player must be host to use transferSelf |
| `reason is required` | Must provide a reason string |
| `reason too long (max 64 chars)` | Shorten the reason |
| `amount exceeds maximum (1000000)` | Over the per-transfer cap |
| `terminal rate limit exceeded` | Too many transfers from this computer (default: 10/min) |
| `player rate limit exceeded` | Player making too many transfers (default: 20/min) |
| `economy system not available` | PlayerShops not installed |
| `transfer partially failed — will be recovered automatically` | Server crashed mid-transfer; WAL will fix it on restart |
| `escrow not found or already resolved` | Invalid or already-used escrow ID |
| `escrow belongs to a different computer` | Security: escrows are computer-bound |
| `escrow expired — tokens auto-refunded to source` | Timeout passed |
| `too many active escrows on this computer` | Per-computer escrow cap hit (default: 5) |

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
| Terminal rate limit | 10/min | Max transfers per computer per minute |
| Player rate limit | 20/min | Max transfers per player per minute |
| Interaction staleness | 30 seconds | Right-click must be this recent for financial ops |
| Escrow timeout | 300 seconds | How long before an escrow auto-refunds |
| Max escrows per computer | 5 | Active escrow limit per terminal |
| Max history results | 50 | Max entries returned by getTransactionHistory |

Server admins can change all of these in the vhcctweaks server config.

---

## Security Model

**Your script CANNOT:**

- ❌ Send tokens to/from arbitrary UUIDs or player names
- ❌ Create tokens from nothing
- ❌ Destroy tokens
- ❌ Read balances without player authentication
- ❌ Forge the authentication prompt
- ❌ Bypass rate limits or transfer caps
- ❌ Operate on a player who hasn't recently right-clicked

**The server guarantees:**

- ✅ Every debit has an equal credit (double-entry ledger)
- ✅ All transfers are permanently logged with TX ID, amounts, parties, reason, and timestamp
- ✅ Crash-safe execution via Write-Ahead Log (incomplete transfers auto-recover on restart)
- ✅ Auth prompts are unforgeable server chat messages with clickable approve buttons
- ✅ Escrow holds auto-refund to the player on crash or timeout
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
