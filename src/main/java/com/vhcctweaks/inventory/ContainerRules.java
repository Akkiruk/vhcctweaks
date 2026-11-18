package com.vhcctweaks.inventory;

import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;

/**
 * The parts of container items and identify that need no game (2.2.37): the reasons, the order they are checked in,
 * when an inventory may be changed, and the insert-first move. ContainerItems and Identify do the game side.
 */
public final class ContainerRules {

    public static final String EMPTY_SLOT = "empty slot";
    public static final String NOT_A_CONTAINER = "not a container";
    public static final String CONTAINERS_OFF = "container items are turned off";
    public static final String CANT_CHANGE = "can't change items in this inventory";

    public static final String IDENTIFY_OFF = "identify is turned off";
    public static final String NOT_IDENTIFIABLE = "not identifiable";
    public static final String ALREADY_IDENTIFIED = "already identified";
    public static final String BEING_IDENTIFIED = "being identified";
    public static final String A_STACK = "can't identify a stack";
    public static final String IDENTIFY_NOT_AVAILABLE = "identify not available";

    /** Shulker boxes hold 27; Vault Hunters counts its wooden barrel as 36 and its other barrels as 45. */
    public static final int SHULKER_SLOTS = 27;
    public static final int WOODEN_BARREL_SLOTS = 36;
    public static final int VAULT_BARREL_SLOTS = 45;

    private ContainerRules() {}

    public static String notOnline(String name) {
        return "player '" + name + "' is not online";
    }

    public static String notApproved(String name) {
        return "player '" + name + "' has not approved this computer";
    }

    /** Null when inner (counted from 1) is a slot of a container of this size. */
    public static String innerOutOfRange(int inner, int size) {
        return inner >= 1 && inner <= size ? null : "inner slot out of range (between 1 and " + size + ")";
    }

    /**
     * Whether a container in an inventory may be changed. The inventory has to let its slots be set, so the block is
     * marked changed through its own handler, and hand out its real items: a backpack is changed through the handler
     * of the real item, which anyone else using that backpack shares. A copy's handler would go stale under them.
     */
    public static boolean writable(boolean slotsCanBeSet, boolean handsOutRealItems) {
        return slotsCanBeSet && handsOutRealItems;
    }

    /** Where a piece stands for identify, as far as the rules care. */
    public enum Piece { UNIDENTIFIED, ROLLING, IDENTIFIED, NOT_IDENTIFIABLE }

    /**
     * Step 5 of identify: why this piece can't be identified, or null when it can. canIdentify is Vault Hunters' own
     * answer for a single piece.
     */
    public static String identifyReason(Piece piece, int count, boolean canIdentify) {
        return switch (piece) {
            case NOT_IDENTIFIABLE -> NOT_IDENTIFIABLE;
            case IDENTIFIED -> ALREADY_IDENTIFIED;
            case ROLLING -> BEING_IDENTIFIED;
            case UNIDENTIFIED -> count > 1 ? A_STACK : canIdentify ? null : NOT_IDENTIFIABLE;
        };
    }

    /**
     * A move out of a container, the way pushItems moves: at most limit, what the slot has and one normal stack go
     * into the target first, and only what the target took leaves the container. insert gets the count to offer and
     * returns how many went in; take gets how many to remove. Returns the count moved.
     */
    public static int moveOut(int available, int limit, int maxStack, IntUnaryOperator insert, IntConsumer take) {
        int want = Math.min(Math.min(available, limit), maxStack);
        if (want <= 0) return 0;
        int inserted = Math.min(want, insert.applyAsInt(want));
        if (inserted <= 0) return 0;
        take.accept(inserted);
        return inserted;
    }
}
