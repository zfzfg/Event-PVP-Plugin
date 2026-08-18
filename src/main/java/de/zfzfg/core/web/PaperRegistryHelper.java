package de.zfzfg.core.web;

import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;

/**
 * Isolierter Helper für Paper-spezifische Registry-Tags.
 *
 * <p>Wird ausschließlich aufgerufen, wenn {@code Platform.isPaper()} true ist.
 * Durch die Auslagerung in eine separate Klasse wird verhindert, dass der Classloader
 * auf reinem Spigot beim Laden von {@link MaterialCatalog} versucht, nicht existierende
 * Paper-Klassen (wie {@code EnchantmentTagKeys}) zu linken.</p>
 */
final class PaperRegistryHelper {

    private PaperRegistryHelper() {}

    static Boolean isTreasure(Enchantment enchantment) {
        try {
            var tagValues = Registry.ENCHANTMENT.getTagValues(io.papermc.paper.registry.keys.tags.EnchantmentTagKeys.TREASURE);
            if (tagValues != null) {
                return tagValues.contains(enchantment);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    static Boolean isCursed(Enchantment enchantment) {
        try {
            var tagValues = Registry.ENCHANTMENT.getTagValues(io.papermc.paper.registry.keys.tags.EnchantmentTagKeys.CURSE);
            if (tagValues != null) {
                return tagValues.contains(enchantment);
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
