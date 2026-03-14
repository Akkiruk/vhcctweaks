package com.vhcctweaks.mixin;

import com.vhcctweaks.VHCCTweaks;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.MethodResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks sendFormattedMessage and sendFormattedMessageToPlayer on ChatBoxPeripheral.
 * These methods accept raw JSON text components that can impersonate server messages,
 * other players, or system notifications — enabling social engineering attacks.
 * Regular sendMessage (with forced prefix) remains available.
 */
@Mixin(targets = "de.srendi.advancedperipherals.common.addons.computercraft.peripheral.ChatBoxPeripheral", remap = false)
public class ChatBoxPeripheralMixin {

    @Inject(method = "sendFormattedMessage", at = @At("HEAD"), cancellable = true)
    private void vhcctweaks_blockFormattedMessage(IArguments arguments, CallbackInfoReturnable<MethodResult> cir) {
        VHCCTweaks.LOGGER.debug("Blocked ChatBox sendFormattedMessage — prevents message spoofing");
        cir.setReturnValue(MethodResult.of(null, "sendFormattedMessage is disabled on this server for security"));
    }

    @Inject(method = "sendFormattedMessageToPlayer", at = @At("HEAD"), cancellable = true)
    private void vhcctweaks_blockFormattedMessageToPlayer(IArguments arguments, CallbackInfoReturnable<MethodResult> cir) {
        VHCCTweaks.LOGGER.debug("Blocked ChatBox sendFormattedMessageToPlayer — prevents targeted message spoofing");
        cir.setReturnValue(MethodResult.of(null, "sendFormattedMessageToPlayer is disabled on this server for security"));
    }
}
