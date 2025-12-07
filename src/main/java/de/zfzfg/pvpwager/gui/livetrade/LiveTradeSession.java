package de.zfzfg.pvpwager.gui.livetrade;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Arena;
import de.zfzfg.pvpwager.models.EquipmentSet;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Live Trade Session - Verbindet zwei Spieler in einem synchronisierten Trade-GUI.
 * Beide Spieler sehen das gleiche GUI in Echtzeit mit Items, Geld, Arena und Equipment.
 */
public class LiveTradeSession {
    
    private final UUID sessionId;
    private final EventPlugin plugin;
    private final LiveTradePlayer player1;
    private final LiveTradePlayer player2;
    private final long creationTime;
    
    // Original-Locations für Rückteleport
    private final Location player1OriginalLocation;
    private final Location player2OriginalLocation;
    
    // Gemeinsame Auswahl (Mitte des GUIs)
    private Arena selectedArena;
    private EquipmentSet selectedEquipment;
    
    // Session-Status
    private volatile boolean ended = false;
    private volatile boolean confirmed = false;
    
    // Countdown wenn beide bestätigt haben
    private final AtomicInteger countdown = new AtomicInteger(-1);
    private final AtomicBoolean countdownActive = new AtomicBoolean(false);
    private BukkitTask countdownTask;
    
    // Update-Lock für Thread-Sicherheit
    private final Object updateLock = new Object();
    
    // Referenz auf prep time für Timeout-Handling
    private volatile long prepTime;
    
    // Referenz auf Manager für Cleanup
    private LiveTradeManager manager;
    
    /**
     * Holt eine Nachricht aus der Config.
     */
    private String getMsg(String key) {
        return plugin.getCoreConfigManager().getMessages().getString("messages.livetrade." + key, key);
    }
    
    /**
     * Holt eine Nachricht aus der Config mit Platzhalter-Ersetzung.
     */
    private String getMsg(String key, String placeholder, String value) {
        return getMsg(key).replace(placeholder, value);
    }
    
    public LiveTradeSession(EventPlugin plugin, Player p1, Player p2) {
        this.sessionId = UUID.randomUUID();
        this.plugin = plugin;
        this.creationTime = System.currentTimeMillis();
        this.prepTime = System.currentTimeMillis();
        
        // Original-Locations speichern BEVOR irgendwas passiert
        this.player1OriginalLocation = p1.getLocation().clone();
        this.player2OriginalLocation = p2.getLocation().clone();
        
        // Trade Players erstellen
        this.player1 = new LiveTradePlayer(this, p1);
        this.player2 = new LiveTradePlayer(this, p2);
        
        // Gegenseitige Referenzen setzen
        this.player1.setOtherPlayer(this.player2);
        this.player2.setOtherPlayer(this.player1);
    }
    
    /**
     * Startet die Wager-Session und öffnet GUIs für beide Spieler.
     */
    public void start() {
        if (ended) return;
        
        // GUIs erstellen und öffnen
        player1.createAndOpenGui();
        player2.createAndOpenGui();
        
        MessageUtil.sendMessage(player1.getPlayer(), getMsg("session-started", "%player%", player2.getPlayer().getName()));
        MessageUtil.sendMessage(player2.getPlayer(), getMsg("session-started", "%player%", player1.getPlayer().getName()));
    }
    
    /**
     * Aktualisiert beide GUIs - wird bei jeder Änderung aufgerufen.
     */
    public void update() {
        if (ended) return;
        
        synchronized (updateLock) {
            prepTime = System.currentTimeMillis();
            
            // GUIs aktualisieren
            if (player1.getGui() != null) {
                player1.getGui().update();
            }
            if (player2.getGui() != null) {
                player2.getGui().update();
            }
            
            // Prüfen ob beide bestätigt haben
            checkBothConfirmed();
        }
    }
    
    /**
     * Prüft ob beide Spieler bestätigt haben und startet ggf. Countdown.
     */
    private void checkBothConfirmed() {
        if (player1.hasConfirmed() && player2.hasConfirmed()) {
            if (!countdownActive.get()) {
                startCountdown();
            }
        } else {
            // Einer hat Bestätigung zurückgezogen
            if (countdownActive.get()) {
                cancelCountdown();
            }
        }
    }
    
