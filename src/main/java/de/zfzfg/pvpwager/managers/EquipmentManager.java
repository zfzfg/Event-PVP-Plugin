package de.zfzfg.pvpwager.managers;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.EquipmentSet;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EquipmentManager {
    private final EventPlugin plugin;
    private final Map<String, EquipmentSet> equipmentSets = new HashMap<>();
    
    public EquipmentManager(EventPlugin plugin) {
        this.plugin = plugin;
        loadEquipmentSets();
    }
    
    public void loadEquipmentSets() {
        equipmentSets.clear();
        FileConfiguration equipmentConfig = plugin.getPvpConfigManager().getEquipmentConfig();

        // Prefer unified 'equipment' section with pvpwager-equip-enable flag
        ConfigurationSection unifiedSection = equipmentConfig.getConfigurationSection("equipment");
        if (unifiedSection != null) {
            for (String setId : unifiedSection.getKeys(false)) {
                try {
                    ConfigurationSection setSection = unifiedSection.getConfigurationSection(setId);
                    if (setSection == null) continue;

                    boolean pvpEnabled = setSection.getBoolean("pvpwager-equip-enable", true);
                    if (!pvpEnabled) {
                        plugin.getLogger().info("Equipment '" + setId + "' not enabled for PvPWager, skipping...");
                        continue;
                    }

                    String displayName = setSection.getString("display-name", setId);

                    // Load armor
                    ConfigurationSection armorSection = setSection.getConfigurationSection("armor");
                    ItemStack helmet = parseItem(armorSection.getString("helmet"));
                    ItemStack chestplate = parseItem(armorSection.getString("chestplate"));
                    ItemStack leggings = parseItem(armorSection.getString("leggings"));
                    ItemStack boots = parseItem(armorSection.getString("boots"));

                    // Load inventory items
                    Map<Integer, ItemStack> inventory = new HashMap<>();
                    List<Map<?, ?>> inventoryList = setSection.getMapList("inventory");
                    for (Map<?, ?> itemMap : inventoryList) {
                        try {
                            int slot = ((Number) itemMap.get("slot")).intValue();
                            String itemName = (String) itemMap.get("item");
                            int amount = itemMap.containsKey("amount") ? ((Number) itemMap.get("amount")).intValue() : 1;

                            ItemStack item = parseItem(itemName);
                            if (item != null) {
                                item.setAmount(amount);
                                if (itemMap.containsKey("enchantments")) {
                                    @SuppressWarnings("unchecked")
                                    List<String> enchantments = (List<String>) itemMap.get("enchantments");
                                    applyEnchantments(item, enchantments);
                                }
                                inventory.put(slot, item);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Error parsing inventory item in equipment '" + setId + "': " + e.getMessage());
                        }
                    }

                    EquipmentSet equipmentSet = new EquipmentSet(setId, displayName, helmet, chestplate, leggings, boots, inventory);
                    applyAllowedWorldsFromSection(equipmentSet, setSection);
                    equipmentSets.put(setId, equipmentSet);
                    plugin.getLogger().info("Loaded PvP equipment: " + setId + " (" + displayName + ")");

                } catch (Exception e) {
                    plugin.getLogger().severe("Error loading equipment '" + setId + "': " + e.getMessage());
                    if (plugin.getConfig() != null && plugin.getConfig().getBoolean("debug", false)) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // Fallback to legacy 'equipment-sets'
        if (equipmentSets.isEmpty()) {
            ConfigurationSection setsSection = equipmentConfig.getConfigurationSection("equipment-sets");
            if (setsSection == null) {
                plugin.getLogger().warning("No equipment sets found in unified 'equipment' or legacy 'equipment-sets'!");
                return;
            }
            for (String setId : setsSection.getKeys(false)) {
                try {
                    ConfigurationSection setSection = setsSection.getConfigurationSection(setId);
                    if (setSection == null) continue;
                
                boolean enabled = setSection.getBoolean("enabled", true);
                if (!enabled) {
                    plugin.getLogger().info("Equipment set '" + setId + "' is disabled, skipping...");
                    continue;
                }
                
                String displayName = setSection.getString("display-name", setId);
                String description = setSection.getString("description", "");
                
                // Load armor
                ConfigurationSection armorSection = setSection.getConfigurationSection("armor");
                ItemStack helmet = parseItem(armorSection.getString("helmet"));
                ItemStack chestplate = parseItem(armorSection.getString("chestplate"));
                ItemStack leggings = parseItem(armorSection.getString("leggings"));
                ItemStack boots = parseItem(armorSection.getString("boots"));
                
                // Load inventory items
                Map<Integer, ItemStack> inventory = new HashMap<>();
                List<Map<?, ?>> inventoryList = setSection.getMapList("inventory");
                
                for (Map<?, ?> itemMap : inventoryList) {
                    try {
                        int slot = ((Number) itemMap.get("slot")).intValue();
                        String itemName = (String) itemMap.get("item");
                        int amount = itemMap.containsKey("amount") ? ((Number) itemMap.get("amount")).intValue() : 1;
                        
                        ItemStack item = parseItem(itemName);
                        if (item != null) {
                            item.setAmount(amount);
                            
                            // Apply enchantments if any
                            if (itemMap.containsKey("enchantments")) {
                                @SuppressWarnings("unchecked")
                                List<String> enchantments = (List<String>) itemMap.get("enchantments");
                                applyEnchantments(item, enchantments);
                            }
                            
                            inventory.put(slot, item);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error parsing inventory item in set '" + setId + "': " + e.getMessage());
                    }
                }
                
                EquipmentSet equipmentSet = new EquipmentSet(setId, displayName, helmet, chestplate, leggings, boots, inventory);
                applyAllowedWorldsFromSection(equipmentSet, setSection);
                equipmentSets.put(setId, equipmentSet);
                
                plugin.getLogger().info("Loaded legacy equipment set: " + setId + " (" + displayName + ")");
                
                } catch (Exception e) {
                    plugin.getLogger().severe("Error loading equipment set '" + setId + "': " + e.getMessage());
                    if (plugin.getConfig() != null && plugin.getConfig().getBoolean("debug", false)) {
                        e.printStackTrace();
                    }
                }
            }
        }

        if (equipmentSets.isEmpty()) {
            plugin.getLogger().warning("No PvP equipment loaded! PvPWager may not work correctly.");
        }
    }
    
    private ItemStack parseItem(String materialName) {
        if (materialName == null || materialName.trim().isEmpty()) {
            return null;
        }
        
        try {
            Material material = Material.valueOf(materialName.toUpperCase());
            return new ItemStack(material);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material: " + materialName);
            return null;
        }
    }
    
    private void applyEnchantments(ItemStack item, List<String> enchantments) {
        for (String enchantString : enchantments) {
            try {
                String[] parts = enchantString.split(":");
                if (parts.length != 2) continue;
                
                String enchantName = parts[0].toUpperCase();
                int level = Integer.parseInt(parts[1]);
                
                Enchantment enchantment = Enchantment.getByName(enchantName);
                if (enchantment != null) {
                    item.addUnsafeEnchantment(enchantment, level);
                } else {
                    plugin.getLogger().warning("Unknown enchantment: " + enchantName);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error parsing enchantment: " + enchantString);
            }
        }
    }
    
    public EquipmentSet getEquipmentSet(String setId) {
        return equipmentSets.get(setId);
    }
    
    public Map<String, EquipmentSet> getEquipmentSets() {
        return new HashMap<>(equipmentSets);
    }
    
    public void reloadEquipmentSets() {
        loadEquipmentSets();
    }

    public java.util.List<EquipmentSet> getAllowedEquipmentForWorld(String worldName) {
        java.util.List<EquipmentSet> list = new java.util.ArrayList<>();
        for (EquipmentSet set : equipmentSets.values()) {
            if (set.isAllowedForWorld(worldName)) list.add(set);
        }
        return list;
    }

    public boolean isEquipmentAllowedInWorld(String equipmentId, String worldName) {
        EquipmentSet set = equipmentSets.get(equipmentId);
        if (set == null) return false;
        return set.isAllowedForWorld(worldName);
    }

    private void applyAllowedWorldsFromSection(EquipmentSet equipmentSet, ConfigurationSection setSection) {
        try {
            java.util.Set<String> worlds = new java.util.HashSet<>();
            Object raw = setSection.get("allowed-pvpwager-worlds");
            if (raw instanceof String) {
                String val = ((String) raw).trim();
                if (val.equalsIgnoreCase("all")) {
                    equipmentSet.setAllowAll(true); equipmentSet.setAllowNone(false);
                } else if (val.equalsIgnoreCase("none")) {
                    equipmentSet.setAllowAll(false); equipmentSet.setAllowNone(true);
                } else if (!val.isEmpty()) {
                    equipmentSet.setAllowAll(false); equipmentSet.setAllowNone(false);
                    for (String part : val.split(",")) { worlds.add(part.trim()); }
                    equipmentSet.setAllowedWorlds(worlds);
                }
            } else if (raw instanceof java.util.List) {
                @SuppressWarnings("unchecked") java.util.List<Object> list = (java.util.List<Object>) raw;
                equipmentSet.setAllowAll(false); equipmentSet.setAllowNone(false);
                for (Object o : list) { if (o != null) worlds.add(String.valueOf(o).trim()); }
                equipmentSet.setAllowedWorlds(worlds);
            } else {
                equipmentSet.setAllowAll(true); equipmentSet.setAllowNone(false);
            }
        } catch (Exception ignored) {}
    }
}