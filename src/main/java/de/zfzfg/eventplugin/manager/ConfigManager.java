package de.zfzfg.eventplugin.manager;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.model.EventConfig;
import de.zfzfg.eventplugin.model.EquipmentGroup;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    
    private final EventPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration equipmentConfig;
    private String equipmentFilePath;
    private FileConfiguration messagesConfig;
    
    private Map<String, EventConfig> events;
    private Map<String, EquipmentGroup> equipmentGroups;
    private String prefix;
    private String mainWorld;
    private boolean savePlayerLocation;
    private int joinPhaseDuration;
    private int lobbyCountdown;
    private String commandRestriction;
    private String worldLoading;  // NEU
    private boolean autoEventsEnabled;
    private int autoEventIntervalMin;
    private int autoEventIntervalMax;
    private boolean autoEventRandomSelection;
    private boolean checkOnlinePlayers;
    private List<String> selectedAutoEvents;
    
    public ConfigManager(EventPlugin plugin) {
        this.plugin = plugin;
        this.events = new HashMap<>();
        this.equipmentGroups = new HashMap<>();
        this.selectedAutoEvents = new ArrayList<>();
    }
    
    public void loadConfigs() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdir();
        }
        
        loadMainConfig();
        loadEquipmentConfig();
        loadMessagesConfig();
        
        parseEvents();
        parseEquipment();

        // Einfache Validierung nach dem Laden
        validate();
    }
    
    private void loadMainConfig() {
        // Verwende ausschließlich die gemeinsame config.yml aus dem Plugin-Datenordner
        File unified = new File(plugin.getDataFolder(), "config.yml");
        if (!unified.exists()) {
            // Kopiere Default aus src/main/resources in den Datenordner
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(unified);
        
        prefix = config.getString("settings.prefix", "&6[Event]&r");
        mainWorld = config.getString("settings.main-world", "world");
        savePlayerLocation = config.getBoolean("settings.save-player-location", true);
        joinPhaseDuration = config.getInt("settings.join-phase-duration", 30);
        lobbyCountdown = config.getInt("settings.lobby-countdown", 30);
        commandRestriction = config.getString("settings.command-restriction", "both");
        worldLoading = config.getString("settings.world-loading", "both");  // NEU

        // Validierung und Begrenzung der Settings-Werte
        joinPhaseDuration = validateRange("settings.join-phase-duration", joinPhaseDuration, 5, 600);
        lobbyCountdown = validateRange("settings.lobby-countdown", lobbyCountdown, 3, 300);
        commandRestriction = validateEnum("settings.command-restriction", commandRestriction, "both",
                new String[]{"join", "lobby", "both"});
        worldLoading = validateEnum("settings.world-loading", worldLoading, "both",
                new String[]{"both", "clone", "load"});
        
        ConfigurationSection autoEvents = config.getConfigurationSection("settings.auto-events");
        if (autoEvents != null) {
            autoEventsEnabled = autoEvents.getBoolean("enabled", false);
            autoEventIntervalMin = autoEvents.getInt("interval-min", 1800);
            autoEventIntervalMax = autoEvents.getInt("interval-max", 3600);
            autoEventRandomSelection = autoEvents.getBoolean("random-selection", true);
            checkOnlinePlayers = autoEvents.getBoolean("check-online-players", true);
            
            // Lade ausgewählte Events
            selectedAutoEvents = autoEvents.getStringList("selected-events");
            if (selectedAutoEvents == null) {
                selectedAutoEvents = new ArrayList<>();
            }

            // Validierung der Auto-Event-Intervalle (1 Minute bis 24 Stunden)
            autoEventIntervalMin = validateRange("settings.auto-events.interval-min", autoEventIntervalMin, 60, 86400);
            autoEventIntervalMax = validateRange("settings.auto-events.interval-max", autoEventIntervalMax, 60, 86400);
            if (autoEventIntervalMax < autoEventIntervalMin) {
                plugin.getLogger().warning("Einstellung 'settings.auto-events.interval-max' ist kleiner als 'interval-min'. Setze max = min.");
                autoEventIntervalMax = autoEventIntervalMin;
            }
        }
    }
    
    private void loadEquipmentConfig() {
        // Verwende ausschließlich die gemeinsame equipment.yml aus dem Plugin-Datenordner
        File unified = new File(plugin.getDataFolder(), "equipment.yml");
        if (!unified.exists()) {
            // Kopiere Default aus src/main/resources in den Datenordner
            plugin.saveResource("equipment.yml", false);
        }
        equipmentConfig = YamlConfiguration.loadConfiguration(unified);
        equipmentFilePath = unified.getAbsolutePath();

        // Validierung: hat die Datei überhaupt eine der erwarteten Sektionen?
        boolean hasUnified = equipmentConfig.getConfigurationSection("equipment") != null;
        boolean hasLegacyGroups = equipmentConfig.getConfigurationSection("equipment-groups") != null;
        boolean hasLegacySets = equipmentConfig.getConfigurationSection("equipment-sets") != null;
        if (!hasUnified && !hasLegacyGroups && !hasLegacySets) {
            plugin.getLogger().warning("equipment.yml enthält keine gültigen Sektionen. Ersetze mit der Paket-Standarddatei.");
            // Überschreibe die existierende Datei mit der eingebetteten Ressource
            try {
                plugin.saveResource("equipment.yml", true);
                equipmentConfig = YamlConfiguration.loadConfiguration(unified);
                equipmentFilePath = unified.getAbsolutePath();
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().severe("Konnte Standard 'equipment.yml' nicht bereitstellen: " + ex.getMessage());
            }
        }
    }
    
    private void loadMessagesConfig() {
        String language = config.getString("settings.language", "en").toLowerCase();

        File defaultMessages = new File(plugin.getDataFolder(), "messages_en.yml");
        if (!defaultMessages.exists()) {
            plugin.saveResource("messages_en.yml", false);
        }

        File langFile = new File(plugin.getDataFolder(), "messages_" + language + ".yml");
        if (!langFile.exists()) {
            if (plugin.getResource("messages_" + language + ".yml") != null) {
                plugin.saveResource("messages_" + language + ".yml", false);
            } else {
                langFile = defaultMessages;
            }
        }

        messagesConfig = YamlConfiguration.loadConfiguration(langFile);
    }
    
    private void parseEvents() {
        events.clear();
        ConfigurationSection eventsSection = config.getConfigurationSection("events");
        if (eventsSection == null) return;
        
        for (String eventId : eventsSection.getKeys(false)) {
            ConfigurationSection eventSection = eventsSection.getConfigurationSection(eventId);
            if (eventSection == null) continue;
            
            if (!eventSection.getBoolean("enabled", true)) continue;
            
            EventConfig eventConfig = new EventConfig(eventId, eventSection);
            events.put(eventId, eventConfig);
            
            plugin.getLogger().info("Event geladen: " + eventId);
        }
    }
    
    private void parseEquipment() {
        equipmentGroups.clear();
        plugin.getLogger().info("Lade Ausrüstungen aus: " + (equipmentFilePath != null ? equipmentFilePath : "<unbekannt>"));

        // Primär: PvPWager-Format 'equipment-sets' (aktuelles Standardformat)
        ConfigurationSection setsSection = equipmentConfig.getConfigurationSection("equipment-sets");
        if (setsSection != null) {
            for (String groupId : setsSection.getKeys(false)) {
                ConfigurationSection groupSection = setsSection.getConfigurationSection(groupId);
                if (groupSection == null) continue;
                boolean enabled = groupSection.getBoolean("enabled", true);
                if (!enabled) {
                    plugin.getLogger().info("Equipment-Set '" + groupId + "' ist deaktiviert, überspringe...");
                    continue;
                }
                EquipmentGroup group = new EquipmentGroup(groupId, groupSection);
                equipmentGroups.put(groupId, group);
                plugin.getLogger().info("Equipment-Set geladen: " + groupId);
            }
        }

        // Fallback: vereinheitlichte Sektion 'equipment' mit event-equip-enable Flag
        if (equipmentGroups.isEmpty()) {
            ConfigurationSection unifiedSection = equipmentConfig.getConfigurationSection("equipment");
            if (unifiedSection != null) {
                for (String groupId : unifiedSection.getKeys(false)) {
                    ConfigurationSection groupSection = unifiedSection.getConfigurationSection(groupId);
                    if (groupSection == null) continue;

                    boolean eventEnabled = groupSection.getBoolean("event-equip-enable", true);
                    if (!eventEnabled) {
                        plugin.getLogger().info("Equipment '" + groupId + "' nicht für Events aktiviert, überspringe...");
                        continue;
                    }

                    EquipmentGroup group = new EquipmentGroup(groupId, groupSection);
                    equipmentGroups.put(groupId, group);
                    plugin.getLogger().info("Equipment für Events geladen: " + groupId);
                }
            }
        }

        // Fallback: legacy 'equipment-groups'
        if (equipmentGroups.isEmpty()) {
            ConfigurationSection groupsSection = equipmentConfig.getConfigurationSection("equipment-groups");
            if (groupsSection != null) {
                for (String groupId : groupsSection.getKeys(false)) {
                    ConfigurationSection groupSection = groupsSection.getConfigurationSection(groupId);
                    if (groupSection == null) continue;

                    EquipmentGroup group = new EquipmentGroup(groupId, groupSection);
                    equipmentGroups.put(groupId, group);
                    plugin.getLogger().info("Legacy Equipment-Gruppe geladen: " + groupId);
                }
            }
        }

        if (equipmentGroups.isEmpty()) {
            plugin.getLogger().severe("Keine Ausrüstungs-Gruppen gefunden. Prüfe Inhalt von '" + (equipmentFilePath != null ? equipmentFilePath : "unbekannt") + "'. Erwartete Sektionen: 'equipment-sets' (primär), 'equipment' oder 'equipment-groups'.");
        } else {
            String ids = String.join(", ", equipmentGroups.keySet());
            plugin.getLogger().info("Ausrüstungen geladen: " + ids);
        }
    }
    
    public void reloadConfigs() {
        loadConfigs();
    }

    /**
     * Einfache Konfig-Validierung mit Warnungen bei offensichtlichen Problemen.
     * Felder in EventConfig sind final, daher wird hier nicht angepasst, nur geloggt.
     */
    private void validate() {
        // Mindest- und Maximalspieler validieren
        for (Map.Entry<String, EventConfig> entry : events.entrySet()) {
            String id = entry.getKey();
            EventConfig ec = entry.getValue();
            if (ec.getMinPlayers() < 2) {
                plugin.getLogger().warning("Event '" + id + "': min-players < 2. Empfohlen: mindestens 2.");
            }
            if (ec.getMaxPlayers() < ec.getMinPlayers()) {
                plugin.getLogger().warning("Event '" + id + "': max-players < min-players. Bitte anpassen.");
            }
            if (ec.getCountdownTime() < 1) {
                plugin.getLogger().warning("Event '" + id + "': countdown-time < 1 Sekunde. Bitte erhöhen.");
            }
        }
    }

    // Hilfsmethoden für Config-Validierung
    private int validateRange(String key, int value, int min, int max) {
        if (value < min) {
            plugin.getLogger().warning("Einstellung '" + key + "' war " + value + ", setze auf Mindestwert " + min + ".");
            return min;
        }
        if (value > max) {
            plugin.getLogger().warning("Einstellung '" + key + "' war " + value + ", begrenze auf Maximalwert " + max + ".");
            return max;
        }
        return value;
    }

    private String validateEnum(String key, String value, String defaultValue, String[] allowed) {
        if (value == null) {
            plugin.getLogger().warning("Einstellung '" + key + "' fehlt oder ist null. Verwende Standard '" + defaultValue + "'.");
            return defaultValue;
        }
        for (String a : allowed) {
            if (a.equalsIgnoreCase(value)) {
                return a; // normalisiere ggf. auf erlaubte Schreibweise
            }
        }
        plugin.getLogger().warning("Einstellung '" + key + "' hat ungültigen Wert '" + value + "'. Erlaubt: " + String.join(", ", allowed) + ". Verwende '" + defaultValue + "'.");
        return defaultValue;
    }
    
    public EventConfig getEventConfig(String eventId) {
        return events.get(eventId);
    }
    
    public Map<String, EventConfig> getAllEvents() {
        return events;
    }
    
    public EquipmentGroup getEquipmentGroup(String groupId) {
        if (groupId == null) {
            plugin.getLogger().severe("Equipment group ID ist null. Bitte 'equipment-group' im Event-Config setzen.");
            return null;
        }

        // Direkter Lookup (case-insensitive unterstützt)
        EquipmentGroup direct = equipmentGroups.get(groupId);
        if (direct != null) return direct;
        for (Map.Entry<String, EquipmentGroup> e : equipmentGroups.entrySet()) {
            if (e.getKey().equalsIgnoreCase(groupId)) {
                return e.getValue();
            }
        }

        // Strikte Fehlerbehandlung statt Provisorien
        plugin.getLogger().severe("Equipment group '" + groupId + "' nicht gefunden. Verfügbare IDs: " + String.join(", ", equipmentGroups.keySet()));
        return null;
    }
    
    public String getPrefix() {
        return prefix;
    }
    
    public String getMainWorld() {
        return mainWorld;
    }
    
    public boolean shouldSavePlayerLocation() {
        return savePlayerLocation;
    }
    
    public int getJoinPhaseDuration() {
        return joinPhaseDuration;
    }
    
    public int getLobbyCountdown() {
        return lobbyCountdown;
    }
    
    public String getCommandRestriction() {
        return commandRestriction;
    }
    
    public String getWorldLoading() {  // NEU
        return worldLoading;
    }
    
    public boolean isAutoEventsEnabled() {
        return autoEventsEnabled;
    }
    
    public int getAutoEventIntervalMin() {
        return autoEventIntervalMin;
    }
    
    public int getAutoEventIntervalMax() {
        return autoEventIntervalMax;
    }
    
    public boolean isAutoEventRandomSelection() {
        return autoEventRandomSelection;
    }
    
    public boolean shouldCheckOnlinePlayers() {
        return checkOnlinePlayers;
    }
    
    public List<String> getSelectedAutoEvents() {
        return new ArrayList<>(selectedAutoEvents);
    }
    
    public String getMessage(String path) {
        return messagesConfig.getString("messages." + path, "&cMissing message: " + path);
    }
    
    public String getMessage(String path, String... replacements) {
        String message = getMessage(path);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
            }
        }
        return message;
    }
}