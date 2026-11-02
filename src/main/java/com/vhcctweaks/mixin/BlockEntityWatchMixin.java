package com.vhcctweaks.mixin;

import com.vhcctweaks.inventory.InventoryWatch;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds inventory_changed. setChanged is called on every change to a block's contents, by players, hoppers and
 * computers alike, and very often: with nothing watched this costs one static volatile read.
 *
 * The mod jar has no refmap, so these are the 1.18.2 SRG names Forge uses at runtime:
 * m_6596_ is setChanged() and m_7651_ is setRemoved().
 */
@Mixin(value = BlockEntity.class, remap = false)
public abstract class BlockEntityWatchMixin {

    @Inject(method = "m_6596_()V", at = @At("HEAD"))
    private void vhcctweaks_contentsChanged(CallbackInfo ci) {
        if (InventoryWatch.active) InventoryWatch.onChanged(this);
    }

    @Inject(method = "m_7651_()V", at = @At("HEAD"))
    private void vhcctweaks_removed(CallbackInfo ci) {
        if (InventoryWatch.active) InventoryWatch.onRemoved(this);
    }
}
