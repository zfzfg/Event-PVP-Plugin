package de.zfzfg.pvpwager.managers;

import de.zfzfg.core.items.ConfiguredItemFactory;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.EquipmentSet;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class EquipmentManager {
    private final EventPlugin plugin;
    /**
     * Sets in Dateireihenfolge. Frueher eine HashMap - damit konnte sich die Anordnung im
     * Auswahlmenue zwischen zwei Serverstarts vertauschen, obwohl niemand etwas geaendert hatte.
     */
    private final Map<String, EquipmentSet> equipmentSets = new LinkedHashMap<>();

    public EquipmentManager(EventPlugin plugin) {
        this.plugin = plugin;
        loadEquipmentSets();
    }

    public void loadEquipmentSets() {
        equipmentSets.clear();
        // Material- und Verzauberungssuche liegen jetzt in der Factory; deren Zwischenspeicher
        // muss beim Reload genauso geleert werden wie frueher die lokalen Maps.
        ConfiguredItemFactory.clearCaches();
        FileConfiguration equipmentConfig = plugin.getPvpConfigManager().getEquipmentConfig();

        // Genau eine Sektion. Die frueheren Rueckfallzweige auf 'equipment-sets' sind bewusst
        // entfallen: der Event-Loader bevorzugte die entgegengesetzte Sektion, wodurch beide
        // Systeme verschiedene Sets unter derselben ID sehen konnten. Alte Dateien werden beim
        // Start von EquipmentSchemaMigration zusammengefuehrt.
        ConfigurationSection section = equipmentConfig.getConfigurationSection(
                de.zfzfg.core.config.EquipmentSchemaMigration.TARGET);
        if (section == null) {
            plugin.getLogger().warning("No 'equipment' section found in equipment.yml!");  // i18n-ignore: technical equipment config log
            return;
        }
        loadFrom(section, "pvpwager-equip-enable", "PvP equipment");  // i18n-ignore: console log label

        if (equipmentSets.isEmpty()) {
            plugin.getLogger().warning("No PvP equipment loaded! PvPWager may not work correctly.");  // i18n-ignore: technical equipment config log
        }
    }

    /**
     * Laedt alle Sets eines Abschnitts.
     *
     * @param parent      Abschnitt, dessen Unterabschnitte je ein Set sind
     * @param enableKey   Name des Aktiv-Schalters in diesem Format
     * @param logLabel    Bezeichnung fuer die Protokollzeile
     */
    private void loadFrom(ConfigurationSection parent, String enableKey, String logLabel) {
        int fileIndex = 0;
        for (String setId : parent.getKeys(false)) {
            int indexOfThisSet = fileIndex++;
            try {
                ConfigurationSection setSection = parent.getConfigurationSection(setId);
                if (setSection == null) continue;

                if (!setSection.getBoolean(enableKey, true)) {
                    plugin.getLogger().info("Equipment '" + setId + "' is disabled, skipping...");  // i18n-ignore: technical equipment config log
                    continue;
                }

                String displayName = setSection.getString("display-name", setId);
                ConfigurationSection armorSection = setSection.getConfigurationSection("armor");

                // Ruestung steht als blosser Materialname; die Verzauberungen liegen daneben
                // in '<slot>-enchantments'. Frueher wurden sie hier gar nicht gelesen.
                ItemStack helmet = armorPiece(armorSection, "helmet", setId);
                ItemStack chestplate = armorPiece(armorSection, "chestplate", setId);
                ItemStack leggings = armorPiece(armorSection, "leggings", setId);
                ItemStack boots = armorPiece(armorSection, "boots", setId);

                // Nebenhand: das Web-Panel schreibt sie seit jeher, bis 1.0.9 las sie niemand.
                // Sie steht eine Ebene hoeher als die Ruestung.
                ItemStack offhand = ConfiguredItemFactory.buildPrefixed(
                        setSection, "offhand", plugin.getLogger(), setId);

                Map<Integer, ItemStack> inventory = new HashMap<>();
                for (Map<?, ?> itemMap : setSection.getMapList("inventory")) {
                    int slot = ConfiguredItemFactory.readSlot(itemMap);
                    if (slot < 0) {
                        plugin.getLogger().warning("Inventory item without slot in equipment '" + setId + "', skipping");  // i18n-ignore: technical equipment parse log
                        continue;
                    }
                    ItemStack item = ConfiguredItemFactory.build(itemMap, plugin.getLogger(), setId);
                    if (item != null) {
                        inventory.put(slot, item);
                    }
                }

                EquipmentSet equipmentSet = new EquipmentSet(setId, displayName, helmet, chestplate,
                        leggings, boots, offhand, inventory);
                equipmentSet.setIcon(readIcon(setSection, setId));
                equipmentSet.setDescription(readDescription(setSection));
                equipmentSet.setOrder(readOrder(setSection, indexOfThisSet));
                applyAllowedWorldsFromSection(equipmentSet, setSection);
                equipmentSets.put(setId, equipmentSet);
                plugin.getLogger().info("Loaded " + logLabel + ": " + setId + " (" + displayName + ")");  // i18n-ignore: technical equipment loader log

            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error loading equipment '" + setId + "'", e);  // i18n-ignore: technical equipment loader log
            }
        }
    }

    /** Ein Ruestungsteil samt seiner '&lt;slot&gt;-*'-Zusatzfelder. */
    private ItemStack armorPiece(ConfigurationSection armorSection, String slot, String setId) {
        return ConfiguredItemFactory.buildPrefixed(armorSection, slot, plugin.getLogger(), setId);
    }

    /**
     * Icon im Auswahlmenue ({@code icon}).
     *
     * <p>Bis 1.0.9 stand das Material im Block {@code gui-item} - Dateien von damals werden
     * weiter gelesen, damit die Menues nach dem Update unveraendert aussehen. Ein
     * unbekanntes Material wird gemeldet und uebergangen, statt das ganze Set scheitern zu
     * lassen.</p>
     *
     * @return null, wenn das Menue das Icon aus dem Brustpanzer ableiten soll
     */
    private org.bukkit.Material readIcon(ConfigurationSection setSection, String setId) {
        String materialName = setSection.getString("icon");
        if (materialName == null || materialName.trim().isEmpty()) {
            ConfigurationSection legacy = setSection.getConfigurationSection("gui-item");
            materialName = legacy != null ? legacy.getString("material") : null;
        }
        if (materialName == null || materialName.trim().isEmpty()) {
            return null;
        }

        org.bukkit.Material material = org.bukkit.Material.matchMaterial(
                materialName.trim().toUpperCase(java.util.Locale.ROOT));
        if (material == null) {
            plugin.getLogger().warning("Equipment '" + setId + "': unknown icon material '"  // i18n-ignore: technical equipment parse log
                    + materialName + "', falling back to the automatic icon");
        }
        return material;
    }

    /**
     * Lore im Auswahlmenue ({@code description}).
     *
     * <p>Das Feld gab es immer, gelesen wurde bis 1.0.9 aber nur {@code gui-item.lore} - im
     * Web-Panel liessen sich damit zwei Texte fuer dieselbe Stelle pflegen. Uebrig ist die
     * Beschreibung; sie kommt als ein Text mit Zeilenumbruechen, eine YAML-Liste wird
     * ebenfalls angenommen.</p>
     *
     * @return leer, wenn das Menue seine automatische Uebersicht bauen soll
     */
    private java.util.List<String> readDescription(ConfigurationSection setSection) {
        Object raw = setSection.get("description");
        if (raw instanceof java.util.List) {
            java.util.List<String> lines = new java.util.ArrayList<>();
            for (Object line : (java.util.List<?>) raw) {
                if (line != null) lines.add(String.valueOf(line));
            }
            return lines;
        }

        String text = raw == null ? null : String.valueOf(raw);
        if (text == null || text.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return java.util.Arrays.asList(text.split("\\r?\\n"));
    }

    /**
     * Position im Auswahlmenue ({@code order}).
     *
     * <p>Ohne Angabe zaehlt die Reihenfolge in der Datei. Aeltere Dateien tragen stattdessen
     * einen festen Platz in {@code gui-item.slot}; der wird als Startwert uebernommen, damit
     * die Sets nach dem Update in gewohnter Anordnung stehen. Die Slots muessen dafuer nicht
     * lueckenlos sein - sortiert wird nur nach ihrer Groesse.</p>
     */
    private int readOrder(ConfigurationSection setSection, int fileIndex) {
        if (setSection.isSet("order")) {
            return setSection.getInt("order");
        }
        ConfigurationSection legacy = setSection.getConfigurationSection("gui-item");
        if (legacy != null && legacy.isSet("slot")) {
            return legacy.getInt("slot");
        }
        return fileIndex;
    }

    public EquipmentSet getEquipmentSet(String setId) {
        return equipmentSets.get(setId);
    }

    public Map<String, EquipmentSet> getEquipmentSets() {
        return java.util.Collections.unmodifiableMap(equipmentSets);
    }

    /**
     * Alle Sets in Anzeigereihenfolge.
     *
     * <p>Was das Web-Panel per Hoch/Runter festlegt, muss im Menue genauso herauskommen -
     * deshalb geht jeder Weg zur Anzeige ueber diese eine Sortierung. Bei gleichem
     * {@code order} entscheidet die ID, damit die Reihenfolge auch dann feststeht.</p>
     */
    public java.util.List<EquipmentSet> getSortedEquipmentSets() {
        return sorted(equipmentSets.values());
    }

    private java.util.List<EquipmentSet> sorted(java.util.Collection<EquipmentSet> sets) {
        java.util.List<EquipmentSet> list = new java.util.ArrayList<>(sets);
        list.sort(java.util.Comparator.comparingInt(EquipmentSet::getOrder)
                .thenComparing(EquipmentSet::getId));
        return list;
    }

    public void reloadEquipmentSets() {
        loadEquipmentSets();
    }

    /** Die in dieser Welt erlaubten Sets, bereits in Anzeigereihenfolge. */
    public java.util.List<EquipmentSet> getAllowedEquipmentForWorld(String worldName) {
        java.util.List<EquipmentSet> list = new java.util.ArrayList<>();
        for (EquipmentSet set : equipmentSets.values()) {
            if (set.isAllowedForWorld(worldName)) list.add(set);
        }
        return sorted(list);
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
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to apply allowed worlds from section: " + e.getMessage());  // i18n-ignore: technical equipment parse log
        }
    }
}
