package de.zfzfg.core.config;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Zentraler Config-Manager für vereinte Konfigurationen:
 * - config.yml (allgemeine Einstellungen)
 * - messages.yml (Nachrichten)
 * - worlds.yml (Welt-/Arena-Definitionen für Events & PvP)
 * - equipment.yml (Ausrüstungen für Events & PvP)
 */
public class CoreConfigManager {

    private final EventPlugin plugin;

    private File configFile;
    private File messagesFile;
    private File worldsFile;
    private File equipmentFile;

    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration worlds;
    private FileConfiguration equipment;

    /**
     * Versionsstempel der config.yml, wie sie beim Start vorlag.
     *
     * Wird gebraucht, weil die Migration den Stempel am Ende von {@link #load()} hochsetzt,
     * der {@link de.zfzfg.core.web.WebConfigManager} aber erst danach entsteht und dann
     * sonst nie erfahren würde, dass seine Datei noch vom alten Stand ist.
     */
    private int detectedConfigVersion = ConfigMigrationService.CURRENT_VERSION;

    /** Was die Migration verändert hat - wird am Ende von {@link #load()} ausgegeben. */
    private final List<String> migrationNotes = new ArrayList<>();
    private final List<String> migrationWarnings = new ArrayList<>();

    public CoreConfigManager(EventPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        migrationNotes.clear();
        migrationWarnings.clear();

        // Hauptconfig
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            // Fallback: events-config.yml falls vorhanden
            File legacy = new File(plugin.getDataFolder(), "events-config.yml");
            if (legacy.exists()) configFile = legacy; else plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        detectedConfigVersion = ConfigMigrationService.readVersion(config);

        // Altlasten umschreiben und fehlende Schlüssel ergänzen, bevor die Modul-Manager
        // dieselbe Datei erneut einlesen: load() läuft in EventPlugin.onEnable() vor dem
        // Event- und dem PvP-ConfigManager, beide sehen danach bereits den neuen Stand.
        // Gespeichert wird erst am Ende von load() - die Modul-Manager lesen die Datei
        // ohnehin erst danach erneut ein.
        ConfigMigrationService.Result configResult =
                ConfigMigrationService.rewriteConfig(config, detectedConfigVersion);

        // Messages - Sprache aus config laden
        String language = config.getString("settings.language", "en");
        loadMessagesForLanguage(language);

        // Worlds/Arenas
        worldsFile = new File(plugin.getDataFolder(), "worlds.yml");
        if (!worldsFile.exists()) {
            File legacy = new File(plugin.getDataFolder(), "arenas.yml");
            if (legacy.exists()) worldsFile = legacy; else plugin.saveResource("worlds.yml", false);
        }
        worlds = YamlConfiguration.loadConfiguration(worldsFile);
        migrateFile(worlds, worldsFile, "worlds.yml",
                ConfigMigrationService.rewriteWorlds(worlds, detectedConfigVersion));

        // Equipment
        equipmentFile = new File(plugin.getDataFolder(), "equipment.yml");
        if (!equipmentFile.exists()) {
            File legacy = new File(plugin.getDataFolder(), "events-equipment.yml");
            if (legacy.exists()) equipmentFile = legacy; else plugin.saveResource("equipment.yml", false);
        }
        equipment = YamlConfiguration.loadConfiguration(equipmentFile);

        // Die drei historischen Set-Sektionen zu einer zusammenfuehren, bevor der PvP- und der
        // Event-ConfigManager dieselbe Datei einlesen. Beide bevorzugten bis 1.0.9
        // entgegengesetzte Sektionen und konnten deshalb verschiedene Sets unter einer ID sehen.
        // Muss vor dem Auffüllen laufen: sonst stünden neue Schlüssel in der Alt-Sektion.
        EquipmentSchemaMigration.run(equipment, equipmentFile, plugin.getLogger());
        migrateFile(equipment, equipmentFile, "equipment.yml", new ConfigMigrationService.Result());

        // Der Stempel kommt zuletzt: erst wenn alle Dateien durch sind, gilt die Installation
        // als migriert. Scheitert vorher ein Schreibzugriff, läuft alles beim nächsten Start
        // erneut - statt halb migriert liegenzubleiben.
        if (ConfigMigrationService.stampVersion(config)) {
            configResult.absorb(markStamped());
        }
        migrateFile(config, configFile, "config.yml", configResult);

        reportMigration();
    }

