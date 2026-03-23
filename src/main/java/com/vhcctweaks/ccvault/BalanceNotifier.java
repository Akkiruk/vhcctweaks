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
 *   <li><b>Bet placed</b>: escrow hold — tokens locked until game resolves</li>
 *   <li><b>Bet returned</b>: escrow refund — tokens returned after win/push/cancel</li>
 *   <li><b>Bet settled</b>: informational — player's escrowed bet went to the house</li>
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

        MutableComponent msg = new TextComponent("")
                .append(new TextComponent("[CCVault] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(new TextComponent("+" + NUM_FMT.format(amount) + " tokens").withStyle(ChatFormatting.GREEN))
                .append(new TextComponent(" | " + reason).withStyle(ChatFormatting.GRAY))
                .append(new TextComponent(fmtBal(newBalance)).withStyle(ChatFormatting.DARK_GRAY));

        player.sendMessage(msg, player.getUUID());
    }

    /**
     * Notify a player that tokens are held in escrow (deducted but not yet awarded).
     * Shows as "Bet placed" for clarity.
     *
     * @param uuid     the affected player's UUID
     * @param amount   positive token amount held
     * @param reason   human-readable reason (e.g. "Blackjack: bet")
     * @param selfPlay true if the player is also the host (testing own machine)
     */
    public static void notifyEscrowHold(UUID uuid, long amount, String reason, boolean selfPlay) {
        ServerPlayer player = getPlayer(uuid);
        if (player == null) return;

        long newBalance = DogBridge.getBalance(uuid);
        String tag = selfPlay ? " [self-play]" : "";

        MutableComponent msg = new TextComponent("")
                .append(new TextComponent("[CCVault] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(new TextComponent("-" + NUM_FMT.format(amount) + " tokens").withStyle(ChatFormatting.RED))
                .append(new TextComponent(" | Bet placed: " + reason + tag).withStyle(ChatFormatting.GRAY))
                .append(new TextComponent(fmtBal(newBalance)).withStyle(ChatFormatting.DARK_GRAY));

        player.sendMessage(msg, player.getUUID());
    }

    /** Backwards-compatible overload (non-self-play). */
    public static void notifyEscrowHold(UUID uuid, long amount, String reason) {
        notifyEscrowHold(uuid, amount, reason, false);
    }

    /**
     * Notify a player that escrowed tokens were returned to them
     * (win, push, cancel, or crash recovery).
     *
     * @param uuid     the affected player's UUID
     * @param amount   positive token amount refunded
     * @param reason   human-readable reason
     * @param selfPlay true if the player is also the host
     */
    public static void notifyEscrowRefund(UUID uuid, long amount, String reason, boolean selfPlay) {
        ServerPlayer player = getPlayer(uuid);
        if (player == null) return;

        long newBalance = DogBridge.getBalance(uuid);
        String tag = selfPlay ? " [self-play]" : "";

        MutableComponent msg = new TextComponent("")
                .append(new TextComponent("[CCVault] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(new TextComponent("+" + NUM_FMT.format(amount) + " tokens").withStyle(ChatFormatting.GREEN))
                .append(new TextComponent(" | Returned: " + reason + tag).withStyle(ChatFormatting.GRAY))
                .append(new TextComponent(fmtBal(newBalance)).withStyle(ChatFormatting.DARK_GRAY));

        player.sendMessage(msg, player.getUUID());
    }

    /** Backwards-compatible overload (non-self-play). */
    public static void notifyEscrowRefund(UUID uuid, long amount, String reason) {
        notifyEscrowRefund(uuid, amount, reason, false);
    }

    /**
     * Informational notification: player's escrowed bet was settled (they lost).
     * No balance change — the escrow was already deducted — but gives closure.
     * Only sent in non-self-play; in self-play the escrow refund handles it.
     *
     * @param uuid   the player who placed the bet
     * @param amount the bet amount that was lost
     * @param reason human-readable reason (e.g. "Blackjack: dealer wins")
     */
    public static void notifyBetSettled(UUID uuid, long amount, String reason) {
        ServerPlayer player = getPlayer(uuid);
        if (player == null) return;

        long newBalance = DogBridge.getBalance(uuid);

        MutableComponent msg = new TextComponent("")
                .append(new TextComponent("[CCVault] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(new TextComponent("Bet settled").withStyle(ChatFormatting.YELLOW))
                .append(new TextComponent(" | " + reason + " (-" + NUM_FMT.format(amount) + ")").withStyle(ChatFormatting.GRAY))
                .append(new TextComponent(fmtBal(newBalance)).withStyle(ChatFormatting.DARK_GRAY));

        player.sendMessage(msg, player.getUUID());
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

        player.sendMessage(msg, player.getUUID());
    }

    private static ServerPlayer getPlayer(UUID uuid) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.getPlayerList().getPlayer(uuid);
    }
}
