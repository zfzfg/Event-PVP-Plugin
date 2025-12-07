package de.zfzfg.pvpwager.gui;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI zur Auswahl des Geld-Einsatzes.
 * Bietet vordefinierte Beträge und eine manuelle Eingabe.
 */
public class MoneySelectionGui extends AbstractWagerGui {
    
    // Layout
    private static final int SIZE = 54;
    
    // Betrag-Buttons
    private static final int[] AMOUNT_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final double[] AMOUNTS = {100, 500, 1000, 5000, 10000, 50000, 100000};
    
    // Schnell-Buttons
    private static final int PLUS_1000_SLOT = 28;
    private static final int PLUS_100_SLOT = 29;
    private static final int PLUS_10_SLOT = 30;
    private static final int CURRENT_SLOT = 31;
    private static final int MINUS_10_SLOT = 32;
    private static final int MINUS_100_SLOT = 33;
    private static final int MINUS_1000_SLOT = 34;
    
    // Prozent-Buttons
    private static final int PERCENT_25_SLOT = 19;
    private static final int PERCENT_50_SLOT = 21;
    private static final int PERCENT_75_SLOT = 23;
    private static final int ALL_IN_SLOT = 25;
    
    // Navigation
    private static final int CLEAR_SLOT = 40;
    private static final int BACK_SLOT = 45;
    private static final int CONFIRM_SLOT = 53;
    private static final int INFO_SLOT = 4;
    
    public MoneySelectionGui(EventPlugin plugin, Player player, WagerSession session) {
        super(plugin, player, session);
    }
    
    @Override
    public void open() {
        inventory = Bukkit.createInventory(null, SIZE, 
            MessageUtil.color("&6&lGeld einsetzen"));
        
        buildLayout();
        openInventory();
    }
    
    private void buildLayout() {
        // Hintergrund
        fillBorder(Material.YELLOW_STAINED_GLASS_PANE);
        
        // Info
        inventory.setItem(INFO_SLOT, createInfoItem());
        
        // Feste Beträge
        createAmountButtons();
        
        // Plus/Minus Buttons
        createAdjustButtons();
        
        // Prozent-Buttons
        createPercentButtons();
        
        // Aktueller Betrag
        updateCurrentAmount();
        
        // Clear
        inventory.setItem(CLEAR_SLOT, createButton(Material.BARRIER,
            "&c&lBetrag löschen",
            "&7Setzt den Einsatz auf 0."));
        
        // Navigation
        inventory.setItem(BACK_SLOT, createBackButton());
        inventory.setItem(CONFIRM_SLOT, createConfirmButton());
    }
    
    private ItemStack createInfoItem() {
        double balance = plugin.hasEconomy() ? plugin.getEconomy().getBalance(player) : 0;
        
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(MessageUtil.color("&7Dein Guthaben:"));
        lore.add(MessageUtil.color("&6$" + String.format("%,.2f", balance)));
        lore.add("");
        lore.add(MessageUtil.color("&7Wähle einen Betrag aus"));
        lore.add(MessageUtil.color("&7oder passe ihn an."));
        
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
            if (canAfford) {
                lore.add(MessageUtil.color("&aKlicke um diesen"));
                lore.add(MessageUtil.color("&aBetrag zu wählen."));
            } else {
                lore.add(MessageUtil.color("&cNicht genug Geld!"));
            }
            
            inventory.setItem(AMOUNT_SLOTS[i], createButton(material,
                color + "$" + String.format("%,.0f", amount), lore));
        }
    }
    
    private void createAdjustButtons() {
        // Plus-Buttons
        inventory.setItem(PLUS_1000_SLOT, createButton(Material.LIME_WOOL,
            "&a&l+$1.000", "&7Erhöht den Einsatz."));
        inventory.setItem(PLUS_100_SLOT, createButton(Material.LIME_WOOL,
            "&a+$100", "&7Erhöht den Einsatz."));
        inventory.setItem(PLUS_10_SLOT, createButton(Material.LIME_WOOL,
            "&a+$10", "&7Erhöht den Einsatz."));
        
        // Minus-Buttons
        inventory.setItem(MINUS_10_SLOT, createButton(Material.RED_WOOL,
            "&c-$10", "&7Verringert den Einsatz."));
        inventory.setItem(MINUS_100_SLOT, createButton(Material.RED_WOOL,
            "&c-$100", "&7Verringert den Einsatz."));
        inventory.setItem(MINUS_1000_SLOT, createButton(Material.RED_WOOL,
            "&c&l-$1.000", "&7Verringert den Einsatz."));
    }
    
    private void createPercentButtons() {
        double balance = getBalance();
        
        inventory.setItem(PERCENT_25_SLOT, createButton(Material.SUNFLOWER,
            "&e25%", "&7= &6$" + String.format("%,.2f", balance * 0.25)));
        
        inventory.setItem(PERCENT_50_SLOT, createButton(Material.SUNFLOWER,
            "&e50%", "&7= &6$" + String.format("%,.2f", balance * 0.50)));
        
        inventory.setItem(PERCENT_75_SLOT, createButton(Material.SUNFLOWER,
            "&e75%", "&7= &6$" + String.format("%,.2f", balance * 0.75)));
        
        inventory.setItem(ALL_IN_SLOT, createButton(Material.NETHER_STAR,
            "&6&lALL IN!", "&7= &6$" + String.format("%,.2f", balance),
            "", "&c⚠ Setzt alles ein!"));
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
        inventory.setItem(CURRENT_SLOT, createButton(material,
            "&a&lAktueller Einsatz", lore));
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
            case PLUS_1000_SLOT:
                adjustAmount(1000);
                return true;
            case PLUS_100_SLOT:
                adjustAmount(100);
                return true;
            case PLUS_10_SLOT:
                adjustAmount(10);
                return true;
            case MINUS_10_SLOT:
                adjustAmount(-10);
                return true;
            case MINUS_100_SLOT:
                adjustAmount(-100);
                return true;
            case MINUS_1000_SLOT:
                adjustAmount(-1000);
                return true;
        }
        
        // Prozent
        double balance = getBalance() + session.getWagerMoney(); // Vollständiges Balance
        switch (slot) {
            case PERCENT_25_SLOT:
                setAmount(balance * 0.25);
                return true;
            case PERCENT_50_SLOT:
                setAmount(balance * 0.50);
                return true;
            case PERCENT_75_SLOT:
                setAmount(balance * 0.75);
                return true;
            case ALL_IN_SLOT:
                setAmount(balance);
                return true;
        }
        
        // Andere Buttons
        switch (slot) {
            case CLEAR_SLOT:
                setAmount(0);
                return true;
            case BACK_SLOT:
                playClickSound();
                switchTo(new WagerMainGui(plugin, player, session));
                return true;
            case CONFIRM_SLOT:
                playSuccessSound();
                switchTo(new WagerMainGui(plugin, player, session));
                return true;
        }
        
        return true;
    }
    
    private void setAmount(double amount) {
        double balance = getBalance() + session.getWagerMoney();
        
        // Clamp auf verfügbares Geld
        amount = Math.max(0, Math.min(amount, balance));
        
        // Auf 2 Dezimalstellen runden
        amount = Math.round(amount * 100.0) / 100.0;
        
        session.setWagerMoney(amount);
        playClickSound();
        updateCurrentAmount();
        createAmountButtons(); // Aktualisiere Verfügbarkeit
    }
    
    private void adjustAmount(double delta) {
        double current = session.getWagerMoney();
        setAmount(current + delta);
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
        // Geld bleibt in der Session
    }
}
