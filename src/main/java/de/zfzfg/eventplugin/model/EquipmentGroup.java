package de.zfzfg.eventplugin.model;

import de.zfzfg.core.items.ConfiguredItemFactory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ausruestungsgruppe der Event-Seite.
 *
 * <p>Der Bau der ItemStacks liegt seit 1.0.9 vollstaendig in {@link ConfiguredItemFactory} -
 * vorher gab es hier zwei eigene Implementierungen, die nur Material, Anzahl und
 * Verzauberungen kannten und bei einem unbekannten Material stillschweigend {@code STONE}
 * einsetzten. Lore, Unbreakable, CustomModelData, Item-Flags und Trankeffekte funktionieren
 * dadurch jetzt auf der Event-Seite genauso wie auf der PvP-Seite.</p>
 */
public class EquipmentGroup {

    private final String id;
    private final ArmorSet armor;
    private final List<InventoryItem> inventory;

    public EquipmentGroup(String id, ConfigurationSection section) {
        this.id = id;

        ConfigurationSection armorSection = section.getConfigurationSection("armor");
        this.armor = new ArmorSet(armorSection, id);

        this.inventory = new ArrayList<>();
        ConfigurationSection invSection = section.getConfigurationSection("inventory");
        if (invSection != null) {
            // Sektions-Schreibweise: benannte Unterabschnitte je Item.
            for (String key : invSection.getKeys(false)) {
                ConfigurationSection itemSection = invSection.getConfigurationSection(key);
                if (itemSection != null) {
                    InventoryItem item = InventoryItem.from(itemSection, id);
                    if (item != null) {
                        inventory.add(item);
                    }
                }
            }
        } else {
            // Listen-Schreibweise, wie sie das Web-Panel schreibt.
            for (Map<?, ?> itemMap : section.getMapList("inventory")) {
                InventoryItem item = InventoryItem.from(itemMap, id);
                if (item != null) {
                    inventory.add(item);
                }
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
        /** Nebenhand; steht in der Konfiguration eine Ebene ueber der Ruestung. */
        private final ItemStack offhand;

        public ArmorSet(ConfigurationSection section) {
            this(section, null);
        }

        ArmorSet(ConfigurationSection section, String context) {
            if (section == null) {
                this.helmet = null;
                this.chestplate = null;
                this.leggings = null;
                this.boots = null;
                this.offhand = null;
                return;
            }
            this.helmet = ConfiguredItemFactory.buildPrefixed(section, "helmet", null, context);
            this.chestplate = ConfiguredItemFactory.buildPrefixed(section, "chestplate", null, context);
            this.leggings = ConfiguredItemFactory.buildPrefixed(section, "leggings", null, context);
            this.boots = ConfiguredItemFactory.buildPrefixed(section, "boots", null, context);

            // Die Nebenhand steht eine Ebene ueber der Ruestung, direkt im Set.
            this.offhand = ConfiguredItemFactory.buildPrefixed(section.getParent(), "offhand", null, context);
        }

        public ItemStack getHelmet() { return helmet; }
        public ItemStack getChestplate() { return chestplate; }
        public ItemStack getLeggings() { return leggings; }
        public ItemStack getBoots() { return boots; }
        public ItemStack getOffhand() { return offhand; }
    }

    public static class InventoryItem {
        private final int slot;
        private final ItemStack itemStack;

        private InventoryItem(int slot, ItemStack itemStack) {
            this.slot = slot;
            this.itemStack = itemStack;
        }

        /** @return das Item oder {@code null}, wenn das Material unbekannt ist */
        static InventoryItem from(ConfigurationSection section, String context) {
            ItemStack stack = ConfiguredItemFactory.build(section, null, context);
            return stack == null ? null : new InventoryItem(section.getInt("slot", 0), stack);
        }

        /** @return das Item oder {@code null}, wenn das Material unbekannt ist */
        static InventoryItem from(Map<?, ?> map, String context) {
            ItemStack stack = ConfiguredItemFactory.build(map, null, context);
            if (stack == null) {
                return null;
            }
            int slot = ConfiguredItemFactory.readSlot(map);
            return new InventoryItem(slot < 0 ? 0 : slot, stack);
        }

        public int getSlot() { return slot; }
        public ItemStack getItemStack() { return itemStack; }
    }
}
