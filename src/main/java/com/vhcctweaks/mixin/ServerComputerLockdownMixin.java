package com.vhcctweaks.mixin;

import com.vhcctweaks.lockdown.LockdownWritableMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wraps a computer's root writable mount so installed casino programs can mark
 * themselves read-only at the filesystem layer.
 */
@Mixin(targets = "dan200.computercraft.shared.computer.core.ServerComputer", remap = false)
public abstract class ServerComputerLockdownMixin {
    @Shadow public abstract int getID();

    @Inject(method = "createRootMount", at = @At("RETURN"), cancellable = true)
    private void vhcctweaks_wrapRootMount(CallbackInfoReturnable<IWritableMount> cir) {
        IWritableMount mount = cir.getReturnValue();
        if (mount == null) return;
        cir.setReturnValue(LockdownWritableMount.wrapIfNeeded(mount, getID()));
    }
}
