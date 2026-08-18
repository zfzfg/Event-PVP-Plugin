package de.zfzfg.core.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlatformTest {

    @Test
    void isPaperGibtKonsistentenStatusZurueck() {
        assertDoesNotThrow(Platform::isPaper);
    }

    @Test
    void isPurpurGibtKonsistentenStatusZurueck() {
        assertDoesNotThrow(Platform::isPurpur);
    }
}
