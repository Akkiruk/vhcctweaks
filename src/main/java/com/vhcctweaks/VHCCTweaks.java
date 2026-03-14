package com.vhcctweaks;

import com.vhcctweaks.api.VHCCTweaksAPI;
import com.vhcctweaks.config.ModConfig;
import com.vhcctweaks.handler.CraftingLockHandler;
import com.vhcctweaks.handler.VaultProtectionHandler;
import com.vhcctweaks.patcher.VaultConfigPatcher;
import dan200.computercraft.api.ComputerCraftAPI;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(VHCCTweaks.MOD_ID)
public class VHCCTweaks {
    public static final String MOD_ID = "vhcctweaks";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public VHCCTweaks() {
        ModLoadingContext.get().registerConfig(Type.SERVER, ModConfig.SERVER_SPEC);

        // Vault dimension protection (blocks all CC blocks/items in vaults)
        MinecraftForge.EVENT_BUS.register(VaultProtectionHandler.class);
        // Crafty turtle lock (strips crafting upgrade until research is unlocked)
        MinecraftForge.EVENT_BUS.register(CraftingLockHandler.class);

        // Patch VH config files early (adds CC entries to blacklists/researches)
        VaultConfigPatcher.patchIfNeeded(FMLPaths.CONFIGDIR.get());

        // Register custom Lua API for CC:Tweaked computers
        // Saves test results to <instance>/vhcctweaks_test_results/
        VHCCTweaksAPI.setOutputDir(FMLPaths.GAMEDIR.get().resolve("vhcctweaks_test_results"));
        ComputerCraftAPI.registerAPIFactory(computer -> new VHCCTweaksAPI());
    }
}
