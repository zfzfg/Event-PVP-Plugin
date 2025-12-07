package de.zfzfg.pvpwager.managers;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Arena;
import de.zfzfg.pvpwager.models.Boundaries;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.utils.MultiverseHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public class ArenaManager {
    
    private final EventPlugin plugin;
    private final MultiverseHelper multiverseHelper;
    private final Map<String, Arena> arenas = new HashMap<>();
    
    public ArenaManager(EventPlugin plugin) {
        this.plugin = plugin;
        this.multiverseHelper = new MultiverseHelper(plugin);
        loadArenas();
    }
    
    public void loadArenas() {
        arenas.clear();
        FileConfiguration arenaConfig = plugin.getPvpConfigManager().getArenaConfig();

        // Neuer Modus: zentrale worlds.yml mit Flags (pvpwager-world-enable, pvpwager-spawn)
        ConfigurationSection worldsSection = arenaConfig.getConfigurationSection("worlds");
        if (worldsSection != null) {
            for (String worldId : worldsSection.getKeys(false)) {
                try {
                    ConfigurationSection worldSection = worldsSection.getConfigurationSection(worldId);
                    if (worldSection == null) continue;

                    boolean pvpEnabled = worldSection.getBoolean("pvpwager-world-enable", false);
                    if (!pvpEnabled) {
                        plugin.getLogger().info("World '" + worldId + "' not enabled for PvPWager, skipping...");
                        continue;
                    }

                    String displayName = worldSection.getString("display-name", worldId);

                    // Weltname: nutze Schlüssel (gleich für Events und PvP)
                    String arenaWorld = worldId;
                    boolean regenerateWorld = worldSection.getBoolean("regenerate-world", false);
                    String cloneSourceWorld = worldSection.getString("clone-source-world", null);

                    // Spectator-Spawn (unter pvpwager-spawn.spawns.spectator oder worldSection.spawns.spectator)
                    Location spectatorSpawn = null;
                    ConfigurationSection pvpSpawnRoot = worldSection.getConfigurationSection("pvpwager-spawn");
                    if (pvpSpawnRoot != null) {
                        ConfigurationSection spectatorSection = pvpSpawnRoot.getConfigurationSection("spawns.spectator");
                        if (spectatorSection == null) spectatorSection = pvpSpawnRoot.getConfigurationSection("spectator");
                        spectatorSpawn = loadLocation(spectatorSection, arenaWorld);
                    } else {
                        ConfigurationSection spectatorSection = worldSection.getConfigurationSection("spawns.spectator");
                        spectatorSpawn = loadLocation(spectatorSection, arenaWorld);
                    }

                    // Boundaries optional
                    Boundaries boundaries = null;
                    if (worldSection.contains("boundaries") && worldSection.getBoolean("boundaries.enabled", false)) {
                        ConfigurationSection boundariesSection = worldSection.getConfigurationSection("boundaries");
                        double minX = boundariesSection.getDouble("min-x");
                        double maxX = boundariesSection.getDouble("max-x");
                        double minY = boundariesSection.getDouble("min-y");
                        double maxY = boundariesSection.getDouble("max-y");
                        double minZ = boundariesSection.getDouble("min-z");
                        double maxZ = boundariesSection.getDouble("max-z");
                        World world = Bukkit.getWorld(arenaWorld);
                        if (world != null) {
                            Location minLoc = new Location(world, minX, minY, minZ);
                            Location maxLoc = new Location(world, maxX, maxY, maxZ);
                            boundaries = new Boundaries(minLoc, maxLoc);
                        }
                    }

                    // Spawn-Type aus pvpwager-spawn
                    Arena.SpawnType spawnType = Arena.SpawnType.FIXED_SPAWNS;
                    Arena.SpawnConfig spawnConfig = new Arena.SpawnConfig();

                    ConfigurationSection spawnSettings = (pvpSpawnRoot != null)
                            ? pvpSpawnRoot
                            : worldSection.getConfigurationSection("spawn-settings");

                    if (spawnSettings != null && spawnSettings.contains("spawn-type")) {
                        String spawnTypeStr = spawnSettings.getString("spawn-type", "FIXED_SPAWNS");
                        spawnType = Arena.SpawnType.valueOf(spawnTypeStr);

                        switch (spawnType) {
                            case FIXED_SPAWNS: {
                                List<Location> fixedSpawns = new ArrayList<>();
                                ConfigurationSection spawnsSection = (pvpSpawnRoot != null)
                                        ? pvpSpawnRoot.getConfigurationSection("spawns")
                                        : worldSection.getConfigurationSection("spawns");
                                if (spawnsSection != null) {
                                    ConfigurationSection player1Section = spawnsSection.getConfigurationSection("player1");
                                    ConfigurationSection player2Section = spawnsSection.getConfigurationSection("player2");
                                    if (player1Section != null) fixedSpawns.add(loadLocation(player1Section, arenaWorld));
                                    if (player2Section != null) fixedSpawns.add(loadLocation(player2Section, arenaWorld));
                                }
                                spawnConfig.setFixedSpawns(fixedSpawns);
                                break;
                            }
                            case RANDOM_RADIUS: {
                                ConfigurationSection radiusSection = spawnSettings.getConfigurationSection("random-radius");
                                if (radiusSection != null) {
                                    Arena.RandomRadiusConfig radiusConfig = new Arena.RandomRadiusConfig(
                                            radiusSection.getDouble("center-x", 0),
                                            radiusSection.getDouble("center-z", 0),
                                            radiusSection.getDouble("radius", 100),
                                            radiusSection.getDouble("min-distance", 10)
                                    );
                                    spawnConfig.setRandomRadius(radiusConfig);
                                }
                                break;
                            }
                            case RANDOM_AREA: {
                                ConfigurationSection areaSection = spawnSettings.getConfigurationSection("random-area");
                                if (areaSection != null) {
                                    ConfigurationSection point1 = areaSection.getConfigurationSection("point1");
                                    ConfigurationSection point2 = areaSection.getConfigurationSection("point2");
                                    Arena.RandomAreaConfig areaConfig = new Arena.RandomAreaConfig(
                                            point1.getDouble("x", 0),
                                            point1.getDouble("z", 0),
                                            point2.getDouble("x", 100),
                                            point2.getDouble("z", 100),
                                            areaSection.getDouble("min-distance", 10)
                                    );
                                    spawnConfig.setRandomArea(areaConfig);
                                }
                                break;
                            }
                            case RANDOM_CUBE: {
                                ConfigurationSection cubeSection = spawnSettings.getConfigurationSection("random-cube");
                                if (cubeSection != null) {
                                    ConfigurationSection point1 = cubeSection.getConfigurationSection("point1");
                                    ConfigurationSection point2 = cubeSection.getConfigurationSection("point2");
                                    Arena.RandomCubeConfig cubeConfig = new Arena.RandomCubeConfig(
                                            point1.getDouble("x", 0),
                                            point1.getDouble("y", 50),
                                            point1.getDouble("z", 0),
                                            point2.getDouble("x", 100),
                                            point2.getDouble("y", 150),
                                            point2.getDouble("z", 100),
                                            cubeSection.getDouble("min-distance", 10)
                                    );
                                    spawnConfig.setRandomCube(cubeConfig);
                                }
                                break;
                            }
                            case MULTIPLE_SPAWNS: {
                                List<Location> multipleSpawns = new ArrayList<>();
                                ConfigurationSection spawnsSection = (pvpSpawnRoot != null)
                                        ? pvpSpawnRoot.getConfigurationSection("spawns")
                                        : worldSection.getConfigurationSection("spawns");
                                if (spawnsSection != null) {
                                    for (String key : spawnsSection.getKeys(false)) {
                                        if (!key.equals("spectator")) {
                                            ConfigurationSection spawnSection = spawnsSection.getConfigurationSection(key);
                                            multipleSpawns.add(loadLocation(spawnSection, arenaWorld));
                                        }
                                    }
                                }
                                spawnConfig.setMultipleSpawns(multipleSpawns);
                                break;
                            }
                            case COMMAND: {
                                ConfigurationSection cmdSection = spawnSettings.getConfigurationSection("command");
                                if (cmdSection != null) {
                                    String command = cmdSection.getString("command", "");
                                    Map<String, String> placeholders = new HashMap<>();
                                    ConfigurationSection placeholderSection = cmdSection.getConfigurationSection("placeholders");
                                    if (placeholderSection != null) {
                                        for (String key : placeholderSection.getKeys(false)) {
                                            placeholders.put(key, placeholderSection.getString(key));
                                        }
                                    }
                                    Arena.CommandConfig commandConfig = new Arena.CommandConfig(command, placeholders);
                                    spawnConfig.setCommandConfig(commandConfig);
                                }
                                break;
                            }
                        }
                    }

                    Arena arena = new Arena(worldId, displayName, arenaWorld, regenerateWorld,
                            spectatorSpawn, boundaries, spawnType, spawnConfig);
                    boolean buildAllowed = worldSection.getBoolean("build-allowed", false);
                    arena.setBuildAllowed(buildAllowed);
                    if (cloneSourceWorld != null && !cloneSourceWorld.trim().isEmpty()) {
                        arena.setCloneSourceWorld(cloneSourceWorld.trim());
                    }
                    arenas.put(worldId, arena);
                    plugin.getLogger().info("Loaded PvP arena from worlds.yml: " + worldId + " (" + displayName + ") - SpawnType: " + spawnType);

                } catch (Exception e) {
                    plugin.getLogger().severe("Error loading PvP world '" + worldId + "': " + e.getMessage());
                    if (plugin.getConfig() != null && plugin.getConfig().getBoolean("debug", false)) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // Legacy-Modus: pvp.arenas oder arenas
        ConfigurationSection arenasSection = arenaConfig.getConfigurationSection("arenas");
        if (arenasSection == null) {
            ConfigurationSection pvpSection = arenaConfig.getConfigurationSection("pvp");
            if (pvpSection != null) {
                arenasSection = pvpSection.getConfigurationSection("arenas");
            }
        }
        if (worldsSection == null && arenasSection == null) {
            plugin.getLogger().warning("No arenas/worlds found in worlds.yml (worlds.* or pvp.arenas) or arenas.yml (arenas)!");
            return;
        }

        if (arenasSection != null) {
        for (String arenaId : arenasSection.getKeys(false)) {
            try {
                ConfigurationSection arenaSection = arenasSection.getConfigurationSection(arenaId);
                if (arenaSection == null) continue;
                
                boolean enabled = arenaSection.getBoolean("enabled", true);
                if (!enabled) {
                    plugin.getLogger().info("Arena '" + arenaId + "' is disabled, skipping...");
                    continue;
                }
                
                String displayName = arenaSection.getString("display-name", arenaId);
                
                // Load world settings (legacy)
                ConfigurationSection legacyWorldsSection = arenaSection.getConfigurationSection("worlds");
                String arenaWorld = legacyWorldsSection.getString("arena-world");
                boolean regenerateWorld = legacyWorldsSection.getBoolean("regenerate-world", false);
                String cloneSourceWorld = legacyWorldsSection.getString("clone-source-world", null);
                
                // Load spectator spawn
                ConfigurationSection spectatorSection = arenaSection.getConfigurationSection("spawns.spectator");
                Location spectatorSpawn = loadLocation(spectatorSection, arenaWorld);
                
                // Load boundaries
                Boundaries boundaries = null;
                if (arenaSection.contains("boundaries") && arenaSection.getBoolean("boundaries.enabled", false)) {
                    ConfigurationSection boundariesSection = arenaSection.getConfigurationSection("boundaries");
                    
                    double minX = boundariesSection.getDouble("min-x");
                    double maxX = boundariesSection.getDouble("max-x");
                    double minY = boundariesSection.getDouble("min-y");
                    double maxY = boundariesSection.getDouble("max-y");
                    double minZ = boundariesSection.getDouble("min-z");
                    double maxZ = boundariesSection.getDouble("max-z");
                    
                    World world = Bukkit.getWorld(arenaWorld);
                    if (world != null) {
                        Location minLoc = new Location(world, minX, minY, minZ);
                        Location maxLoc = new Location(world, maxX, maxY, maxZ);
                        boundaries = new Boundaries(minLoc, maxLoc);
                    }
                }
                
                // Parse Spawn-Type
                ConfigurationSection spawnSettings = arenaSection.getConfigurationSection("spawn-settings");
                Arena.SpawnType spawnType = Arena.SpawnType.FIXED_SPAWNS;
                Arena.SpawnConfig spawnConfig = new Arena.SpawnConfig();
                
                if (spawnSettings != null && spawnSettings.contains("spawn-type")) {
                    String spawnTypeStr = spawnSettings.getString("spawn-type", "FIXED_SPAWNS");
                    spawnType = Arena.SpawnType.valueOf(spawnTypeStr);
                    
                    switch (spawnType) {
                        case FIXED_SPAWNS:
                            List<Location> fixedSpawns = new ArrayList<>();
                            ConfigurationSection player1Section = arenaSection.getConfigurationSection("spawns.player1");
                            ConfigurationSection player2Section = arenaSection.getConfigurationSection("spawns.player2");
                            if (player1Section != null) fixedSpawns.add(loadLocation(player1Section, arenaWorld));
                            if (player2Section != null) fixedSpawns.add(loadLocation(player2Section, arenaWorld));
                            spawnConfig.setFixedSpawns(fixedSpawns);
                            break;
                            
                        case RANDOM_RADIUS:
                            ConfigurationSection radiusSection = spawnSettings.getConfigurationSection("random-radius");
                            if (radiusSection != null) {
                                Arena.RandomRadiusConfig radiusConfig = new Arena.RandomRadiusConfig(
                                    radiusSection.getDouble("center-x", 0),
                                    radiusSection.getDouble("center-z", 0),
                                    radiusSection.getDouble("radius", 100),
                                    radiusSection.getDouble("min-distance", 10)
                                );
                                spawnConfig.setRandomRadius(radiusConfig);
                            }
                            break;
                            
                        case RANDOM_AREA:
                            ConfigurationSection areaSection = spawnSettings.getConfigurationSection("random-area");
                            if (areaSection != null) {
                                ConfigurationSection point1 = areaSection.getConfigurationSection("point1");
                                ConfigurationSection point2 = areaSection.getConfigurationSection("point2");
                                Arena.RandomAreaConfig areaConfig = new Arena.RandomAreaConfig(
                                    point1.getDouble("x", 0),
                                    point1.getDouble("z", 0),
                                    point2.getDouble("x", 100),
                                    point2.getDouble("z", 100),
                                    areaSection.getDouble("min-distance", 10)
                                );
                                spawnConfig.setRandomArea(areaConfig);
                            }
                            break;
                            
                        case RANDOM_CUBE:
                            ConfigurationSection cubeSection = spawnSettings.getConfigurationSection("random-cube");
                            if (cubeSection != null) {
                                ConfigurationSection point1 = cubeSection.getConfigurationSection("point1");
                                ConfigurationSection point2 = cubeSection.getConfigurationSection("point2");
                                Arena.RandomCubeConfig cubeConfig = new Arena.RandomCubeConfig(
                                    point1.getDouble("x", 0),
                                    point1.getDouble("y", 50),
                                    point1.getDouble("z", 0),
                                    point2.getDouble("x", 100),
                                    point2.getDouble("y", 150),
                                    point2.getDouble("z", 100),
                                    cubeSection.getDouble("min-distance", 10)
                                );
                                spawnConfig.setRandomCube(cubeConfig);
                            }
                            break;
                            
                        case MULTIPLE_SPAWNS:
                            List<Location> multipleSpawns = new ArrayList<>();
                            ConfigurationSection spawnsSection = arenaSection.getConfigurationSection("spawns");
                            if (spawnsSection != null) {
                                for (String key : spawnsSection.getKeys(false)) {
                                    if (!key.equals("spectator")) {
                                        ConfigurationSection spawnSection = spawnsSection.getConfigurationSection(key);
                                        multipleSpawns.add(loadLocation(spawnSection, arenaWorld));
                                    }
                                }
                            }
                            spawnConfig.setMultipleSpawns(multipleSpawns);
                            break;
                            
                        case COMMAND:
                            ConfigurationSection cmdSection = spawnSettings.getConfigurationSection("command");
                            if (cmdSection != null) {
                                String command = cmdSection.getString("command", "");
                                Map<String, String> placeholders = new HashMap<>();
                                
                                ConfigurationSection placeholderSection = cmdSection.getConfigurationSection("placeholders");
                                if (placeholderSection != null) {
                                    for (String key : placeholderSection.getKeys(false)) {
                                        placeholders.put(key, placeholderSection.getString(key));
                                    }
                                }
                                
                                Arena.CommandConfig commandConfig = new Arena.CommandConfig(command, placeholders);
                                spawnConfig.setCommandConfig(commandConfig);
                            }
                            break;
                    }
                } else {
                    // Legacy: Alte Config ohne spawn-type
                    List<Location> fixedSpawns = new ArrayList<>();
                    ConfigurationSection player1Section = arenaSection.getConfigurationSection("spawns.player1");
                    ConfigurationSection player2Section = arenaSection.getConfigurationSection("spawns.player2");
                    if (player1Section != null) fixedSpawns.add(loadLocation(player1Section, arenaWorld));
                    if (player2Section != null) fixedSpawns.add(loadLocation(player2Section, arenaWorld));
                    spawnConfig.setFixedSpawns(fixedSpawns);
                }
                
                Arena arena = new Arena(arenaId, displayName, arenaWorld, regenerateWorld,
                                      spectatorSpawn, boundaries, spawnType, spawnConfig);
                boolean buildAllowed = false;
                if (legacyWorldsSection != null && legacyWorldsSection.contains("build-allowed")) {
                    buildAllowed = legacyWorldsSection.getBoolean("build-allowed", false);
                } else {
                    buildAllowed = arenaSection.getBoolean("build-allowed", false);
                }
                arena.setBuildAllowed(buildAllowed);
                // Optional: Clone-basierter Reset
                if (cloneSourceWorld != null && !cloneSourceWorld.trim().isEmpty()) {
                    arena.setCloneSourceWorld(cloneSourceWorld.trim());
                }
                arenas.put(arenaId, arena);
                
                plugin.getLogger().info("Loaded arena: " + arenaId + " (" + displayName + ") - SpawnType: " + spawnType);
                
            } catch (Exception e) {
                plugin.getLogger().severe("Error loading arena '" + arenaId + "': " + e.getMessage());
                e.printStackTrace();
            }
        }
        }
        
        if (arenas.isEmpty()) {
            plugin.getLogger().warning("No arenas loaded! Plugin may not work correctly.");
        }
    }
    
    private Location loadLocation(ConfigurationSection section, String worldName) {
        if (section == null) return null;
        
        try {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("World '" + worldName + "' not found for arena spawn!");
                world = Bukkit.getWorlds().get(0); // Fallback
            }
            
            double x = section.getDouble("x");
            double y = section.getDouble("y");
            double z = section.getDouble("z");
            float yaw = (float) section.getDouble("yaw", 0.0);
            float pitch = (float) section.getDouble("pitch", 0.0);
            
            return new Location(world, x, y, z, yaw, pitch);
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error parsing location: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Regeneriert Arena-Welt mit Multiverse Commands
     */
    public void regenerateArenaWorld(String worldName) {
        multiverseHelper.regenerateWorld(worldName, null);
    }
    
    public void loadArenaWorld(String worldName) {
        loadArenaWorld(worldName, null);
    }
    
    public void loadArenaWorld(String worldName, Runnable callback) {
        int refreshTaskId = -1;
        for (Match match : plugin.getMatchManager().getMatches().values()) {
            if (match.getArena() != null && match.getArena().getArenaWorld().equals(worldName)) {
                match.setWorldLoading(true);
                refreshTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (match.isWorldLoading()) {
                        match.broadcast(plugin.getConfigManager().getMessage("world.loading"));
                    }
                }, 40L, 40L).getTaskId();
            }
        }
        final int finalRefreshTaskId = refreshTaskId;
        Runnable doLoad = () -> multiverseHelper.loadWorld(worldName, new MultiverseHelper.LoadCallback() {
            @Override
            public void onResult(boolean success, String message) {
                if (finalRefreshTaskId != -1) {
                    Bukkit.getScheduler().cancelTask(finalRefreshTaskId);
                }
                for (Match match : plugin.getMatchManager().getMatches().values()) {
                    if (match.getArena() != null && match.getArena().getArenaWorld().equals(worldName)) {
                        match.setWorldLoading(false);
                        if (success) {
                            match.broadcast(plugin.getConfigManager().getMessage("world.loaded"));
                        } else {
                            match.broadcast(plugin.getConfigManager().getMessage("world.load-failed"));
                        }
                    }
                }
                if (success) {
                    plugin.getLogger().info("Welt-Ladung erfolgreich: " + worldName + " - " + message);
                } else {
                    plugin.getLogger().warning("Welt-Ladung fehlgeschlagen: " + worldName + " - " + message);
                }
                if (callback != null) {
                    callback.run();
                }
            }
        });

        doLoad.run();
    }
    
    public void unloadArenaWorld(String worldName) {
        String worldLoading = plugin.getPvpConfigManager().getConfig().getString("settings.world-loading", "both");
        
        if (worldLoading.equalsIgnoreCase("none")) {
            return;
        }
        
        if (worldLoading.equalsIgnoreCase("arena") || worldLoading.equalsIgnoreCase("both")) {
            // Check if world is loaded
            if (Bukkit.getWorld(worldName) == null) {
                return;
            }
            
            plugin.getLogger().info("Unloading arena world via Multiverse: " + worldName);
            multiverseHelper.unloadWorld(worldName);
        }
    }

    /**
     * Setzt die Arena-Welt per Clone zurück: Zielwelt entladen, löschen, aus Quelle klonen.
    */
    public void resetArenaWorldByClone(String sourceWorld, String targetWorld) {
        if (sourceWorld == null || sourceWorld.isEmpty() || targetWorld == null || targetWorld.isEmpty()) {
            plugin.getLogger().warning("Clone-Reset übersprungen: ungültige Weltangaben.");
            return;
        }

        // Entladen (falls geladen)
        unloadArenaWorld(targetWorld);

        // Löschen, danach klonen
        multiverseHelper.deleteWorld(targetWorld, () -> {
            multiverseHelper.cloneWorld(sourceWorld, targetWorld, () -> {
                plugin.getLogger().info("Clone-Reset abgeschlossen für Arena-Welt: " + targetWorld);
            });
        });
    }
    
    public Arena getArena(String arenaId) {
        return arenas.get(arenaId);
    }

    /**
     * Konsistente Optional-API für Arena-Zugriff, vermeidet Null-Rückgaben.
     */
    public java.util.Optional<Arena> getArenaOptional(String arenaId) {
        return java.util.Optional.ofNullable(arenas.get(arenaId));
    }
    
    public Map<String, Arena> getArenas() {
        return new HashMap<>(arenas);
    }
    
    public void reloadArenas() {
        loadArenas();
    }
}