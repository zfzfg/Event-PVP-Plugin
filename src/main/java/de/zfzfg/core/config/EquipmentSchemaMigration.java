package de.zfzfg.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fuehrt die drei historischen Set-Sektionen von {@code equipment.yml} zu einer zusammen.
 *
 * <h2>Warum das noetig war</h2>
 * <p>Die Datei kannte drei Sektionen fuer dieselbe Sache - {@code equipment:},
 * {@code equipment-sets:} und {@code equipment-groups:} -, und die beiden Loader bevorzugten
 * <b>entgegengesetzte</b>: der PvP-Teil las zuerst {@code equipment:}, der Event-Teil zuerst
 * {@code equipment-sets:}. Waren beide belegt, benutzten PvP und Events unterschiedliche
 * Definitionen unter derselben Set-ID - ein Fehler, der sich im Betrieb nur als
 * "das Kit sieht im Event anders aus" bemerkbar macht und praktisch nicht auffindbar ist.</p>
 *
 * <p>Das Web-Panel wiederum schrieb ausschliesslich nach {@code equipment-sets:}. Auf einem
 * Server, der die vereinheitlichte Sektion benutzte, waren die Sets im Panel unsichtbar und
 * alles dort Angelegte blieb wirkungslos.</p>
 *
 * <h2>Verhalten</h2>
 * <ul>
 *   <li>Gibt es nur {@code equipment:}, passiert nichts - insbesondere kein Schreibzugriff.</li>
 *   <li>Sonst wird die Datei zuerst als {@code equipment.yml.bak-<zeitstempel>} gesichert.</li>
 *   <li>Die Aktiv-Schalter werden uebersetzt: {@code enabled: false} der Alt-Sektionen wird zu
 *       {@code pvpwager-equip-enable: false} <em>und</em> {@code event-equip-enable: false},
 *       weil die alte Sektion nur einen gemeinsamen Schalter kannte.</li>
 *   <li>Bei ID-Kollision gewinnt der Eintrag aus {@code equipment:}; der andere wird als
 *       {@code <id>-legacy} uebernommen und die Kollision deutlich gemeldet.</li>
 * </ul>
 *
 * <p>Der Vorgang ist idempotent: ein zweiter Lauf findet nur noch {@code equipment:} vor und
 * fasst die Datei nicht mehr an.</p>
 */
public final class EquipmentSchemaMigration {

    /** Zielsektion, die beide Loader ab 1.0.9 als einzige lesen. */
    public static final String TARGET = "equipment";

    /**
     * Quellsektionen in der Reihenfolge, in der sie uebernommen werden.
     * {@code equipment-sets} zuerst, weil das Web-Panel bis 1.0.9 dorthin geschrieben hat und
     * diese Eintraege damit am ehesten die gepflegten sind.
     */
    private static final String[] LEGACY_SECTIONS = { "equipment-sets", "equipment-groups" };

    /** Gemeinsamer Aktiv-Schalter der Alt-Sektionen. */
    private static final String LEGACY_ENABLED = "enabled";

    /** Getrennte Aktiv-Schalter der Zielsektion. */
    private static final String PVP_ENABLED = "pvpwager-equip-enable";
    private static final String EVENT_ENABLED = "event-equip-enable";

    private EquipmentSchemaMigration() {
    }

    /** Ergebnis eines Migrationslaufs - erlaubt Tests ohne Server und Dateisystem. */
    public static final class Result {
        private final boolean changed;
        private final List<String> notes = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private int migratedSets;

        Result(boolean changed) {
            this.changed = changed;
        }

        public boolean isChanged() { return changed; }
        public int getMigratedSets() { return migratedSets; }
        public List<String> getNotes() { return notes; }
        public List<String> getWarnings() { return warnings; }
    }

