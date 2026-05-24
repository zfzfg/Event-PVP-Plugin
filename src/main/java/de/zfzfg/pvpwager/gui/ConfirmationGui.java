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
    private static final int ITEMS_DISPLAY_START = 28;
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
            MessageUtil.color("&6&lAnfrage bestätigen"));
        
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
            meta.setDisplayName(MessageUtil.color("&e&lHerausforderung an:"));
            meta.setLore(java.util.Arrays.asList(
                MessageUtil.color("&f&l" + session.getTargetName()),
                "",
                MessageUtil.color("&7Diese Anfrage wird an"),
                MessageUtil.color("&7den Spieler gesendet.")
            ));
            head.setItemMeta(meta);
        }
        inventory.setItem(TARGET_INFO_SLOT, head);
    }
    
    private void createVsDisplay() {
        inventory.setItem(VS_SLOT, createButton(Material.IRON_SWORD,
            "&c&l⚔ VS ⚔",
            "",
            "&7" + player.getName(),
            "&7gegen",
            "&7" + session.getTargetName()));
    }
    
    private void createWagerDisplay() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (session.hasWagerItems()) {
            lore.add(MessageUtil.color("&aItems im Einsatz:"));
            for (ItemStack item : session.getWagerItems()) {
                lore.add(MessageUtil.color("&7- &f" + formatItem(item)));
            }
        } else {
            lore.add(MessageUtil.color("&7Keine Items eingesetzt"));
        }
        
        inventory.setItem(YOUR_WAGER_SLOT, createButton(Material.CHEST,
            "&6&l📦 Dein Item-Einsatz", lore));
    }
    
    private void createMoneyDisplay() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (session.hasWagerMoney()) {
            lore.add(MessageUtil.color("&aGeld-Einsatz:"));
            lore.add(MessageUtil.color("&6&l$" + String.format("%,.2f", session.getWagerMoney())));
            lore.add("");
            lore.add(MessageUtil.color("&c⚠ Wird bei Niederlage verloren!"));
        } else {
            lore.add(MessageUtil.color("&7Kein Geld eingesetzt"));
        }
        
        Material material = session.hasWagerMoney() ? Material.GOLD_BLOCK : Material.COAL_BLOCK;
        inventory.setItem(MONEY_DISPLAY_SLOT, createButton(material,
            "&6&l💰 Dein Geld-Einsatz", lore));
    }
    
    private void createArenaDisplay() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (session.getSelectedArena() != null) {
            lore.add(MessageUtil.color("&aArena: &f" + session.getSelectedArena().getDisplayName()));
            lore.add(MessageUtil.color("&7Welt: &f" + session.getSelectedArena().getArenaWorld()));
        } else {
            lore.add(MessageUtil.color("&cKeine Arena ausgewählt!"));
        }
        
        Material material = session.getSelectedArena() != null ? Material.GRASS_BLOCK : Material.BARRIER;
        inventory.setItem(ARENA_SLOT, createButton(material,
            "&6&l🗺 Kampfarena", lore));
    }
    
    private void createEquipmentDisplay() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (session.getSelectedEquipment() != null) {
            lore.add(MessageUtil.color("&aAusrüstung: &f" + session.getSelectedEquipment().getDisplayName()));
            lore.add("");
            lore.add(MessageUtil.color("&7Beide Spieler erhalten"));
            lore.add(MessageUtil.color("&7diese Ausrüstung im Kampf."));
        } else {
            lore.add(MessageUtil.color("&cKeine Ausrüstung ausgewählt!"));
        }
        
        Material material = session.getSelectedEquipment() != null ? Material.DIAMOND_CHESTPLATE : Material.BARRIER;
        inventory.setItem(EQUIPMENT_SLOT, createButton(material,
            "&6&l⚔ Kampfausrüstung", lore));
    }
    
    private void createActionButtons() {
        // Bearbeiten
        inventory.setItem(EDIT_SLOT, createButton(Material.WRITABLE_BOOK,
            "&e&l✎ Bearbeiten",
            "&7Zurück zum Hauptmenü",
            "&7um Änderungen vorzunehmen."));
        
        // Abbrechen
        inventory.setItem(CANCEL_SLOT, createButton(Material.RED_WOOL,
            "&c&l✖ ABBRECHEN",
            "",
            "&7Bricht die Anfrage ab.",
            "&7Alle Items werden zurückgegeben."));
        
        // Bestätigen
        List<String> confirmLore = new ArrayList<>();
        confirmLore.add("");
        confirmLore.add(MessageUtil.color("&7Sendet die Anfrage an:"));
        confirmLore.add(MessageUtil.color("&f" + session.getTargetName()));
        confirmLore.add("");
        
        if (session.hasWager()) {
            confirmLore.add(MessageUtil.color("&c⚠ ACHTUNG:"));
            confirmLore.add(MessageUtil.color("&cDein Einsatz wird eingefroren!"));
            confirmLore.add("");
        }
        
        confirmLore.add(MessageUtil.color("&aKlicke um zu senden!"));
        
        inventory.setItem(CONFIRM_SLOT, createButton(Material.LIME_WOOL,
            "&a&l✔ ANFRAGE SENDEN", confirmLore));
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
            MessageUtil.sendMessage(player, "&cWähle Arena und Ausrüstung!");
            return;
        }
        
        Player target = Bukkit.getPlayer(session.getTargetId());
        if (target == null || !target.isOnline()) {
            playErrorSound();
            MessageUtil.sendMessage(player, "&cDer Spieler ist nicht mehr online!");
            returnWagerItems();
            session.cancel();
            plugin.getGuiManager().removeSession(player);
            closeInventory();
            return;
        }
        
        // Prüfe ob Spieler bereits in Match
        if (plugin.getMatchManager().isPlayerInMatch(player)) {
            playErrorSound();
            MessageUtil.sendMessage(player, "&cDu bist bereits in einem Kampf!");
            return;
        }
        
        if (plugin.getMatchManager().isPlayerInMatch(target)) {
            playErrorSound();
            MessageUtil.sendMessage(player, "&c" + target.getName() + " ist bereits in einem Kampf!");
            return;
        }
        
        // Prüfe auf existierende Anfragen
        if (plugin.getCommandRequestManager().hasPendingRequest(player)) {
            playErrorSound();
            MessageUtil.sendMessage(player, "&cDu hast bereits eine ausstehende Anfrage!");
            return;
        }
        
        // Geld abziehen (wenn Vault verfügbar)
        if (session.hasWagerMoney() && plugin.hasEconomy()) {
            double money = session.getWagerMoney();
            if (!plugin.getEconomy().has(player, money)) {
                playErrorSound();
                MessageUtil.sendMessage(player, "&cNicht genug Geld! Du brauchst: $" + String.format("%.2f", money));
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
        MessageUtil.sendMessage(player, "&a&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(player, "&a&lANFRAGE GESENDET!");
        MessageUtil.sendMessage(player, "&a&l━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendMessage(player, "");
        MessageUtil.sendMessage(player, "&7Warte auf Antwort von &e" + target.getName() + "&7...");
        MessageUtil.sendMessage(player, "&7Die Anfrage läuft in &e60 Sekunden &7ab.");
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
