package com.vhcctweaks.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ModConfig {
    public static final ForgeConfigSpec SERVER_SPEC;

    // Vault protection
    public static final ForgeConfigSpec.BooleanValue BLOCK_CC_IN_VAULT;
    public static final ForgeConfigSpec.ConfigValue<String> VAULT_DIMENSION;

    // CCVault economy
    public static final ForgeConfigSpec.BooleanValue CCVAULT_ENABLED;
    public static final ForgeConfigSpec.IntValue CCVAULT_NONCE_EXPIRY_SECONDS;
    public static final ForgeConfigSpec.IntValue CCVAULT_AUTH_IDLE_TIMEOUT_MINUTES;
    public static final ForgeConfigSpec.LongValue CCVAULT_MAX_TRANSFER_AMOUNT;
    public static final ForgeConfigSpec.IntValue CCVAULT_INTERACTION_STALE_SECONDS;
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

        builder.comment("CCVault Economy System",
                         "Server-authoritative economy API for ComputerCraft terminals.",
                         "Requires Dog's PlayerShops to be installed for actual token operations.").push("ccvault");
        CCVAULT_ENABLED = builder
                .comment("Enable the CCVault economy API on CC computers.")
                .define("enabled", true);
        CCVAULT_NONCE_EXPIRY_SECONDS = builder
                .comment("How long (in seconds) an auth approval nonce stays valid before expiring.")
                .defineInRange("nonceExpirySeconds", 60, 10, 300);
        CCVAULT_AUTH_IDLE_TIMEOUT_MINUTES = builder
                .comment("How long an approved CCVault terminal authorization survives without activity.",
                         "Authorization is also cleared immediately when the player disconnects.")
                .defineInRange("authIdleTimeoutMinutes", 10, 1, 240);
        CCVAULT_MAX_TRANSFER_AMOUNT = builder
                .comment("Maximum tokens per single transfer.")
                .defineInRange("maxTransferAmount", 1_000_000L, 1L, Long.MAX_VALUE);
        CCVAULT_INTERACTION_STALE_SECONDS = builder
                .comment("How many seconds before a player interaction with a computer is considered stale.",
                         "Financial operations require a fresh interaction within this window.")
                .defineInRange("interactionStaleSeconds", 30, 5, 300);
        CCVAULT_MAX_HISTORY_RESULTS = builder
                .comment("Maximum number of transaction history entries a script can retrieve at once.")
                .defineInRange("maxHistoryResults", 50, 1, 200);
        builder.pop();

        SERVER_SPEC = builder.build();
    }
}
