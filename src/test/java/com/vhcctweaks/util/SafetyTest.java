package com.vhcctweaks.util;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetyTest {

    @Test
    void aPlayerConnectedToThisServerIsHere() {
        assertTrue(Safety.isHere(true, false, true, false));
    }

    @Test
    void aPlayerTheNetworkCallsOnlineButNotInThisServersListIsNotHere() {
        // ParadiseCore or the proxy may still say online; only this server's player list counts
        assertFalse(Safety.isHere(false, false, true, false));
    }

    @Test
    void aPlayerLeavingIsNotHere() {
        assertFalse(Safety.isHere(true, true, true, false));
        assertFalse(Safety.isHere(true, false, false, false));
        assertFalse(Safety.isHere(true, false, true, true));
    }

    @Test
    void noPlayerAndNoServerAreNotHere() {
        assertFalse(Safety.isHere(null));
        assertNull(Safety.here(null, UUID.randomUUID()));
        assertNull(Safety.here(null, "Steve"));
    }

    @Test
    void aBugBecomesNilInternalError() throws LuaException {
        assertArrayEquals(new Object[]{null, "internal error"},
                Safety.lua("test.npe", () -> { throw new NullPointerException("boom"); }));
        MethodResult result = Safety.luaResult("test.npe2", () -> { throw new IllegalStateException("boom"); });
        assertArrayEquals(new Object[]{null, "internal error"}, result.getResult());
    }

    @Test
    void aLuaErrorIsTheFunctionsOwnAnswerAndPassesThrough() {
        LuaException e = assertThrows(LuaException.class, () -> Safety.lua("test.lua", () -> { throw new LuaException("bad argument"); }));
        assertEquals("bad argument", e.getMessage());
    }

    @Test
    void aGuardedHandlerNeverThrows() {
        Safety.guard("test.guard", () -> { throw new RuntimeException("boom"); });
        Safety.guard("test.guard", () -> { throw new RuntimeException("again, logged once"); });
    }

    @Test
    void aNormalAnswerIsUntouched() throws LuaException {
        assertArrayEquals(new Object[]{42}, Safety.lua("test.ok", () -> new Object[]{42}));
    }
}
