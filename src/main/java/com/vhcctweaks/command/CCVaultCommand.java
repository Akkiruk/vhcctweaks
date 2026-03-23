package com.vhcctweaks.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.vhcctweaks.ccvault.SessionAuthManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers /ccvault commands:
 *   /ccvault approve <nonce>    — Approve a pending auth request (clickable from chat)
 *   /ccvault revoke <computerId> — Revoke your session with a terminal
 */
public class CCVaultCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ccvault")
                .then(Commands.literal("approve")
                        .then(Commands.argument("nonce", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String nonce = StringArgumentType.getString(ctx, "nonce");
                                    return handleApprove(player, nonce);
                                })
                        )
                )
                .then(Commands.literal("revoke")
                        .then(Commands.argument("computerId", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    int computerId = IntegerArgumentType.getInteger(ctx, "computerId");
                                    return handleRevoke(player, computerId);
                                })
                        )
                )
        );
    }

    private static int handleApprove(ServerPlayer player, String nonce) {
        if (SessionAuthManager.approveNonce(player.getUUID(), nonce)) {
            player.sendMessage(
                    new TextComponent("[CCVault] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                            .append(new TextComponent("Terminal authorized for this session.").withStyle(ChatFormatting.GREEN)),
                    player.getUUID());
            return 1;
        } else {
            player.sendMessage(
                    new TextComponent("[CCVault] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                            .append(new TextComponent("Invalid or expired approval code.").withStyle(ChatFormatting.RED)),
                    player.getUUID());
            return 0;
        }
    }

    private static int handleRevoke(ServerPlayer player, int computerId) {
        SessionAuthManager.revokeSession(player.getUUID(), computerId);
        player.sendMessage(
                new TextComponent("[CCVault] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(new TextComponent("Session revoked for computer #" + computerId + ".").withStyle(ChatFormatting.YELLOW)),
                player.getUUID());
        return 1;
    }
}
