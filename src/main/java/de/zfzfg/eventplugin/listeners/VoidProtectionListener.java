package de.zfzfg.eventplugin.listeners;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.session.EventSession;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.models.MatchState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KRITISCHER SCHUTZ: Verhindert Inventar-Verlust durch Void-Fall
 * NUR wenn die Event/Match-Welt NICHT MEHR GELADEN ist.
 * 
 * WICHTIG: Während eines aktiven Events/Matches in der korrekten Welt
 * ist Void-Tod erlaubt und vorgesehen! Dieser Listener greift NUR ein wenn:
 * 1. Die Event/Match-Welt nicht mehr geladen ist UND Spieler woanders stirbt
 * 2. Der Spieler in einer falschen Welt ist (nicht Event/Lobby/Match-Welt)
 * 
 * Das ist ein Failsafe für den Fall, dass die Welt entladen wird,
 * während ein Spieler noch im Event/Match registriert ist.
 */
public class VoidProtectionListener implements Listener {
    
    private final EventPlugin plugin;
    
    // Cooldown um Spam zu verhindern (1 Teleport pro 2 Sekunden)
    private final ConcurrentHashMap<UUID, Long> teleportCooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 2000;
    
    // Y-Koordinate unter der als "Void-Gefahr" gilt
    private static final double VOID_DANGER_Y_OFFSET = 5;
    
    public VoidProtectionListener(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Void-Schaden NUR blockieren wenn Spieler NICHT in der korrekten Event/Match-Welt ist.
     * Wenn die Welt noch geladen ist und der Spieler dort ist -> normaler Void-Tod erlaubt.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVoidDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) return;
        
        Player player = (Player) event.getEntity();
        
        // Prüfe ob Spieler Schutz benötigt (NUR wenn in falscher Welt)
        if (!needsVoidProtection(player)) return;
        
        // Void-Schaden blockieren
        event.setCancelled(true);
        
