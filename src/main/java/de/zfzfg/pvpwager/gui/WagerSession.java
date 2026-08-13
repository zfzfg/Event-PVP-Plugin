package de.zfzfg.pvpwager.gui;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Arena;
import de.zfzfg.pvpwager.models.EquipmentSet;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Eine Wager-Session speichert alle Daten einer laufenden Wager-Anfrage.
 * Thread-sicher für gleichzeitige Zugriffe.
 */
public class WagerSession {
    
    private final EventPlugin plugin;
    private final UUID senderId;
    private final UUID targetId;
    private final String senderName;
    private final String targetName;
    private final long creationTime;
    
    // Lock für thread-sichere Operationen
    private final ReentrantLock lock = new ReentrantLock();
    
    // Wager-Daten
    private final List<ItemStack> wagerItems = new ArrayList<>();
    private double wagerMoney = 0.0;
    private Arena selectedArena = null;
    private EquipmentSet selectedEquipment = null;
    
    // Status
    private SessionState state = SessionState.ITEM_SELECTION;
    private boolean confirmed = false;
    private boolean cancelled = false;
    
    // Original-Items (Backup für Rollback)
    private final List<ItemStack> originalItems = new ArrayList<>();
    private double originalMoney = 0.0;
    
    public WagerSession(EventPlugin plugin, Player sender, Player target) {
        this.plugin = plugin;
        this.senderId = sender.getUniqueId();
        this.targetId = target.getUniqueId();
        this.senderName = sender.getName();
        this.targetName = target.getName();
        this.creationTime = System.currentTimeMillis();
    }
    
    // === Item Management ===
    
    /**
     * Fügt ein Item zum Wager hinzu.
     * Erstellt einen Backup für möglichen Rollback.
     * @return true wenn erfolgreich, false wenn Item ungültig
     */
    public boolean addWagerItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        
        lock.lock();
        try {
            if (cancelled) return false;
            
            // Klone das Item für sichere Speicherung
            ItemStack clone = item.clone();
            wagerItems.add(clone);
            
            // Backup für Rollback
            originalItems.add(clone.clone());
            
            return true;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Entfernt ein Item vom Wager.
     * @return das entfernte Item oder null
     */
    public ItemStack removeWagerItem(int index) {
        lock.lock();
        try {
            if (cancelled || index < 0 || index >= wagerItems.size()) return null;
            
            return wagerItems.remove(index);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Leert alle Wager-Items.
     * @return Liste der entfernten Items
     */
    public List<ItemStack> clearWagerItems() {
        lock.lock();
        try {
            List<ItemStack> removed = new ArrayList<>(wagerItems);
            wagerItems.clear();
            return removed;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Holt eine Kopie der Wager-Items.
     */
    public List<ItemStack> getWagerItems() {
        lock.lock();
        try {
            List<ItemStack> copy = new ArrayList<>();
            for (ItemStack item : wagerItems) {
                copy.add(item.clone());
            }
            return copy;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Prüft ob Items vorhanden sind.
     */
    public boolean hasWagerItems() {
        lock.lock();
        try {
            return !wagerItems.isEmpty();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Anzahl der Wager-Items.
     */
    public int getWagerItemCount() {
        lock.lock();
        try {
            return wagerItems.size();
        } finally {
            lock.unlock();
        }
    }
    
    // === Money Management ===
    
    /**
     * Setzt den Geld-Wager.
     * @return true wenn gültig, false wenn negativ
     */
    public boolean setWagerMoney(double amount) {
        if (amount < 0) return false;
        
        lock.lock();
        try {
            if (cancelled) return false;
            
            this.wagerMoney = amount;
            return true;
        } finally {
            lock.unlock();
        }
    }
    
    public double getWagerMoney() {
        lock.lock();
        try {
            return wagerMoney;
        } finally {
            lock.unlock();
        }
    }
    
    public boolean hasWagerMoney() {
        lock.lock();
        try {
            return wagerMoney > 0;
        } finally {
            lock.unlock();
        }
    }
    
    // === Arena & Equipment ===
    
    public void setSelectedArena(Arena arena) {
        lock.lock();
        try {
            this.selectedArena = arena;
        } finally {
            lock.unlock();
        }
    }
    
    public Arena getSelectedArena() {
        lock.lock();
        try {
            return selectedArena;
        } finally {
            lock.unlock();
        }
    }
    
    public void setSelectedEquipment(EquipmentSet equipment) {
        lock.lock();
        try {
            this.selectedEquipment = equipment;
        } finally {
            lock.unlock();
        }
    }
    
    public EquipmentSet getSelectedEquipment() {
        lock.lock();
        try {
            return selectedEquipment;
        } finally {
            lock.unlock();
        }
    }
    
    // === State Management ===
    
    public SessionState getState() {
        lock.lock();
        try {
            return state;
        } finally {
            lock.unlock();
        }
    }
    
    public void setState(SessionState state) {
        lock.lock();
        try {
            this.state = state;
        } finally {
            lock.unlock();
        }
    }
    
    public boolean isConfirmed() {
        lock.lock();
        try {
            return confirmed;
        } finally {
            lock.unlock();
        }
    }
    
    public void setConfirmed(boolean confirmed) {
        lock.lock();
        try {
            this.confirmed = confirmed;
        } finally {
            lock.unlock();
        }
    }
    
    public boolean isCancelled() {
        lock.lock();
        try {
            return cancelled;
        } finally {
            lock.unlock();
        }
    }
    
    // === Validation ===
    
    /**
     * Prüft ob die Session vollständig ist.
     */
    public boolean isComplete() {
        lock.lock();
        try {
            return selectedArena != null && selectedEquipment != null;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Prüft ob ein Wager (Items oder Geld) gesetzt wurde.
     */
    public boolean hasWager() {
        lock.lock();
        try {
            return !wagerItems.isEmpty() || wagerMoney > 0;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Prüft ob die Session abgelaufen ist (5 Minuten Timeout).
     */
    public boolean isExpired() {
        return System.currentTimeMillis() - creationTime > 5 * 60 * 1000; // 5 Minuten
    }
    
    // === Cleanup & Rollback ===
    
    /**
     * Bricht die Session ab und gibt alle Items zurück.
     */
    public void cancel() {
        lock.lock();
        try {
            cancelled = true;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Cleanup bei Session-Ende.
     * WICHTIG: Items sollten vorher zurückgegeben werden!
     */
    public void cleanup() {
        lock.lock();
        try {
            cancelled = true;
            wagerItems.clear();
            originalItems.clear();
            wagerMoney = 0.0;
        } finally {
            lock.unlock();
        }
    }
    
    // === Getters ===
    
    public UUID getSenderId() { return senderId; }
    public UUID getTargetId() { return targetId; }
    public String getSenderName() { return senderName; }
    public String getTargetName() { return targetName; }
    public long getCreationTime() { return creationTime; }
    public EventPlugin getPlugin() { return plugin; }
    
    public enum SessionState {
        ITEM_SELECTION,     // Items auswählen
        MONEY_SELECTION,    // Geld eingeben
        ARENA_SELECTION,    // Arena wählen
        EQUIPMENT_SELECTION,// Equipment wählen
        CONFIRMATION,       // Bestätigung
        SENT,               // Anfrage gesendet
        CANCELLED           // Abgebrochen
    }
}
