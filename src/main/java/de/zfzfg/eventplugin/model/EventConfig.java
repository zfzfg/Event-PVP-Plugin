package de.zfzfg.eventplugin.model;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventConfig {
    
    private final String id;
    private final String command;
    private final String displayName;
    private final String description;
    
    private final int minPlayers;
    private final int maxPlayers;
    private final int countdownTime;
    
    private final String lobbyWorld;
    private final Location lobbySpawn;

        private final String eventWorld;
    private final String cloneSourceEventWorld;
    private final boolean regenerateEventWorld;
    private final boolean regenerateLobbyWorld;
    private final boolean buildAllowed;
    
    private final SpawnType spawnType;
    private final SpawnConfig spawnConfig;
    
    private final String equipmentGroup;
    private final boolean giveEquipmentInLobby;
    private final boolean lobbyTeamColoredArmor;
    
    private final GameMode gameMode;
    private final TeamSettings teamSettings;
    private final WinCondition winCondition;
    private final DeathHandling deathHandling;
    private final boolean pvpEnabled;
    private final boolean hungerEnabled;
    
    private final RewardConfig winnerRewards;
    private final RewardConfig teamWinnerRewards;
    private final RewardConfig participationRewards;
    
    private final Map<String, String> messages;
    
    public EventConfig(String id, ConfigurationSection section) {
        this.id = id;
        this.command = section.getString("command", id);
        this.displayName = section.getString("display-name", id);
        this.description = section.getString("description", "");
        
        this.minPlayers = section.getInt("min-players", 2);
        this.maxPlayers = section.getInt("max-players", 20);
        this.countdownTime = section.getInt("countdown-time", 60);
        
        ConfigurationSection worlds = section.getConfigurationSection("worlds");
        if (worlds == null) {
            worlds = section.createSection("worlds");
        }
        this.lobbyWorld = worlds.getString("lobby-world", "EventLobby");
        ConfigurationSection lobbySpawnSection = worlds.getConfigurationSection("lobby-spawn");
        this.lobbySpawn = parseLobbySpawn(lobbySpawnSection);
        this.eventWorld = worlds.getString("event-world", "Event");
        this.cloneSourceEventWorld = worlds.getString("clone-source-event-world", null);
        this.regenerateEventWorld = worlds.getBoolean("regenerate-event-world", false);
        this.regenerateLobbyWorld = worlds.getBoolean("regenerate-lobby-world", false);
        this.buildAllowed = worlds.getBoolean("build-allowed", false);
        
        ConfigurationSection spawnSettings = section.getConfigurationSection("spawn-settings");
        if (spawnSettings == null) {
            spawnSettings = section.createSection("spawn-settings");
        }
        String spawnTypeStr = spawnSettings.getString("spawn-type", "SINGLE_POINT");
        SpawnType parsedSpawnType;
        try {
            parsedSpawnType = SpawnType.valueOf(spawnTypeStr);
        } catch (IllegalArgumentException e) {
            parsedSpawnType = SpawnType.SINGLE_POINT;
        }
        this.spawnType = parsedSpawnType;
        this.spawnConfig = new SpawnConfig(spawnSettings, this.spawnType);
        
        this.equipmentGroup = section.getString("equipment-group", "");
        this.giveEquipmentInLobby = section.getBoolean("give-equipment-in-lobby", false);
        this.lobbyTeamColoredArmor = section.getBoolean("lobby-team-colored-armor", false);
        
        ConfigurationSection mechanics = section.getConfigurationSection("mechanics");
        if (mechanics == null) {
            mechanics = section.createSection("mechanics");
        }
        String gameModeStr = mechanics.getString("game-mode", "SOLO");
        GameMode parsedGameMode;
        try {
            parsedGameMode = GameMode.valueOf(gameModeStr);
        } catch (IllegalArgumentException e) {
            parsedGameMode = GameMode.SOLO;
        }
        this.gameMode = parsedGameMode;
        ConfigurationSection teamSettingsSection = mechanics.getConfigurationSection("team-settings");
        this.teamSettings = teamSettingsSection != null ? new TeamSettings(teamSettingsSection) : null;
        ConfigurationSection winCond = mechanics.getConfigurationSection("win-condition");
        this.winCondition = new WinCondition(winCond);
        ConfigurationSection deathHandle = mechanics.getConfigurationSection("death-handling");
        this.deathHandling = new DeathHandling(deathHandle);
        this.pvpEnabled = mechanics.getBoolean("pvp-enabled", true);
        this.hungerEnabled = mechanics.getBoolean("hunger-enabled", true);
        
        ConfigurationSection rewards = section.getConfigurationSection("rewards");
        if (rewards == null) {
            rewards = section.createSection("rewards");
        }
        ConfigurationSection winnerSection = rewards.getConfigurationSection("winner");
        this.winnerRewards = winnerSection != null ? new RewardConfig(winnerSection) : null;
        ConfigurationSection teamWinnerSection = rewards.getConfigurationSection("team-winner");
        this.teamWinnerRewards = teamWinnerSection != null ? new RewardConfig(teamWinnerSection) : null;
        ConfigurationSection participationSection = rewards.getConfigurationSection("participation");
        this.participationRewards = new RewardConfig(participationSection);
        
        this.messages = new HashMap<>();
        ConfigurationSection msgs = section.getConfigurationSection("messages");
        if (msgs != null) {
            for (String key : msgs.getKeys(false)) {
                messages.put(key, msgs.getString(key));
            }
        }
    }
    
    private Location parseLobbySpawn(ConfigurationSection section) {
        if (section == null) {
            return new Location(null, 0, 100, 0, 0, 0);
        }
        double x = section.getDouble("x", 0);
        double y = section.getDouble("y", 100);
        double z = section.getDouble("z", 0);
        float yaw = (float) section.getDouble("yaw", 0);
        float pitch = (float) section.getDouble("pitch", 0);
        return new Location(null, x, y, z, yaw, pitch);
    }
    
    // Getter-Methoden
    public String getId() { return id; }
    public String getCommand() { return command; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public int getMinPlayers() { return minPlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public int getCountdownTime() { return countdownTime; }
    public String getLobbyWorld() { return lobbyWorld; }
    public Location getLobbySpawn() { return lobbySpawn; }
    
    public String getEventWorld() { return eventWorld; }
public String getCloneSourceEventWorld() { return cloneSourceEventWorld; }
    public boolean shouldRegenerateEventWorld() { return regenerateEventWorld; }
    public boolean shouldRegenerateLobbyWorld() { return regenerateLobbyWorld; }
    public boolean isBuildAllowed() { return buildAllowed; }
    public SpawnType getSpawnType() { return spawnType; }
    public SpawnConfig getSpawnConfig() { return spawnConfig; }
    public String getEquipmentGroup() { return equipmentGroup; }
    public boolean shouldGiveEquipmentInLobby() { return giveEquipmentInLobby; }
    public boolean shouldColorLobbyArmor() { return lobbyTeamColoredArmor; }
    public GameMode getGameMode() { return gameMode; }
    public TeamSettings getTeamSettings() { return teamSettings; }
    public WinCondition getWinCondition() { return winCondition; }
    public DeathHandling getDeathHandling() { return deathHandling; }
    public boolean isPvpEnabled() { return pvpEnabled; }
    public boolean isHungerEnabled() { return hungerEnabled; }
    public RewardConfig getWinnerRewards() { return winnerRewards; }
    public RewardConfig getTeamWinnerRewards() { return teamWinnerRewards; }
    public RewardConfig getParticipationRewards() { return participationRewards; }
    public String getMessage(String key) { return messages.getOrDefault(key, ""); }
    
    // Enums und Inner Classes
    public enum GameMode {
        SOLO,
        TEAM_2,
        TEAM_3
    }
    
    public enum SpawnType {
        SINGLE_POINT,
        RANDOM_RADIUS,
        RANDOM_AREA,
        RANDOM_CUBE,
        MULTIPLE_SPAWNS,
        TEAM_SPAWNS,
        COMMAND  // NEU: Spawn per Command
    }
    
    public static class TeamSettings {
        private final boolean friendlyFire;
        private final boolean autoBalance;
        
        public TeamSettings(ConfigurationSection section) {
            this.friendlyFire = section.getBoolean("friendly-fire", false);
            this.autoBalance = section.getBoolean("auto-balance", true);
        }
        
        public boolean isFriendlyFire() { return friendlyFire; }
        public boolean isAutoBalance() { return autoBalance; }
    }
    
    public static class SpawnConfig {
        private Location singleSpawn;
        private RandomRadiusConfig randomRadius;
        private RandomAreaConfig randomArea;
        private RandomCubeConfig randomCube;
        private List<Location> multipleSpawns;
        private Map<String, List<Location>> teamSpawns;
        private String spawnCommand;  // NEU: Command fÃƒÂ¼r COMMAND spawn-type
        
        public SpawnConfig(ConfigurationSection section, SpawnType type) {
            switch (type) {
                case SINGLE_POINT:
                    ConfigurationSection single = section.getConfigurationSection("single-spawn");
                    this.singleSpawn = parseLocation(single);
                    break;
                case RANDOM_RADIUS:
                    ConfigurationSection random = section.getConfigurationSection("random-radius");
                    this.randomRadius = new RandomRadiusConfig(random);
                    break;
                case RANDOM_AREA:
                    ConfigurationSection area = section.getConfigurationSection("random-area");
                    this.randomArea = new RandomAreaConfig(area);
                    break;
                case RANDOM_CUBE:
                    ConfigurationSection cube = section.getConfigurationSection("random-cube");
                    this.randomCube = new RandomCubeConfig(cube);
                    break;
                case MULTIPLE_SPAWNS:
                    ConfigurationSection multiple = section.getConfigurationSection("multiple-spawns.spawns");
                    this.multipleSpawns = new ArrayList<>();
                    if (multiple != null) {
                        for (String key : multiple.getKeys(false)) {
                            ConfigurationSection spawn = multiple.getConfigurationSection(key);
                            multipleSpawns.add(parseLocation(spawn));
                        }
                    }
                    break;
                case TEAM_SPAWNS:
                    ConfigurationSection teamSection = section.getConfigurationSection("team-spawns");
                    this.teamSpawns = new HashMap<>();
                    if (teamSection != null) {
                        for (String teamName : teamSection.getKeys(false)) {
                            List<Location> spawns = new ArrayList<>();
                            ConfigurationSection teamSpawnSection = teamSection.getConfigurationSection(teamName);
                            if (teamSpawnSection != null) {
                                for (String key : teamSpawnSection.getKeys(false)) {
                                    ConfigurationSection spawn = teamSpawnSection.getConfigurationSection(key);
                                    spawns.add(parseLocation(spawn));
                                }
                            }
                            teamSpawns.put(teamName, spawns);
                        }
                    }
                    break;
                case COMMAND:  // NEU
                    this.spawnCommand = section.getString("spawn-command", "");
                    break;
            }
        }
        
        private Location parseLocation(ConfigurationSection section) {
            if (section == null) {
                return new Location(null, 0, 100, 0, 0, 0);
            }
            double x = section.getDouble("x", 0);
            double y = section.getDouble("y", 100);
            double z = section.getDouble("z", 0);
            float yaw = (float) section.getDouble("yaw", 0);
            float pitch = (float) section.getDouble("pitch", 0);
            return new Location(null, x, y, z, yaw, pitch);
        }
        
        public Location getSingleSpawn() { return singleSpawn; }
        public RandomRadiusConfig getRandomRadius() { return randomRadius; }
        public RandomAreaConfig getRandomArea() { return randomArea; }
        public RandomCubeConfig getRandomCube() { return randomCube; }
        public List<Location> getMultipleSpawns() { return multipleSpawns; }
        public Map<String, List<Location>> getTeamSpawns() { return teamSpawns; }
        public String getSpawnCommand() { return spawnCommand; }  // NEU
    }
    
    public static class RandomRadiusConfig {
        private final double centerX;
        private final double centerZ;
        private final double radius;
        private final double minDistance;
        
        public RandomRadiusConfig(ConfigurationSection section) {
            if (section == null) {
                this.centerX = 0; this.centerZ = 0; this.radius = 100; this.minDistance = 10; return;
            }
            this.centerX = section.getDouble("center-x", 0);
            this.centerZ = section.getDouble("center-z", 0);
            this.radius = section.getDouble("radius", 100);
            this.minDistance = section.getDouble("min-distance", 10);
        }
        
        public double getCenterX() { return centerX; }
        public double getCenterZ() { return centerZ; }
        public double getRadius() { return radius; }
        public double getMinDistance() { return minDistance; }
    }
    
    public static class RandomAreaConfig {
        private final double point1X;
        private final double point1Z;
        private final double point2X;
        private final double point2Z;
        private final double minDistance;
        
        public RandomAreaConfig(ConfigurationSection section) {
            if (section == null) {
                this.point1X = 0; this.point1Z = 0; this.point2X = 100; this.point2Z = 100; this.minDistance = 10; return;
            }
            ConfigurationSection point1 = section.getConfigurationSection("point1");
            ConfigurationSection point2 = section.getConfigurationSection("point2");
            this.point1X = (point1 != null) ? point1.getDouble("x", 0) : 0;
            this.point1Z = (point1 != null) ? point1.getDouble("z", 0) : 0;
            this.point2X = (point2 != null) ? point2.getDouble("x", 100) : 100;
            this.point2Z = (point2 != null) ? point2.getDouble("z", 100) : 100;
            this.minDistance = section.getDouble("min-distance", 10);
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
        private final double point1X;
        private final double point1Y;
        private final double point1Z;
        private final double point2X;
        private final double point2Y;
        private final double point2Z;
        private final double minDistance;
        
        public RandomCubeConfig(ConfigurationSection section) {
            if (section == null) {
                this.point1X = 0; this.point1Y = 50; this.point1Z = 0; this.point2X = 100; this.point2Y = 150; this.point2Z = 100; this.minDistance = 10; return;
            }
            ConfigurationSection point1 = section.getConfigurationSection("point1");
            ConfigurationSection point2 = section.getConfigurationSection("point2");
            this.point1X = (point1 != null) ? point1.getDouble("x", 0) : 0;
            this.point1Y = (point1 != null) ? point1.getDouble("y", 50) : 50;
            this.point1Z = (point1 != null) ? point1.getDouble("z", 0) : 0;
            this.point2X = (point2 != null) ? point2.getDouble("x", 100) : 100;
            this.point2Y = (point2 != null) ? point2.getDouble("y", 150) : 150;
            this.point2Z = (point2 != null) ? point2.getDouble("z", 100) : 100;
            this.minDistance = section.getDouble("min-distance", 10);
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
    
    public static class WinCondition {
        private final String type;
        private final String item;
        private final int amount;
        
        public WinCondition(ConfigurationSection section) {
            if (section == null) {
                this.type = "PICKUP_ITEM"; this.item = "IRON_INGOT"; this.amount = 1; return;
            }
            this.type = section.getString("type", "PICKUP_ITEM");
            this.item = section.getString("item", "IRON_INGOT");
            this.amount = section.getInt("amount", 1);
        }
        
        public String getType() { return type; }
        public String getItem() { return item; }
        public int getAmount() { return amount; }
    }
    
    public static class DeathHandling {
        private final boolean eliminateOnDeath;
        private final boolean spectatorMode;
        private final boolean allowRejoin;
        
        public DeathHandling(ConfigurationSection section) {
            if (section == null) {
                this.eliminateOnDeath = true; this.spectatorMode = true; this.allowRejoin = false; return;
            }
            this.eliminateOnDeath = section.getBoolean("eliminate-on-death", true);
            this.spectatorMode = section.getBoolean("spectator-mode", true);
            this.allowRejoin = section.getBoolean("allow-rejoin", false);
        }
        
        public boolean shouldEliminateOnDeath() { return eliminateOnDeath; }
        public boolean isSpectatorMode() { return spectatorMode; }
        public boolean isAllowRejoin() { return allowRejoin; }
    }
    
    public static class RewardConfig {
        private final boolean itemsEnabled;
        private final List<String> items;
        private final boolean commandsEnabled;
        private final List<String> commands;
        
        public RewardConfig(ConfigurationSection section) {
            if (section == null) {
                this.itemsEnabled = true; this.items = new java.util.ArrayList<>(); this.commandsEnabled = false; this.commands = new java.util.ArrayList<>(); return;
            }
            ConfigurationSection itemsSec = section.getConfigurationSection("items");
            if (itemsSec == null) itemsSec = section.createSection("items");
            this.itemsEnabled = itemsSec.getBoolean("enabled", true);
            this.items = itemsSec.getStringList("items");
            ConfigurationSection cmdsSec = section.getConfigurationSection("commands");
            if (cmdsSec == null) cmdsSec = section.createSection("commands");
            this.commandsEnabled = cmdsSec.getBoolean("enabled", false);
            this.commands = cmdsSec.getStringList("commands");
        }
        
        public boolean areItemsEnabled() { return itemsEnabled; }
        public List<String> getItems() { return items; }
        public boolean areCommandsEnabled() { return commandsEnabled; }
        public List<String> getCommands() { return commands; }
    }
}





