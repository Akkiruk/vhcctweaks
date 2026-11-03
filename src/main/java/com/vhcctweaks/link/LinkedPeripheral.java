package com.vhcctweaks.link;

import com.vhcctweaks.VHCCTweaks;
import com.vhcctweaks.remote.EventPacer;
import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IWorkMonitor;
import dan200.computercraft.api.peripheral.NotAttachedException;
import dan200.computercraft.core.apis.PeripheralAPI;
import dan200.computercraft.core.asm.PeripheralMethod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One of a linked computer's peripherals as the linking computer sees it, named "&lt;id&gt;:&lt;name&gt;".
 * Attached to the peripheral the way CC's wired modem attaches its RemotePeripheralWrapper: the peripheral sees
 * this as one more computer, so monitors send it monitor_touch and inventories find their pushItems targets through it.
 * Events and main-thread work go to the linking computer.
 */
final class LinkedPeripheral implements IComputerAccess {

    private final LinkManager.Link link;
    final String remoteName;
    final String name;
    final IPeripheral peripheral;

    private final String type;
    private final Set<String> additionalTypes;
    private final Map<String, PeripheralMethod> methods;

    private volatile boolean attached;
    private final Set<String> mounts = new HashSet<>();

    LinkedPeripheral(LinkManager.Link link, String remoteName, IPeripheral peripheral) {
        this.link = link;
        this.remoteName = remoteName;
        this.name = LinkRules.remoteName(link.targetId, remoteName);
        this.peripheral = peripheral;
        type = Objects.requireNonNull(peripheral.getType(), "Peripheral type cannot be null");
        additionalTypes = peripheral.getAdditionalTypes();
        methods = PeripheralAPI.getMethods(peripheral);
    }

    private IComputerAccess host() {
        return link.hub.host;
    }

    void attach() {
        attached = true;
        try {
            peripheral.attach(this);
        } catch (RuntimeException e) {
            VHCCTweaks.LOGGER.warn("Linked peripheral {} failed to attach", name, e);
        }
        // Paced: a target with hundreds of peripherals must not overflow the linking computer's event queue
        EventPacer.linkEvent(host(), "peripheral", name);
    }

    void detach() {
        try {
            peripheral.detach(this);
        } catch (RuntimeException e) {
            VHCCTweaks.LOGGER.warn("Linked peripheral {} failed to detach", name, e);
        }
        EventPacer.linkEvent(host(), "peripheral_detach", name);
        attached = false;

        synchronized (this) {
            for (String mount : mounts) {
                try {
                    host().unmount(mount);
                } catch (RuntimeException ignored) {
                    // Already gone with the computer
                }
            }
            mounts.clear();
        }
    }

    boolean isAttached() {
        return attached;
    }

    String getType() {
        return type;
    }

    Set<String> getAdditionalTypes() {
        return additionalTypes;
    }

    Collection<String> getMethodNames() {
        return methods.keySet();
    }

    MethodResult call(ILuaContext context, String methodName, IArguments arguments) throws LuaException {
        PeripheralMethod method = methods.get(methodName);
        if (method == null) throw new LuaException("No such method " + methodName);
        if (!attached) throw notLoaded();
        return method.apply(peripheral, new GuardedContext(context), this, arguments);
    }

    LuaException notLoaded() {
        return new LuaException("computer " + link.targetId + " is not loaded");
    }

    /**
     * The linking computer's context, except that main-thread work checks on the main thread that the linked computer
     * is still loaded, right before it runs. The remote cannot unload between that check and the work.
     */
    private final class GuardedContext implements ILuaContext {
        private final ILuaContext inner;

        GuardedContext(ILuaContext inner) {
            this.inner = inner;
        }

        @Override
        public long issueMainThreadTask(@Nonnull ILuaTask task) throws LuaException {
            return inner.issueMainThreadTask(() -> {
                if (!attached) throw notLoaded();
                return task.execute();
            });
        }
    }

    // ===== IComputerAccess =====

    @Override
    public synchronized String mount(@Nonnull String desiredLocation, @Nonnull IMount mount) {
        return mount(desiredLocation, mount, name);
    }

    @Override
    public synchronized String mount(@Nonnull String desiredLocation, @Nonnull IMount mount, @Nonnull String driveName) {
        if (!attached) throw new NotAttachedException();
        String mounted = host().mount(desiredLocation, mount, driveName);
        if (mounted != null) mounts.add(mounted);
        return mounted;
    }

    @Override
    public synchronized String mountWritable(@Nonnull String desiredLocation, @Nonnull IWritableMount mount) {
        return mountWritable(desiredLocation, mount, name);
    }

    @Override
    public synchronized String mountWritable(@Nonnull String desiredLocation, @Nonnull IWritableMount mount, @Nonnull String driveName) {
        if (!attached) throw new NotAttachedException();
        String mounted = host().mountWritable(desiredLocation, mount, driveName);
        if (mounted != null) mounts.add(mounted);
        return mounted;
    }

    @Override
    public synchronized void unmount(@Nullable String location) {
        if (!attached) throw new NotAttachedException();
        host().unmount(location);
        mounts.remove(location);
    }

    /** The linking computer's ID: a touch on a linked monitor counts for it, and its watches are its own. */
    @Override
    public int getID() {
        if (!attached) throw new NotAttachedException();
        return link.hub.hostId;
    }

    @Override
    public void queueEvent(@Nonnull String event, Object... arguments) {
        if (!attached) throw new NotAttachedException();
        host().queueEvent(event, arguments);
    }

    @Nonnull
    @Override
    public IWorkMonitor getMainThreadMonitor() {
        if (!attached) throw new NotAttachedException();
        return host().getMainThreadMonitor();
    }

    @Nonnull
    @Override
    public String getAttachmentName() {
        if (!attached) throw new NotAttachedException();
        return name;
    }

    /** Everything the linking computer can name: its own sides and wired networks, and every linked peripheral. */
    @Nonnull
    @Override
    public Map<String, IPeripheral> getAvailablePeripherals() {
        if (!attached) throw new NotAttachedException();
        return LinkManager.availableTo(link.hub);
    }

    @Nullable
    @Override
    public IPeripheral getAvailablePeripheral(@Nonnull String name) {
        if (!attached) throw new NotAttachedException();
        return LinkManager.findFor(link.hub, name);
    }
}
