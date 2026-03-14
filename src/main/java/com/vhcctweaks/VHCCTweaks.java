package com.vhcctweaks;

import com.vhcctweaks.api.VHCCTweaksAPI;
import com.vhcctweaks.config.ModConfig;
import com.vhcctweaks.handler.ComputerInteractionTracker;
import com.vhcctweaks.handler.CraftingLockHandler;
import com.vhcctweaks.handler.VaultProtectionHandler;
import com.vhcctweaks.network.VHCCNetwork;
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

        // Network channel for client-bound file operations
        VHCCNetwork.register();

        // Vault dimension protection (blocks all CC blocks/items in vaults)
        MinecraftForge.EVENT_BUS.register(VaultProtectionHandler.class);
        // Crafty turtle lock (strips crafting upgrade until research is unlocked)
        MinecraftForge.EVENT_BUS.register(CraftingLockHandler.class);
        // Track which player last interacted with each CC computer
        MinecraftForge.EVENT_BUS.register(ComputerInteractionTracker.class);

        // Patch VH config files early (adds CC entries to blacklists/researches)
        VaultConfigPatcher.patchIfNeeded(FMLPaths.CONFIGDIR.get());

        // Register custom Lua API for CC:Tweaked computers
        // Server-side: vhcc.write/read/etc. sandboxed to <instance>/vhcc_data/
        // Client-side: vhcc.clientWrite/clientAppend send data to the player's local disk
        VHCCTweaksAPI.setRootDir(FMLPaths.GAMEDIR.get().resolve("vhcc_data"));
        ComputerCraftAPI.registerAPIFactory(computer -> new VHCCTweaksAPI(computer.getID()));
    }
}
