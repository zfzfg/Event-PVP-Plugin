package de.zfzfg.core.util;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility für plattformunabhängigen Zugriff auf Item-Metadaten (DisplayName & Lore).
 *
 * <p>Auf Paper/Purpur wird die native Adventure Component-Pipeline für ItemMeta genutzt
 * (ohne Kursiv-Standard-Artefakte und mit voller RGB-Präzision).
 * Auf Vanilla Spigot wird automatisch auf die String-basierten Methoden zurückgegriffen.</p>
 */
public final class ItemUtil {

    private ItemUtil() {}

    /**
     * Setzt den Anzeigenamen eines Items als Component.
     */
    public static void setDisplayName(ItemMeta meta, Component name) {
        if (meta == null) return;
        if (Platform.isPaper()) {
            meta.displayName(name != null ? name : Component.empty());
        } else {
            meta.setDisplayName(name != null ? Text.toLegacy(name) : "");
        }
    }

    /**
     * Setzt den Anzeigenamen eines Items aus einem Legacy-String.
     */
    public static void setDisplayName(ItemMeta meta, String legacyName) {
        if (meta == null) return;
        if (Platform.isPaper()) {
            meta.displayName(legacyName != null ? Text.ofItem(legacyName) : Component.empty());
        } else {
            meta.setDisplayName(legacyName != null ? TextUtil.color(legacyName) : "");
        }
    }

    /**
     * Setzt die Lore eines Items aus einer Liste von Components.
     */
    public static void setLore(ItemMeta meta, List<Component> lore) {
        if (meta == null) return;
        if (lore == null || lore.isEmpty()) {
            if (Platform.isPaper()) {
                meta.lore(Collections.emptyList());
            } else {
                meta.setLore(Collections.emptyList());
            }
            return;
        }

        if (Platform.isPaper()) {
            meta.lore(lore);
        } else {
            meta.setLore(lore.stream().map(Text::toLegacy).collect(Collectors.toList()));
        }
    }

    /**
     * Setzt die Lore eines Items aus einer Liste von Legacy-Strings.
     */
    public static void setLoreFromStrings(ItemMeta meta, List<String> legacyLore) {
        if (meta == null) return;
        if (legacyLore == null || legacyLore.isEmpty()) {
            if (Platform.isPaper()) {
                meta.lore(Collections.emptyList());
            } else {
                meta.setLore(Collections.emptyList());
            }
            return;
        }

        if (Platform.isPaper()) {
            meta.lore(legacyLore.stream().map(Text::ofItem).collect(Collectors.toList()));
        } else {
            meta.setLore(legacyLore.stream().map(TextUtil::color).collect(Collectors.toList()));
        }
    }

    /**
     * Gibt die Lore als modifizierbare Liste von Components zurück.
     */
    public static List<Component> getLore(ItemMeta meta) {
        if (meta == null || !meta.hasLore()) return new ArrayList<>();
        if (Platform.isPaper()) {
            List<Component> l = meta.lore();
            return l != null ? new ArrayList<>(l) : new ArrayList<>();
        } else {
            List<String> legacy = meta.getLore();
            if (legacy == null) return new ArrayList<>();
            return legacy.stream().map(Text::of).collect(Collectors.toCollection(ArrayList::new));
        }
    }

    /**
     * Gibt den Anzeigenamen als Legacy-String (§-Codes) zurück.
     */
    public static String getDisplayName(ItemMeta meta) {
        if (meta == null || !meta.hasDisplayName()) return "";
        if (Platform.isPaper()) {
            Component c = meta.displayName();
            return c != null ? Text.toLegacy(c) : "";
        } else {
            String name = meta.getDisplayName();
            return name != null ? name : "";
        }
    }
}
