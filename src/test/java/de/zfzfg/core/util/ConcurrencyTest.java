package de.zfzfg.core.util;

import de.zfzfg.core.web.SlidingWindowLimiter;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class ConcurrencyTest {

    @Test
    void textCacheIstThreadSicher() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Component>> futures = new ArrayList<>();

        for (int i = 0; i < 200; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return Text.of("&aParallel");
            }));
        }

        start.countDown();
        Component first = futures.get(0).get(5, TimeUnit.SECONDS);
        for (Future<Component> f : futures) {
            assertEquals(first, f.get(5, TimeUnit.SECONDS));
        }

        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void slidingWindowLimiterIstThreadSicher() throws Exception {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(50, 60_000L);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return limiter.tryAcquire();
            }));
        }

        start.countDown();
        int allowedCount = 0;
        for (Future<Boolean> f : futures) {
            if (f.get(5, TimeUnit.SECONDS)) {
                allowedCount++;
            }
        }

        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(50, allowedCount, "Genau 50 Requests muessen innerhalb des Limits erlaubt sein");
    }

    @Test
    void commandCooldownManagerIstThreadSicher() throws Exception {
        CommandCooldownManager manager = new CommandCooldownManager(5000L);
        Player player = Mockito.mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return manager.checkAndApply(player, "pvp");
            }));
        }

        start.countDown();
        int allowedCount = 0;
        for (Future<Boolean> f : futures) {
            if (f.get(5, TimeUnit.SECONDS)) {
                allowedCount++;
            }
        }

        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(1, allowedCount, "Genau 1 Aufruf darf den Cooldown passieren");
    }
}
