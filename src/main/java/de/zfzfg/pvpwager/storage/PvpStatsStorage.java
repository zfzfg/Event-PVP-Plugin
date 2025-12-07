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
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load PvP stats: " + e.getMessage());
        }
        return map;
    }

    public static void save(EventPlugin plugin, Map<UUID, PlayerStats> stats) {
        try {
            File folder = plugin.getDataFolder();
            if (!folder.exists()) folder.mkdirs();
            File file = new File(folder, FILE_NAME);
            YamlConfiguration cfg = new YamlConfiguration();
            for (Map.Entry<UUID, PlayerStats> e : stats.entrySet()) {
                String base = "players." + e.getKey();
                cfg.set(base + ".wins", e.getValue().getWins());
                cfg.set(base + ".losses", e.getValue().getLosses());
                cfg.set(base + ".draws", e.getValue().getDraws());
            }
            saveYamlAtomic(cfg, file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save PvP stats: " + e.getMessage());
        }
    }

    public static void saveAsync(EventPlugin plugin, Map<UUID, PlayerStats> stats) {
        // Run disk I/O asynchronously to avoid blocking the main server thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                save(plugin, stats);
            } catch (Exception e) {
                plugin.getLogger().warning("Async save PvP stats failed: " + e.getMessage());
            }
        });
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Object> FILE_LOCKS = new java.util.concurrent.ConcurrentHashMap<>();

    private static void saveYamlAtomic(org.bukkit.configuration.file.YamlConfiguration cfg, java.io.File file) throws java.io.IOException {
        Object lock = FILE_LOCKS.computeIfAbsent(file.getAbsolutePath(), k -> new Object());
        synchronized (lock) {
            java.io.File tmp = new java.io.File(file.getParentFile(), file.getName() + ".tmp");
            cfg.save(tmp);
            try {
                java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                cfg.save(file);
            }
        }
    }
}