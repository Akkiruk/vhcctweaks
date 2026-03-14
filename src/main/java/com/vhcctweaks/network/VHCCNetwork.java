package com.vhcctweaks.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class VHCCNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static SimpleChannel channel;

    public static void register() {
        channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation("vhcctweaks", "main"),
                () -> PROTOCOL_VERSION,
                s -> true,  // Client accepts servers without this mod
                s -> true   // Server accepts clients without this mod
        );

        channel.registerMessage(0, ClientFilePacket.class,
                ClientFilePacket::encode,
                ClientFilePacket::decode,
                ClientFilePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendToClient(ServerPlayer player, ClientFilePacket packet) {
        if (channel != null) {
            channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }
}
