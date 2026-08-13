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
                plugin.getLogger().info(plugin.getConsoleMsg("pvp-death-saved", "player", player.getName(), "location", String.format("%s @ %.2f, %.2f, %.2f", (savedLocation.getWorld() != null ? savedLocation.getWorld().getName() : "NULL"), savedLocation.getX(), savedLocation.getY(), savedLocation.getZ())));
            } else {
                plugin.getLogger().warning(plugin.getConsoleMsg("pvp-death-no-location", "player", player.getName()));
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
    /**
     * Spielt das Inventar von vor dem Event zurueck, nachdem ein Teilnehmer gestorben ist.
     *
     * <p>Nur, wenn er tatsaechlich ausgeschieden ist: laeuft das Event mit Wiedereinstieg
     * weiter und der Spieler darf zurueck in die Arena, wuerde ihm das Survival-Inventar
     * dort nur im Weg stehen. Massgeblich ist der Zustand der Guard-Sitzung, nicht das
     * Death-Event.</p>
     */
    private void restoreAfterEventRespawn(UUID playerId) {
        de.zfzfg.core.inventory.InventorySessionManager sessions = plugin.getInventorySessions();
        if (sessions == null || !sessions.isManaged()
                || !plugin.getInventoryConfig().restoreOnRespawn()) {
            return;
        }
        de.zfzfg.core.inventory.guard.GuardEntry entry = plugin.getInventoryGuard().get(playerId);
        if (entry == null
                || entry.context() != de.zfzfg.core.inventory.guard.GuardContext.EVENT) {
            return;
        }
        if (plugin.getEventManager().isPlayerInEvent(playerId)) {
            // Noch dabei - der Rueckweg laeuft spaeter ueber teleportBack.
            return;
        }
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin,
                () -> sessions.finish(playerId, outcome -> {
                    if (!outcome.isSuccess()) {
                        plugin.getLogger().warning(plugin.getConsoleMsg("inventory-respawn-restore-failed",
                                "player", playerId.toString(), "reason", outcome.name()));
                    }
                }), de.zfzfg.core.util.Time.ticks(15));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        restoreAfterEventRespawn(playerId);

        // ZUERST: Prüfe ob wir eine gespeicherte Respawn-Location haben (vom Death-Event)
        Location savedFromDeath = pendingEventRespawnLocations.remove(playerId);
        
        if (savedFromDeath != null) {
            // Wir haben eine gespeicherte Location aus dem Death-Event
            Location safeLocation = prepareSafeLocation(savedFromDeath, player.getName());
            
            if (safeLocation != null) {
                String targetCoords = String.format("%.2f, %.2f, %.2f", 
                    safeLocation.getX(), safeLocation.getY(), safeLocation.getZ());
                String targetWorld = safeLocation.getWorld() != null ? safeLocation.getWorld().getName() : "NULL";
                
                plugin.getLogger().info(plugin.getConsoleMsg("safe-teleport-player", "player", player.getName(), "world", targetWorld, "coords", targetCoords));
                
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
                
                plugin.getLogger().info(plugin.getConsoleMsg("safe-teleport-player", "player", player.getName(), "world", targetWorld, "coords", targetCoords));
                
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
            plugin.getLogger().warning(plugin.getConsoleMsg("safe-respawn-invalid-loc", "player", playerName));
            return getMainWorldSpawn();
        }
        
        String worldName = savedLocation.getWorld().getName();
        World world = Bukkit.getWorld(worldName);
        
        if (world == null) {
            plugin.getLogger().warning(plugin.getConsoleMsg("safe-respawn-unloaded-world", "world", worldName, "player", playerName));
            return getMainWorldSpawn();
        }
        
        Location safeLocation = savedLocation.clone();
        safeLocation.setWorld(world);
        return safeLocation;
    }
    
    /**
     * Kontrolliert nach dem Respawn, ob der Spieler wirklich dort gelandet ist.
     *
     * <p>Pruefung und Korrektur liegen seit der Vereinheitlichung im
     * {@link de.zfzfg.core.location.SafeLocationResolver}. Hier wird bewusst nur geprueft
     * und nicht teleportiert: das Ziel setzt {@code setRespawnLocation()}.</p>
     */
    private void verifySingleTeleport(Player player, Location expected) {
        plugin.getSafeLocations().verifyArrival(player, expected);
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
                    plugin.getLogger().info(plugin.getConsoleMsg("safe-teleport-player", "player", player.getName(), "world", savedWorld.getName(), "coords", String.format("%.0f, %.0f, %.0f", safeLoc.getX(), safeLoc.getY(), safeLoc.getZ())));
                    return safeLoc;
                }
            }
            
            // Fallback zur Hauptwelt
            return getMainWorldSpawn();
        }
        
        return null;
    }
    
    /** Ob eine Location benutzbar ist. Delegiert an die gemeinsame Pruefung. */
    private boolean isSafeLocation(Location loc) {
        return plugin.getSafeLocations().isSafe(loc);
    }

    /** Spawn der Hauptwelt, ersatzweise der ersten geladenen Welt. */
    private Location getMainWorldSpawn() {
        return plugin.getSafeLocations().fallbackSpawn();
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
            queueRestoreForQuit(player);
            sessionOpt.get().removePlayer(player);
        }
    }

    /**
     * Reiht die Inventar-Wiederherstellung fuer den naechsten Login ein.
     *
     * <p>Das Gegenstueck zu {@code PvPListener.queueRestoreForQuit}: bisher verliess sich
     * das Event-Modul allein auf das Join-Sicherheitsnetz. Beide Wege fuehren zum Ziel, aber
     * mit dem Einreihen greift die Wiederherstellung schon im Provider selbst - eine Schicht
     * frueher und damit unabhaengig davon, ob das Netz spaeter zieht.</p>
     */
    private void queueRestoreForQuit(Player player) {
        de.zfzfg.core.inventory.InventorySessionManager sessions = plugin.getInventorySessions();
        if (sessions == null || !sessions.isManaged()
                || !plugin.getInventoryConfig().restoreOnRejoin()) {
            return;
        }
        de.zfzfg.core.inventory.guard.GuardEntry entry =
                plugin.getInventoryGuard().get(player.getUniqueId());
        if (entry == null
                || entry.context() != de.zfzfg.core.inventory.guard.GuardContext.EVENT) {
            return;
        }
        sessions.queueForJoin(player.getUniqueId());
        plugin.getLogger().info(plugin.getConsoleMsg("inventory-quit-queued", "player", player.getName()));
    }
}