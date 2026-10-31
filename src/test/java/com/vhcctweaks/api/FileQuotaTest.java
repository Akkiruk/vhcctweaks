package com.vhcctweaks.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileQuotaTest {

    @Test
    void missingFolderUsesNothing(@TempDir Path dir) throws IOException {
        assertEquals(0, FileQuota.usage(dir.resolve("never-written")));
    }

    @Test
    void everyEntryCostsAtLeastTheMinimum(@TempDir Path dir) throws IOException {
        Path root = dir.resolve("root");
        Files.createDirectories(root.resolve("folder"));
        Files.writeString(root.resolve("small.txt"), "x");
        Files.write(root.resolve("big.bin"), new byte[10_000]);

        // root, folder, small.txt, big.bin
        assertEquals(FileQuota.MIN_ENTRY_BYTES * 3 + 10_000, FileQuota.usage(root));
    }

    @Test
    void overwritingOnlyCountsTheDifference() {
        long limit = 100_000;
        assertTrue(FileQuota.fits(100_000, 50_000, 50_000, limit));
        assertFalse(FileQuota.fits(100_000, 50_000, 50_001, limit));
        assertFalse(FileQuota.fits(100_000, 0, 0, limit));
    }
}
