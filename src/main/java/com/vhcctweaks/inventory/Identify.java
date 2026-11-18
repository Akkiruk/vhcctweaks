package com.vhcctweaks.inventory;

import com.vhcctweaks.util.Safety;
import iskallia.vault.gear.VaultGearState;
import iskallia.vault.gear.item.IdentifiableItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Identifying a Vault Hunters item from a computer (2.2.37), with the call the Identification Stand makes for each
 * piece in a player's inventory: IdentifiableItem.instantIdentify(player, piece). The player matters to the roll:
 * their Legendary expertise adds to the legendary chance, the model is picked from and discovered into their
 * discoveries, a unique joins their wardrobe collection, and stats, advancements and bounty tasks count it.
 *
 * Always works on a copy, which the caller writes back only when it came out identified.
 */
final class Identify {

    private Identify() {}

    /** Why this piece (a copy) can't be identified, or null when it can. */
    static String check(ItemStack piece, ServerPlayer player) {
        try {
            if (!(piece.getItem() instanceof IdentifiableItem item)) return ContainerRules.NOT_IDENTIFIABLE;
            ContainerRules.Piece state = switch (item.getState(piece)) {
                case UNIDENTIFIED -> ContainerRules.Piece.UNIDENTIFIED;
                case ROLLING -> ContainerRules.Piece.ROLLING;
                case IDENTIFIED -> ContainerRules.Piece.IDENTIFIED;
            };
            boolean canIdentify = state != ContainerRules.Piece.UNIDENTIFIED || piece.getCount() > 1 || item.canIdentify(player, piece);
            return ContainerRules.identifyReason(state, piece.getCount(), canIdentify);
        } catch (LinkageError e) {
            return ContainerRules.IDENTIFY_NOT_AVAILABLE;
        } catch (RuntimeException e) {
            Safety.logOnce("identify check", e);
            return ContainerRules.IDENTIFY_NOT_AVAILABLE;
        }
    }

    /** Identifies the piece (a copy that passed check). True when it came out identified. */
    static boolean run(ItemStack piece, ServerPlayer player) {
        try {
            IdentifiableItem item = (IdentifiableItem) piece.getItem();
            item.instantIdentify(player, piece);
            return item.getState(piece) == VaultGearState.IDENTIFIED;
        } catch (LinkageError | RuntimeException e) {
            Safety.logOnce("identify", e);
            return false;
        }
    }
}
