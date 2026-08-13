package de.zfzfg.core.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import de.zfzfg.core.config.ConfigMigrationService;
import de.zfzfg.eventplugin.EventPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

/**
 * Verwaltet die Web-Interface Konfiguration und YAML-Operationen
 * Verwendet Gson für JSON-Konvertierung (in Spigot eingebaut)
 */
public class WebConfigManager {

    private final EventPlugin plugin;
    private final Gson gson;
    
    private FileConfiguration webConfig;
    private File webConfigFile;

    public WebConfigManager(EventPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadWebConfig();
    }

    /**
     * Lädt die Web-Interface Konfiguration
     */
    private void loadWebConfig() {
        webConfigFile = new File(plugin.getDataFolder(), "web-config.yml");
        if (!webConfigFile.exists()) {
            plugin.getLogger().info("web-config.yml not found, creating default file...");  // i18n-ignore: initial setup trace
            plugin.saveResource("web-config.yml", false);
        }
        webConfig = YamlConfiguration.loadConfiguration(webConfigFile);

        // Abgelöste Schlüssel umschreiben und neue aus der Jar-Vorlage nachtragen. Die
        // Versionsnummer kommt vom CoreConfigManager, weil der Stempel in der config.yml
        // steht und dort am Ende von dessen load() bereits hochgesetzt wurde - diese
        // Klasse entsteht erst danach und würde sonst nie erfahren, dass sie migrieren muss.
        int fromVersion = plugin.getCoreConfigManager() != null
                ? plugin.getCoreConfigManager().getDetectedConfigVersion()
                : ConfigMigrationService.CURRENT_VERSION;

        ConfigMigrationService.Result result =
                ConfigMigrationService.rewriteWebConfig(webConfig, fromVersion);
        result.absorb(ConfigMigrationService.mergeMissing(
                webConfig, jarDefaults(), ConfigMigrationService.ID_SECTIONS));

        if (result.isChanged()) {
            ConfigMigrationService.save(webConfig, webConfigFile, plugin.getLogger());
        }

        // Debug: Show loaded values
        plugin.getDebugManager().log("WebConfig loaded: enabled=" + webConfig.getBoolean("web.enabled", true) + ", port=" + webConfig.getInt("web.port", 8085) + ", public-url=" + webConfig.getString("web.public-url", "NOT FOUND")); // i18n-ignore: debug trace
    }

