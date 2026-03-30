package com.vhcctweaks.mixin.client.playershops;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Pseudo
@Mixin(targets = "com.dog.playershops.gui.BuyerShopScreen", remap = false)
public interface BuyerShopScreenAccessor {

    @Accessor("shopId")
    long vhcctweaks$getShopId();

    @Accessor("shopName")
    String vhcctweaks$getShopName();

    @Accessor("ownerName")
    String vhcctweaks$getOwnerName();

    @Accessor("serverOwned")
    boolean vhcctweaks$isServerOwned();

    @Accessor("listings")
    List<Object> vhcctweaks$getListings();
}