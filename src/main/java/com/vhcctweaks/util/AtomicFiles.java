package com.vhcctweaks.util;

import com.vhcctweaks.VHCCTweaks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Saves state files so a crash leaves the old version or the new one, never half of either. */
public final class AtomicFiles {

    private AtomicFiles() {}

    public static void writeString(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Keeps a file that could not be parsed, so the next save does not destroy what is left of it. */
    public static void setAside(Path file) {
        Path kept = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.move(file, kept);
            VHCCTweaks.LOGGER.error("Kept unreadable file as {}", kept.getFileName());
        } catch (IOException e) {
            VHCCTweaks.LOGGER.error("Could not set aside unreadable file {}", file.getFileName(), e);
        }
    }
}
