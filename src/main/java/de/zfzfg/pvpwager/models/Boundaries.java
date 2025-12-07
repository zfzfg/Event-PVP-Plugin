package de.zfzfg.pvpwager.models;

import org.bukkit.Location;

public class Boundaries {
    private Location min;
    private Location max;
    
    public Boundaries(Location min, Location max) {
        this.min = min;
        this.max = max;
    }
    
    public boolean isInside(Location location) {
        if (location == null || min == null || max == null) {
            return true; // No boundaries check if not set
        }
        
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        
        double minX = Math.min(min.getX(), max.getX());
        double maxX = Math.max(min.getX(), max.getX());
        double minY = Math.min(min.getY(), max.getY());
        double maxY = Math.max(min.getY(), max.getY());
        double minZ = Math.min(min.getZ(), max.getZ());
        double maxZ = Math.max(min.getZ(), max.getZ());
        
        return x >= minX && x <= maxX &&
               y >= minY && y <= maxY &&
               z >= minZ && z <= maxZ;
    }
    
    // Getters and setters
    public Location getMin() { return min; }
    public Location getMax() { return max; }
}