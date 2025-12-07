package de.zfzfg.pvpwager.gui;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.CommandRequest;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GUI für Gegenangebot - Item und Geld Auswahl kombiniert.
 * Ermöglicht das Anpassen des eigenen Einsatzes für ein Gegenangebot.
 */
public class CounterOfferItemGui extends AbstractWagerGui {
    
    private static final int SIZE = 54;
    
    // Layout - 6 Zeilen
    // Zeile 0: Info
    private static final int INFO_SLOT = 4;
    private static final int CLEAR_ITEMS_SLOT = 0;
    private static final int CLEAR_MONEY_SLOT = 8;
    
    // Zeile 1: Ausgewählte Items (7 Slots)
    private static final int SELECTED_ITEMS_START = 10;
    private static final int SELECTED_ITEMS_END = 16;
    
    // Zeile 2: Geld-Auswahl
    private static final int MONEY_DISPLAY_SLOT = 22;
    private static final int[] MONEY_BUTTONS = {19, 20, 21, 23, 24, 25};
    private static final double[] MONEY_AMOUNTS = {-100, -10, -1, 1, 10, 100};
    
    // Zeile 3-4: Spieler-Inventar Vorschau
    private static final int PLAYER_INV_START = 27;
    
    // Zeile 5: Navigation
    private static final int BACK_SLOT = 45;
    private static final int SEND_SLOT = 53;
    
    private final CommandRequest request;
    private final boolean isSender;
    
    // Temporäre Werte für Gegenangebot
    private List<ItemStack> counterItems;
    private double counterMoney;
    
    public CounterOfferItemGui(EventPlugin plugin, Player player, WagerSession session, 
                               CommandRequest request, boolean isSender) {
        super(plugin, player, session);
        this.request = request;
        this.isSender = isSender;
        
        // Kopiere aktuelle Werte als Startpunkt
        if (isSender) {
            this.counterItems = new ArrayList<>(request.getWagerItems());
            this.counterMoney = request.getMoney();
        } else {
            this.counterItems = new ArrayList<>(request.getTargetWagerItems());
            this.counterMoney = request.getTargetWagerMoney();
        }
    }
    
    @Override
    public void open() {
        inventory = Bukkit.createInventory(null, SIZE,
            MessageUtil.color("&6&lGegenangebot erstellen"));
        
        buildLayout();
        openInventory();
    }
    
    private void buildLayout() {
        // Hintergrund
        fillBorder(Material.ORANGE_STAINED_GLASS_PANE);
        
        // Info-Bereich
        createInfoSection();
        
        // Ausgewählte Items
        updateSelectedItems();
        
        // Geld-Bereich
        createMoneySection();
        
        // Spieler-Inventar Vorschau
        createPlayerInventoryPreview();
        
        // Navigation
        createNavigationButtons();
    }
    
    private void createInfoSection() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(MessageUtil.color("&7Passe deinen Einsatz an,"));
        lore.add(MessageUtil.color("&7um ein Gegenangebot zu senden."));
        lore.add("");
        lore.add(MessageUtil.color("&eKlicke Items in deinem"));
        lore.add(MessageUtil.color("&eInventar um sie hinzuzufügen."));
        
        inventory.setItem(INFO_SLOT, createButton(Material.PAPER,
            "&e&lGegenangebot", lore));
        
        // Clear Items Button
        inventory.setItem(CLEAR_ITEMS_SLOT, createButton(Material.BARRIER,
            "&c&l✖ Items leeren",
            "&7Entfernt alle ausgewählten Items"));
        
