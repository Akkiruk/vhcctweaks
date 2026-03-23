-- ============================================================================
--  CCVault API Reference — ComputerCraft Developer Guide
-- ============================================================================
--
--  CCVault is a server-authoritative economy API for CC:Tweaked computers.
--  It lets your scripts move Vault Tokens between the player using your
--  terminal and the player who placed/owns the computer — nothing else.
--
--  KEY RULES:
--    * You CANNOT send tokens to arbitrary players or UUIDs.
--    * You CANNOT create or destroy tokens. Every debit has an equal credit.
--    * The only two identities you can reference are "player" and "host".
--    * All money operations require the player to authenticate first.
--    * Auth is per-session — it resets every time the player disconnects.
--
--  "player" = the person currently right-clicking / using the computer
--  "host"   = the person who placed the computer block in the world
--
-- ============================================================================


-- ============================================================================
--  1. CHECKING AVAILABILITY
-- ============================================================================

-- Before doing anything, check that the economy backend is loaded.
-- Returns false if Dog's PlayerShops isn't installed on the server.

if not ccvault.isAvailable() then
    print("Economy system is not available on this server.")
    return
end


-- ============================================================================
--  2. AUTHENTICATION FLOW
-- ============================================================================

-- Every session (each time a player logs in), they must approve your terminal
-- before you can touch their wallet. This is NON-OPTIONAL.
--
-- When you call requestAuth(), the SERVER sends the player a clickable chat
-- message that looks like:
--
--   [CCVault] Computer #5 is requesting access to your wallet. [APPROVE]
--
-- This message is impossible for your script to fake. The player clicks
-- [APPROVE] and your terminal is authorized for the rest of their session.
--
-- IMPORTANT: The player must have right-clicked your computer/monitor FIRST.
-- If nobody has interacted with the computer, requestAuth() will error.

-- Step 1: Wait for a player to interact
while not ccvault.getPlayerName() do
    os.sleep(1)
end

-- Step 2: Check if already authenticated (they may have approved earlier)
if not ccvault.isAuthenticated() then
    -- Step 3: Send the auth request
    local ok, err = ccvault.requestAuth()
    if not ok then
        print("Auth request failed: " .. (err or "unknown"))
        return
    end

    print("Waiting for player to approve in chat...")

    -- Step 4: Poll until they click [APPROVE] (or give up after a timeout)
    local timeout = os.startTimer(60)
    while not ccvault.isAuthenticated() do
        local event, id = os.pullEvent()
        if event == "timer" and id == timeout then
            print("Auth timed out.")
            return
        end
        os.sleep(0.5)
    end
end

print("Authenticated! Welcome, " .. ccvault.getPlayerName())


-- ============================================================================
--  3. CHECKING BALANCES
-- ============================================================================

-- getBalance(target) -> number | nil, errorString
-- target must be "player" or "host". Requires authentication.

local playerBal, err = ccvault.getBalance("player")
if playerBal then
    print("Your balance: " .. playerBal .. " tokens")
else
    print("Could not read balance: " .. (err or "unknown"))
end

local hostBal, err2 = ccvault.getBalance("host")
if hostBal then
    print("Shop owner balance: " .. hostBal .. " tokens")
else
    print("Could not read host balance: " .. (err2 or "unknown"))
end


-- ============================================================================
--  4. MAKING TRANSFERS
-- ============================================================================

-- transfer(from, to, amount, reason) -> resultTable | nil, errorString
--
-- from:    "player" or "host"
-- to:      "player" or "host"  (must be different from 'from')
-- amount:  positive integer (whole tokens only)
-- reason:  string, max 64 characters (logged permanently in the ledger)
--
-- On success, returns a table:  { success = true, txId = "18f3a2b..." }
-- On failure, returns nil + error string.

-- Example: Player pays the host (shop purchase)
local result, err = ccvault.transfer("player", "host", 50, "bought diamond pickaxe")
if result then
    print("Payment successful! TX: " .. result.txId)
else
    print("Payment failed: " .. (err or "unknown"))
end

-- Example: Host pays the player (casino payout)
local result2, err2 = ccvault.transfer("host", "player", 200, "blackjack payout")
if result2 then
    print("Payout sent! TX: " .. result2.txId)
