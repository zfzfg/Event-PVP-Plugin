package de.zfzfg.pvpwager.gui;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * Abstrakte Basisklasse für alle Wager-GUIs.
 * Bietet gemeinsame Funktionalität für sichere GUI-Operationen.
 */
public abstract class AbstractWagerGui {
    
    protected final EventPlugin plugin;
    protected final Player player;
    protected final WagerSession session;
    protected Inventory inventory;
    protected boolean closed = false;
    
    // Standard-Slot-Konstanten
    protected static final int BACK_BUTTON_SLOT = 45;
    protected static final int CONFIRM_BUTTON_SLOT = 53;
    protected static final int CANCEL_BUTTON_SLOT = 49;
    protected static final int INFO_SLOT = 4;
    
    public AbstractWagerGui(EventPlugin plugin, Player player, WagerSession session) {
        this.plugin = plugin;
        this.player = player;
        this.session = session;
    }
    
    /**
     * Erstellt und öffnet das GUI.
     */
    public abstract void open();
    
    /**
     * Verarbeitet einen Klick im GUI.
     * @return true wenn der Klick verarbeitet wurde (Event sollte gecancelt werden)
     */
    public abstract boolean handleClick(InventoryClickEvent event);
    
    /**
     * Wird aufgerufen wenn das GUI geschlossen wird.
     * Ermöglicht Cleanup und Item-Rückgabe.
     */
    public abstract void onClose();
    
    /**
     * Getter für das Inventar.
     */
    public Inventory getInventory() {
        return inventory;
    }
    
    /**
     * Markiert GUI als geschlossen.
     */
    public void setClosed(boolean closed) {
        this.closed = closed;
    }
    
    public boolean isClosed() {
        return closed;
    }
    
    // === Helper-Methoden für Item-Erstellung ===
    
    /**
     * Erstellt ein dekoratives Item (Glasscheibe etc.).
     */
    protected ItemStack createFillerItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * Erstellt ein Button-Item mit Name und Lore.
     */
    protected ItemStack createButton(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.color(name));
            if (lore.length > 0) {
                meta.setLore(MessageUtil.color(Arrays.asList(lore)));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * Erstellt ein Button-Item mit Name und Lore-Liste.
     */
    protected ItemStack createButton(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.color(name));
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(MessageUtil.color(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * Erstellt einen "Zurück"-Button.
     */
    protected ItemStack createBackButton() {
        return createButton(Material.ARROW, "&c« Zurück", 
            "&7Klicke um zurückzugehen");
    }
    
    /**
     * Erstellt einen "Bestätigen"-Button.
     */
    protected ItemStack createConfirmButton() {
        return createButton(Material.LIME_WOOL, "&a✔ Bestätigen", 
            "&7Klicke um fortzufahren");
    }
    
    /**
     * Erstellt einen "Abbrechen"-Button.
     */
    protected ItemStack createCancelButton() {
        return createButton(Material.RED_WOOL, "&c✖ Abbrechen", 
            "&7Klicke um abzubrechen");
    }
    
    /**
     * Füllt leere Slots mit Filler-Items.
     */
    protected void fillEmptySlots(Material filler) {
        ItemStack fillerItem = createFillerItem(filler);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, fillerItem);
            }
        }
    }
    
    /**
     * Füllt eine Reihe mit Filler-Items.
     */
    protected void fillRow(int row, Material filler) {
        ItemStack fillerItem = createFillerItem(filler);
        int start = row * 9;
        for (int i = start; i < start + 9 && i < inventory.getSize(); i++) {
            inventory.setItem(i, fillerItem);
        }
    }
    
    /**
     * Füllt die Ränder mit Filler-Items.
     */
    protected void fillBorder(Material filler) {
        ItemStack fillerItem = createFillerItem(filler);
        int rows = inventory.getSize() / 9;
        
        // Obere Reihe
        fillRow(0, filler);
        
        // Untere Reihe
        fillRow(rows - 1, filler);
        
        // Seitenränder
        for (int row = 1; row < rows - 1; row++) {
            inventory.setItem(row * 9, fillerItem);
            inventory.setItem(row * 9 + 8, fillerItem);
        }
    }
    
    /**
     * Spielt einen Klick-Sound.
     */
    protected void playClickSound() {
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }
    
    /**
     * Spielt einen Erfolgs-Sound.
     */
    protected void playSuccessSound() {
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
    }
    
    /**
     * Spielt einen Fehler-Sound.
     */
    protected void playErrorSound() {
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
    }
    
    /**
     * Öffnet das GUI thread-sicher.
     */
    protected void openInventory() {
        // Registriere GUI im Manager
        plugin.getGuiManager().registerGui(player, this);
        
        // Öffne das Inventar auf dem Hauptthread
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && !closed && !session.isCancelled()) {
                player.openInventory(inventory);
            }
        });
    }
    
    /**
     * Schließt das GUI thread-sicher.
     */
    protected void closeInventory() {
        closed = true;
        plugin.getGuiManager().unregisterGui(player);
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.closeInventory();
            }
        });
    }
    
    /**
     * Wechselt zu einem anderen GUI.
     */
    protected void switchTo(AbstractWagerGui nextGui) {
        closed = true;
        plugin.getGuiManager().unregisterGui(player);
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && !session.isCancelled()) {
                nextGui.open();
            }
        });
    }
    
    /**
     * Prüft ob ein Slot ein Filler ist.
     */
    protected boolean isFillerSlot(int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item == null) return false;
        
        ItemMeta meta = item.getItemMeta();
        return meta != null && " ".equals(meta.getDisplayName());
    }
}
