package de.zfzfg.pvpwager.gui;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.CommandRequest;
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
 * GUI zum Beantworten einer eingehenden Wager-Anfrage.
 * Ermöglicht das Setzen eines Gegeneinsatzes.
 */
public class ResponseGui extends AbstractWagerGui {
    
    private static final int SIZE = 54;
    
    // Layout-Konstanten
    private static final int SENDER_INFO_SLOT = 4;
    private static final int SENDER_WAGER_SLOT = 11;
    private static final int SENDER_MONEY_SLOT = 13;
    private static final int ARENA_SLOT = 15;
    
    private static final int YOUR_ITEMS_SLOT = 29;
    private static final int YOUR_MONEY_SLOT = 31;
    private static final int YOUR_ITEMS_START = 37;
    
    private static final int ACCEPT_SLOT = 48;
    private static final int DECLINE_SLOT = 50;
    private static final int EDIT_ITEMS_SLOT = 46;
    private static final int EDIT_MONEY_SLOT = 52;
    
    private final CommandRequest incomingRequest;
    
    public ResponseGui(EventPlugin plugin, Player player, WagerSession session, CommandRequest request) {
        super(plugin, player, session);
        this.incomingRequest = request;
    }
    
    @Override
    public void open() {
        inventory = Bukkit.createInventory(null, SIZE,
            MessageUtil.color("&6&lAnfrage beantworten"));
        
        buildLayout();
        openInventory();
    }
    
    private void buildLayout() {
        // Hintergrund
        fillBorder(Material.CYAN_STAINED_GLASS_PANE);
        
        // Innerer Bereich
        for (int i = 9; i < 45; i++) {
            if (i % 9 != 0 && i % 9 != 8) {
                inventory.setItem(i, createFillerItem(Material.GRAY_STAINED_GLASS_PANE));
            }
        }
        
        // Sender-Infos
        createSenderInfo();
        createSenderWagerDisplay();
        createSenderMoneyDisplay();
        createArenaDisplay();
        
        // Dein Gegeneinsatz
        createYourItemsDisplay();
        createYourMoneyDisplay();
        
        // Aktions-Buttons
        createActionButtons();
    }
    
