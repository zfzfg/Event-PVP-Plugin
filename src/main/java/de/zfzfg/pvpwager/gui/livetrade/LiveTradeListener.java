package de.zfzfg.pvpwager.gui.livetrade;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

/**
 * Listener für Live Trade GUI Events.
 */
public class LiveTradeListener implements Listener {
    
    private final EventPlugin plugin;
    private final LiveTradeManager manager;
    
    public LiveTradeListener(EventPlugin plugin, LiveTradeManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        LiveTradeSession session = manager.getSession(player);
        
        if (session == null || session.isEnded()) return;
        
        // Prüfe ob das geklickte Inventar das Trade-GUI ist
        LiveTradePlayer tradePlayer = session.getTradePlayer(player);
        if (tradePlayer == null) return;
        
        LiveTradeGui gui = tradePlayer.getGui();
        if (gui == null) return;
        
        Inventory clickedInv = event.getClickedInventory();
        Inventory guiInv = gui.getInventory();
        
        // Prüfe ob es unser GUI ist
        if (event.getView().getTopInventory().equals(guiInv)) {
            gui.handleClick(event);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        LiveTradeSession session = manager.getSession(player);
        
        if (session == null || session.isEnded()) return;
        
        LiveTradePlayer tradePlayer = session.getTradePlayer(player);
        if (tradePlayer == null) return;
        
        LiveTradeGui gui = tradePlayer.getGui();
        if (gui == null) return;
        
        Inventory guiInv = gui.getInventory();
        
        // Prüfe ob in unser GUI gedraggt wird
        if (event.getView().getTopInventory().equals(guiInv)) {
            gui.handleDrag(event);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        Player player = (Player) event.getPlayer();
        LiveTradeSession session = manager.getSession(player);
        
        if (session == null || session.isEnded()) return;
        
        LiveTradePlayer tradePlayer = session.getTradePlayer(player);
        if (tradePlayer == null) return;
        
        LiveTradeGui gui = tradePlayer.getGui();
        if (gui == null) return;
        
        Inventory guiInv = gui.getInventory();
        
        // Prüfe ob unser GUI geschlossen wurde
        if (event.getInventory().equals(guiInv)) {
            gui.handleClose(event);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.handlePlayerQuit(event.getPlayer());
    }
}
