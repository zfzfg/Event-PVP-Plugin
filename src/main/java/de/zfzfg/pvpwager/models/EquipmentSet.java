package de.zfzfg.pvpwager.models;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class EquipmentSet {

    private String id;
    private String displayName;
    private ItemStack helmet, chestplate, leggings, boots;
    /** Nebenhand. Das Web-Panel konnte sie immer setzen, gelesen wurde sie bis 1.0.9 nie. */
    private ItemStack offhand;
    private Map<Integer, ItemStack> inventory;
    private java.util.Set<String> allowedWorlds;
    private boolean allowAll;
    private boolean allowNone;

    /**
     * Aussehen und Position des Sets im Ingame-Auswahlmenue.
     *
     * <p>Bis 1.0.9 stand das alles im Block {@code gui-item}, der neben Icon und Platz auch
     * Titel und Lore noch einmal fuehrte - dieselben Angaben, die das Set unter
     * {@code display-name} und {@code description} ohnehin schon trug. Geblieben ist das
     * Icon; Titel und Lore kommen jetzt aus den Basisfeldern, und statt eines frei
     * belegbaren Slots gibt es nur noch eine Reihenfolge, denn das Menue reiht die Sets
     * fortlaufend auf.</p>
     */
    private Material icon;
    /** Lore im Auswahlmenue; leer = automatische Ruestungs- und Inventaruebersicht. */
    private List<String> description = Collections.emptyList();
    /** Position im Auswahlmenue; kleinere Werte zuerst. */
    private int order;

    public EquipmentSet(String id, String displayName, ItemStack helmet, ItemStack chestplate,
                       ItemStack leggings, ItemStack boots, Map<Integer, ItemStack> inventory) {
        this(id, displayName, helmet, chestplate, leggings, boots, null, inventory);
    }

    public EquipmentSet(String id, String displayName, ItemStack helmet, ItemStack chestplate,
                       ItemStack leggings, ItemStack boots, ItemStack offhand,
                       Map<Integer, ItemStack> inventory) {
        this.id = id;
        this.displayName = displayName;
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;
        this.offhand = offhand;
        this.inventory = inventory;
        this.allowedWorlds = new java.util.HashSet<>();
        this.allowAll = true;
        this.allowNone = false;
    }

    // Getters and setters
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public ItemStack getHelmet() { return helmet; }
    public ItemStack getChestplate() { return chestplate; }
    public ItemStack getLeggings() { return leggings; }
    public ItemStack getBoots() { return boots; }
    public ItemStack getOffhand() { return offhand; }
    /** @return null, wenn das Menue das Icon aus dem Brustpanzer ableiten soll */
    public Material getIcon() { return icon; }
    public void setIcon(Material icon) { this.icon = icon; }

    /** @return leer, wenn die automatische Ruestungsuebersicht verwendet werden soll */
    public List<String> getDescription() { return description; }
    public void setDescription(List<String> description) {
        this.description = description == null ? Collections.emptyList()
                : Collections.unmodifiableList(description);
    }

    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
    public Map<Integer, ItemStack> getInventory() { return inventory; }
    public java.util.Set<String> getAllowedWorlds() { return allowedWorlds; }
    public void setAllowedWorlds(java.util.Set<String> worlds) { this.allowedWorlds = worlds != null ? worlds : new java.util.HashSet<>(); }
    public boolean isAllowAll() { return allowAll; }
    public void setAllowAll(boolean allowAll) { this.allowAll = allowAll; }
    public boolean isAllowNone() { return allowNone; }
    public void setAllowNone(boolean allowNone) { this.allowNone = allowNone; }
    public boolean isAllowedForWorld(String worldName) {
        if (allowNone) return false;
        if (allowAll) return true;
        if (worldName == null) return false;
        for (String w : allowedWorlds) {
            if (worldName.equalsIgnoreCase(w)) return true;
        }
        return false;
    }
}