else
    print("Payout failed: " .. (err2 or "unknown"))
end


-- ============================================================================
--  5. INFO FUNCTIONS (no auth required)
-- ============================================================================

-- getPlayerName() -> string | nil
-- Returns the username of whoever last right-clicked this computer.
-- Returns nil if nobody has interacted yet.
local name = ccvault.getPlayerName()

-- getHostName() -> string | nil, errorString
-- Returns the username of whoever placed this computer.
-- Returns the UUID string if the owner is offline.
-- Returns nil if the computer has no registered owner.
local host, hostErr = ccvault.getHostName()

-- getComputerId() -> number
-- Returns this computer's CC ID. Useful for telling the player which
-- terminal to revoke if they want to (/ccvault revoke <id>).
local id = ccvault.getComputerId()


-- ============================================================================
--  6. SESSION INFO (no auth required)
-- ============================================================================

-- getSessionInfo() -> table
-- Returns rich metadata about the current session.
-- Very useful for detecting self-play, checking rate limits, etc.

local info = ccvault.getSessionInfo()

print("Computer: " .. info.computerId)
print("Player: " .. (info.playerName or "none"))
print("Host: " .. (info.hostName or "unknown"))
print("Self-play: " .. tostring(info.isSelfPlay))
print("Authenticated: " .. tostring(info.authenticated))
print("Transfers remaining: " .. info.transfersRemaining)

-- The isSelfPlay flag is determined server-side by comparing UUIDs.
-- Use it to show "TEST MODE" banners or adapt game behavior:
if info.isSelfPlay then
    print("TEST MODE — you are the house owner")
end


-- ============================================================================
--  7. SELF-TRANSFER / TEST MODE (auth required)
-- ============================================================================

