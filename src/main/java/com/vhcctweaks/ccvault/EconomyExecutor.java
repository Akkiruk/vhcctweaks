package com.vhcctweaks.ccvault;

import com.vhcctweaks.VHCCTweaks;
import com.vhcctweaks.util.Safety;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Money calls that reach the database run here, on one thread, in the order they were made, so a slow or stuck
 * database never holds up CC's computer thread and every other computer with it.
 *
 * Each call gets a ticket and its answer is delivered once: either the work's own result, or, if it has not finished
 * within TIMEOUT_SECONDS, nil and "economy timed out". The work still runs to the end after a timeout, and everything
 * that must follow the money (the ledger, an allowance's settlement) happens inside the work, so it stays right
 * whatever Lua was told or whether the computer is still waiting.
 */
public final class EconomyExecutor {

    public static final int TIMEOUT_SECONDS = 20;
    public static final String TIMED_OUT = "economy timed out - the transfer may still complete, check verifyTransaction";
    public static final String UNAVAILABLE = "economy not available";

    private static final AtomicLong TICKETS = new AtomicLong();
    /** TIMEOUT_SECONDS, shorter in tests. */
    static volatile long timeoutMillis = TIMEOUT_SECONDS * 1000L;
    private static volatile ExecutorService work;
    private static volatile ScheduledExecutorService watchdog;

    private EconomyExecutor() {}

    /** Server about to start: fresh threads. */
    public static synchronized void start() {
        stop();
        work = Executors.newSingleThreadExecutor(r -> daemon(r, "VHCC-Economy"));
        watchdog = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "VHCC-Economy-Watchdog"));
    }

    /** Server stopped: let queued money calls finish, briefly, then stop the threads. */
    public static synchronized void stop() {
        ExecutorService w = work;
        ScheduledExecutorService d = watchdog;
        work = null;
        watchdog = null;
        if (w != null) {
            w.shutdown();
            try {
                if (!w.awaitTermination(10, TimeUnit.SECONDS)) {
                    VHCCTweaks.LOGGER.warn("CCVault: money calls still running at shutdown were abandoned");
                    w.shutdownNow();
                }
            } catch (InterruptedException e) {
                w.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (d != null) d.shutdownNow();
    }

    /**
     * Runs work on the economy thread and hands its answer, exactly once, to deliver. Returns the ticket, or -1 when the
     * economy threads are not running (the caller answers "economy not available" itself).
     */
    public static long submit(String where, Callable<Object[]> job, Consumer<Object[]> deliver) {
        ExecutorService w = work;
        ScheduledExecutorService d = watchdog;
        if (w == null || d == null) return -1;
        long ticket = TICKETS.incrementAndGet();
        AtomicBoolean answered = new AtomicBoolean();
        try {
            w.execute(() -> {
                Object[] out;
                try {
                    out = job.call();
                } catch (Exception | LinkageError e) {
                    Safety.logOnce(where, e);
                    out = new Object[]{null, Safety.INTERNAL_ERROR};
                }
                if (answered.compareAndSet(false, true)) Safety.guard(where + " answer", deliverOf(deliver, ticket, out));
            });
            d.schedule(() -> {
                if (answered.compareAndSet(false, true)) {
                    Safety.guard(where + " timeout", deliverOf(deliver, ticket, new Object[]{null, TIMED_OUT}));
                }
            }, timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            return -1;
        }
        return ticket;
    }

    private static Runnable deliverOf(Consumer<Object[]> deliver, long ticket, Object[] out) {
        return () -> {
            Object[] event = new Object[out.length + 1];
            event[0] = ticket;
            System.arraycopy(out, 0, event, 1, out.length);
            deliver.accept(event);
        };
    }

    private static Thread daemon(Runnable r, String name) {
        Thread thread = new Thread(r, name);
        thread.setDaemon(true);
        return thread;
    }
}
