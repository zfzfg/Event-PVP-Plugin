package de.zfzfg.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextButtonTest {

    private String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void buttonMitLeeremLabel() {
        Component c = Text.button("", "help", "hover");
        assertNotNull(c);
        assertEquals("", plain(c));
    }

    @Test
    void buttonMitNullCommand() {
        Component c = Text.button("Label", null, null);
        assertNotNull(c);
        assertEquals("Label", plain(c));
    }

    @Test
    void buttonHoverEnthaeltFarbe() {
        Component c = Text.button("&aButton", "/cmd", "&cHoverText");
        assertNotNull(c.hoverEvent());
        assertEquals("Button", plain(c));
    }

    @Test
    void verschachtelteAppendKette() {
        Component a = Text.of("&aErster");
        Component b = Text.of("&bZweiter");
        Component combined = a.append(Component.space()).append(b);
        assertEquals("Erster Zweiter", plain(combined));
    }

    @Test
    void sehrLangerTextWirdNichtAbgeschnitten() {
        String longString = "A".repeat(5000);
        Component c = Text.of(longString);
        assertEquals(5000, plain(c).length());
    }

    @Test
    void unbekannterFarbcode() {
        Component c = Text.of("&zTest");
        assertNotNull(c);
        assertTrue(plain(c).contains("Test"));
    }

    @Test
    void nurFarbcodeOhneText() {
        Component c = Text.of("&c");
        assertNotNull(c);
        assertEquals("", plain(c));
    }

    @Test
    void cacheUeberschreitetLimitNicht() {
        Text.clearCache();
        for (int i = 0; i < 5000; i++) {
            Text.of("&aMessage_" + i);
        }
        // Limit im Code ist 4096
        // Ein weiterer Aufruf darf nicht abstuerzen
        assertNotNull(Text.of("&bExtraMessage"));
    }
}
