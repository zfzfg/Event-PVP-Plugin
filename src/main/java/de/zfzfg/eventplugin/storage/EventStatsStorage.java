package de.zfzfg.eventplugin.storage;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.models.EventStats;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EventStatsStorage {
    private static final String FILE_NAME = "eventstats.yml";

    public static Map<UUID, EventStats> load(EventPlugin plugin) {
        Map<UUID, EventStats> map = new HashMap<>();
        try {
            File file = new File(plugin.getDataFolder(), FILE_NAME);
            if (!file.exists()) return map;
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection players = cfg.getConfigurationSection("players");
            if (players == null) return map;
            for (String key : players.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    int wins = players.getInt(key + ".wins", 0);
                    int parts = players.getInt(key + ".participations", 0);
                    EventStats stats = new EventStats(id);
                    stats.addWins(wins);
                    stats.addParticipations(parts);
                    map.put(id, stats);
                } catch (Exception e) {
                    // Kaputter Eintrag (z. B. keine UUID): ueberspringen, aber sichtbar machen.
                    plugin.getLogger().warning("Skipping invalid Event stats entry '" + key + "': " + e.getMessage());  // i18n-ignore: technical storage exception log
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load Event stats: " + e.getMessage());  // i18n-ignore: technical storage exception log
        }
        return map;
    }

    private static String snapshot(Map<UUID, EventStats> stats) {
        YamlConfiguration cfg = new YamlConfiguration();
        for (var entry : stats.entrySet()) {
            cfg.set("players." + entry.getKey() + ".wins", entry.getValue().getWins());
            cfg.set("players." + entry.getKey() + ".participations", entry.getValue().getParticipations());
        }
        return cfg.saveToString();
    }
    public static void save(EventPlugin plugin, Map<UUID, EventStats> stats) {
        try { de.zfzfg.core.util.OrderedFileWriter.now(new File(plugin.getDataFolder(), FILE_NAME).toPath(), snapshot(stats)); }
        catch (IOException ex) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Statistics write failed", ex); }
    }
    public static void saveAsync(EventPlugin plugin, Map<UUID, EventStats> stats) {
        de.zfzfg.core.util.OrderedFileWriter.later(new File(plugin.getDataFolder(), FILE_NAME).toPath(), snapshot(stats), plugin.getLogger());
    }
}
