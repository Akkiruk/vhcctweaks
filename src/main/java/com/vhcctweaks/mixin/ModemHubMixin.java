package com.vhcctweaks.mixin;

import com.vhcctweaks.link.LinkManager;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.shared.peripheral.modem.ModemPeripheral;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An ender modem leaving a computer ends its links, the way a wired modem detaching drops its remote peripherals. */
@Mixin(value = ModemPeripheral.class, remap = false)
public abstract class ModemHubMixin {

    @Inject(method = "detach", at = @At("HEAD"))
    private void vhcctweaks_endLinks(IComputerAccess computer, CallbackInfo ci) {
        LinkManager.onHubDetached(computer);
    }
}
