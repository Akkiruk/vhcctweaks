package com.vhcctweaks.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnershipPersistenceTest {

    @TempDir
    Path dataDir;

    private final UUID owner = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private void init() {
        ComputerPlacementTracker.init(dataDir);
        ComputerLock.init(dataDir);
    }

    private List<String> keptFiles() throws IOException {
        try (Stream<Path> files = Files.list(dataDir.resolve("ccvault"))) {
            return files.map(f -> f.getFileName().toString()).filter(n -> n.contains(".corrupt-")).toList();
        }
    }

    @Test
    void ownerSurvivesARestart() {
        init();
        ComputerPlacementTracker.setOwner(7, owner);
        init();
        assertEquals(owner, ComputerPlacementTracker.getOwner(7));
    }

    @Test
    void setOwnerNeverReplacesAnOwner() {
        init();
        assertTrue(ComputerPlacementTracker.setOwner(7, owner));
        assertFalse(ComputerPlacementTracker.setOwner(7, other));
        assertEquals(owner, ComputerPlacementTracker.getOwner(7));
    }

    @Test
    void forgettingAnOwnerLastsAndLiftsTheLock() {
        init();
        ComputerPlacementTracker.setOwner(7, owner);
        ComputerLock.setLocked(7, true);

        assertTrue(ComputerPlacementTracker.clearOwner(7));
        assertFalse(ComputerPlacementTracker.clearOwner(7));

        init();
        assertNull(ComputerPlacementTracker.getOwner(7));
        assertFalse(ComputerLock.isLocked(7));
        assertTrue(ComputerPlacementTracker.setOwner(7, other));
    }

    @Test
    void savesLeaveNoTemporaryFiles() throws IOException {
        init();
        ComputerPlacementTracker.setOwner(7, owner);
        assertFalse(Files.exists(dataDir.resolve("ccvault/owners.json.tmp")));
    }

    @Test
    void aDamagedOwnersFileIsKeptAndDoesNotStopStartup() throws IOException {
        Files.createDirectories(dataDir.resolve("ccvault"));
        Files.writeString(dataDir.resolve("ccvault/owners.json"), "{\"7\": \"not fin");

        init();

        assertNull(ComputerPlacementTracker.getOwner(7));
        assertEquals(1, keptFiles().size());
    }

    @Test
    void anEmptyOwnersFileIsKept() throws IOException {
        Files.createDirectories(dataDir.resolve("ccvault"));
        Files.writeString(dataDir.resolve("ccvault/owners.json"), "");

        init();

        assertEquals(1, keptFiles().size());
    }

    @Test
    void aDamagedLocksFileIsKeptAndDoesNotStopStartup() throws IOException {
        Files.createDirectories(dataDir.resolve("ccvault"));
        Files.writeString(dataDir.resolve("ccvault/locks.json"), "[1, 2");

        init();

        assertFalse(ComputerLock.isLocked(1));
        assertEquals(1, keptFiles().size());
    }

    @Test
    void aNewWorldStartsWithNothingFromTheLast(@TempDir Path otherWorld) {
        init();
        ComputerPlacementTracker.setOwner(7, owner);
        ComputerLock.setLocked(7, true);

        ComputerPlacementTracker.init(otherWorld);
        ComputerLock.init(otherWorld);

        assertNull(ComputerPlacementTracker.getOwner(7));
        assertFalse(ComputerLock.isLocked(7));
    }
}
