package com.vhcctweaks.link;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LinkRulesTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void sameOwnerSameDimensionBothEnderIsAllowed() {
        assertNull(LinkRules.refusal(true, alice, alice, true, true));
    }

    @Test
    void anUnloadedComputerIsRefusedFirst() {
        assertEquals("not loaded", LinkRules.refusal(false, alice, bob, false, false));
    }

    @Test
    void anotherOwnersComputerIsNotYours() {
        assertEquals("not your computer", LinkRules.refusal(true, alice, bob, true, true));
    }

    @Test
    void anUnownedComputerCannotLinkOrBeLinked() {
        assertEquals("not your computer", LinkRules.refusal(true, null, alice, true, true));
        assertEquals("not your computer", LinkRules.refusal(true, alice, null, true, true));
        assertEquals("not your computer", LinkRules.refusal(true, null, null, true, true));
    }

    @Test
    void theTargetNeedsAnEnderModem() {
        assertEquals("no ender modem", LinkRules.refusal(true, alice, alice, false, true));
    }

    @Test
    void otherDimensionsAreRefused() {
        assertEquals("other dimension", LinkRules.refusal(true, alice, alice, true, false));
    }

    @Test
    void theLimitOnlyStopsNewLinks() {
        assertEquals("too many links", LinkRules.refusalForNewLink(false, 64, 64, null));
        assertNull(LinkRules.refusalForNewLink(false, 63, 64, null));
        assertNull(LinkRules.refusalForNewLink(true, 64, 64, "not loaded"));
        assertEquals("not loaded", LinkRules.refusalForNewLink(false, 64, 64, "not loaded"));
    }

    @Test
    void remoteNamesCarryTheComputerId() {
        assertEquals("5:left", LinkRules.remoteName(5, "left"));
        assertEquals("12:minecraft:chest_3", LinkRules.remoteName(12, "minecraft:chest_3"));
    }

    @Test
    void parsesRemoteNamesAndLeavesWiredNamesAlone() {
        assertEquals(5, LinkRules.computerIdOf("5:left"));
        assertEquals("left", LinkRules.localPart("5:left"));
        assertEquals(12, LinkRules.computerIdOf("12:minecraft:chest_3"));
        assertEquals("minecraft:chest_3", LinkRules.localPart("12:minecraft:chest_3"));
        assertEquals(0, LinkRules.computerIdOf("0:top"));

        assertEquals(-1, LinkRules.computerIdOf("minecraft:chest_3"));
        assertEquals(-1, LinkRules.computerIdOf("left"));
        assertEquals(-1, LinkRules.computerIdOf(":left"));
        assertEquals(-1, LinkRules.computerIdOf("5:"));
        assertEquals(-1, LinkRules.computerIdOf("5a:left"));
        assertEquals(-1, LinkRules.computerIdOf("99999999999:left"));
        assertEquals(-1, LinkRules.computerIdOf(null));
    }
}
