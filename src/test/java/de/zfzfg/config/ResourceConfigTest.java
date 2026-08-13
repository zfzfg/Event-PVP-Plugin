package de.zfzfg.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validiert die mitgelieferten YAML-Ressourcen.
 *
 * <p>Diese Tests laufen ohne Server: {@link YamlConfiguration} ist eine reine
 * Bukkit-Utility und braucht keine laufende Instanz.
 */
class ResourceConfigTest {

    private static final String RES = "src/main/resources/";

    /** Die beiden gepflegten Sprachen. Sie sind die Referenz fuer den Schluesselabgleich. */
    private static final String REFERENZ_SPRACHE = "messages_de.yml";
    private static final String ZWEITE_SPRACHE = "messages_en.yml";

    private static YamlConfiguration load(String name) {
        File file = new File(RES + name);
        assertTrue(file.exists(), "Datei fehlt: " + name);
        return YamlConfiguration.loadConfiguration(file);
    }

    /** Nur Blattknoten - Zwischenknoten sind fuer den Abgleich uninteressant. */
    private static Set<String> blattSchluessel(YamlConfiguration config) {
        Set<String> blaetter = new TreeSet<>();
        for (String key : config.getKeys(true)) {
            if (!config.isConfigurationSection(key)) blaetter.add(key);
        }
        return blaetter;
    }

    // ------------------------------------------------------------------
    // 1. Jede ausgelieferte YAML ist ladbar und nicht leer
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "config.yml", "equipment.yml", "worlds.yml", "web-config.yml",
            "messages_de.yml", "messages_en.yml", "messages_es.yml", "messages_fr.yml",
            "messages_ja.yml", "messages_pl.yml", "messages_ru.yml"
    })
    void yamlIstLadbarUndNichtLeer(String name) {
        assertFalse(load(name).getKeys(false).isEmpty(), "Config ist leer: " + name);
    }

    // ------------------------------------------------------------------
    // 2. Schluesselgleichheit der Sprachdateien
    // ------------------------------------------------------------------

    /**
     * Die beiden gepflegten Sprachen muessen exakt denselben Schluesselsatz haben.
     * Weicht das ab, fehlt in einer der beiden eine Nachricht und der Spieler sieht
     * zur Laufzeit den Rohschluessel.
     */
    @Test
    void deUndEnHabenIdentischeSchluessel() {
        Set<String> de = blattSchluessel(load(REFERENZ_SPRACHE));
        Set<String> en = blattSchluessel(load(ZWEITE_SPRACHE));

        Set<String> nurDe = new TreeSet<>(de);
        nurDe.removeAll(en);
        Set<String> nurEn = new TreeSet<>(en);
        nurEn.removeAll(de);

        assertTrue(nurDe.isEmpty() && nurEn.isEmpty(),
                "Schluesselsaetze weichen ab.\n  Nur in de (" + nurDe.size() + "): " + gekuerzt(nurDe)
                        + "\n  Nur in en (" + nurEn.size() + "): " + gekuerzt(nurEn));
    }

    /**
     * Die uebrigen Sprachen duerfen keine Schluessel enthalten, die es in der Referenz
     * nicht gibt (Tippfehler / verwaiste Eintraege). Fehlende Schluessel werden hier
     * bewusst NICHT als Fehler gewertet - unvollstaendige Uebersetzungen fallen auf
     * die Standardsprache zurueck und sind ein redaktionelles, kein technisches Problem.
     */
    @ParameterizedTest
    @ValueSource(strings = {"messages_es.yml", "messages_fr.yml", "messages_ja.yml",
            "messages_pl.yml", "messages_ru.yml"})
    void uebersetzungHatKeineUnbekanntenSchluessel(String name) {
        Set<String> referenz = blattSchluessel(load(REFERENZ_SPRACHE));
        Set<String> fremd = new TreeSet<>(blattSchluessel(load(name)));
        fremd.removeAll(referenz);

        assertTrue(fremd.isEmpty(),
                name + " enthaelt " + fremd.size() + " Schluessel, die in "
                        + REFERENZ_SPRACHE + " fehlen: " + gekuerzt(fremd));
    }

    // ------------------------------------------------------------------
    // 3. Keine Section-Codes in den Nachrichtenwerten
    // ------------------------------------------------------------------

    /**
     * Jeder Nachrichtenwert muss sich von Text.of(...) restlos in eine Component
     * uebersetzen lassen - im Klartext darf hinterher kein Formatcode mehr stehen.
     *
     * <p>Die Sprachdateien mischen historisch '&'- und '§'-Codes (die Chat-Button-Texte
     * sind durchgehend mit '§' geschrieben). Das ist stilistisch unschoen, aber
     * funktional unkritisch: der Legacy-Serializer von Adventure erkennt beide Zeichen.
     * Dieser Test sichert genau diese Eigenschaft ab - er wuerde anschlagen, sobald ein
     * Code stehen bleibt und der Spieler rohes "§a" im Chat saehe.
     */
    @ParameterizedTest
    @ValueSource(strings = {"messages_de.yml", "messages_en.yml", "messages_es.yml",
            "messages_fr.yml", "messages_ja.yml", "messages_pl.yml", "messages_ru.yml"})
    void alleNachrichtenParsenRestlos(String name) {
        YamlConfiguration config = load(name);
        Set<String> treffer = new LinkedHashSet<>();
        for (String key : blattSchluessel(config)) {
            Object value = config.get(key);
            if (value instanceof String s) {
                if (hatRestcode(s)) treffer.add(key);
            } else if (value instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof String s2 && hatRestcode(s2)) { treffer.add(key); break; }
                }
            }
        }
        assertTrue(treffer.isEmpty(),
                name + ": unaufgeloeste Formatcodes nach dem Parsen bei " + gekuerzt(treffer));
    }

    /** true, wenn nach dem Parsen noch ein Section-Zeichen im Klartext steht. */
    private static boolean hatRestcode(String legacy) {
        return de.zfzfg.core.util.Text.plain(legacy).indexOf('§') >= 0;
    }

    // ------------------------------------------------------------------
    // 4. plugin.yml
    // ------------------------------------------------------------------

    @Test
    void jederCommandHatBeschreibung() {
        YamlConfiguration plugin = load("plugin.yml");
        var commands = plugin.getConfigurationSection("commands");
        assertNotNull(commands, "plugin.yml hat keinen commands-Abschnitt");

        List<String> ohne = new ArrayList<>();
        for (String cmd : commands.getKeys(false)) {
            String desc = commands.getString(cmd + ".description");
            if (desc == null || desc.isBlank()) ohne.add(cmd);
        }
        assertTrue(ohne.isEmpty(), "Commands ohne description: " + ohne);
    }

    @Test
    void pluginYmlZieltAufPurpur26() {
        assertEquals("26.2", load("plugin.yml").getString("api-version"));
    }

    private static String gekuerzt(Set<String> keys) {
        if (keys.isEmpty()) return "[]";
        List<String> list = new ArrayList<>(keys);
        if (list.size() <= 15) return list.toString();
        return list.subList(0, 15) + " ... (+" + (list.size() - 15) + " weitere)";
    }
}
