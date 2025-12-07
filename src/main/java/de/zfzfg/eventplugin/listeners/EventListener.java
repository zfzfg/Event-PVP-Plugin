package de.zfzfg.eventplugin.listeners;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.session.EventSession;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EventListener implements Listener {
    
    private final EventPlugin plugin;
    
    // KRITISCH: Speichert die Respawn-Location für Spieler die im Event gestorben sind.
    // Das Event könnte bereits beendet sein wenn der Spieler respawnt, daher müssen
    // wir die Location VORHER speichern (beim Tod).
    private final Map<UUID, Location> pendingEventRespawnLocations = new ConcurrentHashMap<>();
    
    public EventListener(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Optional<EventSession> sessionOpt = plugin.getEventManager().getPlayerSession(player);
        
        if (sessionOpt.isPresent()) {
            EventSession session = sessionOpt.get();
            
            // KRITISCH: Original-Location JETZT speichern, BEVOR das Event beendet wird!
            // Das Event könnte schon beendet sein wenn der Spieler respawnt.
            Location savedLocation = plugin.getEventManager().getSavedLocation(player.getUniqueId());
            if (savedLocation != null) {
                pendingEventRespawnLocations.put(player.getUniqueId(), savedLocation.clone());
                plugin.getLogger().info("[EventDeath] Original-Location für " + player.getName() + " gespeichert: " +
                    (savedLocation.getWorld() != null ? savedLocation.getWorld().getName() : "NULL") + " @ " + 
                    String.format("%.2f, %.2f, %.2f", savedLocation.getX(), savedLocation.getY(), savedLocation.getZ()));
            } else {
                plugin.getLogger().warning("[EventDeath] WARNUNG: Keine gespeicherte Location für " + player.getName() + " gefunden!");
            }
            
            if (session.getState() == EventSession.EventState.RUNNING) {
                session.handlePlayerDeath(player);
            }
        }
    }
    
    /**
     * KRITISCH: Sicherer Respawn-Handler für Event-Spieler.
     * 
     * Priorität: HIGHEST um sicherzustellen, dass wir nach anderen Plugins kommen
     * und den Respawn-Ort definitiv setzen können.
     * 
     * WICHTIG: Verwendet die beim Tod gespeicherte Location aus pendingEventRespawnLocations,
     * da das Event möglicherweise schon beendet ist wenn der Spieler respawnt!
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // ZUERST: Prüfe ob wir eine gespeicherte Respawn-Location haben (vom Death-Event)
        Location savedFromDeath = pendingEventRespawnLocations.remove(playerId);
        
        if (savedFromDeath != null) {
            // Wir haben eine gespeicherte Location aus dem Death-Event
            Location safeLocation = prepareSafeLocation(savedFromDeath, player.getName());
            
            if (safeLocation != null) {
                String targetCoords = String.format("%.2f, %.2f, %.2f", 
                    safeLocation.getX(), safeLocation.getY(), safeLocation.getZ());
                String targetWorld = safeLocation.getWorld() != null ? safeLocation.getWorld().getName() : "NULL";
                
                plugin.getLogger().info("[EventRespawn] " + player.getName() + " verwendet gespeicherte Location: " + 
                    targetWorld + " @ " + targetCoords);
                
                event.setRespawnLocation(safeLocation);
                verifySingleTeleport(player, safeLocation.clone());
            }
            return;
        }
        
        // FALLBACK: Prüfe ob Spieler noch in einer aktiven Event-Session ist
        Optional<EventSession> sessionOpt = plugin.getEventManager().getPlayerSession(player);
        
        if (sessionOpt.isPresent()) {
            EventSession session = sessionOpt.get();
            Location safeRespawnLocation = determineSafeRespawnLocation(player, session);
            
            if (safeRespawnLocation != null) {
                String targetCoords = String.format("%.2f, %.2f, %.2f", 
                    safeRespawnLocation.getX(), safeRespawnLocation.getY(), safeRespawnLocation.getZ());
                String targetWorld = safeRespawnLocation.getWorld() != null ? safeRespawnLocation.getWorld().getName() : "NULL";
                
                plugin.getLogger().info("[EventRespawn] " + player.getName() + " (aus Session) soll zu " + 
                    targetWorld + " @ " + targetCoords + " teleportiert werden");
                
                event.setRespawnLocation(safeRespawnLocation);
                verifySingleTeleport(player, safeRespawnLocation.clone());
            }
        }
    }
    
    /**
     * Bereitet eine gespeicherte Location für den Respawn vor.
     */
    private Location prepareSafeLocation(Location savedLocation, String playerName) {
        if (savedLocation == null || savedLocation.getWorld() == null) {
            plugin.getLogger().warning("[EventRespawn] Gespeicherte Location für " + playerName + " ist ungültig!");
            return getMainWorldSpawn();
        }
        
        String worldName = savedLocation.getWorld().getName();
        World world = Bukkit.getWorld(worldName);
        
        if (world == null) {
            plugin.getLogger().warning("[EventRespawn] Welt " + worldName + " für " + playerName + 
                " nicht mehr geladen - verwende Hauptwelt-Spawn");
            return getMainWorldSpawn();
        }
        
        Location safeLocation = savedLocation.clone();
        safeLocation.setWorld(world);
        return safeLocation;
    }
    
    /**
     * EINMALIGE Verifizierung ob der Spieler am richtigen Ort respawned ist.
     */
    private void verifySingleTeleport(Player player, Location expected) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            
            Location current = player.getLocation();
            String currentWorld = current.getWorld() != null ? current.getWorld().getName() : "NULL";
            String expectedWorld = expected.getWorld() != null ? expected.getWorld().getName() : "NULL";
            
            boolean wrongWorld = !currentWorld.equals(expectedWorld);
            double distance = wrongWorld ? Double.MAX_VALUE : current.distance(expected);
            boolean criticallyFar = distance > 50.0;
            
            String currentCoords = String.format("%.2f, %.2f, %.2f", current.getX(), current.getY(), current.getZ());
            String expectedCoords = String.format("%.2f, %.2f, %.2f", expected.getX(), expected.getY(), expected.getZ());
            
            if (wrongWorld) {
                plugin.getLogger().warning("[EventRespawn-Verify] KRITISCH: " + player.getName() + " ist in FALSCHER WELT!");
                plugin.getLogger().warning("  Erwartet: " + expectedWorld + " @ " + expectedCoords);
                plugin.getLogger().warning("  Aktuell:  " + currentWorld + " @ " + currentCoords);
                plugin.getLogger().info("[EventRespawn-Verify] Teleportiere " + player.getName() + " zur korrekten Welt...");
                player.teleport(expected);
                verifyFinalTeleport(player, expected);
            } else if (criticallyFar) {
                plugin.getLogger().warning("[EventRespawn-Verify] " + player.getName() + " ist " + 
                    String.format("%.1f", distance) + " Blöcke von der erwarteten Position entfernt!");
                plugin.getLogger().info("[EventRespawn-Verify] Teleportiere " + player.getName() + " zur korrekten Position...");
                player.teleport(expected);
                verifyFinalTeleport(player, expected);
            } else {
                plugin.getLogger().info("[EventRespawn-Verify] ✓ " + player.getName() + 
                    " korrekt in " + currentWorld + " @ " + currentCoords + 
                    " (Distanz: " + String.format("%.1f", distance) + " Blöcke - OK)");
            }
        }, 5L);
    }
    
    /**
     * Finale Verifizierung nach Korrektur-Teleport.
     */
    private void verifyFinalTeleport(Player player, Location expected) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            
            Location current = player.getLocation();
            String currentWorld = current.getWorld() != null ? current.getWorld().getName() : "NULL";
            String expectedWorld = expected.getWorld() != null ? expected.getWorld().getName() : "NULL";
            
            boolean wrongWorld = !currentWorld.equals(expectedWorld);
            double distance = wrongWorld ? Double.MAX_VALUE : current.distance(expected);
            
            if (wrongWorld || distance > 50.0) {
                plugin.getLogger().severe("[EventRespawn-Verify] FEHLER: Korrektur-Teleport für " + player.getName() + 
                    " fehlgeschlagen!");
                player.teleport(expected);
            } else {
                plugin.getLogger().info("[EventRespawn-Verify] ✓ Korrektur erfolgreich - " + player.getName() + 
                    " jetzt in " + currentWorld);
            }
        }, 3L);
    }
    
    /**
     * Prüft ob die Event-Welt entladen ist.
     */
    private boolean isEventWorldUnloaded(EventSession session) {
        String eventWorldName = session.getConfig().getEventWorld();
        String lobbyWorldName = session.getConfig().getLobbyWorld();
        
        World eventWorld = eventWorldName != null ? Bukkit.getWorld(eventWorldName) : null;
        World lobbyWorld = lobbyWorldName != null ? Bukkit.getWorld(lobbyWorldName) : null;
        
        // Wenn beide Welten entladen sind, ist die Welt "entladen"
        return eventWorld == null && lobbyWorld == null;
    }
    
    /**
     * Ermittelt einen sicheren Respawn-Ort für einen Event-Spieler.
     * 
     * @param player Der Spieler, der respawnt
     * @param session Die Event-Session des Spielers
     * @return Sichere Location oder null wenn Standard-Respawn verwendet werden soll
     */
    private Location determineSafeRespawnLocation(Player player, EventSession session) {
        EventSession.EventState state = session.getState();
        boolean eventActive = state == EventSession.EventState.RUNNING || 
                              state == EventSession.EventState.COUNTDOWN ||
                              state == EventSession.EventState.JOIN_PHASE;
        
        String eventWorldName = session.getConfig().getEventWorld();
        String lobbyWorldName = session.getConfig().getLobbyWorld();
        
        World eventWorld = eventWorldName != null ? Bukkit.getWorld(eventWorldName) : null;
        World lobbyWorld = lobbyWorldName != null ? Bukkit.getWorld(lobbyWorldName) : null;
        
        // FALL 1: Event läuft und mindestens eine Welt ist geladen -> Spectator-Spawn
        if (eventActive && (eventWorld != null || lobbyWorld != null)) {
            if (session.getConfig().getDeathHandling().isSpectatorMode()) {
                // Priorität 1: Lobby-Spawn
                Location lobbySpawn = session.getConfig().getLobbySpawn();
                if (lobbySpawn != null && lobbyWorld != null) {
                    Location fixed = lobbySpawn.clone();
                    fixed.setWorld(lobbyWorld);
                    if (isSafeLocation(fixed)) {
                        return fixed;
                    }
                }
                
                // Priorität 2: Event-Welt Spawn
                if (eventWorld != null) {
                    Location eventSpawn = eventWorld.getSpawnLocation();
                    if (isSafeLocation(eventSpawn)) {
                        return eventSpawn;
                    }
                }
                
                // Priorität 3: Lobby-Welt Spawn
                if (lobbyWorld != null) {
                    Location lobbyWorldSpawn = lobbyWorld.getSpawnLocation();
                    if (isSafeLocation(lobbyWorldSpawn)) {
                        return lobbyWorldSpawn;
                    }
                }
            }
            
            // Welt ist noch geladen - normaler Respawn in Event/Lobby erlaubt
            return null;
        }
        
        // FALL 2: Event nicht mehr aktiv ODER BEIDE Welten nicht mehr geladen
        // -> IMMER zum Original-Standort teleportieren!
        if (!eventActive || (eventWorld == null && lobbyWorld == null)) {
            // Versuche gespeicherten Original-Standort zu finden
            Location savedLocation = plugin.getEventManager().getSavedLocation(player.getUniqueId());
            if (savedLocation != null && savedLocation.getWorld() != null) {
                World savedWorld = Bukkit.getWorld(savedLocation.getWorld().getName());
                if (savedWorld != null) {
                    Location safeLoc = savedLocation.clone();
                    safeLoc.setWorld(savedWorld);
                    plugin.getLogger().info("[SafeRespawn] Event beendet/Welt entladen - " + 
                        player.getName() + " wird zu Original-Standort teleportiert: " + 
                        savedWorld.getName());
                    return safeLoc;
                }
            }
            
            // Fallback zur Hauptwelt
            return getMainWorldSpawn();
        }
        
        return null;
    }
    
    /**
     * Prüft, ob eine Location sicher ist (nicht im Void, nicht in Blöcken).
     */
    private boolean isSafeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        
        // Prüfe ob Welt noch geladen ist
        if (Bukkit.getWorld(loc.getWorld().getName()) == null) return false;
        
        // Prüfe Y-Koordinate (Void-Schutz)
        double minY = loc.getWorld().getMinHeight();
        if (loc.getY() < minY + 5) return false;
        
        return true;
    }
    
    /**
     * Prüft, ob eine Location unsicher ist (Void, ungeladene Welt).
     */
    private boolean isUnsafeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return true;
        
        // Prüfe ob Welt noch geladen ist
        if (Bukkit.getWorld(loc.getWorld().getName()) == null) return true;
        
        // Prüfe Y-Koordinate (Void-Schutz)
        double minY = loc.getWorld().getMinHeight();
        if (loc.getY() < minY + 2) return true;
        
        return false;
    }
    
    /**
     * Gibt den Spawn der Hauptwelt zurück.
     */
    private Location getMainWorldSpawn() {
        String mainWorldName = plugin.getConfigManager().getMainWorld();
        World mainWorld = mainWorldName != null ? Bukkit.getWorld(mainWorldName) : null;
        
        if (mainWorld == null && !Bukkit.getWorlds().isEmpty()) {
            mainWorld = Bukkit.getWorlds().get(0);
        }
        
        if (mainWorld != null) {
            return mainWorld.getSpawnLocation();
        }
        
        return null;
    }
    
    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        Optional<EventSession> sessionOpt = plugin.getEventManager().getPlayerSession(player);
        
        if (sessionOpt.isPresent()) {
            EventSession session = sessionOpt.get();
            if (session.getState() == EventSession.EventState.RUNNING) {
                session.handleItemPickup(player, event.getItem().getItemStack().getType());
            }
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Optional<EventSession> sessionOpt = plugin.getEventManager().getPlayerSession(player);
        
        if (sessionOpt.isPresent()) {
            sessionOpt.get().removePlayer(player);
        }
    }
}