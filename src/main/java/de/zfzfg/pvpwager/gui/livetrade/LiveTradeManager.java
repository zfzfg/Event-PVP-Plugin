package de.zfzfg.pvpwager.gui.livetrade;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager für Live Trade Sessions.
 * Verwaltet alle aktiven Trade-Sessions und deren Lifecycle.
 */
public class LiveTradeManager {
    
    private final EventPlugin plugin;
    
    // Aktive Sessions: SessionId -> Session
    private final Map<UUID, LiveTradeSession> sessions = new ConcurrentHashMap<>();
    
    // Player -> SessionId Mapping für schnellen Zugriff
    private final Map<UUID, UUID> playerToSession = new ConcurrentHashMap<>();
    
    // Cleanup-Task für abgelaufene Sessions
    private BukkitTask cleanupTask;
    
    public LiveTradeManager(EventPlugin plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }
    
    /**
     * Erstellt eine neue Trade-Session zwischen zwei Spielern.
     * @return Die neue Session oder null wenn eine Session bereits existiert
     */
    public LiveTradeSession createSession(Player player1, Player player2) {
        // Prüfe ob einer der Spieler bereits in einer Session ist
        if (isInSession(player1)) {
            return null;
        }
        if (isInSession(player2)) {
            return null;
        }
        
        // Prüfe ob einer der Spieler in einem Match ist
        if (plugin.getMatchManager().isPlayerInMatch(player1)) {
            return null;
        }
        if (plugin.getMatchManager().isPlayerInMatch(player2)) {
            return null;
        }
        
        // Neue Session erstellen
        LiveTradeSession session = new LiveTradeSession(plugin, player1, player2);
        
        // Manager-Referenz setzen für Cleanup-Callbacks
        session.setManager(this);
        
        // Registrieren
        sessions.put(session.getSessionId(), session);
        playerToSession.put(player1.getUniqueId(), session.getSessionId());
        playerToSession.put(player2.getUniqueId(), session.getSessionId());
        
        return session;
    }
    
    /**
     * Beendet eine Session und räumt auf.
     */
    public void removeSession(LiveTradeSession session) {
        if (session == null) return;
        
        UUID sessionId = session.getSessionId();
        sessions.remove(sessionId);
        
        // Player Mappings entfernen
        playerToSession.remove(session.getPlayer1().getPlayerId());
        playerToSession.remove(session.getPlayer2().getPlayerId());
    }
    
    /**
     * Beendet die Session eines Spielers.
     */
    public void removeSession(Player player) {
        LiveTradeSession session = getSession(player);
        if (session != null) {
            removeSession(session);
        }
    }
    
    /**
     * Holt die Session eines Spielers.
     */
    public LiveTradeSession getSession(Player player) {
        UUID sessionId = playerToSession.get(player.getUniqueId());
        return sessionId != null ? sessions.get(sessionId) : null;
    }
    
    /**
     * Holt eine Session per ID.
     */
    public LiveTradeSession getSession(UUID sessionId) {
        return sessions.get(sessionId);
    }
    
    /**
     * Prüft ob ein Spieler in einer Session ist.
     */
    public boolean isInSession(Player player) {
        return playerToSession.containsKey(player.getUniqueId());
    }
    
    /**
     * Holt den LiveTradePlayer für einen Spieler.
     */
    public LiveTradePlayer getTradePlayer(Player player) {
        LiveTradeSession session = getSession(player);
        if (session == null) return null;
        return session.getTradePlayer(player);
    }
    
    /**
     * Startet den Cleanup-Task für abgelaufene Sessions.
     */
    private void startCleanupTask() {
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (LiveTradeSession session : sessions.values()) {
                if (session.isExpired() && !session.isEnded()) {
                    plugin.getLogger().info("PVP Wager Session expired: " + session.getSessionId());
                    session.abort();
                    removeSession(session);
                }
            }
        }, 20L * 30, 20L * 30); // Alle 30 Sekunden prüfen
    }
    
    /**
     * Behandelt Spieler-Quit.
     */
    public void handlePlayerQuit(Player player) {
        LiveTradeSession session = getSession(player);
        if (session != null && !session.isEnded()) {
            session.abort();
            removeSession(session);
        }
    }
    
    /**
     * Cleanup beim Plugin-Disable.
     */
    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        
        // Alle Sessions abbrechen
        for (LiveTradeSession session : sessions.values()) {
            if (!session.isEnded()) {
                session.abort();
            }
        }
        
        sessions.clear();
        playerToSession.clear();
    }
    
    /**
     * Anzahl aktiver Sessions.
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
    
    public EventPlugin getPlugin() {
        return plugin;
    }
}
