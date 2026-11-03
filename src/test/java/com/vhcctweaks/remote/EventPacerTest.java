package com.vhcctweaks.remote;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventPacerTest {

    private final List<String> sent = new ArrayList<>();
    private final Object computer = new Object();

    @BeforeEach
    void reset() {
        EventPacer.reset();
    }

    private void offer(int n) {
        for (int i = 0; i < n; i++) {
            EventPacer.offer(computer, 64, (event, args) -> sent.add(event + " " + args[0]), "peripheral", new Object[]{"5:p" + i});
        }
    }

    @Test
    void upToTheLimitGoesOutAtOnce() {
        offer(64);
        assertEquals(64, sent.size());
        assertEquals(0, EventPacer.waiting(computer));
    }

    @Test
    void theRestGoOutOverFollowingTicksInOrder() {
        offer(200);
        assertEquals(64, sent.size());
        EventPacer.endTick();
        assertEquals(128, sent.size());
        EventPacer.endTick();
        assertEquals(192, sent.size());
        EventPacer.endTick();
        assertEquals(200, sent.size());
        for (int i = 0; i < 200; i++) assertEquals("peripheral 5:p" + i, sent.get(i));
    }

    @Test
    void aNewEventWaitsBehindOlderOnes() {
        offer(70);
        EventPacer.endTick();
        offer(1);
        assertEquals(71, sent.size());
        assertEquals("peripheral 5:p0", sent.get(70));
    }

    @Test
    void aComputerThatHasGoneLosesItsWaitingEvents() {
        for (int i = 0; i < 100; i++) {
            EventPacer.offer(computer, 64, (event, args) -> {
                if (sent.size() >= 64) throw new IllegalStateException("not attached");
                sent.add(event);
            }, "peripheral", new Object[]{"x"});
        }
        EventPacer.endTick();
        assertEquals(64, sent.size());
        assertEquals(0, EventPacer.waiting(computer));
    }

    @Test
    void eachComputerHasItsOwnLimit() {
        Object other = new Object();
        offer(64);
        EventPacer.offer(other, 64, (event, args) -> sent.add("other"), "peripheral", new Object[]{"y"});
        assertEquals(65, sent.size());
    }
}
