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
        inventory = de.zfzfg.core.util.GuiUtil.createInventory(null, 54, 
            de.zfzfg.core.util.Text.of(t("wager-title", "target", session.getTargetName())));
        
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
            de.zfzfg.core.util.ItemUtil.setDisplayName(meta, de.zfzfg.core.util.Text.ofItem(t("challenge-to-title")));
            de.zfzfg.core.util.ItemUtil.setLore(meta, Arrays.asList(
                de.zfzfg.core.util.Text.ofItem(t("challenge-to-player", "player", session.getTargetName())),
                de.zfzfg.core.util.Text.ofItem(""),
                de.zfzfg.core.util.Text.ofItem(t("challenge-to-line1")),
                de.zfzfg.core.util.Text.ofItem(t("challenge-to-line2"))
            ));
            head.setItemMeta(meta);
        }
        inventory.setItem(TARGET_INFO_SLOT, head);
    }
    
    private void createItemsButton() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (session.hasWagerItems()) {
            lore.add(MessageUtil.color(t("selected-label")));
            int count = 0;
            for (ItemStack item : session.getWagerItems()) {
                if (count >= 5) {
                    lore.add(MessageUtil.color(t("more-items", "count", String.valueOf(session.getWagerItemCount() - 5))));
                    break;
                }
                lore.add(MessageUtil.color("&7- &f" + formatItemName(item)));
                count++;
            }
        } else {
            lore.add(MessageUtil.color(t("no-items-selected")));
        }
        
        lore.add("");
        lore.add(MessageUtil.color(t("click-to-edit")));
        
        ItemStack button = createButton(Material.CHEST, 
            t("items-title"), lore);
        inventory.setItem(ITEMS_BUTTON_SLOT, button);
    }
    
    private void createMoneyButton() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (session.hasWagerMoney()) {
            lore.add(MessageUtil.color(t("current-wager-label")));
            lore.add(MessageUtil.color("&6$" + String.format("%.2f", session.getWagerMoney())));
        } else {
            lore.add(MessageUtil.color(t("no-money-selected")));
        }
        
        lore.add("");
        
        if (plugin.hasEconomy()) {
            double balance = plugin.getEconomy().getBalance(player);
            lore.add(MessageUtil.color(t("your-balance", "balance", String.format("%.2f", balance))));
            lore.add("");
            lore.add(MessageUtil.color(t("click-to-edit")));
        } else {
            lore.add(MessageUtil.color(t("economy-not-available")));
        }
        
        ItemStack button = createButton(Material.GOLD_INGOT, 
            t("money-title"), lore);
        inventory.setItem(MONEY_BUTTON_SLOT, button);
    }
    
    private void createArenaButton() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (session.getSelectedArena() != null) {
            lore.add(MessageUtil.color(t("selected-label")));
            lore.add(MessageUtil.color("&f" + session.getSelectedArena().getDisplayName()));
        } else {
            lore.add(MessageUtil.color(t("no-arena-selected")));
            lore.add(MessageUtil.color(t("required-field")));
        }
        
        lore.add("");
        lore.add(MessageUtil.color(t("click-to-select")));
        
        Material material = session.getSelectedArena() != null ? 
            Material.GRASS_BLOCK : Material.BARRIER;
        
        ItemStack button = createButton(material, 
            t("arena-title"), lore);
        inventory.setItem(ARENA_BUTTON_SLOT, button);
    }
    
    private void createEquipmentButton() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (session.getSelectedEquipment() != null) {
            lore.add(MessageUtil.color(t("selected-label")));
            lore.add(MessageUtil.color("&f" + session.getSelectedEquipment().getDisplayName()));
        } else {
            lore.add(MessageUtil.color(t("no-equipment-selected")));
            lore.add(MessageUtil.color(t("required-field")));
        }
        
        lore.add("");
        lore.add(MessageUtil.color(t("click-to-select")));
        
        Material material = session.getSelectedEquipment() != null ? 
            Material.DIAMOND_CHESTPLATE : Material.BARRIER;
        
        ItemStack button = createButton(material, 
            t("equipment-title"), lore);
        inventory.setItem(EQUIPMENT_BUTTON_SLOT, button);
    }
    
    private void createOverview() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(MessageUtil.color("&7━━━━━━━━━━━━━━━━━━━━"));
        
        // Items
        if (session.hasWagerItems()) {
            lore.add(MessageUtil.color(t("overview.items-set", "count", String.valueOf(session.getWagerItemCount()))));
        } else {
            lore.add(MessageUtil.color(t("overview.items-none")));
        }
        
        // Geld
        if (session.hasWagerMoney()) {
            lore.add(MessageUtil.color(t("overview.money-set", "amount", String.format("%.2f", session.getWagerMoney()))));
        } else {
            lore.add(MessageUtil.color(t("overview.money-none")));
        }
        
        // Arena
        if (session.getSelectedArena() != null) {
            lore.add(MessageUtil.color(t("overview.arena-set", "arena", session.getSelectedArena().getDisplayName())));
        } else {
            lore.add(MessageUtil.color(t("overview.arena-none")));
        }
        
        // Equipment
        if (session.getSelectedEquipment() != null) {
            lore.add(MessageUtil.color(t("overview.equipment-set", "equipment", session.getSelectedEquipment().getDisplayName())));
        } else {
            lore.add(MessageUtil.color(t("overview.equipment-none")));
        }
        
        lore.add(MessageUtil.color(t("overview.separator")));
        
        // Status
        if (session.isComplete()) {
            lore.add(MessageUtil.color(t("ready-to-send")));
        } else {
            lore.add(MessageUtil.color(t("not-ready-yet")));
        }
        
        ItemStack overview = createButton(Material.BOOK, 
            t("overview-title"), lore);
        inventory.setItem(OVERVIEW_SLOT, overview);
    }
    
    private void createSendButton() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        boolean canSend = session.isComplete();
        
        if (canSend) {
            lore.add(MessageUtil.color(t("all-ready")));
            lore.add("");
            lore.add(MessageUtil.color(t("click-to-send-line-1")));
            lore.add(MessageUtil.color(t("click-to-send-line-2")));
        } else {
            lore.add(MessageUtil.color(t("not-ready-yet")));
            lore.add("");
            if (session.getSelectedArena() == null) {
                lore.add(MessageUtil.color(t("choose-arena")));
            }
            if (session.getSelectedEquipment() == null) {
                lore.add(MessageUtil.color(t("choose-equipment")));
            }
        }
        
        Material material = canSend ? Material.LIME_WOOL : Material.GRAY_WOOL;
        ItemStack button = createButton(material, 
            canSend ? t("send-button-ready-title") : t("send-button-disabled-title"), lore);
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
                    MessageUtil.sendMessage(player, MessageUtil.color(t("economy-not-available")));
                }
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
                    MessageUtil.sendMessage(player, MessageUtil.color(t("error-choose-arena-equipment")));
                }
                break;

            case CANCEL_SLOT:
                // Fehlte bisher: der Abbrechen-Button wurde zwar gezeichnet, aber nie
                // ausgewertet - der Klick blieb wirkungslos und die Items steckten in der
                // Session fest. Gleiche Behandlung wie in der ConfirmationGui.
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
        
        MessageUtil.sendMessage(player, MessageUtil.color(t("wager-cancelled")));
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
