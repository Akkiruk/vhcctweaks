package com.vhcctweaks.ccvault;

import com.vhcctweaks.VHCCTweaks;
import com.vhcctweaks.config.ModConfig;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reflection bridge to the ParadiseCore token economy, which owns every balance on the server.
 * Reflection keeps ParadiseCore out of this repo and out of the compile classpath.
 * All methods fail closed if ParadiseCore is missing or its storage is down.
 */
public class EconomyBridge {

    private static final String API_CLASS = "com.dog.paradisecore.economy.Economy";

    private static volatile boolean initialized = false;
    private static volatile boolean available = false;

    // Cached reflection handles
    private static Method isAvailable;  // boolean isAvailable()
    private static Method getBalance;   // long getBalance(UUID)
    private static Method transfer;     // boolean transfer(UUID, UUID, long)

    private EconomyBridge() {}

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> api = Class.forName(API_CLASS);
            isAvailable = api.getMethod("isAvailable");
            getBalance = api.getMethod("getBalance", UUID.class);
            transfer = api.getMethod("transfer", UUID.class, UUID.class, long.class);
            available = true;
            VHCCTweaks.LOGGER.info("CCVault: ParadiseCore economy found and cached");
        } catch (ClassNotFoundException e) {
            VHCCTweaks.LOGGER.info("CCVault: ParadiseCore not installed — economy features disabled");
        } catch (NoSuchMethodException e) {
            VHCCTweaks.LOGGER.error("CCVault: ParadiseCore economy found but method signatures don't match", e);
        }
    }

    /** Whether the economy can be used right now. */
    public static boolean isAvailable() {
        if (!ModConfig.CCVAULT_ENABLED.get()) return false;
        if (!initialized) init();
        if (!available) {
            return false;
        }
        try {
            Object result = isAvailable.invoke(null);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            VHCCTweaks.LOGGER.error("CCVault: could not verify economy state", e);
            return false;
        }
    }

    /** Get the token balance for a UUID. Returns -1 if the economy is unavailable. */
    public static long getBalance(UUID uuid) {
        if (!isAvailable()) return -1;
        try {
            return ((Number) getBalance.invoke(null, uuid)).longValue();
        } catch (Exception e) {
            VHCCTweaks.LOGGER.error("CCVault: getBalance failed for {}", uuid, e);
            return -1;
        }
    }

    /** Move tokens as one transaction: either both balances change or neither does. */
    public static boolean transfer(UUID from, UUID to, long amount) {
        if (!isAvailable() || amount <= 0) return false;
        try {
            return (Boolean) transfer.invoke(null, from, to, amount);
        } catch (Exception e) {
            VHCCTweaks.LOGGER.error("CCVault: transfer({}, {}, {}) failed", from, to, amount, e);
            return false;
        }
    }
}
