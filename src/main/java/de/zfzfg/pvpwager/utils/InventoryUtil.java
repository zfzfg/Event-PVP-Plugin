package de.zfzfg.pvpwager.utils;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class InventoryUtil {
    
    public static boolean hasSpaceForItems(Player player, List<ItemStack> items) {
        Inventory inventory = player.getInventory();
        int emptySlots = 0;
        
        // Count empty slots
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                emptySlots++;
            }
        }
        
        // Check if we have enough space
        return emptySlots >= items.size();
    }
    
    public static boolean canFitItems(Player player, List<ItemStack> items) {
        Inventory inventory = player.getInventory();
        
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;
            
            int amountNeeded = item.getAmount();
            boolean foundSpace = false;
            
            // Check existing stacks of the same type
            for (ItemStack existing : inventory.getContents()) {
                if (existing != null && existing.getType() == item.getType() && existing.getAmount() < existing.getMaxStackSize()) {
                    int spaceInStack = existing.getMaxStackSize() - existing.getAmount();
                    if (spaceInStack >= amountNeeded) {
                        foundSpace = true;
                        break;
                    }
                    amountNeeded -= spaceInStack;
                }
            }
            
            // Check empty slots
            if (!foundSpace) {
                int emptySlots = 0;
                for (ItemStack existing : inventory.getContents()) {
                    if (existing == null || existing.getType() == Material.AIR) {
                        emptySlots++;
                    }
                }
                
                // Each empty slot can hold max stack size
                int totalSpace = emptySlots * item.getMaxStackSize();
                if (totalSpace >= amountNeeded) {
                    foundSpace = true;
                }
            }
            
            if (!foundSpace) {
                return false;
            }
        }
        
        return true;
    }
    
    public static void giveItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) {
                player.getInventory().addItem(item);
            }
        }
    }
    
    public static void clearInventory(Player player) {
        player.getInventory().clear();
    }
    
    public static List<ItemStack> getNonEmptyItems(Inventory inventory) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                items.add(item);
            }
        }
        return items;
    }
}