package de.zfzfg.core.util;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ItemUtilTest {

    @Test
    void nullSicherheitBeiItemMetaMethoden() {
        assertDoesNotThrow(() -> ItemUtil.setDisplayName(null, Component.text("test")));
        assertDoesNotThrow(() -> ItemUtil.setDisplayName(null, "test"));
        assertDoesNotThrow(() -> ItemUtil.setLore(null, Collections.emptyList()));
        assertDoesNotThrow(() -> ItemUtil.setLoreFromStrings(null, Collections.emptyList()));
        
        List<Component> lore = ItemUtil.getLore(null);
        assertNotNull(lore);
        assertTrue(lore.isEmpty());

        String name = ItemUtil.getDisplayName(null);
        assertNotNull(name);
        assertEquals("", name);
    }
}