    /**
     * Lädt die mitgelieferte web-config.yml aus dem Jar als Sollstand für den Merge.
     *
     * @return {@code null}, wenn die Resource fehlt - dann wird nichts ergänzt
     */
    private FileConfiguration jarDefaults() {
        try (InputStream stream = plugin.getResource("web-config.yml")) {
            if (stream == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read the bundled web-config.yml ("  // i18n-ignore: technical config log
                    + e.getMessage() + ") - skipping the merge of new keys.");
            return null;
        }
    }

    /**
     * Speichert die Web-Interface Konfiguration
     */
    public void saveWebConfig() {
        try {
            webConfig.save(webConfigFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving web-config.yml", e);  // i18n-ignore: technical exception log
        }
    }

    /**
     * Gibt den konfigurierten Port zurück
     */
    public int getPort() {
        return webConfig.getInt("web.port", 8085);
    }

    /**
     * Prüft ob der Web-Server aktiviert ist
     */
    public boolean isEnabled() {
        return webConfig.getBoolean("web.enabled", true);
    }
    
    /**
     * Gibt die konfigurierte Bind-Addresse zurück (leer = alle Interfaces)
     */
    public String getBindAddress() {
        return webConfig.getString("web.bind-address", "");
    }

    /**
     * Prüft, ob Item-Texturen aus dem Server-Resourcepack übernommen werden sollen.
     *
     * <p>Standardmäßig aus: die Übernahme lädt beim Start eine externe Datei herunter,
     * das soll niemand ungefragt bekommen.</p>
     */
    public boolean isResourcePackTexturesEnabled() {
        return webConfig.getBoolean("items.resource-pack.enabled", false);
    }

    /** Obergrenze für das heruntergeladene Resourcepack in Megabyte. */
    public int getResourcePackMaxSizeMb() {
        return webConfig.getInt("items.resource-pack.max-size-mb", 50);
    }

    /**
     * Prüft ob Authentifizierung aktiviert ist
     */
    public boolean isAuthEnabled() {
        return webConfig.getBoolean("security.auth-enabled", true);
    }
    
    /**
     * Gibt die Token-Gültigkeit in Minuten zurück
     */
    public long getTokenValidityMinutes() {
        return webConfig.getLong("security.token-validity-minutes", 10);
    }
    
    /**
     * Gibt die Session-Gültigkeit in Minuten zurück
     */
    public long getSessionValidityMinutes() {
        return webConfig.getLong("security.session-validity-minutes", 60);
    }
    
    /**
     * Gibt die öffentliche URL für das Web-Interface zurück
     * Ersetzt {port} durch den konfigurierten Port
     */
    public String getPublicUrl() {
        String url = webConfig.getString("web.public-url", "http://localhost:{port}");
        int port = getPort();
        String finalUrl = url.replace("{port}", String.valueOf(port));
        
        // Debug-Log
        plugin.getDebugManager().log("WebConfig - Loading public-url: '" + url + "' -> '" + finalUrl + "'");  // i18n-ignore: debug trace
        
        return finalUrl;
    }

    // ============ Config Operationen ============

    /**
     * Liest die config.yml und konvertiert zu Map
     */
    public Map<String, Object> getConfigAsMap() {
        FileConfiguration config = plugin.getConfig();
        return configSectionToMap(config);
    }

    /**
     * Speichert die Config aus einer Map
     */
    public void saveConfigFromMap(Map<String, Object> data) {
        FileConfiguration config = plugin.getConfig();
        clearAndSetConfig(config, data);
        plugin.saveConfig();
        plugin.reloadConfig();
    }

    // ============ Worlds.yml Operationen ============

    /**
     * Liest die worlds.yml und konvertiert zu Map
     */
    public Map<String, Object> getWorldsAsMap() {
        File worldsFile = new File(plugin.getDataFolder(), "worlds.yml");
        if (!worldsFile.exists()) {
            return new LinkedHashMap<>();
        }
        FileConfiguration worldsConfig = YamlConfiguration.loadConfiguration(worldsFile);
        return configSectionToMap(worldsConfig);
    }

    /**
     * Speichert die Worlds aus einer Map
     */
    public void saveWorldsFromMap(Map<String, Object> data) {
        File worldsFile = new File(plugin.getDataFolder(), "worlds.yml");
        FileConfiguration worldsConfig = new YamlConfiguration();
        setConfigFromMap(worldsConfig, data);
        try {
            worldsConfig.save(worldsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving worlds.yml", e);  // i18n-ignore: technical exception log
        }
    }

    // ============ Equipment.yml Operationen ============

    /**
     * Liest die equipment.yml und konvertiert zu Map
     */
    public Map<String, Object> getEquipmentAsMap() {
        File equipmentFile = new File(plugin.getDataFolder(), "equipment.yml");
        if (!equipmentFile.exists()) {
            return new LinkedHashMap<>();
        }
        FileConfiguration equipmentConfig = YamlConfiguration.loadConfiguration(equipmentFile);
        return configSectionToMap(equipmentConfig);
    }

    /**
     * Speichert die Equipment aus einer Map
     */
    public void saveEquipmentFromMap(Map<String, Object> data) {
        File equipmentFile = new File(plugin.getDataFolder(), "equipment.yml");
        plugin.getLogger().info("[Web-Config] Saving equipment.yml to: " + equipmentFile.getAbsolutePath());  // i18n-ignore: web config diagnostic trace
        plugin.getLogger().info("[Web-Config] Data keys: " + data.keySet());  // i18n-ignore: web config diagnostic trace
        
        FileConfiguration equipmentConfig = new YamlConfiguration();
        setConfigFromMap(equipmentConfig, data);
        
        try {
            equipmentConfig.save(equipmentFile);
            plugin.getLogger().info("[Web-Config] equipment.yml saved successfully!");  // i18n-ignore: web config diagnostic trace
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving equipment.yml", e);  // i18n-ignore: technical exception log
        }
    }

    // ============ Web-Config Operationen ============

    /**
     * Liest die web-config.yml und konvertiert zu Map
     */
    public Map<String, Object> getWebConfigAsMap() {
        return configSectionToMap(webConfig);
    }

    /**
     * Speichert die Web-Config aus einer Map
     */
    public void saveWebConfigFromMap(Map<String, Object> data) {
        setConfigFromMap(webConfig, data);
        saveWebConfig();
    }

    // ============ Hilfsmethoden ============

    /**
     * Konvertiert einen ConfigurationSection zu einer Map
     */
    private Map<String, Object> configSectionToMap(ConfigurationSection section) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            
            if (value instanceof ConfigurationSection) {
                result.put(key, configSectionToMap((ConfigurationSection) value));
            } else if (value instanceof List) {
                result.put(key, convertList((List<?>) value));
            } else {
                result.put(key, value);
            }
        }
        
        return result;
    }

    /**
     * Konvertiert eine Liste (rekursiv)
     */
    private List<Object> convertList(List<?> list) {
        List<Object> result = new ArrayList<>();
        
        for (Object item : list) {
            if (item instanceof ConfigurationSection) {
                result.add(configSectionToMap((ConfigurationSection) item));
            } else if (item instanceof Map) {
                result.add(convertMap((Map<?, ?>) item));
            } else if (item instanceof List) {
                result.add(convertList((List<?>) item));
            } else {
                result.add(item);
            }
        }
        
        return result;
    }

    /**
     * Konvertiert eine Map (rekursiv)
     */
    private Map<String, Object> convertMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            
            if (value instanceof ConfigurationSection) {
                result.put(key, configSectionToMap((ConfigurationSection) value));
            } else if (value instanceof Map) {
                result.put(key, convertMap((Map<?, ?>) value));
            } else if (value instanceof List) {
                result.put(key, convertList((List<?>) value));
            } else {
                result.put(key, value);
            }
        }
        
