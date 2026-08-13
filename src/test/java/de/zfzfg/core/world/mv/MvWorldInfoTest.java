package de.zfzfg.core.world.mv;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MvWorldInfoTest {

    @Test
    void testToMapSerialization() {
        MvWorldInfo info = new MvWorldInfo("pvp_arena", "NORMAL", "NORMAL", true, true, true);
        Map<String, Object> map = info.toMap();

        assertEquals("pvp_arena", map.get("name"));
        assertEquals("NORMAL", map.get("environment"));
        assertEquals("NORMAL", map.get("worldType"));
        assertEquals(true, map.get("loaded"));
        assertEquals(true, map.get("knownToMultiverse"));
        assertEquals(true, map.get("existsOnDisk"));
    }

    @Test
    void testToMapWithSpecialCharacters() {
        MvWorldInfo info = new MvWorldInfo("world_\"quote\"_&_special", "NETHER", "FLAT", false, false, false);
        Map<String, Object> map = info.toMap();

        assertEquals("world_\"quote\"_&_special", map.get("name"));
        assertEquals("NETHER", map.get("environment"));
        assertEquals(false, map.get("loaded"));
    }
}