    /**
     * Startet den 5-Sekunden-Countdown.
     */
    private void startCountdown() {
        if (countdownActive.compareAndSet(false, true)) {
            countdown.set(5);
            
            broadcast(getMsg("broadcast-divider"));
            broadcast(getMsg("broadcast-both-ready"));
            broadcast(getMsg("broadcast-divider"));
            
            countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (ended || !countdownActive.get()) {
                    if (countdownTask != null) countdownTask.cancel();
                    return;
                }
                
                int current = countdown.decrementAndGet();
                
                if (current <= 0) {
                    // Countdown fertig - Match starten!
                    countdownTask.cancel();
                    completeAndStartMatch();
                } else {
                    // Countdown-Sound und Update
                    playCountdownSound(current);
                    update();
                }
            }, 20L, 20L); // Jede Sekunde
        }
    }
    
    /**
     * Bricht den Countdown ab (wenn jemand Änderungen macht).
     */
    public void cancelCountdown() {
        if (countdownActive.compareAndSet(true, false)) {
            countdown.set(-1);
            if (countdownTask != null) {
                countdownTask.cancel();
                countdownTask = null;
            }
            
            // Reset Confirmations
            player1.setConfirmed(false);
            player2.setConfirmed(false);
            
            broadcast(getMsg("broadcast-change-detected"));
            
            // Sound spielen
            player1.getPlayer().playSound(player1.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            player2.getPlayer().playSound(player2.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            
            update();
        }
    }
    
    /**
     * Spielt Countdown-Sound für beide Spieler.
     */
    private void playCountdownSound(int seconds) {
        float pitch = seconds <= 3 ? 1.5f : 1.0f;
        player1.getPlayer().playSound(player1.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch);
        player2.getPlayer().playSound(player2.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch);
    }
    
    /**
     * Schließt die Session ab und startet das Match.
     */
    private void completeAndStartMatch() {
        if (ended) return;
        ended = true;
        confirmed = true;
        
        broadcast("");
        broadcast(getMsg("broadcast-divider"));
        broadcast(getMsg("broadcast-match-starting"));
        broadcast(getMsg("broadcast-divider"));
        broadcast("");
        
        // GUIs schließen
        closeGuis();
        
        // Session aus Manager entfernen BEVOR Match startet
        removeFromManager();
        
        // Match über MatchManager starten
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Request erstellen mit den gesammelten Daten
                de.zfzfg.pvpwager.models.CommandRequest request = new de.zfzfg.pvpwager.models.CommandRequest(
                    player1.getPlayer(), 
                    player2.getPlayer()
                );
                
                // Original-Locations setzen
                request.setSenderOriginalLocation(player1OriginalLocation);
                request.setTargetOriginalLocation(player2OriginalLocation);
                
                // Wager-Daten setzen
                request.setWagerItems(player1.getWagerItems());
                request.setTargetWagerItems(player2.getWagerItems());
                request.setMoney(player1.getWagerMoney());
                request.setTargetWagerMoney(player2.getWagerMoney());
                
                // Arena und Equipment
                if (selectedArena != null) {
                    request.setFinalArenaId(selectedArena.getId());
                }
                if (selectedEquipment != null) {
                    request.setFinalEquipmentId(selectedEquipment.getId());
                }
                
                // Match starten
                plugin.getMatchManager().startMatchFromCommand(request);
                
            } catch (Exception e) {
                plugin.getLogger().severe("Fehler beim Starten des Matches aus LiveTrade: " + e.getMessage());
                e.printStackTrace();
                
                // Items zurückgeben bei Fehler
                returnAllItems();
                broadcast(getMsg("broadcast-error-match-start"));
            }
        });
    }
    
    /**
     * Bricht die Session ab und gibt alle Items zurück.
     */
    public void abort() {
        abort(true);
    }
    
    public void abort(boolean returnItems) {
        if (ended) return;
        ended = true;
        
        // Countdown abbrechen
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        
        // GUIs schließen
        closeGuis();
        
        // Items zurückgeben
        if (returnItems) {
            returnAllItems();
        }
        
        broadcast(getMsg("broadcast-cancelled"));
        
        // Session aus Manager entfernen
        removeFromManager();
    }
    
    /**
     * Gibt alle Items an die jeweiligen Spieler zurück.
     */
    private void returnAllItems() {
        player1.returnItems();
        player2.returnItems();
        
        // Geld wurde noch nicht abgezogen, nichts zurückzugeben
    }
    
    /**
     * Schließt die GUIs beider Spieler.
     */
    private void closeGuis() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player1.getPlayer().isOnline()) {
                player1.getPlayer().closeInventory();
            }
            if (player2.getPlayer().isOnline()) {
                player2.getPlayer().closeInventory();
            }
        });
    }
    
    /**
     * Sendet eine Nachricht an beide Spieler.
     */
    public void broadcast(String message) {
        String colored = MessageUtil.color(message);
        if (player1.getPlayer().isOnline()) {
            player1.getPlayer().sendMessage(colored);
        }
        if (player2.getPlayer().isOnline()) {
            player2.getPlayer().sendMessage(colored);
        }
    }
    
    // === Arena & Equipment Selection ===
    
    public void setSelectedArena(Arena arena) {
        synchronized (updateLock) {
            this.selectedArena = arena;
            // Änderung -> Countdown abbrechen
            cancelCountdown();
            update();
        }
    }
    
    public Arena getSelectedArena() {
        return selectedArena;
    }
    
    public void setSelectedEquipment(EquipmentSet equipment) {
        synchronized (updateLock) {
            this.selectedEquipment = equipment;
            // Änderung -> Countdown abbrechen
            cancelCountdown();
            update();
        }
    }
    
    public EquipmentSet getSelectedEquipment() {
        return selectedEquipment;
    }
    
    /**
     * Prüft ob Arena und Equipment ausgewählt wurden.
     */
    public boolean isConfigurationComplete() {
        return selectedArena != null && selectedEquipment != null;
    }
    
    // === Getters ===
    
    public UUID getSessionId() {
        return sessionId;
    }
    
    public EventPlugin getPlugin() {
        return plugin;
    }
    
    public LiveTradePlayer getPlayer1() {
        return player1;
    }
    
    public LiveTradePlayer getPlayer2() {
        return player2;
    }
    
    public LiveTradePlayer getTradePlayer(Player player) {
        if (player1.getPlayer().getUniqueId().equals(player.getUniqueId())) {
            return player1;
        }
        if (player2.getPlayer().getUniqueId().equals(player.getUniqueId())) {
            return player2;
        }
        return null;
    }
    
    public Player getOtherPlayer(Player player) {
        if (player1.getPlayer().equals(player)) return player2.getPlayer();
        if (player2.getPlayer().equals(player)) return player1.getPlayer();
        return null;
    }
    
    public long getCreationTime() {
        return creationTime;
    }
    
    public long getPrepTime() {
        return prepTime;
    }
    
    public boolean isEnded() {
        return ended;
    }
    
    public boolean isConfirmed() {
        return confirmed;
    }
    
    public int getCountdown() {
        return countdown.get();
    }
    
    public boolean isCountdownActive() {
        return countdownActive.get();
    }
    
    public Location getPlayer1OriginalLocation() {
        return player1OriginalLocation;
    }
    
    public Location getPlayer2OriginalLocation() {
        return player2OriginalLocation;
    }
    
    /**
     * Prüft ob die Session abgelaufen ist (5 Minuten Timeout).
     */
    public boolean isExpired() {
        return System.currentTimeMillis() - prepTime > 5 * 60 * 1000;
    }
    
    /**
     * Setzt den Manager für Cleanup-Callbacks.
     */
    public void setManager(LiveTradeManager manager) {
        this.manager = manager;
    }
    
    /**
     * Entfernt diese Session aus dem Manager.
     */
    private void removeFromManager() {
        if (manager != null) {
            manager.removeSession(this);
        }
    }
}
