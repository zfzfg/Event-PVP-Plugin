package de.zfzfg.pvpwager.utils;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MessageUtilTest {

    @Test
    void testFormatTime() {
        assertEquals("00", MessageUtil.formatTime(0));
        assertEquals("59", MessageUtil.formatTime(59));
        assertEquals("01:00", MessageUtil.formatTime(60));
        assertEquals("01:01", MessageUtil.formatTime(61));
        assertEquals("59:59", MessageUtil.formatTime(3599));
        assertEquals("60:00", MessageUtil.formatTime(3600));
    }

    @Test
    void testColorDelegation() {
        assertEquals("\u00a7aHallo", MessageUtil.color("&aHallo"));
        assertEquals("", MessageUtil.color((String) null));
    }

    @Test
    void testColorList() {
        List<String> colored = MessageUtil.color(Arrays.asList("&aEins", "&bZwei"));
        assertEquals(2, colored.size());
        assertEquals("\u00a7aEins", colored.get(0));
        assertEquals("\u00a7bZwei", colored.get(1));
    }

    @Test
    void testFormatItemListNullOrEmpty() {
        assertNotNull(MessageUtil.formatItemList(null));
        assertNotNull(MessageUtil.formatItemList(Collections.emptyList()));
    }
}
