package de.zfzfg.pvpwager.models;

import org.bukkit.Location;
import org.bukkit.World;
import java.util.List;
import java.util.Map;

public class Arena {
    private String id;
    private String displayName;
    private String arenaWorld;
    private boolean regenerateWorld;
    private String cloneSourceWorld;
    private Location spectatorSpawn;
    private Boundaries boundaries;
    
    // Neue Spawn-Konfiguration
    private SpawnType spawnType;
    private SpawnConfig spawnConfig;
    private boolean buildAllowed;
    
    // Legacy Spawns (für Rückwärtskompatibilität)
    private Location player1Spawn;
    private Location player2Spawn;
    
    public Arena(String id, String displayName, String arenaWorld, boolean regenerateWorld,
                Location player1Spawn, Location player2Spawn, Location spectatorSpawn, Boundaries boundaries) {
        this.id = id;
        this.displayName = displayName;
        this.arenaWorld = arenaWorld;
        this.regenerateWorld = regenerateWorld;
        this.player1Spawn = player1Spawn;
        this.player2Spawn = player2Spawn;
        this.spectatorSpawn = spectatorSpawn;
        this.boundaries = boundaries;
        
        // Standard: FIXED_SPAWNS für alte Configs
        this.spawnType = SpawnType.FIXED_SPAWNS;
        this.spawnConfig = null;
    }
    
    // Neuer Constructor mit Spawn-Types
    public Arena(String id, String displayName, String arenaWorld, boolean regenerateWorld,
                Location spectatorSpawn, Boundaries boundaries, SpawnType spawnType, SpawnConfig spawnConfig) {
        this.id = id;
        this.displayName = displayName;
        this.arenaWorld = arenaWorld;
        this.regenerateWorld = regenerateWorld;
        this.spectatorSpawn = spectatorSpawn;
        this.boundaries = boundaries;
        this.spawnType = spawnType;
        this.spawnConfig = spawnConfig;
        this.buildAllowed = false;
        
        // Legacy Spawns für FIXED_SPAWNS
        if (spawnType == SpawnType.FIXED_SPAWNS && spawnConfig != null) {
            List<Location> fixedSpawns = spawnConfig.getFixedSpawns();
            if (fixedSpawns != null && fixedSpawns.size() >= 2) {
                this.player1Spawn = fixedSpawns.get(0);
                this.player2Spawn = fixedSpawns.get(1);
            }
        }
    }
    
    // Getters
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getArenaWorld() { return arenaWorld; }
    public boolean isRegenerateWorld() { return regenerateWorld; }
    public String getCloneSourceWorld() { return cloneSourceWorld; }
    public Location getPlayer1Spawn() { return player1Spawn; }
    public Location getPlayer2Spawn() { return player2Spawn; }
    public Location getSpectatorSpawn() {
        if (spectatorSpawn == null) return null;
        
        // Dynamically fix world reference if needed
        World currentWorld = spectatorSpawn.getWorld();
        World expectedWorld = org.bukkit.Bukkit.getWorld(arenaWorld);
        
        if (expectedWorld != null && (currentWorld == null || !currentWorld.getName().equals(arenaWorld))) {
            spectatorSpawn.setWorld(expectedWorld);
        }
        
        return spectatorSpawn;
    }
    public Boundaries getBoundaries() { return boundaries; }
    public SpawnType getSpawnType() { return spawnType; }
    public SpawnConfig getSpawnConfig() { return spawnConfig; }
    public boolean isBuildAllowed() { return buildAllowed; }
    public void setBuildAllowed(boolean buildAllowed) { this.buildAllowed = buildAllowed; }
    
    // Setters
    public void setSpawnType(SpawnType spawnType) { this.spawnType = spawnType; }
    public void setSpawnConfig(SpawnConfig spawnConfig) { this.spawnConfig = spawnConfig; }
    public void setCloneSourceWorld(String cloneSourceWorld) { this.cloneSourceWorld = cloneSourceWorld; }
    
    // Enums
    public enum SpawnType {
        FIXED_SPAWNS,      // Standard: Feste Spawns (player1, player2)
        RANDOM_RADIUS,     // Zufällig im Radius um Zentrum
        RANDOM_AREA,       // Zufällig in Rechteck-Area
        RANDOM_CUBE,       // Zufällig in 3D-Würfel
        MULTIPLE_SPAWNS,   // Liste von Spawn-Punkten
        COMMAND            // Custom Command ausführen
    }
    
    // Inner Classes für Spawn-Konfiguration
    public static class SpawnConfig {
        private List<Location> fixedSpawns;
        private RandomRadiusConfig randomRadius;
        private RandomAreaConfig randomArea;
        private RandomCubeConfig randomCube;
        private List<Location> multipleSpawns;
        private CommandConfig commandConfig;
        
        // Getters
        public List<Location> getFixedSpawns() { return fixedSpawns; }
        public RandomRadiusConfig getRandomRadius() { return randomRadius; }
        public RandomAreaConfig getRandomArea() { return randomArea; }
        public RandomCubeConfig getRandomCube() { return randomCube; }
        public List<Location> getMultipleSpawns() { return multipleSpawns; }
        public CommandConfig getCommandConfig() { return commandConfig; }
        
