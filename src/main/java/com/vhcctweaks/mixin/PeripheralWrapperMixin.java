package com.vhcctweaks.mixin;

import com.vhcctweaks.link.LinkManager;
import com.vhcctweaks.util.Safety;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A peripheral on one of a computer's own sides can name that computer's linked peripherals,
 * so a local chest can pushItems into "5:left".
 */
@Mixin(targets = "dan200.computercraft.core.apis.PeripheralAPI$PeripheralWrapper", remap = false)
public abstract class PeripheralWrapperMixin {

    @Inject(method = "getAvailablePeripheral", at = @At("RETURN"), cancellable = true)
    private void vhcctweaks_findLinked(String name, CallbackInfoReturnable<IPeripheral> cir) {
        if (cir.getReturnValue() != null) return;
        try {
            IPeripheral remote = LinkManager.findRemote(((IComputerAccess) this).getID(), name);
            if (remote != null) cir.setReturnValue(remote);
        } catch (RuntimeException e) {
            Safety.logOnce("linked peripheral lookup", e);
        }
    }

    @Inject(method = "getAvailablePeripherals", at = @At("RETURN"), cancellable = true)
    private void vhcctweaks_listLinked(CallbackInfoReturnable<Map<String, IPeripheral>> cir) {
        try {
            int id = ((IComputerAccess) this).getID();
            if (!LinkManager.hasRemotes(id)) return;
            Map<String, IPeripheral> all = new HashMap<>(cir.getReturnValue());
            LinkManager.addRemotes(id, all);
            cir.setReturnValue(Collections.unmodifiableMap(all));
        } catch (RuntimeException e) {
            Safety.logOnce("linked peripheral list", e);
        }
    }
}
