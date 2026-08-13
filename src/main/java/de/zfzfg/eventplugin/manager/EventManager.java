package de.zfzfg.eventplugin.manager;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.model.EventConfig;
import de.zfzfg.eventplugin.session.EventSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Optional;

public class EventManager {

    private final EventPlugin plugin;
    private final Map<String, EventSession> activeSessions;
    // O(1) Index: Spieler -> EventId
    private final ConcurrentHashMap<java.util.UUID, String> playerToEventId = new ConcurrentHashMap<>();
    // Global store: pre-event player locations that survive session removal
    private final ConcurrentHashMap<java.util.UUID, Location> globalSavedLocations = new ConcurrentHashMap<>();

    public EventManager(EventPlugin plugin) {
        this.plugin = plugin;
        this.activeSessions = new ConcurrentHashMap<>();
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

    /**
     * Ob dieser Spieler gerade an einem laufenden Event teilnimmt.
     *
     * <p>Arbeitet ueber den Index statt ueber {@code getPlayerSession}, damit die Frage auch
     * fuer offline stehende Spieler beantwortbar bleibt - genau das braucht der
     * Inventar-Guard beim Wiederanlauf und beim Join.</p>
     */
    public boolean isPlayerInEvent(java.util.UUID playerId) {
        String eventId = playerToEventId.get(playerId);
        return eventId != null && activeSessions.containsKey(eventId);
    }
    
    public void stopAllEvents() {
        for (EventSession session : activeSessions.values()) {
            session.forceStop();
        }
        activeSessions.clear();
        playerToEventId.clear();
        // Nur der Zwischenspeicher. Der ReturnLocationStore bleibt bewusst stehen: wer beim
        // Herunterfahren noch eine offene Position hat, braucht sie beim naechsten Start.
        globalSavedLocations.clear();
    }
    
    public Map<String, EventSession> getActiveSessions() {
        return new java.util.HashMap<>(activeSessions);
    }

    // Index-API: von EventSession aufrufen
    public void indexPlayer(String eventId, java.util.UUID playerId) {
        playerToEventId.put(playerId, eventId);
    }

    public void unindexPlayer(java.util.UUID playerId) {
        playerToEventId.remove(playerId);
    }

    // Global saved locations API
    //
    // Die Map im Arbeitsspeicher bleibt der schnelle Weg; parallel dazu haelt der
    // ReturnLocationStore dieselbe Position auf der Platte. Ein Absturz warf frueher genau
    // diese Zuordnung weg - das Inventar kam ueber das Guard-Journal zurueck, die Position
    // war verloren. Alle bestehenden Aufrufer bekommen die Persistenz hier mit, ohne selbst
    // etwas zu aendern.

    public void savePlayerLocation(java.util.UUID playerId, Location location) {
        if (location == null) {
            return;
        }
        globalSavedLocations.put(playerId, location.clone());
        if (plugin.getReturnLocations() != null) {
            plugin.getReturnLocations().remember(playerId, location,
                    de.zfzfg.core.location.ReturnReason.EVENT);
        }
    }

    /**
     * Die gemerkte Position. Faellt auf den persistenten Speicher zurueck, wenn die Map sie
     * nicht mehr hat - genau der Fall nach einem Serverneustart.
     */
    public Location getSavedLocation(java.util.UUID playerId) {
        Location inMemory = globalSavedLocations.get(playerId);
        if (inMemory != null) {
            return inMemory;
        }
        if (plugin.getReturnLocations() == null) {
            return null;
        }
        de.zfzfg.core.location.StoredReturn stored = plugin.getReturnLocations().peek(playerId);
        return stored == null ? null : stored.toLocation();
    }

    public void clearSavedLocation(java.util.UUID playerId) {
        globalSavedLocations.remove(playerId);
        if (plugin.getReturnLocations() != null) {
            plugin.getReturnLocations().forget(playerId);
        }
    }
}