        // Setters
        public void setFixedSpawns(List<Location> fixedSpawns) { this.fixedSpawns = fixedSpawns; }
        public void setRandomRadius(RandomRadiusConfig randomRadius) { this.randomRadius = randomRadius; }
        public void setRandomArea(RandomAreaConfig randomArea) { this.randomArea = randomArea; }
        public void setRandomCube(RandomCubeConfig randomCube) { this.randomCube = randomCube; }
        public void setMultipleSpawns(List<Location> multipleSpawns) { this.multipleSpawns = multipleSpawns; }
        public void setCommandConfig(CommandConfig commandConfig) { this.commandConfig = commandConfig; }
    }
    
    public static class RandomRadiusConfig {
        private double centerX;
        private double centerZ;
        private double radius;
        private double minDistance;
        
        public RandomRadiusConfig(double centerX, double centerZ, double radius, double minDistance) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.radius = radius;
            this.minDistance = minDistance;
        }
        
        public double getCenterX() { return centerX; }
        public double getCenterZ() { return centerZ; }
        public double getRadius() { return radius; }
        public double getMinDistance() { return minDistance; }
    }
    
    public static class RandomAreaConfig {
        private double point1X;
        private double point1Z;
        private double point2X;
        private double point2Z;
        private double minDistance;
        
        public RandomAreaConfig(double point1X, double point1Z, double point2X, double point2Z, double minDistance) {
            this.point1X = point1X;
            this.point1Z = point1Z;
            this.point2X = point2X;
            this.point2Z = point2Z;
            this.minDistance = minDistance;
        }
        
        public double getPoint1X() { return point1X; }
        public double getPoint1Z() { return point1Z; }
        public double getPoint2X() { return point2X; }
        public double getPoint2Z() { return point2Z; }
        public double getMinDistance() { return minDistance; }
        public double getMinX() { return Math.min(point1X, point2X); }
        public double getMaxX() { return Math.max(point1X, point2X); }
        public double getMinZ() { return Math.min(point1Z, point2Z); }
        public double getMaxZ() { return Math.max(point1Z, point2Z); }
    }
    
    public static class RandomCubeConfig {
        private double point1X;
        private double point1Y;
        private double point1Z;
        private double point2X;
        private double point2Y;
        private double point2Z;
        private double minDistance;
        
        public RandomCubeConfig(double point1X, double point1Y, double point1Z,
                               double point2X, double point2Y, double point2Z, double minDistance) {
            this.point1X = point1X;
            this.point1Y = point1Y;
            this.point1Z = point1Z;
            this.point2X = point2X;
            this.point2Y = point2Y;
            this.point2Z = point2Z;
            this.minDistance = minDistance;
        }
        
        public double getPoint1X() { return point1X; }
        public double getPoint1Y() { return point1Y; }
        public double getPoint1Z() { return point1Z; }
        public double getPoint2X() { return point2X; }
        public double getPoint2Y() { return point2Y; }
        public double getPoint2Z() { return point2Z; }
        public double getMinDistance() { return minDistance; }
        public double getMinX() { return Math.min(point1X, point2X); }
        public double getMaxX() { return Math.max(point1X, point2X); }
        public double getMinY() { return Math.min(point1Y, point2Y); }
        public double getMaxY() { return Math.max(point1Y, point2Y); }
        public double getMinZ() { return Math.min(point1Z, point2Z); }
        public double getMaxZ() { return Math.max(point1Z, point2Z); }
    }
    
    public static class CommandConfig {
        private String command;
        private Map<String, String> placeholders;
        
        public CommandConfig(String command, Map<String, String> placeholders) {
            this.command = command;
            this.placeholders = placeholders;
        }
        
        public String getCommand() { return command; }
        public Map<String, String> getPlaceholders() { return placeholders; }
        
        public String formatCommand(String playerName, int playerNumber, Location location) {
            if (command == null || command.trim().isEmpty()) {
                return "";
            }

            String safePlayerName = (playerName != null) ? playerName : "";
            int x = 0, y = 64, z = 0;
            String worldName = "";
            if (location != null) {
                x = location.getBlockX();
                y = location.getBlockY();
                z = location.getBlockZ();
                if (location.getWorld() != null) {
                    worldName = location.getWorld().getName();
                }
            }

            String formatted = command;
            formatted = formatted.replace("{player}", safePlayerName);
            formatted = formatted.replace("{player_number}", String.valueOf(playerNumber));
            formatted = formatted.replace("{x}", String.valueOf(x));
            formatted = formatted.replace("{y}", String.valueOf(y));
            formatted = formatted.replace("{z}", String.valueOf(z));
            formatted = formatted.replace("{world}", worldName);

            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (key != null && value != null) {
                        formatted = formatted.replace("{" + key + "}", value);
                    }
                }
            }

            return formatted;
        }
    }
}
