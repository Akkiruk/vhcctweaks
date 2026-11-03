package com.vhcctweaks.mixin;

import com.vhcctweaks.link.LinkManager;
import com.vhcctweaks.util.Safety;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A peripheral on a computer's wired network can name that computer's linked peripherals too. */
@Mixin(targets = "dan200.computercraft.shared.peripheral.modem.wired.WiredModemPeripheral$RemotePeripheralWrapper", remap = false)
public abstract class WiredRemoteWrapperMixin {

    @Inject(method = "getAvailablePeripheral", at = @At("RETURN"), cancellable = true)
    private void vhcctweaks_findLinked(String name, CallbackInfoReturnable<IPeripheral> cir) {
        if (cir.getReturnValue() != null) return;
        try {
            IPeripheral remote = LinkManager.findRemote(((IComputerAccess) this).getID(), name);
            if (remote != null) cir.setReturnValue(remote);
        } catch (RuntimeException e) {
            Safety.logOnce("linked peripheral lookup (wired)", e);
        }
    }
}
