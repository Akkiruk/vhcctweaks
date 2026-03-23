package com.vhcctweaks.ccvault;

import com.vhcctweaks.VHCCTweaks;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reflection bridge to Dog's PlayerShops VaultTokenAPI.
 * Caches Method handles on first successful lookup.
 * All methods are no-ops if PlayerShops is not loaded.
 */
public class DogBridge {

    private static final String API_CLASS = "com.dog.playershops.data.VaultTokenAPI";

    private static volatile boolean initialized = false;
    private static volatile boolean available = false;

    // Cached reflection handles
    private static Method getTokens;   // long getTokens(UUID)
    private static Method addTokens;   // void addTokens(UUID, long)
    private static Method removeTokens; // void removeTokens(UUID, long)

    private DogBridge() {}

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> api = Class.forName(API_CLASS);
            getTokens = api.getMethod("getTokens", UUID.class);
            addTokens = api.getMethod("addTokens", UUID.class, long.class);
            removeTokens = api.getMethod("removeTokens", UUID.class, long.class);
            available = true;
            VHCCTweaks.LOGGER.info("CCVault: Dog's VaultTokenAPI found and cached");
        } catch (ClassNotFoundException e) {
            VHCCTweaks.LOGGER.info("CCVault: Dog's PlayerShops not installed — economy features disabled");
        } catch (NoSuchMethodException e) {
            VHCCTweaks.LOGGER.error("CCVault: VaultTokenAPI found but method signatures don't match", e);
        }
    }

    /** Whether Dog's API is available on this server. */
    public static boolean isAvailable() {
        if (!initialized) init();
        return available;
    }

    /** Get the token balance for a UUID. Returns -1 if API unavailable. */
    public static long getBalance(UUID uuid) {
        if (!isAvailable()) return -1;
        try {
            Object result = getTokens.invoke(null, uuid);
            return ((Number) result).longValue();
        } catch (Exception e) {
            VHCCTweaks.LOGGER.error("CCVault: getTokens failed for {}", uuid, e);
            return -1;
        }
    }

    /**
     * Add tokens to a UUID's balance.
     * Returns true on success, false on failure.
     */
    public static boolean add(UUID uuid, long amount) {
        if (!isAvailable()) return false;
        if (amount <= 0) return false;
        try {
            addTokens.invoke(null, uuid, amount);
            return true;
        } catch (Exception e) {
            VHCCTweaks.LOGGER.error("CCVault: addTokens({}, {}) failed", uuid, amount, e);
            return false;
        }
    }

    /**
     * Remove tokens from a UUID's balance.
     * Returns true on success, false on failure.
     */
    public static boolean remove(UUID uuid, long amount) {
        if (!isAvailable()) return false;
        if (amount <= 0) return false;
        try {
            removeTokens.invoke(null, uuid, amount);
            return true;
        } catch (Exception e) {
            VHCCTweaks.LOGGER.error("CCVault: removeTokens({}, {}) failed", uuid, amount, e);
            return false;
        }
    }
}
