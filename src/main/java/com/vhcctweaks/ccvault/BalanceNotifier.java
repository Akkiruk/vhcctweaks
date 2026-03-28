package com.vhcctweaks.ccvault;

import com.vhcctweaks.VHCCTweaks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Sends chat notifications to players whenever their token balance changes
 * from a CCVault computer transaction.
 *
 * <p>Notification categories:
 * <ul>
 *   <li><b>Debit</b>: tokens removed (transfers to another player)</li>
 *   <li><b>Credit</b>: tokens added (transfers from another player)</li>
 * </ul>
 *
 * <p>Self-play (player == host) handling:
 * <ul>
 *   <li>No real money moves — all operations are simulated</li>
 *   <li>Chat messages describe what WOULD happen, tagged with [Self-Play]</li>
 *   <li>Balance is never modified, preventing duplication/loss bugs</li>
 * </ul>
 */
public class BalanceNotifier {

    private static final NumberFormat NUM_FMT = NumberFormat.getIntegerInstance(Locale.US);

    private BalanceNotifier() {}

    private static String fmtBal(long balance) {
        return balance >= 0 ? " | Bal: " + NUM_FMT.format(balance) : "";
    }

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

        MutableComponent msg = new TextComponent("")
                .append(new TextComponent("[CCVault] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(new TextComponent("-" + NUM_FMT.format(amount) + " tokens").withStyle(ChatFormatting.RED))
                .append(new TextComponent(" | " + reason).withStyle(ChatFormatting.GRAY))
                .append(new TextComponent(fmtBal(newBalance)).withStyle(ChatFormatting.DARK_GRAY));

        PlayerMessageQueue.send(player, msg);
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

        MutableComponent msg = new TextComponent("")
                .append(new TextComponent("[CCVault] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(new TextComponent("+" + NUM_FMT.format(amount) + " tokens").withStyle(ChatFormatting.GREEN))
                .append(new TextComponent(" | " + reason).withStyle(ChatFormatting.GRAY))
                .append(new TextComponent(fmtBal(newBalance)).withStyle(ChatFormatting.DARK_GRAY));

        PlayerMessageQueue.send(player, msg);
    }

    /**
     * Self-play simulation notification. No balance was actually changed.
     * Shows what WOULD have happened if this were a real transaction.
     *
     * @param uuid        the player's UUID
     * @param description human-readable description of the simulated action
     */
    public static void notifySelfPlay(UUID uuid, String description) {
        ServerPlayer player = getPlayer(uuid);
        if (player == null) return;

        long balance = DogBridge.getBalance(uuid);

        MutableComponent msg = new TextComponent("")
                .append(new TextComponent("[CCVault] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(new TextComponent("[Self-Play] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(new TextComponent(description).withStyle(ChatFormatting.YELLOW))
                .append(new TextComponent(fmtBal(balance)).withStyle(ChatFormatting.DARK_GRAY));

        PlayerMessageQueue.send(player, msg);
    }

    private static ServerPlayer getPlayer(UUID uuid) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.getPlayerList().getPlayer(uuid);
    }
}
