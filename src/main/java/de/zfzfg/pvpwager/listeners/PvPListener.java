package de.zfzfg.pvpwager.listeners;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.models.MatchState;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PvPListener implements Listener {
    private static final Set<String> MISSING_KEYS_LOGGED = ConcurrentHashMap.newKeySet();
    
    private final EventPlugin plugin;
    
    // KRITISCH: Speichert die Respawn-Location für Spieler die im Match gestorben sind.
    // Das Match könnte bereits beendet sein wenn der Spieler respawnt, daher müssen
    // wir die Location VORHER speichern (beim Tod).
    private final Map<UUID, Location> pendingRespawnLocations = new ConcurrentHashMap<>();
    
    public PvPListener(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    private void warnMissingKey(String path) {
        if (MISSING_KEYS_LOGGED.add(path)) {
            plugin.getLogger().warning("Missing message key: " + path + " (check messages_*.yml)"); // i18n-ignore: i18n system warning
        }
    }

    private String getMsg(String key) {
        if (key == null || key.isEmpty()) return "";
        String val = null;
        if (key.startsWith("messages.")) {
            val = plugin.getCoreConfigManager().getMessages().getString(key, null);
        }
        if (val == null) {
            val = plugin.getCoreConfigManager().getMessages().getString("messages.pvp-listener." + key, null);
        }
        if (val == null) {
            val = plugin.getCoreConfigManager().getMessages().getString(key, null);
        }
        if (val != null) return val;
        warnMissingKey("messages.pvp-listener." + key);
        return "&c[missing: " + key + "]";
    }
    
    private String getMsg(String key, String p1, String v1, String p2, String v2) {
        return getMsg(key, new String[]{p1, v1, p2, v2});
    }
    
    private String getMsg(String key, String p1, String v1) {
        return getMsg(key, new String[]{p1, v1});
    }

    private String getMsg(String key, String... replacements) {
        String msg = getMsg(key);
        if (replacements != null && replacements.length > 0) {
            for (int i = 0; i < replacements.length - 1; i += 2) {
                String raw = replacements[i] != null ? replacements[i].replaceAll("^[{%]+|[%}]+$", "") : "";
                String val = replacements[i + 1] != null ? replacements[i + 1] : "";
                if (!raw.isEmpty()) {
                    msg = msg.replace("{" + raw + "}", val)
                             .replace("%" + raw + "%", val);
                }
            }
        }
        return msg;
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
                plugin.getLogger().info(plugin.getConsoleMsg("pvp-death-saved", "player", player.getName(), "location", String.format("%s @ %.2f, %.2f, %.2f", (originalLocation.getWorld() != null ? originalLocation.getWorld().getName() : "NULL"), originalLocation.getX(), originalLocation.getY(), originalLocation.getZ())));
            } else {
                plugin.getLogger().warning(plugin.getConsoleMsg("pvp-death-no-location", "player", player.getName()));
            }
            
            Player killer = player.getKiller();
            // Suppress default death message synchronously; we will broadcast our own later
            event.setDeathMessage(null);

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
                    match.broadcast(getMsg("draw-double-death"));
                    match.broadcast("&a&l━━━━━━━━━━━━━━━━━━━━━━━");
                    match.broadcast("");
                    match.broadcast(getMsg("both-died-simultaneously"));
                    plugin.getMatchManager().endMatch(match, null, true);
                    return;
                }

                // Standard outcome
                if (killer != null && (killer.equals(match.getPlayer1()) || killer.equals(match.getPlayer2()))) {
                    plugin.getMatchManager().endMatch(match, killer, false);
                    match.broadcast(MessageUtil.color(
                        getMsg("defeated-by", "player", player.getName(), "killer", killer.getName())
                    ));
                } else {
                    Player opponent = match.getOpponent(player);
                    if (opponent != null) {
                        plugin.getMatchManager().endMatch(match, opponent, false);
                        String deathCause = getDeathCause(event.getEntity().getLastDamageCause());
                        match.broadcast(MessageUtil.color(
                            getMsg("died-in-match", "player", player.getName(), "cause", deathCause)
                        ));
                    }
                }
            }, Time.ticks(1));
            
            // Prevent item/XP drops for all deaths in match
            event.getDrops().clear();
            event.setDroppedExp(0);
            
            // WICHTIG: Inventar NICHT behalten!
            // Das Arena-Equipment soll nicht beim Respawn da sein. Das Survival-Inventar
            // kommt beim Respawn aus dem Pre-Match-Backup zurueck (onPlayerRespawn); im
            // Legacy-Betrieb uebernimmt das weiterhin Multiverse-Inventories.
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
    /**
     * Spielt das Pre-Match-Backup nach einem Tod zurueck.
     *
     * <p>Ohne das steht der Spieler nach dem Respawn mit leerem Inventar da, sobald kein
     * Multiverse-Inventories den Weltwechsel abfaengt. Der Aufruf ist idempotent: laeuft
     * gleichzeitig schon ein Restore aus {@code endMatch}, gewinnt genau einer von beiden.</p>
     */
    private void restoreAfterRespawn(UUID playerId) {
        de.zfzfg.core.inventory.InventorySessionManager sessions = plugin.getInventorySessions();
        if (sessions == null || !sessions.isManaged()
                || !plugin.getInventoryConfig().restoreOnRespawn()) {
            return;
        }
        de.zfzfg.core.inventory.guard.GuardEntry entry = plugin.getInventoryGuard().get(playerId);
        if (entry == null
                || entry.context() != de.zfzfg.core.inventory.guard.GuardContext.PVP_MATCH) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> sessions.finish(playerId, outcome -> {
            if (!outcome.isSuccess()) {
                plugin.getLogger().warning(plugin.getConsoleMsg("inventory-respawn-restore-failed",
                        "player", playerId.toString(), "reason", outcome.name()));
            }
        }), Time.ticks(15));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Survival-Inventar zurueckspielen, sobald der Respawn durch ist. Der Respawn selbst
        // ist zum Zeitpunkt dieses Events noch nicht abgeschlossen - deshalb erst danach,
        // und mit etwas Abstand zum Teleport in die Ursprungswelt.
        restoreAfterRespawn(playerId);

        // ZUERST: Prüfe ob wir eine gespeicherte Respawn-Location haben (vom Death-Event)
        Location savedLocation = pendingRespawnLocations.remove(playerId);
        
        if (savedLocation != null) {
            // Wir haben eine gespeicherte Location aus dem Death-Event
            Location safeLocation = prepareSafeLocation(savedLocation, player.getName());
            
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
        
        // FALLBACK: Prüfe ob Spieler noch in einem aktiven Match ist
        Match match = plugin.getMatchManager().getMatchByPlayer(player);
        if (match != null) {
            Location safeLocation = determineSafeRespawnLocation(player, match);
            
            if (safeLocation != null) {
                String targetCoords = String.format("%.2f, %.2f, %.2f (Yaw: %.1f, Pitch: %.1f)",  // i18n-ignore: nur Log-Diagnose (SafeRespawn)
                    safeLocation.getX(), safeLocation.getY(), safeLocation.getZ(),
                    safeLocation.getYaw(), safeLocation.getPitch());
                String targetWorld = safeLocation.getWorld() != null ? safeLocation.getWorld().getName() : "NULL";
                
                plugin.getLogger().info(plugin.getConsoleMsg("safe-teleport-player", "player", player.getName(), "world", targetWorld, "coords", targetCoords));
                
                event.setRespawnLocation(safeLocation);
                verifySingleTeleport(player, safeLocation.clone());
            }
        }
    }
    
    // === SICHERES RESPAWN SYSTEM ===
    
    /**
     * Bereitet eine gespeicherte Location für den Respawn vor.
     * Prüft ob die Welt noch geladen ist und gibt einen Fallback zurück falls nötig.
     */
    private Location prepareSafeLocation(Location savedLocation, String playerName) {
        if (savedLocation == null || savedLocation.getWorld() == null) {
            plugin.getLogger().warning(plugin.getConsoleMsg("safe-respawn-invalid-loc", "player", playerName));
            return getMainWorldSpawn();
        }
        
        String worldName = savedLocation.getWorld().getName();
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
        
        if (world == null) {
            plugin.getLogger().warning(plugin.getConsoleMsg("safe-respawn-unloaded-world", "world", worldName, "player", playerName));
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
                plugin.getLogger().warning(plugin.getConsoleMsg("safe-respawn-wrong-world", "player", player.getName()));
                
                // Teleportieren und nochmal prüfen
                player.teleport(expected);
                verifyFinalTeleport(player, expected);
                
            } else if (criticallyFar) {
                // Spieler ist sehr weit weg (>50 Blöcke) - wahrscheinlich falsch gespawnt
                plugin.getLogger().warning(plugin.getConsoleMsg("safe-respawn-distance-warn", "player", player.getName(), "distance", String.format("%.1f", distance)));
                
                player.teleport(expected);
                verifyFinalTeleport(player, expected);
                
            } else {
                // Alles OK - Spieler ist in richtiger Welt und nah genug
                plugin.getLogger().info(plugin.getConsoleMsg("safe-respawn-correct", "player", player.getName(), "world", currentWorld, "coords", currentCoords, "distance", String.format("%.1f", distance)));
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
                plugin.getLogger().severe(plugin.getConsoleMsg("safe-respawn-correction-failed", "player", player.getName()));
                // Letzter Versuch
                player.teleport(expected);
            } else {
                plugin.getLogger().info(plugin.getConsoleMsg("safe-respawn-correction-success", "player", player.getName(), "world", currentWorld));
            }
        }, 3L);
    }
    
    /**
     * Prüft ob die Arena-Welt entladen ist.
     */
    private boolean isArenaWorldUnloaded(Match match) {
        if (match == null || match.getArena() == null) return false;
        
        String arenaWorldName = match.getArena().getArenaWorld();
        if (arenaWorldName == null || arenaWorldName.isEmpty()) return false;
        
        return org.bukkit.Bukkit.getWorld(arenaWorldName) == null;
    }
    
    /**
     * Holt die Original-Location oder gibt den Fallback zurück.
     */
    private Location getOriginalLocationOrFallback(Player player, Location originalLocation, String reason) {
        if (originalLocation != null && originalLocation.getWorld() != null) {
            org.bukkit.World originWorld = org.bukkit.Bukkit.getWorld(originalLocation.getWorld().getName());
            if (originWorld != null) {
                plugin.getLogger().info(plugin.getConsoleMsg("safe-teleport-player", "player", player.getName(), "world", originalLocation.getWorld().getName(), "coords", String.format("%.1f, %.1f, %.1f", originalLocation.getX(), originalLocation.getY(), originalLocation.getZ())));
                Location safeOrigin = originalLocation.clone();
                safeOrigin.setWorld(originWorld);
                return safeOrigin;
            } else {
                plugin.getLogger().warning(plugin.getConsoleMsg("pvp-respawn-unloaded", "world", originalLocation.getWorld().getName()));
            }
        } else {
            plugin.getLogger().warning(plugin.getConsoleMsg("pvp-respawn-no-location", "player", player.getName()));
        }
        
        // Fallback zur Hauptwelt
        return getMainWorldSpawn();
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
            return getOriginalLocationOrFallback(player, originalLocation, "player death in match");  // i18n-ignore: debug log trace (SafeRespawn)
        }
        
        // Für Spectators: Auch zum Original-Standort (Spectator sollte sowieso nicht sterben)
        if (match.getSpectators().contains(player.getUniqueId())) {
            return getOriginalLocationOrFallback(player, originalLocation, "spectator death");  // i18n-ignore: debug log trace (SafeRespawn)
        }
        
        // Fallback für alle anderen Fälle
        return getOriginalLocationOrFallback(player, originalLocation, "unknown fallback");  // i18n-ignore: debug log trace (SafeRespawn)
    }
    
    /**
     * Spawn der Hauptwelt, ersatzweise der ersten geladenen Welt.
     *
     * <p>Delegiert an den gemeinsamen {@link de.zfzfg.core.location.SafeLocationResolver};
     * hier stand vorher eine eigene Kopie, ebenso wie zwei nie aufgerufene
     * Location-Pruefungen.</p>
     */
    private Location getMainWorldSpawn() {
        return plugin.getSafeLocations().fallbackSpawn();
    }

    private String getDeathCause(EntityDamageEvent damageEvent) {
        if (damageEvent == null) return MessageUtil.color(getMsg("cause.unknown"));
        
        DamageCause cause = damageEvent.getCause();
        
        if (cause == DamageCause.FALL) {
            return MessageUtil.color(getMsg("cause.fall"));
        } else if (cause == DamageCause.FIRE || cause == DamageCause.FIRE_TICK) {
            return MessageUtil.color(getMsg("cause.fire"));
        } else if (cause == DamageCause.LAVA) {
            return MessageUtil.color(getMsg("cause.lava"));
        } else if (cause == DamageCause.DROWNING) {
            return MessageUtil.color(getMsg("cause.drowning"));
        } else if (cause == DamageCause.SUFFOCATION) {
            return MessageUtil.color(getMsg("cause.suffocation"));
        } else if (cause == DamageCause.STARVATION) {
            return MessageUtil.color(getMsg("cause.starvation"));
        } else if (cause == DamageCause.VOID) {
            return MessageUtil.color(getMsg("cause.void"));
        } else if (cause == DamageCause.LIGHTNING) {
            return MessageUtil.color(getMsg("cause.lightning"));
        } else if (cause == DamageCause.BLOCK_EXPLOSION || cause == DamageCause.ENTITY_EXPLOSION) {
            return MessageUtil.color(getMsg("cause.explosion"));
        } else if (cause == DamageCause.MAGIC) {
            return MessageUtil.color(getMsg("cause.magic"));
        } else if (cause == DamageCause.WITHER) {
            return MessageUtil.color(getMsg("cause.wither"));
        } else if (cause == DamageCause.CONTACT) {
            return MessageUtil.color(getMsg("cause.contact"));
        } else {
            return MessageUtil.color(getMsg("cause.unknown"));
        }
    }
    
    /**
     * Reiht die Wiederherstellung eines Aussteigers fuer seinen naechsten Join ein.
     *
     * <p>Persistent: der Eintrag ueberlebt einen Serverneustart. Damit kostet ein Ragequit
     * mitten im Kampf hoechstens einen Login und nicht mehr das ganze Survival-Inventar.</p>
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
                || entry.context() != de.zfzfg.core.inventory.guard.GuardContext.PVP_MATCH) {
            return;
        }
        sessions.queueForJoin(player.getUniqueId());
        plugin.getLogger().info(plugin.getConsoleMsg("inventory-quit-queued", "player", player.getName()));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Memory leak fix: remove any pending teleport verification cache entry
        plugin.getMatchManager().clearTeleportVerification(player.getUniqueId());

        Match match = plugin.getMatchManager().getMatchByPlayer(player);
        
        if (match != null) {
            // Check if player is spectator
            if (match.getSpectators().contains(player.getUniqueId())) {
                plugin.getMatchManager().removeSpectator(match, player);
                match.broadcast("&e" + player.getName() + " " + getMsg("stopped-spectating"));
                return;
            }

            // If the quitting player initiated a draw vote, cancel it immediately
            try {
                match.cancelDrawVoteIfInitiator(player.getUniqueId());
            } catch (Exception ignored) {}

            // Inventar des Aussteigers fuer den naechsten Join einreihen, BEVOR das Match
            // beendet wird: endMatch versucht sonst einen Restore auf einen Spieler, der
            // bereits offline ist, und beansprucht damit die Sitzung fuer sich.
            queueRestoreForQuit(player);

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
                        getMsg("disconnected-cancelled", "player", player.getName()));
                }
                plugin.getMatchManager().endMatch(match, null, true);
            } else if (match.getState() == MatchState.FIGHTING) {
                // Player disconnected during fight - opponent wins
                Player opponent = match.getOpponent(player);
                if (opponent != null) {
                    plugin.getMatchManager().endMatch(match, opponent, false);
                    MessageUtil.sendMessage(opponent, 
                        getMsg("disconnected-win", "player", player.getName()));
                }
            }
        }
    }
}