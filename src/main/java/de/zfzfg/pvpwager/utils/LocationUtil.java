package de.zfzfg.pvpwager.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class LocationUtil {
    
    public static Location deserializeLocation(String locationString) {
        if (locationString == null || locationString.isEmpty()) return null;
        
        String[] parts = locationString.split(",");
        if (parts.length < 4) return null;
        
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;
            
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    public static String serializeLocation(Location location) {
        if (location == null) return "";
        String worldName = (location.getWorld() != null) ? location.getWorld().getName() : "unknown";
        return worldName + "," +
               location.getX() + "," +
               location.getY() + "," +
               location.getZ() + "," +
               location.getYaw() + "," +
               location.getPitch();
    }
    
    public static Location getLocationFromConfig(FileConfiguration config, String path) {
        if (!config.contains(path)) return null;
        
        String locationString = config.getString(path);
        return deserializeLocation(locationString);
    }
    
    public static void saveLocationToConfig(FileConfiguration config, String path, Location location) {
        if (location == null) return;
        
        config.set(path, serializeLocation(location));
    }
    
    public static Location getCenterLocation(Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null || !loc1.getWorld().equals(loc2.getWorld())) {
            return null;
        }
        
        double centerX = (loc1.getX() + loc2.getX()) / 2;
        double centerY = (loc1.getY() + loc2.getY()) / 2;
        double centerZ = (loc1.getZ() + loc2.getZ()) / 2;
        
        return new Location(loc1.getWorld(), centerX, centerY, centerZ);
    }
}