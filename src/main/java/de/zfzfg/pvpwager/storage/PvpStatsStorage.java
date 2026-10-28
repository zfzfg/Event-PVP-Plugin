package de.zfzfg.pvpwager.storage;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.PlayerStats;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PvpStatsStorage {
    private static final String FILE_NAME = "pvpstats.yml";

    public static Map<UUID, PlayerStats> load(EventPlugin plugin) {
        Map<UUID, PlayerStats> map = new HashMap<>();
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
                    int losses = players.getInt(key + ".losses", 0);
                    int draws = players.getInt(key + ".draws", 0);
                    PlayerStats stats = new PlayerStats(id);
                    stats.addWins(wins);
                    stats.addLosses(losses);
                    stats.addDraws(draws);
                    map.put(id, stats);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to parse player stats for key '" + key + "': " + e.getMessage());  // i18n-ignore: technical storage exception log
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load PvP stats: " + e.getMessage());  // i18n-ignore: technical storage exception log
        }
        return map;
    }

    private static String snapshot(Map<UUID, PlayerStats> stats) {
        YamlConfiguration cfg = new YamlConfiguration();
        for (var entry : stats.entrySet()) {
            cfg.set("players." + entry.getKey() + ".wins", entry.getValue().getWins());
            cfg.set("players." + entry.getKey() + ".losses", entry.getValue().getLosses());
            cfg.set("players." + entry.getKey() + ".draws", entry.getValue().getDraws());
        }
        return cfg.saveToString();
    }
    public static void save(EventPlugin plugin, Map<UUID, PlayerStats> stats) {
        try { de.zfzfg.core.util.OrderedFileWriter.now(new File(plugin.getDataFolder(), FILE_NAME).toPath(), snapshot(stats)); }
        catch (IOException ex) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Statistics write failed", ex); }
    }
    public static void saveAsync(EventPlugin plugin, Map<UUID, PlayerStats> stats) {
        de.zfzfg.core.util.OrderedFileWriter.later(new File(plugin.getDataFolder(), FILE_NAME).toPath(), snapshot(stats), plugin.getLogger());
    }
}
