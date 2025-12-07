package de.zfzfg.pvpwager.gui.livetrade;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Arena;
import de.zfzfg.pvpwager.models.EquipmentSet;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * Live Trade GUI - Wird von beiden Spielern gleichzeitig gesehen.
 * 
 * Layout (6 Reihen à 9 Slots = 54 Slots):
 * 
 * Reihe 0: [P1-HEAD] [P1-GELD] [P1-CONFIRM] [=] [ÜBERSICHT] [=] [P2-CONFIRM] [P2-GELD] [P2-HEAD]
 * Reihe 1: [P1-Slot] [P1-Slot] [P1-Slot] [|] [ARENA-SEL] [|] [P2-Slot] [P2-Slot] [P2-Slot]
 * Reihe 2: [P1-Slot] [P1-Slot] [P1-Slot] [|] [EQUIP-SEL] [|] [P2-Slot] [P2-Slot] [P2-Slot]
 * Reihe 3: [P1-Slot] [P1-Slot] [P1-Slot] [|] [COUNTDOWN] [|] [P2-Slot] [P2-Slot] [P2-Slot]
 * Reihe 4: [P1-Slot] [P1-Slot] [P1-Slot] [|] [COUNT-BAR] [|] [P2-Slot] [P2-Slot] [P2-Slot]
 * Reihe 5: [===] [===] [===] [CANCEL] [===] [===] [===] [===] [===]
 * 
 * Spieler 1 kann NUR in seinen Slots (links) Items platzieren
 * Spieler 2 kann NUR in seinen Slots (rechts) Items platzieren
 * Die Mitte ist für beide Spieler zum Klicken (Arena/Equipment auswählen)
 * Items können per Shift-Klick ODER Drag & Drop hinzugefügt werden
 */
public class LiveTradeGui {
    
    private final LiveTradeSession session;
    private final LiveTradePlayer tradePlayer;
    private final Player player;
    private final EventPlugin plugin;
    private Inventory inventory;
    private boolean opened = false;
    
    // Hilfsmethode für LiveTrade-Nachrichten
    private String getMsg(String key) {
        return plugin.getCoreConfigManager().getMessages()
            .getString("messages.livetrade." + key, key);
    }
    
    private String getMsg(String key, String placeholder, String value) {
        String message = plugin.getCoreConfigManager().getMessages()
            .getString("messages.livetrade." + key, key);
        return message.replace("{" + placeholder + "}", value);
    }
    
    // === Slot-Definitionen ===
    
    // Player 1 Slots (links, 12 Slots für Items)
    private static final List<Integer> PLAYER1_ITEM_SLOTS = Arrays.asList(
        9, 10, 11,      // Reihe 1
        18, 19, 20,     // Reihe 2
        27, 28, 29,     // Reihe 3
        36, 37, 38      // Reihe 4
    );
    
    // Player 2 Slots (rechts, 12 Slots für Items)
    private static final List<Integer> PLAYER2_ITEM_SLOTS = Arrays.asList(
        15, 16, 17,     // Reihe 1
        24, 25, 26,     // Reihe 2
        33, 34, 35,     // Reihe 3
        42, 43, 44      // Reihe 4
    );
    
    // Mittlere Spalte (Separator + Funktionen)
    private static final int CENTER_COLUMN = 4;
    private static final List<Integer> CENTER_SLOTS = Arrays.asList(4, 13, 22, 31, 40, 49);
    
    // Separator-Spalten
    private static final List<Integer> SEPARATOR_LEFT = Arrays.asList(3, 12, 21, 30, 39, 48);
    private static final List<Integer> SEPARATOR_RIGHT = Arrays.asList(5, 14, 23, 32, 41, 50);
    
    // Funktions-Slots (neues Layout: Geld & Bestätigen oben)
    private static final int PLAYER1_HEAD_SLOT = 0;
    private static final int PLAYER1_MONEY_SLOT = 1;      // Geld nach oben
    private static final int PLAYER1_CONFIRM_SLOT = 2;    // Bestätigen nach oben
    private static final int OVERVIEW_SLOT = 4;           // Übersicht oben mitte
    private static final int PLAYER2_CONFIRM_SLOT = 6;    // Bestätigen nach oben
    private static final int PLAYER2_MONEY_SLOT = 7;      // Geld nach oben
    private static final int PLAYER2_HEAD_SLOT = 8;
    