    /**
     * Führt Umschreibungen und das Auffüllen fehlender Schlüssel für eine Datei zusammen
     * und speichert sie, falls sich etwas geändert hat.
     */
    private void migrateFile(FileConfiguration configuration, File file, String resourceName,
                             ConfigMigrationService.Result result) {
        result.absorb(ConfigMigrationService.mergeMissing(
                configuration, jarDefaults(resourceName), ConfigMigrationService.ID_SECTIONS));

        if (!result.isChanged()) {
            return;
        }

        if (!ConfigMigrationService.save(configuration, file, plugin.getLogger())) {
            migrationWarnings.add(resourceName + ": could not be saved"  // i18n-ignore: migration note, matches EquipmentSchemaMigration
                    + " - the migration will be retried on the next startup.");
            return;
        }

        if (!result.getAddedKeys().isEmpty()) {
            migrationNotes.add(resourceName + ": added " + result.getAddedKeys().size()  // i18n-ignore: migration note, matches EquipmentSchemaMigration
                    + " new setting(s)");
        }
        migrationNotes.addAll(result.getNotes());
        migrationWarnings.addAll(result.getWarnings());
    }

    /** Kleiner Helfer, damit das Setzen des Stempels im Bericht auftaucht. */
    private ConfigMigrationService.Result markStamped() {
        ConfigMigrationService.Result result = new ConfigMigrationService.Result();
        result.note("config.yml: stamped " + ConfigMigrationService.VERSION_KEY + ": "  // i18n-ignore: migration note, matches EquipmentSchemaMigration
                + ConfigMigrationService.CURRENT_VERSION);
        return result;
    }

    /**
     * Lädt eine Vorlage aus dem Jar.
     *
     * Bewusst {@code getResource} statt der Datei im Datenordner: die Vorlage ist der
     * Sollstand dieser Plugin-Version, die Datei daneben der Iststand des Admins.
     *
     * @return {@code null}, wenn es die Resource nicht gibt - der Aufrufer ergänzt dann nichts
     */
    private FileConfiguration jarDefaults(String resourceName) {
        try (InputStream stream = plugin.getResource(resourceName)) {
            if (stream == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read the bundled " + resourceName  // i18n-ignore: migration note, may run before language bundle load
                    + " (" + e.getMessage() + ") - skipping the merge of new keys for this file.");
            return null;
        }
    }

    /** Gibt am Ende von {@link #load()} aus, was die Migration verändert hat. */
    private void reportMigration() {
        if (migrationNotes.isEmpty() && migrationWarnings.isEmpty()) {
            return;
        }

        plugin.getLogger().info(getConsoleMsg("migration-header"));
        for (String note : migrationNotes) {
            plugin.getLogger().info(getConsoleMsg("migration-entry", "entry", note));
        }
        for (String warning : migrationWarnings) {
            plugin.getLogger().warning(getConsoleMsg("migration-entry", "entry", warning));
        }
        plugin.getLogger().info(getConsoleMsg("migration-footer"));
    }

    /** Versionsstempel der config.yml, wie sie beim Serverstart vorlag. */
    public int getDetectedConfigVersion() {
        return detectedConfigVersion;
    }

    /** Meldungen der Migration - für das Web-Panel und Tests. */
    public List<String> getMigrationNotes() {
        return Collections.unmodifiableList(migrationNotes);
    }