    private void createSenderInfo() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        if (meta != null) {
            Player sender = incomingRequest.getSender();
            if (sender != null && sender.isOnline()) {
                meta.setOwningPlayer(sender);
            }
            meta.setDisplayName(MessageUtil.color("&e&lHerausforderung von:"));
            meta.setLore(java.util.Arrays.asList(
                MessageUtil.color("&f&l" + incomingRequest.getSender().getName()),
                "",
                MessageUtil.color("&7Setze deinen Gegeneinsatz")
            ));
            head.setItemMeta(meta);
        }
        inventory.setItem(SENDER_INFO_SLOT, head);
    }
    
    private void createSenderWagerDisplay() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(MessageUtil.color("&7Gegner setzt:"));
        
        List<ItemStack> items = incomingRequest.getWagerItems();
        if (items != null && !items.isEmpty()) {
            for (ItemStack item : items) {
                lore.add(MessageUtil.color("&7- &f" + formatItem(item)));
            }
        } else {
            lore.add(MessageUtil.color("&8Keine Items"));
        }
        
        inventory.setItem(SENDER_WAGER_SLOT, createButton(Material.CHEST,
            "&6&lGegner Items", lore));
    }
    
    private void createSenderMoneyDisplay() {
        double money = incomingRequest.getMoney();
        
        List<String> lore = new ArrayList<>();
        lore.add("");
        if (money > 0) {
            lore.add(MessageUtil.color("&7Gegner setzt:"));
            lore.add(MessageUtil.color("&6$" + String.format("%,.2f", money)));
        } else {
            lore.add(MessageUtil.color("&8Kein Geld"));
        }
        
        Material material = money > 0 ? Material.GOLD_BLOCK : Material.COAL_BLOCK;
        inventory.setItem(SENDER_MONEY_SLOT, createButton(material,
            "&6&lGegner Geld", lore));
    }
    
    private void createArenaDisplay() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(MessageUtil.color("&7Arena: &f" + incomingRequest.getArenaId()));
        lore.add(MessageUtil.color("&7Ausrüstung: &f" + incomingRequest.getEquipmentId()));
        
        inventory.setItem(ARENA_SLOT, createButton(Material.GRASS_BLOCK,
            "&6&lKampf-Details", lore));
    }
    
    private void createYourItemsDisplay() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (session.hasWagerItems()) {
            lore.add(MessageUtil.color("&aDeine Items:"));
            int count = 0;
            for (ItemStack item : session.getWagerItems()) {
                if (count >= 4) {
                    lore.add(MessageUtil.color("&7... und weitere"));
                    break;
                }
                lore.add(MessageUtil.color("&7- &f" + formatItem(item)));
                count++;
            }
        } else {
            lore.add(MessageUtil.color("&7Keine Items gesetzt"));
        }
        
        lore.add("");
        lore.add(MessageUtil.color("&eKlicke zum Bearbeiten"));
        
        inventory.setItem(YOUR_ITEMS_SLOT, createButton(Material.ENDER_CHEST,
            "&a&lDein Item-Einsatz", lore));
        
        // Zeige ausgewählte Items
        displaySelectedItems();
    }
    
    private void displaySelectedItems() {
        List<ItemStack> items = session.getWagerItems();
        
        for (int i = 0; i < 4; i++) {
            int slot = YOUR_ITEMS_START + i;
            if (i < items.size()) {
                ItemStack display = items.get(i).clone();
                inventory.setItem(slot, display);
            } else {
                inventory.setItem(slot, createFillerItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE));
            }
        }
    }
    
    private void createYourMoneyDisplay() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (session.hasWagerMoney()) {
            lore.add(MessageUtil.color("&aDein Einsatz:"));
            lore.add(MessageUtil.color("&6$" + String.format("%,.2f", session.getWagerMoney())));
        } else {
            lore.add(MessageUtil.color("&7Kein Geld gesetzt"));
        }
        
        lore.add("");
        lore.add(MessageUtil.color("&eKlicke zum Bearbeiten"));
        
        Material material = session.hasWagerMoney() ? Material.EMERALD_BLOCK : Material.COAL_BLOCK;
        inventory.setItem(YOUR_MONEY_SLOT, createButton(material,
            "&a&lDein Geld-Einsatz", lore));
    }
    
    private void createActionButtons() {
        // Items bearbeiten
        inventory.setItem(EDIT_ITEMS_SLOT, createButton(Material.WRITABLE_BOOK,
            "&e✎ Items wählen",
            "&7Öffnet die Item-Auswahl"));
        
        // Geld bearbeiten
        inventory.setItem(EDIT_MONEY_SLOT, createButton(Material.GOLD_INGOT,
            "&e✎ Geld setzen",
            "&7Öffnet die Geld-Auswahl"));
        
        // Annehmen
        inventory.setItem(ACCEPT_SLOT, createButton(Material.LIME_WOOL,
            "&a&l✔ ANNEHMEN",
            "",
            "&7Akzeptiert die Anfrage",
            "&7mit deinem Gegeneinsatz."));
        
        // Ablehnen
        inventory.setItem(DECLINE_SLOT, createButton(Material.RED_WOOL,
            "&c&l✖ ABLEHNEN",
            "",
            "&7Lehnt die Anfrage ab."));
    }
    
    @Override
    public boolean handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return true;
        
        switch (slot) {
            case YOUR_ITEMS_SLOT:
            case EDIT_ITEMS_SLOT:
                playClickSound();
                switchTo(new ResponseItemSelectionGui(plugin, player, session, incomingRequest));
                break;
                
            case YOUR_MONEY_SLOT:
            case EDIT_MONEY_SLOT:
                playClickSound();
                switchTo(new ResponseMoneySelectionGui(plugin, player, session, incomingRequest));
                break;
                
            case ACCEPT_SLOT:
                acceptRequest();
                break;
                
            case DECLINE_SLOT:
                declineRequest();
                break;
        }
        
        return true;
    }
    
    private void acceptRequest() {
        Player sender = incomingRequest.getSender();
        
        if (sender == null || !sender.isOnline()) {
            playErrorSound();
            MessageUtil.sendMessage(player, "&cDer Herausforderer ist nicht mehr online!");
            closeAndCleanup();
            return;
        }
        
        // Geld prüfen
        if (session.hasWagerMoney() && plugin.hasEconomy()) {
            if (!plugin.getEconomy().has(player, session.getWagerMoney())) {
                playErrorSound();
                MessageUtil.sendMessage(player, "&cNicht genug Geld!");
                return;
            }
        }
        
        // Items sind bereits in der Session (aus Inventar entfernt)
        
        // Antwort setzen
        incomingRequest.setTargetResponse(
            session.getWagerItems(),
            session.getWagerMoney(),
            null, // Keine Arena-Änderung
            null  // Keine Equipment-Änderung
        );
        
        // Setze Flag für Verhandlung
        incomingRequest.setWaitingForConfirmation(true);
        incomingRequest.setLastOfferer(player.getUniqueId());
        
        playSuccessSound();
        
        MessageUtil.sendMessage(player, "&aEinsatz gewählt! Warte auf Bestätigung von " + sender.getName() + "...");
        
        // Benachrichtige Sender und öffne Verhandlungs-GUI
        MessageUtil.sendMessage(sender, "");
        MessageUtil.sendMessage(sender, "&6&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(sender, "&e" + player.getName() + " &7hat seinen Einsatz gewählt!");
        MessageUtil.sendMessage(sender, "&7Überprüfe das Angebot...");
        MessageUtil.sendMessage(sender, "&6&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(sender, "");
        
        // Session für Sender erstellen/holen
        WagerSession senderSession = plugin.getGuiManager().getOrCreateSession(sender, player);
        
        // Öffne NegotiationGui beim Sender
        Bukkit.getScheduler().runTask(plugin, () -> {
            new NegotiationGui(plugin, sender, senderSession, incomingRequest).open();
        });
        
        closeInventory();
    }
    
    private void declineRequest() {
        Player sender = incomingRequest.getSender();
        
        // Items zurückgeben
        returnWagerItems();
        
        // Anfrage entfernen
        if (sender != null) {
            plugin.getCommandRequestManager().removeRequest(sender);
            
            if (sender.isOnline()) {
                MessageUtil.sendMessage(sender, "&c" + player.getName() + " hat deine Anfrage abgelehnt!");
                
                // Sender's Items auch zurückgeben
                for (ItemStack item : incomingRequest.getWagerItems()) {
                    if (item != null && !item.getType().isAir()) {
                        sender.getInventory().addItem(item.clone());
                    }
                }
            }
        }
        
        playClickSound();
        MessageUtil.sendMessage(player, "&cAnfrage abgelehnt.");
        
        // Cleanup
        session.cancel();
        plugin.getGuiManager().removeSession(player);
        closeInventory();
    }
    
    private void closeAndCleanup() {
        returnWagerItems();
        session.cancel();
        plugin.getGuiManager().removeSession(player);
        closeInventory();
    }
    
    private void returnWagerItems() {
        for (ItemStack item : session.clearWagerItems()) {
            if (item != null && !item.getType().isAir()) {
                player.getInventory().addItem(item.clone());
            }
        }
    }
    
    private String formatItem(ItemStack item) {
        String name = item.getType().name().replace("_", " ").toLowerCase();
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
    
    @Override
    public void onClose() {
        // Items bleiben in Session
    }
}
