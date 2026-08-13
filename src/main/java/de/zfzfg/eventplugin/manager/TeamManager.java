package de.zfzfg.eventplugin.manager;

import org.bukkit.Color;
import org.bukkit.entity.Player;

import java.util.*;

public class TeamManager {
    
    public enum Team {
        RED("red", "&c&lRed", Color.RED),
        BLUE("blue", "&9&lBlue", Color.BLUE),
        GREEN("green", "&a&lGreen", Color.GREEN);
        
        private final String translationKey;
        private final String fallbackDisplayName;
        private final Color color;
        
        Team(String translationKey, String fallbackDisplayName, Color color) {
            this.translationKey = translationKey;
            this.fallbackDisplayName = fallbackDisplayName;
            this.color = color;
        }
        
        public String getTranslationKey() { return translationKey; }
        public String getDisplayName() { return fallbackDisplayName; }
        public Color getColor() { return color; }
    }
    
    private final Map<UUID, Team> playerTeams;
    private final Map<Team, Set<UUID>> teamMembers;
    
    public TeamManager() {
        this.playerTeams = new HashMap<>();
        this.teamMembers = new HashMap<>();
        for (Team team : Team.values()) {
            teamMembers.put(team, new HashSet<>());
        }
    }
    
    public void assignTeams(List<UUID> players, int teamCount) {
        playerTeams.clear();
        for (Team team : Team.values()) {
            teamMembers.get(team).clear();
        }
        
        // Mische Spieler
        List<UUID> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);
        
        // Sortiere überzählige Spieler aus (damit Teams gleich groß sind)
        int validPlayerCount = (shuffled.size() / teamCount) * teamCount;
        List<UUID> validPlayers = shuffled.subList(0, validPlayerCount);
        
        // Verteile Spieler auf Teams
        Team[] teams = teamCount == 2 ? 
            new Team[]{Team.RED, Team.BLUE} : 
            new Team[]{Team.RED, Team.BLUE, Team.GREEN};
        
        for (int i = 0; i < validPlayers.size(); i++) {
            UUID playerId = validPlayers.get(i);
            Team team = teams[i % teamCount];
            playerTeams.put(playerId, team);
            teamMembers.get(team).add(playerId);
        }
    }
    
    public Team getPlayerTeam(Player player) {
        return playerTeams.get(player.getUniqueId());
    }
    
    public Team getPlayerTeam(UUID playerId) {
        return playerTeams.get(playerId);
    }
    
    public Set<UUID> getTeamMembers(Team team) {
        return new HashSet<>(teamMembers.get(team));
    }
    
    public boolean areTeammates(Player p1, Player p2) {
        Team team1 = getPlayerTeam(p1);
        Team team2 = getPlayerTeam(p2);
        return team1 != null && team1 == team2;
    }
    
    public Team getWinningTeam(Set<UUID> alivePlayers) {
        for (Team team : Team.values()) {
            Set<UUID> teamMembers = getTeamMembers(team);
            if (!teamMembers.isEmpty()) {
                // Prüfe ob mindestens ein Teammitglied noch lebt
                for (UUID memberId : teamMembers) {
                    if (alivePlayers.contains(memberId)) {
                        // Prüfe ob alle noch lebenden Spieler in diesem Team sind
                        boolean allInTeam = true;
                        for (UUID aliveId : alivePlayers) {
                            if (!teamMembers.contains(aliveId)) {
                                allInTeam = false;
                                break;
                            }
                        }
                        if (allInTeam) {
                            return team;
                        }
                    }
                }
            }
        }
        return null;
    }
    
    public int getAliveTeamCount(Set<UUID> alivePlayers) {
        Set<Team> aliveTeams = new HashSet<>();
        for (UUID playerId : alivePlayers) {
            Team team = getPlayerTeam(playerId);
            if (team != null) {
                aliveTeams.add(team);
            }
        }
        return aliveTeams.size();
    }
    
    public void removePlayer(UUID playerId) {
        Team team = playerTeams.remove(playerId);
        if (team != null) {
            teamMembers.get(team).remove(playerId);
        }
    }
    
    public void clear() {
        playerTeams.clear();
        for (Team team : Team.values()) {
            teamMembers.get(team).clear();
        }
    }
    
    public Map<UUID, Team> getAllAssignments() {
        return new HashMap<>(playerTeams);
    }
}