-- transferSelf(amount, reason) -> {success, txId, testMode} | nil, error
--
-- When player == host (you're playing your own game), normal transfer()
-- blocks self-transfers. Use transferSelf() instead.
--
-- The transfer goes through the full pipeline (WAL, ledger, rate limiter)
-- so you can test everything, but the net balance effect is zero.
-- All self-transfers are tagged with "[test]" in the ledger.

local info2 = ccvault.getSessionInfo()
if info2.isSelfPlay then
    local result, err = ccvault.transferSelf(50, "blackjack bet")
    if result then
        print("Test transfer OK, TX: " .. result.txId)
        print("Test mode: " .. tostring(result.testMode))  -- always true
    else
        print("Self-transfer failed: " .. (err or "unknown"))
    end
end

-- NOTE: transferSelf() will error if player != host.
-- Use ccvault.getSessionInfo().isSelfPlay to check before calling.


-- ============================================================================
--  8. TRANSACTION VERIFICATION (auth required)
-- ============================================================================

-- verifyTransaction(txId) -> table | nil
--
-- Looks up a transaction by ID in the permanent server-side ledger.
-- Only returns transactions where you were the player or host.
-- Returns nil if not found (not an error — just means the TX doesn't exist).
--
-- USE CASE: After a crash, check if a charge/payout actually completed
-- before retrying. Prevents double-charges and double-refunds.

local tx = ccvault.verifyTransaction("18f3a2b0-4c7e...")
if tx then
    print("Found! Amount: " .. tx.amount .. " at " .. tx.timestamp)
else
    print("Not found — safe to retry")
end


-- ============================================================================
--  9. TRANSACTION HISTORY (auth required)
-- ============================================================================

-- getTransactionHistory(limit) -> table (array of entries)
--
-- Returns the most recent transactions involving you.
-- Each entry has: txId, from, to, amount, reason, computerId,
-- playerUuid, hostUuid, timestamp.
-- Limit is capped by server config (default max: 50).

local txs = ccvault.getTransactionHistory(10)
for i, tx in ipairs(txs) do
    print(string.format("#%d: %s  %d tokens  %s", i, tx.txId, tx.amount, tx.reason))
end


-- ============================================================================
--  10. ESCROW SYSTEM (auth required)
-- ============================================================================

-- The escrow system solves the "payout fails after charge" problem.
-- Instead of immediately giving tokens to the host on a bet:
--   1. Hold tokens in server-side escrow
--   2. Play the game
--   3. Resolve the escrow to the winner
-- If anything goes wrong (crash, timeout), tokens auto-refund to the player.

-- escrow(amount, reason)
--   -> {success, escrowId, amount, timeoutSeconds} | nil, error
--   Deducts from player, holds in escrow. Returns escrowId.

-- resolveEscrow(escrowId, recipient, reason)
--   -> {success, txId} | nil, error
--   Sends held tokens to "player" or "host".

-- cancelEscrow(escrowId, reason)
--   -> {success, txId} | nil, error
--   Returns held tokens to the original source (player).

-- getEscrowInfo(escrowId)
--   -> {escrowId, amount, reason, timeRemaining, status} | nil
--   Check status of an active escrow.

-- EXAMPLE: Casino game with escrow
local hold, err = ccvault.escrow(100, "blackjack bet")
if not hold then
    print("Could not place bet: " .. (err or "unknown"))
    return
end

local escrowId = hold.escrowId
print("Bet placed! Escrow: " .. escrowId)
print("Auto-refund in " .. hold.timeoutSeconds .. " seconds if not resolved")

-- ... play the game ...

-- Check escrow status mid-game:
local escrowInfo = ccvault.getEscrowInfo(escrowId)
if escrowInfo then
    print("Time remaining: " .. escrowInfo.timeRemaining .. "s")
end

-- Resolve based on outcome:
local playerWon = true -- (determined by game logic)

if playerWon then
    -- Return the bet to the player
    ccvault.resolveEscrow(escrowId, "player", "player won - bet returned")
    -- Pay the winnings separately from host
    ccvault.transfer("host", "player", 100, "blackjack winnings")
else
    -- Host claims the lost bet
    ccvault.resolveEscrow(escrowId, "host", "player lost - house wins")
end


-- ============================================================================
--  11. ERROR HANDLING
-- ============================================================================

-- All ccvault functions that can fail return:  nil, "error message"
-- Some functions throw instead (requestAuth when no player is present).
-- Wrap everything in pcall() if you want to be safe:

local ok, result, err = pcall(ccvault.transfer, "player", "host", 100, "test")
if not ok then
    -- 'result' contains the error string thrown by the function
    print("Exception: " .. tostring(result))
elseif result then
    print("Success: " .. result.txId)
else
    print("Failed: " .. (err or "unknown"))
end


-- ============================================================================
--  12. COMMON ERROR MESSAGES
-- ============================================================================

--  "no player interacting with this computer"
--      Nobody has right-clicked this computer yet. Can't do anything.
--
--  "not authenticated — call ccvault.requestAuth() first"
--      The player hasn't approved this terminal yet. Send requestAuth().
--
--  "computer has no registered owner — place it to register"
--      The computer was placed before vhcctweaks was installed, or
--      something went wrong. Break and re-place the computer.
--
--  "insufficient balance"
--      Source account doesn't have enough tokens.
--
--  "amount must be positive"
--      You passed 0 or a negative number.
--
--  "cannot transfer to self"
--      from and to are the same ("player" to "player").
--
--  "reason is required"
--      You must provide a reason string for the audit log.
--
--  "reason too long (max 64 chars)"
--      Keep it short.
--
--  "amount exceeds maximum (1000000)"
--      Server config caps single transfers. Default is 1M tokens.
--
--  "terminal rate limit exceeded"
--      Too many transfers from this computer. Default: 10/min.
--
--  "player rate limit exceeded"
--      This player is making too many transfers total. Default: 20/min.
--
--  "economy system not available"
--      Dog's PlayerShops isn't installed or didn't load.
--
--  "could not read balance"
--      Dog's API returned an error. Might be a server issue.
--
--  "debit failed"
--      Dog's removeTokens() call failed. No money was moved.
--
--  "transfer partially failed — will be recovered automatically"
--      Debit succeeded but credit failed. The server's Write-Ahead Log
--      will automatically complete the credit on next restart. Don't panic.
--
--  "invalid target 'xxx' — use 'player' or 'host'"
--      You passed something other than "player" or "host".


-- ============================================================================
--  13. FULL FUNCTION REFERENCE (QUICK)
-- ============================================================================

-- Core:
--  ccvault.isAvailable()                           -> boolean
--  ccvault.requestAuth()                           -> true | nil, error
--  ccvault.isAuthenticated()                       -> boolean
--  ccvault.getBalance("player"|"host")             -> number | nil, error
--  ccvault.transfer(from, to, amount, reason)      -> {success,txId} | nil, error
--  ccvault.getPlayerName()                         -> string | nil
--  ccvault.getHostName()                           -> string | nil, error
--  ccvault.getComputerId()                         -> number
--
-- New in v2.1:
--  ccvault.getSessionInfo()                        -> table
--  ccvault.transferSelf(amount, reason)             -> {success,txId,testMode} | nil, error
--  ccvault.verifyTransaction(txId)                 -> table | nil
--  ccvault.getTransactionHistory(limit)            -> table (array)
--  ccvault.escrow(amount, reason)                  -> {success,escrowId,amount,timeoutSeconds} | nil, error
--  ccvault.resolveEscrow(escrowId, target, reason) -> {success,txId} | nil, error
--  ccvault.cancelEscrow(escrowId, reason)          -> {success,txId} | nil, error
--  ccvault.getEscrowInfo(escrowId)                 -> table | nil


-- ============================================================================
--  14. PLAYER COMMANDS
-- ============================================================================

-- Players have two commands they can run in chat:
--
--   /ccvault approve <code>
--       Approves a pending auth request. Players don't need to type this —
--       they just click the [APPROVE] button in the chat message.
--
--   /ccvault revoke <computerId>
--       Revokes the player's session with a specific terminal. Use
--       ccvault.getComputerId() to show the player which ID to revoke.


-- ============================================================================
--  15. SECURITY MODEL (what your script CANNOT do)
-- ============================================================================

-- * You cannot send tokens to/from arbitrary UUIDs or player names.
-- * You cannot create tokens from nothing (no "add" or "mint").
-- * You cannot destroy tokens (no "remove" or "burn").
-- * You cannot read balances without the player authenticating.
-- * You cannot forge the authentication prompt (it's a server chat message).
-- * You cannot bypass rate limits.
-- * You cannot exceed the per-transfer cap set by the server.
-- * You cannot operate on a player who hasn't recently right-clicked.
-- * Every transfer is permanently logged in a server-side ledger.


-- ============================================================================
--  16. EXAMPLE: SIMPLE SHOP TERMINAL
-- ============================================================================

local ITEMS = {
    { name = "Diamond Pickaxe", price = 50 },
    { name = "Golden Apple",    price = 25 },
    { name = "Netherite Ingot", price = 200 },
}

local function drawMenu()
    term.clear()
    term.setCursorPos(1, 1)
    print("=== TOKEN SHOP ===")
    print()
    for i, item in ipairs(ITEMS) do
        print(i .. ". " .. item.name .. " - " .. item.price .. " tokens")
    end
    print()
    print("Type a number to buy, or 'q' to quit.")
end

local function waitForAuth()
    while not ccvault.getPlayerName() do
        term.clear()
        term.setCursorPos(1, 1)
        print("Right-click this computer to begin.")
        os.sleep(1)
    end

    if not ccvault.isAuthenticated() then
        ccvault.requestAuth()
        term.clear()
        term.setCursorPos(1, 1)
        print("Check your chat and click [APPROVE].")

        local t = os.startTimer(60)
        while not ccvault.isAuthenticated() do
            local ev, id = os.pullEvent()
            if ev == "timer" and id == t then
                print("Timed out. Right-click again.")
                return false
            end
            os.sleep(0.5)
        end
    end
    return true
end

-- Main loop
while true do
    if not ccvault.isAvailable() then
        print("Economy offline.")
        os.sleep(5)
    elseif waitForAuth() then
        drawMenu()
        local input = read()
        if input == "q" then break end

        local choice = tonumber(input)
        if choice and ITEMS[choice] then
            local item = ITEMS[choice]
            local res, err = ccvault.transfer("player", "host", item.price, item.name)
            if res then
                print("Purchased " .. item.name .. "!")
                print("TX: " .. res.txId)
                -- TODO: actually give the item (e.g. drop from a turtle/dropper)
            else
                print("Failed: " .. (err or "unknown"))
            end
            os.sleep(2)
        end
    end
end
