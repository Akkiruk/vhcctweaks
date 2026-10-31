package com.vhcctweaks.ccvault;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerLookupTest {

    private final UUID known = UUID.randomUUID();
    private final List<Runnable> queued = new ArrayList<>();
    private int resolved = 0;

    private PlayerLookup lookup() {
        return new PlayerLookup(name -> {
            resolved++;
            return name.equalsIgnoreCase("Known") ? Optional.of(known) : Optional.empty();
        }, queued::add);
    }

    private void runQueued() {
        queued.forEach(Runnable::run);
        queued.clear();
    }

    @Test
    void neverWaitsOnTheLookup() {
        PlayerLookup lookup = lookup();
        assertEquals(PlayerLookup.Status.PENDING, lookup.lookup("Known").status());
        assertEquals(0, resolved);

        runQueued();
        PlayerLookup.Result result = lookup.lookup("Known");
        assertEquals(PlayerLookup.Status.FOUND, result.status());
        assertEquals(known, result.uuid());
    }

    @Test
    void repeatsAndCaseShareOneLookup() {
        PlayerLookup lookup = lookup();
        lookup.lookup("Known");
        lookup.lookup("KNOWN");
        lookup.lookup("known");
        assertEquals(1, queued.size());
    }

    @Test
    void unknownNamesAreRemembered() {
        PlayerLookup lookup = lookup();
        lookup.lookup("Nobody");
        runQueued();
        assertEquals(PlayerLookup.Status.UNKNOWN, lookup.lookup("Nobody").status());
        assertEquals(PlayerLookup.Status.UNKNOWN, lookup.lookup("nobody").status());
        assertEquals(1, resolved);
    }

    @Test
    void aFailedLookupIsUnknown() {
        PlayerLookup lookup = new PlayerLookup(name -> { throw new IllegalStateException("network down"); }, Runnable::run);
        assertEquals(PlayerLookup.Status.UNKNOWN, lookup.lookup("Anyone").status());
    }

    @Test
    void aFloodOfNamesStaysBounded() {
        PlayerLookup lookup = lookup();
        for (int i = 0; i < PlayerLookup.MAX_ENTRIES * 2; i++) {
            lookup.lookup("name" + i);
        }
        assertEquals(PlayerLookup.MAX_ENTRIES, queued.size());
    }
}