        return result;
    }

    /**
     * Löscht die Config und setzt neue Werte
     */
    private void clearAndSetConfig(FileConfiguration config, Map<String, Object> data) {
        // Lösche alle vorhandenen Keys
        for (String key : config.getKeys(false)) {
            config.set(key, null);
        }
        // Setze neue Werte
        setConfigFromMap(config, data);
    }

    /**
     * Setzt Config-Werte aus einer Map (rekursiv)
     */
    @SuppressWarnings("unchecked")
    private void setConfigFromMap(ConfigurationSection section, Map<String, Object> data) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof Map) {
                // Erstelle neue Section und fülle rekursiv
                ConfigurationSection subSection = section.createSection(key);
                setConfigFromMap(subSection, (Map<String, Object>) value);
            } else if (value instanceof List) {
                // Listen direkt setzen
                section.set(key, convertListForYaml((List<?>) value));
            } else {
                // Primitive Werte direkt setzen
                section.set(key, value);
            }
        }
    }

    /**
     * Konvertiert Liste für YAML-Format
     */
    @SuppressWarnings("unchecked")
    private List<Object> convertListForYaml(List<?> list) {
        List<Object> result = new ArrayList<>();
        
        for (Object item : list) {
            if (item instanceof Map) {
                Map<String, Object> convertedMap = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) item).entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    Object value = entry.getValue();
                    
                    if (value instanceof Map) {
                        convertedMap.put(key, convertMapForYaml((Map<?, ?>) value));
                    } else if (value instanceof List) {
                        convertedMap.put(key, convertListForYaml((List<?>) value));
                    } else {
                        convertedMap.put(key, value);
                    }
                }
                result.add(convertedMap);
            } else if (item instanceof List) {
                result.add(convertListForYaml((List<?>) item));
            } else {
                result.add(item);
            }
        }
        
        return result;
    }

    /**
     * Konvertiert Map für YAML-Format
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertMapForYaml(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            
            if (value instanceof Map) {
                result.put(key, convertMapForYaml((Map<?, ?>) value));
            } else if (value instanceof List) {
                result.put(key, convertListForYaml((List<?>) value));
            } else {
                result.put(key, value);
            }
        }
        
        return result;
    }

    /**
     * Reload der Konfigurationen
     */
    public void reload() {
        loadWebConfig();
        plugin.reloadConfig();
    }

    /**
     * Getter für Gson (falls extern benötigt)
     */
    public Gson getGson() {
        return gson;
    }
}
