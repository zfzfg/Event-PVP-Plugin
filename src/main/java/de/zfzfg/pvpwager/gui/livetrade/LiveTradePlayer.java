package de.zfzfg.pvpwager.gui.livetrade;

import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Repräsentiert einen Spieler in einer LiveTrade-Session.
 * Verwaltet Items, Geld und Bestätigungsstatus.
 */
public class LiveTradePlayer {
    
    private final LiveTradeSession session;
    private final Player player;
    private final UUID playerId;
    private LiveTradePlayer otherPlayer;
    private LiveTradeGui gui;
    
    // Thread-sicheres Lock
    private final ReentrantLock lock = new ReentrantLock();
    
    // Wager-Items (max 12 Slots)
    private final List<ItemStack> wagerItems = new ArrayList<>();
    
    // Wager-Geld
    private double wagerMoney = 0.0;
    
    // Bestätigungsstatus
    private volatile boolean confirmed = false;
    
    public LiveTradePlayer(LiveTradeSession session, Player player) {
        this.session = session;
        this.player = player;
        this.playerId = player.getUniqueId();
    }
    
    public void setOtherPlayer(LiveTradePlayer otherPlayer) {
        this.otherPlayer = otherPlayer;
    }
    
    /**
     * Holt eine Nachricht aus der Config.
     */
    private String getMsg(String key) {
        return session.getPlugin().getCoreConfigManager().getMessages().getString("messages.livetrade." + key, key);
    }
    
    /**
     * Holt eine Nachricht aus der Config mit Platzhalter-Ersetzung.
     */
    private String getMsg(String key, String placeholder, String value) {
        return getMsg(key).replace(placeholder, value);
    }
    
    /**
     * Erstellt und öffnet das GUI für diesen Spieler.
     */
    public void createAndOpenGui() {
        this.gui = new LiveTradeGui(session, this);
        gui.open();
    }
    
    // === Item Management ===
    
    /**
     * Fügt ein Item zum Wager hinzu.
     * @return true wenn erfolgreich hinzugefügt
     */
    public boolean addWagerItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        
        lock.lock();
        try {
            if (wagerItems.size() >= 12) { // Max 12 Items
                MessageUtil.sendMessage(player, getMsg("max-items-reached"));
                return false;
            }
            
            wagerItems.add(item.clone());
            
            // Bestätigung zurücksetzen bei Änderung
            resetConfirmation();
            
            return true;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Entfernt ein Item vom Wager und gibt es zurück.
     */
    public ItemStack removeWagerItem(int index) {
        lock.lock();
        try {
            if (index < 0 || index >= wagerItems.size()) return null;
            
            ItemStack removed = wagerItems.remove(index);
            
            // Bestätigung zurücksetzen bei Änderung
            resetConfirmation();
            
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
     * Gibt alle Items zurück an den Spieler.
     */
    public void returnItems() {
        lock.lock();
        try {
            for (ItemStack item : wagerItems) {
                if (item != null && !item.getType().isAir()) {
                    player.getInventory().addItem(item.clone());
                }
            }
            wagerItems.clear();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Leert die Wager-Items (ohne Rückgabe).
     */
    public void clearWagerItems() {
        lock.lock();
        try {
            wagerItems.clear();
        } finally {
            lock.unlock();
        }
    }
    
    // === Money Management ===
    
    /**
     * Setzt den Geld-Wager.
     */
    public boolean setWagerMoney(double amount) {
        if (amount < 0) return false;
        
        lock.lock();
        try {
            // Prüfe ob Spieler genug Geld hat
            if (amount > 0 && session.getPlugin().hasEconomy()) {
                double balance = session.getPlugin().getEconomy().getBalance(player);
                if (balance < amount) {
                    MessageUtil.sendMessage(player, getMsg("not-enough-money", "%balance%", String.format("%.2f", balance)));
                    return false;
                }
            }
            
            this.wagerMoney = amount;
            
            // Bestätigung zurücksetzen bei Änderung
            resetConfirmation();
            
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
    
    // === Confirmation ===
    
    /**
     * Bestätigt den aktuellen Trade-Stand.
     */
    public void confirm() {
        // Prüfe ob Konfiguration vollständig
        if (!session.isConfigurationComplete()) {
            MessageUtil.sendMessage(player, getMsg("select-arena-equipment-first"));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return;
        }
        
        this.confirmed = true;
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        session.update();
    }
    
    /**
     * Nimmt die Bestätigung zurück.
     */
    public void cancelConfirmation() {
        this.confirmed = false;
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
        session.update();
    }
    
    /**
     * Setzt die Bestätigung zurück (bei Änderungen).
     */
    private void resetConfirmation() {
        if (confirmed) {
            this.confirmed = false;
        }
        // Auch die Session über Änderung informieren
        if (session != null && !session.isEnded()) {
            session.cancelCountdown();
        }
    }
    
    public boolean hasConfirmed() {
        return confirmed;
    }
    
    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }
    
    // === Getters ===
    
    public LiveTradeSession getSession() {
        return session;
    }
    
    public Player getPlayer() {
        return player;
    }
    
    public UUID getPlayerId() {
        return playerId;
    }
    
    public LiveTradePlayer getOtherPlayer() {
        return otherPlayer;
    }
    
    public LiveTradeGui getGui() {
        return gui;
    }
    
    /**
     * Prüft ob dieser Spieler einen Wager (Items oder Geld) hat.
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
     * Anzahl freier Inventory-Slots.
     */
    public int getEmptySlots() {
        int empty = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                empty++;
            }
        }
        return empty;
    }
}
