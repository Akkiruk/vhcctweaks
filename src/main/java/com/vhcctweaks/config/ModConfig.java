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

    // CCVault economy
    public static final ForgeConfigSpec.BooleanValue CCVAULT_ENABLED;
    public static final ForgeConfigSpec.IntValue CCVAULT_NONCE_EXPIRY_SECONDS;
    public static final ForgeConfigSpec.IntValue CCVAULT_MAX_TRANSFERS_PER_MIN_TERMINAL;
    public static final ForgeConfigSpec.IntValue CCVAULT_MAX_TRANSFERS_PER_MIN_PLAYER;
    public static final ForgeConfigSpec.LongValue CCVAULT_MAX_TRANSFER_AMOUNT;
    public static final ForgeConfigSpec.IntValue CCVAULT_INTERACTION_STALE_SECONDS;
    public static final ForgeConfigSpec.IntValue CCVAULT_ESCROW_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.IntValue CCVAULT_MAX_ESCROWS_PER_COMPUTER;
    public static final ForgeConfigSpec.IntValue CCVAULT_MAX_HISTORY_RESULTS;

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

        builder.comment("CCVault Economy System",
                         "Server-authoritative economy API for ComputerCraft terminals.",
                         "Requires Dog's PlayerShops to be installed for actual token operations.").push("ccvault");
        CCVAULT_ENABLED = builder
                .comment("Enable the CCVault economy API on CC computers.")
                .define("enabled", true);
        CCVAULT_NONCE_EXPIRY_SECONDS = builder
                .comment("How long (in seconds) an auth approval nonce stays valid before expiring.")
                .defineInRange("nonceExpirySeconds", 60, 10, 300);
        CCVAULT_MAX_TRANSFERS_PER_MIN_TERMINAL = builder
                .comment("Maximum transfers per minute per terminal.")
                .defineInRange("maxTransfersPerMinTerminal", 10, 1, 100);
        CCVAULT_MAX_TRANSFERS_PER_MIN_PLAYER = builder
                .comment("Maximum transfers per minute per player.")
                .defineInRange("maxTransfersPerMinPlayer", 20, 1, 200);
        CCVAULT_MAX_TRANSFER_AMOUNT = builder
                .comment("Maximum tokens per single transfer.")
                .defineInRange("maxTransferAmount", 1_000_000L, 1L, Long.MAX_VALUE);
        CCVAULT_INTERACTION_STALE_SECONDS = builder
                .comment("How many seconds before a player interaction with a computer is considered stale.",
                         "Financial operations require a fresh interaction within this window.")
                .defineInRange("interactionStaleSeconds", 30, 5, 300);
        CCVAULT_ESCROW_TIMEOUT_SECONDS = builder
                .comment("How many seconds an escrow hold remains active before auto-refunding.",
                         "If a game crashes mid-bet, held tokens return to the player after this timeout.")
                .defineInRange("escrowTimeoutSeconds", 300, 30, 3600);
        CCVAULT_MAX_ESCROWS_PER_COMPUTER = builder
                .comment("Maximum active escrow holds per computer at once.",
                         "Prevents scripts from locking large amounts of tokens in escrow.")
                .defineInRange("maxEscrowsPerComputer", 5, 1, 20);
        CCVAULT_MAX_HISTORY_RESULTS = builder
                .comment("Maximum number of transaction history entries a script can retrieve at once.")
                .defineInRange("maxHistoryResults", 50, 1, 200);
        builder.pop();

        SERVER_SPEC = builder.build();
    }
}
