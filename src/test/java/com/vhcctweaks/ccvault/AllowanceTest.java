package com.vhcctweaks.ccvault;

import com.vhcctweaks.ccvault.SessionAuthManager.AllowanceAsk;
import com.vhcctweaks.ccvault.SessionAuthManager.Approval;
import com.vhcctweaks.ccvault.SessionAuthManager.Request;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllowanceTest {

    private static final int COMPUTER = 9;

    private final UUID steve = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();
    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void init() {
        SessionAuthManager.reset();
        AllowanceManager.setSink((computerId, event, args) ->
                events.add(computerId + " " + event + " " + args[0] + " " + args[1] + " " + args[2]));
    }

    @AfterEach
    void restoreSink() {
        AllowanceManager.setSink((computerId, event, args) -> {});
    }

    private String ask(UUID player, String name, int requestId, long max, int minutes, long expiresAt) {
        return SessionAuthManager.addPending(player, COMPUTER, expiresAt, Request.ALLOWANCE,
                new AllowanceAsk(requestId, name, max, minutes));
    }

    @Test
    void approvingGrantsTheAllowanceAndTellsTheComputer() {
        long now = System.currentTimeMillis();
        String nonce = ask(steve, "Steve", 4, 500, 60, now + 60_000);

        assertEquals(Approval.ALLOWANCE, SessionAuthManager.approveNonce(steve, nonce));
        assertEquals(List.of(COMPUTER + " ccvault_allowance 4 Steve true"), events);

        long[] allowance = AllowanceManager.get(COMPUTER, steve, System.currentTimeMillis());
        assertEquals(500, allowance[0]);
        assertTrue(allowance[1] > 3590 && allowance[1] <= 3600, "seconds left " + allowance[1]);
    }

    @Test
    void someoneElseCannotApproveIt() {
        String nonce = ask(steve, "Steve", 1, 500, 60, System.currentTimeMillis() + 60_000);
        assertEquals(Approval.INVALID, SessionAuthManager.approveNonce(alex, nonce));
        assertNull(AllowanceManager.get(COMPUTER, alex, System.currentTimeMillis()));
        assertTrue(events.isEmpty());
    }

    @Test
    void anExpiredRequestTellsTheComputerNo() {
        long now = System.currentTimeMillis();
        ask(steve, "Steve", 2, 500, 60, now - 1);
        SessionAuthManager.cleanupPendingNonces(now);
        assertEquals(List.of(COMPUTER + " ccvault_allowance 2 Steve false"), events);

        // Only once
        SessionAuthManager.cleanupPendingNonces(now);
        assertEquals(1, events.size());
    }

    @Test
    void clickingAnExpiredRequestTellsTheComputerNo() {
        String nonce = ask(steve, "Steve", 3, 500, 60, System.currentTimeMillis() - 1);
        assertEquals(Approval.INVALID, SessionAuthManager.approveNonce(steve, nonce));
        assertEquals(List.of(COMPUTER + " ccvault_allowance 3 Steve false"), events);
        assertNull(AllowanceManager.get(COMPUTER, steve, System.currentTimeMillis()));
    }

    @Test
    void leavingAnswersAPendingRequestAndEndsAllowances() {
        long now = System.currentTimeMillis();
        AllowanceManager.grant(COMPUTER, steve, 100, now + 60_000);
        ask(steve, "Steve", 5, 500, 60, now + 60_000);

        SessionAuthManager.clearPlayerSessions(steve);
        assertEquals(List.of(COMPUTER + " ccvault_allowance 5 Steve false"), events);
        assertNull(AllowanceManager.get(COMPUTER, steve, now));
    }

    @Test
    void chargesComeOffTheAllowanceAndCannotOverspend() {
        long now = System.currentTimeMillis();
        AllowanceManager.grant(COMPUTER, steve, 100, now + 60_000);

        assertTrue(AllowanceManager.reserve(COMPUTER, steve, 60, now));
        AllowanceManager.settle(COMPUTER, steve, 60, true);
        assertFalse(AllowanceManager.reserve(COMPUTER, steve, 41, now));
        assertEquals(40, AllowanceManager.get(COMPUTER, steve, now)[0]);
    }

    @Test
    void aFailedChargeGivesTheAmountBack() {
        long now = System.currentTimeMillis();
        AllowanceManager.grant(COMPUTER, steve, 100, now + 60_000);
        assertTrue(AllowanceManager.reserve(COMPUTER, steve, 100, now));
        assertFalse(AllowanceManager.reserve(COMPUTER, steve, 1, now));
        AllowanceManager.settle(COMPUTER, steve, 100, false);
        assertEquals(100, AllowanceManager.get(COMPUTER, steve, now)[0]);
    }

    @Test
    void usingItUpEndsIt() {
        long now = System.currentTimeMillis();
        AllowanceManager.grant(COMPUTER, steve, 100, now + 60_000);
        assertTrue(AllowanceManager.reserve(COMPUTER, steve, 100, now));
        AllowanceManager.settle(COMPUTER, steve, 100, true);
        assertNull(AllowanceManager.get(COMPUTER, steve, now));
        assertFalse(AllowanceManager.reserve(COMPUTER, steve, 1, now));
    }

    @Test
    void itEndsWhenItsTimeIsUp() {
        long now = System.currentTimeMillis();
        AllowanceManager.grant(COMPUTER, steve, 100, now + 1_000);
        assertArrayEquals(new long[]{100, 1}, AllowanceManager.get(COMPUTER, steve, now));
        assertFalse(AllowanceManager.reserve(COMPUTER, steve, 1, now + 1_000));
        assertNull(AllowanceManager.get(COMPUTER, steve, now + 1_000));
    }

    @Test
    void itBelongsToOneComputerAndOnePlayer() {
        long now = System.currentTimeMillis();
        AllowanceManager.grant(COMPUTER, steve, 100, now + 60_000);
        assertFalse(AllowanceManager.reserve(COMPUTER + 1, steve, 1, now));
        assertFalse(AllowanceManager.reserve(COMPUTER, alex, 1, now));
    }

    @Test
    void revokingTheComputerEndsItsAllowanceOnly() {
        long now = System.currentTimeMillis();
        AllowanceManager.grant(COMPUTER, steve, 100, now + 60_000);
        AllowanceManager.grant(COMPUTER + 1, steve, 100, now + 60_000);
        SessionAuthManager.revokeSession(steve, COMPUTER);
        assertNull(AllowanceManager.get(COMPUTER, steve, now));
        assertEquals(100, AllowanceManager.get(COMPUTER + 1, steve, now)[0]);

        SessionAuthManager.revokeAll(steve);
        assertNull(AllowanceManager.get(COMPUTER + 1, steve, now));
    }

    @Test
    void endAllowanceEndsIt() {
        long now = System.currentTimeMillis();
        AllowanceManager.grant(COMPUTER, steve, 100, now + 60_000);
        assertTrue(AllowanceManager.end(COMPUTER, steve));
        assertFalse(AllowanceManager.end(COMPUTER, steve));
    }

    @Test
    void aRebootDropsWaitingRequestsQuietlyButKeepsAllowances() {
        long now = System.currentTimeMillis();
        AllowanceManager.grant(COMPUTER, steve, 100, now + 60_000);
        String nonce = ask(alex, "Alex", 7, 50, 5, now + 60_000);

        SessionAuthManager.dropPendingAllowances(COMPUTER);
        assertEquals(Approval.INVALID, SessionAuthManager.approveNonce(alex, nonce));
        assertTrue(events.isEmpty());
        assertEquals(100, AllowanceManager.get(COMPUTER, steve, now)[0]);
    }

    @Test
    void requestIdsCountFromOnePerComputerAndRestartOnBoot() {
        assertEquals(1, AllowanceManager.nextRequestId(COMPUTER));
        assertEquals(2, AllowanceManager.nextRequestId(COMPUTER));
        assertEquals(1, AllowanceManager.nextRequestId(COMPUTER + 1));
        AllowanceManager.restartIds(COMPUTER);
        assertEquals(1, AllowanceManager.nextRequestId(COMPUTER));
    }

    @Test
    void secondsLeftRoundDown() {
        long now = System.currentTimeMillis();
        AllowanceManager.grant(COMPUTER, steve, 10, now + 1_999);
        assertEquals(1, AllowanceManager.get(COMPUTER, steve, now)[1]);
    }

    @Test
    void aNewApprovalReplacesTheOldAllowance() {
        long now = System.currentTimeMillis();
        AllowanceManager.grant(COMPUTER, steve, 100, now + 60_000);
        assertEquals(Approval.ALLOWANCE, SessionAuthManager.approveNonce(steve, ask(steve, "Steve", 6, 30, 1, now + 60_000)));
        assertEquals(30, AllowanceManager.get(COMPUTER, steve, now)[0]);
    }
}
