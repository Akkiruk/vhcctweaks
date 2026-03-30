package com.vhcctweaks.mixin.client.playershops;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Pseudo
@Mixin(targets = "com.dog.playershops.gui.ShopBrowserScreen", remap = false)
public interface ShopBrowserScreenAccessor {

    @Accessor("shops")
    List<Object> vhcctweaks$getShops();

    @Accessor("currentPage")
    int vhcctweaks$getCurrentPage();

    @Accessor("totalPages")
    int vhcctweaks$getTotalPages();
}