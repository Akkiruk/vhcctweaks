package com.vhcctweaks.inventory;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerRulesTest {

    @Test
    void innerSlotsCountFromOne() {
        assertNull(ContainerRules.innerOutOfRange(1, 27));
        assertNull(ContainerRules.innerOutOfRange(27, 27));
        assertEquals("inner slot out of range (between 1 and 27)", ContainerRules.innerOutOfRange(0, 27));
        assertEquals("inner slot out of range (between 1 and 27)", ContainerRules.innerOutOfRange(28, 27));
        assertEquals("inner slot out of range (between 1 and 0)", ContainerRules.innerOutOfRange(1, 0));
    }

    @Test
    void onlyAnInventoryThatSetsSlotsAndHandsOutRealItemsCanBeChanged() {
        assertTrue(ContainerRules.writable(true, true));
        assertFalse(ContainerRules.writable(true, false));
        assertFalse(ContainerRules.writable(false, true));
        assertFalse(ContainerRules.writable(false, false));
    }

    @Test
    void identifyReasons() {
        assertNull(ContainerRules.identifyReason(ContainerRules.Piece.UNIDENTIFIED, 1, true));
        assertEquals("not identifiable", ContainerRules.identifyReason(ContainerRules.Piece.NOT_IDENTIFIABLE, 1, true));
        assertEquals("already identified", ContainerRules.identifyReason(ContainerRules.Piece.IDENTIFIED, 1, true));
        assertEquals("being identified", ContainerRules.identifyReason(ContainerRules.Piece.ROLLING, 1, true));
        assertEquals("can't identify a stack", ContainerRules.identifyReason(ContainerRules.Piece.UNIDENTIFIED, 2, true));
        // Vault Hunters said no to a single piece
        assertEquals("not identifiable", ContainerRules.identifyReason(ContainerRules.Piece.UNIDENTIFIED, 1, false));
    }

    @Test
    void aStateReasonComesBeforeTheStackReason() {
        assertEquals("already identified", ContainerRules.identifyReason(ContainerRules.Piece.IDENTIFIED, 5, true));
        assertEquals("being identified", ContainerRules.identifyReason(ContainerRules.Piece.ROLLING, 5, false));
    }

    @Test
    void playerReasonsNameThePlayer() {
        assertEquals("player 'Steve' is not online", ContainerRules.notOnline("Steve"));
        assertEquals("player 'Steve' has not approved this computer", ContainerRules.notApproved("Steve"));
    }

    @Test
    void aMoveOutInsertsFirstAndTakesOnlyWhatTheTargetTook() {
        List<String> order = new ArrayList<>();
        int[] taken = {0};
        int moved = ContainerRules.moveOut(40, Integer.MAX_VALUE, 64,
                n -> { order.add("insert " + n); return 25; },
                n -> { order.add("take " + n); taken[0] = n; });
        assertEquals(25, moved);
        assertEquals(25, taken[0]);
        assertEquals(List.of("insert 40", "take 25"), order);
    }

    @Test
    void aFullTargetTakesNothingOut() {
        boolean[] took = {false};
        assertEquals(0, ContainerRules.moveOut(10, 64, 64, n -> 0, n -> took[0] = true));
        assertFalse(took[0]);
    }

    @Test
    void aMoveOffersAtMostTheLimitTheSlotAndOneStack() {
        int[] offered = {0};
        ContainerRules.moveOut(200, Integer.MAX_VALUE, 64, n -> offered[0] = n, n -> { });
        assertEquals(64, offered[0]);
        ContainerRules.moveOut(200, 10, 64, n -> offered[0] = n, n -> { });
        assertEquals(10, offered[0]);
        ContainerRules.moveOut(3, 10, 64, n -> offered[0] = n, n -> { });
        assertEquals(3, offered[0]);
    }

    @Test
    void aTargetClaimingMoreThanOfferedOnlyTakesWhatWasOffered() {
        int[] taken = {0};
        assertEquals(5, ContainerRules.moveOut(5, 64, 64, n -> 99, n -> taken[0] = n));
        assertEquals(5, taken[0]);
    }

    @Test
    void nothingToMoveCallsNeither() {
        boolean[] called = {false};
        assertEquals(0, ContainerRules.moveOut(0, 64, 64, n -> { called[0] = true; return n; }, n -> called[0] = true));
        assertEquals(0, ContainerRules.moveOut(5, 0, 64, n -> { called[0] = true; return n; }, n -> called[0] = true));
        assertFalse(called[0]);
    }
}
