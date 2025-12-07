package de.zfzfg.pvpwager.models;

import org.bukkit.inventory.ItemStack;
import java.util.Map;

public class EquipmentSet {
    private String id;
    private String displayName;
    private ItemStack helmet, chestplate, leggings, boots;
    private Map<Integer, ItemStack> inventory;
    private java.util.Set<String> allowedWorlds;
    private boolean allowAll;
    private boolean allowNone;
    
    public EquipmentSet(String id, String displayName, ItemStack helmet, ItemStack chestplate, 
                       ItemStack leggings, ItemStack boots, Map<Integer, ItemStack> inventory) {
        this.id = id;
        this.displayName = displayName;
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;
        this.inventory = inventory;
        this.allowedWorlds = new java.util.HashSet<>();
        this.allowAll = true;
        this.allowNone = false;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public ItemStack getHelmet() { return helmet; }
    public ItemStack getChestplate() { return chestplate; }
    public ItemStack getLeggings() { return leggings; }
    public ItemStack getBoots() { return boots; }
    public Map<Integer, ItemStack> getInventory() { return inventory; }
    public java.util.Set<String> getAllowedWorlds() { return allowedWorlds; }
    public void setAllowedWorlds(java.util.Set<String> worlds) { this.allowedWorlds = worlds != null ? worlds : new java.util.HashSet<>(); }
    public boolean isAllowAll() { return allowAll; }
    public void setAllowAll(boolean allowAll) { this.allowAll = allowAll; }
    public boolean isAllowNone() { return allowNone; }
    public void setAllowNone(boolean allowNone) { this.allowNone = allowNone; }
    public boolean isAllowedForWorld(String worldName) {
        if (allowNone) return false;
        if (allowAll) return true;
        if (worldName == null) return false;
        for (String w : allowedWorlds) {
            if (worldName.equalsIgnoreCase(w)) return true;
        }
        return false;
    }
}