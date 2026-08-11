package de.zfzfg.core.web;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueft die Bremse von {@code POST /api/inventories/restore}.
 *
 * <p>Der Endpunkt legt Items im Spiel an. Faellt die Begrenzung aus, ist ein uebernommenes
 * Web-Login eine Item-Fabrik - deshalb ist das sicherheitsrelevant und nicht nur Komfort.</p>
 */
class SlidingWindowLimiterTest {

    @Test
    void allowsExactlyTheConfiguredNumberOfCalls() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(3, 60_000L);

        assertTrue(limiter.tryAcquire(1_000L));
        assertTrue(limiter.tryAcquire(1_100L));
        assertTrue(limiter.tryAcquire(1_200L));
        assertFalse(limiter.tryAcquire(1_300L), "der vierte Zugriff muss abgelehnt werden");
    }

    @Test
    void staysClosedForTheRestOfTheWindow() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(1, 60_000L);

        assertTrue(limiter.tryAcquire(0L));
        assertFalse(limiter.tryAcquire(30_000L));
        assertFalse(limiter.tryAcquire(59_999L), "erst ab 60000 darf wieder geoeffnet werden");
    }

    @Test
    void opensAgainAfterTheWindowElapsed() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(2, 60_000L);

        assertTrue(limiter.tryAcquire(0L));
        assertTrue(limiter.tryAcquire(10L));
        assertFalse(limiter.tryAcquire(20L));

        assertTrue(limiter.tryAcquire(60_000L), "genau bei Fensterende beginnt die Zaehlung neu");
        assertTrue(limiter.tryAcquire(60_010L));
        assertFalse(limiter.tryAcquire(60_020L));
    }

    @Test
    void reportsTheRemainingBudget() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(3, 60_000L);

        assertEquals(3, limiter.remaining());
        limiter.tryAcquire(0L);
        assertEquals(2, limiter.remaining());
        limiter.tryAcquire(1L);
        limiter.tryAcquire(2L);
        assertEquals(0, limiter.remaining());
        limiter.tryAcquire(3L);
        assertEquals(0, limiter.remaining(), "abgelehnte Zugriffe duerfen nicht ins Minus zaehlen");
    }

    @Test
    void rejectsNonsensicalConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowLimiter(0, 1_000L));
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowLimiter(-1, 1_000L));
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowLimiter(1, 0L));
    }

    /**
     * HTTP-Handler laufen auf mehreren Threads. Ohne Synchronisierung koennten zwei
     * gleichzeitige Anfragen dieselbe Restmenge sehen und beide durchkommen - dann waere
     * die Grenze genau in dem Moment wirkungslos, in dem sie gebraucht wird.
     */
    @Test
    void neverHandsOutMorePermitsThanAllowedUnderConcurrency() throws InterruptedException {
        final int permits = 50;
        final int threads = 16;
        final int callsPerThread = 20;

        SlidingWindowLimiter limiter = new SlidingWindowLimiter(permits, 60_000L);
        AtomicInteger granted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int c = 0; c < callsPerThread; c++) {
                        if (limiter.tryAcquire()) {
                            granted.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "die Threads muessen fertig werden");
        assertEquals(permits, granted.get(),
                "es duerfen weder mehr noch weniger als " + permits + " Zugriffe durchkommen");
    }
}
