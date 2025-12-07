package de.zfzfg.pvpwager.managers;

import de.zfzfg.pvpwager.models.PlayerStats;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory statistics manager tracking wins/losses/draws per player.
 */
public class StatsManager {
    private final Map<UUID, PlayerStats> statsByPlayer = new ConcurrentHashMap<>();

    private PlayerStats getOrCreate(UUID playerId) {
        return statsByPlayer.computeIfAbsent(playerId, PlayerStats::new);
    }

    public void recordWin(Player player) {
        if (player == null) return;
        getOrCreate(player.getUniqueId()).recordWin();
    }

    public void recordLoss(Player player) {
        if (player == null) return;
        getOrCreate(player.getUniqueId()).recordLoss();
    }

    public void recordDraw(Player player) {
        if (player == null) return;
        getOrCreate(player.getUniqueId()).recordDraw();
    }

    public Optional<PlayerStats> getStats(UUID playerId) {
        return Optional.ofNullable(statsByPlayer.get(playerId));
    }

    /**
     * Returns top N players by wins.
     */
    public List<PlayerStats> getTopByWins(int n) {
        return statsByPlayer.values().stream()
                .sorted(Comparator.comparingInt(PlayerStats::getWins).reversed())
                .limit(Math.max(n, 0))
                .collect(Collectors.toList());
    }

    // Persistence hooks
    public void loadFrom(Map<UUID, PlayerStats> loaded) {
        statsByPlayer.clear();
        if (loaded != null) {
            statsByPlayer.putAll(loaded);
        }
    }

    public Map<UUID, PlayerStats> toMap() {
        return new HashMap<>(statsByPlayer);
    }

    // Admin helpers
    public boolean reset(UUID playerId) {
        if (playerId == null) return false;
        statsByPlayer.put(playerId, new PlayerStats(playerId));
        return true;
    }

    public void addWins(UUID playerId, int n) {
        if (playerId == null || n <= 0) return;
        getOrCreate(playerId).addWins(n);
    }

    public void addLosses(UUID playerId, int n) {
        if (playerId == null || n <= 0) return;
        getOrCreate(playerId).addLosses(n);
    }

    public void addDraws(UUID playerId, int n) {
        if (playerId == null || n <= 0) return;
        getOrCreate(playerId).addDraws(n);
    }
}