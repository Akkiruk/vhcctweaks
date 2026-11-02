package com.vhcctweaks.mixin;

import com.vhcctweaks.link.ComputerIndex;
import dan200.computercraft.shared.computer.core.ServerComputer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps ComputerIndex in step with CC's own registry: a computer is findable by ID from register until unload. */
@Mixin(value = ServerComputer.class, remap = false)
public abstract class ServerComputerIndexMixin {

    @Inject(method = "register", at = @At("RETURN"))
    private void vhcctweaks_indexOnRegister(CallbackInfoReturnable<Integer> cir) {
        ComputerIndex.onRegistered((ServerComputer) (Object) this);
    }

    @Inject(method = "unload", at = @At("HEAD"))
    private void vhcctweaks_unindexOnUnload(CallbackInfo ci) {
        ComputerIndex.onUnloaded((ServerComputer) (Object) this);
    }
}
