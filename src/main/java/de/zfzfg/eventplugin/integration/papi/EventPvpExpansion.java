package de.zfzfg.eventplugin.integration.papi;

import de.zfzfg.eventplugin.managers.EventStatsManager;
import de.zfzfg.eventplugin.models.EventStats;
import de.zfzfg.pvpwager.managers.StatsManager;
import de.zfzfg.pvpwager.models.PlayerStats;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

public class EventPvpExpansion extends PlaceholderExpansion {
    private final EventStatsManager eventStatsManager;
    private final StatsManager pvpStatsManager;

    public EventPvpExpansion(EventStatsManager eventStatsManager, StatsManager pvpStatsManager) {
        this.eventStatsManager = eventStatsManager;
        this.pvpStatsManager = pvpStatsManager;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String getIdentifier() {
        return "eventpvp";
    }

    @Override
    public String getAuthor() {
        return "zfzfg";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || params == null) {
            return "0";
        }

        String key = params.toLowerCase(Locale.ROOT).trim();
        switch (key) {
            case "event_wins":
                return String.valueOf(getEventWins(player));
            case "event_participations":
                return String.valueOf(getEventParticipations(player));
            case "pvp_wins":
                return String.valueOf(getPvpWins(player));
            case "pvp_losses":
                return String.valueOf(getPvpLosses(player));
            case "pvp_draws":
                return String.valueOf(getPvpDraws(player));
            default:
                return null;
        }
    }

    private int getEventWins(OfflinePlayer player) {
        return eventStatsManager.getStats(player.getUniqueId())
                .map(EventStats::getWins)
                .orElse(0);
    }

    private int getEventParticipations(OfflinePlayer player) {
        return eventStatsManager.getStats(player.getUniqueId())
                .map(EventStats::getParticipations)
                .orElse(0);
    }

    private int getPvpWins(OfflinePlayer player) {
        return pvpStatsManager.getStats(player.getUniqueId())
                .map(PlayerStats::getWins)
                .orElse(0);
    }

    private int getPvpLosses(OfflinePlayer player) {
        return pvpStatsManager.getStats(player.getUniqueId())
                .map(PlayerStats::getLosses)
                .orElse(0);
    }

    private int getPvpDraws(OfflinePlayer player) {
        return pvpStatsManager.getStats(player.getUniqueId())
                .map(PlayerStats::getDraws)
                .orElse(0);
    }
}
