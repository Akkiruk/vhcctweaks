package com.vhcctweaks.link;

import java.util.UUID;

/** The linking rules and remote names, kept free of the game so they can be tested on their own. */
public final class LinkRules {

    public static final String NOT_LOADED = "not loaded";
    public static final String NOT_YOURS = "not your computer";
    public static final String NO_ENDER_MODEM = "no ender modem";
    public static final String OTHER_DIMENSION = "other dimension";
    public static final String TOO_MANY = "too many links";

    private LinkRules() {}

    /**
     * Why a computer may not use another one's peripherals, or null if it may. Checked in the order the reasons are
     * listed in the spec. Both computers need an owner, and the same one.
     */
    public static String refusal(boolean targetLoaded, UUID hostOwner, UUID targetOwner,
                                 boolean targetHasEnderModem, boolean sameDimension) {
        if (!targetLoaded) return NOT_LOADED;
        if (hostOwner == null || targetOwner == null || !hostOwner.equals(targetOwner)) return NOT_YOURS;
        if (!targetHasEnderModem) return NO_ENDER_MODEM;
        if (!sameDimension) return OTHER_DIMENSION;
        return null;
    }

    /** A new link also has to fit under the limit. Linking an existing link again is always fine. */
    public static String refusalForNewLink(boolean alreadyLinked, int linkCount, int maxLinks, String ruleRefusal) {
        if (alreadyLinked) return null;
        if (ruleRefusal != null) return ruleRefusal;
        return linkCount >= maxLinks ? TOO_MANY : null;
    }

    /** "5:left" for computer 5's "left". */
    public static String remoteName(int computerId, String name) {
        return computerId + ":" + name;
    }

    /** The computer ID in a remote name, or -1 when the name is not one: "minecraft:chest_3" is a wired name. */
    public static int computerIdOf(String name) {
        if (name == null) return -1;
        int colon = name.indexOf(':');
        if (colon <= 0 || colon > 10 || colon == name.length() - 1) return -1;
        long id = 0;
        for (int i = 0; i < colon; i++) {
            char c = name.charAt(i);
            if (c < '0' || c > '9') return -1;
            id = id * 10 + (c - '0');
        }
        return id > Integer.MAX_VALUE ? -1 : (int) id;
    }

    /** The remote computer's own name for the peripheral: "left" in "5:left". Only valid when computerIdOf is not -1. */
    public static String localPart(String name) {
        return name.substring(name.indexOf(':') + 1);
    }
}
