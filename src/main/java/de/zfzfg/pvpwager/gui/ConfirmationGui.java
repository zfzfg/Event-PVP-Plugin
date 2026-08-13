package de.zfzfg.pvpwager.gui;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.CommandRequest;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Finales Bestätigungs-GUI vor dem Senden der Wager-Anfrage.
 * Zeigt alle Details und fragt nach finaler Bestätigung.
 */
public class ConfirmationGui extends AbstractWagerGui {
    
    private static final int SIZE = 54;
    
    // Layout-Konstanten
    private static final int TARGET_INFO_SLOT = 4;
    private static final int YOUR_WAGER_SLOT = 19;
    private static final int VS_SLOT = 22;
    private static final int ARENA_SLOT = 25;
    private static final int MONEY_DISPLAY_SLOT = 30;
    private static final int EQUIPMENT_SLOT = 32;
    
    private static final int CANCEL_SLOT = 45;
    private static final int CONFIRM_SLOT = 53;
    private static final int EDIT_SLOT = 49;
    
    public ConfirmationGui(EventPlugin plugin, Player player, WagerSession session) {
        super(plugin, player, session);
    }
    
    @Override
    public void open() {
        inventory = Bukkit.createInventory(null, SIZE,
            MessageUtil.color(t("confirmation-title")));
        
        buildLayout();
        openInventory();
    }
    
    private void buildLayout() {
        // Hintergrund
        fillBorder(Material.ORANGE_STAINED_GLASS_PANE);
        
        // Zentrale Dekoration
        for (int i = 9; i < 45; i++) {
            if (i % 9 != 0 && i % 9 != 8) {
                inventory.setItem(i, createFillerItem(Material.BLACK_STAINED_GLASS_PANE));
            }
        }
        
        // Spieler-Infos
        createTargetInfo();
        createVsDisplay();
        
        // Wager-Details
        createWagerDisplay();
        createMoneyDisplay();
        
        // Arena & Equipment
        createArenaDisplay();
        createEquipmentDisplay();
        
        // Buttons
        createActionButtons();
    }
    
