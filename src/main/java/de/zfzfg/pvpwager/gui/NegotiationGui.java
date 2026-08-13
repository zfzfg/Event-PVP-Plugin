package de.zfzfg.pvpwager.gui;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.CommandRequest;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * GUI zur Verhandlung zwischen zwei Spielern.
 * Zeigt beide Einsätze nebeneinander an und ermöglicht:
 * - Annehmen (Match starten)
 * - Ablehnen (Verhandlung abbrechen)
 * - Gegenangebot (eigenen Einsatz anpassen und zurücksenden)
 */
public class NegotiationGui extends AbstractWagerGui {
    
    private static final int SIZE = 54;
    
    // Layout-Konstanten - Linke Seite (Gegner)
    private static final int OPPONENT_HEAD_SLOT = 2;
    private static final int OPPONENT_ITEMS_TITLE_SLOT = 10;
    private static final int[] OPPONENT_ITEMS_SLOTS = {19, 20, 21, 28, 29, 30};
    private static final int OPPONENT_MONEY_SLOT = 37;
    
    // Layout-Konstanten - Mitte
    private static final int VS_SLOT = 4;
    private static final int ARENA_SLOT = 13;
    private static final int EQUIPMENT_SLOT = 22;
    
    // Layout-Konstanten - Rechte Seite (Du)
    private static final int YOUR_HEAD_SLOT = 6;
    private static final int YOUR_ITEMS_TITLE_SLOT = 16;
    private static final int[] YOUR_ITEMS_SLOTS = {23, 24, 25, 32, 33, 34};
    private static final int YOUR_MONEY_SLOT = 43;
    
    // Buttons
    private static final int DECLINE_SLOT = 45;
    private static final int COUNTER_OFFER_SLOT = 49;
    private static final int ACCEPT_SLOT = 53;
    
    private final CommandRequest request;
    private final boolean isSender; // true = ursprünglicher Sender, false = Target
    
    public NegotiationGui(EventPlugin plugin, Player player, WagerSession session, CommandRequest request) {
        super(plugin, player, session);
        this.request = request;
        this.isSender = player.getUniqueId().equals(request.getSender().getUniqueId());
    }
    
    @Override
    public void open() {
        String title = isSender ? t("negotiation.title-sender") : t("negotiation.title-target");
        inventory = Bukkit.createInventory(null, SIZE, MessageUtil.color(title));
        
        buildLayout();
        openInventory();
    }
    
    private void buildLayout() {
        // Hintergrund
        fillBackground();
        
        // VS in der Mitte
        createVsDisplay();
        
        // Arena & Equipment Info
        createArenaEquipmentDisplay();
        
        // Gegner-Seite (links)
        createOpponentDisplay();
        
        // Deine Seite (rechts)
        createYourDisplay();
        
        // Action Buttons
        createActionButtons();
    }
    
    private void fillBackground() {
        // Rahmen
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        
        // Linke Seite (Gegner) - Rot
        for (int row = 1; row < 5; row++) {
            for (int col = 1; col < 4; col++) {
                int slot = row * 9 + col;
                inventory.setItem(slot, createFillerItem(Material.RED_STAINED_GLASS_PANE));
            }
        }
        
        // Mitte - Grau
        for (int row = 1; row < 5; row++) {
            int slot = row * 9 + 4;
            inventory.setItem(slot, createFillerItem(Material.BLACK_STAINED_GLASS_PANE));
        }
        
        // Rechte Seite (Du) - Grün
        for (int row = 1; row < 5; row++) {
            for (int col = 5; col < 8; col++) {
                int slot = row * 9 + col;
                inventory.setItem(slot, createFillerItem(Material.LIME_STAINED_GLASS_PANE));
            }
        }
    }
    
    private void createVsDisplay() {
        inventory.setItem(VS_SLOT, createButton(Material.IRON_SWORD,
            t("negotiation.vs-title"),
            "",
            t("negotiation.vs-line1"),
            t("negotiation.vs-line2")));
    }
    
    private void createArenaEquipmentDisplay() {
        // Arena
        String arenaName = request.getFinalArenaId();
        inventory.setItem(ARENA_SLOT, createButton(Material.GRASS_BLOCK,
            t("negotiation.arena-title"),
            "&7" + arenaName));
        
        // Equipment
        String equipName = request.getFinalEquipmentId();
        inventory.setItem(EQUIPMENT_SLOT, createButton(Material.DIAMOND_CHESTPLATE,
            t("negotiation.equipment-title"),
            "&7" + equipName));
    }
    
