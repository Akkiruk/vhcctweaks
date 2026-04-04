package com.vhcctweaks;

import com.vhcctweaks.api.VHCCTweaksAPI;
import com.vhcctweaks.ccvault.CCVaultAPI;
import com.vhcctweaks.ccvault.SessionAuthManager;
import com.vhcctweaks.ccvault.TransactionLedger;
import com.vhcctweaks.ccvault.TransferService;
import com.vhcctweaks.command.CCVaultCommand;
import com.vhcctweaks.config.ModConfig;
import com.vhcctweaks.handler.ComputerInteractionTracker;
import com.vhcctweaks.handler.ComputerPlacementTracker;
import com.vhcctweaks.handler.CraftingLockHandler;
import com.vhcctweaks.handler.VaultProtectionHandler;
import com.vhcctweaks.patcher.VaultConfigPatcher;
import com.vhcctweaks.detail.VaultItemDetailProvider;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.detail.DetailRegistries;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

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
        // Track which player last interacted with each CC computer
        MinecraftForge.EVENT_BUS.register(ComputerInteractionTracker.class);
        // Track which player owns each CC computer (host UUID for CCVault)
        MinecraftForge.EVENT_BUS.register(ComputerPlacementTracker.class);
        // Track CCVault auth grants and approval prompts
        MinecraftForge.EVENT_BUS.register(SessionAuthManager.class);
        // Command registration and server start events
        MinecraftForge.EVENT_BUS.register(VHCCTweaks.class);

        // Patch VH config files early (adds CC entries to blacklists/researches)
        VaultConfigPatcher.patchIfNeeded(FMLPaths.CONFIGDIR.get());
        // Patch CC:Tweaked monitor sizes and sync managed CraftTweaker scripts for both
        // integrated and dedicated-server folder layouts.
        VaultConfigPatcher.patchGameConfigsIfNeeded(FMLPaths.GAMEDIR.get());

        // Data directory for all vhcctweaks persistent storage
        Path dataDir = FMLPaths.GAMEDIR.get().resolve("vhcc_data");

        // Register custom Lua API for CC:Tweaked computers
        // Server-side: vhcc.write/read/etc. sandboxed to <instance>/vhcc_data/
        VHCCTweaksAPI.setRootDir(dataDir);
        ComputerCraftAPI.registerAPIFactory(computer -> new VHCCTweaksAPI(computer.getID()));

        // CCVault economy system init
        SessionAuthManager.init(dataDir);
        ComputerPlacementTracker.init(dataDir);
        TransactionLedger.init(dataDir);
        TransferService.init(dataDir);
        ComputerCraftAPI.registerAPIFactory(computer -> new CCVaultAPI(computer.getID()));

        // Vault item detail provider — enriches getItemDetail() with VH item data
        DetailRegistries.ITEM_STACK.addProvider(new VaultItemDetailProvider());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CCVaultCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // Run WAL crash recovery after server is fully loaded
        TransferService.recover();
        LOGGER.info("CCVault: WAL recovery complete");
    }


}
