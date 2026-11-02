package com.vhcctweaks.mixin;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.peripheral.modem.wireless.WirelessModemPeripheral;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Set;

/**
 * An ender modem is a "peripheral_hub", so CC's own peripheral API lists and wraps linked peripherals with no ROM
 * changes. CC reads a peripheral's types once when it attaches, so an ender modem always has the type; with no
 * links its hub simply lists nothing. Plain wireless modems are unchanged.
 */
@Mixin(value = WirelessModemPeripheral.class, remap = false)
public abstract class WirelessModemHubTypeMixin implements IPeripheral {

    @Unique
    private static final Set<String> VHCCTWEAKS_HUB = Collections.singleton("peripheral_hub");

    @Nonnull
    @Override
    public Set<String> getAdditionalTypes() {
        return ((WirelessModemPeripheral) (Object) this).isInterdimensional() ? VHCCTWEAKS_HUB : Collections.emptySet();
    }
}
