package de.zfzfg.pvpwager.managers;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class ConfigManager {
    private final EventPlugin plugin;
    
    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration defaultMessages;
    private FileConfiguration arenaConfig;
    private FileConfiguration equipmentConfig;
    
    private File configFile;
    private File messagesFile;
    private File defaultMessagesFile;
    private File arenaFile;
    private File equipmentFile;
    
    public ConfigManager(EventPlugin plugin) {
        this.plugin = plugin;
        loadConfigs();
    }
    
    public void loadConfigs() {
        // Create plugin folder if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        // Main config
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Messages config (language-aware)
        loadLanguageConfigs();
        
        // Arena/Worlds config (Unified worlds.yml bevorzugt, Fallback auf arenas.yml)
        File unifiedWorlds = new File(plugin.getDataFolder(), "worlds.yml");
        File legacyArenas = new File(plugin.getDataFolder(), "arenas.yml");
        arenaFile = unifiedWorlds.exists() ? unifiedWorlds : legacyArenas;
        if (!arenaFile.exists()) {
            // arenas.yml gibt es im Jar nicht mehr - saveResource() würde dafür eine
            // IllegalArgumentException werfen. Angelegt wird immer die vereinheitlichte
            // worlds.yml; der Legacy-Name wird oben nur noch gelesen, nie erzeugt.
            plugin.saveResource("worlds.yml", false);
            arenaFile = unifiedWorlds;
        }
        arenaConfig = YamlConfiguration.loadConfiguration(arenaFile);
        
        // Equipment config
        equipmentFile = new File(plugin.getDataFolder(), "equipment.yml");
        if (!equipmentFile.exists()) {
            plugin.saveResource("equipment.yml", false);
        }
        equipmentConfig = YamlConfiguration.loadConfiguration(equipmentFile);
    }
    
    public void reloadConfigs() {
        config = YamlConfiguration.loadConfiguration(configFile);
        loadLanguageConfigs();
        arenaConfig = YamlConfiguration.loadConfiguration(arenaFile);
        equipmentConfig = YamlConfiguration.loadConfiguration(equipmentFile);

        plugin.getLogger().info(plugin.getConsoleMsg("core-reload-all"));
    }
    
    public void saveConfig(FileConfiguration config, File file) {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, plugin.getConsoleMsg("config-save-error", "file", file.getName(), "error", e.getMessage()), e);
        }
    }
    
    public FileConfiguration getConfig() {
        return config;
    }
    
    public FileConfiguration getMessages() {
        return messages;
    }
    
    public FileConfiguration getArenaConfig() {
        return arenaConfig;
    }
    
    public FileConfiguration getEquipmentConfig() {
        return equipmentConfig;
    }
    
    public String getMessage(String path, String... replacements) {
        String message = messages.getString(path, null);
        if (message == null) {
            message = messages.getString("messages." + path, null);
        }
        if (message == null) {
            message = messages.getString("messages.match-manager." + path, null);
        }
        if (message == null) {
            message = messages.getString("messages.match-display." + path, null);
        }
        if (message == null) {
            message = messages.getString("messages.match-system." + path, null);
        }
        if (message == null) {
            message = messages.getString("messages.command-request." + path, null);
        }
        if (message == null) {
            message = messages.getString("messages.system." + path, null);
        }
        if (message == null) {
            message = messages.getString("messages.general." + path, null);
        }
        if (message == null) {
            message = messages.getString("messages.pvpwager.help." + path, null);
        }
        if (message == null) {
            message = messages.getString("messages.pvpask." + path, null);
        }
        if (message == null) {
            message = messages.getString("messages.gui." + path, null);
        }
        if (message == null) {
            message = messages.getString("messages.request." + path, null);
        }
        if (message == null && defaultMessages != null) {
            message = defaultMessages.getString(path, null);
            if (message == null) {
                message = defaultMessages.getString("messages." + path, null);
            }
            if (message == null) {
                message = defaultMessages.getString("messages.match-manager." + path, null);
            }
            if (message == null) {
                message = defaultMessages.getString("messages.match-display." + path, null);
            }
            if (message == null) {
                message = defaultMessages.getString("messages.match-system." + path, null);
            }
            if (message == null) {
                message = defaultMessages.getString("messages.command-request." + path, null);
            }
            if (message == null) {
                message = defaultMessages.getString("messages.system." + path, null);
            }
            if (message == null) {
                message = defaultMessages.getString("messages.general." + path, null);
            }
        }
        
        if (message == null) {
            // Einheitlicher Marker wie im restlichen Code -- bewusst unlokalisiert,
            // damit im Fehlerfall der Key-Pfad lesbar bleibt.
            message = "&c[missing: " + path + "]";
        }
        
        // Apply replacements in pairs (key, value)
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

    private void loadLanguageConfigs() {
        String language = config.getString("settings.language", "en").toLowerCase();

        // Reload default messages
        if (defaultMessagesFile == null) {
            defaultMessagesFile = new File(plugin.getDataFolder(), "messages_en.yml");
        }
        if (!defaultMessagesFile.exists()) {
            plugin.saveResource("messages_en.yml", false);
        }
        defaultMessages = YamlConfiguration.loadConfiguration(defaultMessagesFile);

        // Reload language-specific messages
        messagesFile = new File(plugin.getDataFolder(), "messages_" + language + ".yml");
        if (!messagesFile.exists()) {
            if (plugin.getResource("messages_" + language + ".yml") != null) {
                plugin.saveResource("messages_" + language + ".yml", false);
            } else {
                messagesFile = defaultMessagesFile;
            }
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }
}
