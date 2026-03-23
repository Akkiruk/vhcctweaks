package com.vhcctweaks.ccvault;

import com.vhcctweaks.VHCCTweaks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.UUID;

/**
 * Sends chat notifications to players whenever their token balance changes
 * from a CCVault computer transaction.
 */
public class BalanceNotifier {

    private BalanceNotifier() {}

    /**
     * Notify a player that tokens were deducted from their balance.
     *
     * @param uuid   the affected player's UUID
     * @param amount positive token amount that was removed
     * @param reason human-readable reason
     */
    public static void notifyDebit(UUID uuid, long amount, String reason) {
        ServerPlayer player = getPlayer(uuid);
        if (player == null) return;

        long newBalance = DogBridge.getBalance(uuid);
        String balStr = newBalance >= 0 ? " | Balance: " + newBalance : "";

        MutableComponent msg = new TextComponent("")
                .append(new TextComponent("[CCVault] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(new TextComponent("-" + amount + " tokens").withStyle(ChatFormatting.RED))
                .append(new TextComponent(" (" + reason + ")").withStyle(ChatFormatting.GRAY))
                .append(new TextComponent(balStr).withStyle(ChatFormatting.DARK_GRAY));

        player.sendMessage(msg, player.getUUID());
    }

    /**
     * Notify a player that tokens were added to their balance.
     *
     * @param uuid   the affected player's UUID
     * @param amount positive token amount that was added
     * @param reason human-readable reason
     */
    public static void notifyCredit(UUID uuid, long amount, String reason) {
        ServerPlayer player = getPlayer(uuid);
        if (player == null) return;

        long newBalance = DogBridge.getBalance(uuid);
        String balStr = newBalance >= 0 ? " | Balance: " + newBalance : "";

        MutableComponent msg = new TextComponent("")
                .append(new TextComponent("[CCVault] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(new TextComponent("+" + amount + " tokens").withStyle(ChatFormatting.GREEN))
                .append(new TextComponent(" (" + reason + ")").withStyle(ChatFormatting.GRAY))
                .append(new TextComponent(balStr).withStyle(ChatFormatting.DARK_GRAY));

        player.sendMessage(msg, player.getUUID());
    }

    /**
     * Notify a player that tokens are held in escrow (deducted but not yet awarded).
     *
     * @param uuid   the affected player's UUID
     * @param amount positive token amount held
     * @param reason human-readable reason
     */
    public static void notifyEscrowHold(UUID uuid, long amount, String reason) {
        ServerPlayer player = getPlayer(uuid);
        if (player == null) return;

        long newBalance = DogBridge.getBalance(uuid);
        String balStr = newBalance >= 0 ? " | Balance: " + newBalance : "";

        MutableComponent msg = new TextComponent("")
                .append(new TextComponent("[CCVault] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(new TextComponent("-" + amount + " tokens").withStyle(ChatFormatting.RED))
                .append(new TextComponent(" (held: " + reason + ")").withStyle(ChatFormatting.GRAY))
                .append(new TextComponent(balStr).withStyle(ChatFormatting.DARK_GRAY));

        player.sendMessage(msg, player.getUUID());
    }

    /**
     * Notify a player that escrowed tokens were refunded to them.
     *
     * @param uuid   the affected player's UUID
     * @param amount positive token amount refunded
     * @param reason human-readable reason
     */
    public static void notifyEscrowRefund(UUID uuid, long amount, String reason) {
        ServerPlayer player = getPlayer(uuid);
        if (player == null) return;

        long newBalance = DogBridge.getBalance(uuid);
        String balStr = newBalance >= 0 ? " | Balance: " + newBalance : "";

        MutableComponent msg = new TextComponent("")
                .append(new TextComponent("[CCVault] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(new TextComponent("+" + amount + " tokens").withStyle(ChatFormatting.GREEN))
                .append(new TextComponent(" (refund: " + reason + ")").withStyle(ChatFormatting.GRAY))
                .append(new TextComponent(balStr).withStyle(ChatFormatting.DARK_GRAY));

        player.sendMessage(msg, player.getUUID());
    }

    private static ServerPlayer getPlayer(UUID uuid) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.getPlayerList().getPlayer(uuid);
    }
}
