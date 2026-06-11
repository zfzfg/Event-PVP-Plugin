package de.zfzfg.pvpwager.models;

import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.UUID;

public class Wager {
    private UUID playerId;
    private List<ItemStack> items;
    private double money;
    
    public Wager(UUID playerId, List<ItemStack> items, double money) {
        this.playerId = playerId;
        this.items = items;
        this.money = money;
    }
    
    // Getters and setters
    public UUID getPlayerId() { return playerId; }
    public List<ItemStack> getItems() { return items; }
    public double getMoney() { return money; }
    
    public void setItems(List<ItemStack> items) { this.items = items; }
    public void setMoney(double money) { this.money = money; }
}