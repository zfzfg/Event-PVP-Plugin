package de.zfzfg.pvpwager.models;

import java.util.UUID;

/**
 * Simple per-player statistics for PvP wagers.
 */
public class PlayerStats {
    private final UUID playerId;
    private int wins;
    private int losses;
    private int draws;

    public PlayerStats(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() { return playerId; }
    public int getWins() { return wins; }
    public int getLosses() { return losses; }
    public int getDraws() { return draws; }

    public void recordWin() { wins++; }
    public void recordLoss() { losses++; }
    public void recordDraw() { draws++; }

    // Admin/persistence helpers
    public void addWins(int n) { if (n > 0) wins += n; }
    public void addLosses(int n) { if (n > 0) losses += n; }
    public void addDraws(int n) { if (n > 0) draws += n; }
    public void reset() { wins = 0; losses = 0; draws = 0; }
}