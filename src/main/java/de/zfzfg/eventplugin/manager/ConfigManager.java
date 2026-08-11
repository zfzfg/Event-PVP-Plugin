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
    private boolean manageEventWorlds;
    private boolean autoEventsEnabled;
    private int autoEventIntervalMin;
    private int autoEventIntervalMax;
    private boolean autoEventRandomSelection;
    private boolean checkOnlinePlayers;
    private List<String> selectedAutoEvents;
    
    // Update-Check Settings
    /** Kontakt fuer den User-Agent -- Modrinth verlangt eine erreichbare Adresse. */
    private static final String DEFAULT_UPDATE_CONTACT = "https://modrinth.com/plugin/pqJQdZ6R";
    private boolean updateCheckEnabled;
    private boolean checkOnStartup;
    private boolean notifyAdminsOnJoin;
    private String modrinthProjectId;
    private int startupDelayTicks;
    private boolean updateStableOnly;
    private String updateContact;

    // External integrations
    private boolean ajLeaderboardsEnabled;
    private boolean decentHologramsEnabled;
    private boolean pvpManagerEnabled;
    private int integrationRefreshIntervalTicks;
    
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
        manageEventWorlds = config.getBoolean("settings.world-management.events", true);

        // Validierung und Begrenzung der Settings-Werte
        joinPhaseDuration = validateRange("settings.join-phase-duration", joinPhaseDuration, 5, 600);
        lobbyCountdown = validateRange("settings.lobby-countdown", lobbyCountdown, 3, 300);
        // Exakt die Werte, die WorldChangeListener.onCommandPreprocess auswertet
        commandRestriction = validateEnum("settings.command-restriction", commandRestriction, "both",
                new String[]{"both", "event", "lobby", "none"});
        
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
                plugin.getLogger().warning("Einstellung 'settings.auto-events.interval-max' ist kleiner als 'interval-min'. Setze max = min."); // i18n-ignore: technical config validation log
                autoEventIntervalMax = autoEventIntervalMin;
            }
        }
        
        // Update-Check Settings laden
        ConfigurationSection updateCheck = config.getConfigurationSection("settings.update-check");
        if (updateCheck != null) {
            updateCheckEnabled = updateCheck.getBoolean("enabled", true);
            checkOnStartup = updateCheck.getBoolean("check-on-startup", true);
            notifyAdminsOnJoin = updateCheck.getBoolean("notify-admins-on-join", true);
            modrinthProjectId = updateCheck.getString("modrinth-project-id", "pqJQdZ6R");
            startupDelayTicks = updateCheck.getInt("startup-delay-ticks", 20);
            updateStableOnly = updateCheck.getBoolean("stable-only", true);
            updateContact = updateCheck.getString("contact", DEFAULT_UPDATE_CONTACT);
        } else {
            // Default-Werte wenn Sektion fehlt
            updateCheckEnabled = true;
            checkOnStartup = true;
            notifyAdminsOnJoin = true;
            modrinthProjectId = "pqJQdZ6R";
            startupDelayTicks = 20;
            updateStableOnly = true;
            updateContact = DEFAULT_UPDATE_CONTACT;
        }

        ConfigurationSection integrations = config.getConfigurationSection("settings.integrations");
        if (integrations != null) {
            ajLeaderboardsEnabled = integrations.getConfigurationSection("ajleaderboards") != null
                    && integrations.getConfigurationSection("ajleaderboards").getBoolean("enabled", false);
            decentHologramsEnabled = integrations.getConfigurationSection("decentholograms") != null
                    && integrations.getConfigurationSection("decentholograms").getBoolean("enabled", false);
            pvpManagerEnabled = integrations.getConfigurationSection("pvpmanager") != null
                    && integrations.getConfigurationSection("pvpmanager").getBoolean("enabled", false);
            integrationRefreshIntervalTicks = integrations.getInt("refresh-interval-ticks", 20);
        } else {
            ajLeaderboardsEnabled = false;
            decentHologramsEnabled = false;
            pvpManagerEnabled = false;
            integrationRefreshIntervalTicks = 20;
        }
        integrationRefreshIntervalTicks = validateRange("settings.integrations.refresh-interval-ticks", integrationRefreshIntervalTicks, 1, 1200);
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
        //
        // Die beiden Alt-Sektionen werden hier weiterhin akzeptiert, obwohl der Loader unten
        // nur noch 'equipment' liest. Grund: dieser Zweig überschreibt die Datei des Nutzers
        // mit der mitgelieferten Vorlage. Konnte die Migration in CoreConfigManager nicht
        // laufen - etwa weil sich keine Sicherungskopie anlegen ließ -, stünden hier noch die
        // alten Sektionen, und ein strengerer Test würde die Konfiguration wegwerfen statt sie
        // beim nächsten Start erneut zu migrieren.
        boolean hasUnified = equipmentConfig.getConfigurationSection("equipment") != null;
        boolean hasLegacyGroups = equipmentConfig.getConfigurationSection("equipment-groups") != null;
        boolean hasLegacySets = equipmentConfig.getConfigurationSection("equipment-sets") != null;
        if (!hasUnified && !hasLegacyGroups && !hasLegacySets) {
            plugin.getLogger().warning("equipment.yml does not contain valid sections. Replacing with default package file."); // i18n-ignore: technical config validation log
            // Überschreibe die existierende Datei mit der eingebetteten Ressource
            try {
                plugin.saveResource("equipment.yml", true);
                equipmentConfig = YamlConfiguration.loadConfiguration(unified);
                equipmentFilePath = unified.getAbsolutePath();
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().severe("Could not deploy default 'equipment.yml': " + ex.getMessage()); // i18n-ignore: technical exception log
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
            
            plugin.getLogger().info(plugin.getConsoleMsg("event-loaded", "event", eventId));
        }
    }
    
    private void parseEquipment() {
        equipmentGroups.clear();

        // Genau eine Sektion. Frueher standen hier drei Zweige - 'equipment-sets' zuerst,
        // dann 'equipment', dann 'equipment-groups' -, waehrend der PvP-Loader die umgekehrte
        // Reihenfolge hatte. Bei zwei belegten Sektionen sahen Events und PvP dadurch
        // verschiedene Definitionen unter derselben Set-ID. Alte Dateien werden beim Start von
        // EquipmentSchemaMigration zusammengefuehrt.
        ConfigurationSection section = equipmentConfig.getConfigurationSection(
                de.zfzfg.core.config.EquipmentSchemaMigration.TARGET);
        if (section != null) {
            for (String groupId : section.getKeys(false)) {
                ConfigurationSection groupSection = section.getConfigurationSection(groupId);
                if (groupSection == null) continue;

                if (!groupSection.getBoolean("event-equip-enable", true)) {
                    continue;
                }

                EquipmentGroup group = new EquipmentGroup(groupId, groupSection);
                equipmentGroups.put(groupId, group);
                plugin.getLogger().info(plugin.getConsoleMsg("equipment-loaded", "set", groupId));
            }
        }

        if (equipmentGroups.isEmpty()) {
            plugin.getLogger().severe(plugin.getConsoleMsg("equipment-empty-error"));
        } else {
            String ids = String.join(", ", equipmentGroups.keySet());
            plugin.getLogger().info(plugin.getConsoleMsg("equipment-all-loaded", "sets", ids));
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
                plugin.getLogger().warning("Event '" + id + "': min-players < 2. Empfohlen: mindestens 2."); // i18n-ignore: technical config validation log
            }
            if (ec.getMaxPlayers() < ec.getMinPlayers()) {
                plugin.getLogger().warning("Event '" + id + "': max-players < min-players. Bitte anpassen."); // i18n-ignore: technical config validation log
            }
            if (ec.getCountdownTime() < 1) {
                plugin.getLogger().warning("Event '" + id + "': countdown-time < 1 Sekunde. Bitte erhöhen."); // i18n-ignore: technical config validation log
            }
        }
    }

    // Hilfsmethoden für Config-Validierung
    private int validateRange(String key, int value, int min, int max) {
        if (value < min) {
            plugin.getLogger().warning("Einstellung '" + key + "' war " + value + ", setze auf Mindestwert " + min + "."); // i18n-ignore: technical config validation log
            return min;
        }
        if (value > max) {
            plugin.getLogger().warning("Einstellung '" + key + "' war " + value + ", begrenze auf Maximalwert " + max + "."); // i18n-ignore: technical config validation log
            return max;
        }
        return value;
    }

    private String validateEnum(String key, String value, String defaultValue, String[] allowed) {
        if (value == null) {
            plugin.getLogger().warning("Einstellung '" + key + "' fehlt oder ist null. Verwende Standard '" + defaultValue + "'."); // i18n-ignore: technical config validation log
            return defaultValue;
        }
        for (String a : allowed) {
            if (a.equalsIgnoreCase(value)) {
                return a; // normalisiere ggf. auf erlaubte Schreibweise
            }
        }
        plugin.getLogger().warning("Einstellung '" + key + "' hat ungültigen Wert '" + value + "'. Erlaubt: " + String.join(", ", allowed) + ". Verwende '" + defaultValue + "'."); // i18n-ignore: technical config validation log
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
            plugin.getLogger().severe("Equipment group ID ist null. Bitte 'equipment-group' im Event-Config setzen."); // i18n-ignore: technical config validation log
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
        plugin.getLogger().severe(plugin.getConsoleMsg("equipment-not-found", "group", groupId, "available", String.join(", ", equipmentGroups.keySet())));
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
    
    /**
     * Ob das Plugin Lobby- und Eventwelten automatisch laden und entladen darf.
     * Welche der beiden Welten ein Event braucht, entscheidet dessen 'use-lobby'.
     */
    public boolean isManageEventWorlds() {
        return manageEventWorlds;
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
        String msg = messagesConfig.getString("messages." + path, null);
        if (msg == null) {
            msg = messagesConfig.getString("messages.general." + path, null);
        }
        if (msg == null) {
            msg = messagesConfig.getString("messages.end." + path, null);
        }
        if (msg == null) {
            msg = messagesConfig.getString("messages.system." + path, null);
        }
        if (msg == null) {
            msg = messagesConfig.getString(path, null);
        }
        if (msg == null) {
            // Einheitlicher Marker wie im restlichen Code -- bewusst unlokalisiert,
            // damit im Fehlerfall der Key-Pfad lesbar bleibt.
            return "&c[missing: " + path + "]";
        }
        return msg;
    }
    
    public String getMessage(String path, String... replacements) {
        String message = getMessage(path);
        if (replacements != null && replacements.length > 0) {
            for (int i = 0; i < replacements.length - 1; i += 2) {
                String raw = replacements[i] != null ? replacements[i].replaceAll("^[{%]+|[%}]+$", "") : "";
                String val = replacements[i + 1] != null ? replacements[i + 1] : "";
                if (!raw.isEmpty()) {
                    message = message.replace("{" + raw + "}", val)
                                     .replace("%" + raw + "%", val);
                }
            }
        }
        return message;
    }
    
    // Update-Check Getter
    public boolean isUpdateCheckEnabled() {
        return updateCheckEnabled;
    }
    
    public boolean shouldCheckOnStartup() {
        return checkOnStartup;
    }
    
    public boolean shouldNotifyAdminsOnJoin() {
        return notifyAdminsOnJoin;
    }
    
    public boolean isAjLeaderboardsEnabled() {
        return ajLeaderboardsEnabled;
    }

    public boolean isDecentHologramsEnabled() {
        return decentHologramsEnabled;
    }

    public boolean isPvpManagerEnabled() {
        return pvpManagerEnabled;
    }

    public int getIntegrationRefreshIntervalTicks() {
        return integrationRefreshIntervalTicks;
    }
    
    public String getModrinthProjectId() {
        return modrinthProjectId;
    }

    /** true = Vorabversionen (beta/alpha) werden beim Update-Check ignoriert. */
    public boolean isUpdateStableOnly() {
        return updateStableOnly;
    }

    /** Kontaktangabe fuer den User-Agent der Modrinth-Anfrage. */
    public String getUpdateContact() {
        return updateContact;
    }
    
    public int getStartupDelayTicks() {
        return startupDelayTicks;
    }
}