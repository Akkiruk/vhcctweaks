package com.vhcctweaks.remote;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TooLargeReplyTest {

    @Test
    void anOversizedReplyBecomesASmallErrorWithItsSeqAndExec() {
        JsonObject big = new JsonObject();
        big.addProperty("seq", 7);
        big.addProperty("exec", "abc");
        big.addProperty("result", "\"".repeat(100_000));
        JsonObject small = RemoteControl.tooLarge(big);
        assertEquals(7, small.get("seq").getAsInt());
        assertEquals("abc", small.get("exec").getAsString());
        assertFalse(small.get("ok").getAsBoolean());
        assertEquals("reply too large for one chat line", small.get("error").getAsString());
        assertFalse(small.has("result"));
    }
}
