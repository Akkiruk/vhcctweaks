package com.vhcctweaks.api;

import com.vhcctweaks.VHCCTweaks;
import dan200.computercraft.api.lua.ILuaAPI;
import dan200.computercraft.api.lua.LuaFunction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * General-purpose local filesystem API exposed as "vhcc" to all CC:Tweaked computers.
 * All operations are sandboxed inside a configurable root directory.
 * Works in singleplayer and on servers (writes to the server/instance folder).
 *
 * Lua usage:
 *   vhcc.write("mydata/log.txt", "hello world")
 *   local content = vhcc.read("mydata/log.txt")
 *   vhcc.append("mydata/log.txt", "another line\n")
 *   local files = vhcc.list("mydata")
 *   vhcc.makeDir("mydata/subfolder")
 *   vhcc.delete("mydata/old.txt")
 *   local exists = vhcc.exists("mydata/log.txt")
 *   local isDir = vhcc.isDir("mydata")
 *   local size = vhcc.getSize("mydata/log.txt")
 *   local path = vhcc.getBasePath()
 */
public class VHCCTweaksAPI implements ILuaAPI {

    // Only allow safe path segments: alphanumeric, dash, underscore, dot
    // No segment can be empty, "..", or start with "."
    private static final Pattern SAFE_SEGMENT = Pattern.compile("^[a-zA-Z0-9_\\-][a-zA-Z0-9_\\-.]{0,127}$");
    private static final long MAX_WRITE_SIZE = 1024 * 1024; // 1 MB per write
    private static final int MAX_PATH_DEPTH = 16;

    private static Path rootDir;

    public static void setRootDir(Path dir) {
        rootDir = dir;
    }

    @Override
    public String[] getNames() {
        return new String[]{"vhcc"};
    }

    // ===== Path validation =====

    private Path resolve(String path) throws Exception {
        if (rootDir == null) {
            throw new Exception("vhcc: root directory not configured");
        }
        if (path == null || path.isEmpty()) {
            throw new Exception("vhcc: path cannot be empty");
        }

        // Normalize separators
        String normalized = path.replace('\\', '/');

        // No absolute paths
        if (normalized.startsWith("/")) {
            throw new Exception("vhcc: absolute paths not allowed");
        }

        // Split and validate each segment
        String[] segments = normalized.split("/");
        if (segments.length > MAX_PATH_DEPTH) {
            throw new Exception("vhcc: path too deep (max " + MAX_PATH_DEPTH + " levels)");
        }

        for (String seg : segments) {
            if (seg.isEmpty()) continue; // skip double slashes
            if (seg.equals("..") || seg.equals(".")) {
                throw new Exception("vhcc: '..' and '.' not allowed in paths");
            }
            if (!SAFE_SEGMENT.matcher(seg).matches()) {
                throw new Exception("vhcc: invalid path segment '" + seg +
                        "' - use letters, numbers, dashes, underscores, dots");
            }
        }

        Path resolved = rootDir.resolve(normalized).normalize();

        // Final containment check
        if (!resolved.startsWith(rootDir.normalize())) {
            throw new Exception("vhcc: path escapes sandbox");
        }

        return resolved;
    }

    // ===== API Functions =====

    /**
     * Check if the vhcc API is available.
     * Lua: if vhcc.isAvailable() then ... end
     */
    @LuaFunction
    public final boolean isAvailable() {
        return rootDir != null;
    }

    /**
     * Get the real filesystem path where files are stored.
     * Lua: local path = vhcc.getBasePath()
     */
    @LuaFunction
    public final String getBasePath() throws Exception {
        if (rootDir == null) {
            throw new Exception("vhcc: root directory not configured");
        }
        return rootDir.toAbsolutePath().toString();
    }

