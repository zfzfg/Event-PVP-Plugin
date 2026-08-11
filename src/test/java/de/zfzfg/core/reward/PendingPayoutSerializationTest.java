package de.zfzfg.core.reward;

import de.zfzfg.core.reward.PendingPayoutStore.PendingPayout;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueft das Umwandeln offener Posten in ihre Dateiform und zurueck.
 *
 * <p>Diese Umwandlung ist der Punkt, an dem Items real verschwinden koennen: was beim
 * Schreiben verlorengeht oder beim Lesen nicht wiederkommt, hat der Spieler nie gesehen und
 * wird auch niemand vermissen koennen - der Eintrag existiert dann schlicht nicht mehr.
 * Deshalb eigene Tests, obwohl sie ohne laufenden Server nur den Rahmen abdecken.</p>
 *
 * <p>Was hier <b>nicht</b> geprueft wird: die eigentliche {@code ItemStack}-Serialisierung.
 * Sie gehoert Bukkit und braucht eine registrierte Server-Instanz. Geprueft wird alles
 * drumherum - Betrag, Grund, Leer-Erkennung und der Umgang mit beschaedigten Eintraegen.</p>
 */
class PendingPayoutSerializationTest {

    private static Map<String, Object> map(Object items, Object money, Object reason) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("items", items);
        raw.put("money", money);
        raw.put("reason", reason);
        raw.put("createdAt", 1_700_000_000_000L);
        return raw;
    }

    @Test
    void keepsMoneyAndReasonAcrossTheRoundTrip() {
        PendingPayout restored = PendingPayout.fromMap(map(new ArrayList<>(), 250.5, "pvp-win"));

        assertNotNull(restored, "ein Posten mit Geld darf nicht verworfen werden");
        assertEquals(250.5, restored.money(), 0.0001);
        assertEquals("pvp-win", restored.reason());
        assertEquals(1_700_000_000_000L, restored.createdAt());
    }

    @Test
    void dropsCompletelyEmptyEntries() {
        // Ohne Items und ohne Geld gibt es nichts auszugeben. Solche Eintraege muellen sonst
        // die Datei zu und fuehren beim Join zu einer Meldung ohne Inhalt.
        assertNull(PendingPayout.fromMap(map(new ArrayList<>(), 0, "leer")));
        assertNull(PendingPayout.fromMap(map(null, null, null)));
    }

    @Test
    void survivesADamagedEntry() {
        // Eine von Hand bearbeitete oder halb geschriebene Datei darf den Start nicht
        // verhindern - schlimmstenfalls fehlt ein Posten, nicht alle.
        assertNull(PendingPayout.fromMap(null));
        assertNull(PendingPayout.fromMap(map("kein array", "kein betrag", 42)));
    }

    @Test
    void treatsAMissingTimestampAsNow() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("money", 10.0);

        PendingPayout restored = PendingPayout.fromMap(raw);

        assertNotNull(restored);
        assertTrue(restored.createdAt() > 0, "ohne Zeitstempel muss die aktuelle Zeit einspringen");
    }

    @Test
    void negativeAmountsNeverBecomeAPayout() {
        // Ein negativer Betrag wuerde dem Spieler Geld abziehen. Der Konstruktionsweg
        // klemmt ihn auf 0; damit bleibt nur ein leerer Posten uebrig.
        PendingPayout payout = PendingPayout.of(new ArrayList<>(), -100, "kaputt");
        assertTrue(payout.isEmpty());
        assertEquals(0, payout.money(), 0.0001);
    }

    @Test
    void nullItemsAreIgnoredInsteadOfCrashing() {
        List<org.bukkit.inventory.ItemStack> items = new ArrayList<>();
        items.add(null);

        PendingPayout payout = PendingPayout.of(items, 5, "mit-luecke");

        assertEquals(0, payout.items().size(), "null-Eintraege gehoeren nicht in die Ausgabe");
        assertEquals(5, payout.money(), 0.0001);
        assertFalse(payout.isEmpty(), "der Betrag allein macht den Posten schon gueltig");
    }

    @Test
    void reasonFallsBackWhenMissing() {
        assertEquals("unknown", PendingPayout.of(new ArrayList<>(), 1, null).reason());
    }

    @Test
    void serializedFormCarriesEveryField() {
        Map<String, Object> written = PendingPayout.of(new ArrayList<>(), 42.0, "event-reward:winter").toMap();

        assertEquals(42.0, written.get("money"));
        assertEquals("event-reward:winter", written.get("reason"));
        assertNotNull(written.get("items"), "die Item-Liste muss auch leer geschrieben werden");
        assertNotNull(written.get("createdAt"));

        // Wieder einlesbar - sonst waere die Datei nach einem Neustart wertlos.
        PendingPayout back = PendingPayout.fromMap(written);
        assertNotNull(back);
        assertEquals(42.0, back.money(), 0.0001);
        assertEquals("event-reward:winter", back.reason());
    }
}
