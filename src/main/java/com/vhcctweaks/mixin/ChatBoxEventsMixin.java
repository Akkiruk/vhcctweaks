package com.vhcctweaks.mixin;

import com.vhcctweaks.VHCCTweaks;
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
    private static void vhcctweaks_blockHiddenChatChannel(Object event, CallbackInfo ci) {
        try {
            // Access the message via reflection to check for '$' prefix
            // ServerChatEvent.getMessage() returns the raw string
            java.lang.reflect.Method getMsg = event.getClass().getMethod("getMessage");
            String message = (String) getMsg.invoke(event);
            if (message != null && message.startsWith("$")) {
                // Cancel AP's handler entirely — let the message go through normally
                // (with the $ still visible, treating it as a normal character)
                ci.cancel();
                VHCCTweaks.LOGGER.debug("Blocked AP hidden chat channel ($ prefix) for message");
            }
        } catch (Exception e) {
            // If reflection fails, let AP handle it normally
            VHCCTweaks.LOGGER.debug("ChatBox event mixin reflection error: {}", e.getMessage());
        }
    }

    /**
     * Intercept the onCommand handler (CommandEvent).
     * Same fix for /say commands with '$' prefix.
     */
    @Inject(method = "onCommand", at = @At("HEAD"), cancellable = true)
    private static void vhcctweaks_blockHiddenCommandChannel(Object event, CallbackInfo ci) {
        // For /say commands, we simply prevent AP from canceling them when they have '$'
        // This is a blanket fix — if AP's handler would suppress the command, we block that
        try {
            java.lang.reflect.Method getParseResults = event.getClass().getMethod("getParseResults");
            Object parseResults = getParseResults.invoke(event);
            java.lang.reflect.Method getReader = parseResults.getClass().getMethod("getReader");
            Object reader = getReader.invoke(parseResults);
            String fullCommand = reader.toString();
            if (fullCommand.contains("$")) {
                ci.cancel();
                VHCCTweaks.LOGGER.debug("Blocked AP hidden command channel ($ prefix)");
            }
        } catch (Exception e) {
            // If reflection fails, let AP handle it normally
        }
    }
}
