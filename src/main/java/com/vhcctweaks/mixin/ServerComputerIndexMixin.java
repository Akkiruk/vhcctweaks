package com.vhcctweaks.mixin;

import com.vhcctweaks.link.ComputerIndex;
import dan200.computercraft.shared.computer.core.ServerComputer;
import com.vhcctweaks.util.Safety;
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
        ServerComputer self = (ServerComputer) (Object) this;
        Safety.guard("computer index register", () -> ComputerIndex.onRegistered(self));
    }

    @Inject(method = "unload", at = @At("HEAD"))
    private void vhcctweaks_unindexOnUnload(CallbackInfo ci) {
        ServerComputer self = (ServerComputer) (Object) this;
        Safety.guard("computer index unload", () -> ComputerIndex.onUnloaded(self));
    }
}
