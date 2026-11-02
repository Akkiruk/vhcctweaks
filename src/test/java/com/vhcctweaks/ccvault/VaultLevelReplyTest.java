package com.vhcctweaks.ccvault;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class VaultLevelReplyTest {

    @Test
    void aKnownPlayerWithDataGetsTheirLevel() {
        assertArrayEquals(new Object[]{37}, VaultLevelReply.answer(PlayerLookup.Status.FOUND, "Steve", 37));
        assertArrayEquals(new Object[]{0}, VaultLevelReply.answer(PlayerLookup.Status.FOUND, "Steve", 0));
    }

    @Test
    void aKnownPlayerWithNoSavedStatsHasNoVaultData() {
        assertArrayEquals(new Object[]{null, "no vault data"}, VaultLevelReply.answer(PlayerLookup.Status.FOUND, "Steve", null));
    }

    @Test
    void anUnknownNameIsAnUnknownPlayer() {
        assertArrayEquals(new Object[]{null, "unknown player"}, VaultLevelReply.answer(PlayerLookup.Status.UNKNOWN, "nobody", null));
    }

    @Test
    void aNameStillBeingLookedUpSaysTryAgainLikeGetPlayerBalance() {
        assertArrayEquals(new Object[]{null, "looking up player 'Steve' - try again"},
                VaultLevelReply.answer(PlayerLookup.Status.PENDING, "Steve", null));
    }
}
