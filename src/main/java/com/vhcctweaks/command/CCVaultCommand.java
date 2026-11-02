package com.vhcctweaks.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.vhcctweaks.ccvault.SessionAuthManager;
import com.vhcctweaks.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers /ccvault commands:
 *   /ccvault approve <nonce>    — Approve a pending auth or host claim request (clickable from chat)
 *   /ccvault revoke <computerId> — Revoke your session and allowance with a terminal
 *   /ccvault revoke              — Revoke every session and allowance you have given
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
                        .executes(ctx -> handleRevokeAll(ctx.getSource().getPlayerOrException()))
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
        SessionAuthManager.Approval approval = SessionAuthManager.approveNonce(player.getUUID(), nonce);
        // Someone else claimed it first: the offer is simply over
        if (approval == SessionAuthManager.Approval.TAKEN) return 0;

        String text = switch (approval) {
            case SESSION -> "Terminal authorized until you disconnect or go "
                    + ModConfig.CCVAULT_AUTH_IDLE_TIMEOUT_MINUTES.get() + " minutes idle.";
            case HOST -> "You are now this computer's host.";
            case ALLOWANCE -> "Allowance approved. It ends when it runs out, when its time is up, when you disconnect, "
                    + "or with /ccvault revoke.";
            default -> "Invalid or expired approval code.";
        };
        boolean approved = approval != SessionAuthManager.Approval.INVALID;
        player.sendMessage(
                new TextComponent("[CCVault] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(new TextComponent(text).withStyle(approved ? ChatFormatting.GREEN : ChatFormatting.RED)),
                player.getUUID());
        return approved ? 1 : 0;
    }

    private static int handleRevoke(ServerPlayer player, int computerId) {
        SessionAuthManager.revokeSession(player.getUUID(), computerId);
        player.sendMessage(
                new TextComponent("[CCVault] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(new TextComponent("Session and allowance revoked for computer #" + computerId + ".").withStyle(ChatFormatting.YELLOW)),
                player.getUUID());
        return 1;
    }

    private static int handleRevokeAll(ServerPlayer player) {
        SessionAuthManager.revokeAll(player.getUUID());
        player.sendMessage(
                new TextComponent("[CCVault] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(new TextComponent("Every session and allowance you gave has been revoked.").withStyle(ChatFormatting.YELLOW)),
                player.getUUID());
        return 1;
    }
}
