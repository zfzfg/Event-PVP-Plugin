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
        return "&cNachricht nicht gefunden: " + path;
    }

    /**
     * Lädt alle bekannten Konfigurationen neu und aktualisiert abhängige Manager.
     */
    public void reloadAll() {
        // Core
        CoreConfigManager core = plugin.getCoreConfigManager();
        if (core != null) {
            try { core.reloadAll(); } catch (Exception e) { plugin.getLogger().warning("Core-Reload Fehler: " + e.getMessage()); }
        }

        // Events
        ConfigManager events = plugin.getConfigManager();
        if (events != null) {
            try { events.reloadConfigs(); } catch (Exception e) { plugin.getLogger().warning("Event-Reload Fehler: " + e.getMessage()); }
        }

        // PvP
        de.zfzfg.pvpwager.managers.ConfigManager pvp = plugin.getPvpConfigManager();
        if (pvp != null) {
            try { pvp.reloadConfigs(); } catch (Exception e) { plugin.getLogger().warning("PvP-Reload Fehler: " + e.getMessage()); }
        }

        // Abhängige Manager aktualisieren
        ArenaManager arenas = plugin.getArenaManager();
        if (arenas != null) {
            try { arenas.reloadArenas(); } catch (Exception e) { plugin.getLogger().warning("Arenen-Reload Fehler: " + e.getMessage()); }
        }
        EquipmentManager equip = plugin.getEquipmentManager();
        if (equip != null) {
            try { equip.reloadEquipmentSets(); } catch (Exception e) { plugin.getLogger().warning("Equipment-Reload Fehler: " + e.getMessage()); }
        }

        WorldStateManager worldState = plugin.getWorldStateManager();
        if (worldState != null) {
            try { worldState.clearCache(); } catch (Exception ignored) {}
        }

        plugin.getLogger().info("Alle Konfigurationen und abhängigen Manager neu geladen.");
    }
}