    /**
     * Fuehrt die Sektionen in der uebergebenen Konfiguration zusammen.
     *
     * <p>Arbeitet nur im Speicher; Sicherung und Speichern erledigt
     * {@link #run(FileConfiguration, File, java.util.logging.Logger)}. Diese Trennung macht die
     * Umschreibregeln ohne Dateisystem testbar.</p>
     *
     * @return was geaendert wurde; {@code changed == false} heisst "nichts zu tun"
     */
    public static Result merge(FileConfiguration config) {
        boolean hasLegacy = false;
        for (String section : LEGACY_SECTIONS) {
            if (config.isConfigurationSection(section)) {
                hasLegacy = true;
                break;
            }
        }
        if (!hasLegacy) {
            return new Result(false);
        }

        Result result = new Result(true);

        // Zielsektion als einfache Map aufbauen. Ueber Maps statt ConfigurationSections, weil
        // set(...) auf einer Sektion, aus der gerade gelesen wird, schwer nachvollziehbar ist.
        Map<String, Object> target = new LinkedHashMap<>();
        ConfigurationSection existing = config.getConfigurationSection(TARGET);
        if (existing != null) {
            for (String id : existing.getKeys(false)) {
                ConfigurationSection set = existing.getConfigurationSection(id);
                if (set != null) {
                    target.put(id, toPlainMap(set));
                }
            }
        }

        for (String sectionName : LEGACY_SECTIONS) {
            ConfigurationSection section = config.getConfigurationSection(sectionName);
            if (section == null) {
                continue;
            }
            for (String id : section.getKeys(false)) {
                ConfigurationSection set = section.getConfigurationSection(id);
                if (set == null) {
                    continue;
                }

                Map<String, Object> values = translateFlags(toPlainMap(set));

                String targetId = id;
                if (target.containsKey(id)) {
                    // Genau diese Kollision ist die Ursache dafuer, dass PvP und Events heute
                    // verschiedene Sets unter einer ID sehen koennen. Nichts ueberschreiben.
                    targetId = id + "-legacy";
                    int suffix = 2;
                    while (target.containsKey(targetId)) {
                        targetId = id + "-legacy" + suffix++;
                    }
                    result.warnings.add("Equipment set '" + id + "' exists in both '" + TARGET  // i18n-ignore: migration warning, English console text
                            + "' and '" + sectionName + "' with different definitions. Kept the '"
                            + TARGET + "' one and imported the other as '" + targetId
                            + "'. Check which one your kits should use.");
                }

                target.put(targetId, values);
                result.migratedSets++;
                result.notes.add("equipment.yml: " + sectionName + "." + id + " -> " + TARGET + "." + targetId);  // i18n-ignore: migration note, English console text
            }
        }

        // Erst jetzt schreiben - vorher wurde aus genau diesen Sektionen gelesen.
        config.set(TARGET, null);
        ConfigurationSection targetSection = config.createSection(TARGET);
        for (Map.Entry<String, Object> entry : target.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> values = (Map<String, Object>) entry.getValue();
            // createSection(name, map) baut verschachtelte Maps wieder zu Unterabschnitten
            // auf. set(name, map) wuerde die Map als einzelnen Wert ablegen - die Felder
            // waeren dann ueber getString("...armor.helmet") nicht mehr erreichbar.
            targetSection.createSection(entry.getKey(), values);
        }
        for (String sectionName : LEGACY_SECTIONS) {
            config.set(sectionName, null);
        }

        return result;
    }

    /**
     * Wandelt einen Abschnitt in verschachtelte Maps um.
     *
     * <p>Bewusst nicht {@code getValues(true)}: das liefert eine flache Map mit punktierten
     * Schluesseln ({@code "armor.helmet"}), die beim Zurueckschreiben nicht wieder in
     * Unterabschnitte zerlegt wird. Die Ruestungs- und Inventardaten waeren danach im
     * Ergebnis vorhanden, aber fuer die Loader unlesbar.</p>
     */
    private static Map<String, Object> toPlainMap(ConfigurationSection section) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection) {
                result.put(key, toPlainMap((ConfigurationSection) value));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * Uebersetzt den gemeinsamen Schalter der Alt-Sektionen auf die zwei getrennten.
     *
     * <p>Die Alt-Sektionen kannten nur {@code enabled} fuer beide Systeme. Ein deaktiviertes
     * Set muss deshalb in <em>beiden</em> neuen Schaltern deaktiviert ankommen - sonst taucht
     * ein bewusst abgeschaltetes Kit nach der Migration wieder im Spiel auf.</p>
     */
    private static Map<String, Object> translateFlags(Map<String, Object> values) {
        Map<String, Object> copy = new LinkedHashMap<>(values);

        Object legacy = copy.remove(LEGACY_ENABLED);
        if (legacy != null) {
            boolean enabled = !(legacy instanceof Boolean) || (Boolean) legacy;
            // Bereits vorhandene, spezifischere Schalter haben Vorrang.
            copy.putIfAbsent(PVP_ENABLED, enabled);
            copy.putIfAbsent(EVENT_ENABLED, enabled);
        }
        return copy;
    }

    /**
     * Fuehrt die Migration inklusive Sicherung und Speichern aus.
     *
     * @param config        bereits geladene {@code equipment.yml}
     * @param file          die zugehoerige Datei
     * @param logger        fuer Hinweise und Kollisionswarnungen
     * @return das Ergebnis von {@link #merge(FileConfiguration)}
     */
    public static Result run(FileConfiguration config, File file, java.util.logging.Logger logger) {
        Result result = merge(config);
        if (!result.isChanged()) {
            return result;
        }

        // Sicherung vor dem ersten Schreibzugriff. Schlaegt sie fehl, wird nicht migriert -
        // eine Konfiguration ohne Rueckweg umzuschreiben waere das schlechtere Ergebnis.
        File backup = new File(file.getParentFile(),
                file.getName() + ".bak-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()));
        try {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.warning("Could not back up equipment.yml before migrating (" + e.getMessage()  // i18n-ignore: migration note, runs before language bundle load
                    + ") - keeping the file unchanged. The old sections stay in use for now.");
            return new Result(false);
        }

        try {
            config.save(file);
        } catch (IOException e) {
            logger.warning("Could not save equipment.yml after migrating: " + e.getMessage()  // i18n-ignore: migration note, runs before language bundle load
                    + " - will retry on the next startup. A backup is at " + backup.getName());
            return new Result(false);
        }

        logger.info("equipment.yml: merged " + result.getMigratedSets()  // i18n-ignore: migration note, runs before language bundle load
                + " set(s) into the single '" + TARGET + "' section. Backup: " + backup.getName());
        for (String note : result.getNotes()) {
            logger.info(note);  // i18n-ignore: migration note, runs before language bundle load
        }
        for (String warning : result.getWarnings()) {
            logger.warning(warning);  // i18n-ignore: migration note, runs before language bundle load
        }
        return result;
    }
}
