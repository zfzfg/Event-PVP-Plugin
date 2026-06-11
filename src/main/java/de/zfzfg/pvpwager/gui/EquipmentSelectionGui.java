package de.zfzfg.pvpwager.gui;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.EquipmentSet;
import de.zfzfg.pvpwager.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GUI zur Auswahl der Ausrüstung für den Kampf.
 */
public class EquipmentSelectionGui extends AbstractWagerGui {
    
    private static final int SIZE = 54;
    private static final int INFO_SLOT = 4;
    private static final int BACK_SLOT = 45;
    private static final int CONFIRM_SLOT = 53;
    
    // Startslot für Equipment-Buttons
    private static final int EQUIP_START_SLOT = 10;
    
    private final List<EquipmentSet> availableEquipment = new ArrayList<>();
    
    public EquipmentSelectionGui(EventPlugin plugin, Player player, WagerSession session) {
        super(plugin, player, session);
    }
    
    @Override
    public void open() {
        inventory = Bukkit.createInventory(null, SIZE,
            MessageUtil.color(t("equipment-select-title")));
        
        loadEquipment();
        buildLayout();
        openInventory();
    }
    
    private void loadEquipment() {
        availableEquipment.clear();
        
        // Hole Equipment, das für die ausgewählte Arena erlaubt ist
        if (session.getSelectedArena() != null) {
            String worldName = session.getSelectedArena().getArenaWorld();
            availableEquipment.addAll(
                plugin.getEquipmentManager().getAllowedEquipmentForWorld(worldName));
        } else {
            // Falls keine Arena gewählt, zeige alle
            availableEquipment.addAll(
                plugin.getEquipmentManager().getEquipmentSets().values());
        }
    }
    
    private void buildLayout() {
        // Hintergrund
        fillBorder(Material.PURPLE_STAINED_GLASS_PANE);
        
        // Info
        inventory.setItem(INFO_SLOT, createInfoItem());
        
        // Equipment anzeigen
        displayEquipment();
        
        // Navigation
        inventory.setItem(BACK_SLOT, createBackButton());
        
        // Confirm nur wenn Equipment gewählt
        if (session.getSelectedEquipment() != null) {
            inventory.setItem(CONFIRM_SLOT, createConfirmButton());
        } else {
            inventory.setItem(CONFIRM_SLOT, createButton(Material.GRAY_WOOL,
                t("confirm-title"),
                t("error-select-equipment")));
        }
    }
    
    private ItemStack createInfoItem() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(MessageUtil.color(t("available-equipment-count", "count", String.valueOf(availableEquipment.size()))));
        lore.add("");
        
        if (session.getSelectedEquipment() != null) {
            lore.add(MessageUtil.color(t("selected-label")));
            lore.add(MessageUtil.color("&f" + session.getSelectedEquipment().getDisplayName()));
        } else {
            lore.add(MessageUtil.color(t("no-equipment-selected")));
        }
        
        if (session.getSelectedArena() != null) {
            lore.add("");
            lore.add(MessageUtil.color(t("filter-arena", "arena", session.getSelectedArena().getDisplayName())));
        }
        
