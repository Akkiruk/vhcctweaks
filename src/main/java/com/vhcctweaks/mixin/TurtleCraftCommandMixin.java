package com.vhcctweaks.mixin;

import com.vhcctweaks.config.ModConfig;
import com.vhcctweaks.handler.CraftingLockHandler;
import com.mojang.authlib.GameProfile;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Directly blocks turtle.craft() at the CC:Tweaked level before any crafting occurs.
 * This is the primary enforcement — CraftingLockHandler's event-based approach is backup.
 */
@Mixin(targets = "dan200.computercraft.shared.turtle.core.TurtleCraftCommand", remap = false)
public class TurtleCraftCommandMixin {

    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private void vhcctweaks_blockCraftWithoutResearch(ITurtleAccess turtle, CallbackInfoReturnable<TurtleCommandResult> cir) {
        if (!ModConfig.LOCK_CRAFTY_TURTLES.get()) return;

        GameProfile owner = turtle.getOwningPlayer();
        if (owner == null) {
            cir.setReturnValue(TurtleCommandResult.failure("No owner found"));
            return;
        }

        UUID ownerUUID = owner.getId();
        Level level = turtle.getLevel();

        if (!CraftingLockHandler.hasResearchByUUID(ownerUUID, level)) {
            String researchName = ModConfig.AUTOCRAFTING_RESEARCH_NAME.get();
            cir.setReturnValue(TurtleCommandResult.failure(
                    "Requires the " + researchName + " research to use turtle.craft()"));
        }
    }
}
