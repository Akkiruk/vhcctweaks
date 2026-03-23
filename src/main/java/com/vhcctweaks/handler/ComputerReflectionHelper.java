package com.vhcctweaks.handler;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared reflection utilities for extracting computerIDs from CC:Tweaked block entities.
 * Caches Method handles by class to avoid repeated class hierarchy scans.
 */
public class ComputerReflectionHelper {

    // Class → resolved method (null value = already scanned, no method found)
    private static final Map<Class<?>, Method> methodCache = new ConcurrentHashMap<>();
    private static final String[] METHOD_NAMES = {"getComputerID", "getID"};

    /**
     * Extract the computer ID from a CC:Tweaked BlockEntity via reflection.
     * Returns -1 if the block entity doesn't expose a computer ID.
     */
    public static int getComputerIdFromBlockEntity(Object blockEntity) {
        if (blockEntity == null) return -1;

        Class<?> clazz = blockEntity.getClass();
        Method cached = methodCache.get(clazz);

        if (cached != null) {
            return invokeMethod(cached, blockEntity);
        }

        // Check if we already scanned this class and found nothing
        if (methodCache.containsKey(clazz)) {
            return -1;
        }

        // Scan for a suitable method
        for (String methodName : METHOD_NAMES) {
            Method m = findDeclaredMethod(clazz, methodName);
            if (m != null) {
                m.setAccessible(true);
                methodCache.put(clazz, m);
                return invokeMethod(m, blockEntity);
            }
        }

        // Mark as scanned with no result
        methodCache.put(clazz, null);
        return -1;
    }

    private static int invokeMethod(Method method, Object target) {
        try {
            Object result = method.invoke(target);
            if (result instanceof Number) {
                return ((Number) result).intValue();
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static Method findDeclaredMethod(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredMethod(name);
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
