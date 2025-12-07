package de.zfzfg.core.web;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Verwaltet Token-basierte Authentifizierung für das Web-Interface
 */
public class WebAuthManager {
    
    private final JavaPlugin plugin;
    private final Map<String, AuthToken> activeTokens = new ConcurrentHashMap<>();
    private final Map<String, AuthSession> activeSessions = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    
    // Konfigurierbare Werte
    private long tokenValidityMinutes = 10;
    private long sessionValidityMinutes = 60;
    private final String requiredPermission;
    
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    
    public WebAuthManager(JavaPlugin plugin, String requiredPermission) {
        this.plugin = plugin;
        this.requiredPermission = requiredPermission;
        
        // Cleanup-Task alle 5 Minuten
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupExpired, 
            20L * 60 * 5, 20L * 60 * 5);
    }
    
    /**
     * Generiert einen neuen Login-Token für einen Spieler
     * @param player Der Spieler
     * @return Der generierte Token oder null wenn keine Permission
     */
    public String generateToken(Player player) {
        if (!player.hasPermission(requiredPermission) && !player.isOp()) {
            return null;
        }
        
        // Lösche alte Tokens des Spielers
        activeTokens.entrySet().removeIf(entry -> 
            entry.getValue().playerUuid.equals(player.getUniqueId()));
        
        // Generiere neuen Token
        String token = generateSecureToken(8);
        AuthToken authToken = new AuthToken(
            player.getUniqueId(),
            player.getName(),
            System.currentTimeMillis() + (tokenValidityMinutes * 60 * 1000)
        );
        
        activeTokens.put(token, authToken);
        
        plugin.getLogger().log(Level.INFO, "Web-Token generiert für " + player.getName());
        return token;
    }
    
    /**
     * Validiert einen Token und erstellt eine Session
     * @param token Der zu validierende Token
     * @param clientIp Die IP des Clients
     * @return Session-ID oder null wenn ungültig
     */
    public String validateTokenAndCreateSession(String token, String clientIp) {
        AuthToken authToken = activeTokens.get(token);
        
        if (authToken == null) {
            plugin.getLogger().log(Level.WARNING, "Ungültiger Web-Token Versuch von " + clientIp);
            return null;
        }
        
        if (System.currentTimeMillis() > authToken.expiresAt) {
            activeTokens.remove(token);
            plugin.getLogger().log(Level.WARNING, "Abgelaufener Web-Token Versuch von " + clientIp);
            return null;
        }
        
        // Token ist gültig - erstelle Session
        activeTokens.remove(token); // Token kann nur einmal verwendet werden
        
        String sessionId = generateSecureToken(32);
        AuthSession session = new AuthSession(
            authToken.playerUuid,
            authToken.playerName,
            clientIp,
            System.currentTimeMillis() + (sessionValidityMinutes * 60 * 1000)
        );
        
        activeSessions.put(sessionId, session);
        
        plugin.getLogger().log(Level.INFO, "Web-Session erstellt für " + authToken.playerName + " von " + clientIp);
        return sessionId;
    }
    
    /**
     * Validiert eine Session
     * @param sessionId Die Session-ID (aus Cookie)
     * @param clientIp Die IP des Clients
     * @return AuthSession oder null wenn ungültig
     */
    public AuthSession validateSession(String sessionId, String clientIp) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        
        AuthSession session = activeSessions.get(sessionId);
        
        if (session == null) {
            return null;
        }
        
        if (System.currentTimeMillis() > session.expiresAt) {
            activeSessions.remove(sessionId);
            return null;
        }
        
        // Optional: IP-Check (kann bei dynamischen IPs Probleme machen)
        // if (!session.clientIp.equals(clientIp)) {
        //     return null;
        // }
        
        // Session verlängern bei Aktivität
        session.expiresAt = System.currentTimeMillis() + (sessionValidityMinutes * 60 * 1000);
        
        return session;
    }
    
    /**
     * Beendet eine Session (Logout)
     */
    public void invalidateSession(String sessionId) {
        AuthSession removed = activeSessions.remove(sessionId);
        if (removed != null) {
            plugin.getLogger().log(Level.INFO, "Web-Session beendet für " + removed.playerName);
        }
    }
    
    /**
     * Beendet alle Sessions eines Spielers
     */
    public void invalidateAllPlayerSessions(UUID playerUuid) {
        activeSessions.entrySet().removeIf(entry -> 
            entry.getValue().playerUuid.equals(playerUuid));
    }
    
    /**
     * Entfernt abgelaufene Tokens und Sessions
     */
    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        
        int tokensRemoved = 0;
        int sessionsRemoved = 0;
        
        Iterator<Map.Entry<String, AuthToken>> tokenIt = activeTokens.entrySet().iterator();
        while (tokenIt.hasNext()) {
            if (now > tokenIt.next().getValue().expiresAt) {
                tokenIt.remove();
                tokensRemoved++;
            }
        }
        
        Iterator<Map.Entry<String, AuthSession>> sessionIt = activeSessions.entrySet().iterator();
        while (sessionIt.hasNext()) {
            if (now > sessionIt.next().getValue().expiresAt) {
                sessionIt.remove();
                sessionsRemoved++;
            }
        }
        
        if (tokensRemoved > 0 || sessionsRemoved > 0) {
            plugin.getLogger().log(Level.FINE, "Cleanup: " + tokensRemoved + " Tokens, " + sessionsRemoved + " Sessions entfernt");
        }
    }
    
    /**
     * Generiert einen sicheren zufälligen Token
     */
    private String generateSecureToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(secureRandom.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
    
    /**
     * Setzt die Token-Gültigkeit
     */
    public void setTokenValidityMinutes(long minutes) {
        this.tokenValidityMinutes = minutes;
    }
    
    /**
     * Setzt die Session-Gültigkeit
     */
    public void setSessionValidityMinutes(long minutes) {
        this.sessionValidityMinutes = minutes;
    }
    
    /**
     * Gibt die Token-Gültigkeit in Minuten zurück
     */
    public long getTokenValidityMinutes() {
        return tokenValidityMinutes;
    }
    
    /**
     * Gibt die Anzahl aktiver Sessions zurück
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }
    
    // ============ Inner Classes ============
    
    private static class AuthToken {
        final UUID playerUuid;
        final String playerName;
        final long expiresAt;
        
        AuthToken(UUID playerUuid, String playerName, long expiresAt) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.expiresAt = expiresAt;
        }
    }
    
    public static class AuthSession {
        public final UUID playerUuid;
        public final String playerName;
        public final String clientIp;
        public long expiresAt;
        
        AuthSession(UUID playerUuid, String playerName, String clientIp, long expiresAt) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.clientIp = clientIp;
            this.expiresAt = expiresAt;
        }
    }
}
