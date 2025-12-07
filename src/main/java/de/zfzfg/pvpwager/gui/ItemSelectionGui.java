package de.zfzfg.pvpwager.gui;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI zur Auswahl von Items aus dem Spieler-Inventar für den Wager.
 * 
 * Layout (6 Reihen = 54 Slots):
 * Reihe 0 (0-8):   Info-Bereich + Clear-Button
 * Reihe 1 (9-17):  Ausgewählte Items (7 Slots: 10-16)
 * Reihe 2 (18-26): Navigation (Zurück + Bestätigen)
 * Reihe 3-5 (27-53): Kopie des Spieler-Inventars
 * 
 * Der Spieler kann Items aus seinem echten Inventar unten im GUI anklicken.
 */
public class ItemSelectionGui extends AbstractWagerGui {
    
    // Layout-Konstanten
    private static final int SIZE = 54; // 6 Reihen
    
    // Oberer Bereich: Ausgewählte Items (Reihe 1)
    private static final int SELECTED_START = 10;
    private static final int SELECTED_END = 16;
    
    // Unterer Bereich: Spieler-Inventar Kopie (Reihen 3-5)
    private static final int PLAYER_INV_START = 27;
    
    // Button-Slots
    private static final int INFO_SLOT = 4;
    private static final int CLEAR_ALL_SLOT = 8;
    private static final int BACK_SLOT = 18;
    private static final int CONFIRM_SLOT = 26;
    
    public ItemSelectionGui(EventPlugin plugin, Player player, WagerSession session) {
        super(plugin, player, session);
    }
    
    @Override
    public void open() {
        inventory = Bukkit.createInventory(null, SIZE, 
            MessageUtil.color("&6&lItems auswählen"));
        
        buildLayout();
        openInventory();
    }
    
