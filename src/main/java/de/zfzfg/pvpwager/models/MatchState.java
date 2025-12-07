package de.zfzfg.pvpwager.models;

public enum MatchState {
    SETUP,       // Match is being set up (wager, arena, equipment selection)
    STARTING,    // Countdown is running before the match starts
    FIGHTING,    // Match is active, players are fighting
    ENDED        // Match has ended (winner determined or draw)
}