package de.zfzfg.pvpwager.gui;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener für alle GUI-Events.
 * Behandelt Klicks, Drag-Events und Schließen von GUIs.
 */
public class GuiListener implements Listener {
    
    private final EventPlugin plugin;
    
    public GuiListener(EventPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        
        // Prüfe ob Spieler ein aktives GUI hat
        AbstractWagerGui gui = plugin.getGuiManager().getActiveGui(player);
        if (gui == null) return;
        
        // Prüfe ob es das richtige Inventar ist
        if (!event.getInventory().equals(gui.getInventory())) return;
        
        // Verarbeite den Klick
        boolean handled = gui.handleClick(event);
        
        // Cancel Event wenn vom GUI verarbeitet
        if (handled) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        
        // Prüfe ob Spieler ein aktives GUI hat
        AbstractWagerGui gui = plugin.getGuiManager().getActiveGui(player);
        if (gui == null) return;
        
        // Prüfe ob es das richtige Inventar ist
        if (!event.getInventory().equals(gui.getInventory())) return;
        
        // Drag-Events in GUIs immer canceln
        event.setCancelled(true);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        Player player = (Player) event.getPlayer();
        
        // Prüfe ob Spieler ein aktives GUI hat
        AbstractWagerGui gui = plugin.getGuiManager().getActiveGui(player);
        if (gui == null) return;
        
        // Prüfe ob es das richtige Inventar ist
        if (!event.getInventory().equals(gui.getInventory())) return;
        
        // Wenn GUI noch nicht als geschlossen markiert, wurde es vom Spieler geschlossen
        if (!gui.isClosed()) {
            gui.onClose();
            gui.setClosed(true);
            
            // Session prüfen - wenn nicht bestätigt und nicht bereits gecancelt, Session abbrechen
            WagerSession session = plugin.getGuiManager().getSession(player);
            if (session != null && !session.isConfirmed() && !session.isCancelled()) {
                // Items zurückgeben
                returnSessionItems(player, session);
                
                // Session entfernen
                plugin.getGuiManager().removeSession(player);
                
                // Spieler informieren (verzögert, damit nicht während GUI-Close)
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        de.zfzfg.pvpwager.utils.MessageUtil.sendMessage(player, "&cWager-Anfrage abgebrochen.");
                    }
                }, 1L);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Session aufräumen
        WagerSession session = plugin.getGuiManager().getSession(player);
        if (session != null) {
            // Items zurückgeben bevor der Spieler ausloggt
            returnSessionItems(player, session);
            plugin.getGuiManager().removeSession(player);
        }
    }
    
    /**
     * Gibt alle Items einer Session an den Spieler zurück.
     */
    private void returnSessionItems(Player player, WagerSession session) {
        if (session.isCancelled()) return;
        
        // Items zurückgeben
        for (org.bukkit.inventory.ItemStack item : session.getWagerItems()) {
            if (item != null && !item.getType().isAir()) {
                player.getInventory().addItem(item.clone());
            }
        }
        
        // Geld wurde noch nicht abgezogen (erst bei Match-Start)
        
        session.cancel();
    }
}
