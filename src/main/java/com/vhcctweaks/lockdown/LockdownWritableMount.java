package com.vhcctweaks.lockdown;

import dan200.computercraft.api.filesystem.FileOperationException;
import dan200.computercraft.api.filesystem.IWritableMount;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.OptionalLong;

/**
 * Delegating writable mount which makes most filesystem writes read-only once
 * a lockdown manifest is installed on the computer.
 */
public final class LockdownWritableMount implements IWritableMount {
    private final IWritableMount delegate;
    private final int computerId;
    /** Null while the computer has no manifest: then every write is allowed. */
    private volatile LockdownManifest manifest;

    /** Each computer's current root mount, so a reboot can read its manifest again. */
    private static final java.util.Map<Integer, LockdownWritableMount> LIVE = new java.util.concurrent.ConcurrentHashMap<>();

    private LockdownWritableMount(IWritableMount delegate, int computerId, LockdownManifest manifest) {
        this.delegate = delegate;
        this.computerId = computerId;
        this.manifest = manifest;
    }

    /**
     * Every root mount is wrapped, manifest or not. CC keeps a computer's root mount for as long as the computer object
     * lives, so the manifest is read again on each boot (reload), not only when the mount is made: adding, changing or
     * removing vhcc_lockdown.txt takes effect on the next reboot.
     */
    public static IWritableMount wrapIfNeeded(IWritableMount delegate, int computerId) {
        LockdownWritableMount mount = new LockdownWritableMount(delegate, computerId, LockdownManifest.fromMount(delegate, computerId));
        LIVE.put(computerId, mount);
        return mount;
    }

    /** A computer is booting: read its manifest again. */
    public static void reload(int computerId) {
        LockdownWritableMount mount = LIVE.get(computerId);
        if (mount != null) mount.manifest = LockdownManifest.fromMount(mount.delegate, computerId);
    }

    public static void reset() {
        LIVE.clear();
    }

    @Override
    public boolean exists(String path) throws IOException {
        return delegate.exists(path);
    }

    @Override
    public boolean isDirectory(String path) throws IOException {
        return delegate.isDirectory(path);
    }

    @Override
    public void list(String path, List<String> contents) throws IOException {
        delegate.list(path, contents);
    }

    @Override
    public long getSize(String path) throws IOException {
        return delegate.getSize(path);
    }

    @Override
    public ReadableByteChannel openForRead(String path) throws IOException {
        return delegate.openForRead(path);
    }

    @Override
    public BasicFileAttributes getAttributes(String path) throws IOException {
        return delegate.getAttributes(path);
    }

    @Override
    public void makeDirectory(String path) throws IOException {
        assertWritable(path);
        delegate.makeDirectory(path);
    }

    @Override
    public void delete(String path) throws IOException {
        assertWritable(path);
        delegate.delete(path);
    }

    @Override
    public WritableByteChannel openForWrite(String path) throws IOException {
        assertWritable(path);
        return delegate.openForWrite(path);
    }

    @Override
    public WritableByteChannel openForAppend(String path) throws IOException {
        assertWritable(path);
        return delegate.openForAppend(path);
    }

    @Override
    public long getRemainingSpace() throws IOException {
        return delegate.getRemainingSpace();
    }

    @Override
    public OptionalLong getCapacity() {
        return delegate.getCapacity();
    }

    private void assertWritable(String path) throws IOException {
        LockdownManifest current = manifest;
        // The rule in memory first; the unlock file is a disk check, needed only for a write the rule refuses
        if (current == null || current.allowsWrite(path) || isUnlocked(current)) return;
        throw new FileOperationException(path, "Access denied");
    }

    private boolean isUnlocked(LockdownManifest current) throws IOException {
        return delegate.exists(current.unlockFile());
    }
}