        // Sofortige Rettung
        rescuePlayer(player, "Void-Schaden in falscher Welt erkannt");  // i18n-ignore: nur Log-Diagnose (VoidProtection)
    }
    
    /**
     * Überwacht Spielerbewegung NUR wenn in falscher Welt.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        
        Player player = event.getPlayer();
        
        // Prüfe ob Spieler Schutz benötigt
        if (!needsVoidProtection(player)) return;
        
        // Prüfe ob Spieler in Void-Gefahr ist
        World world = to.getWorld();
        if (world == null) {
            // Welt existiert nicht mehr - sofortige Rettung!
            rescuePlayer(player, "World no longer loaded");  // i18n-ignore: console-only diagnosis (VoidProtection)
            return;
        }
        
        double minY = world.getMinHeight();
        if (to.getY() < minY + VOID_DANGER_Y_OFFSET) {
            rescuePlayer(player, "Below void threshold in invalid world");  // i18n-ignore: console-only diagnosis (VoidProtection)
        }
    }
    
    /**
     * Prüft ob ein Spieler Void-Schutz benötigt.
     * 
     * SCHUTZ NUR WENN:
     * - Spieler in Event/Match registriert IST
     * - ABER NICHT in der korrekten Event/Lobby/Match-Welt ist
     * 
     * KEIN SCHUTZ WENN:
     * - Spieler in der korrekten Event/Lobby/Match-Welt ist (normaler Void-Tod erlaubt)
     * - Spieler nicht in Event/Match ist
     */
    private boolean needsVoidProtection(Player player) {
        String currentWorldName = player.getWorld() != null ? player.getWorld().getName() : null;
        if (currentWorldName == null) return true; // Welt null = definitiv Schutz nötig
        
        // Event-Prüfung
        Optional<EventSession> sessionOpt = plugin.getEventManager().getPlayerSession(player);
        if (sessionOpt.isPresent()) {
            EventSession session = sessionOpt.get();
            EventSession.EventState state = session.getState();
            
            // Nur während aktiver Event-Phasen prüfen
            if (state == EventSession.EventState.JOIN_PHASE ||
                state == EventSession.EventState.COUNTDOWN ||
                state == EventSession.EventState.RUNNING) {
                
                String eventWorld = session.getConfig().getEventWorld();
                String lobbyWorld = session.getConfig().getLobbyWorld();
                
                // Prüfe ob Event/Lobby-Welten noch existieren
                World eventW = eventWorld != null ? Bukkit.getWorld(eventWorld) : null;
                World lobbyW = lobbyWorld != null ? Bukkit.getWorld(lobbyWorld) : null;
                
                // Wenn Spieler IN der korrekten Welt ist -> KEIN Schutz (Void-Tod erlaubt)
                if (currentWorldName.equals(eventWorld) && eventW != null) {
                    return false; // In Event-Welt, kein Schutz nötig
                }
                if (currentWorldName.equals(lobbyWorld) && lobbyW != null) {
                    return false; // In Lobby-Welt, kein Schutz nötig
                }
                
                // Spieler ist im Event registriert, aber NICHT in korrekter Welt
                // -> SCHUTZ AKTIVIEREN
                return true;
            }
        }
        
        // PvP-Match-Prüfung
        Match match = plugin.getMatchManager().getMatchByPlayer(player);
        if (match != null) {
            MatchState state = match.getState();
            
            // Nur während aktiver Match-Phasen prüfen
            if (state == MatchState.SETUP || 
                state == MatchState.STARTING || 
                state == MatchState.FIGHTING) {
                
                String arenaWorldName = null;
                if (match.getArena() != null) {
                    arenaWorldName = match.getArena().getArenaWorld();
                }
                
                // Prüfe ob Arena-Welt noch existiert
                World arenaW = arenaWorldName != null ? Bukkit.getWorld(arenaWorldName) : null;
                
                // Wenn Spieler IN der Arena-Welt ist -> KEIN Schutz (Void-Tod erlaubt)
                if (arenaWorldName != null && currentWorldName.equals(arenaWorldName) && arenaW != null) {
                    return false; // In Arena-Welt, kein Schutz nötig
                }
                
                // Spieler ist im Match registriert, aber NICHT in korrekter Welt
                // -> SCHUTZ AKTIVIEREN
                return true;
            }
        }
        
        // Spieler nicht in Event/Match -> kein Schutz durch diesen Listener
        return false;
    }
    
    /**
     * Teleportiert einen Spieler zu einem sicheren Standort.
     * Verwendet Cooldown um Teleport-Spam zu verhindern.
     */
    private void rescuePlayer(Player player, String reason) {
        UUID playerId = player.getUniqueId();
        
        // Cooldown prüfen
        Long lastTeleport = teleportCooldowns.get(playerId);
        long now = System.currentTimeMillis();
        if (lastTeleport != null && (now - lastTeleport) < COOLDOWN_MS) {
            return;
        }
        teleportCooldowns.put(playerId, now);
        
        Location safeLocation = findSafeLocation(player);
        
        if (safeLocation != null) {
            plugin.getLogger().warning(plugin.getConsoleMsg("void-protection-rescued", "player", player.getName(), "reason", reason));
            
            // Sofortiger Teleport
            player.teleport(safeLocation);
            
            // Schaden heilen falls nötig
            if (player.getHealth() < 4) {
                double maxHealth = 20.0;
                var attr = player.getAttribute(Attribute.MAX_HEALTH);
                if (attr != null) {
                    maxHealth = attr.getValue();
                }
                player.setHealth(Math.min(player.getHealth() + 10, maxHealth));
            }
            
            player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', 
                plugin.getConfigManager().getMessage("system.void-protection-teleported")));
        } else {
            plugin.getLogger().severe(plugin.getConsoleMsg("void-protection-critical", "player", player.getName()));
        }
    }
    
    /**
     * Findet einen sicheren Standort fuer den Spieler.
     *
     * <p>Die Kette liegt seit der Vereinheitlichung im
     * {@link de.zfzfg.core.location.SafeLocationResolver}; frueher stand hier eine eigene
     * Fassung, die mit den drei anderen Kopien im Plugin auseinanderlaufen konnte.</p>
     */
    private Location findSafeLocation(Player player) {
        return plugin.getSafeLocations().resolve(player);
    }

    /**
     * Bereinigt Cooldowns für offline Spieler.
     * Sollte periodisch aufgerufen werden.
     */
    public void cleanupCooldowns() {
        long now = System.currentTimeMillis();
        teleportCooldowns.entrySet().removeIf(entry -> 
            (now - entry.getValue()) > COOLDOWN_MS * 10 ||
            Bukkit.getPlayer(entry.getKey()) == null);
    }
}
