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
 * Geld-Auswahl speziell für das Beantworten einer Anfrage.
 */
public class ResponseMoneySelectionGui extends AbstractWagerGui {
    
    private static final int SIZE = 54;
    
    private static final int[] AMOUNT_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final double[] AMOUNTS = {100, 500, 1000, 5000, 10000, 50000, 100000};
    
    private static final int PLUS_1000_SLOT = 28;
    private static final int PLUS_100_SLOT = 29;
    private static final int PLUS_10_SLOT = 30;
    private static final int CURRENT_SLOT = 31;
    private static final int MINUS_10_SLOT = 32;
    private static final int MINUS_100_SLOT = 33;
    private static final int MINUS_1000_SLOT = 34;
    
    private static final int MATCH_SENDER_SLOT = 22;
    
    private static final int CLEAR_SLOT = 40;
    private static final int BACK_SLOT = 45;
    private static final int CONFIRM_SLOT = 53;
    private static final int INFO_SLOT = 4;
    
    private final CommandRequest incomingRequest;
    
    public ResponseMoneySelectionGui(EventPlugin plugin, Player player, WagerSession session, CommandRequest request) {
        super(plugin, player, session);
        this.incomingRequest = request;
    }
    
    @Override
    public void open() {
        inventory = Bukkit.createInventory(null, SIZE, 
            MessageUtil.color("&6&lDein Geld-Einsatz"));
        
        buildLayout();
        openInventory();
    }
    
    private void buildLayout() {
        fillBorder(Material.YELLOW_STAINED_GLASS_PANE);
        
        inventory.setItem(INFO_SLOT, createInfoItem());
        
        createAmountButtons();
        createAdjustButtons();
        createMatchSenderButton();
        updateCurrentAmount();
        
        inventory.setItem(CLEAR_SLOT, createButton(Material.BARRIER,
            "&c&lBetrag löschen", "&7Setzt auf 0."));
        
        inventory.setItem(BACK_SLOT, createBackButton());
        inventory.setItem(CONFIRM_SLOT, createConfirmButton());
    }
    
    private ItemStack createInfoItem() {
        double balance = plugin.hasEconomy() ? plugin.getEconomy().getBalance(player) : 0;
        double senderMoney = incomingRequest.getMoney();
        
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(MessageUtil.color("&7Dein Guthaben:"));
        lore.add(MessageUtil.color("&6$" + String.format("%,.2f", balance)));
        lore.add("");
        lore.add(MessageUtil.color("&7Gegner setzt:"));
        lore.add(MessageUtil.color("&6$" + String.format("%,.2f", senderMoney)));
        
        return createButton(Material.GOLD_BLOCK, "&e&lGeld-Einsatz", lore);
    }
    
    private void createAmountButtons() {
        for (int i = 0; i < AMOUNT_SLOTS.length && i < AMOUNTS.length; i++) {
            double amount = AMOUNTS[i];
            boolean canAfford = canAfford(amount);
            
            Material material = canAfford ? Material.GOLD_INGOT : Material.IRON_INGOT;
            String color = canAfford ? "&6" : "&7";
            
            List<String> lore = new ArrayList<>();
            lore.add("");
            if (!canAfford) {
                lore.add(MessageUtil.color("&cNicht genug Geld!"));
            }
            
            inventory.setItem(AMOUNT_SLOTS[i], createButton(material,
                color + "$" + String.format("%,.0f", amount), lore));
        }
    }
    
    private void createAdjustButtons() {
        inventory.setItem(PLUS_1000_SLOT, createButton(Material.LIME_WOOL, "&a&l+$1.000"));
        inventory.setItem(PLUS_100_SLOT, createButton(Material.LIME_WOOL, "&a+$100"));
        inventory.setItem(PLUS_10_SLOT, createButton(Material.LIME_WOOL, "&a+$10"));
        
        inventory.setItem(MINUS_10_SLOT, createButton(Material.RED_WOOL, "&c-$10"));
        inventory.setItem(MINUS_100_SLOT, createButton(Material.RED_WOOL, "&c-$100"));
        inventory.setItem(MINUS_1000_SLOT, createButton(Material.RED_WOOL, "&c&l-$1.000"));
    }
    
    private void createMatchSenderButton() {
        double senderMoney = incomingRequest.getMoney();
        
        inventory.setItem(MATCH_SENDER_SLOT, createButton(Material.SUNFLOWER,
            "&e&lGegner angleichen",
            "",
            "&7Setzt auf: &6$" + String.format("%,.2f", senderMoney),
            "",
            "&eKlicke um den Betrag des",
            "&eGegners zu übernehmen."));
    }
    
    private void updateCurrentAmount() {
        double current = session.getWagerMoney();
        double balance = getBalance();
        
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(MessageUtil.color("&7Aktueller Einsatz:"));
        lore.add(MessageUtil.color("&6&l$" + String.format("%,.2f", current)));
        lore.add("");
        lore.add(MessageUtil.color("&7Verbleibendes Guthaben:"));
        lore.add(MessageUtil.color("&e$" + String.format("%,.2f", balance)));
        
        Material material = current > 0 ? Material.EMERALD_BLOCK : Material.COAL_BLOCK;
        inventory.setItem(CURRENT_SLOT, createButton(material, "&a&lAktueller Einsatz", lore));
    }
    
    @Override
    public boolean handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return true;
        
        // Feste Beträge
        for (int i = 0; i < AMOUNT_SLOTS.length; i++) {
            if (slot == AMOUNT_SLOTS[i]) {
                setAmount(AMOUNTS[i]);
                return true;
            }
        }
        
        // Plus/Minus
        switch (slot) {
            case PLUS_1000_SLOT: adjustAmount(1000); return true;
            case PLUS_100_SLOT: adjustAmount(100); return true;
            case PLUS_10_SLOT: adjustAmount(10); return true;
            case MINUS_10_SLOT: adjustAmount(-10); return true;
            case MINUS_100_SLOT: adjustAmount(-100); return true;
            case MINUS_1000_SLOT: adjustAmount(-1000); return true;
        }
        
        // Match Sender
        if (slot == MATCH_SENDER_SLOT) {
            setAmount(incomingRequest.getMoney());
            return true;
        }
        
        // Andere Buttons
        switch (slot) {
            case CLEAR_SLOT:
                setAmount(0);
                return true;
            case BACK_SLOT:
            case CONFIRM_SLOT:
                playClickSound();
                switchTo(new ResponseGui(plugin, player, session, incomingRequest));
                return true;
        }
        
        return true;
    }
    
    private void setAmount(double amount) {
        double balance = getBalance() + session.getWagerMoney();
        amount = Math.max(0, Math.min(amount, balance));
        amount = Math.round(amount * 100.0) / 100.0;
        
        session.setWagerMoney(amount);
        playClickSound();
        updateCurrentAmount();
        createAmountButtons();
    }
    
    private void adjustAmount(double delta) {
        setAmount(session.getWagerMoney() + delta);
    }
    
    private boolean canAfford(double amount) {
        double available = getBalance() + session.getWagerMoney();
        return available >= amount;
    }
    
    private double getBalance() {
        return plugin.hasEconomy() ? plugin.getEconomy().getBalance(player) : 0;
    }
    
    @Override
    public void onClose() {
        // Geld bleibt in Session
    }
}
