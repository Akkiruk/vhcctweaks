package com.vhcctweaks.mixin;

import com.vhcctweaks.detail.VaultItemDetailProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Reading a Vault Hunters item's details must never change the item. Vault Hunters writes cache tags into the stack
 * it is asked about (its name and gear-cache code fill in what is missing, with no setChanged), so after its first
 * getItemDetail a piece's CC nbt hash could change, and programs that track items by that hash would lose them.
 *
 * Every provider, CC's own included, is handed a copy of a Vault item instead of the live stack. The nbt hash is taken
 * from the copy before anything runs, so it is exactly the live item's hash. Other items are not copied.
 */
@Mixin(targets = "dan200.computercraft.impl.detail.DetailRegistryImpl", remap = false)
public abstract class DetailRegistryCopyMixin {

    @ModifyVariable(method = "getDetails", at = @At("HEAD"), argsOnly = true)
    private Object vhcctweaks_detailsFromACopy(Object object) {
        return VaultItemDetailProvider.copyIfVault(object);
    }
}
