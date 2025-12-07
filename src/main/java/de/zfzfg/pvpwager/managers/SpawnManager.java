package de.zfzfg.pvpwager.managers;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Arena;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SpawnManager {
    
    private final Random random;
    private final EventPlugin plugin;
    
    public SpawnManager(EventPlugin plugin) {
        this.random = new Random();
        this.plugin = plugin;
    }
    
    /**
     * Teleportiert zwei Spieler basierend auf dem Spawn-Typ der Arena.
     * Wählt die jeweils passende Strategie (fixed, random, multiple, command)
     * und setzt bei Fehlern einen sicheren Fallback zum Welt-Spawn.
     *
     * @param player1 Erster Spieler
     * @param player2 Zweiter Spieler
     * @param arena   Arena-Konfiguration inkl. Spawn-Einstellungen
     * @param world   Ziel-Welt, in der gespawnt wird (muss geladen sein)
     */
    public void teleportPlayers(Player player1, Player player2, Arena arena, World world) {
        try {
            switch (arena.getSpawnType()) {
                case FIXED_SPAWNS:
                    teleportFixedSpawns(player1, player2, arena, world);
                    break;

                case RANDOM_RADIUS:
                    teleportRandomRadius(player1, player2, arena, world);
                    break;

                case RANDOM_AREA:
                    teleportRandomArea(player1, player2, arena, world);
                    break;

                case RANDOM_CUBE:
                    teleportRandomCube(player1, player2, arena, world);
                    break;

                case MULTIPLE_SPAWNS:
                    teleportMultipleSpawns(player1, player2, arena, world);
                    break;

                case COMMAND:
                    teleportViaCommand(player1, player2, arena, world);
                    break;

                default:
                    // Fallback zu Fixed Spawns
                    teleportFixedSpawns(player1, player2, arena, world);
                    break;
            }
        } catch (Exception ex) {
            plugin.getLogger().severe("Teleport-Fehler: " + ex.getMessage());
            // Fallback: beide Spieler zum Welt-Spawn
            try {
                Location spawn = world.getSpawnLocation();
                if (player1 != null && player1.isOnline()) player1.teleport(spawn);
                if (player2 != null && player2.isOnline()) player2.teleport(spawn);
            } catch (Exception ignore) { }
        }
    }
    
    private void teleportFixedSpawns(Player player1, Player player2, Arena arena, World world) {
        List<Location> spawns = arena.getSpawnConfig().getFixedSpawns();
        if (spawns != null && spawns.size() >= 2) {
            Location spawn1 = java.util.Optional.ofNullable(spawns.get(0))
                .map(Location::clone)
                .orElseGet(world::getSpawnLocation);
            Location spawn2 = java.util.Optional.ofNullable(spawns.get(1))
                .map(Location::clone)
                .orElseGet(world::getSpawnLocation);
            spawn1.setWorld(world);
            spawn2.setWorld(world);
            
            safeTeleport(player1, spawn1);
            safeTeleport(player2, spawn2);
        }
    }
    
    private void teleportRandomRadius(Player player1, Player player2, Arena arena, World world) {
        Arena.RandomRadiusConfig config = arena.getSpawnConfig().getRandomRadius();
        if (config == null) return;
        
        List<Location> usedLocations = new ArrayList<>();
        
        // Player 1
        Location loc1 = findRandomRadiusLocation(world, config, usedLocations);
        if (loc1 != null) {
            usedLocations.add(loc1);
            safeTeleport(player1, loc1);
        }
        
        // Player 2
        Location loc2 = findRandomRadiusLocation(world, config, usedLocations);
        if (loc2 != null) {
            safeTeleport(player2, loc2);
        }
    }
    
    private void teleportRandomArea(Player player1, Player player2, Arena arena, World world) {
        Arena.RandomAreaConfig config = arena.getSpawnConfig().getRandomArea();
        if (config == null) return;
        
        List<Location> usedLocations = new ArrayList<>();
        
        // Player 1
        Location loc1 = findRandomAreaLocation(world, config, usedLocations);
        if (loc1 != null) {
            usedLocations.add(loc1);
            safeTeleport(player1, loc1);
        }
        
        // Player 2
        Location loc2 = findRandomAreaLocation(world, config, usedLocations);
        if (loc2 != null) {
            safeTeleport(player2, loc2);
        }
    }
    
    private void teleportRandomCube(Player player1, Player player2, Arena arena, World world) {
        Arena.RandomCubeConfig config = arena.getSpawnConfig().getRandomCube();
        if (config == null) return;
        
        List<Location> usedLocations = new ArrayList<>();
        
        // Player 1
        Location loc1 = findRandomCubeLocation(world, config, usedLocations);
        if (loc1 != null) {
            usedLocations.add(loc1);
            safeTeleport(player1, loc1);
        }
        
        // Player 2
        Location loc2 = findRandomCubeLocation(world, config, usedLocations);
        if (loc2 != null) {
            safeTeleport(player2, loc2);
        }
    }
    
    private void teleportMultipleSpawns(Player player1, Player player2, Arena arena, World world) {
        List<Location> spawns = arena.getSpawnConfig().getMultipleSpawns();
        if (spawns == null || spawns.isEmpty()) return;
        
        // Zufällige Auswahl von 2 verschiedenen Spawns
        List<Location> shuffled = new ArrayList<>(spawns);
        java.util.Collections.shuffle(shuffled);
        
        if (shuffled.size() >= 2) {
            Location spawn1 = shuffled.get(0).clone();
            Location spawn2 = shuffled.get(1).clone();
            spawn1.setWorld(world);
            spawn2.setWorld(world);
            
            safeTeleport(player1, spawn1);
            safeTeleport(player2, spawn2);
        } else if (shuffled.size() == 1) {
            // Nur ein Spawn verfügbar - beide Spieler an gleichen Ort
            Location spawn = shuffled.get(0).clone();
            spawn.setWorld(world);
            
            safeTeleport(player1, spawn);
            safeTeleport(player2, spawn);
        }
    }
    
    private final java.util.Set<java.util.UUID> commandTeleportInProgress = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private void teleportViaCommand(Player player1, Player player2, Arena arena, World world) {
        Arena.CommandConfig config = arena.getSpawnConfig().getCommandConfig();
        if (config == null || config.getCommand() == null || config.getCommand().isEmpty()) {
            return;
        }

        // Teleportiere Player 1
        Location currentLoc1 = player1.getLocation();
        String cmd1 = config.formatCommand(player1.getName(), 1, currentLoc1);
        // Run command through plugin scheduler
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (!commandTeleportInProgress.add(player1.getUniqueId())) {
                    plugin.getLogger().warning("Teleport command already in progress for " + player1.getName());
                    return;
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd1);
            } catch (Exception ex) {
                plugin.getLogger().severe("Teleport-Command Fehler für " + player1.getName() + ": " + ex.getMessage());
                // Fallback: Welt-Spawn
                safeTeleport(player1, world.getSpawnLocation());
            } finally {
                commandTeleportInProgress.remove(player1.getUniqueId());
            }
        });
        
        // Teleportiere Player 2 (mit leichter Verzögerung)
        Location currentLoc2 = player2.getLocation();
        String cmd2 = config.formatCommand(player2.getName(), 2, currentLoc2);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (!commandTeleportInProgress.add(player2.getUniqueId())) {
                    plugin.getLogger().warning("Teleport command already in progress for " + player2.getName());
                    return;
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd2);
            } catch (Exception ex) {
                plugin.getLogger().severe("Teleport-Command Fehler für " + player2.getName() + ": " + ex.getMessage());
                safeTeleport(player2, world.getSpawnLocation());
            } finally {
                commandTeleportInProgress.remove(player2.getUniqueId());
            }
        }, de.zfzfg.core.util.Time.ticks(5));

    }

    /**
     * Führt einen robusten Teleport durch und nutzt bei Fehlern den Welt-Spawn
     * als Fallback. Diese Methode kapselt die gemeinsame Teleport-Fehlerbehandlung.
     *
     * @param player   Spieler, der teleportiert werden soll
     * @param location Ziel-Location in der Ziel-Welt
     */
    private void safeTeleport(Player player, Location location) {
        if (player == null || location == null) return;
        try {
            player.teleport(location);
        } catch (Exception ex) {
            plugin.getLogger().severe("Teleport fehlgeschlagen für " + player.getName() + ": " + ex.getMessage());
            // Fallback auf Welt-Spawn
            try {
                World w = location.getWorld();
                if (w != null) player.teleport(w.getSpawnLocation());
            } catch (Exception ignore) {}
        }
    }
    
    // === HILFSMETHODEN ===
    
    private Location findRandomRadiusLocation(World world, Arena.RandomRadiusConfig config, List<Location> usedLocations) {
        int maxAttempts = 100;
        
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * config.getRadius();
            
            double x = config.getCenterX() + distance * Math.cos(angle);
            double z = config.getCenterZ() + distance * Math.sin(angle);
            double y = world.getHighestBlockYAt((int) x, (int) z) + 1;
            
            Location candidate = new Location(world, x, y, z);
            
            // Prüfe Mindestabstand
            boolean validLocation = true;
            for (Location usedLoc : usedLocations) {
                if (candidate.distance(usedLoc) < config.getMinDistance()) {
                    validLocation = false;
                    break;
                }
            }
            
            if (validLocation) {
                return candidate;
            }
        }
        
        // Fallback: Gebe irgendeine Position zurück
        double x = config.getCenterX();
        double z = config.getCenterZ();
        double y = world.getHighestBlockYAt((int) x, (int) z) + 1;
        return new Location(world, x, y, z);
    }
    
    private Location findRandomAreaLocation(World world, Arena.RandomAreaConfig config, List<Location> usedLocations) {
        int maxAttempts = 100;
        
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double x = config.getMinX() + random.nextDouble() * (config.getMaxX() - config.getMinX());
            double z = config.getMinZ() + random.nextDouble() * (config.getMaxZ() - config.getMinZ());
            double y = world.getHighestBlockYAt((int) x, (int) z) + 1;
            
            Location candidate = new Location(world, x, y, z);
            
            // Prüfe Mindestabstand
            boolean validLocation = true;
            for (Location usedLoc : usedLocations) {
                if (candidate.distance(usedLoc) < config.getMinDistance()) {
                    validLocation = false;
                    break;
                }
            }
            
            if (validLocation) {
                return candidate;
            }
        }
        
        // Fallback
        double x = (config.getMinX() + config.getMaxX()) / 2;
        double z = (config.getMinZ() + config.getMaxZ()) / 2;
        double y = world.getHighestBlockYAt((int) x, (int) z) + 1;
        return new Location(world, x, y, z);
    }
    
    private Location findRandomCubeLocation(World world, Arena.RandomCubeConfig config, List<Location> usedLocations) {
        int maxAttempts = 100;
        
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double x = config.getMinX() + random.nextDouble() * (config.getMaxX() - config.getMinX());
            double y = config.getMinY() + random.nextDouble() * (config.getMaxY() - config.getMinY());
            double z = config.getMinZ() + random.nextDouble() * (config.getMaxZ() - config.getMinZ());
            
            Location candidate = new Location(world, x, y, z);
            
            // Prüfe Mindestabstand
            boolean validLocation = true;
            for (Location usedLoc : usedLocations) {
                if (candidate.distance(usedLoc) < config.getMinDistance()) {
                    validLocation = false;
                    break;
                }
            }
            
            if (validLocation) {
                return candidate;
            }
        }
        
        // Fallback
        double x = (config.getMinX() + config.getMaxX()) / 2;
        double y = config.getMinY();
        double z = (config.getMinZ() + config.getMaxZ()) / 2;
        return new Location(world, x, y, z);
    }
}