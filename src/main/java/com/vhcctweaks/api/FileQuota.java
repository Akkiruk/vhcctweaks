package com.vhcctweaks.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** How much of its disk allowance a computer's vhcc folder uses. */
final class FileQuota {

    // Empty files and folders still cost the disk something, so a flood of them has to count
    static final long MIN_ENTRY_BYTES = 4096;

    private FileQuota() {}

    static long cost(long fileBytes) {
        return Math.max(fileBytes, MIN_ENTRY_BYTES);
    }

    static long usage(Path root) throws IOException {
        if (!Files.exists(root)) return 0;
        try (Stream<Path> entries = Files.walk(root)) {
            return entries.mapToLong(FileQuota::entryCost).sum();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static long entryCost(Path entry) {
        try {
            return cost(Files.isDirectory(entry) ? 0 : Files.size(entry));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Whether a file at oldCost can become newBytes long. oldCost is 0 for a file that does not exist yet. */
    static boolean fits(long used, long oldCost, long newBytes, long limit) {
        return used - oldCost + cost(newBytes) <= limit;
    }
}