        return createButton(Material.DIAMOND_CHESTPLATE, t("equipment-title"), lore);
    }
    
    private void displayEquipment() {
        int slot = EQUIP_START_SLOT;
        
        for (EquipmentSet equipment : availableEquipment) {
            // Überspringe Slots in den Rändern
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            
            if (slot >= SIZE - 9) break; // Nicht in letzte Reihe
            
            inventory.setItem(slot, createEquipmentItem(equipment));
            slot++;
        }
        
        // Falls keine Ausrüstung verfügbar
        if (availableEquipment.isEmpty()) {
            inventory.setItem(22, createButton(Material.BARRIER,
                t("no-equipment-available-title"),
                "",
                t("no-equipment-available-line-1"),
                t("no-equipment-available-line-2"),
                "",
                t("no-equipment-available-lore")));
        }
    }
    
    private ItemStack createEquipmentItem(EquipmentSet equipment) {
        boolean isSelected = session.getSelectedEquipment() != null && 
                            session.getSelectedEquipment().getId().equals(equipment.getId());
        
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        // Rüstungsinfo
        lore.add(MessageUtil.color(t("armor-label")));
        if (equipment.getHelmet() != null) {
            lore.add(MessageUtil.color(t("armor-piece-label", "part", t("armor-helmet"), "material", formatMaterial(equipment.getHelmet().getType()))));
        }
        if (equipment.getChestplate() != null) {
            lore.add(MessageUtil.color(t("armor-piece-label", "part", t("armor-chestplate"), "material", formatMaterial(equipment.getChestplate().getType()))));
        }
        if (equipment.getLeggings() != null) {
            lore.add(MessageUtil.color(t("armor-piece-label", "part", t("armor-leggings"), "material", formatMaterial(equipment.getLeggings().getType()))));
        }
        if (equipment.getBoots() != null) {
            lore.add(MessageUtil.color(t("armor-piece-label", "part", t("armor-boots"), "material", formatMaterial(equipment.getBoots().getType()))));
        }
        
        // Inventar-Items
        if (equipment.getInventory() != null && !equipment.getInventory().isEmpty()) {
            lore.add("");
            lore.add(MessageUtil.color(t("inventory-label")));
            int count = 0;
            for (Map.Entry<Integer, ItemStack> entry : equipment.getInventory().entrySet()) {
                if (count >= 3) {
                    lore.add(MessageUtil.color(t("inventory-more")));
                    break;
                }
                ItemStack item = entry.getValue();
                lore.add(MessageUtil.color("  &8• &f" + formatMaterial(item.getType()) + " x" + item.getAmount()));
                count++;
            }
        }
        
        lore.add("");
        
        if (isSelected) {
            lore.add(MessageUtil.color(t("selected-check")));
        } else {
            lore.add(MessageUtil.color(t("click-to-select")));
        }
        
        // Material basierend auf Rüstung
        Material material = getMaterialForEquipment(equipment);
        if (isSelected) {
            material = Material.EMERALD_BLOCK;
        }
        
        String prefix = isSelected ? "&a&l✔ " : "&e";
        return createButton(material, t("equipment-item-title", "name", prefix + equipment.getDisplayName()), lore);
    }
    
    private Material getMaterialForEquipment(EquipmentSet equipment) {
        // Versuche Material aus Chestplate zu nehmen
        if (equipment.getChestplate() != null) {
            Material chest = equipment.getChestplate().getType();
            if (chest.name().contains("DIAMOND")) return Material.DIAMOND_CHESTPLATE;
            if (chest.name().contains("NETHERITE")) return Material.NETHERITE_CHESTPLATE;
            if (chest.name().contains("IRON")) return Material.IRON_CHESTPLATE;
            if (chest.name().contains("GOLD")) return Material.GOLDEN_CHESTPLATE;
            if (chest.name().contains("LEATHER")) return Material.LEATHER_CHESTPLATE;
            if (chest.name().contains("CHAIN")) return Material.CHAINMAIL_CHESTPLATE;
        }
        
        return Material.IRON_CHESTPLATE;
    }
    
    private String formatMaterial(Material material) {
        String name = material.name().replace("_", " ").toLowerCase();
        // Capitalize
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)));
                result.append(word.substring(1)).append(" ");
            }
        }
        return result.toString().trim();
    }
    
    @Override
    public boolean handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return true;
        
        // Navigation
        switch (slot) {
            case BACK_SLOT:
                playClickSound();
                switchTo(new WagerMainGui(plugin, player, session));
                return true;
            case CONFIRM_SLOT:
                if (session.getSelectedEquipment() != null) {
                    playSuccessSound();
                    switchTo(new WagerMainGui(plugin, player, session));
                } else {
                    playErrorSound();
                    MessageUtil.sendMessage(player, MessageUtil.color(t("error-select-equipment")));
                }
                return true;
        }
        
        // Equipment-Auswahl
        int index = getEquipmentIndexForSlot(slot);
        if (index >= 0 && index < availableEquipment.size()) {
            EquipmentSet selected = availableEquipment.get(index);
            session.setSelectedEquipment(selected);
            
            playClickSound();
            MessageUtil.sendMessage(player, MessageUtil.color(t("equipment-selected-message", "equipment", selected.getDisplayName())));
            
            // GUI neu aufbauen
            buildLayout();
        }
        
        return true;
    }
    
    private int getEquipmentIndexForSlot(int slot) {
        int checkSlot = EQUIP_START_SLOT;
        
        for (int i = 0; i < availableEquipment.size(); i++) {
            while (checkSlot % 9 == 0 || checkSlot % 9 == 8) {
                checkSlot++;
            }
            
            if (checkSlot >= SIZE - 9) break;
            
            if (checkSlot == slot) {
                return i;
            }
            
            checkSlot++;
        }
        
        return -1;
    }
    
    @Override
    public void onClose() {
        // Equipment-Auswahl bleibt in der Session
    }
}
