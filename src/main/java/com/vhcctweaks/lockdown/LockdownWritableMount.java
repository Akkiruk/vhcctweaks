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
    private final LockdownManifest manifest;

    private LockdownWritableMount(IWritableMount delegate, LockdownManifest manifest) {
        this.delegate = delegate;
        this.manifest = manifest;
    }

    public static IWritableMount wrapIfNeeded(IWritableMount delegate, int computerId) {
        LockdownManifest manifest = LockdownManifest.fromMount(delegate, computerId);
        if (manifest == null) return delegate;
        return new LockdownWritableMount(delegate, manifest);
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
        if (isUnlocked() || manifest.allowsWrite(path)) return;
        throw new FileOperationException(path, "Access denied");
    }

    private boolean isUnlocked() throws IOException {
        return delegate.exists(manifest.unlockFile());
    }
}
