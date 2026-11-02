package com.vhcctweaks.ccvault;

/** What ccvault.getPlayerVaultLevel answers. Kept apart from VaultLevels, which needs Vault Hunters loaded. */
final class VaultLevelReply {

    static final String UNKNOWN_PLAYER = "unknown player";
    static final String NO_DATA = "no vault data";

    private VaultLevelReply() {}

    /** Given the name lookup and the level read (null when Vault Hunters has none). */
    static Object[] answer(PlayerLookup.Status status, String playerName, Integer level) {
        return switch (status) {
            case PENDING -> new Object[]{null, "looking up player '" + playerName + "' - try again"};
            case UNKNOWN -> new Object[]{null, UNKNOWN_PLAYER};
            case FOUND -> level == null ? new Object[]{null, NO_DATA} : new Object[]{level};
        };
    }
}
