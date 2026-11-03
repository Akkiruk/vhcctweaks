package com.vhcctweaks.mixin;

import com.vhcctweaks.link.LinkManager;
import dan200.computercraft.api.network.wired.IWiredNetworkChange;
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemElement;
import com.vhcctweaks.util.Safety;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A wired network gaining or losing a peripheral may change what a linked computer offers. */
@Mixin(value = WiredModemElement.class, remap = false)
public abstract class WiredModemElementMixin {

    @Inject(method = "networkChanged", at = @At("TAIL"))
    private void vhcctweaks_wiredChanged(IWiredNetworkChange change, CallbackInfo ci) {
        Safety.guard("wired network changed", LinkManager::onWiredChanged);
    }
}
