package de.zfzfg.eventplugin.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColorUtilTest {

    @Test
    void testColor() {
        assertEquals("\u00a7cTest", ColorUtil.color("&cTest"));
        assertEquals("", ColorUtil.color(null));
    }

    @Test
    void testStripColor() {
        assertEquals("Test", ColorUtil.stripColor("&cTest"));
        assertEquals("Test", ColorUtil.stripColor("\u00a7cTest"));
        assertEquals("", ColorUtil.stripColor(null));
    }
}
