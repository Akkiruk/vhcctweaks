package com.vhcctweaks.ccvault;

import com.vhcctweaks.ccvault.SessionAuthManager.Approval;
import com.vhcctweaks.ccvault.SessionAuthManager.Request;
import com.vhcctweaks.handler.ComputerPlacementTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HostApprovalTest {

    private static final int COMPUTER = 7;

    @TempDir
    Path dataDir;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void init() {
        SessionAuthManager.reset();
        ComputerPlacementTracker.init(dataDir);
    }

    private String pending(UUID player, Request request) {
        return SessionAuthManager.addPending(player, COMPUTER, System.currentTimeMillis() + 60_000, request);
    }

    @Test
    void anOfferMakesTheClickerTheHost() {
        assertEquals(Approval.HOST, SessionAuthManager.approveNonce(alice, pending(alice, Request.OFFER)));
        assertEquals(alice, ComputerPlacementTracker.getOwner(COMPUTER));
    }

    @Test
    void theFirstOfTwoOffersWins() {
        String aliceOffer = pending(alice, Request.OFFER);
        String bobOffer = pending(bob, Request.OFFER);

        assertEquals(Approval.HOST, SessionAuthManager.approveNonce(alice, aliceOffer));
        assertEquals(Approval.TAKEN, SessionAuthManager.approveNonce(bob, bobOffer));
        assertEquals(alice, ComputerPlacementTracker.getOwner(COMPUTER));
    }

    @Test
    void aClaimCanTakeOverButAnOfferCannot() {
        ComputerPlacementTracker.setOwner(COMPUTER, alice);

        assertEquals(Approval.TAKEN, SessionAuthManager.approveNonce(bob, pending(bob, Request.OFFER)));
        assertEquals(alice, ComputerPlacementTracker.getOwner(COMPUTER));

        assertEquals(Approval.HOST, SessionAuthManager.approveNonce(bob, pending(bob, Request.CLAIM)));
        assertEquals(bob, ComputerPlacementTracker.getOwner(COMPUTER));
    }

    @Test
    void someoneElsesLinkDoesNothing() {
        assertEquals(Approval.INVALID, SessionAuthManager.approveNonce(bob, pending(alice, Request.OFFER)));
        assertNull(ComputerPlacementTracker.getOwner(COMPUTER));
    }

    @Test
    void anExpiredOfferDoesNothing() {
        String offer = SessionAuthManager.addPending(alice, COMPUTER, System.currentTimeMillis() - 1, Request.OFFER);
        assertEquals(Approval.INVALID, SessionAuthManager.approveNonce(alice, offer));
        assertNull(ComputerPlacementTracker.getOwner(COMPUTER));
    }

    @Test
    void aSessionGrantsNoOwnership() {
        assertEquals(Approval.SESSION, SessionAuthManager.approveNonce(alice, pending(alice, Request.SESSION)));
        assertNull(ComputerPlacementTracker.getOwner(COMPUTER));
    }
}
