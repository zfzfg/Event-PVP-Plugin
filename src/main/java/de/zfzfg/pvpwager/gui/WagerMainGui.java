package de.zfzfg.pvpwager.gui;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Hauptmenü für PVP-Wager-Anfragen.
 * Zeigt Übersicht und navigiert zu den Untermenüs.
 */
public class WagerMainGui extends AbstractWagerGui {
    
    // Slot-Konstanten für das Hauptmenü
    private static final int TARGET_INFO_SLOT = 4;
    private static final int ITEMS_BUTTON_SLOT = 20;
    private static final int MONEY_BUTTON_SLOT = 22;
    private static final int ARENA_BUTTON_SLOT = 24;
    private static final int EQUIPMENT_BUTTON_SLOT = 30;
    private static final int OVERVIEW_SLOT = 32;
    private static final int SEND_BUTTON_SLOT = 40;
    private static final int CANCEL_SLOT = 49;
    
    public WagerMainGui(EventPlugin plugin, Player player, WagerSession session) {
        super(plugin, player, session);
    }
    
    @Override
    public void open() {
        // Erstelle 6-Reihen Inventar
        inventory = Bukkit.createInventory(null, 54, 
            MessageUtil.color("&6&lPVP Wager - &e" + session.getTargetName()));
        
        // Fülle Hintergrund
        fillBorder(Material.BLACK_STAINED_GLASS_PANE);
        
        // Target-Info
        createTargetInfo();
        
        // Haupt-Buttons
        createItemsButton();
        createMoneyButton();
        createArenaButton();
        createEquipmentButton();
        createOverview();
        createSendButton();
        
        // Abbrechen-Button
        inventory.setItem(CANCEL_SLOT, createCancelButton());
        
        // Öffne das Inventar
        openInventory();
    }
    
    private void createTargetInfo() {
        // Spielerkopf des Ziels
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        if (meta != null) {
            Player target = Bukkit.getPlayer(session.getTargetId());
            if (target != null) {
                meta.setOwningPlayer(target);
            }
            meta.setDisplayName(MessageUtil.color("&e&lHerausforderung an:"));
            meta.setLore(Arrays.asList(
                MessageUtil.color("&f" + session.getTargetName()),
                "",
                MessageUtil.color("&7Stelle deinen Einsatz zusammen"),
                MessageUtil.color("&7und wähle Arena & Ausrüstung.")
            ));
            head.setItemMeta(meta);
        }
        inventory.setItem(TARGET_INFO_SLOT, head);
    }
    
    private void createItemsButton() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (session.hasWagerItems()) {
            lore.add(MessageUtil.color("&aAusgewählte Items:"));
            int count = 0;
            for (ItemStack item : session.getWagerItems()) {
                if (count >= 5) {
                    lore.add(MessageUtil.color("&7... und " + (session.getWagerItemCount() - 5) + " weitere"));
                    break;
                }
                lore.add(MessageUtil.color("&7- &f" + formatItemName(item)));
                count++;
            }
        } else {
            lore.add(MessageUtil.color("&cKeine Items ausgewählt"));
        }
        
        lore.add("");
        lore.add(MessageUtil.color("&eKlicke zum Bearbeiten"));
        
