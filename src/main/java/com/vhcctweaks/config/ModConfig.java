package com.vhcctweaks.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ModConfig {
    public static final ForgeConfigSpec SERVER_SPEC;

    // Vault protection
    public static final ForgeConfigSpec.BooleanValue BLOCK_CC_IN_VAULT;
    public static final ForgeConfigSpec.ConfigValue<String> VAULT_DIMENSION;

    // Crafty turtle (autocrafting) lock
    public static final ForgeConfigSpec.BooleanValue LOCK_CRAFTY_TURTLES;
    public static final ForgeConfigSpec.ConfigValue<String> AUTOCRAFTING_RESEARCH_NAME;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Vault Dimension Protection").push("vault");
        BLOCK_CC_IN_VAULT = builder
                .comment("Completely block all CC:Tweaked blocks and items inside the Vault dimension.",
                         "This prevents computers, turtles, modems, and all peripherals from functioning in vaults.")
                .define("blockCCInVault", true);
        VAULT_DIMENSION = builder
                .comment("The resource location of the Vault dimension.")
                .define("vaultDimension", "the_vault:vault");
        builder.pop();

        builder.comment("Crafty Turtle Restrictions",
                         "Gates the turtle crafting upgrade behind Vault Hunters research.",
                         "Regular turtles (mining, building, moving) are NOT affected.").push("autocrafting");
        LOCK_CRAFTY_TURTLES = builder
                .comment("If true, the crafting table upgrade on turtles is locked until the player",
                         "has unlocked the required Vault Hunters research (Automatic Genius by default).",
                         "Regular turtles without the crafting upgrade are completely unaffected.")
                .define("lockCraftyTurtles", true);
        AUTOCRAFTING_RESEARCH_NAME = builder
                .comment("The name of the Vault Hunters research that unlocks crafty turtles.",
                         "Must match the research name exactly as it appears in researches.json.")
                .define("autocraftingResearchName", "Automatic Genius");
        builder.pop();

        SERVER_SPEC = builder.build();
    }
}
