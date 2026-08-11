package de.zfzfg.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Bringt die Konfigurationsdateien eines bestehenden Servers auf den Stand dieser Version.
 *
 * <h2>Warum das noetig ist</h2>
 * <p>Das Plugin kannte bis 1.0.8 weder {@code copyDefaults()} noch einen Versionsstempel. Wer
 * das Jar tauschte, behielt seine alten Dateien unveraendert - neue Schluessel tauchten darin
 * nie auf. Die neuen Funktionen liefen dann ausschliesslich auf den im Java-Code hinterlegten
 * Rueckfallwerten: fuer den Admin unsichtbar und nicht einstellbar. Umgekehrt blieben
 * abgeloeste Schluessel stehen und sahen weiter nach einer wirksamen Einstellung aus,
 * obwohl sie niemand mehr liest.</p>
 *
 * <h2>Zwei getrennte Aufgaben</h2>
 * <ol>
 *   <li>{@link #rewriteConfig(FileConfiguration, int)} und die Geschwister schreiben
 *       abgeloeste Schluessel einmalig auf ihre Nachfolger um. Sie haengen am Versionsstempel
 *       {@link #VERSION_KEY} und laufen genau einmal.</li>
 *   <li>{@link #mergeMissing} ergaenzt fehlende Schluessel aus der Jar-Vorlage. Das laeuft
 *       bewusst <em>versionsunabhaengig</em> bei jedem Start, damit auch von Hand gekuerzte
 *       oder halb migrierte Dateien wieder vollstaendig werden.</li>
 * </ol>
 *
 * <p>Die Umschreibregeln arbeiten nur im Speicher; Sicherung und Speichern erledigt
 * {@link #save(FileConfiguration, File, Logger)}. Diese Trennung macht sie ohne Server und
 * Dateisystem testbar - dasselbe Muster wie in {@link EquipmentSchemaMigration}.</p>
 */
public final class ConfigMigrationService {

    /** Versionsstempel in {@code config.yml}. Fehlt er, stammt die Datei aus 1.0.8 oder aelter. */
    public static final String VERSION_KEY = "config-version";

    /** Stand, den diese Plugin-Version erwartet. */
    public static final int CURRENT_VERSION = 2;

    /** Version einer Datei ohne Stempel. */
    public static final int LEGACY_VERSION = 1;

    /**
     * Sektionen, in denen der Admin die Schluesselmenge bestimmt.
     *
     * <p>Unterhalb dieser Wurzeln steht auf der ersten Ebene eine vom Admin vergebene ID
     * (Event-, Set- oder Weltname). Wuerde der Merge dort nach Jar-Vorlage auffuellen, kaeme
     * jedes bewusst geloeschte Beispiel-Event bei jedem Serverstart zurueck. Innerhalb
     * <em>vorhandener</em> IDs wird dagegen normal ergaenzt, damit neue Optionen eines Events
     * auch in bestehenden Eintraegen ankommen.</p>
     */
    public static final Set<String> ID_SECTIONS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("events", "equipment", "worlds")));

    private ConfigMigrationService() {
    }

    /** Ergebnis eines Laufs - erlaubt Tests ohne Server und Dateisystem. */
    public static final class Result {
        private final List<String> notes = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> addedKeys = new ArrayList<>();
        private boolean changed;

        public boolean isChanged() { return changed; }
        public List<String> getNotes() { return notes; }
        public List<String> getWarnings() { return warnings; }
        public List<String> getAddedKeys() { return addedKeys; }

        void note(String text) {
            notes.add(text);
            changed = true;
        }

        void warn(String text) {
            warnings.add(text);
        }

        void added(String path) {
            addedKeys.add(path);
            changed = true;
        }

        /** Fuehrt das Ergebnis eines weiteren Laufs auf derselben Datei mit diesem zusammen. */
        public void absorb(Result other) {
            notes.addAll(other.notes);
            warnings.addAll(other.warnings);
            addedKeys.addAll(other.addedKeys);
            changed |= other.changed;
        }
    }

    // ==================================================================================
    // Versionsstempel
    // ==================================================================================

    /**
     * Liest den Versionsstempel einer Konfiguration.
     *
     * @return {@link #LEGACY_VERSION}, wenn kein Stempel vorhanden ist
     */
    public static int readVersion(FileConfiguration config) {
        return config.getInt(VERSION_KEY, LEGACY_VERSION);
    }

    /** Setzt den Stempel auf {@link #CURRENT_VERSION}, falls er noch nicht dort steht. */
    public static boolean stampVersion(FileConfiguration config) {
        if (config.getInt(VERSION_KEY, LEGACY_VERSION) >= CURRENT_VERSION) {
            return false;
        }
        config.set(VERSION_KEY, CURRENT_VERSION);
        return true;
    }

    // ==================================================================================
    // Umschreibungen
    // ==================================================================================

    /**
     * Schreibt abgeloeste Schluessel der {@code config.yml} auf ihre Nachfolger um.
     *
     * <p>Setzt den Stempel <b>nicht</b> - das erledigt der Aufrufer, nachdem auch die
     * uebrigen Dateien migriert sind. Sonst gaelte die config.yml bereits als aktuell,
     * waehrend web-config.yml und worlds.yml noch auf dem alten Stand liegen.</p>
     *
     * @param fromVersion Stempel der Datei vor dem Lauf, siehe {@link #readVersion}
     */
    public static Result rewriteConfig(FileConfiguration config, int fromVersion) {
        Result result = new Result();
        if (fromVersion >= CURRENT_VERSION) {
            return result;
        }

        // settings.world-loading (ein String fuer zwei Module) ->
        // settings.world-management.events / .arenas (zwei unabhaengige Schalter)
        if (config.isSet("settings.world-loading") && !config.isSet("settings.world-management")) {
            String legacy = String.valueOf(config.get("settings.world-loading")).trim().toLowerCase();

            boolean events;
            boolean arenas;
            switch (legacy) {
                case "none":
                    events = false;
                    arenas = false;
                    break;
                case "arena":
                    events = false;
                    arenas = true;
                    break;
                case "event":
                case "lobby":
                    // Das Event-Modul verwarf diese Werte ohnehin und fiel auf "both"
                    // zurueck; Arenen entluden nur bei "arena"/"both".
                    events = true;
                    arenas = false;
                    break;
                default:
                    // "both" sowie die nie ausgewerteten Werte "clone"/"load"
                    events = true;
                    arenas = true;
                    break;
            }

            config.set("settings.world-management.events", events);
            config.set("settings.world-management.arenas", arenas);
            config.set("settings.world-loading", null);
            result.note("config.yml: settings.world-loading: '" + legacy  // i18n-ignore: migration note, runs before language bundle load
                    + "' -> settings.world-management.events: " + events + ", .arenas: " + arenas);
        }

        // settings.command-restriction: "join" wurde nie ausgewertet, "pvp" war nie gueltig -
        // beide verhielten sich wie "both".
        String restriction = config.getString("settings.command-restriction");
        if (restriction != null) {
            String value = restriction.trim().toLowerCase();
            if (value.equals("join") || value.equals("pvp")) {
                config.set("settings.command-restriction", "both");
                result.note("config.yml: settings.command-restriction: '" + value  // i18n-ignore: migration note, runs before language bundle load
                        + "' -> 'both' (the old value was inoperative)");
            }
        }

        rewriteInventorySnapshots(config, result);
        collapseInventoryProviderAlias(config, result);

        // settings.inventory-group wurde vom Code gelesen, stand aber in keiner Vorlage und
        // hat seit dem Wegfall der eigenen Snapshot-Verwaltung keine Wirkung mehr.
        if (config.isSet("settings.inventory-group")) {
            config.set("settings.inventory-group", null);
            result.note("config.yml: removed settings.inventory-group (it had no effect)");  // i18n-ignore: migration note, runs before language bundle load
        }

        return result;
    }

    /**
     * Schreibt den Altwert {@code provider: inventoryrestore} auf {@code auto} um.
     *
     * <p>Beide Werte taten von Anfang an dasselbe - es gab nie eine Codestelle, die zwischen
     * ihnen unterschieden haette. Zwei Eintraege fuer eine Betriebsart haben im Panel
     * lediglich die Frage aufgeworfen, worin der Unterschied bestehe. Der Altwert wird
     * weiterhin gelesen, aber nicht mehr geschrieben.</p>
     */
    private static void collapseInventoryProviderAlias(FileConfiguration config, Result result) {
        String provider = config.getString("settings.inventory-management.provider");
        if (provider == null || !"inventoryrestore".equalsIgnoreCase(provider.trim())) {
            return;
        }
        config.set("settings.inventory-management.provider", "auto");
        result.note("config.yml: settings.inventory-management.provider: 'inventoryrestore' -> 'auto'"  // i18n-ignore: migration note, runs before language bundle load
                + " (both values always behaved identically)");
    }

    /**
     * Uebersetzt die abgeloeste eigene Snapshot-Verwaltung auf den neuen Provider-Schalter.
     *
     * <p>Bis 1.0.8 sicherte das Plugin Inventare selbst nach {@code inventory_backups.yml}.
     * Ab 1.0.9 uebernimmt das Plugin InventoryBackup. Uebertragbar ist davon genau eine
     * Entscheidung: ob das Plugin die Inventare ueberhaupt anfassen soll. Ein abgeschaltetes
     * {@code inventory-snapshots.enabled} bedeutete "ein anderes Plugin verwaltet das" - das
     * entspricht dem neuen {@code provider: none}. Alles andere - Aufbewahrungsdauer,
     * Gruppen, ID-Laengen - liegt jetzt bei InventoryBackup und hat hier keine Entsprechung
     * mehr; diese Schluessel werden entfernt und gemeldet, statt stillschweigend
     * wirkungslos stehenzubleiben.</p>
     */
    private static void rewriteInventorySnapshots(FileConfiguration config, Result result) {
        if (!config.isSet("settings.inventory-snapshots")) {
            return;
        }

        boolean enabled = config.getBoolean("settings.inventory-snapshots.enabled", true);

        if (!config.isSet("settings.inventory-management.provider")) {
            String provider = enabled ? "auto" : "none";
            config.set("settings.inventory-management.provider", provider);
            result.note("config.yml: settings.inventory-snapshots.enabled: " + enabled  // i18n-ignore: migration note, runs before language bundle load
                    + " -> settings.inventory-management.provider: '" + provider + "'");
        }

        if (!enabled && !config.isSet("settings.inventory-management.legacy-safety-backups")) {
            // Im Legacy-Betrieb weiter Sicherungskopien anlegen. Der alte Zustand kannte
            // dieses Netz nicht, aber ohne es waere ein Fehlschlag beim Inventartausch
            // unwiederbringlich - und genau davor soll das Update nicht schlechter stehen.
            config.set("settings.inventory-management.legacy-safety-backups", true);
            result.note("config.yml: set settings.inventory-management.legacy-safety-backups: true"  // i18n-ignore: migration note, runs before language bundle load
                    + " (safety copies while another plugin swaps inventories)");
        }

        List<String> dropped = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("settings.inventory-snapshots");
        if (section != null) {
            for (String key : section.getKeys(true)) {
                if (!section.isConfigurationSection(key) && !key.equals("enabled")) {
                    dropped.add("settings.inventory-snapshots." + key);
                }
            }
        }

        config.set("settings.inventory-snapshots", null);
        result.note("config.yml: removed settings.inventory-snapshots"  // i18n-ignore: migration note, runs before language bundle load
                + " - inventories are handled by the InventoryBackup plugin now");
        if (!dropped.isEmpty()) {
            result.warn("config.yml: these settings have no counterpart any more and were removed"  // i18n-ignore: migration note, runs before language bundle load
                    + " - retention is InventoryBackup's job now: " + String.join(", ", dropped));
        }
    }

    /** Entfernt die abgeloesten Textur-Schluessel der {@code web-config.yml}. */
    public static Result rewriteWebConfig(FileConfiguration webConfig, int fromVersion) {
        Result result = new Result();
        if (fromVersion >= CURRENT_VERSION) {
            return result;
        }

        for (String path : new String[] { "items.local-texture-path", "items.block-texture-source" }) {
            if (webConfig.isSet(path)) {
                webConfig.set(path, null);
                result.note("web-config.yml: " + path + " removed"  // i18n-ignore: migration note, runs before language bundle load
                        + " (superseded by items.resource-pack.*)");
            }
        }

        return result;
    }

    /**
     * Entfernt fehlgeparste Kommentarzeilen aus {@code worlds.yml}.
     *
     * <p>In der ausgelieferten Vorlage bis 1.0.9 fehlte zwei Kommentarzeilen unterhalb von
     * {@code worlds.PvPArena} das {@code #}. YAML liest sie deshalb als Schluessel - sie
     * stehen also in der Datei jedes Servers, der die Vorlage je uebernommen hat, und
     * erscheinen dort auch im Web-Panel. Erkennungsmerkmal ist der Leerraum im Namen: kein
     * echter Schluessel dieser Datei enthaelt einen.</p>
     */
    public static Result rewriteWorlds(FileConfiguration worlds, int fromVersion) {
        Result result = new Result();
        if (fromVersion >= CURRENT_VERSION) {
            return result;
        }

        ConfigurationSection root = worlds.getConfigurationSection("worlds");
        if (root == null) {
            return result;
        }

        for (String worldId : root.getKeys(false)) {
            ConfigurationSection world = root.getConfigurationSection(worldId);
            if (world == null) {
                continue;
            }
            for (String key : new ArrayList<>(world.getKeys(false))) {
                if (key.contains(" ") || key.equals("Optional")) {
                    world.set(key, null);
                    result.note("worlds.yml: worlds." + worldId + "." + key  // i18n-ignore: migration note, runs before language bundle load
                            + " removed (a comment line of the old template that lost its '#')");
                }
            }
        }

        return result;
    }

    // ==================================================================================
    // Auffuellen fehlender Schluessel
    // ==================================================================================

    /**
     * Ergaenzt in {@code user} jeden Schluessel, den die Jar-Vorlage kennt und die Datei nicht.
     *
     * <p>Vorhandene Werte werden nie ueberschrieben - auch nicht, wenn sie vom Standard
     * abweichen. Zu jedem neu gesetzten Schluessel wird der Kommentar der Vorlage
     * uebernommen, damit die Erklaerung zum neuen Schalter direkt in der Datei des Admins
     * steht und nicht nur im Wiki.</p>
     *
     * @param idSections Wurzeln, unter denen keine neuen IDs angelegt werden duerfen,
     *                   siehe {@link #ID_SECTIONS}
     */
    public static Result mergeMissing(FileConfiguration user, ConfigurationSection defaults, Set<String> idSections) {
        Result result = new Result();
        if (defaults == null) {
            return result;
        }
        for (String key : defaults.getKeys(false)) {
            walk(user, defaults, key, key, idSections, result);
        }
        return result;
    }

    private static void walk(FileConfiguration user, ConfigurationSection defParent, String key,
                             String path, Set<String> idSections, Result result) {
        // Eine vom Admin geloeschte ID darf nicht aus der Vorlage zurueckkehren.
        if (isManagedId(path, idSections) && !user.isSet(path)) {
            return;
        }

        Object value = defParent.get(key);

        if (!(value instanceof ConfigurationSection)) {
            if (!user.isSet(path)) {
                user.set(path, value);
                copyComments(user, defParent, key, path);
                result.added(path);
            } else {
                backfillComments(user, defParent, key, path);
            }
            return;
        }

        ConfigurationSection defSection = (ConfigurationSection) value;
        Set<String> children = defSection.getKeys(false);

        if (children.isEmpty()) {
            if (!user.isSet(path)) {
                user.createSection(path);
                copyComments(user, defParent, key, path);
                result.added(path);
            }
            return;
        }

        // Fehlt die Sektion ganz, entsteht sie beim Setzen des ersten Kindes von selbst.
        // Ihr Kommentar erklaert aber den ganzen Block und muss deshalb vorher gesetzt
        // werden - danach ist nicht mehr erkennbar, ob sie neu war.
        boolean sectionIsNew = !user.isSet(path);

        for (String child : children) {
            walk(user, defSection, child, path + "." + child, idSections, result);
        }

        if (!user.isSet(path)) {
            return;
        }
        if (sectionIsNew) {
            copyComments(user, defParent, key, path);
        } else {
            backfillComments(user, defParent, key, path);
        }
    }

    /**
     * Prueft, ob ein Pfad genau eine vom Admin vergebene ID bezeichnet.
     *
     * <p>Also {@code events.pvparena}, aber weder {@code events} noch
     * {@code events.pvparena.min-players}: Letzteres soll in einem vorhandenen Event sehr
     * wohl ergaenzt werden.</p>
     */
    static boolean isManagedId(String path, Set<String> idSections) {
        int firstDot = path.indexOf('.');
        if (firstDot < 0) {
            return false;
        }
        if (!idSections.contains(path.substring(0, firstDot))) {
            return false;
        }
        return path.indexOf('.', firstDot + 1) < 0;
    }

    /**
     * Traegt den Kommentar der Vorlage nach, wenn an dieser Stelle noch keiner steht.
     *
     * <p>Nötig, weil die Umschreibregeln Schluessel selbst anlegen - {@code provider} und der
     * ganze Block {@code settings.inventory-management} entstehen in
     * {@link #rewriteInventorySnapshots}, nicht im Merge. Ohne dieses Nachtragen kaeme
     * ausgerechnet die Einstellung, die das Update erklaert, ohne ihre Erklaerung in der
     * Datei an.</p>
     *
     * <p>Zaehlt bewusst <b>nicht</b> als Aenderung: ein fehlender Kommentar allein
     * rechtfertigt kein Umschreiben der Datei samt Sicherungskopie. Er wird mitgeschrieben,
     * wenn ohnehin gespeichert wird - und das ist genau der Migrationslauf. Ein vom Admin
     * selbst geschriebener Kommentar wird nie ersetzt.</p>
     */
    private static void backfillComments(FileConfiguration user, ConfigurationSection defParent,
                                         String key, String path) {
        try {
            if (user.getComments(path).isEmpty()) {
                List<String> comments = defParent.getComments(key);
                if (!comments.isEmpty()) {
                    user.setComments(path, comments);
                }
            }
        } catch (Throwable ignored) {
            // Kommentare sind Beiwerk - der Wert steht bereits.
        }
    }

    /**
     * Uebernimmt Kommentare der Vorlage fuer einen neu gesetzten Schluessel.
     *
     * <p>Fehlschlaege werden geschluckt: die Kommentar-API kam erst mit 1.18, und ein
     * fehlender Kommentar ist kein Grund, eine sonst gelungene Migration abzubrechen.</p>
     */
    private static void copyComments(FileConfiguration user, ConfigurationSection defParent,
                                     String key, String path) {
        try {
            List<String> comments = defParent.getComments(key);
            if (!comments.isEmpty()) {
                user.setComments(path, comments);
            }
            List<String> inline = defParent.getInlineComments(key);
            if (!inline.isEmpty()) {
                user.setInlineComments(path, inline);
            }
        } catch (Throwable ignored) {
            // Kommentare sind Beiwerk - der Wert steht bereits.
        }
    }

    // ==================================================================================
    // Dateizugriff
    // ==================================================================================

    /**
     * Sichert die Datei und speichert die geaenderte Konfiguration.
     *
     * <p>Schlaegt die Sicherung fehl, bleibt die Datei unangetastet: eine Konfiguration ohne
     * Rueckweg umzuschreiben waere das schlechtere Ergebnis. Der naechste Serverstart
     * versucht es erneut, weil der Versionsstempel dann noch nicht geschrieben ist.</p>
     *
     * @return {@code true}, wenn gespeichert wurde
     */
    public static boolean save(FileConfiguration config, File file, Logger logger) {
        File backup = new File(file.getParentFile(),
                file.getName() + ".bak-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()));
        try {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.warning("Could not back up " + file.getName() + " before migrating ("  // i18n-ignore: migration note, runs before language bundle load
                    + e.getMessage() + ") - keeping the file unchanged, will retry on the next startup.");
            return false;
        }

        try {
            config.save(file);
        } catch (IOException e) {
            logger.warning("Could not save " + file.getName() + " after migrating: "  // i18n-ignore: migration note, runs before language bundle load
                    + e.getMessage() + " - will retry on the next startup. A backup is at " + backup.getName());
            return false;
        }

        logger.info("Migrated " + file.getName() + ". Backup: " + backup.getName());  // i18n-ignore: migration note, runs before language bundle load
        return true;
    }
}
