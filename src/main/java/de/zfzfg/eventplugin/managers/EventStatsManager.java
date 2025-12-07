package de.zfzfg.eventplugin.managers;

import de.zfzfg.eventplugin.models.EventStats;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class EventStatsManager {
    private final Map<UUID, EventStats> statsByPlayer = new ConcurrentHashMap<>();

    private EventStats getOrCreate(UUID playerId) {
        return statsByPlayer.computeIfAbsent(playerId, EventStats::new);
    }

    public void recordParticipation(Player player) {
        if (player == null) return;
        getOrCreate(player.getUniqueId()).recordParticipation();
    }

    public void recordWin(Player player) {
        if (player == null) return;
        getOrCreate(player.getUniqueId()).recordWin();
    }

    public Optional<EventStats> getStats(UUID playerId) {
        return Optional.ofNullable(statsByPlayer.get(playerId));
    }

    public List<EventStats> getTopByWins(int n) {
        return statsByPlayer.values().stream()
                .sorted(Comparator.comparingInt(EventStats::getWins).reversed())
                .limit(Math.max(n, 0))
                .collect(Collectors.toList());
    }

    // Persistence hooks
    public void loadFrom(Map<UUID, EventStats> loaded) {
        statsByPlayer.clear();
        if (loaded != null) statsByPlayer.putAll(loaded);
    }

    public Map<UUID, EventStats> toMap() {
        return new HashMap<>(statsByPlayer);
    }

    // Admin helpers
    public boolean reset(UUID playerId) {
        if (playerId == null) return false;
        statsByPlayer.put(playerId, new EventStats(playerId));
        return true;
    }

    public void addWins(UUID playerId, int n) {
        if (playerId == null || n <= 0) return;
        getOrCreate(playerId).addWins(n);
    }

    public void addParticipations(UUID playerId, int n) {
        if (playerId == null || n <= 0) return;
        getOrCreate(playerId).addParticipations(n);
    }
}