    private void createOpponentDisplay() {
        Player opponent = isSender ? request.getTarget() : request.getSender();
        List<ItemStack> opponentItems = isSender ? request.getTargetWagerItems() : request.getWagerItems();
        double opponentMoney = isSender ? request.getTargetWagerMoney() : request.getMoney();
        
        // Spieler-Kopf
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        if (meta != null && opponent != null) {
            meta.setOwningPlayer(opponent);
            meta.displayName(de.zfzfg.core.util.Text.ofItem(t("negotiation.opponent-title", "player", opponent.getName())));
            meta.lore(java.util.Arrays.asList(
                de.zfzfg.core.util.Text.ofItem(""),
                de.zfzfg.core.util.Text.ofItem(t("negotiation.opponent-lore1")),
                de.zfzfg.core.util.Text.ofItem(t("negotiation.opponent-lore2"))
            ));
            head.setItemMeta(meta);
        }
        inventory.setItem(OPPONENT_HEAD_SLOT, head);
        
        // Items-Titel
        int itemCount = opponentItems != null ? opponentItems.size() : 0;
        inventory.setItem(OPPONENT_ITEMS_TITLE_SLOT, createButton(Material.CHEST,
            t("negotiation.opponent-items-title"),
            t("negotiation.items-count", "count", String.valueOf(itemCount))));
        
        // Items anzeigen
        displayItemsInSlots(opponentItems, OPPONENT_ITEMS_SLOTS, Material.RED_STAINED_GLASS_PANE);
        
        // Geld
        Material moneyMat = opponentMoney > 0 ? Material.GOLD_BLOCK : Material.COAL_BLOCK;
        String moneyText = opponentMoney > 0 ? 
            "&6$" + String.format("%,.2f", opponentMoney) : t("negotiation.no-money");
        inventory.setItem(OPPONENT_MONEY_SLOT, createButton(moneyMat,
            t("negotiation.opponent-money-title"),
            moneyText));
    }
    
    private void createYourDisplay() {
        // Dein Einsatz - abhängig davon wer du bist
        List<ItemStack> yourItems = isSender ? request.getWagerItems() : request.getTargetWagerItems();
        double yourMoney = isSender ? request.getMoney() : request.getTargetWagerMoney();
        
        // Falls noch keine Antwort gesetzt wurde, nimm Session-Daten
        if (!isSender && !request.hasTargetResponded()) {
            yourItems = session.getWagerItems();
            yourMoney = session.getWagerMoney();
        }
        
        // Spieler-Kopf
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.displayName(de.zfzfg.core.util.Text.ofItem(t("negotiation.you-title", "player", player.getName())));
            meta.lore(java.util.Arrays.asList(
                de.zfzfg.core.util.Text.ofItem(""),
                de.zfzfg.core.util.Text.ofItem(t("negotiation.you-lore"))
            ));
            head.setItemMeta(meta);
        }
        inventory.setItem(YOUR_HEAD_SLOT, head);
        
        // Items-Titel
        int itemCount = yourItems != null ? yourItems.size() : 0;
        inventory.setItem(YOUR_ITEMS_TITLE_SLOT, createButton(Material.ENDER_CHEST,
            t("negotiation.your-items-title"),
            t("negotiation.items-count", "count", String.valueOf(itemCount))));
        
        // Items anzeigen
        displayItemsInSlots(yourItems, YOUR_ITEMS_SLOTS, Material.LIME_STAINED_GLASS_PANE);
        
