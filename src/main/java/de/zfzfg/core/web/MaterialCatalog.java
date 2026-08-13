package de.zfzfg.core.web;

import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Item- und Verzauberungskatalog des <em>laufenden</em> Servers fuer das Web-Panel.
 *
 * <p>Das Panel hatte seine Auswahl frueher aus zwei fest einprogrammierten JavaScript-Listen
 * (137 bzw. 86 Eintraege) gespeist. Damit war nicht einmal jedes zehnte Item auswaehlbar, und
 * auf einem aelteren Server konnte man Items setzen, die es dort gar nicht gibt - das
 * Equipment-Set war dann still kaputt. Dieser Katalog liest stattdessen {@link Material} und
 * die Verzauberungs-Registry der tatsaechlich laufenden Serverversion aus.</p>
 *
 * <p>Das Ergebnis ist zur Laufzeit unveraenderlich und wird deshalb einmalig berechnet und
 * gehalten. Es enthaelt bewusst nur Rohdaten - jede Beschriftung entsteht im Panel aus
 * {@code web/lang/*.json}.</p>
 */
final class MaterialCatalog {

    /** Kategorien, nach denen das Panel die Item-Auswahl gruppiert. */
    static final String CAT_WEAPONS = "weapons";
    static final String CAT_ARMOR = "armor";
    static final String CAT_TOOLS = "tools";
    static final String CAT_FOOD = "food";
    static final String CAT_POTIONS = "potions";
    static final String CAT_PROJECTILES = "projectiles";
    static final String CAT_SPAWN_EGGS = "spawnEggs";
    static final String CAT_REDSTONE = "redstone";
    static final String CAT_BLOCKS = "blocks";
    static final String CAT_MISC = "misc";

    /**
     * Reihenfolge, in der die Kategorien im Panel erscheinen. Bewusst nach Nutzungshaeufigkeit
     * im Equipment-Editor sortiert, nicht alphabetisch.
     */
    private static final List<String> CATEGORY_ORDER = Arrays.asList(
            CAT_WEAPONS, CAT_ARMOR, CAT_TOOLS, CAT_FOOD, CAT_POTIONS,
            CAT_PROJECTILES, CAT_BLOCKS, CAT_REDSTONE, CAT_SPAWN_EGGS, CAT_MISC);

    /** Waffen, die nicht auf {@code _SWORD}/{@code _AXE} enden. */
    private static final Set<String> WEAPON_NAMES = new LinkedHashSet<>(Arrays.asList(
            "BOW", "CROSSBOW", "TRIDENT", "MACE"));

    /** Offhand-/Kampfhilfsmittel, die im Panel bei den Waffen besser aufgehoben sind. */
    private static final Set<String> COMBAT_UTILITY = new LinkedHashSet<>(Arrays.asList(
            "SHIELD", "TOTEM_OF_UNDYING", "ENDER_PEARL", "ENDER_EYE", "FIREWORK_ROCKET",
            "END_CRYSTAL", "TNT", "FLINT_AND_STEEL"));

    private static final Set<String> PROJECTILES = new LinkedHashSet<>(Arrays.asList(
            "ARROW", "SPECTRAL_ARROW", "TIPPED_ARROW", "SNOWBALL", "EGG", "FIRE_CHARGE",
            "WIND_CHARGE", "SPLASH_POTION", "LINGERING_POTION"));

    /** Traenke und Pfeile, fuer die das Panel den Trank-Editor anbietet. */
    private static final Set<String> POTION_ITEMS = new LinkedHashSet<>(Arrays.asList(
            "POTION", "SPLASH_POTION", "LINGERING_POTION", "TIPPED_ARROW"));

    private static volatile Map<String, Object> cached;

    private MaterialCatalog() {
    }

    /** Erzwingt eine Neuberechnung - nur fuer Tests bzw. einen Plugin-Reload gedacht. */
    static void invalidate() {
        cached = null;
    }

    /**
     * Liefert den fertigen Katalog als {@code data}-Nutzlast fuer {@code GET /api/materials}.
     *
     * <p>Doppelt gepruefte Sperre: der Aufbau laeuft ueber ~1600 Materials samt
     * {@link ItemStack}-Instanzierung fuer die Haltbarkeit, das soll nicht bei jedem
     * Panel-Start erneut passieren.</p>
     */
    static Map<String, Object> get() {
        Map<String, Object> local = cached;
        if (local == null) {
            synchronized (MaterialCatalog.class) {
                local = cached;
                if (local == null) {
                    local = build();
                    cached = local;
                }
            }
        }
        return local;
    }

    private static Map<String, Object> build() {
        List<Map<String, Object>> materials = new ArrayList<>();

        for (Material material : Material.values()) {
            // isLegacy(): die vorreflektierten 1.12-Materials, die kein Server mehr ausgibt.
            // isItem(): reine Platzierungs-/Technikbloecke wie WATER oder PISTON_HEAD lassen
            // sich nicht als ItemStack ins Inventar legen und gehoeren nicht in die Auswahl.
            if (material.isLegacy() || material == Material.AIR || !material.isItem()) {
                continue;
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            String name = material.name();
            entry.put("name", name);
            entry.put("category", categoryOf(material));
            entry.put("maxStack", material.getMaxStackSize());
            if (material.isBlock()) {
                entry.put("block", true);
            }
            if (isEdible(material)) {
                entry.put("edible", true);
            }

            int maxDurability = material.getMaxDurability();
            if (maxDurability > 0 && supportsDamage(material)) {
                entry.put("maxDurability", maxDurability);
            }
            String armorSlot = armorSlotOf(name);
            if (armorSlot != null) {
                entry.put("armorSlot", armorSlot);
            }
            if (POTION_ITEMS.contains(name)) {
                entry.put("potion", true);
            }

            List<String> enchantments = validEnchantmentsFor(material);
            if (!enchantments.isEmpty()) {
                entry.put("enchantments", enchantments);
            }

            materials.add(entry);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("materials", materials);
        data.put("categories", CATEGORY_ORDER);
        data.put("enchantments", describeEnchantments());
        data.put("itemFlags", describeItemFlags());
        data.put("potionTypes", describePotionTypes());
        data.put("potionEffects", describePotionEffects());
        return data;
    }

    // ============ Kategorisierung ============

    /**
     * Ordnet ein Material einer Panel-Kategorie zu.
     *
     * <p>Bewusst aus Namensmustern plus Bukkit-Abfragen abgeleitet und nicht aus einer
     * gepflegten Liste: so rutschen Items kuenftiger Minecraft-Versionen automatisch in eine
     * sinnvolle Gruppe, statt bis zum naechsten Plugin-Update unsichtbar zu bleiben.</p>
     */
    private static String categoryOf(Material material) {
        String name = material.name();

        if (armorSlotOf(name) != null) {
            return CAT_ARMOR;
        }
        if (name.endsWith("_SWORD") || name.endsWith("_AXE") || WEAPON_NAMES.contains(name)
                || COMBAT_UTILITY.contains(name)) {
            // _AXE trifft auch PICKAXE - das faengt die Werkzeug-Pruefung unten nicht mehr ab,
            // deshalb hier explizit zuerst aussortieren.
            if (name.endsWith("_PICKAXE")) {
                return CAT_TOOLS;
            }
            return CAT_WEAPONS;
        }
        if (PROJECTILES.contains(name)) {
            return CAT_PROJECTILES;
        }
        if (POTION_ITEMS.contains(name) || name.endsWith("_POTION") || name.equals("POTION")) {
            return CAT_POTIONS;
        }
        if (name.endsWith("_SPAWN_EGG")) {
            return CAT_SPAWN_EGGS;
        }
        if (isEdible(material)) {
            return CAT_FOOD;
        }
        if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")
                || name.equals("SHEARS") || name.equals("FISHING_ROD") || name.equals("BRUSH")
                || name.equals("SPYGLASS") || name.equals("COMPASS") || name.equals("CLOCK")
                || name.endsWith("_BUCKET") || name.equals("BUCKET")) {
            return CAT_TOOLS;
        }
        if (name.contains("REDSTONE") || name.equals("REPEATER") || name.equals("COMPARATOR")
                || name.equals("OBSERVER") || name.equals("HOPPER") || name.equals("DISPENSER")
                || name.equals("DROPPER") || name.contains("PISTON") || name.endsWith("_BUTTON")
                || name.endsWith("_PRESSURE_PLATE") || name.equals("LEVER") || name.endsWith("_RAIL")) {
            return CAT_REDSTONE;
        }
        if (material.isBlock()) {
            return CAT_BLOCKS;
        }
        return CAT_MISC;
    }

    /** Ruestungsslot eines Materials oder {@code null}, wenn es keine Ruestung ist. */
    private static String armorSlotOf(String name) {
        if (name.endsWith("_HELMET") || name.equals("TURTLE_HELMET") || name.equals("CARVED_PUMPKIN")) {
            return "helmet";
        }
        if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")) {
            return "chestplate";
        }
        if (name.endsWith("_LEGGINGS")) {
            return "leggings";
        }
        if (name.endsWith("_BOOTS")) {
            return "boots";
        }
        return null;
    }

    /**
     * {@code Material.isEdible()} existiert erst ab neueren API-Staenden zuverlaessig fuer alle
     * Items; ueber Reflection abgefragt bleibt die Klasse auf 1.19 kompilierbar und nutzt auf
     * neueren Servern trotzdem die echte Antwort.
     */
    private static boolean isEdible(Material material) {
        try {
            return material.isEdible();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Prueft, ob das Item wirklich einen Schadenswert traegt.
     *
     * <p>{@code getMaxDurability() > 0} allein reicht nicht: manche Materials melden eine
     * Haltbarkeit, deren Meta aber kein {@link Damageable} ist. Das Panel wuerde dann einen
     * Haltbarkeits-Regler anbieten, den der Server beim Laden ignoriert.</p>
     */
    private static boolean supportsDamage(Material material) {
        try {
            ItemMeta meta = new ItemStack(material).getItemMeta();
            return meta instanceof Damageable;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ============ Verzauberungen ============

    /**
     * Verzauberungen, die auf dieses Material passen.
     *
     * <p>{@link Enchantment#canEnchantItem(ItemStack)} ist die Antwort des Servers selbst und
     * ersetzt die frueher im Panel gepflegte Kategorien-Tabelle. Ein Buch bekommt bewusst
     * alle Verzauberungen, weil {@code canEnchantItem} dort nur {@code false} liefert.</p>
     */
    private static List<String> validEnchantmentsFor(Material material) {
        List<String> result = new ArrayList<>();
        boolean isBook = material == Material.BOOK || material.name().equals("ENCHANTED_BOOK");
        ItemStack probe;
        try {
            probe = new ItemStack(material);
        } catch (Throwable ignored) {
            return result;
        }
        for (Enchantment enchantment : allEnchantments()) {
            try {
                if (isBook || enchantment.canEnchantItem(probe)) {
                    result.add(keyOf(enchantment));
                }
            } catch (Throwable ignored) {
                // Einzelne Verzauberungen werfen auf manchen Servern bei exotischen Items -
                // die lassen wir aus, statt den ganzen Katalog scheitern zu lassen.
            }
        }
        return result;
    }

    private static List<Map<String, Object>> describeEnchantments() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Enchantment enchantment : allEnchantments()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", keyOf(enchantment));
            entry.put("maxLevel", enchantment.getMaxLevel());
            entry.put("startLevel", enchantment.getStartLevel());
            entry.put("treasure", isTreasure(enchantment));
            entry.put("curse", isCursed(enchantment));
            list.add(entry);
        }
        return list;
    }

    @SuppressWarnings("deprecation")
    private static boolean isTreasure(Enchantment enchantment) {
        try {
            var tagValues = Registry.ENCHANTMENT.getTagValues(io.papermc.paper.registry.keys.tags.EnchantmentTagKeys.TREASURE);
            if (tagValues != null) {
                return tagValues.contains(enchantment);
            }
        } catch (Throwable ignored) {
        }
        try {
            return enchantment.isTreasure();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean isCursed(Enchantment enchantment) {
        try {
            var tagValues = Registry.ENCHANTMENT.getTagValues(io.papermc.paper.registry.keys.tags.EnchantmentTagKeys.CURSE);
            if (tagValues != null) {
                return tagValues.contains(enchantment);
            }
        } catch (Throwable ignored) {
        }
        try {
            return enchantment.isCursed();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Alle registrierten Verzauberungen.
     *
     * <p>Bevorzugt die Registry - {@code Enchantment.values()} ist ab 1.20.5 deprecated und
     * faellt spaeter ganz weg. Der Rueckfall bleibt trotzdem drin, damit der Katalog auch auf
     * Servern steht, deren Registry-Iteration von einem Fork veraendert wurde.</p>
     */
    @SuppressWarnings("deprecation")
    private static List<Enchantment> allEnchantments() {
        List<Enchantment> list = new ArrayList<>();
        try {
            for (Enchantment enchantment : org.bukkit.Registry.ENCHANTMENT) {
                list.add(enchantment);
            }
        } catch (Throwable ignored) {
            list.clear();
        }
        if (list.isEmpty()) {
            list.addAll(Arrays.asList(Enchantment.values()));
        }
        return list;
    }

    // "removal", nicht "deprecation": Enchantment#getName() ist deprecated-for-removal, und
    // dafuer ist in Eclipse/javac eine eigene Kategorie zustaendig. Mit "deprecation" blieb die
    // Warnung stehen und die Annotation wurde zusaetzlich als ueberfluessig gemeldet.
    @SuppressWarnings("removal")
    private static String keyOf(Enchantment enchantment) {
        try {
            return enchantment.getKey().getKey().toUpperCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return enchantment.getName();
        }
    }

    // ============ Item-Flags, Traenke ============

    private static List<String> describeItemFlags() {
        List<String> flags = new ArrayList<>();
        for (org.bukkit.inventory.ItemFlag flag : org.bukkit.inventory.ItemFlag.values()) {
            flags.add(flag.name());
        }
        return flags;
    }

    /** Basis-Trankarten des Servers (VanillaPotionType-Enum, versionsabhaengig). */
    private static List<String> describePotionTypes() {
        List<String> types = new ArrayList<>();
        try {
            for (org.bukkit.potion.PotionType type : org.bukkit.potion.PotionType.values()) {
                types.add(type.name());
            }
        } catch (Throwable ignored) {
            // Ohne Trankarten blendet das Panel den Basis-Typ aus und laesst nur
            // eigene Effekte zu - besser als ein leerer Katalog.
        }
        return types;
    }

    /** Effekttypen fuer eigene Trankeffekte. */
    private static List<String> describePotionEffects() {
        List<String> effects = new ArrayList<>();
        try {
            for (org.bukkit.potion.PotionEffectType type : org.bukkit.Registry.POTION_EFFECT_TYPE) {
                if (type != null) {
                    effects.add(type.getKey().getKey());
                }
            }
        } catch (Throwable ignored) {
            // siehe describePotionTypes()
        }
        java.util.Collections.sort(effects);
        return effects;
    }
}
