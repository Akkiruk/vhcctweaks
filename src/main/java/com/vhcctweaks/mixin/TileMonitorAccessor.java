package com.vhcctweaks.mixin;

import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.shared.peripheral.monitor.TileMonitor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.Consumer;

/** Exposes every computer attached to a monitor, directly or through wired modems. */
@Mixin(value = TileMonitor.class, remap = false)
public interface TileMonitorAccessor {
    @Invoker("eachComputer")
    void vhcctweaks$eachComputer(Consumer<IComputerAccess> action);
}
