package de.zfzfg.core.web;

import com.google.gson.Gson;
import org.bukkit.plugin.java.JavaPlugin;
import de.zfzfg.eventplugin.EventPlugin;

import java.util.*;
import java.util.logging.Level;

/**
 * Handhabt die REST API Endpoints für das Web-Interface
 */
public class WebApiHandler {

    private final JavaPlugin plugin;
    private final WebConfigManager configManager;
    private final Gson gson;

    public WebApiHandler(JavaPlugin plugin, WebConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.gson = configManager.getGson();
    }

    // ============ Config API ============

    /**
     * GET /api/config/get - Gibt die config.yml zurück
     */
    public Map<String, Object> getConfig() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", configManager.getConfigAsMap());
        return response;
    }

    /**
     * POST /api/config/save - Speichert die config.yml
     */
    public Map<String, Object> saveConfig(Map<String, Object> requestBody) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            plugin.getLogger().info("[Web-API] Speichere config.yml...");
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) requestBody.get("data");
            if (data != null) {
                configManager.saveConfigFromMap(data);
                plugin.getLogger().info("[Web-API] config.yml gespeichert");
                response.put("success", true);
                response.put("message", "Config gespeichert");
            } else {
                plugin.getLogger().warning("[Web-API] Keine Daten empfangen für config.yml");
                response.put("success", false);
                response.put("message", "Keine Daten empfangen");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Web-API] Fehler beim Speichern von config.yml", e);
            response.put("success", false);
            response.put("message", "Fehler: " + e.getMessage());
        }
        return response;
    }

    // ============ Worlds API ============

    /**
     * GET /api/worlds/get - Gibt die worlds.yml zurück
     */
    public Map<String, Object> getWorlds() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", configManager.getWorldsAsMap());
        return response;
    }

    /**
     * POST /api/worlds/save - Speichert die worlds.yml
     */
    public Map<String, Object> saveWorlds(Map<String, Object> requestBody) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            plugin.getLogger().info("[Web-API] Speichere worlds.yml...");
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) requestBody.get("data");
            if (data != null) {
                configManager.saveWorldsFromMap(data);
                plugin.getLogger().info("[Web-API] worlds.yml gespeichert");
                response.put("success", true);
                response.put("message", "Worlds gespeichert");
            } else {
                plugin.getLogger().warning("[Web-API] Keine Daten empfangen für worlds.yml");
                response.put("success", false);
                response.put("message", "Keine Daten empfangen");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Web-API] Fehler beim Speichern von worlds.yml", e);
            response.put("success", false);
            response.put("message", "Fehler: " + e.getMessage());
        }
        return response;
    }

    // ============ Equipment API ============

    /**
     * GET /api/equipment/get - Gibt die equipment.yml zurück
     */
    public Map<String, Object> getEquipment() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", configManager.getEquipmentAsMap());
        return response;
    }

    /**
     * POST /api/equipment/save - Speichert die equipment.yml
     */
    public Map<String, Object> saveEquipment(Map<String, Object> requestBody) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            plugin.getLogger().info("[Web-API] Speichere equipment.yml...");
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) requestBody.get("data");
            if (data != null) {
                configManager.saveEquipmentFromMap(data);
                plugin.getLogger().info("[Web-API] equipment.yml gespeichert");
                response.put("success", true);
                response.put("message", "Equipment gespeichert");
            } else {
                plugin.getLogger().warning("[Web-API] Keine Daten empfangen für equipment.yml");
                response.put("success", false);
                response.put("message", "Keine Daten empfangen");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Web-API] Fehler beim Speichern von equipment.yml", e);
            response.put("success", false);
            response.put("message", "Fehler: " + e.getMessage());
        }
        return response;
    }

    // ============ Web-Config API ============

    /**
     * GET /api/webconfig/get - Gibt die web-config.yml zurück
     */
    public Map<String, Object> getWebConfig() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", configManager.getWebConfigAsMap());
        return response;
    }

    /**
     * POST /api/webconfig/save - Speichert die web-config.yml
     */
    public Map<String, Object> saveWebConfig(Map<String, Object> requestBody) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) requestBody.get("data");
            if (data != null) {
                configManager.saveWebConfigFromMap(data);
                response.put("success", true);
                response.put("message", "Web-Config gespeichert");
            } else {
                response.put("success", false);
                response.put("message", "Keine Daten empfangen");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Fehler: " + e.getMessage());
        }
        return response;
    }

    // ============ Reload & Status API ============

    /**
     * POST /api/reload - Lädt alle Konfigurationen neu
     */
    public Map<String, Object> reload() {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            plugin.getLogger().info("[Web-API] Reload angefordert...");
            
            // Versuche den vollständigen Reload über ConfigurationService
            if (plugin instanceof EventPlugin) {
                EventPlugin eventPlugin = (EventPlugin) plugin;
                if (eventPlugin.getConfigurationService() != null) {
                    eventPlugin.getConfigurationService().reloadAll();
                    plugin.getLogger().info("[Web-API] ConfigurationService.reloadAll() erfolgreich");
                } else {
                    // Fallback
                    configManager.reload();
                    plugin.getLogger().info("[Web-API] Fallback: WebConfigManager.reload()");
                }
            } else {
                configManager.reload();
                plugin.getLogger().info("[Web-API] WebConfigManager.reload()");
            }
            
            response.put("success", true);
            response.put("message", "Alle Konfigurationen neu geladen");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Web-API] Reload Fehler: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Fehler: " + e.getMessage());
        }
        return response;
    }

    /**
     * GET /api/status - Gibt den Plugin-Status zurück
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("pluginName", plugin.getDescription().getName());
        status.put("pluginVersion", plugin.getDescription().getVersion());
        status.put("serverVersion", plugin.getServer().getVersion());
        status.put("onlinePlayers", plugin.getServer().getOnlinePlayers().size());
        status.put("maxPlayers", plugin.getServer().getMaxPlayers());
        status.put("tps", getTps());
        status.put("uptime", getUptime());
        
        // Aktuelle Sprache hinzufügen
        String currentLang = "en";
        if (plugin instanceof EventPlugin) {
            EventPlugin eventPlugin = (EventPlugin) plugin;
            if (eventPlugin.getCoreConfigManager() != null) {
                currentLang = eventPlugin.getCoreConfigManager().getLanguage();
            }
        }
        status.put("language", currentLang);
        
        response.put("data", status);
        return response;
    }
    
    /**
     * GET /api/language/get - Gibt die aktuelle Sprache zurück
     */
    public Map<String, Object> getLanguage() {
        Map<String, Object> response = new LinkedHashMap<>();
        String currentLang = "en";
        if (plugin instanceof EventPlugin) {
            EventPlugin eventPlugin = (EventPlugin) plugin;
            if (eventPlugin.getCoreConfigManager() != null) {
                currentLang = eventPlugin.getCoreConfigManager().getLanguage();
                plugin.getLogger().info("[Web-API] getLanguage() returning: " + currentLang);
            } else {
                plugin.getLogger().warning("[Web-API] CoreConfigManager is null!");
            }
        } else {
            plugin.getLogger().warning("[Web-API] Plugin is not EventPlugin!");
        }
        response.put("success", true);
        response.put("language", currentLang);
        return response;
    }
    
    /**
     * POST /api/language/save - Speichert die Sprache in der config.yml
     */
    public Map<String, Object> saveLanguage(Map<String, Object> requestBody) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            String language = (String) requestBody.get("language");
            if (language == null || language.isEmpty()) {
                response.put("success", false);
                response.put("message", "Keine Sprache angegeben");
                return response;
            }
            
            // Sprache in config.yml speichern
            if (plugin instanceof EventPlugin) {
                EventPlugin eventPlugin = (EventPlugin) plugin;
                if (eventPlugin.getCoreConfigManager() != null) {
                    eventPlugin.getCoreConfigManager().setLanguage(language);
                    plugin.getLogger().info("[Web-API] Sprache geändert zu: " + language);
                    response.put("success", true);
                    response.put("message", "Sprache gespeichert");
                    response.put("language", language);
                } else {
                    response.put("success", false);
                    response.put("message", "CoreConfigManager nicht verfügbar");
                }
            } else {
                response.put("success", false);
                response.put("message", "Plugin-Typ nicht unterstützt");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Web-API] Fehler beim Speichern der Sprache", e);
            response.put("success", false);
            response.put("message", "Fehler: " + e.getMessage());
        }
        return response;
    }

    /**
     * Berechnet die Server-TPS (Ticks per Second)
     */
    private double getTps() {
        try {
            // Versuche TPS über Reflection zu bekommen (funktioniert auf Spigot/Paper)
            Object server = plugin.getServer().getClass().getMethod("getServer").invoke(plugin.getServer());
            double[] recentTps = (double[]) server.getClass().getField("recentTps").get(server);
            return Math.round(recentTps[0] * 100.0) / 100.0;
        } catch (Exception e) {
            return 20.0; // Default TPS
        }
    }

    /**
     * Berechnet die Server-Uptime
     */
    private String getUptime() {
        try {
            long uptimeMillis = System.currentTimeMillis() - 
                java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
            long seconds = uptimeMillis / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;
            
            if (days > 0) {
                return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
            } else if (hours > 0) {
                return String.format("%dh %dm", hours, minutes % 60);
            } else {
                return String.format("%dm %ds", minutes, seconds % 60);
            }
        } catch (Exception e) {
            return "Unbekannt";
        }
    }
}
