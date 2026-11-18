package com.vhcctweaks.ccvault;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The table ccvault.getLimits returns: the server config values a program would otherwise have to guess or learn
 * from error messages. A value that cannot be read is left out.
 */
final class LimitsShape {

    private final Map<String, Object> out = new HashMap<>();

    LimitsShape put(String key, Supplier<?> value) {
        try {
            Object read = value.get();
            if (read != null) out.put(key, read);
        } catch (RuntimeException e) {
            // Config not loaded: the key is left out
        }
        return this;
    }

    Map<String, Object> build() {
        return out;
    }
}
