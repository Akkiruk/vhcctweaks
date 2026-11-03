package com.vhcctweaks;

import com.vhcctweaks.api.VHCCTweaksAPI;
import com.vhcctweaks.ccvault.CCVaultAPI;
import com.vhcctweaks.ccvault.SessionAuthManager;
import com.vhcctweaks.ccvault.TransactionLedger;
import com.vhcctweaks.ccvault.TransferService;
import com.vhcctweaks.command.CCVaultCommand;
import com.vhcctweaks.command.RemoteCommand;
import com.vhcctweaks.config.ModConfig;
import com.vhcctweaks.handler.ComputerInteractionTracker;
import com.vhcctweaks.handler.ComputerLock;
import com.vhcctweaks.handler.ComputerPlacementTracker;
import com.vhcctweaks.handler.VaultProtectionHandler;
import com.vhcctweaks.inventory.InventoryExtras;
import com.vhcctweaks.inventory.InventoryWatch;
import com.vhcctweaks.link.ComputerIndex;
import com.vhcctweaks.link.LinkManager;
import com.vhcctweaks.patcher.VaultConfigPatcher;
import com.vhcctweaks.remote.RemoteControl;
import com.vhcctweaks.detail.VaultItemDetailProvider;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.detail.DetailRegistries;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod(VHCCTweaks.MOD_ID)
public class VHCCTweaks {
    public static final String MOD_ID = "vhcctweaks";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public VHCCTweaks() {
        ModLoadingContext.get().registerConfig(Type.SERVER, ModConfig.SERVER_SPEC);

        // Vault dimension protection (blocks all CC blocks/items in vaults)
        MinecraftForge.EVENT_BUS.register(VaultProtectionHandler.class);
        // Track which player last interacted with each CC computer
        MinecraftForge.EVENT_BUS.register(ComputerInteractionTracker.class);
        // Track which player owns each CC computer (host UUID for CCVault)
        MinecraftForge.EVENT_BUS.register(ComputerPlacementTracker.class);
        // Refuse locked computers to everyone but their owner
        MinecraftForge.EVENT_BUS.register(ComputerLock.class);
        // Track CCVault auth grants and approval prompts
        MinecraftForge.EVENT_BUS.register(SessionAuthManager.class);
        // Command registration and server start events
        MinecraftForge.EVENT_BUS.register(VHCCTweaks.class);
        // Linked ender modems: load, unload and peripheral changes of linked computers
        MinecraftForge.EVENT_BUS.register(LinkManager.class);
        // inventory_changed: one event per watched inventory per computer per tick
        MinecraftForge.EVENT_BUS.register(InventoryWatch.class);
        // Who is on this server, kept for lookups from computer threads
        MinecraftForge.EVENT_BUS.register(com.vhcctweaks.util.Safety.class);

        // Patch VH config files early (adds blacklist entries and tunes Advanced Peripherals)
        VaultConfigPatcher.patchIfNeeded(FMLPaths.CONFIGDIR.get());
        // Patch CC:Tweaked monitor sizes and sync managed CraftTweaker scripts for both
        // integrated and dedicated-server folder layouts.
        VaultConfigPatcher.patchGameConfigsIfNeeded(FMLPaths.GAMEDIR.get());

        // Register custom Lua APIs for CC:Tweaked computers
        ComputerCraftAPI.registerAPIFactory(computer -> new VHCCTweaksAPI(computer.getID()));
        ComputerCraftAPI.registerAPIFactory(computer -> new CCVaultAPI(computer.getID()));

        // Vault item detail provider — enriches getItemDetail() with VH item data
        DetailRegistries.ITEM_STACK.addProvider(new VaultItemDetailProvider());

        // listDetailed, moveMany, watch and unwatch on every inventory peripheral
        ComputerCraftAPI.registerGenericSource(new InventoryExtras());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CCVaultCommand.register(event.getDispatcher());
        RemoteCommand.register(event.getDispatcher());
    }

    /** Computer IDs belong to a world, so everything keyed by them is stored with it and loaded fresh for it. */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        Path dataDir = event.getServer().getWorldPath(LevelResource.ROOT).resolve("vhcc_data").toAbsolutePath().normalize();
        adoptInstanceData(FMLPaths.GAMEDIR.get().resolve("vhcc_data").toAbsolutePath().normalize(), dataDir);

        VHCCTweaksAPI.setRootDir(dataDir);
        SessionAuthManager.reset();
        ComputerInteractionTracker.reset();
        ComputerIndex.reset();
        com.vhcctweaks.util.Safety.resetPlayers();
        LinkManager.reset();
        InventoryWatch.reset();
        ComputerPlacementTracker.init(dataDir);
        ComputerLock.init(dataDir);
        TransactionLedger.init(dataDir);
        TransferService.init(dataDir);
        RemoteControl.init(dataDir);
    }

    /** Versions before 2.2.35 kept one vhcc_data per instance. The first world started after updating takes it over. */
    private static void adoptInstanceData(Path instanceData, Path worldData) {
        if (!Files.isDirectory(instanceData) || Files.exists(worldData)) return;
        try {
            Files.createDirectories(worldData.getParent());
            Files.move(instanceData, worldData);
            LOGGER.info("Moved {} to {}", instanceData, worldData);
        } catch (IOException e) {
            LOGGER.error("Could not move {} to {}: this world starts with no owners, locks or ledger", instanceData, worldData, e);
        }
    }
}
