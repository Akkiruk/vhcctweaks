package com.vhcctweaks.ccvault;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyExecutorTest {

    @BeforeEach
    void start() {
        EconomyExecutor.start();
    }

    @AfterEach
    void stop() {
        EconomyExecutor.stop();
    }

    @Test
    void callsCompleteInTheOrderTheyWereMade() throws InterruptedException {
        List<Integer> ran = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(50);
        for (int i = 0; i < 50; i++) {
            int n = i;
            EconomyExecutor.submit("test", () -> {
                if (n % 7 == 0) Thread.sleep(3);
                ran.add(n);
                return new Object[]{n};
            }, event -> done.countDown());
        }
        assertTrue(done.await(10, TimeUnit.SECONDS));
        for (int i = 0; i < 50; i++) assertEquals(i, ran.get(i));
    }

    @Test
    void theAnswerCarriesItsTicketThenTheResults() throws InterruptedException {
        List<Object[]> events = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(1);
        long ticket = EconomyExecutor.submit("test", () -> new Object[]{null, "insufficient balance"}, e -> { events.add(e); done.countDown(); });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(ticket, events.get(0)[0]);
        assertEquals(null, events.get(0)[1]);
        assertEquals("insufficient balance", events.get(0)[2]);
    }

    @Test
    void aCrashIsAnInternalErrorNotAHang() throws InterruptedException {
        List<Object[]> events = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(1);
        EconomyExecutor.submit("test.crash", () -> { throw new IllegalStateException("database gone"); }, e -> { events.add(e); done.countDown(); });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals("internal error", events.get(0)[2]);
    }

    @Test
    void aStuckDatabaseCallTimesOutOnceAndLaterCallsAreNotLost() throws InterruptedException {
        EconomyExecutor.timeoutMillis = 200;
        try {
            List<Object[]> events = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch finished = new CountDownLatch(1);
            EconomyExecutor.submit("test.stuck", () -> {
                release.await(5, TimeUnit.SECONDS);
                finished.countDown();
                return new Object[]{"late"};
            }, events::add);
            Thread.sleep(500);
            assertEquals(1, events.size());
            assertEquals(EconomyExecutor.TIMED_OUT, events.get(0)[2]);
            release.countDown();
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            Thread.sleep(100);
            // The late result is not delivered a second time
            assertEquals(1, events.size());
        } finally {
            EconomyExecutor.timeoutMillis = EconomyExecutor.TIMEOUT_SECONDS * 1000L;
        }
    }

    @Test
    void notRunningMeansNoTicket() {
        EconomyExecutor.stop();
        assertEquals(-1, EconomyExecutor.submit("test", () -> new Object[]{1}, e -> {}));
    }

    @Test
    void stopLetsQueuedCallsFinish() throws InterruptedException {
        List<Integer> ran = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 5; i++) {
            int n = i;
            EconomyExecutor.submit("test", () -> { Thread.sleep(20); ran.add(n); return new Object[]{n}; }, e -> {});
        }
        EconomyExecutor.stop();
        assertEquals(5, ran.size());
    }
}
