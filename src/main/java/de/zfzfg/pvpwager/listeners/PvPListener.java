package de.zfzfg.pvpwager.listeners;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.models.MatchState;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import de.zfzfg.core.util.Time;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PvPListener implements Listener {
    
    private final EventPlugin plugin;
    
    // KRITISCH: Speichert die Respawn-Location für Spieler die im Match gestorben sind.
    // Das Match könnte bereits beendet sein wenn der Spieler respawnt, daher müssen
    // wir die Location VORHER speichern (beim Tod).
    private final Map<UUID, Location> pendingRespawnLocations = new ConcurrentHashMap<>();
    
    public PvPListener(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    private String getMsg(String key) {
        return plugin.getCoreConfigManager().getMessages().getString("messages.pvp-listener." + key, key);
    }
    
    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player attacker = (Player) event.getDamager();
        Player victim = (Player) event.getEntity();
        
        // Check if attacker is a spectator
        Match attackerMatch = plugin.getMatchManager().getMatchByPlayer(attacker);
        if (attackerMatch != null && attackerMatch.getSpectators().contains(attacker.getUniqueId())) {
            event.setCancelled(true);
            MessageUtil.sendMessage(attacker, getMsg("spectator-no-attack"));
            return;
        }
        
        // Check if victim is a spectator
        Match victimMatch = plugin.getMatchManager().getMatchByPlayer(victim);
        if (victimMatch != null && victimMatch.getSpectators().contains(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        
        // Check if both are in the same match
        Match match = plugin.getMatchManager().getMatch(attacker, victim);
        if (match == null) {
            // PvP außerhalb von Matches ist erlaubt (für Events)
            return;
        }
        
        // Check match state - only allow damage during FIGHTING
        if (match.getState() != MatchState.FIGHTING) {
            event.setCancelled(true);
            MessageUtil.sendMessage(attacker, getMsg("match-not-started"));
            return;
        }
        
        // Allow damage in active match
    }
    
    @EventHandler
    public void onSpectatorDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        Match match = plugin.getMatchManager().getMatchByPlayer(player);
        
        // Protect spectators from all damage
        if (match != null && match.getSpectators().contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Match match = plugin.getMatchManager().getMatchByPlayer(player);
        
        if (match != null && match.getState() == MatchState.FIGHTING) {
            
            // KRITISCH: Original-Location JETZT speichern, BEVOR das Match beendet wird!
            // Das Match könnte schon aus der Liste entfernt sein wenn der Spieler respawnt.
            Location originalLocation = match.getOriginalLocations().get(player.getUniqueId());
            if (originalLocation != null) {
                pendingRespawnLocations.put(player.getUniqueId(), originalLocation.clone());
                plugin.getLogger().info("[PvPDeath] Original-Location für " + player.getName() + " gespeichert: " +
                    originalLocation.getWorld().getName() + " @ " + 
                    String.format("%.2f, %.2f, %.2f", originalLocation.getX(), originalLocation.getY(), originalLocation.getZ()));
            } else {
                plugin.getLogger().warning("[PvPDeath] WARNUNG: Keine Original-Location für " + player.getName() + " gefunden!");
            }
            
            Player killer = player.getKiller();
            // Evaluate outcome one tick later to catch simultaneous deaths (double-kill/void)
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (match.getState() != MatchState.FIGHTING) return; // already handled

                Player p1 = match.getPlayer1();
                Player p2 = match.getPlayer2();
                boolean p1Dead = p1 == null || p1.isDead() || p1.getHealth() <= 0.0;
                boolean p2Dead = p2 == null || p2.isDead() || p2.getHealth() <= 0.0;

                if (p1Dead && p2Dead) {
                    match.broadcast("");
                    match.broadcast("&a&l━━━━━━━━━━━━━━━━━━━━━━━");
                    match.broadcast("&a&lDRAW (double-death)!");
                    match.broadcast("&a&l━━━━━━━━━━━━━━━━━━━━━━━");
                    match.broadcast("");
                    match.broadcast("&7Both players died nearly simultaneously.");
                    plugin.getMatchManager().endMatch(match, null, true);
                    return;
                }

                // Standard outcome
                if (killer != null && (killer.equals(match.getPlayer1()) || killer.equals(match.getPlayer2()))) {
                    plugin.getMatchManager().endMatch(match, killer, false);
                    event.setDeathMessage(MessageUtil.color(
                        "&c" + player.getName() + " &7wurde von &c" + killer.getName() + 
                        " &7im PvP-Match besiegt!"
                    ));
                } else {
                    Player opponent = match.getOpponent(player);
                    if (opponent != null) {
                        plugin.getMatchManager().endMatch(match, opponent, false);
                        String deathCause = getDeathCause(event.getEntity().getLastDamageCause());
                        event.setDeathMessage(MessageUtil.color(
                            "&c" + player.getName() + " &7ist " + deathCause + " &7im PvP-Match gestorben!"
                        ));
                    }
                }
            }, Time.ticks(1));
            
            // Prevent item/XP drops for all deaths in match
            event.getDrops().clear();
            event.setDroppedExp(0);
            
            // WICHTIG: Inventar NICHT behalten!
            // Das Arena-Equipment soll nicht beim Respawn da sein.
            // Per-World-Inventory kümmert sich um das richtige Inventar beim Weltenwechsel.
            event.setKeepInventory(false);
            
            // Equipment sofort aus dem Inventar löschen (verhindert Drop und Behalten)
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
        }
    }

    /**
     * KRITISCH: Sicherer Respawn-Handler für PvP-Match-Spieler.
     * 
     * Priorität: HIGHEST um sicherzustellen, dass wir nach anderen Plugins kommen.
     * 
     * WICHTIG: Verwendet die beim Tod gespeicherte Location aus pendingRespawnLocations,
     * da das Match möglicherweise schon beendet ist wenn der Spieler respawnt!
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // ZUERST: Prüfe ob wir eine gespeicherte Respawn-Location haben (vom Death-Event)
        Location savedLocation = pendingRespawnLocations.remove(playerId);
        
        if (savedLocation != null) {
            // Wir haben eine gespeicherte Location aus dem Death-Event
            Location safeLocation = prepareSafeLocation(savedLocation, player.getName());
            
            if (safeLocation != null) {
                String targetCoords = String.format("%.2f, %.2f, %.2f", 
                    safeLocation.getX(), safeLocation.getY(), safeLocation.getZ());
                String targetWorld = safeLocation.getWorld() != null ? safeLocation.getWorld().getName() : "NULL";
                
                plugin.getLogger().info("[Respawn] " + player.getName() + " verwendet gespeicherte Location: " + 
                    targetWorld + " @ " + targetCoords);
                
                event.setRespawnLocation(safeLocation);
                verifySingleTeleport(player, safeLocation.clone());
            }
            return;
        }
        
        // FALLBACK: Prüfe ob Spieler noch in einem aktiven Match ist
        Match match = plugin.getMatchManager().getMatchByPlayer(player);
        if (match != null) {
            Location safeLocation = determineSafeRespawnLocation(player, match);
            
            if (safeLocation != null) {
                String targetCoords = String.format("%.2f, %.2f, %.2f (Yaw: %.1f, Pitch: %.1f)", 
                    safeLocation.getX(), safeLocation.getY(), safeLocation.getZ(),
                    safeLocation.getYaw(), safeLocation.getPitch());
                String targetWorld = safeLocation.getWorld() != null ? safeLocation.getWorld().getName() : "NULL";
                
                plugin.getLogger().info("[Respawn] " + player.getName() + " (aus Match) soll zu " + 
                    targetWorld + " @ " + targetCoords + " teleportiert werden");
                
                event.setRespawnLocation(safeLocation);
                verifySingleTeleport(player, safeLocation.clone());
            }
        }
    }
    
    /**
     * Bereitet eine gespeicherte Location für den Respawn vor.
     * Prüft ob die Welt noch geladen ist und gibt einen Fallback zurück falls nötig.
     */
    private Location prepareSafeLocation(Location savedLocation, String playerName) {
        if (savedLocation == null || savedLocation.getWorld() == null) {
            plugin.getLogger().warning("[Respawn] Gespeicherte Location für " + playerName + " ist ungültig!");
            return getMainWorldSpawn();
        }
        
        String worldName = savedLocation.getWorld().getName();
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
        
        if (world == null) {
            plugin.getLogger().warning("[Respawn] Welt " + worldName + " für " + playerName + 
                " nicht mehr geladen - verwende Hauptwelt-Spawn");
            return getMainWorldSpawn();
        }
        
        // Welt existiert - sichere Location erstellen
        Location safeLocation = savedLocation.clone();
        safeLocation.setWorld(world);
        return safeLocation;
    }
    
    /**
     * EINMALIGE Verifizierung ob der Spieler am richtigen Ort respawned ist.
     * Korrigiert NUR wenn:
     * - Spieler in falscher Welt ist, ODER
     * - Spieler mehr als 50 Blöcke von der erwarteten Position entfernt ist
     * 
     * Normale Bewegung nach dem Respawn (paar Blöcke laufen) wird NICHT korrigiert!
     */
    private void verifySingleTeleport(Player player, Location expected) {
        // Kurze Verzögerung um dem Respawn-Event Zeit zu geben
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            
            Location current = player.getLocation();
            String currentWorld = current.getWorld() != null ? current.getWorld().getName() : "NULL";
            String expectedWorld = expected.getWorld() != null ? expected.getWorld().getName() : "NULL";
            
            // Prüfe ob Spieler in der FALSCHEN WELT ist
            boolean wrongWorld = !currentWorld.equals(expectedWorld);
            
            // Prüfe Distanz NUR wenn in der gleichen Welt (Toleranz: 50 Blöcke für normale Bewegung)
            double distance = wrongWorld ? Double.MAX_VALUE : current.distance(expected);
            boolean criticallyFar = distance > 50.0;
            
            String currentCoords = String.format("%.2f, %.2f, %.2f", current.getX(), current.getY(), current.getZ());
            String expectedCoords = String.format("%.2f, %.2f, %.2f", expected.getX(), expected.getY(), expected.getZ());
            
            if (wrongWorld) {
                // KRITISCH: Spieler in falscher Welt - MUSS teleportiert werden!
                plugin.getLogger().warning("[Respawn-Verify] KRITISCH: " + player.getName() + " ist in FALSCHER WELT!");
                plugin.getLogger().warning("  Erwartet: " + expectedWorld + " @ " + expectedCoords);
                plugin.getLogger().warning("  Aktuell:  " + currentWorld + " @ " + currentCoords);
                plugin.getLogger().info("[Respawn-Verify] Teleportiere " + player.getName() + " zur korrekten Welt...");
                
                // Teleportieren und nochmal prüfen
                player.teleport(expected);
                verifyFinalTeleport(player, expected);
                
            } else if (criticallyFar) {
                // Spieler ist sehr weit weg (>50 Blöcke) - wahrscheinlich falsch gespawnt
                plugin.getLogger().warning("[Respawn-Verify] " + player.getName() + " ist " + 
                    String.format("%.1f", distance) + " Blöcke von der erwarteten Position entfernt!");
                plugin.getLogger().warning("  Erwartet: " + expectedWorld + " @ " + expectedCoords);
                plugin.getLogger().warning("  Aktuell:  " + currentWorld + " @ " + currentCoords);
                plugin.getLogger().info("[Respawn-Verify] Teleportiere " + player.getName() + " zur korrekten Position...");
                
                player.teleport(expected);
                verifyFinalTeleport(player, expected);
                
            } else {
                // Alles OK - Spieler ist in richtiger Welt und nah genug
                plugin.getLogger().info("[Respawn-Verify] ✓ " + player.getName() + 
                    " korrekt in " + currentWorld + " @ " + currentCoords + 
                    " (Distanz: " + String.format("%.1f", distance) + " Blöcke - OK)");
            }
        }, 5L); // 5 Ticks = 0.25 Sekunden nach Respawn
    }
    
    /**
     * Finale Verifizierung nach Korrektur-Teleport.
     */
    private void verifyFinalTeleport(Player player, Location expected) {
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            
            Location current = player.getLocation();
            String currentWorld = current.getWorld() != null ? current.getWorld().getName() : "NULL";
            String expectedWorld = expected.getWorld() != null ? expected.getWorld().getName() : "NULL";
            
            boolean wrongWorld = !currentWorld.equals(expectedWorld);
            double distance = wrongWorld ? Double.MAX_VALUE : current.distance(expected);
            
            if (wrongWorld || distance > 50.0) {
                plugin.getLogger().severe("[Respawn-Verify] FEHLER: Korrektur-Teleport für " + player.getName() + 
                    " fehlgeschlagen! Spieler ist immer noch am falschen Ort.");
                plugin.getLogger().severe("  Erwartet: " + expectedWorld);
                plugin.getLogger().severe("  Aktuell:  " + currentWorld);
                // Letzter Versuch
                player.teleport(expected);
            } else {
                plugin.getLogger().info("[Respawn-Verify] ✓ Korrektur erfolgreich - " + player.getName() + 
                    " jetzt in " + currentWorld);
            }
        }, 3L);
    }
    
    /**
     * Prüft ob die Arena-Welt entladen ist.
     */
    private boolean isArenaWorldUnloaded(Match match) {
        if (match.getArena() == null) return true;
        String arenaWorldName = match.getArena().getArenaWorld();
        return arenaWorldName == null || org.bukkit.Bukkit.getWorld(arenaWorldName) == null;
    }
    
    /**
     * Ermittelt einen sicheren Respawn-Ort für einen PvP-Match-Spieler.
     * 
     * WICHTIG: Nach einem Tod im Match wird der Spieler IMMER zum Original-Standort teleportiert!
     * Spieler respawnen NIEMALS in der Arena - das Match ist nach einem Tod effektiv vorbei.
     * 
     * @param player Der Spieler, der respawnt
     * @param match Das Match des Spielers
     * @return Sichere Location (Original-Standort oder Fallback)
     */
    private Location determineSafeRespawnLocation(Player player, Match match) {
        java.util.Map<java.util.UUID, org.bukkit.Location> origins = match.getOriginalLocations();
        Location originalLocation = origins.get(player.getUniqueId());
        
        // Prüfe ob der Spieler ein aktiver Match-Spieler ist (nicht Spectator)
        boolean isActivePlayer = player.equals(match.getPlayer1()) || player.equals(match.getPlayer2());
        
        // KRITISCH: Aktive Match-Spieler werden IMMER zum Original-Standort teleportiert!
        // Wenn ein Spieler stirbt, ist das Match effektiv vorbei. Kein Respawn in Arena.
        // Das Per-World-Inventory Plugin kümmert sich um das richtige Inventar beim Weltenwechsel.
        if (isActivePlayer) {
            return getOriginalLocationOrFallback(player, originalLocation, "Spieler-Tod im Match");
        }
        
        // Für Spectators: Auch zum Original-Standort (Spectator sollte sowieso nicht sterben)
        if (match.getSpectators().contains(player.getUniqueId())) {
            return getOriginalLocationOrFallback(player, originalLocation, "Spectator-Tod");
        }
        
        // Fallback für alle anderen Fälle
        return getOriginalLocationOrFallback(player, originalLocation, "Unbekannter Fall");
    }
    
    /**
     * Gibt den Original-Standort zurück oder einen Fallback.
     */
    private Location getOriginalLocationOrFallback(Player player, Location originalLocation, String reason) {
        if (originalLocation != null && originalLocation.getWorld() != null) {
            org.bukkit.World originWorld = org.bukkit.Bukkit.getWorld(originalLocation.getWorld().getName());
            if (originWorld != null) {
                plugin.getLogger().info("[SafeRespawn-PvP] " + reason + " - " + 
                    player.getName() + " wird zu Original-Standort teleportiert: " + 
                    originalLocation.getWorld().getName() + " (" + 
                    String.format("%.1f, %.1f, %.1f", originalLocation.getX(), originalLocation.getY(), originalLocation.getZ()) + ")");
                Location safeOrigin = originalLocation.clone();
                safeOrigin.setWorld(originWorld);
                return safeOrigin;
            } else {
                plugin.getLogger().warning("[SafeRespawn-PvP] Original-Welt nicht geladen: " + 
                    originalLocation.getWorld().getName() + " - verwende Hauptwelt-Spawn");
            }
        } else {
            plugin.getLogger().warning("[SafeRespawn-PvP] Kein Original-Standort für " + player.getName() + 
                " gefunden - verwende Hauptwelt-Spawn");
        }
        
        // Fallback zur Hauptwelt
        return getMainWorldSpawn();
    }
    
    /**
     * Prüft, ob eine Location sicher ist.
     */
    private boolean isSafeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        
        // Prüfe ob Welt noch geladen ist
        if (org.bukkit.Bukkit.getWorld(loc.getWorld().getName()) == null) return false;
        
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
        if (org.bukkit.Bukkit.getWorld(loc.getWorld().getName()) == null) return true;
        
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
        org.bukkit.World mainWorld = mainWorldName != null ? org.bukkit.Bukkit.getWorld(mainWorldName) : null;
        
        if (mainWorld == null && !org.bukkit.Bukkit.getWorlds().isEmpty()) {
            mainWorld = org.bukkit.Bukkit.getWorlds().get(0);
        }
        
        if (mainWorld != null) {
            return mainWorld.getSpawnLocation();
        }
        
        return null;
    }
    
    private String getDeathCause(EntityDamageEvent damageEvent) {
        if (damageEvent == null) return "&7unbekannt";
        
        DamageCause cause = damageEvent.getCause();
        
        if (cause == DamageCause.FALL) {
            return "&7beim Fallen";
        } else if (cause == DamageCause.FIRE || cause == DamageCause.FIRE_TICK) {
            return "&7im Feuer";
        } else if (cause == DamageCause.LAVA) {
            return "&7in Lava";
        } else if (cause == DamageCause.DROWNING) {
            return "&7beim Ertrinken";
        } else if (cause == DamageCause.SUFFOCATION) {
            return "&7am Ersticken";
        } else if (cause == DamageCause.STARVATION) {
            return "&7am Verhungern";
        } else if (cause == DamageCause.VOID) {
            return "&7in der Void";
        } else if (cause == DamageCause.LIGHTNING) {
            return "&7durch einen Blitz";
        } else if (cause == DamageCause.BLOCK_EXPLOSION || cause == DamageCause.ENTITY_EXPLOSION) {
            return "&7in einer Explosion";
        } else if (cause == DamageCause.MAGIC) {
            return "&7durch Magie";
        } else if (cause == DamageCause.WITHER) {
            return "&7am Verwelken";
        } else if (cause == DamageCause.CONTACT) {
            return "&7an einem Kaktus";
        } else {
            return "&7durch Umweltschaden";
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Match match = plugin.getMatchManager().getMatchByPlayer(player);
        
        if (match != null) {
            // Check if player is spectator
            if (match.getSpectators().contains(player.getUniqueId())) {
                plugin.getMatchManager().removeSpectator(match, player);
                match.broadcast("&e" + player.getName() + " &7hat das Zuschauen beendet.");
                return;
            }

            // If the quitting player initiated a draw vote, cancel it immediately
            try {
                match.cancelDrawVoteIfInitiator(player.getUniqueId());
            } catch (Exception ignored) {}

            // Player disconnected during match setup or fighting
            if (match.getState() == MatchState.SETUP || match.getState() == MatchState.STARTING) {
                // Cancel any active confirmations tied to this player before ending
                try {
                    match.cancelWagerConfirmation(player);
                } catch (Exception ignored) {}
                try {
                    match.cancelArenaConfirmation(player);
                } catch (Exception ignored) {}
                try {
                    match.cancelEquipmentConfirmation(player);
                } catch (Exception ignored) {}
                try {
                    if (match.isConfirmationActive()) {
                        match.stopConfirmationCountdown();
                    }
                } catch (Exception ignored) {}
                // Cancel match setup
                Player opponent = match.getOpponent(player);
                if (opponent != null) {
                    MessageUtil.sendMessage(opponent, 
                        "&e" + player.getName() + " &chat die Verbindung getrennt! Match abgebrochen.");
                }
                plugin.getMatchManager().endMatch(match, null, true);
            } else if (match.getState() == MatchState.FIGHTING) {
                // Player disconnected during fight - opponent wins
                Player opponent = match.getOpponent(player);
                if (opponent != null) {
                    plugin.getMatchManager().endMatch(match, opponent, false);
                    MessageUtil.sendMessage(opponent, 
                        "&e" + player.getName() + " &chat die Verbindung getrennt! Du gewinnst das Match!");
                }
            }
        }
    }
}