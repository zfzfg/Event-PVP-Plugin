package de.zfzfg.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextTest {

    private String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void nullUndLeerErgebenLeereComponent() {
        assertEquals(Component.empty(), Text.of(null));
        assertEquals(Component.empty(), Text.of(""));
    }

    @Test
    void ampersandCodeWirdZuFarbe() {
        Component c = Text.of("&cFehler");
        assertEquals("Fehler", plain(c));
        assertEquals(NamedTextColor.RED, c.color());
    }

    @Test
    void hexCodeWirdGeparst() {
        Component c = Text.of("&#ff8800Warnung");
        assertEquals("Warnung", plain(c));
        assertNotNull(c.color());
    }

    @Test
    void formatierungBleibtErhalten() {
        Component c = Text.of("&a&lFett");
        assertTrue(c.hasDecoration(TextDecoration.BOLD));
    }

    @Test
    void textOhneCodesBleibtUnveraendert() {
        assertEquals("Hallo Welt", plain(Text.of("Hallo Welt")));
    }

    @Test
    void itemVarianteSchaltetKursivAb() {
        assertEquals(TextDecoration.State.FALSE,
                Text.ofItem("&bSchwert").decoration(TextDecoration.ITALIC));
    }

    @Test
    void cacheLiefertIdentischeInstanz() {
        Text.clearCache();
        assertSame(Text.of("&aWiederholt"), Text.of("&aWiederholt"));
    }

    @Test
    void roundtripUeberLegacyIstStabil() {
        String original = "&cRot &7grau";
        assertEquals("\u00a7cRot \u00a77grau", Text.toLegacy(Text.of(original)));
    }

    @Test
    void plainEntferntAlleCodes() {
        assertEquals("Rot grau", Text.plain("&cRot &7grau"));
    }

    @Test
    void mehrzeiligerTextUeberlebt() {
        assertTrue(plain(Text.of("&aZeile1\nZeile2")).contains("\n"));
    }

    @Test
    void buttonSetztRunCommandMitSlash() {
        Component c = Text.button("&aJa", "pvpaccept Bob", "&7Annehmen");
        var click = c.clickEvent();
        assertNotNull(click);
        assertNotNull(c.hoverEvent());
        assertTrue(Text.toLegacy(c).contains("Ja"));
    }

    @Test
    void buttonDoppeltKeinenSlash() {
        assertNotNull(Text.button("&aJa", "/pvpaccept Bob", null).clickEvent());
    }

    @Test
    void buttonOhneHoverHatKeinenHover() {
        assertNull(Text.button("&aJa", "pvpaccept", null).hoverEvent());
    }

    @Test
    void linkSetztOpenUrl() {
        assertNotNull(Text.link("&bWeb", "http://localhost:8080", "&7Oeffnen").clickEvent());
    }
}