        ItemStack button = createButton(Material.CHEST, 
            "&6&l📦 Items Einsetzen", lore);
        inventory.setItem(ITEMS_BUTTON_SLOT, button);
    }
    
    private void createMoneyButton() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (session.hasWagerMoney()) {
            lore.add(MessageUtil.color("&aAktueller Einsatz:"));
            lore.add(MessageUtil.color("&6$" + String.format("%.2f", session.getWagerMoney())));
        } else {
            lore.add(MessageUtil.color("&cKein Geld eingesetzt"));
        }
        
        lore.add("");
        
        if (plugin.hasEconomy()) {
            double balance = plugin.getEconomy().getBalance(player);
            lore.add(MessageUtil.color("&7Dein Guthaben: &6$" + String.format("%.2f", balance)));
            lore.add("");
            lore.add(MessageUtil.color("&eKlicke zum Bearbeiten"));
        } else {
            lore.add(MessageUtil.color("&c⚠ Economy nicht verfügbar"));
        }
        
        ItemStack button = createButton(Material.GOLD_INGOT, 
            "&6&l💰 Geld Einsetzen", lore);
        inventory.setItem(MONEY_BUTTON_SLOT, button);
    }
    
    private void createArenaButton() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (session.getSelectedArena() != null) {
            lore.add(MessageUtil.color("&aAusgewählt:"));
            lore.add(MessageUtil.color("&f" + session.getSelectedArena().getDisplayName()));
        } else {
            lore.add(MessageUtil.color("&c⚠ Keine Arena ausgewählt"));
            lore.add(MessageUtil.color("&7(Pflichtfeld!)"));
        }
        
        lore.add("");
        lore.add(MessageUtil.color("&eKlicke zum Auswählen"));
        
        Material material = session.getSelectedArena() != null ? 
            Material.GRASS_BLOCK : Material.BARRIER;
        
        ItemStack button = createButton(material, 
            "&6&l🗺 Arena Wählen", lore);
        inventory.setItem(ARENA_BUTTON_SLOT, button);
    }
    
    private void createEquipmentButton() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (session.getSelectedEquipment() != null) {
            lore.add(MessageUtil.color("&aAusgewählt:"));
            lore.add(MessageUtil.color("&f" + session.getSelectedEquipment().getDisplayName()));
        } else {
            lore.add(MessageUtil.color("&c⚠ Keine Ausrüstung ausgewählt"));
            lore.add(MessageUtil.color("&7(Pflichtfeld!)"));
        }
        
        lore.add("");
        lore.add(MessageUtil.color("&eKlicke zum Auswählen"));
        
        Material material = session.getSelectedEquipment() != null ? 
            Material.DIAMOND_CHESTPLATE : Material.BARRIER;
        
        ItemStack button = createButton(material, 
            "&6&l⚔ Ausrüstung Wählen", lore);
        inventory.setItem(EQUIPMENT_BUTTON_SLOT, button);
    }
    
    private void createOverview() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(MessageUtil.color("&7━━━━━━━━━━━━━━━━━━━━"));
        
        // Items
        if (session.hasWagerItems()) {
            lore.add(MessageUtil.color("&a✔ &fItems: &e" + session.getWagerItemCount() + " Stück"));
        } else {
            lore.add(MessageUtil.color("&7✗ &fItems: &cKeine"));
        }
        
        // Geld
        if (session.hasWagerMoney()) {
            lore.add(MessageUtil.color("&a✔ &fGeld: &6$" + String.format("%.2f", session.getWagerMoney())));
        } else {
            lore.add(MessageUtil.color("&7✗ &fGeld: &cKein"));
        }
        
        // Arena
        if (session.getSelectedArena() != null) {
            lore.add(MessageUtil.color("&a✔ &fArena: &e" + session.getSelectedArena().getDisplayName()));
        } else {
            lore.add(MessageUtil.color("&c✗ &fArena: &cNicht gewählt"));
        }
        
        // Equipment
        if (session.getSelectedEquipment() != null) {
            lore.add(MessageUtil.color("&a✔ &fAusrüstung: &e" + session.getSelectedEquipment().getDisplayName()));
        } else {
            lore.add(MessageUtil.color("&c✗ &fAusrüstung: &cNicht gewählt"));
        }
        
        lore.add(MessageUtil.color("&7━━━━━━━━━━━━━━━━━━━━"));
        
        // Status
        if (session.isComplete()) {
            lore.add(MessageUtil.color("&a✔ Bereit zum Senden!"));
        } else {
            lore.add(MessageUtil.color("&c⚠ Wähle Arena & Ausrüstung"));
        }
        
        ItemStack overview = createButton(Material.BOOK, 
            "&e&lÜbersicht", lore);
        inventory.setItem(OVERVIEW_SLOT, overview);
    }
    
    private void createSendButton() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        boolean canSend = session.isComplete();
        
        if (canSend) {
            lore.add(MessageUtil.color("&aAlles bereit!"));
            lore.add("");
            lore.add(MessageUtil.color("&eKlicke um die Anfrage"));
            lore.add(MessageUtil.color("&ezu senden!"));
        } else {
            lore.add(MessageUtil.color("&c⚠ Noch nicht bereit!"));
            lore.add("");
            if (session.getSelectedArena() == null) {
                lore.add(MessageUtil.color("&c- Wähle eine Arena"));
            }
            if (session.getSelectedEquipment() == null) {
                lore.add(MessageUtil.color("&c- Wähle eine Ausrüstung"));
            }
        }
        
        Material material = canSend ? Material.LIME_WOOL : Material.GRAY_WOOL;
        ItemStack button = createButton(material, 
            canSend ? "&a&l➤ ANFRAGE SENDEN" : "&7➤ Anfrage Senden", lore);
        inventory.setItem(SEND_BUTTON_SLOT, button);
    }
    
    @Override
    public boolean handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        
        // Ignoriere Klicks außerhalb des GUIs
        if (slot < 0 || slot >= inventory.getSize()) {
            return true;
        }
        
        // Ignoriere Filler
        if (isFillerSlot(slot)) {
            return true;
        }
        
        switch (slot) {
            case ITEMS_BUTTON_SLOT:
                playClickSound();
                switchTo(new ItemSelectionGui(plugin, player, session));
                break;
                
            case MONEY_BUTTON_SLOT:
                if (plugin.hasEconomy()) {
                    playClickSound();
                    switchTo(new MoneySelectionGui(plugin, player, session));
                } else {
                    playErrorSound();
                    MessageUtil.sendMessage(player, "&cEconomy ist nicht verfügbar!");
                }
                break;
                
            case ARENA_BUTTON_SLOT:
                playClickSound();
                switchTo(new ArenaSelectionGui(plugin, player, session));
                break;
                
            case EQUIPMENT_BUTTON_SLOT:
                playClickSound();
                switchTo(new EquipmentSelectionGui(plugin, player, session));
                break;
                
            case SEND_BUTTON_SLOT:
                if (session.isComplete()) {
                    playSuccessSound();
                    switchTo(new ConfirmationGui(plugin, player, session));
                } else {
                    playErrorSound();
                    MessageUtil.sendMessage(player, "&cWähle zuerst Arena und Ausrüstung!");
                }
                break;
                
            case CANCEL_SLOT:
                playClickSound();
                cancelAndClose();
                break;
        }
        
        return true;
    }
    
    @Override
    public void onClose() {
        // Bei normalem Schließen (nicht durch GUI-Wechsel):
        // Items bleiben in der Session und werden erst bei Cancel zurückgegeben
        if (!closed && !session.isCancelled()) {
            // GUI wurde vom Spieler geschlossen - Session bleibt aktiv
            // Spieler kann später weitermachen
        }
    }
    
    private void cancelAndClose() {
        // Items zurückgeben
        returnWagerItems();
        
        // Geld bleibt beim Spieler (wurde noch nicht abgezogen)
        
        // Session beenden
        session.cancel();
        plugin.getGuiManager().removeSession(player);
        
        MessageUtil.sendMessage(player, "&cWager-Anfrage abgebrochen.");
        closeInventory();
    }
    
    private void returnWagerItems() {
        List<ItemStack> items = session.clearWagerItems();
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                player.getInventory().addItem(item.clone());
            }
        }
    }
    
    private String formatItemName(ItemStack item) {
        String name = item.getType().name().replace("_", " ").toLowerCase();
        // Capitalize first letter of each word
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)));
                result.append(word.substring(1)).append(" ");
            }
        }
        return result.toString().trim() + " x" + item.getAmount();
    }
}
