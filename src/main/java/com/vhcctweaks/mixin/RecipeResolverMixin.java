package com.vhcctweaks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.resources.ResourceLocation;

/**
 * Suppresses CC:Tweaked's RecipeResolver JEI plugin which dynamically generates
 * "impostor" recipes with vanilla ingredients for JEI display. Without this,
 * JEI shows both the default CC recipes AND the VH-gated CraftTweaker replacements.
 */
@Mixin(targets = "dan200.computercraft.shared.integration.jei.RecipeResolver", remap = false)
public class RecipeResolverMixin {

    @Inject(method = "getRecipeCategoryUids", at = @At("HEAD"), cancellable = true)
    private void vhcctweaks_suppressCategoryUids(IFocus<?> focus, CallbackInfoReturnable<List<ResourceLocation>> cir) {
        cir.setReturnValue(Collections.emptyList());
    }

    @Inject(method = "getRecipes(Lmezz/jei/api/recipe/category/IRecipeCategory;Lmezz/jei/api/recipe/IFocus;)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true)
    private void vhcctweaks_suppressFocusedRecipes(IRecipeCategory<?> category, IFocus<?> focus, CallbackInfoReturnable<List<?>> cir) {
        cir.setReturnValue(Collections.emptyList());
    }

    @Inject(method = "getRecipes(Lmezz/jei/api/recipe/category/IRecipeCategory;)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true)
    private void vhcctweaks_suppressAllRecipes(IRecipeCategory<?> category, CallbackInfoReturnable<List<?>> cir) {
        cir.setReturnValue(Collections.emptyList());
    }
}
