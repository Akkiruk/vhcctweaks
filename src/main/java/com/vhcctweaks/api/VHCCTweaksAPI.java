package com.vhcctweaks.api;

import com.vhcctweaks.VHCCTweaks;
import dan200.computercraft.api.lua.ILuaAPI;
import dan200.computercraft.api.lua.LuaFunction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;

/**
 * Custom Lua API exposed as "vhcc" to all CC:Tweaked computers.
 * Provides saveResults() to write test results to the real filesystem.
 */
public class VHCCTweaksAPI implements ILuaAPI {

    private static final Pattern SAFE_FILENAME = Pattern.compile("^[a-zA-Z0-9_\\-][a-zA-Z0-9_\\-.]{0,63}$");
    private static Path outputDir;

    public static void setOutputDir(Path dir) {
        outputDir = dir;
    }

    @Override
    public String[] getNames() {
        return new String[]{"vhcc"};
    }

    /**
     * Save text content to a file in the vhcctweaks_test_results folder.
     * Lua usage: vhcc.saveResults("run_day5_1430.txt", "contents here")
     */
    @LuaFunction
    public final boolean saveResults(String filename, String content) throws Exception {
        if (outputDir == null) {
            throw new Exception("Output directory not configured");
        }
        if (filename == null || filename.isEmpty()) {
            throw new Exception("Filename cannot be empty");
        }
        // Sanitize: only allow safe filenames (no path traversal)
        if (!SAFE_FILENAME.matcher(filename).matches()) {
            throw new Exception("Invalid filename - use only letters, numbers, dashes, underscores, dots");
        }
        // Must end with .txt
        if (!filename.endsWith(".txt")) {
            throw new Exception("Filename must end with .txt");
        }

        try {
            Files.createDirectories(outputDir);
            Path target = outputDir.resolve(filename);
            // Double-check resolved path is still inside outputDir
            if (!target.normalize().startsWith(outputDir.normalize())) {
                throw new Exception("Invalid path");
            }
            Files.writeString(target, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            VHCCTweaks.LOGGER.info("Test results saved to {}", target);
            return true;
        } catch (IOException e) {
            VHCCTweaks.LOGGER.warn("Failed to save test results: {}", e.getMessage());
            throw new Exception("Failed to write file: " + e.getMessage());
        }
    }

    /**
     * Append a line to a file in the vhcctweaks_test_results folder.
     * Lua usage: vhcc.appendResults("history.txt", "line to append")
     */
    @LuaFunction
    public final boolean appendResults(String filename, String line) throws Exception {
        if (outputDir == null) {
            throw new Exception("Output directory not configured");
        }
        if (filename == null || filename.isEmpty()) {
            throw new Exception("Filename cannot be empty");
        }
        if (!SAFE_FILENAME.matcher(filename).matches()) {
            throw new Exception("Invalid filename - use only letters, numbers, dashes, underscores, dots");
        }
        if (!filename.endsWith(".txt")) {
            throw new Exception("Filename must end with .txt");
        }

        try {
            Files.createDirectories(outputDir);
            Path target = outputDir.resolve(filename);
            if (!target.normalize().startsWith(outputDir.normalize())) {
                throw new Exception("Invalid path");
            }
            Files.writeString(target, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return true;
        } catch (IOException e) {
            VHCCTweaks.LOGGER.warn("Failed to append test results: {}", e.getMessage());
            throw new Exception("Failed to append file: " + e.getMessage());
        }
    }

    /**
     * Get the real filesystem path where results are saved.
     * Lua usage: local path = vhcc.getResultsPath()
     */
    @LuaFunction
    public final String getResultsPath() throws Exception {
        if (outputDir == null) {
            throw new Exception("Output directory not configured");
        }
        return outputDir.toAbsolutePath().toString();
    }

    /**
     * Check if the vhcc API is available (always returns true).
     * Lua usage: if vhcc and vhcc.isAvailable() then ... end
     */
    @LuaFunction
    public final boolean isAvailable() {
        return outputDir != null;
    }
}
