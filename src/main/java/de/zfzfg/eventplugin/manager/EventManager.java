package de.zfzfg.eventplugin.manager;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.model.EventConfig;
import de.zfzfg.eventplugin.session.EventSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class EventManager {
    
    private final EventPlugin plugin;
    private final Map<String, EventSession> activeSessions;
    // O(1) Index: Spieler -> EventId
    private final java.util.concurrent.ConcurrentHashMap<java.util.UUID, String> playerToEventId = new java.util.concurrent.ConcurrentHashMap<>();
    // Global store: pre-event player locations that survive session removal
    private final java.util.concurrent.ConcurrentHashMap<java.util.UUID, Location> globalSavedLocations = new java.util.concurrent.ConcurrentHashMap<>();
    
    public EventManager(EventPlugin plugin) {
        this.plugin = plugin;
        this.activeSessions = new HashMap<>();
    }
    
    public boolean createEvent(String eventId) {
        if (activeSessions.containsKey(eventId)) {
            return false;
        }
        
        EventConfig config = plugin.getConfigManager().getEventConfig(eventId);
        if (config == null) {
            return false;
        }
        
        EventSession session = new EventSession(plugin, config);
        activeSessions.put(eventId, session);
        return true;
    }
    
    public Optional<EventSession> getSession(String eventId) {
        return Optional.ofNullable(activeSessions.get(eventId));
    }
    
    public Optional<EventSession> getPlayerSession(Player player) {
        String eventId = playerToEventId.get(player.getUniqueId());
        if (eventId == null) return Optional.empty();
        EventSession session = activeSessions.get(eventId);
        return Optional.ofNullable(session);
    }
    
    public void removeSession(String eventId) {
        EventSession session = activeSessions.remove(eventId);
        if (session != null) {
            // Entferne alle Teilnehmer aus dem Index
            for (java.util.UUID uuid : new java.util.HashSet<>(session.getParticipants())) {
                playerToEventId.remove(uuid);
            }
        }
    }
    
    public boolean isEventActive(String eventId) {
        return activeSessions.containsKey(eventId);
    }
    
    public void stopAllEvents() {
        for (EventSession session : activeSessions.values()) {
            session.forceStop();
        }
        activeSessions.clear();
        playerToEventId.clear();
        globalSavedLocations.clear();
    }
    
    public Map<String, EventSession> getActiveSessions() {
        return activeSessions;
    }

    // Index-API: von EventSession aufrufen
    public void indexPlayer(String eventId, java.util.UUID playerId) {
        playerToEventId.put(playerId, eventId);
    }

    public void unindexPlayer(java.util.UUID playerId) {
        playerToEventId.remove(playerId);
    }

    // Global saved locations API
    public void savePlayerLocation(java.util.UUID playerId, Location location) {
        if (location != null) {
            globalSavedLocations.put(playerId, location.clone());
        }
    }

    public Location getSavedLocation(java.util.UUID playerId) {
        return globalSavedLocations.get(playerId);
    }

    public void clearSavedLocation(java.util.UUID playerId) {
        globalSavedLocations.remove(playerId);
    }
}