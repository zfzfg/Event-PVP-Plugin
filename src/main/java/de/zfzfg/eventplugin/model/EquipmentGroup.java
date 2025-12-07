package de.zfzfg.eventplugin.model;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EquipmentGroup {
    
    private final String id;
    private final ArmorSet armor;
    private final List<InventoryItem> inventory;
    
    public EquipmentGroup(String id, ConfigurationSection section) {
        this.id = id;
        
        // Lade Rüstung
        ConfigurationSection armorSection = section.getConfigurationSection("armor");
        this.armor = new ArmorSet(armorSection);
        
        // Lade Inventar
        this.inventory = new ArrayList<>();
        ConfigurationSection invSection = section.getConfigurationSection("inventory");
        if (invSection != null) {
            for (String key : invSection.getKeys(false)) {
                ConfigurationSection itemSection = invSection.getConfigurationSection(key);
                if (itemSection != null) {
                    inventory.add(new InventoryItem(itemSection));
                }
            }
        } else {
            // Alte Format-Unterstützung (Liste)
            List<Map<?, ?>> invList = section.getMapList("inventory");
            for (Map<?, ?> itemMap : invList) {
                inventory.add(new InventoryItem(itemMap));
            }
        }
    }
    
    public String getId() {
        return id;
    }
    
    public ArmorSet getArmor() {
        return armor;
    }
    
    public List<InventoryItem> getInventory() {
        return inventory;
    }
    
    public static class ArmorSet {
        private final ItemStack helmet;
        private final ItemStack chestplate;
        private final ItemStack leggings;
        private final ItemStack boots;
        
        public ArmorSet(ConfigurationSection section) {
            this.helmet = parseItem(section.getString("helmet"));
            this.chestplate = parseItem(section.getString("chestplate"));
            this.leggings = parseItem(section.getString("leggings"));
            this.boots = parseItem(section.getString("boots"));
        }
        
        private ItemStack parseItem(String materialName) {
            if (materialName == null || materialName.equalsIgnoreCase("null")) {
                return null;
            }
            try {
                Material material = Material.valueOf(materialName.toUpperCase());
                return new ItemStack(material);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        
        public ItemStack getHelmet() { return helmet; }
        public ItemStack getChestplate() { return chestplate; }
        public ItemStack getLeggings() { return leggings; }
        public ItemStack getBoots() { return boots; }
    }
    
    public static class InventoryItem {
        private final int slot;
        private final ItemStack itemStack;
        
        public InventoryItem(ConfigurationSection section) {
            this.slot = section.getInt("slot", 0);
            
            String itemStr = section.getString("item", "STONE");
            int amount = section.getInt("amount", 1);
            List<String> enchantments = section.getStringList("enchantments");
            
            this.itemStack = createItemStack(itemStr, amount, enchantments);
        }
        
        public InventoryItem(Map<?, ?> map) {
            // Slot mit sicherer Typ-Konvertierung
            Object slotObj = map.get("slot");
            if (slotObj instanceof Number) {
                this.slot = ((Number) slotObj).intValue();
            } else {
                this.slot = 0;
            }
            
            // Item String
            Object itemObj = map.get("item");
            String itemStr = (itemObj instanceof String) ? (String) itemObj : "STONE";
            
            // Amount mit sicherer Typ-Konvertierung
            Object amountObj = map.get("amount");
            int amount = 1;
            if (amountObj instanceof Number) {
                amount = ((Number) amountObj).intValue();
            }
            
            // Enchantments
            List<String> enchantments = new ArrayList<>();
            Object enchObj = map.get("enchantments");
            if (enchObj instanceof List<?>) {
                List<?> enchList = (List<?>) enchObj;
                for (Object obj : enchList) {
                    if (obj instanceof String) {
                        enchantments.add((String) obj);
                    }
                }
            }
            
            this.itemStack = createItemStack(itemStr, amount, enchantments);
        }
        
        private ItemStack createItemStack(String itemStr, int amount, List<String> enchantments) {
            try {
                Material material = Material.valueOf(itemStr.toUpperCase());
                ItemStack item = new ItemStack(material, amount);
                
                // Füge Verzauberungen hinzu
                if (enchantments != null && !enchantments.isEmpty()) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        for (String enchStr : enchantments) {
                            String[] parts = enchStr.split(":");
                            if (parts.length == 2) {
                                try {
                                    Enchantment enchant = Enchantment.getByName(parts[0].toUpperCase());
                                    int level = Integer.parseInt(parts[1]);
                                    if (enchant != null) {
                                        meta.addEnchant(enchant, level, true);
                                    }
                                } catch (Exception e) {
                                    // Ignoriere fehlerhafte Verzauberungen
                                }
                            }
                        }
                        item.setItemMeta(meta);
                    }
                }
                
                return item;
            } catch (IllegalArgumentException e) {
                return new ItemStack(Material.STONE);
            }
        }
        
        public int getSlot() { return slot; }
        public ItemStack getItemStack() { return itemStack; }
    }
}