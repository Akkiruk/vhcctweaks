package com.vhcctweaks.handler;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared reflection utilities for extracting computerIDs from CC:Tweaked block entities.
 * Caches Method handles by class to avoid repeated class hierarchy scans.
 * Uses Optional because ConcurrentHashMap does not permit null values.
 */
public class ComputerReflectionHelper {

    // Class → resolved method (empty Optional = already scanned, no method found)
    private static final Map<Class<?>, Optional<Method>> methodCache = new ConcurrentHashMap<>();
    private static final String[] METHOD_NAMES = {"getComputerID", "getID"};

    /**
     * Extract the computer ID from a CC:Tweaked BlockEntity via reflection.
     * Returns -1 if the block entity doesn't expose a computer ID.
     */
    public static int getComputerIdFromBlockEntity(Object blockEntity) {
        if (blockEntity == null) return -1;

        Class<?> clazz = blockEntity.getClass();
        Optional<Method> cached = methodCache.get(clazz);

        if (cached != null) {
            return cached.map(m -> invokeMethod(m, blockEntity)).orElse(-1);
        }

        // Scan for a suitable method
        for (String methodName : METHOD_NAMES) {
            Method m = findDeclaredMethod(clazz, methodName);
            if (m != null) {
                m.setAccessible(true);
                methodCache.put(clazz, Optional.of(m));
                return invokeMethod(m, blockEntity);
            }
        }

        // Mark as scanned with no result
        methodCache.put(clazz, Optional.empty());
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