    private static final int ARENA_SELECT_SLOT = 13;      // Arena auswählen (Reihe 1)
    private static final int EQUIPMENT_SELECT_SLOT = 22;  // Equipment auswählen (Reihe 2)
    private static final int COUNTDOWN_SLOT = 31;         // Countdown-Anzeige (Reihe 3)
    private static final int COUNTDOWN_BAR_SLOT = 40;     // Countdown-Balken (Reihe 4)
    private static final int CANCEL_SLOT = 49;            // Abbrechen unten mitte
    
    // Filler-Slots
    private static final List<Integer> TOP_FILLER = Arrays.asList(3, 5);
    private static final List<Integer> BOTTOM_FILLER = Arrays.asList(45, 46, 47, 48, 50, 51, 52, 53);
    
    public LiveTradeGui(LiveTradeSession session, LiveTradePlayer tradePlayer) {
        this.session = session;
        this.tradePlayer = tradePlayer;
        this.player = tradePlayer.getPlayer();
        this.plugin = session.getPlugin();
    }
    
    /**
     * Erstellt und öffnet das GUI.
     */
    public void open() {
        String title = MessageUtil.color(getMsg("gui-title", "opponent", tradePlayer.getOtherPlayer().getPlayer().getName()));
        inventory = Bukkit.createInventory(null, 54, title);
        
        // GUI aufbauen
        buildGui();
        
        // GUI öffnen
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && !session.isEnded()) {
                player.openInventory(inventory);
                opened = true;
            }
        });
    }
    
    /**
     * Baut das GUI komplett auf.
     */
    private void buildGui() {
        // Alles mit Glas füllen
        ItemStack filler = createFiller(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler);
        }
        
        // Separator (mittlere Spalten)
        ItemStack separator = createFiller(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot : SEPARATOR_LEFT) {
            inventory.setItem(slot, separator);
        }
        for (int slot : SEPARATOR_RIGHT) {
            inventory.setItem(slot, separator);
        }
        for (int slot : CENTER_SLOTS) {
            if (slot != OVERVIEW_SLOT && slot != ARENA_SELECT_SLOT && 
                slot != EQUIPMENT_SELECT_SLOT && slot != COUNTDOWN_SLOT && 
                slot != COUNTDOWN_BAR_SLOT && slot != CANCEL_SLOT) {
                inventory.setItem(slot, separator);
            }
        }
        
        // Item-Slots leeren (für Items)
        for (int slot : PLAYER1_ITEM_SLOTS) {
            inventory.setItem(slot, null);
        }
        for (int slot : PLAYER2_ITEM_SLOTS) {
            inventory.setItem(slot, null);
        }
        
        // Update durchführen
        update();
    }
    
    /**
     * Aktualisiert das gesamte GUI.
     */
    public void update() {
        if (inventory == null || session.isEnded()) return;
        
        LiveTradePlayer player1 = session.getPlayer1();
        LiveTradePlayer player2 = session.getPlayer2();
        
        // Spielerköpfe
        updatePlayerHeads(player1, player2);
        
        // Geld-Anzeige (jetzt oben)
        updateMoneyDisplays(player1, player2);
        
        // Bestätigungs-Buttons (jetzt oben)
        updateConfirmButtons(player1, player2);
        
        // Übersicht
        updateOverview();
        
        // Arena-Auswahl
        updateArenaSelection();
        
        // Equipment-Auswahl
        updateEquipmentSelection();
        
        // Countdown/Status + Balken
        updateCountdown();
        updateCountdownBar();
        
        // Abbrechen-Button
        updateCancelButton();
        
        // Items beider Spieler
        updateItemSlots(player1, player2);
    }
    
    // === Update-Methoden für einzelne Bereiche ===
    
    private void updatePlayerHeads(LiveTradePlayer player1, LiveTradePlayer player2) {
        // Player 1 Kopf
        ItemStack head1 = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta1 = (SkullMeta) head1.getItemMeta();
        if (meta1 != null) {
            meta1.setOwningPlayer(player1.getPlayer());
            meta1.setDisplayName(MessageUtil.color("&a&l" + player1.getPlayer().getName()));
            List<String> lore1 = new ArrayList<>();
            lore1.add("");
            lore1.add(MessageUtil.color(getMsg("items-label", "count", String.valueOf(player1.getWagerItemCount()))));
            lore1.add(MessageUtil.color(getMsg("money-label", "amount", String.format("%.2f", player1.getWagerMoney()))));
            lore1.add("");
            lore1.add(player1.hasConfirmed() ? 
                MessageUtil.color(getMsg("status-ready")) : 
                MessageUtil.color(getMsg("status-waiting")));
            meta1.setLore(lore1);
            head1.setItemMeta(meta1);
        }
        inventory.setItem(PLAYER1_HEAD_SLOT, head1);
        
        // Player 2 Kopf
        ItemStack head2 = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta2 = (SkullMeta) head2.getItemMeta();
        if (meta2 != null) {
            meta2.setOwningPlayer(player2.getPlayer());
            meta2.setDisplayName(MessageUtil.color("&c&l" + player2.getPlayer().getName()));
            List<String> lore2 = new ArrayList<>();
            lore2.add("");
            lore2.add(MessageUtil.color(getMsg("items-label", "count", String.valueOf(player2.getWagerItemCount()))));
            lore2.add(MessageUtil.color(getMsg("money-label", "amount", String.format("%.2f", player2.getWagerMoney()))));
            lore2.add("");
            lore2.add(player2.hasConfirmed() ? 
                MessageUtil.color(getMsg("status-ready")) : 
                MessageUtil.color(getMsg("status-waiting")));
            meta2.setLore(lore2);
            head2.setItemMeta(meta2);
        }
        inventory.setItem(PLAYER2_HEAD_SLOT, head2);
    }
    
    private void updateOverview() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(MessageUtil.color("&7━━━━━━━━━━━━━━━━━━━━"));
        
        Arena arena = session.getSelectedArena();
        EquipmentSet equipment = session.getSelectedEquipment();
        
        if (arena != null) {
            lore.add(MessageUtil.color(getMsg("arena-selected", "arena", arena.getDisplayName())));
        } else {
            lore.add(MessageUtil.color(getMsg("arena-not-selected")));
        }
        
        if (equipment != null) {
            lore.add(MessageUtil.color(getMsg("equipment-selected", "equipment", equipment.getDisplayName())));
        } else {
            lore.add(MessageUtil.color(getMsg("equipment-not-selected")));
        }
        
        lore.add(MessageUtil.color("&7━━━━━━━━━━━━━━━━━━━━"));
        lore.add("");
        
        if (session.isConfigurationComplete()) {
            lore.add(MessageUtil.color(getMsg("ready-to-confirm")));
        } else {
            lore.add(MessageUtil.color(getMsg("select-arena-equipment")));
        }
        
        ItemStack overview = createButton(Material.BOOK, getMsg("overview-title"), lore);
        inventory.setItem(OVERVIEW_SLOT, overview);
    }
    
    private void updateArenaSelection() {
        Arena selected = session.getSelectedArena();
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (selected != null) {
            lore.add(MessageUtil.color(getMsg("arena-current", "arena", selected.getDisplayName())));
        } else {
            lore.add(MessageUtil.color(getMsg("arena-none-selected")));
        }
        
        lore.add("");
        lore.add(MessageUtil.color(getMsg("arena-left-click")));
        lore.add(MessageUtil.color(getMsg("arena-right-click")));
        
        Material material = selected != null ? Material.GRASS_BLOCK : Material.BARRIER;
        ItemStack item = createButton(material, getMsg("arena-select-title"), lore);
        inventory.setItem(ARENA_SELECT_SLOT, item);
    }
    
    private void updateEquipmentSelection() {
        EquipmentSet selected = session.getSelectedEquipment();
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (selected != null) {
            lore.add(MessageUtil.color(getMsg("equipment-current", "equipment", selected.getDisplayName())));
        } else {
            lore.add(MessageUtil.color(getMsg("equipment-none-selected")));
        }
        
        lore.add("");
        lore.add(MessageUtil.color(getMsg("equipment-left-click")));
        lore.add(MessageUtil.color(getMsg("equipment-right-click")));
        
        Material material = selected != null ? Material.DIAMOND_CHESTPLATE : Material.BARRIER;
        ItemStack item = createButton(material, getMsg("equipment-select-title"), lore);
        inventory.setItem(EQUIPMENT_SELECT_SLOT, item);
    }
    
    private void updateCountdown() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (session.isCountdownActive()) {
            int countdown = session.getCountdown();
            lore.add(MessageUtil.color(getMsg("countdown-both-ready")));
            lore.add("");
            lore.add(MessageUtil.color(getMsg("countdown-match-starts")));
            lore.add(MessageUtil.color(getMsg("countdown-seconds", "seconds", String.valueOf(countdown))));
            
            ItemStack item = createButton(Material.LIME_CONCRETE, getMsg("countdown-title", "seconds", String.valueOf(countdown)), lore);
            inventory.setItem(COUNTDOWN_SLOT, item);
        } else {
            LiveTradePlayer p1 = session.getPlayer1();
            LiveTradePlayer p2 = session.getPlayer2();
            
            lore.add(MessageUtil.color(getMsg("status-both-must-confirm")));
            lore.add("");
            lore.add(MessageUtil.color(p1.getPlayer().getName() + ": " + 
                (p1.hasConfirmed() ? getMsg("status-ready") : getMsg("status-waiting-short"))));
            lore.add(MessageUtil.color(p2.getPlayer().getName() + ": " + 
                (p2.hasConfirmed() ? getMsg("status-ready") : getMsg("status-waiting-short"))));
            
            ItemStack item = createButton(Material.CLOCK, getMsg("status-title"), lore);
            inventory.setItem(COUNTDOWN_SLOT, item);
        }
    }
    
    /**
     * Aktualisiert den Countdown-Balken mit abnehmenden grünen Blöcken.
     */
    private void updateCountdownBar() {
        if (session.isCountdownActive()) {
            int countdown = session.getCountdown();
            int maxCountdown = 5; // Standard Countdown
            
            // Berechne wie viele grüne Blöcke angezeigt werden sollen (1-5)
            int greenBlocks = Math.max(1, countdown);
            
            // Erstelle den Balken
            StringBuilder bar = new StringBuilder();
            for (int i = 0; i < maxCountdown; i++) {
                if (i < greenBlocks) {
                    bar.append("&a█");
                } else {
                    bar.append("&8█");
                }
            }
            
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(MessageUtil.color(bar.toString()));
            lore.add("");
            lore.add(MessageUtil.color(getMsg("countdown-fight-soon")));
            
            // Material basierend auf verbleibender Zeit
            Material material;
            if (countdown >= 4) {
                material = Material.LIME_STAINED_GLASS_PANE;
            } else if (countdown >= 2) {
                material = Material.YELLOW_STAINED_GLASS_PANE;
            } else {
                material = Material.ORANGE_STAINED_GLASS_PANE;
            }
            
            ItemStack item = createButton(material, "&e" + bar.toString(), lore);
            inventory.setItem(COUNTDOWN_BAR_SLOT, item);
        } else {
            // Kein Countdown aktiv - zeige Info
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(MessageUtil.color(getMsg("countdown-bar-info")));
            
            ItemStack item = createButton(Material.GRAY_STAINED_GLASS_PANE, "&8&l⬜⬜⬜⬜⬜", lore);
            inventory.setItem(COUNTDOWN_BAR_SLOT, item);
        }
    }
    
    private void updateMoneyDisplays(LiveTradePlayer player1, LiveTradePlayer player2) {
        // Player 1 Geld
        List<String> lore1 = new ArrayList<>();
        lore1.add("");
        lore1.add(MessageUtil.color(getMsg("money-stake", "amount", String.format("%.2f", player1.getWagerMoney()))));
        
        // Nur der eigene Spieler kann sein Geld ändern
        if (isPlayer1()) {
            lore1.add("");
            lore1.add(MessageUtil.color(getMsg("money-left-click")));
            lore1.add(MessageUtil.color(getMsg("money-right-click")));
            lore1.add(MessageUtil.color(getMsg("money-shift-left")));
            lore1.add(MessageUtil.color(getMsg("money-shift-right")));
            lore1.add(MessageUtil.color(getMsg("money-q-key")));
        }
        
        ItemStack money1 = createButton(Material.GOLD_INGOT, "&6&l$" + String.format("%.0f", player1.getWagerMoney()), lore1);
        inventory.setItem(PLAYER1_MONEY_SLOT, money1);
        
        // Player 2 Geld
        List<String> lore2 = new ArrayList<>();
        lore2.add("");
        lore2.add(MessageUtil.color(getMsg("money-stake", "amount", String.format("%.2f", player2.getWagerMoney()))));
        
        // Nur der eigene Spieler kann sein Geld ändern
        if (!isPlayer1()) {
            lore2.add("");
            lore2.add(MessageUtil.color(getMsg("money-left-click")));
            lore2.add(MessageUtil.color(getMsg("money-right-click")));
            lore2.add(MessageUtil.color(getMsg("money-shift-left")));
            lore2.add(MessageUtil.color(getMsg("money-shift-right")));
            lore2.add(MessageUtil.color(getMsg("money-q-key")));
        }
        
        ItemStack money2 = createButton(Material.GOLD_INGOT, "&6&l$" + String.format("%.0f", player2.getWagerMoney()), lore2);
        inventory.setItem(PLAYER2_MONEY_SLOT, money2);
    }
    
    private void updateConfirmButtons(LiveTradePlayer player1, LiveTradePlayer player2) {
        boolean canConfirm = session.isConfigurationComplete();
        
        // Player 1 Bestätigung
        if (player1.hasConfirmed()) {
            inventory.setItem(PLAYER1_CONFIRM_SLOT, createButton(Material.LIME_WOOL, 
                getMsg("confirm-title-ready", "player", player1.getPlayer().getName()),
                "", getMsg("confirm-click-to-withdraw")));
        } else {
            Material mat = canConfirm ? Material.YELLOW_WOOL : Material.GRAY_WOOL;
            String title = canConfirm ? getMsg("confirm-title-wait") : getMsg("confirm-title-disabled");
            List<String> lore = new ArrayList<>();
            lore.add("");
            if (!canConfirm) {
                lore.add(MessageUtil.color(getMsg("confirm-select-first")));
            } else if (isPlayer1()) {
                lore.add(MessageUtil.color(getMsg("confirm-click-to-confirm")));
            } else {
                lore.add(MessageUtil.color(getMsg("confirm-waiting-for", "player", player1.getPlayer().getName())));
            }
            inventory.setItem(PLAYER1_CONFIRM_SLOT, createButton(mat, title, lore));
        }
        
        // Player 2 Bestätigung
        if (player2.hasConfirmed()) {
            inventory.setItem(PLAYER2_CONFIRM_SLOT, createButton(Material.LIME_WOOL, 
                getMsg("confirm-title-ready", "player", player2.getPlayer().getName()),
                "", getMsg("confirm-click-to-withdraw")));
        } else {
            Material mat = canConfirm ? Material.YELLOW_WOOL : Material.GRAY_WOOL;
            String title = canConfirm ? getMsg("confirm-title-wait") : getMsg("confirm-title-disabled");
            List<String> lore = new ArrayList<>();
            lore.add("");
            if (!canConfirm) {
                lore.add(MessageUtil.color(getMsg("confirm-select-first")));
            } else if (!isPlayer1()) {
                lore.add(MessageUtil.color(getMsg("confirm-click-to-confirm")));
            } else {
                lore.add(MessageUtil.color(getMsg("confirm-waiting-for", "player", player2.getPlayer().getName())));
            }
            inventory.setItem(PLAYER2_CONFIRM_SLOT, createButton(mat, title, lore));
        }
    }
    
    private void updateCancelButton() {
        ItemStack cancel = createButton(Material.RED_WOOL, getMsg("cancel-title"),
            "", getMsg("cancel-click"),
            getMsg("cancel-items-returned"));
        inventory.setItem(CANCEL_SLOT, cancel);
    }
    
    private void updateItemSlots(LiveTradePlayer player1, LiveTradePlayer player2) {
        // Player 1 Items (links)
        List<ItemStack> p1Items = player1.getWagerItems();
        for (int i = 0; i < PLAYER1_ITEM_SLOTS.size(); i++) {
            int slot = PLAYER1_ITEM_SLOTS.get(i);
            if (i < p1Items.size()) {
                inventory.setItem(slot, p1Items.get(i).clone());
            } else {
                inventory.setItem(slot, null);
            }
        }
        
        // Player 2 Items (rechts)
        List<ItemStack> p2Items = player2.getWagerItems();
        for (int i = 0; i < PLAYER2_ITEM_SLOTS.size(); i++) {
            int slot = PLAYER2_ITEM_SLOTS.get(i);
            if (i < p2Items.size()) {
                inventory.setItem(slot, p2Items.get(i).clone());
            } else {
                inventory.setItem(slot, null);
            }
        }
    }
    
    // === Click Handling ===
    
    /**
     * Verarbeitet einen Klick im GUI.
     */
    public boolean handleClick(InventoryClickEvent event) {
        if (session.isEnded()) {
            event.setCancelled(true);
            return true;
        }
        
        int slot = event.getRawSlot();
        ClickType click = event.getClick();
        ItemStack cursor = event.getCursor();
        
        // Klick im Player-Inventar (unten)
        if (slot >= 54) {
            return handleBottomInventoryClick(event);
        }
        
        // Klick im Trade-GUI (oben)
        event.setCancelled(true);
        
        // Prüfe ob Spieler ein Item in der Hand hält (Cursor) und auf eigenen Slot klickt
        if (cursor != null && !cursor.getType().isAir() && isOwnItemSlot(slot)) {
            // Item platzieren
            if (tradePlayer.getWagerItemCount() < 12) {
                tradePlayer.addWagerItem(cursor.clone());
                player.setItemOnCursor(null);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
                session.update();
            } else {
                MessageUtil.sendMessage(player, getMsg("max-items-reached"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            }
            return true;
        }
        
        // Prüfe welcher Bereich geklickt wurde
        if (isOwnItemSlot(slot)) {
            handleOwnItemSlotClick(slot, click);
        } else if (isOtherItemSlot(slot)) {
            // Gegnerische Slots - nicht interagierbar
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.3f, 1.0f);
        } else if (slot == ARENA_SELECT_SLOT) {
            handleArenaClick(click);
        } else if (slot == EQUIPMENT_SELECT_SLOT) {
            handleEquipmentClick(click);
        } else if (slot == getOwnMoneySlot()) {
            handleMoneyClick(click);
        } else if (slot == getOwnConfirmSlot()) {
            handleConfirmClick();
        } else if (slot == CANCEL_SLOT) {
            handleCancelClick();
        }
        
        return true;
    }
    
    /**
     * Verarbeitet Drag-Events für echtes Drag & Drop.
     */
    public boolean handleDrag(InventoryDragEvent event) {
        if (session.isEnded()) {
            event.setCancelled(true);
            return true;
        }
        
        Set<Integer> topSlots = new HashSet<>();
        for (int slot : event.getRawSlots()) {
            if (slot < 54) {
                topSlots.add(slot);
            }
        }
        
        // Wenn keine Slots im Trade-GUI betroffen sind, erlauben
        if (topSlots.isEmpty()) {
            return true;
        }
        
        // Prüfe ob ALLE betroffenen Slots eigene Slots sind
        for (int slot : topSlots) {
            if (!isOwnItemSlot(slot)) {
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.3f, 1.0f);
                return true;
            }
        }
        
        // Prüfe ob noch Platz ist
        int currentItems = tradePlayer.getWagerItemCount();
        if (currentItems >= 12) {
            event.setCancelled(true);
            MessageUtil.sendMessage(player, getMsg("max-items-reached"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return true;
        }
        
        // Items zu eigenen Slots hinzufügen (verzögert nach Event)
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean itemAdded = false;
            for (int slot : topSlots) {
                if (isOwnItemSlot(slot)) {
                    ItemStack item = inventory.getItem(slot);
                    if (item != null && !item.getType().isAir()) {
                        if (tradePlayer.getWagerItemCount() < 12) {
                            tradePlayer.addWagerItem(item.clone());
                            itemAdded = true;
                        }
                        inventory.setItem(slot, null);
                    }
                }
            }
            if (itemAdded) {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
            }
            session.update();
        });
        
        return true;
    }
    
    /**
     * Verarbeitet Klicks im unteren Spieler-Inventar.
     * Erlaubt Shift-Klick UND normales Aufnehmen für Drag & Drop.
     */
    private boolean handleBottomInventoryClick(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        
        if (event.isShiftClick() && clicked != null && !clicked.getType().isAir()) {
            // Shift-Klick: Item zum Trade hinzufügen
            event.setCancelled(true);
            
            if (tradePlayer.getWagerItemCount() >= 12) {
                MessageUtil.sendMessage(player, getMsg("max-items-reached"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                return true;
            }
            
            tradePlayer.addWagerItem(clicked.clone());
            event.setCurrentItem(null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
            session.update();
        }
        // Normales Aufnehmen für Drag & Drop ist erlaubt (kein setCancelled)
        
        return true;
    }
    
    /**
     * Verarbeitet das Platzieren eines Items auf einem eigenen Slot.
     */
    private void handlePlaceItem(int slot, ItemStack cursor) {
        if (cursor == null || cursor.getType().isAir()) return;
        
        if (tradePlayer.getWagerItemCount() >= 12) {
            MessageUtil.sendMessage(player, getMsg("max-items-reached"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return;
        }
        
        tradePlayer.addWagerItem(cursor.clone());
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
        session.update();
    }
    
    /**
     * Verarbeitet Klick auf eigenen Item-Slot (Item entfernen).
     */
    private void handleOwnItemSlotClick(int slot, ClickType click) {
        List<Integer> ownSlots = isPlayer1() ? PLAYER1_ITEM_SLOTS : PLAYER2_ITEM_SLOTS;
        int index = ownSlots.indexOf(slot);
        
        if (index >= 0 && index < tradePlayer.getWagerItemCount()) {
            // Item entfernen und zurückgeben
            ItemStack removed = tradePlayer.removeWagerItem(index);
            if (removed != null) {
                player.getInventory().addItem(removed);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 0.8f);
                session.update();
            }
        }
    }
    
    /**
     * Verarbeitet Arena-Auswahl.
     */
    private void handleArenaClick(ClickType click) {
        Collection<Arena> arenas = plugin.getArenaManager().getArenas().values();
        if (arenas.isEmpty()) {
            MessageUtil.sendMessage(player, getMsg("arena-none-available"));
            return;
        }
        
        List<Arena> arenaList = new ArrayList<>(arenas);
        Arena current = session.getSelectedArena();
        int currentIndex = current != null ? arenaList.indexOf(current) : -1;
        
        int nextIndex;
        if (click.isRightClick()) {
            // Vorherige
            nextIndex = currentIndex <= 0 ? arenaList.size() - 1 : currentIndex - 1;
        } else {
            // Nächste
            nextIndex = currentIndex >= arenaList.size() - 1 ? 0 : currentIndex + 1;
        }
        
        Arena newArena = arenaList.get(nextIndex);
        session.setSelectedArena(newArena);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }
    
    /**
     * Verarbeitet Equipment-Auswahl.
     */
    private void handleEquipmentClick(ClickType click) {
        // Equipment abhängig von Arena
        Arena arena = session.getSelectedArena();
        List<EquipmentSet> equipments;
        
        if (arena != null) {
            equipments = plugin.getEquipmentManager().getAllowedEquipmentForWorld(arena.getArenaWorld());
        } else {
            equipments = new ArrayList<>(plugin.getEquipmentManager().getEquipmentSets().values());
        }
        
        if (equipments.isEmpty()) {
            MessageUtil.sendMessage(player, getMsg("equipment-none-available"));
            return;
        }
        
        EquipmentSet current = session.getSelectedEquipment();
        int currentIndex = current != null ? equipments.indexOf(current) : -1;
        
        int nextIndex;
        if (click.isRightClick()) {
            // Vorherige
            nextIndex = currentIndex <= 0 ? equipments.size() - 1 : currentIndex - 1;
        } else {
            // Nächste
            nextIndex = currentIndex >= equipments.size() - 1 ? 0 : currentIndex + 1;
        }
        
        EquipmentSet newEquipment = equipments.get(nextIndex);
        session.setSelectedEquipment(newEquipment);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }
    
    /**
     * Verarbeitet Geld-Änderung.
     * Linksklick: +$10 / Rechtsklick: -$10
     * Shift+Links: +$100 / Shift+Rechts: -$100
     * Q-Taste: +$1 / Shift+Q: +$1000
     */
    private void handleMoneyClick(ClickType click) {
        if (!plugin.hasEconomy()) {
            MessageUtil.sendMessage(player, getMsg("economy-not-available"));
            return;
        }
        
        double current = tradePlayer.getWagerMoney();
        double change;
        
        // Bestimme den Änderungsbetrag basierend auf Click-Type
        if (click == ClickType.DROP) {
            // Q-Taste: +$1
            change = 1.0;
        } else if (click == ClickType.CONTROL_DROP) {
            // Shift+Q: +$1000
            change = 1000.0;
        } else if (click.isShiftClick()) {
            // Shift+Klick: $100
            change = 100.0;
        } else {
            // Normal Klick: $10
            change = 10.0;
        }
        
        double newAmount;
        if (click.isRightClick()) {
            newAmount = Math.max(0, current - change);
        } else {
            newAmount = current + change;
        }
        
        if (tradePlayer.setWagerMoney(newAmount)) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
            session.update();
        }
    }
    
    /**
     * Verarbeitet Bestätigungs-Klick.
     */
    private void handleConfirmClick() {
        if (tradePlayer.hasConfirmed()) {
            tradePlayer.cancelConfirmation();
        } else {
            tradePlayer.confirm();
        }
    }
    
    /**
     * Verarbeitet Abbrechen-Klick.
     */
    private void handleCancelClick() {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
        session.abort();
    }
    
    /**
     * Wird aufgerufen wenn das GUI geschlossen wird.
     */
    public void handleClose(InventoryCloseEvent event) {
        if (!session.isEnded() && !session.isConfirmed()) {
            // GUI wurde geschlossen ohne Abschluss -> Abbrechen
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!session.isEnded()) {
                    session.abort();
                }
            });
        }
    }
    
    // === Helper-Methoden ===
    
    /**
     * Prüft ob dieser Spieler Player 1 ist.
     */
    private boolean isPlayer1() {
        return tradePlayer.getPlayerId().equals(session.getPlayer1().getPlayerId());
    }
    
    /**
     * Prüft ob ein Slot ein eigener Item-Slot ist.
     */
    private boolean isOwnItemSlot(int slot) {
        return isPlayer1() ? PLAYER1_ITEM_SLOTS.contains(slot) : PLAYER2_ITEM_SLOTS.contains(slot);
    }
    
    /**
     * Prüft ob ein Slot ein Item-Slot des Gegners ist.
     */
    private boolean isOtherItemSlot(int slot) {
        return isPlayer1() ? PLAYER2_ITEM_SLOTS.contains(slot) : PLAYER1_ITEM_SLOTS.contains(slot);
    }
    
    /**
     * Gibt den eigenen Geld-Slot zurück.
     */
    private int getOwnMoneySlot() {
        return isPlayer1() ? PLAYER1_MONEY_SLOT : PLAYER2_MONEY_SLOT;
    }
    
    /**
     * Gibt den eigenen Bestätigungs-Slot zurück.
     */
    private int getOwnConfirmSlot() {
        return isPlayer1() ? PLAYER1_CONFIRM_SLOT : PLAYER2_CONFIRM_SLOT;
    }
    
    private ItemStack createFiller(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createButton(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.color(name));
            if (lore.length > 0) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    coloredLore.add(MessageUtil.color(line));
                }
                meta.setLore(coloredLore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createButton(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.color(name));
            if (lore != null && !lore.isEmpty()) {
                item.setItemMeta(meta);
                meta = item.getItemMeta();
                if (meta != null) {
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
            } else {
                item.setItemMeta(meta);
            }
        }
        return item;
    }
    
    public Inventory getInventory() {
        return inventory;
    }
    
    public boolean isOpened() {
        return opened;
    }
}
