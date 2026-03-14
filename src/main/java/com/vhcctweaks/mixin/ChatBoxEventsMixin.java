package com.vhcctweaks.mixin;

import com.vhcctweaks.VHCCTweaks;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes the hidden '$' message channel from AP's chat event handlers.
 * Without this fix, any message starting with '$' is suppressed from all players
 * but still captured by ChatBox peripherals — creating a covert communication
 * channel invisible to admins and chat logs.
 *
 * This mixin cancels the event handlers entirely so they re-fire without the
 * '$' suppression. AP's ChatBox will still receive normal (non-hidden) messages.
 */
@Mixin(targets = "de.srendi.advancedperipherals.common.events.Events", remap = false)
public class ChatBoxEventsMixin {

    /**
     * Intercept the onChatBox handler (ServerChatEvent).
     * If the message starts with '$', we prevent AP from canceling the event
     * and stripping the '$'. The message goes through normally as plain text.
     */
    @Inject(method = "onChatBox", at = @At("HEAD"), cancellable = true)
    private static void vhcctweaks_blockHiddenChatChannel(ServerChatEvent event, CallbackInfo ci) {
        try {
            String message = event.getMessage();
            if (message != null && message.startsWith("$")) {
                ci.cancel();
                VHCCTweaks.LOGGER.debug("Blocked AP hidden chat channel ($ prefix) for message");
            }
        } catch (Exception e) {
            VHCCTweaks.LOGGER.debug("ChatBox event mixin error: {}", e.getMessage());
        }
    }

    /**
     * Intercept the onCommand handler (CommandEvent).
     * Blocks AP from suppressing /say commands where the message starts with '$'.
     * Only targets "say $..." to avoid interfering with unrelated commands.
     */
    @Inject(method = "onCommand", at = @At("HEAD"), cancellable = true)
    private static void vhcctweaks_blockHiddenCommandChannel(CommandEvent event, CallbackInfo ci) {
        try {
            String fullCommand = event.getParseResults().getReader().getString();
            if (fullCommand.matches("(?i)^/?say\\s+\\$.*")) {
                ci.cancel();
                VHCCTweaks.LOGGER.debug("Blocked AP hidden command channel (say $ prefix)");
            }
        } catch (Exception e) {
            // If something fails, let AP handle it normally
        }
    }
}
