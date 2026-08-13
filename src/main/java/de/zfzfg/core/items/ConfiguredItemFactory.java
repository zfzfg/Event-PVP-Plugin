package de.zfzfg.core.items;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Baut aus einem Konfigurationseintrag einen {@link ItemStack}.
 *
 * <p>Diese Klasse ist die einzige Stelle, die das Item-Schema von {@code equipment.yml}
 * kennt. Vorher gab es drei voneinander unabhaengige Bauplaetze - in {@code EquipmentManager},
 * in {@code EquipmentGroup.ArmorSet} und in {@code EquipmentGroup.InventoryItem} -, die
 * jeweils unterschiedlich viel vom Schema verstanden. Praktische Folge: das Web-Panel schrieb
 * seit jeher ein {@code offhand}-Feld, das kein einziger Loader las, und Trankeffekte oder
 * Lore waren gar nicht erst konfigurierbar.</p>
 *
 * <h2>Unterstuetztes Schema</h2>
 * <pre>
 * slot: 0                       # nur in Inventarlisten, hier nicht ausgewertet
 * item: DIAMOND_SWORD           # Bukkit-Material; Pflichtfeld
 * amount: 1
 * name: "&amp;bMein Schwert"        # Anzeigename, &amp;-Farbcodes erlaubt
 * lore:                         # Beschreibungszeilen, &amp;-Farbcodes erlaubt
 *   - "&amp;7Zeile 1"
 * enchantments: ["SHARPNESS:5"] # NAME:STUFE
 * unbreakable: true
 * damage: 100                   # Schadenswert (0 = unbeschaedigt)
 * custom-model-data: 12345
 * item-flags: [HIDE_ATTRIBUTES]
 * potion:                       # nur POTION/SPLASH_POTION/LINGERING_POTION/TIPPED_ARROW
 *   type: STRENGTH
 *   extended: false
 *   upgraded: true
 *   custom-effects:
 *     - type: SPEED
 *       duration: 600           # in Ticks
 *       amplifier: 1
 *       ambient: false
 *       particles: true
 * </pre>
 *
 * <p>Alle Felder ausser {@code item} sind optional, unbekannte Felder werden ignoriert.
 * Damit laedt jede aeltere {@code equipment.yml} unveraendert weiter.</p>
 */
public final class ConfiguredItemFactory {

    /** Materialsuche ist der haeufigste Aufruf beim Laden - Ergebnis merken. */
    private static final Map<String, Material> MATERIAL_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Enchantment> ENCHANTMENT_CACHE = new ConcurrentHashMap<>();

    /** Platzhalter im Cache fuer "gibt es nicht" - ConcurrentHashMap vertraegt kein null. */
    private static final Material MATERIAL_MISSING = Material.AIR;

    private ConfiguredItemFactory() {
    }

    /** Leert die Zwischenspeicher; beim Plugin-Reload aufzurufen. */
    public static void clearCaches() {
        MATERIAL_CACHE.clear();
        ENCHANTMENT_CACHE.clear();
    }

    // ============ Einstiegspunkte ============

    /**
     * Baut ein Item aus einem Listeneintrag, wie ihn {@code getMapList("inventory")} liefert.
     *
     * @return das fertige Item oder {@code null}, wenn kein gueltiges Material angegeben war
     */
    public static ItemStack build(Map<?, ?> map, Logger logger, String context) {
        if (map == null) {
            return null;
        }
        return build(new MapSource(map), logger, context);
    }

    /** Baut ein Item aus einem Konfigurationsabschnitt (Event-Seite, Sektions-Schreibweise). */
    public static ItemStack build(ConfigurationSection section, Logger logger, String context) {
        if (section == null) {
            return null;
        }
        return build(new SectionSource(section), logger, context);
    }

