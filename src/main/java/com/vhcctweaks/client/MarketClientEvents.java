package com.vhcctweaks.client;

import com.mojang.brigadier.CommandDispatcher;
import com.vhcctweaks.VHCCTweaks;
import com.vhcctweaks.client.market.PlayerShopsMarketService;
import com.vhcctweaks.client.market.UniversalMarketScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenOpenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VHCCTweaks.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MarketClientEvents {

    private MarketClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        registerMarketCommand(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onScreenOpen(ScreenOpenEvent event) {
        Screen screen = event.getScreen();
        PlayerShopsMarketService service = PlayerShopsMarketService.getInstance();
        if (!service.shouldIntercept(screen)) {
            return;
        }

        service.captureInterceptedScreen(screen);
        event.setCanceled(true);
    }

    private static void registerMarketCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("market")
                .executes(ctx -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.player == null) {
                        return 0;
                    }

                    if (!PlayerShopsMarketService.getInstance().isPlayerShopsAvailable()) {
                        minecraft.player.displayClientMessage(
                                new TextComponent("PlayerShops is not installed on this client, so /market is unavailable."),
                                false
                        );
                        return 0;
                    }

                    UniversalMarketScreen screen = new UniversalMarketScreen();
                    minecraft.setScreen(screen);
                    PlayerShopsMarketService.getInstance().openMarket(screen);
                    return 1;
                }));
    }
}