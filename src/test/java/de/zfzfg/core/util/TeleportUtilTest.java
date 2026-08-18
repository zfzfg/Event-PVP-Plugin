package de.zfzfg.core.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class TeleportUtilTest {

    @Test
    void nullSpielerOderLocationGibtFalseFutureZurueck() {
        CompletableFuture<Boolean> future1 = TeleportUtil.teleport(null, null);
        assertNotNull(future1);
        assertTrue(future1.isDone());
        assertFalse(future1.join());
    }
}