    /**
     * Laden der Nachrichten-Datei basierend auf der konfigurierten Sprache
     * Unterstützt: de (Deutsch), en (Englisch), fr (Französisch), ja (Japanisch), ru (Russisch), es (Spanisch), pl (Polnisch)
     */
    private void loadMessagesForLanguage(String language) {
        // Gültige Sprachen: de, en, fr, ja, ru, es, pl
        if (!language.equals("de") && !language.equals("en") && !language.equals("fr") && !language.equals("ja") && !language.equals("ru") && !language.equals("es") && !language.equals("pl")) {
            language = "en"; // Fallback auf Englisch
        }
        
        String filename = "messages_" + language + ".yml";
        messagesFile = new File(plugin.getDataFolder(), filename);
        
        // Wenn die sprachspezifische Datei nicht existiert, speichern aus Resources
        if (!messagesFile.exists()) {
            plugin.saveResource(filename, false);
        }
        
        messages = YamlConfiguration.loadConfiguration(messagesFile);

        // Fehlende Texte aus der gleichsprachigen Jar-Vorlage nachtragen - nicht aus der
        // englischen. Ein deutscher Server, der seine messages_de.yml je angefasst hat,
        // bekommt so die neuen Texte auf Deutsch in die Datei statt sie über den
        // englischen Rückfall unten stumm auf Englisch auszugeben.
        // Ohne ID-Sektionen: in einer Sprachdatei bestimmt der Admin keine Schlüssel.
        ConfigMigrationService.Result messageResult = ConfigMigrationService.mergeMissing(
                messages, jarDefaults(filename), java.util.Collections.emptySet());
        if (messageResult.isChanged()
                && ConfigMigrationService.save(messages, messagesFile, plugin.getLogger())) {
            migrationNotes.add(filename + ": added " + messageResult.getAddedKeys().size()  // i18n-ignore: migration note, runs before language bundle load
                    + " new message(s)");
        }

        // English fallback defaults so missing keys in foreign/custom language files resolve to English
        try (java.io.InputStream defStream = plugin.getResource("messages_en.yml")) {
            if (defStream != null) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(defStream, java.nio.charset.StandardCharsets.UTF_8)
                );
                messages.setDefaults(defConfig);
            }
        } catch (Exception e) {
            plugin.getLogger().warning(getConsoleMsg("lang-fallback-error", "error", e.getMessage()));
        }
        
        plugin.getLogger().info(getConsoleMsg("lang-loaded", "lang", language, "file", filename));
    }

    public void reloadAll() {
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Messages mit aktueller Sprache neu laden
        String language = config.getString("settings.language", "en");
        loadMessagesForLanguage(language);
        
        worlds = YamlConfiguration.loadConfiguration(worldsFile);
        equipment = YamlConfiguration.loadConfiguration(equipmentFile);
        plugin.getLogger().info(getConsoleMsg("core-reload"));
    }
    
    /**
     * Gibt die aktuell konfigurierte Sprache zurück
     */
    public String getLanguage() {
        return config.getString("settings.language", "en");
    }
    
    /**
     * Setzt die Sprache und speichert sie in der config.yml
     * Lädt auch automatisch die passende messages_XX.yml neu
     */
    public void setLanguage(String language) {
        // Gültige Sprachen: de, en, fr, ja, ru, es, pl
        if (!language.equals("de") && !language.equals("en") && !language.equals("fr") && !language.equals("ja") && !language.equals("ru") && !language.equals("es") && !language.equals("pl")) {
            language = "en"; // Fallback auf Englisch
        }
        
        config.set("settings.language", language);
        
        try {
            config.save(configFile);
            plugin.getLogger().info(getConsoleMsg("lang-changed", "lang", language));
            
            // Messages neu laden
            loadMessagesForLanguage(language);
        } catch (Exception e) {
            plugin.getLogger().severe(getConsoleMsg("lang-save-error", "error", e.getMessage()));
        }
    }

    /**
     * Gibt den rohen Wert von settings.debug zurück ("off", "on", "full").
     */
    public String getDebugSetting() {
        return config.getString("settings.debug", "off");
    }

    /**
     * Setzt settings.debug und speichert die config.yml.
     */
    public void setDebugSetting(String value) {
        config.set("settings.debug", value);

        try {
            config.save(configFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save debug setting: " + e.getMessage()); // i18n-ignore: technical config save log
        }
    }

    /**
     * Holt eine lokalisierte Konsolen-/Terminal-Nachricht aus messages.console.<key>
     */
    public String getConsoleMsg(String key, String... replacements) {
        if (key == null || key.isEmpty()) return "";
        String msg = null;
        if (messages != null) {
            msg = messages.getString("messages.console." + key);
            if (msg == null) {
                msg = messages.getString("messages.system." + key);
            }
        }
        if (msg == null) {
            msg = "&c[missing: messages.console." + key + "]";
        }
        if (replacements != null && replacements.length > 0) {
            for (int i = 0; i < replacements.length - 1; i += 2) {
                String raw = replacements[i] != null ? replacements[i].replaceAll("^[{%]+|[%}]+$", "") : "";
                String val = replacements[i + 1] != null ? replacements[i + 1] : "";
                if (!raw.isEmpty()) {
                    msg = msg.replace("{" + raw + "}", val)
                             .replace("%" + raw + "%", val);
                }
            }
        }
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', msg);
    }

    public FileConfiguration getConfig() { return config; }
    public FileConfiguration getMessages() { return messages; }
    public FileConfiguration getWorlds() { return worlds; }
    public FileConfiguration getEquipment() { return equipment; }
}