    private void buildLayout() {
        // Reihe 0: Hintergrund + Info
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, createFillerItem(Material.GRAY_STAINED_GLASS_PANE));
        }
        inventory.setItem(INFO_SLOT, createInfoItem());
        inventory.setItem(CLEAR_ALL_SLOT, createButton(Material.BARRIER, 
            "&c&lAlles Entfernen",
            "&7Entfernt alle Items",
            "&7aus dem Wager."));
        
        // Reihe 1: Ausgewählte Items
        for (int i = 9; i < 18; i++) {
            inventory.setItem(i, createFillerItem(Material.GRAY_STAINED_GLASS_PANE));
        }
        displaySelectedItems();
        
        // Reihe 2: Navigation
        for (int i = 18; i < 27; i++) {
            inventory.setItem(i, createFillerItem(Material.BLACK_STAINED_GLASS_PANE));
        }
        inventory.setItem(BACK_SLOT, createBackButton());
        inventory.setItem(CONFIRM_SLOT, createConfirmButton());
        
        // Reihen 3-5: Spieler-Inventar Kopie anzeigen
        displayPlayerInventory();
    }
    
    private ItemStack createInfoItem() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(MessageUtil.color("&7Klicke auf Items unten"));
        lore.add(MessageUtil.color("&7um sie hinzuzufügen."));
        lore.add("");
        lore.add(MessageUtil.color("&7Klicke auf ausgewählte Items"));
        lore.add(MessageUtil.color("&7um sie zu entfernen."));
        lore.add("");
        lore.add(MessageUtil.color("&eLinks-Klick: &f1 Item"));
        lore.add(MessageUtil.color("&eRechts-Klick: &fGanzer Stack"));
        
        return createButton(Material.NETHER_STAR, "&e&lAnleitung", lore);
    }
    
    private void displaySelectedItems() {
        List<ItemStack> selected = session.getWagerItems();
        
        // Alle Selected-Slots als Platzhalter
        for (int i = SELECTED_START; i <= SELECTED_END; i++) {
            inventory.setItem(i, createButton(Material.LIME_STAINED_GLASS_PANE,
                "&7Leerer Slot",
                "&7Füge Items hinzu!"));
        }
        
        // Ausgewählte Items anzeigen
        int slot = SELECTED_START;
        for (ItemStack item : selected) {
            if (slot > SELECTED_END) break;
            
            ItemStack display = item.clone();
            addRemoveLore(display);
            inventory.setItem(slot, display);
            slot++;
        }
    }
    
    private void displayPlayerInventory() {
        // Zeige Spieler-Inventar in Reihen 3-5 (Slots 27-53)
        // Spieler-Inventar: Slots 9-35 = Hauptinventar (27 Slots)
        ItemStack[] contents = player.getInventory().getContents();
        
        for (int i = 0; i < 27; i++) {
            int guiSlot = PLAYER_INV_START + i;
            int invSlot = i + 9; // Spieler-Inventar Slot 9-35
            
            if (invSlot < contents.length && contents[invSlot] != null && !contents[invSlot].getType().isAir()) {
                ItemStack display = contents[invSlot].clone();
                addClickToAddLore(display);
                inventory.setItem(guiSlot, display);
            } else {
                inventory.setItem(guiSlot, createFillerItem(Material.BLACK_STAINED_GLASS_PANE));
            }
        }
    }
    
    private void addRemoveLore(ItemStack item) {
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(MessageUtil.color("&c➤ Klicke zum Entfernen"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }
    
    private void addClickToAddLore(ItemStack item) {
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(MessageUtil.color("&a➤ Klicke zum Hinzufügen"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }
    
    @Override
    public boolean handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        ClickType clickType = event.getClick();
        
        if (slot < 0) return true;
        
        // Klick im Spieler-Inventar unten (außerhalb der GUI)
        if (slot >= SIZE) {
            handleRealPlayerInventoryClick(event);
            return true;
        }
        
        // Klick auf ausgewählte Items (Reihe 1)
        if (slot >= SELECTED_START && slot <= SELECTED_END) {
            handleSelectedItemClick(slot, clickType);
            return true;
        }
        
        // Klick auf Spieler-Inventar Kopie (Reihen 3-5)
        if (slot >= PLAYER_INV_START && slot < SIZE) {
            handleInventoryCopyClick(slot, clickType);
            return true;
        }
        
        // Buttons
        switch (slot) {
            case CLEAR_ALL_SLOT:
                handleClearAll();
                break;
            case BACK_SLOT:
                playClickSound();
                switchTo(new WagerMainGui(plugin, player, session));
                break;
            case CONFIRM_SLOT:
                playSuccessSound();
                switchTo(new WagerMainGui(plugin, player, session));
                break;
        }
        
        return true;
    }
    
    private void handleRealPlayerInventoryClick(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        
        int amount = determineAmount(event.getClick(), clicked.getAmount());
        int playerSlot = event.getSlot();
        
        addItemToWagerFromSlot(playerSlot, amount);
    }
    
    private void handleInventoryCopyClick(int guiSlot, ClickType clickType) {
        // Berechne den echten Spieler-Inventar-Slot
        int invIndex = guiSlot - PLAYER_INV_START;
        int playerSlot = invIndex + 9; // Spieler-Inventar Slot 9-35
        
        ItemStack playerItem = player.getInventory().getItem(playerSlot);
        if (playerItem == null || playerItem.getType().isAir()) return;
        
        int amount = determineAmount(clickType, playerItem.getAmount());
        addItemToWagerFromSlot(playerSlot, amount);
    }
    
    private void handleSelectedItemClick(int slot, ClickType clickType) {
        int index = slot - SELECTED_START;
        List<ItemStack> selected = session.getWagerItems();
        
        if (index < 0 || index >= selected.size()) return;
        
        ItemStack removed = session.removeWagerItem(index);
        if (removed != null) {
            player.getInventory().addItem(removed.clone());
            playClickSound();
            refreshDisplay();
        }
    }
    
    private int determineAmount(ClickType clickType, int maxAmount) {
        switch (clickType) {
            case LEFT:
                return 1;
            case RIGHT:
                return maxAmount;
            case SHIFT_LEFT:
            case SHIFT_RIGHT:
                return Math.max(1, maxAmount / 2);
            default:
                return 1;
        }
    }
    
    private void addItemToWagerFromSlot(int playerSlot, int amount) {
        ItemStack playerItem = player.getInventory().getItem(playerSlot);
        if (playerItem == null || playerItem.getType().isAir()) return;
        
        int available = playerItem.getAmount();
        amount = Math.min(amount, available);
        
        if (amount <= 0) return;
        
        // Prüfe ob noch Platz ist
        if (session.getWagerItemCount() >= 7) {
            playErrorSound();
            MessageUtil.sendMessage(player, "&cMaximal 7 Item-Stacks erlaubt!");
            return;
        }
        
        // Item zum Wager hinzufügen (OHNE Stacking - einfacher und sicherer)
        ItemStack toAdd = playerItem.clone();
        toAdd.setAmount(amount);
        session.addWagerItem(toAdd);
        
        // Item aus Spieler-Inventar entfernen
        if (available <= amount) {
            player.getInventory().setItem(playerSlot, null);
        } else {
            playerItem.setAmount(available - amount);
        }
        
        playClickSound();
        refreshDisplay();
    }
    
    private void handleClearAll() {
        List<ItemStack> items = session.clearWagerItems();
        
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                player.getInventory().addItem(item.clone());
            }
        }
        
        playClickSound();
        MessageUtil.sendMessage(player, "&eAlle Items entfernt.");
        refreshDisplay();
    }
    
    private void refreshDisplay() {
        displaySelectedItems();
        displayPlayerInventory();
    }
    
    @Override
    public void onClose() {
        // Items bleiben in der Session
    }
}