    /**
     * Baut ein Ruestungs- oder Nebenhandteil, das in der Konfiguration nicht als eigener
     * Abschnitt, sondern als Feldgruppe mit gemeinsamem Praefix steht:
     *
     * <pre>
     * armor:
     *   helmet: DIAMOND_HELMET
     *   helmet-enchantments: ["PROTECTION:4"]
     *   helmet-name: "&amp;bKrone"
     *   helmet-lore: ["&amp;7Zeile"]
     *   helmet-unbreakable: true
     * </pre>
     *
     * @param section Abschnitt, der die Felder enthaelt ({@code armor} bzw. das Set selbst)
     * @param prefix  Feldname des Materials, zugleich Praefix der Zusatzfelder
     * @return das Item oder {@code null}, wenn kein oder ein unbekanntes Material dasteht
     */
    public static ItemStack buildPrefixed(ConfigurationSection section, String prefix,
                                          Logger logger, String context) {
        if (section == null) {
            return null;
        }
        String materialName = section.getString(prefix);
        // "null" als Zeichenkette kommt in aelteren Konfigurationen vor und meint "leer".
        if (materialName == null || materialName.trim().isEmpty() || materialName.equalsIgnoreCase("null")) {
            return null;
        }
        Material material = resolveMaterial(materialName, logger, context);
        if (material == null) {
            return null;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String displayName = section.getString(prefix + "-name");
        if (displayName != null && !displayName.isEmpty()) {
            meta.setDisplayName(color(displayName));
        }

        List<String> lore = section.getStringList(prefix + "-lore");
        if (!lore.isEmpty()) {
            List<String> colored = new ArrayList<>(lore.size());
            for (String line : lore) {
                colored.add(color(line));
            }
            meta.setLore(colored);
        }

        applyEnchantments(item, meta, section.getStringList(prefix + "-enchantments"), logger, context);

        if (section.getBoolean(prefix + "-unbreakable", false)) {
            meta.setUnbreakable(true);
        }

        item.setItemMeta(meta);
        return item;
    }

    /** Slot-Nummer eines Inventareintrags, {@code -1} wenn keine angegeben ist. */
    public static int readSlot(Map<?, ?> map) {
        Object raw = map == null ? null : map.get("slot");
        return raw instanceof Number ? ((Number) raw).intValue() : -1;
    }

    // ============ Kern ============

    private static ItemStack build(Source source, Logger logger, String context) {
        String materialName = source.string("item");
        if (materialName == null || materialName.trim().isEmpty()) {
            // Ohne 'item' laesst sich nichts bauen. Eine Zeile ins Log, statt den Eintrag
            // stumm verschwinden zu lassen - sonst sucht man im Panel vergeblich nach dem Item.
            logSkipped(logger, context, "Inventory entry without an 'item' field, skipping");  // i18n-ignore: technical item-parsing log
            return null;
        }
        Material material = resolveMaterial(materialName, logger, context);
        if (material == null) {
            return null;
        }

        int amount = source.integer("amount", 1);
        // Ein Stapel unter 1 waere ein leerer Slot, oberhalb der Materialgrenze wuerde der
        // Server ihn beim Setzen stillschweigend kappen - hier sichtbar begrenzen.
        amount = Math.max(1, Math.min(amount, material.getMaxStackSize()));

        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            // Materials ohne Meta (praktisch nur AIR) koennen nichts weiter tragen.
            return item;
        }

        String displayName = source.string("name");
        if (displayName != null && !displayName.isEmpty()) {
            meta.setDisplayName(color(displayName));
        }

        List<String> lore = source.strings("lore");
        if (!lore.isEmpty()) {
            List<String> colored = new ArrayList<>(lore.size());
            for (String line : lore) {
                colored.add(color(line));
            }
            meta.setLore(colored);
        }

        applyEnchantments(item, meta, source.strings("enchantments"), logger, context);

        if (source.bool("unbreakable", false)) {
            meta.setUnbreakable(true);
        }

        int damage = source.integer("damage", 0);
        if (damage > 0 && meta instanceof Damageable && material.getMaxDurability() > 0) {
            // Ueber die Maximalhaltbarkeit hinaus waere das Item sofort zerstoert.
            ((Damageable) meta).setDamage(Math.min(damage, material.getMaxDurability()));
        }

        int customModelData = source.integer("custom-model-data", 0);
        if (customModelData > 0) {
            try {
                meta.setCustomModelData(customModelData);
            } catch (Throwable ignored) {
                // Sehr alte Server kennen CustomModelData nicht - Item bleibt sonst gueltig.
            }
        }

        for (String flagName : source.strings("item-flags")) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(flagName.toUpperCase(Locale.ROOT).trim()));
            } catch (IllegalArgumentException e) {
                logSkipped(logger, context, "Unknown item flag: " + flagName);  // i18n-ignore: technical item-parsing log
            }
        }

        if (meta instanceof PotionMeta) {
            applyPotion((PotionMeta) meta, source.child("potion"), logger, context);
        }

        item.setItemMeta(meta);
        return item;
    }

    // ============ Teilaspekte ============

    private static void applyEnchantments(ItemStack item, ItemMeta meta, List<String> entries,
                                          Logger logger, String context) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            String[] parts = entry.split(":");
            if (parts.length != 2) {
                logSkipped(logger, context, "Malformed enchantment (expected NAME:LEVEL): " + entry);  // i18n-ignore: technical item-parsing log
                continue;
            }
            Enchantment enchantment = resolveEnchantment(parts[0]);
            if (enchantment == null) {
                logSkipped(logger, context, "Unknown enchantment: " + parts[0]);  // i18n-ignore: technical item-parsing log
                continue;
            }
            int level;
            try {
                level = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                logSkipped(logger, context, "Enchantment level is not a number: " + entry);  // i18n-ignore: technical item-parsing log
                continue;
            }
            // addUnsafeEnchantment: Event-Ausruestung darf bewusst ueber die Vanilla-Grenze
            // hinausgehen (Schaerfe X o.ae.) - genau dafuer ist der Editor da.
            if (meta != null) {
                meta.addEnchant(enchantment, level, true);
            } else {
                item.addUnsafeEnchantment(enchantment, level);
            }
        }
    }

    /**
     * Traegt Basis-Trankart und eigene Effekte ein.
     *
     * <p>Die Basisart wird ueber Reflection gesetzt: {@code PotionMeta.setBasePotionType}
     * gibt es erst ab 1.20.5, davor hiess es {@code setBasePotionData(PotionData)}. Ohne
     * diese Weiche liesse sich das Plugin nicht gegen die alte API bauen und auf neuen
     * Servern betreiben.</p>
     */
    private static void applyPotion(PotionMeta meta, Source potion, Logger logger, String context) {
        if (potion == null) {
            return;
        }

        String typeName = potion.string("type");
        if (typeName != null && !typeName.isEmpty()) {
            boolean extended = potion.bool("extended", false);
            boolean upgraded = potion.bool("upgraded", false);
            if (!setBasePotion(meta, typeName, extended, upgraded)) {
                logSkipped(logger, context, "Unknown or unsupported potion type: " + typeName);  // i18n-ignore: technical item-parsing log
            }
        }

        for (Source effect : potion.children("custom-effects")) {
            String effectName = effect.string("type");
            if (effectName == null || effectName.isEmpty()) {
                continue;
            }
            PotionEffectType type = resolvePotionEffect(effectName);
            if (type == null) {
                logSkipped(logger, context, "Unknown potion effect: " + effectName);  // i18n-ignore: technical item-parsing log
                continue;
            }
            int duration = Math.max(1, effect.integer("duration", 600));
            int amplifier = Math.max(0, effect.integer("amplifier", 0));
            boolean ambient = effect.bool("ambient", false);
            boolean particles = effect.bool("particles", true);
            meta.addCustomEffect(new PotionEffect(type, duration, amplifier, ambient, particles), true);
        }
    }

    private static boolean setBasePotion(PotionMeta meta, String typeName, boolean extended, boolean upgraded) {
        try {
            org.bukkit.potion.PotionType potionType = org.bukkit.potion.PotionType.valueOf(typeName.toUpperCase(Locale.ROOT).trim());
            meta.setBasePotionType(potionType);
            return true;
        } catch (IllegalArgumentException | NoSuchMethodError e) {
            return false;
        }
    }

    // ============ Aufloesung ============

    private static Material resolveMaterial(String name, Logger logger, String context) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String key = name.trim().toUpperCase(Locale.ROOT);
        Material cached = MATERIAL_CACHE.get(key);
        if (cached != null) {
            return cached == MATERIAL_MISSING ? null : cached;
        }

        Material material = Material.matchMaterial(key);
        if (material == null || material == Material.AIR) {
            MATERIAL_CACHE.put(key, MATERIAL_MISSING);
            // Frueher wurde hier stillschweigend STONE eingesetzt - ein falsches Item im
            // Inventar ist schwerer zu bemerken als ein fehlendes plus Logzeile.
            logSkipped(logger, context, "Unknown material: " + name);  // i18n-ignore: technical item-parsing log
            return null;
        }
        MATERIAL_CACHE.put(key, material);
        return material;
    }

    static int parseAmount(String raw, int def, int maxStack) {
        if (raw == null || raw.isBlank()) return def;
        try {
            int parsed = Integer.parseInt(raw.trim());
            return Math.max(1, Math.min(parsed, maxStack));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static String normalizeEnchantKey(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if (!key.contains(":")) key = "minecraft:" + key;
        return key;
    }

    @SuppressWarnings("deprecation")
    private static Enchantment resolveEnchantment(String rawName) {
        if (rawName == null || rawName.isBlank()) return null;
        String normalized = normalizeEnchantKey(rawName);
        if (normalized == null) return null;

        Enchantment cached = ENCHANTMENT_CACHE.get(normalized);
        if (cached != null) {
            return cached;
        }

        Enchantment enchantment = null;
        NamespacedKey nsk = NamespacedKey.fromString(normalized);
        if (nsk != null) {
            try {
                enchantment = org.bukkit.Registry.ENCHANTMENT.get(nsk);
            } catch (Throwable ignored) {
            }
        }
        if (enchantment == null) {
            // Alte Bukkit-Namen (DAMAGE_ALL statt SHARPNESS) weiterhin akzeptieren, damit
            // bestehende equipment.yml-Dateien nicht brechen.
            try {
                enchantment = Enchantment.getByName(rawName.trim().toUpperCase(Locale.ROOT));
            } catch (Throwable ignored) {
                enchantment = null;
            }
        }
        if (enchantment != null) {
            ENCHANTMENT_CACHE.put(normalized, enchantment);
        }
        return enchantment;
    }

    private static PotionEffectType resolvePotionEffect(String rawName) {
        String key = rawName.trim().toUpperCase(Locale.ROOT);
        // getByKey nimmt den heutigen Minecraft-Namen (z.B. "strength"), getByName zusaetzlich
        // die alten Bukkit-Konstanten (z.B. "INCREASE_DAMAGE"). Beide Wege offen halten,
        // damit bestehende equipment.yml-Dateien weiterhin laden.
        try {
            PotionEffectType byKey = PotionEffectType.getByKey(
                    NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
            if (byKey != null) {
                return byKey;
            }
        } catch (Throwable ignored) {
            // Rueckfall unten.
        }
        try {
            return PotionEffectType.getByName(key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static void logSkipped(Logger logger, String context, String message) {
        Logger target = logger != null ? logger : Bukkit.getLogger();
        target.log(Level.WARNING, "[Item] " + (context == null ? "" : context + ": ") + message);  // i18n-ignore: technical config parse log
    }

    // ============ Einheitlicher Zugriff auf Map und ConfigurationSection ============

    /**
     * Kleinster gemeinsamer Nenner der beiden Konfigurationsformen.
     *
     * <p>{@code equipment.yml} enthaelt Items mal als Listeneintraege (PvP-Seite,
     * {@code getMapList}) und mal als benannte Unterabschnitte (Event-Seite). Statt den
     * Bau-Code zu verdoppeln, wird nur der Zugriff hinter dieser Schnittstelle vereinheitlicht.</p>
     */
    private interface Source {
        String string(String key);

        int integer(String key, int fallback);

        boolean bool(String key, boolean fallback);

        List<String> strings(String key);

        /** Untergeordneter Abschnitt oder {@code null}. */
        Source child(String key);

        /** Liste untergeordneter Abschnitte; nie {@code null}. */
        List<Source> children(String key);
    }

    private static final class MapSource implements Source {
        private final Map<?, ?> map;

        MapSource(Map<?, ?> map) {
            this.map = map;
        }

        @Override
        public String string(String key) {
            Object value = map.get(key);
            return value == null ? null : String.valueOf(value);
        }

        @Override
        public int integer(String key, int fallback) {
            Object value = map.get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            if (value instanceof String) {
                try {
                    return Integer.parseInt(((String) value).trim());
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }
            return fallback;
        }

        @Override
        public boolean bool(String key, boolean fallback) {
            Object value = map.get(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            if (value instanceof String) {
                return Boolean.parseBoolean((String) value);
            }
            return fallback;
        }

        @Override
        public List<String> strings(String key) {
            List<String> result = new ArrayList<>();
            Object value = map.get(key);
            if (value instanceof List<?>) {
                for (Object element : (List<?>) value) {
                    if (element != null) {
                        result.add(String.valueOf(element));
                    }
                }
            } else if (value instanceof String) {
                result.add((String) value);
            }
            return result;
        }

        @Override
        public Source child(String key) {
            Object value = map.get(key);
            if (value instanceof Map<?, ?>) {
                return new MapSource((Map<?, ?>) value);
            }
            if (value instanceof ConfigurationSection) {
                return new SectionSource((ConfigurationSection) value);
            }
            return null;
        }

        @Override
        public List<Source> children(String key) {
            List<Source> result = new ArrayList<>();
            Object value = map.get(key);
            if (value instanceof List<?>) {
                for (Object element : (List<?>) value) {
                    if (element instanceof Map<?, ?>) {
                        result.add(new MapSource((Map<?, ?>) element));
                    }
                }
            }
            return result;
        }
    }

    private static final class SectionSource implements Source {
        private final ConfigurationSection section;

        SectionSource(ConfigurationSection section) {
            this.section = section;
        }

        @Override
        public String string(String key) {
            return section.getString(key);
        }

        @Override
        public int integer(String key, int fallback) {
            return section.getInt(key, fallback);
        }

        @Override
        public boolean bool(String key, boolean fallback) {
            return section.getBoolean(key, fallback);
        }

        @Override
        public List<String> strings(String key) {
            return section.getStringList(key);
        }

        @Override
        public Source child(String key) {
            ConfigurationSection nested = section.getConfigurationSection(key);
            return nested == null ? null : new SectionSource(nested);
        }

        @Override
        public List<Source> children(String key) {
            List<Source> result = new ArrayList<>();
            // Sektions-Schreibweise: benannte Unterabschnitte.
            ConfigurationSection nested = section.getConfigurationSection(key);
            if (nested != null) {
                for (String childKey : nested.getKeys(false)) {
                    ConfigurationSection child = nested.getConfigurationSection(childKey);
                    if (child != null) {
                        result.add(new SectionSource(child));
                    }
                }
                return result;
            }
            // Listen-Schreibweise, auch innerhalb eines Abschnitts erlaubt.
            for (Map<?, ?> map : section.getMapList(key)) {
                result.add(new MapSource(map));
            }
            return result;
        }
    }
}
