package de.zfzfg.core.service;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.manager.ConfigManager;
import de.zfzfg.pvpwager.managers.ArenaManager;
import de.zfzfg.pvpwager.managers.EquipmentManager;
import de.zfzfg.eventplugin.world.WorldStateManager;
import de.zfzfg.core.config.CoreConfigManager;

/**
 * Zentraler Zugriff auf Konfigurationen und abhängige Reload-Operationen.
 * Vereinheitlicht das Nachladen von Core-, Event- und PvP-Konfigurationen
 * sowie das Aktualisieren von abhängigen Managern.
 */
public class ConfigurationService {

    private final EventPlugin plugin;

    public ConfigurationService(EventPlugin plugin) {
        this.plugin = plugin;
    }

    public String getPrefix() {
        return plugin.getConfigManager() != null ? plugin.getConfigManager().getPrefix() : "&6[Event]&r";
    }

    public String getMessage(String path, String... replacements) {
        if (plugin.getConfigManager() != null) {
            return plugin.getConfigManager().getMessage(path, replacements);
        }
        // i18n-ignore -- Notfall-Marker: greift nur, wenn gar kein ConfigManager
        // existiert, also auch kein Bundle geladen werden koennte. Englisch wie
        // der gleichartige Marker in AbstractWagerGui.t().
        return "&c[missing: " + path + "]";
    }

    /**
     * Lädt alle bekannten Konfigurationen neu und aktualisiert abhängige Manager.
     */
    public void reloadAll() {
        // Core
        CoreConfigManager core = plugin.getCoreConfigManager();
        if (core != null) {
            try { core.reloadAll(); } catch (Exception e) { plugin.getLogger().warning("Core reload error: " + e.getMessage()); }  // i18n-ignore: technical exception log
        }

        // Debug-Stufe aus der frisch geladenen config.yml uebernehmen
        de.zfzfg.core.monitoring.debug.DebugManager debug = plugin.getDebugManager();
        if (debug != null) {
            try { debug.loadFromConfig(); } catch (Exception e) { plugin.getLogger().warning("Debug reload error: " + e.getMessage()); }  // i18n-ignore: technical exception log
        }

        // Events
        ConfigManager events = plugin.getConfigManager();
        if (events != null) {
            try { events.reloadConfigs(); } catch (Exception e) { plugin.getLogger().warning("Event reload error: " + e.getMessage()); }  // i18n-ignore: technical exception log
        }

        // PvP
        de.zfzfg.pvpwager.managers.ConfigManager pvp = plugin.getPvpConfigManager();
        if (pvp != null) {
            try { pvp.reloadConfigs(); } catch (Exception e) { plugin.getLogger().warning("PvP reload error: " + e.getMessage()); }  // i18n-ignore: technical exception log
        }

        // Dependent managers
        ArenaManager arenas = plugin.getArenaManager();
        if (arenas != null) {
            try { arenas.reloadArenas(); } catch (Exception e) { plugin.getLogger().warning("Arenas reload error: " + e.getMessage()); }  // i18n-ignore: technical exception log
        }
        EquipmentManager equip = plugin.getEquipmentManager();
        if (equip != null) {
            try { equip.reloadEquipmentSets(); } catch (Exception e) { plugin.getLogger().warning("Equipment reload error: " + e.getMessage()); }  // i18n-ignore: technical exception log
        }

        WorldStateManager worldState = plugin.getWorldStateManager();
        if (worldState != null) {
            try { worldState.clearCache(); } catch (Exception ignored) {}
        }

        // Inventar-Verwaltung: haelt ihre Werte in final-Feldern, muss also neu gelesen werden.
        try { plugin.reloadInventoryConfig(); } catch (Exception e) { plugin.getLogger().warning("Inventory config reload error: " + e.getMessage()); }  // i18n-ignore: technical exception log

        plugin.getLogger().info(plugin.getConsoleMsg("core-reload-all"));
    }
}
