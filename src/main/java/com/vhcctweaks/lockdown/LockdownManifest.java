package com.vhcctweaks.lockdown;

import com.vhcctweaks.VHCCTweaks;
import dan200.computercraft.api.filesystem.IMount;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Parsed per-computer lockdown policy read from {@value #FILE_NAME}.
 *
 * <p>The manifest lives inside the computer's root save mount, so normal repo
 * installs can ship it as an asset. When present, code/config files become
 * read-only at the filesystem layer while selected runtime data paths remain
 * writable.</p>
 */
public final class LockdownManifest {
    public static final String FILE_NAME = "vhcc_lockdown.txt";
    public static final String DEFAULT_UNLOCK_FILE = ".vhcc_unlock";

    private final String unlockFile;
    private final List<String> exactPaths;
    private final List<String> prefixes;
    private final List<String> suffixes;

    private LockdownManifest(String unlockFile, List<String> exactPaths, List<String> prefixes, List<String> suffixes) {
        this.unlockFile = normalizePath(unlockFile);
        this.exactPaths = exactPaths;
        this.prefixes = prefixes;
        this.suffixes = suffixes;
    }

    public String unlockFile() {
        return unlockFile;
    }

    public boolean allowsWrite(String path) {
        String normalized = normalizePath(path);
        if (normalized.isEmpty()) return false;
        if (FILE_NAME.equals(normalized)) return false;

        for (String exact : exactPaths) {
            if (exact.equals(normalized)) return true;
        }

        for (String prefix : prefixes) {
            if (normalized.equals(prefix) || normalized.startsWith(prefix + "/")) return true;
        }

        for (String suffix : suffixes) {
            if (normalized.endsWith(suffix)) return true;
        }

        return false;
    }

    public static LockdownManifest fromMount(IMount mount, int computerId) {
        try {
            if (!mount.exists(FILE_NAME)) return null;
        } catch (IOException e) {
            VHCCTweaks.LOGGER.warn("Lockdown: failed checking manifest for computer {}: {}", computerId, e.getMessage());
            return null;
        }

        String unlockFile = DEFAULT_UNLOCK_FILE;
        List<String> exactPaths = new ArrayList<>();
        List<String> prefixes = new ArrayList<>();
        List<String> suffixes = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            Channels.newInputStream(mount.openForRead(FILE_NAME)), StandardCharsets.UTF_8))) {
            String rawLine;
            int lineNumber = 0;
            while ((rawLine = reader.readLine()) != null) {
                lineNumber++;
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                int equals = line.indexOf('=');
                if (equals <= 0) {
                    VHCCTweaks.LOGGER.warn("Lockdown: invalid manifest line {} on computer {}: {}", lineNumber, computerId, line);
                    continue;
                }

                String key = line.substring(0, equals).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(equals + 1).trim();
                if (value.isEmpty()) continue;

                switch (key) {
                    case "unlock-file":
                    case "unlock_file":
                        unlockFile = normalizePath(value);
                        break;
                    case "allow-exact":
                    case "allow_exact":
                        exactPaths.add(normalizePath(value));
                        break;
                    case "allow-prefix":
                    case "allow_prefix":
                        prefixes.add(normalizePath(value));
                        break;
                    case "allow-suffix":
                    case "allow_suffix":
                        suffixes.add(value.trim());
                        break;
                    default:
                        VHCCTweaks.LOGGER.warn("Lockdown: unknown manifest key '{}' on computer {}", key, computerId);
                        break;
                }
            }
        } catch (IOException e) {
            VHCCTweaks.LOGGER.warn("Lockdown: failed reading manifest for computer {}: {}", computerId, e.getMessage());
            return null;
        }

        LockdownManifest manifest = new LockdownManifest(
            unlockFile,
            Collections.unmodifiableList(exactPaths),
            Collections.unmodifiableList(prefixes),
            Collections.unmodifiableList(suffixes)
        );

        VHCCTweaks.LOGGER.info("Lockdown enabled for computer {}", computerId);
        return manifest;
    }

    private static String normalizePath(String path) {
        String normalized = path.replace('\\', '/').trim();
        while (normalized.startsWith("./")) normalized = normalized.substring(2);
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.contains("//")) normalized = normalized.replace("//", "/");
        while (normalized.endsWith("/") && normalized.length() > 1) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
