package com.vhcctweaks.lockdown;

import dan200.computercraft.api.filesystem.IMount;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockdownManifestTest {

    private static final String REAL_MANIFEST = """
            # vhcctweaks filesystem lockdown
            unlock-file=.vhcc_unlock
            allow-exact=.vhcc_unlock
            allow-exact=.update_lock
            allow-prefix=phone_data
            allow-suffix=.dat
            allow-suffix=.log
            not a setting
            mystery=value
            """;

    /** A mount holding only the manifest, or nothing when text is null. */
    private record ManifestMount(String text) implements IMount {
        @Override
        public boolean exists(String path) {
            return text != null && path.equals(LockdownManifest.FILE_NAME);
        }

        @Override
        public boolean isDirectory(String path) {
            return false;
        }

        @Override
        public void list(String path, List<String> contents) {
        }

        @Override
        public long getSize(String path) {
            return text == null ? 0 : text.length();
        }

        @Override
        public ReadableByteChannel openForRead(String path) throws IOException {
            if (!exists(path)) throw new FileNotFoundException(path);
            return Channels.newChannel(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static LockdownManifest manifest(String text) {
        return LockdownManifest.fromMount(new ManifestMount(text), 1);
    }

    @Test
    void noManifestMeansNoLockdown() {
        assertNull(manifest(null));
    }

    @Test
    void writesOnlyWhatTheManifestAllows() {
        LockdownManifest manifest = manifest(REAL_MANIFEST);
        assertTrue(manifest.allowsWrite(".update_lock"));
        assertTrue(manifest.allowsWrite("phone_data"));
        assertTrue(manifest.allowsWrite("phone_data/save.txt"));
        assertTrue(manifest.allowsWrite("anywhere/game.dat"));
        assertFalse(manifest.allowsWrite("startup.lua"));
        assertFalse(manifest.allowsWrite("phone_data_extra/file.txt"));
        assertFalse(manifest.allowsWrite(""));
    }

    @Test
    void pathsAreComparedInOneForm() {
        LockdownManifest manifest = manifest(REAL_MANIFEST);
        assertTrue(manifest.allowsWrite("/phone_data//save.txt"));
        assertTrue(manifest.allowsWrite("phone_data\\save.txt"));
    }

    @Test
    void theManifestCanNeverBeWritten() {
        assertFalse(manifest("allow-suffix=.txt\nallow-exact=vhcc_lockdown.txt").allowsWrite(LockdownManifest.FILE_NAME));
    }

    @Test
    void unlockFileHasADefaultAndCanBeChanged() {
        assertEquals(LockdownManifest.DEFAULT_UNLOCK_FILE, manifest("allow-suffix=.dat").unlockFile());
        assertEquals("custom/unlock", manifest("unlock_file=/custom/unlock").unlockFile());
    }
}
