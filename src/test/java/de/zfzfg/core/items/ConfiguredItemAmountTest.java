package de.zfzfg.core.items;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfiguredItemAmountTest {

    @Test
    void testParseAmountValid() {
        assertEquals(1, ConfiguredItemAmountTestHelper(1, 64, "1"));
        assertEquals(64, ConfiguredItemAmountTestHelper(1, 64, "64"));
        assertEquals(16, ConfiguredItemAmountTestHelper(1, 16, "16"));
    }

    @Test
    void testParseAmountClamped() {
        // Unter 1 wird zu 1 geklemmt
        assertEquals(1, ConfiguredItemAmountTestHelper(1, 64, "0"));
        assertEquals(1, ConfiguredItemAmountTestHelper(1, 64, "-5"));
        // Ueber MaxStack wird geklemmt
        assertEquals(64, ConfiguredItemAmountTestHelper(1, 64, "999"));
        assertEquals(16, ConfiguredItemAmountTestHelper(1, 16, "64"));
    }

    @Test
    void testParseAmountFallbackOnInvalid() {
        assertEquals(5, ConfiguredItemAmountTestHelper(5, 64, null));
        assertEquals(5, ConfiguredItemAmountTestHelper(5, 64, ""));
        assertEquals(5, ConfiguredItemAmountTestHelper(5, 64, "abc"));
    }

    private int ConfiguredItemAmountTestHelper(int def, int maxStack, String raw) {
        return ConfiguredItemFactory.parseAmount(raw, def, maxStack);
    }
}