    private void createTargetInfo() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        if (meta != null) {
            Player target = Bukkit.getPlayer(session.getTargetId());
            if (target != null) {
                meta.setOwningPlayer(target);
            }
            meta.displayName(de.zfzfg.core.util.Text.ofItem(t("confirmation.target-title")));
            meta.lore(java.util.Arrays.asList(
                de.zfzfg.core.util.Text.ofItem(t("confirmation.target-player", "player", session.getTargetName())),
                de.zfzfg.core.util.Text.ofItem(""),
                de.zfzfg.core.util.Text.ofItem(t("confirmation.target-line1")),
                de.zfzfg.core.util.Text.ofItem(t("confirmation.target-line2"))
            ));
            head.setItemMeta(meta);
        }
        inventory.setItem(TARGET_INFO_SLOT, head);
    }
    
    private void createVsDisplay() {
        inventory.setItem(VS_SLOT, createButton(Material.IRON_SWORD,
            "&c&l⚔ VS ⚔",
            "",
            MessageUtil.color(t("confirmation.vs-player", "player", player.getName())),
            MessageUtil.color(t("confirmation.vs-against")),
            MessageUtil.color(t("confirmation.vs-target", "target", session.getTargetName()))));
    }
    
    private void createWagerDisplay() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (session.hasWagerItems()) {
            lore.add(MessageUtil.color(t("confirmation.items-in-wager")));
            for (ItemStack item : session.getWagerItems()) {
                lore.add(MessageUtil.color(t("confirmation.item-line", "item", formatItem(item))));
            }
        } else {
            lore.add(MessageUtil.color(t("confirmation.no-items")));
        }
        
        inventory.setItem(YOUR_WAGER_SLOT, createButton(Material.CHEST,
            t("confirmation.item-title"), lore));
    }
    
    private void createMoneyDisplay() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (session.hasWagerMoney()) {
            lore.add(MessageUtil.color(t("confirmation.money-label")));
            lore.add(MessageUtil.color("&6&l$" + String.format("%,.2f", session.getWagerMoney())));
            lore.add("");
            lore.add(MessageUtil.color(t("confirmation.money-warning")));
        } else {
            lore.add(MessageUtil.color(t("confirmation.no-money")));
        }
        
        Material material = session.hasWagerMoney() ? Material.GOLD_BLOCK : Material.COAL_BLOCK;
        inventory.setItem(MONEY_DISPLAY_SLOT, createButton(material,
            t("confirmation.money-title"), lore));
    }
    
    private void createArenaDisplay() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (session.getSelectedArena() != null) {
            lore.add(MessageUtil.color(t("confirmation.arena-label", "arena", session.getSelectedArena().getDisplayName())));
            lore.add(MessageUtil.color(t("confirmation.arena-world", "world", session.getSelectedArena().getArenaWorld())));
        } else {
            lore.add(MessageUtil.color(t("confirmation.no-arena")));
        }
        
        Material material = session.getSelectedArena() != null ? Material.GRASS_BLOCK : Material.BARRIER;
        inventory.setItem(ARENA_SLOT, createButton(material,
            t("confirmation.arena-title"), lore));
    }
    
    private void createEquipmentDisplay() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (session.getSelectedEquipment() != null) {
            lore.add(MessageUtil.color(t("confirmation.equipment-label", "equipment", session.getSelectedEquipment().getDisplayName())));
            lore.add("");
            lore.add(MessageUtil.color(t("confirmation.equipment-line1")));
            lore.add(MessageUtil.color(t("confirmation.equipment-line2")));
        } else {
            lore.add(MessageUtil.color(t("confirmation.no-equipment")));
        }
        
        Material material = session.getSelectedEquipment() != null ? Material.DIAMOND_CHESTPLATE : Material.BARRIER;
        inventory.setItem(EQUIPMENT_SLOT, createButton(material,
            t("confirmation.equipment-title"), lore));
    }
    
    private void createActionButtons() {
        // Bearbeiten
        inventory.setItem(EDIT_SLOT, createButton(Material.WRITABLE_BOOK,
            t("confirmation.edit-title"),
            t("confirmation.edit-line1"),
            t("confirmation.edit-line2")));
        
        // Abbrechen
        inventory.setItem(CANCEL_SLOT, createButton(Material.RED_WOOL,
            t("confirmation.cancel-title"),
            "",
            t("confirmation.cancel-line1"),
            t("confirmation.cancel-line2")));
        
        // Bestätigen
        List<String> confirmLore = new ArrayList<>();
        confirmLore.add("");
        confirmLore.add(MessageUtil.color(t("confirmation.confirm-send-line1")));
        confirmLore.add(MessageUtil.color(t("confirmation.confirm-send-target", "target", session.getTargetName())));
        confirmLore.add("");
        
        if (session.hasWager()) {
            confirmLore.add(MessageUtil.color(t("confirmation.confirm-warning-title")));
            confirmLore.add(MessageUtil.color(t("confirmation.confirm-warning-body")));
            confirmLore.add("");
        }
        
        confirmLore.add(MessageUtil.color(t("confirmation.click-to-send")));
        
        inventory.setItem(CONFIRM_SLOT, createButton(Material.LIME_WOOL,
            t("confirmation.confirm-button-title"), confirmLore));
    }
    
    @Override
    public boolean handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return true;
        
        switch (slot) {
            case EDIT_SLOT:
                playClickSound();
                switchTo(new WagerMainGui(plugin, player, session));
                break;
                
            case CANCEL_SLOT:
                playClickSound();
                cancelAndClose();
                break;
                
            case CONFIRM_SLOT:
                sendRequest();
                break;
        }
        
        return true;
    }
    
    private void sendRequest() {
        // Finale Validierung
        if (!session.isComplete()) {
            playErrorSound();
            MessageUtil.sendMessage(player, MessageUtil.color(t("error-choose-arena-equipment")));
            return;
        }
        
        Player target = Bukkit.getPlayer(session.getTargetId());
        if (target == null || !target.isOnline()) {
            playErrorSound();
            MessageUtil.sendMessage(player, MessageUtil.color(t("confirmation.error-player-offline")));
            returnWagerItems();
            session.cancel();
            plugin.getGuiManager().removeSession(player);
            closeInventory();
            return;
        }
        
        // Prüfe ob Spieler bereits in Match
        if (plugin.getMatchManager().isPlayerInMatch(player)) {
            playErrorSound();
            MessageUtil.sendMessage(player, MessageUtil.color(t("confirmation.error-already-in-match")));
            return;
        }
        
        if (plugin.getMatchManager().isPlayerInMatch(target)) {
            playErrorSound();
            MessageUtil.sendMessage(player, MessageUtil.color(t("confirmation.error-target-in-match", "target", target.getName())));
            return;
        }
        
        // Prüfe auf existierende Anfragen
        if (plugin.getCommandRequestManager().hasPendingRequest(player)) {
            playErrorSound();
            MessageUtil.sendMessage(player, MessageUtil.color(t("confirmation.error-already-pending-request")));
            return;
        }
        
        // Geld abziehen (wenn Vault verfügbar)
        if (session.hasWagerMoney() && plugin.hasEconomy()) {
            double money = session.getWagerMoney();
            if (!plugin.getEconomy().has(player, money)) {
                playErrorSound();
                MessageUtil.sendMessage(player, MessageUtil.color(t("confirmation.error-not-enough-money", "money", String.format("%.2f", money))));
                return;
            }
            
            // Geld NICHT sofort abziehen - erst bei Match-Start
            // Das verhindert Geldverlust bei Ablehnung
        }
        
        // Items sind bereits in der Session (aus Spieler-Inventar entfernt)
        // Sie werden bei Ablehnung/Timeout zurückgegeben
        
        // CommandRequest erstellen
        CommandRequest request = new CommandRequest(
            player,
            target,
            session.getWagerItems(),
            session.getWagerMoney(),
            session.getSelectedArena().getId(),
            session.getSelectedEquipment().getId()
        );
        
        // Anfrage registrieren
        plugin.getCommandRequestManager().addRequest(request);
        
        // Benachrichtigungen
        plugin.getCommandRequestManager().sendRequestNotification(request);
        
        // Erfolg
        playSuccessSound();
        session.setState(WagerSession.SessionState.SENT);
        session.setConfirmed(true);
        
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, MessageUtil.color(t("confirmation.sent-line1")));
        MessageUtil.sendMessage(player, MessageUtil.color(t("confirmation.sent-line2")));
        MessageUtil.sendMessage(player, MessageUtil.color(t("confirmation.sent-line3")));
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, MessageUtil.color(t("confirmation.sent-waiting", "target", target.getName())));
        MessageUtil.sendMessage(player, MessageUtil.color(t("confirmation.sent-expire")));
        MessageUtil.sendMessage(player, "");
        
        // Session behalten (Items sind gesperrt bis Antwort kommt)
        // Session wird erst bei Accept/Deny/Timeout entfernt
        
        closeInventory();
    }
    
    private void cancelAndClose() {
        // Items zurückgeben
        returnWagerItems();
        
        // Geld wurde noch nicht abgezogen
        
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
        // Bei normalem Schließen ohne Bestätigung:
        // Items bleiben in Session falls bereits gesendet
        if (!session.isConfirmed() && !session.isCancelled()) {
            // GUI wurde manuell geschlossen ohne Action
            // Session bleibt aktiv
        }
    }
}
