package com.vhcctweaks.ccvault;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ensures CCVault player messages are always sent on the main server thread.
 * ComputerCraft Lua APIs often run on worker threads, and sending packets from
 * those threads can trip connection safety checks on dedicated servers.
 */
final class PlayerMessageQueue {

    private PlayerMessageQueue() {}

    static void send(ServerPlayer player, Component message) {
        if (player == null || message == null) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        server.execute(() -> {
            if (player.connection == null || player.isRemoved()) {
                return;
            }
            player.sendMessage(message, player.getUUID());
        });
    }
}
