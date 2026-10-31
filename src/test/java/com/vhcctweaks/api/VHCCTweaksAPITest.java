package com.vhcctweaks.api;

import dan200.computercraft.api.lua.LuaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VHCCTweaksAPITest {

    @ParameterizedTest
    @ValueSource(strings = {"file.txt", "a/b/c.dat", "a\\b.txt", "ccvault/owners.json", "name-with_marks.1"})
    void acceptsNormalPaths(String path) {
        assertDoesNotThrow(() -> VHCCTweaksAPI.validatePath(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/etc/passwd", "\\windows", "../other", "a/../../b", "./file", ".hidden", "a b", "a:b", "c:/x", "a/*"})
    void refusesPathsThatLeaveOrHide(String path) {
        assertThrows(LuaException.class, () -> VHCCTweaksAPI.validatePath(path));
    }

    @Test
    void refusesDeepPaths() {
        assertThrows(LuaException.class, () -> VHCCTweaksAPI.validatePath("a/".repeat(17) + "b"));
    }
}
