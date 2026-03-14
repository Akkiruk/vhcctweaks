package com.vhcctweaks.mixin;

import com.vhcctweaks.VHCCTweaks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks the isSlimeChunk() Lua function on EnvironmentDetectorPeripheral.
 * This function internally uses the world seed to compute slime chunks.
 * By mapping ~30 slime chunks, tools can reverse-engineer the world seed,
 * revealing all structure locations, ore veins, and vault layouts.
 */
@Mixin(targets = "de.srendi.advancedperipherals.common.addons.computercraft.peripheral.EnvironmentDetectorPeripheral", remap = false)
public class EnvironmentDetectorMixin {

    @Inject(method = "isSlimeChunk", at = @At("HEAD"), cancellable = true)
    private void vhcctweaks_blockSlimeChunkSeedLeak(CallbackInfoReturnable<Boolean> cir) {
        VHCCTweaks.LOGGER.debug("Blocked isSlimeChunk() call — prevents world seed reverse-engineering");
        cir.setReturnValue(false);
    }
}
