package com.vhcctweaks.mixin;

import com.vhcctweaks.link.HubMethods;
import com.vhcctweaks.link.LinkManager;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.apis.IAPIEnvironment;
import dan200.computercraft.core.apis.PeripheralAPI;
import dan200.computercraft.core.asm.PeripheralMethod;
import dan200.computercraft.core.computer.ComputerSide;
import dan200.computercraft.shared.peripheral.modem.wireless.WirelessModemPeripheral;
import org.spongepowered.asm.mixin.Final;
import com.vhcctweaks.util.Safety;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Ender modems gain the link and peripheral hub methods. PeripheralAPI.getMethods builds the method table for every
 * attached peripheral, CC's own wrappers and ours, so this is the one place to add them.
 * A computer's side peripherals changing also tells LinkManager, for the computers linked to it.
 */
@Mixin(value = PeripheralAPI.class, remap = false)
public abstract class PeripheralAPIMixin {

    @Shadow @Final private IAPIEnvironment environment;

    // PeripheralAPI also has the Lua function getMethods(String), so name the static one by its descriptor
    @Inject(method = "getMethods(Ldan200/computercraft/api/peripheral/IPeripheral;)Ljava/util/Map;", at = @At("RETURN"))
    private static void vhcctweaks_addHubMethods(IPeripheral peripheral, CallbackInfoReturnable<Map<String, PeripheralMethod>> cir) {
        if (peripheral instanceof WirelessModemPeripheral modem && modem.isInterdimensional()) {
            Safety.guard("ender modem methods", () -> HubMethods.addTo(cir.getReturnValue()));
        }
    }

    @Inject(method = "onPeripheralChanged", at = @At("TAIL"))
    private void vhcctweaks_sidesChanged(ComputerSide side, IPeripheral peripheral, CallbackInfo ci) {
        Safety.guard("linked sides changed", () -> LinkManager.onSidesChanged(environment.getComputerID()));
    }
}
