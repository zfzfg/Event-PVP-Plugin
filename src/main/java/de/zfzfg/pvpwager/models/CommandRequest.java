package de.zfzfg.pvpwager.models;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CommandRequest {
    private final Player sender;
    private final Player target;
    private List<ItemStack> senderWagerItems;
    private double senderWagerMoney;
    private String arenaId;
    private String equipmentId;
    private long timestamp;
    
    // Original Locations - gespeichert beim Request-Erstellen
    // WICHTIG: Diese Locations sind der "sichere Rückkehr-Ort" nach dem Match
    private Location senderOriginalLocation;
    private Location targetOriginalLocation;
    
    // Target's response
    private List<ItemStack> targetWagerItems;
    private double targetWagerMoney;
    private String targetArenaId;  // Optional: can override
    private String targetEquipmentId;  // Optional: can override
    private boolean targetResponded = false;
    private boolean senderConfirmed = false;
    
    // Verhandlungs-System
    private boolean waitingForConfirmation = false;
    private UUID lastOfferer = null; // Wer hat das letzte Angebot gemacht?
    private int negotiationRound = 0; // Anzahl der Verhandlungsrunden
    
    public CommandRequest(Player sender, Player target, List<ItemStack> wagerItems, 
                         double wagerMoney, String arenaId, String equipmentId) {
        this.sender = sender;
        this.target = target;
        this.senderWagerItems = new ArrayList<>(wagerItems);
        this.senderWagerMoney = wagerMoney;
        this.arenaId = arenaId;
        this.equipmentId = equipmentId;
        this.timestamp = System.currentTimeMillis();
        this.targetWagerItems = new ArrayList<>();
        this.targetWagerMoney = 0.0;
        
        // KRITISCH: Original-Locations sofort speichern!
        // Dies ist der früheste Zeitpunkt wo wir sicher sein können,
        // dass die Spieler noch in ihrer "normalen" Welt sind.
        this.senderOriginalLocation = sender.getLocation().clone();
        this.targetOriginalLocation = target.getLocation().clone();
    }
    
    /**
     * Minimaler Konstruktor für Live-Trade-System.
     * Locations und andere Daten werden später gesetzt.
     */
    public CommandRequest(Player sender, Player target) {
        this.sender = sender;
        this.target = target;
        this.senderWagerItems = new ArrayList<>();
        this.senderWagerMoney = 0.0;
        this.arenaId = null;
        this.equipmentId = null;
        this.timestamp = System.currentTimeMillis();
        this.targetWagerItems = new ArrayList<>();
        this.targetWagerMoney = 0.0;
        
        // Locations werden später über Setter gesetzt
        this.senderOriginalLocation = null;
        this.targetOriginalLocation = null;
    }
    
    public void setTargetResponse(List<ItemStack> items, double money, String arena, String equipment) {
        this.targetWagerItems = new ArrayList<>(items);
        this.targetWagerMoney = money;
        
        // Optional overrides
        if (arena != null && !arena.isEmpty()) {
            this.targetArenaId = arena;
        }
        if (equipment != null && !equipment.isEmpty()) {
            this.targetEquipmentId = equipment;
        }
        
        this.targetResponded = true;
    }
    
    // Getters
    public Player getSender() { return sender; }
    public Player getTarget() { return target; }
    public List<ItemStack> getWagerItems() { return senderWagerItems; }
    public double getMoney() { return senderWagerMoney; }
    public String getArenaId() { return arenaId; }
    public String getEquipmentId() { return equipmentId; }
    public long getTimestamp() { return timestamp; }
    
    public List<ItemStack> getTargetWagerItems() { return targetWagerItems; }
    public double getTargetWagerMoney() { return targetWagerMoney; }
    public String getFinalArenaId() { return targetArenaId != null ? targetArenaId : arenaId; }
    public String getFinalEquipmentId() { return targetEquipmentId != null ? targetEquipmentId : equipmentId; }
    
    // Original Locations - diese wurden beim Request-Erstellen gespeichert
    public Location getSenderOriginalLocation() { return senderOriginalLocation; }
    public Location getTargetOriginalLocation() { return targetOriginalLocation; }
    
    // Setter für Original-Locations (für Live-Trade-System)
    public void setSenderOriginalLocation(Location location) { 
        this.senderOriginalLocation = location != null ? location.clone() : null; 
    }
    public void setTargetOriginalLocation(Location location) { 
        this.targetOriginalLocation = location != null ? location.clone() : null; 
    }
    
    // Setter für Wager-Daten (für Live-Trade-System)
    public void setWagerItems(List<ItemStack> items) { 
        this.senderWagerItems = items != null ? new ArrayList<>(items) : new ArrayList<>(); 
    }
    public void setMoney(double money) { this.senderWagerMoney = money; }
    public void setTargetWagerItems(List<ItemStack> items) { 
        this.targetWagerItems = items != null ? new ArrayList<>(items) : new ArrayList<>(); 
    }
    public void setTargetWagerMoney(double money) { this.targetWagerMoney = money; }
    public void setFinalArenaId(String arenaId) { this.arenaId = arenaId; }
    public void setFinalEquipmentId(String equipmentId) { this.equipmentId = equipmentId; }
    
    public boolean hasTargetResponded() { return targetResponded; }
    public boolean hasSenderConfirmed() { return senderConfirmed; }
    
    // Verhandlungs-Getter und Setter
    public boolean isWaitingForConfirmation() { return waitingForConfirmation; }
    public void setWaitingForConfirmation(boolean waiting) { this.waitingForConfirmation = waiting; }
    
    public UUID getLastOfferer() { return lastOfferer; }
    public void setLastOfferer(UUID offerer) { this.lastOfferer = offerer; }
    
    public int getNegotiationRound() { return negotiationRound; }
    public void incrementNegotiationRound() { this.negotiationRound++; }
    
    /**
     * Aktualisiert den Sender-Einsatz (für Gegenangebote).
     */
    public void updateSenderWager(List<ItemStack> items, double money) {
        this.senderWagerItems = new ArrayList<>(items);
        this.senderWagerMoney = money;
        this.negotiationRound++;
    }
    
    /**
     * Prüft ob die Anfrage abgelaufen ist (60 Sekunden Timeout).
     */
    public boolean isExpired() {
        return System.currentTimeMillis() - timestamp > 60 * 1000; // 60 Sekunden
    }
    
    public void setSenderConfirmed(boolean confirmed) { this.senderConfirmed = confirmed; }
}