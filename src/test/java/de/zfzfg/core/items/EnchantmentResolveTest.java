package de.zfzfg.core.items;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnchantmentResolveTest {
    @Test void ergaenztNamespace()      { assertEquals("minecraft:sharpness", ConfiguredItemFactory.normalizeEnchantKey("sharpness")); }
    @Test void behaeltNamespace()       { assertEquals("minecraft:sharpness", ConfiguredItemFactory.normalizeEnchantKey("minecraft:sharpness")); }
    @Test void kleinschreibung()        { assertEquals("minecraft:sharpness", ConfiguredItemFactory.normalizeEnchantKey("SHARPNESS")); }
    @Test void trimmtLeerzeichen()      { assertEquals("minecraft:sharpness", ConfiguredItemFactory.normalizeEnchantKey("  sharpness ")); }
    @Test void fremderNamespaceBleibt() { assertEquals("custom:foo", ConfiguredItemFactory.normalizeEnchantKey("custom:foo")); }
    @Test void nullBleibtNull()         { assertNull(ConfiguredItemFactory.normalizeEnchantKey(null)); }
    @Test void leerBleibtNull()         { assertNull(ConfiguredItemFactory.normalizeEnchantKey("   ")); }
}
