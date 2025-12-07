package de.zfzfg.eventplugin.models;

import java.util.UUID;

/**
 * Simple per-player statistics for Events.
 */
public class EventStats {
    private final UUID playerId;
    private int wins;
    private int participations;

    public EventStats(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() { return playerId; }
    public int getWins() { return wins; }
    public int getParticipations() { return participations; }

    public void recordWin() { wins++; }
    public void recordParticipation() { participations++; }

    // Admin/persistence helpers
    public void addWins(int n) { if (n > 0) wins += n; }
    public void addParticipations(int n) { if (n > 0) participations += n; }
    public void reset() { wins = 0; participations = 0; }
}