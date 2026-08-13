package de.zfzfg.core.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextUtilTest {

    @Test
    void colorErzeugtSectionCodes() {
        assertEquals("\u00a7cFehler", TextUtil.color("&cFehler"));
    }

    @Test
    void colorAufNullIstLeerString() {
        assertEquals("", TextUtil.color(null));
    }

    @Test
    void colorIstIdempotent() {
        String einmal = TextUtil.color("&aTest");
        assertEquals(einmal, TextUtil.color(einmal));
    }

    @Test
    void stripEntferntCodes() {
        assertEquals("Fehler", TextUtil.strip("&cFehler"));
    }

    @Test
    void stripAufNullIstLeerString() {
        assertEquals("", TextUtil.strip(null));
    }

    @Test
    void componentLiefertGeparsteComponent() {
        assertNotNull(TextUtil.component("&cX").color());
    }

    @Test
    void sendAufNullEmpfaengerWirftNicht() {
        assertDoesNotThrow(() -> TextUtil.send((org.bukkit.entity.Player) null, "x"));
    }
}