        // Geld
        Material moneyMat = yourMoney > 0 ? Material.EMERALD_BLOCK : Material.COAL_BLOCK;
        String moneyText = yourMoney > 0 ? 
            "&6$" + String.format("%,.2f", yourMoney) : t("negotiation.no-money");
        inventory.setItem(YOUR_MONEY_SLOT, createButton(moneyMat,
            t("negotiation.your-money-title"),
            moneyText));
    }
    
    private void displayItemsInSlots(List<ItemStack> items, int[] slots, Material emptyMaterial) {
        for (int i = 0; i < slots.length; i++) {
            if (items != null && i < items.size()) {
                ItemStack display = items.get(i).clone();
                inventory.setItem(slots[i], display);
            } else {
                inventory.setItem(slots[i], createFillerItem(emptyMaterial));
            }
        }
    }
    
    private void createActionButtons() {
        // Ablehnen (links)
        inventory.setItem(DECLINE_SLOT, createButton(Material.RED_WOOL,
            t("negotiation.decline-title"),
            "",
            t("negotiation.decline-line1"),
            t("negotiation.decline-line2"),
            "",
            t("negotiation.decline-line3")));
        
        // Gegenangebot (mitte)
        inventory.setItem(COUNTER_OFFER_SLOT, createButton(Material.ORANGE_WOOL,
            t("negotiation.counter-offer-title"),
            "",
            t("negotiation.counter-offer-line1"),
            t("negotiation.counter-offer-line2"),
            "",
            t("negotiation.counter-offer-line3"),
            t("negotiation.counter-offer-line4")));
        
        // Annehmen (rechts)
        inventory.setItem(ACCEPT_SLOT, createButton(Material.LIME_WOOL,
            t("negotiation.accept-title"),
            "",
            t("negotiation.accept-line1"),
            t("negotiation.accept-line2"),
            "",
            t("negotiation.accept-line3")));
    }
    
    @Override
    public boolean handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return true;
        
        switch (slot) {
            case DECLINE_SLOT:
                declineOffer();
                break;
                
            case COUNTER_OFFER_SLOT:
                openCounterOffer();
                break;
                
            case ACCEPT_SLOT:
                acceptOffer();
                break;
        }
        
        return true;
    }
    
    private void declineOffer() {
        playClickSound();
        
        Player opponent = isSender ? request.getTarget() : request.getSender();
        
        // Items beider Spieler zurückgeben
        returnAllItems();
        
        // Benachrichtigungen
        MessageUtil.sendMessage(player, t("offer-declined-self"));
        if (opponent != null && opponent.isOnline()) {
            MessageUtil.sendMessage(opponent, t("offer-declined-other", "player", player.getName()));
            // Opponent's GUI schließen falls offen
            opponent.closeInventory();
        }
        
        // Cleanup
        cleanupNegotiation();
        closeInventory();
    }
    
    private void openCounterOffer() {
        playClickSound();
        
        // Öffne Item/Geld-Auswahl für Gegenangebot
        // Wir nutzen eine spezielle Session für das Gegenangebot
        session.setState(WagerSession.SessionState.ITEM_SELECTION);
        
        // Öffne Counter-Offer GUI (nutzt ResponseItemSelectionGui mit Rückkehr zu NegotiationGui)
        switchTo(new CounterOfferItemGui(plugin, player, session, request, isSender));
    }
    
    private void acceptOffer() {
        Player opponent = isSender ? request.getTarget() : request.getSender();
        
        // Prüfungen
        if (opponent == null || !opponent.isOnline()) {
            playErrorSound();
            MessageUtil.sendMessage(player, t("opponent-offline"));
            returnAllItems();
            cleanupNegotiation();
            closeInventory();
            return;
        }
        
        // Prüfe ob Spieler in Match
        if (plugin.getMatchManager().isPlayerInMatch(player)) {
            playErrorSound();
            MessageUtil.sendMessage(player, t("already-in-match-self"));
            return;
        }
        
        if (plugin.getMatchManager().isPlayerInMatch(opponent)) {
            playErrorSound();
            MessageUtil.sendMessage(player, t("already-in-match-other", "player", opponent.getName()));
            return;
        }
        
        // Geld prüfen für beide Spieler
        if (!validateMoney()) {
            return;
        }
        
        // Match starten!
        playSuccessSound();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        
        MessageUtil.sendMessage(player, t("offer-accepted-self"));
        MessageUtil.sendMessage(opponent, t("offer-accepted-other", "player", player.getName()));
        
        // Opponent's GUI schließen
        opponent.closeInventory();
        
        // Stelle sicher dass Target-Response gesetzt ist
        if (!request.hasTargetResponded()) {
            request.setTargetResponse(
                session.getWagerItems(),
                session.getWagerMoney(),
                null, null
            );
        }
        
        // Match starten
        plugin.getMatchManager().startMatchFromCommand(request);
        
        // Cleanup
        cleanupNegotiation();
        closeInventory();
    }
    
    private boolean validateMoney() {
        Player sender = request.getSender();
        Player target = request.getTarget();
        
        if (plugin.hasEconomy()) {
            double senderMoney = request.getMoney();
            double targetMoney = request.getTargetWagerMoney();
            
            if (senderMoney > 0 && !plugin.getEconomy().has(sender, senderMoney)) {
                playErrorSound();
                MessageUtil.sendMessage(player, t("not-enough-money-other", "player", sender.getName()));
                return false;
            }
            
            if (targetMoney > 0 && !plugin.getEconomy().has(target, targetMoney)) {
                playErrorSound();
                MessageUtil.sendMessage(player, t("not-enough-money-other", "player", target.getName()));
                return false;
            }
        }
        
        return true;
    }
    
    private void returnAllItems() {
        Player sender = request.getSender();
        Player target = request.getTarget();
        
        // Sender's Items zurück
        if (sender != null && sender.isOnline()) {
            for (ItemStack item : request.getWagerItems()) {
                if (item != null && !item.getType().isAir()) {
                    sender.getInventory().addItem(item.clone());
                }
            }
        }
        
        // Target's Items zurück
        if (target != null && target.isOnline()) {
            for (ItemStack item : request.getTargetWagerItems()) {
                if (item != null && !item.getType().isAir()) {
                    target.getInventory().addItem(item.clone());
                }
            }
            // Auch Session-Items falls noch nicht in Request
            if (!request.hasTargetResponded() && session != null) {
                for (ItemStack item : session.clearWagerItems()) {
                    if (item != null && !item.getType().isAir()) {
                        target.getInventory().addItem(item.clone());
                    }
                }
            }
        }
    }
    
    private void cleanupNegotiation() {
        Player sender = request.getSender();
        Player target = request.getTarget();
        
        // Request entfernen
        plugin.getCommandRequestManager().removeRequest(sender);
        
        // Sessions entfernen
        if (sender != null) {
            plugin.getGuiManager().removeSession(sender);
        }
        if (target != null) {
            plugin.getGuiManager().removeSession(target);
        }
    }
    
    @Override
    public void onClose() {
        // Bei manuellem Schließen - nicht automatisch abbrechen
        // Der andere Spieler wartet noch
    }
}