    /**
     * Write content to a file (creates or overwrites).
     * Parent directories are created automatically.
     * Lua: vhcc.write("folder/file.txt", "content here")
     */
    @LuaFunction
    public final boolean write(String path, String content) throws Exception {
        if (content != null && content.length() > MAX_WRITE_SIZE) {
            throw new Exception("vhcc: content too large (max " + (MAX_WRITE_SIZE / 1024) + " KB)");
        }
        Path target = resolve(path);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content != null ? content : "",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) {
            throw new Exception("vhcc: write failed - " + e.getMessage());
        }
    }

    /**
     * Append content to a file (creates if missing).
     * Parent directories are created automatically.
     * Lua: vhcc.append("log.txt", "new line\n")
     */
    @LuaFunction
    public final boolean append(String path, String content) throws Exception {
        if (content != null && content.length() > MAX_WRITE_SIZE) {
            throw new Exception("vhcc: content too large (max " + (MAX_WRITE_SIZE / 1024) + " KB)");
        }
        Path target = resolve(path);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content != null ? content : "",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            return true;
        } catch (IOException e) {
            throw new Exception("vhcc: append failed - " + e.getMessage());
        }
    }

    /**
     * Read the entire contents of a file.
     * Lua: local text = vhcc.read("folder/file.txt")
     * Returns nil if the file doesn't exist.
     */
    @LuaFunction
    public final Object[] read(String path) throws Exception {
        Path target = resolve(path);
        if (!Files.exists(target)) {
            return new Object[]{null};
        }
        if (Files.isDirectory(target)) {
            throw new Exception("vhcc: cannot read a directory");
        }
        try {
            String content = Files.readString(target, StandardCharsets.UTF_8);
            return new Object[]{content};
        } catch (IOException e) {
            throw new Exception("vhcc: read failed - " + e.getMessage());
        }
    }

    /**
     * Check if a file or directory exists.
     * Lua: if vhcc.exists("folder/file.txt") then ... end
     */
    @LuaFunction
    public final boolean exists(String path) throws Exception {
        Path target = resolve(path);
        return Files.exists(target);
    }

    /**
     * Check if a path is a directory.
     * Lua: if vhcc.isDir("folder") then ... end
     */
    @LuaFunction
    public final boolean isDir(String path) throws Exception {
        Path target = resolve(path);
        return Files.isDirectory(target);
    }

    /**
     * Get the size of a file in bytes.
     * Lua: local bytes = vhcc.getSize("file.txt")
     */
    @LuaFunction
    public final long getSize(String path) throws Exception {
        Path target = resolve(path);
        if (!Files.exists(target)) {
            throw new Exception("vhcc: file not found - " + path);
        }
        if (Files.isDirectory(target)) {
            throw new Exception("vhcc: cannot get size of a directory");
        }
        try {
            return Files.size(target);
        } catch (IOException e) {
            throw new Exception("vhcc: getSize failed - " + e.getMessage());
        }
    }

    /**
     * List files and directories at a path. Returns a table of names.
     * Directory names end with "/".
     * Lua: local items = vhcc.list("folder")
     *      for _, name in ipairs(items) do print(name) end
     * Call with "" or "/" to list the root.
     */
    @LuaFunction
    public final Map<Integer, String> list(String path) throws Exception {
        Path target;
        if (path == null || path.isEmpty() || path.equals("/")) {
            if (rootDir == null) {
                throw new Exception("vhcc: root directory not configured");
            }
            target = rootDir;
        } else {
            target = resolve(path);
        }

        if (!Files.exists(target)) {
            throw new Exception("vhcc: directory not found - " + path);
        }
        if (!Files.isDirectory(target)) {
            throw new Exception("vhcc: not a directory - " + path);
        }

        Map<Integer, String> result = new HashMap<>();
        try (Stream<Path> entries = Files.list(target)) {
            int index = 1;
            Iterator<Path> it = entries.sorted().iterator();
            while (it.hasNext()) {
                Path entry = it.next();
                String name = entry.getFileName().toString();
                if (Files.isDirectory(entry)) {
                    name = name + "/";
                }
                result.put(index++, name);
            }
        } catch (IOException e) {
            throw new Exception("vhcc: list failed - " + e.getMessage());
        }
        return result;
    }

    /**
     * Create a directory (and parents).
     * Lua: vhcc.makeDir("folder/subfolder")
     */
    @LuaFunction
    public final boolean makeDir(String path) throws Exception {
        Path target = resolve(path);
        try {
            Files.createDirectories(target);
            return true;
        } catch (IOException e) {
            throw new Exception("vhcc: makeDir failed - " + e.getMessage());
        }
    }

    /**
     * Delete a file or empty directory.
     * Lua: vhcc.delete("folder/old.txt")
     */
    @LuaFunction
    public final boolean delete(String path) throws Exception {
        Path target = resolve(path);
        if (!Files.exists(target)) {
            return false; // nothing to delete
        }
        if (Files.isDirectory(target)) {
            // Only allow deleting empty directories for safety
            try (Stream<Path> entries = Files.list(target)) {
                if (entries.findAny().isPresent()) {
                    throw new Exception("vhcc: directory not empty - " + path);
                }
            }
        }
        try {
            Files.delete(target);
            return true;
        } catch (IOException e) {
            throw new Exception("vhcc: delete failed - " + e.getMessage());
        }
    }

    /**
     * Move/rename a file or directory.
     * Lua: vhcc.move("old/path.txt", "new/path.txt")
     */
    @LuaFunction
    public final boolean move(String from, String to) throws Exception {
        Path source = resolve(from);
        Path dest = resolve(to);
        if (!Files.exists(source)) {
            throw new Exception("vhcc: source not found - " + from);
        }
        if (Files.exists(dest)) {
            throw new Exception("vhcc: destination already exists - " + to);
        }
        try {
            Files.createDirectories(dest.getParent());
            Files.move(source, dest);
            return true;
        } catch (IOException e) {
            throw new Exception("vhcc: move failed - " + e.getMessage());
        }
    }

    /**
     * Copy a file.
     * Lua: vhcc.copy("source.txt", "dest.txt")
     */
    @LuaFunction
    public final boolean copy(String from, String to) throws Exception {
        Path source = resolve(from);
        Path dest = resolve(to);
        if (!Files.exists(source)) {
            throw new Exception("vhcc: source not found - " + from);
        }
        if (Files.isDirectory(source)) {
            throw new Exception("vhcc: cannot copy directories");
        }
        if (Files.exists(dest)) {
            throw new Exception("vhcc: destination already exists - " + to);
        }
        try {
            Files.createDirectories(dest.getParent());
            Files.copy(source, dest);
            return true;
        } catch (IOException e) {
            throw new Exception("vhcc: copy failed - " + e.getMessage());
        }
    }
}
