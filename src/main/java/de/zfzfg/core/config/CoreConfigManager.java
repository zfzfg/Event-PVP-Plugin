package de.zfzfg.core.config;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

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

    public CoreConfigManager(EventPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // Hauptconfig
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            // Fallback: events-config.yml falls vorhanden
            File legacy = new File(plugin.getDataFolder(), "events-config.yml");
            if (legacy.exists()) configFile = legacy; else plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

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

        // Equipment
        equipmentFile = new File(plugin.getDataFolder(), "equipment.yml");
        if (!equipmentFile.exists()) {
            File legacy = new File(plugin.getDataFolder(), "events-equipment.yml");
            if (legacy.exists()) equipmentFile = legacy; else plugin.saveResource("equipment.yml", false);
        }
        equipment = YamlConfiguration.loadConfiguration(equipmentFile);
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
        plugin.getLogger().info("Messages für Sprache '" + language + "' geladen (" + filename + ")");
    }

    public void reloadAll() {
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Messages mit aktueller Sprache neu laden
        String language = config.getString("settings.language", "en");
        loadMessagesForLanguage(language);
        
        worlds = YamlConfiguration.loadConfiguration(worldsFile);
        equipment = YamlConfiguration.loadConfiguration(equipmentFile);
        plugin.getLogger().info("Core-Konfigurationen neu geladen.");
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
            plugin.getLogger().info("Sprache geändert zu: " + language);
            
            // Messages neu laden
            loadMessagesForLanguage(language);
        } catch (Exception e) {
            plugin.getLogger().severe("Fehler beim Speichern der Sprache: " + e.getMessage());
        }
    }

    public FileConfiguration getConfig() { return config; }
    public FileConfiguration getMessages() { return messages; }
    public FileConfiguration getWorlds() { return worlds; }
    public FileConfiguration getEquipment() { return equipment; }
}