        // Clear Money Button
        inventory.setItem(CLEAR_MONEY_SLOT, createButton(Material.COAL_BLOCK,
            "&c&l✖ Geld auf 0",
            "&7Setzt den Geldbetrag auf 0"));
    }
    
    private void updateSelectedItems() {
        // Ausgewählte Items anzeigen (Slots 10-16)
        for (int i = 0; i < 7; i++) {
            int slot = SELECTED_ITEMS_START + i;
            if (i < counterItems.size()) {
                ItemStack display = counterItems.get(i).clone();
                // Lore hinzufügen für Entfernen-Hinweis
                ItemMeta meta = display.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    lore.add("");
                    lore.add(MessageUtil.color("&cRechtsklick zum Entfernen"));
                    meta.setLore(lore);
                    display.setItemMeta(meta);
                }
                inventory.setItem(slot, display);
            } else {
                inventory.setItem(slot, createFillerItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE));
            }
        }
    }
    
    private void createMoneySection() {
        // Geld-Anzeige
        updateMoneyDisplay();
        
        // Geld-Buttons
        Material[] buttonMats = {
            Material.RED_WOOL, Material.ORANGE_WOOL, Material.YELLOW_WOOL,
            Material.LIME_WOOL, Material.GREEN_WOOL, Material.EMERALD_BLOCK
        };
        String[] buttonNames = {"-100", "-10", "-1", "+1", "+10", "+100"};
        
        for (int i = 0; i < MONEY_BUTTONS.length; i++) {
            String prefix = MONEY_AMOUNTS[i] < 0 ? "&c" : "&a";
            inventory.setItem(MONEY_BUTTONS[i], createButton(buttonMats[i],
                prefix + buttonNames[i] + "$",
                "&7Klicke zum Ändern"));
        }
    }
    
    private void updateMoneyDisplay() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(MessageUtil.color("&7Aktueller Einsatz:"));
        lore.add(MessageUtil.color("&6&l$" + String.format("%,.2f", counterMoney)));
        lore.add("");
        if (plugin.hasEconomy()) {
            double balance = plugin.getEconomy().getBalance(player);
            lore.add(MessageUtil.color("&7Dein Guthaben: &f$" + String.format("%,.2f", balance)));
        }
        
        Material mat = counterMoney > 0 ? Material.GOLD_BLOCK : Material.COAL_BLOCK;
        inventory.setItem(MONEY_DISPLAY_SLOT, createButton(mat,
            "&6&lGeld-Einsatz", lore));
    }
    
    private void createPlayerInventoryPreview() {
        // Zeige Spieler-Inventar als Vorschau (erste 18 Slots)
        ItemStack[] playerInv = player.getInventory().getContents();
        
        for (int i = 0; i < 18; i++) {
            int slot = PLAYER_INV_START + i;
            if (i < playerInv.length && playerInv[i] != null && !playerInv[i].getType().isAir()) {
                // Prüfe ob Item bereits ausgewählt ist
                ItemStack display = playerInv[i].clone();
                boolean alreadySelected = isItemSelected(playerInv[i], i);
                
                if (alreadySelected) {
                    ItemMeta meta = display.getItemMeta();
                    if (meta != null) {
                        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                        lore.add("");
                        lore.add(MessageUtil.color("&a✓ Bereits ausgewählt"));
                        meta.setLore(lore);
                        display.setItemMeta(meta);
                    }
                }
                
                inventory.setItem(slot, display);
            } else {
                inventory.setItem(slot, createFillerItem(Material.BLACK_STAINED_GLASS_PANE));
            }
        }
    }
    
    private boolean isItemSelected(ItemStack item, int invSlot) {
        // Vereinfachte Prüfung - nur Material und Menge vergleichen
        for (ItemStack selected : counterItems) {
            if (selected.isSimilar(item)) {
                return true;
            }
        }
        return false;
    }
    
    private void createNavigationButtons() {
        // Zurück zum Verhandlungs-GUI
        inventory.setItem(BACK_SLOT, createButton(Material.ARROW,
            "&c&l← Abbrechen",
            "&7Zurück ohne Änderungen"));
        
        // Gegenangebot senden
        List<String> sendLore = new ArrayList<>();
        sendLore.add("");
        sendLore.add(MessageUtil.color("&7Items: &f" + counterItems.size()));
        sendLore.add(MessageUtil.color("&7Geld: &6$" + String.format("%,.2f", counterMoney)));
        sendLore.add("");
        sendLore.add(MessageUtil.color("&aSendet das Gegenangebot!"));
        
        inventory.setItem(SEND_SLOT, createButton(Material.EMERALD,
            "&a&l➤ SENDEN", sendLore));
    }
    
    @Override
    public boolean handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        Inventory clickedInv = event.getClickedInventory();
        
        // Klick im Spieler-Inventar (unterer Bereich)
        if (clickedInv != null && clickedInv.equals(player.getInventory())) {
            handlePlayerInventoryClick(event);
            return true;
        }
        
        // Klick im GUI
        if (slot < 0 || slot >= SIZE) return true;
        
        // Clear Buttons
        if (slot == CLEAR_ITEMS_SLOT) {
            clearItems();
            return true;
        }
        
        if (slot == CLEAR_MONEY_SLOT) {
            clearMoney();
            return true;
        }
        
        // Ausgewählte Items - Rechtsklick zum Entfernen
        if (slot >= SELECTED_ITEMS_START && slot <= SELECTED_ITEMS_END) {
            if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
                removeSelectedItem(slot - SELECTED_ITEMS_START);
            }
            return true;
        }
        
        // Geld-Buttons
        for (int i = 0; i < MONEY_BUTTONS.length; i++) {
            if (slot == MONEY_BUTTONS[i]) {
                adjustMoney(MONEY_AMOUNTS[i]);
                return true;
            }
        }
        
        // Spieler-Inventar Vorschau Klick
        if (slot >= PLAYER_INV_START && slot < PLAYER_INV_START + 18) {
            int invIndex = slot - PLAYER_INV_START;
            addItemFromPreview(invIndex);
            return true;
        }
        
        // Navigation
        if (slot == BACK_SLOT) {
            goBack();
            return true;
        }
        
        if (slot == SEND_SLOT) {
            sendCounterOffer();
            return true;
        }
        
        return true;
    }
    
    private void handlePlayerInventoryClick(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        
        playClickSound();
        
        // Item hinzufügen (mit Stacking)
        if (!tryAddItem(clicked.clone())) {
            playErrorSound();
            MessageUtil.sendMessage(player, "&cMaximal 7 verschiedene Items!");
        } else {
            updateSelectedItems();
            createNavigationButtons();
        }
    }
    
    private void addItemFromPreview(int invIndex) {
        ItemStack[] playerInv = player.getInventory().getContents();
        if (invIndex >= playerInv.length || playerInv[invIndex] == null) return;
        
        ItemStack clicked = playerInv[invIndex];
        if (clicked.getType().isAir()) return;
        
        playClickSound();
        
        if (!tryAddItem(clicked.clone())) {
            playErrorSound();
            MessageUtil.sendMessage(player, "&cMaximal 7 verschiedene Items!");
        } else {
            updateSelectedItems();
            createPlayerInventoryPreview();
            createNavigationButtons();
        }
    }
    
    private boolean tryAddItem(ItemStack item) {
        // Versuche zu stacken
        for (int i = 0; i < counterItems.size(); i++) {
            ItemStack existing = counterItems.get(i);
            if (canStackWith(existing, item)) {
                int newAmount = Math.min(existing.getAmount() + item.getAmount(), existing.getMaxStackSize());
                existing.setAmount(newAmount);
                return true;
            }
        }
        
        // Neuen Slot verwenden
        if (counterItems.size() < 7) {
            counterItems.add(item);
            return true;
        }
        
        return false;
    }
    
    private boolean canStackWith(ItemStack a, ItemStack b) {
        if (a.getType() != b.getType()) return false;
        if (a.getAmount() >= a.getMaxStackSize()) return false;
        
        ItemMeta metaA = a.getItemMeta();
        ItemMeta metaB = b.getItemMeta();
        
        if (metaA == null && metaB == null) return true;
        if (metaA == null || metaB == null) return false;
        
        // Vergleiche wichtige Meta-Eigenschaften (ohne Lore die wir hinzugefügt haben)
        if (metaA.hasDisplayName() != metaB.hasDisplayName()) return false;
        if (metaA.hasDisplayName() && !metaA.getDisplayName().equals(metaB.getDisplayName())) return false;
        if (!metaA.getEnchants().equals(metaB.getEnchants())) return false;
        
        return true;
    }
    
    private void removeSelectedItem(int index) {
        if (index < 0 || index >= counterItems.size()) return;
        
        playClickSound();
        counterItems.remove(index);
        updateSelectedItems();
        createNavigationButtons();
    }
    
    private void clearItems() {
        playClickSound();
        counterItems.clear();
        updateSelectedItems();
        createNavigationButtons();
    }
    
    private void adjustMoney(double amount) {
        playClickSound();
        
        double newAmount = counterMoney + amount;
        
        // Minimum 0
        if (newAmount < 0) newAmount = 0;
        
        // Maximum = Spieler-Guthaben
        if (plugin.hasEconomy()) {
            double balance = plugin.getEconomy().getBalance(player);
            if (newAmount > balance) {
                newAmount = balance;
            }
        }
        
        counterMoney = newAmount;
        updateMoneyDisplay();
        createNavigationButtons();
    }
    
    private void clearMoney() {
        playClickSound();
        counterMoney = 0;
        updateMoneyDisplay();
        createNavigationButtons();
    }
    
    private void goBack() {
        playClickSound();
        // Zurück zum Verhandlungs-GUI ohne Änderungen
        switchTo(new NegotiationGui(plugin, player, session, request));
    }
    
    private void sendCounterOffer() {
        Player opponent = isSender ? request.getTarget() : request.getSender();
        
        if (opponent == null || !opponent.isOnline()) {
            playErrorSound();
            MessageUtil.sendMessage(player, "&cDer andere Spieler ist nicht mehr online!");
            return;
        }
        
        // Geld validieren
        if (counterMoney > 0 && plugin.hasEconomy()) {
            if (!plugin.getEconomy().has(player, counterMoney)) {
                playErrorSound();
                MessageUtil.sendMessage(player, "&cNicht genug Geld!");
                return;
            }
        }
        
        // Items aus Spieler-Inventar entfernen
        List<ItemStack> removedItems = new ArrayList<>();
        for (ItemStack item : counterItems) {
            // Prüfen ob Spieler Item hat
            if (!player.getInventory().containsAtLeast(item, item.getAmount())) {
                // Items zurückgeben die wir schon entfernt haben
                for (ItemStack removed : removedItems) {
                    player.getInventory().addItem(removed);
                }
                playErrorSound();
                MessageUtil.sendMessage(player, "&cDu hast nicht alle Items!");
                return;
            }
            
            // Item entfernen
            player.getInventory().removeItem(item);
            removedItems.add(item.clone());
        }
        
        // Update Request mit neuen Werten
        if (isSender) {
            // Sender ändert seinen Einsatz - alte Items zurückgeben
            for (ItemStack oldItem : request.getWagerItems()) {
                if (oldItem != null && !oldItem.getType().isAir()) {
                    player.getInventory().addItem(oldItem.clone());
                }
            }
            request.updateSenderWager(removedItems, counterMoney);
        } else {
            // Target ändert seinen Einsatz - alte Items zurückgeben
            for (ItemStack oldItem : request.getTargetWagerItems()) {
                if (oldItem != null && !oldItem.getType().isAir()) {
                    player.getInventory().addItem(oldItem.clone());
                }
            }
            request.setTargetResponse(removedItems, counterMoney, null, null);
        }
        
        // Setze Flag dass auf Bestätigung gewartet wird
        request.setWaitingForConfirmation(true);
        request.setLastOfferer(player.getUniqueId());
        
        playSuccessSound();
        MessageUtil.sendMessage(player, "&aGegenangebot gesendet! Warte auf Antwort...");
        
        // Öffne Verhandlungs-GUI beim anderen Spieler
        MessageUtil.sendMessage(opponent, "");
        MessageUtil.sendMessage(opponent, "&6&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(opponent, "&e" + player.getName() + " &7hat ein &6Gegenangebot &7gesendet!");
        MessageUtil.sendMessage(opponent, "&6&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(opponent, "");
        
        // Session für Gegner erstellen/aktualisieren
        WagerSession opponentSession = plugin.getGuiManager().getOrCreateSession(opponent, player);
        
        // Öffne NegotiationGui beim Gegner
        Bukkit.getScheduler().runTask(plugin, () -> {
            new NegotiationGui(plugin, opponent, opponentSession, request).open();
        });
        
        closeInventory();
    }
    
    @Override
    public void onClose() {
        // Nichts tun - Items noch nicht aus Inventar entfernt
    }
}
