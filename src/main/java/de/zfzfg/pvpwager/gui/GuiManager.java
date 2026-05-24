package de.zfzfg.pvpwager.gui;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zentrale Verwaltung aller GUI-Instanzen.
 * Sorgt für Thread-sichere Zuordnung und Cleanup.
 */
public class GuiManager {
    
    private final EventPlugin plugin;
    
    // Aktive GUI-Sessions pro Spieler
    private final Map<UUID, WagerSession> activeSessions = new ConcurrentHashMap<>();
    
    // Aktive GUI-Inventories pro Spieler (für Click-Event-Handling)
    private final Map<UUID, AbstractWagerGui> activeGuis = new ConcurrentHashMap<>();
    
    public GuiManager(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Erstellt eine neue Wager-Session für einen Spieler.
     * Vorherige Sessions werden automatisch abgebrochen.
     */
    public WagerSession createSession(Player sender, Player target) {
        UUID senderId = sender.getUniqueId();
        
        // Alte Session abbrechen
        WagerSession existing = activeSessions.remove(senderId);
        if (existing != null) {
            existing.cleanup();
        }
        
        WagerSession session = new WagerSession(plugin, sender, target);
        activeSessions.put(senderId, session);
        return session;
    }
    
    /**
     * Holt die aktive Session eines Spielers.
     */
    public WagerSession getSession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }
    
    /**
     * Holt oder erstellt eine Session für einen Spieler.
     * Falls keine Session existiert, wird eine neue erstellt.
     */
    public WagerSession getOrCreateSession(Player sender, Player target) {
        WagerSession existing = activeSessions.get(sender.getUniqueId());
        if (existing != null && !existing.isExpired() && !existing.isCancelled()) {
            return existing;
        }
        return createSession(sender, target);
    }
    
    /**
     * Holt die aktive Session per UUID.
     */
    public WagerSession getSession(UUID playerId) {
        return activeSessions.get(playerId);
    }
    
    /**
     * Entfernt eine Session.
     */
    public void removeSession(Player player) {
        WagerSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            session.cleanup();
        }
        activeGuis.remove(player.getUniqueId());
    }
    
    /**
     * Registriert ein aktives GUI für Click-Handling.
     */
    public void registerGui(Player player, AbstractWagerGui gui) {
        activeGuis.put(player.getUniqueId(), gui);
    }
    
    /**
     * Entfernt GUI-Registrierung.
     */
    public void unregisterGui(Player player) {
        activeGuis.remove(player.getUniqueId());
    }
    
    /**
     * Holt das aktive GUI eines Spielers.
     */
    public AbstractWagerGui getActiveGui(Player player) {
        return activeGuis.get(player.getUniqueId());
    }
    
    /**
     * Prüft ob ein Inventar zu einem aktiven GUI gehört.
     */
    public boolean isWagerGui(Inventory inventory) {
        if (inventory == null || inventory.getHolder() != null) return false;
        
        String title = inventory.getType().getDefaultTitle();
        // Prüfe auf unsere GUI-Titel-Präfixe
        for (AbstractWagerGui gui : activeGuis.values()) {
            if (gui != null && gui.getInventory() != null && gui.getInventory().equals(inventory)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Cleanup aller Sessions (beim Plugin-Disable).
     */
    public void cleanup() {
        for (WagerSession session : activeSessions.values()) {
            try {
                session.cleanup();
            } catch (Exception ignored) {}
        }
        activeSessions.clear();
        activeGuis.clear();
    }
    
    /**
     * Entfernt Sessions für einen Spieler der das Spiel verlässt.
     */
    public void handlePlayerQuit(Player player) {
        removeSession(player);
    }
    
    public EventPlugin getPlugin() {
        return plugin;
    }
}
