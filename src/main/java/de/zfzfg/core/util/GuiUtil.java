package de.zfzfg.core.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Utility für plattformunabhängige GUI- und Inventar-Erstellung.
 *
 * <p>Auf Paper/Purpur wird die native {@code Bukkit.createInventory(holder, size, Component)}
 * Methode aufgerufen, um volle RGB/Adventure-Unterstützung im GUI-Titel zu gewährleisten.
 * Auf Vanilla Spigot wird automatisch die String-basierte Methode mit Legacy-Farbcodes genutzt.</p>
 */
public final class GuiUtil {

    private GuiUtil() {}

    /**
     * Erstellt ein Inventar mit definierter Slot-Anzahl und Component-Titel.
     */
    public static Inventory createInventory(InventoryHolder holder, int size, Component title) {
        if (title == null) title = Component.empty();
        if (Platform.isPaper()) {
            return Bukkit.createInventory(holder, size, title);
        } else {
            return Bukkit.createInventory(holder, size, Text.toLegacy(title));
        }
    }

    /**
     * Erstellt ein Inventar mit definierter Slot-Anzahl und Legacy-String-Titel.
     */
    public static Inventory createInventory(InventoryHolder holder, int size, String legacyTitle) {
        if (Platform.isPaper()) {
            return Bukkit.createInventory(holder, size, Text.of(legacyTitle));
        } else {
            return Bukkit.createInventory(holder, size, TextUtil.color(legacyTitle));
        }
    }

    /**
     * Erstellt ein Inventar mit bestimmtem InventoryType und Component-Titel.
     */
    public static Inventory createInventory(InventoryHolder holder, InventoryType type, Component title) {
        if (title == null) title = Component.empty();
        if (Platform.isPaper()) {
            return Bukkit.createInventory(holder, type, title);
        } else {
            return Bukkit.createInventory(holder, type, Text.toLegacy(title));
        }
    }
}
