package de.zfzfg.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bruecke zwischen den Legacy-Nachrichten aus den messages_*.yml (&-Farbcodes)
 * und der Adventure-API von Purpur 26.2.
 *
 * <p>Bewusst die einzige Stelle im Plugin, die Legacy-Text parst. Wer Text an einen
 * Spieler schicken will, geht ueber {@link #of(String)} oder ueber TextUtil/MessageUtil,
 * die hier hindurch delegieren. Direkte Aufrufe von LegacyComponentSerializer an anderer
 * Stelle sind ein Fehler - dann liegt Parsing-Logik doppelt vor.
 *
 * <p>Der Serializer ist mit {@code hexColors()} konfiguriert, damit die in einigen
 * Sprachdateien vorhandenen &#RRGGBB-Codes weiterhin funktionieren.
 */
public final class Text {

    private Text() {}

    /** Parser fuer &-Codes inkl. &#RRGGBB-Hex. Thread-safe und wiederverwendbar. */
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    /** Serializer fuer Section-Codes (§) fuer Bukkit Alt-APIs. */
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .build();

    /**
     * Cache fuer bereits geparste Nachrichten. Ersetzt den frueheren String-Cache in
     * TextUtil: dieselbe Motivation (Nachrichten wiederholen sich stark), nur eine Ebene
     * weiter oben, sodass auch das Parsing gespart wird und nicht nur das Ersetzen.
     * Components sind immutable, das Teilen ist daher gefahrlos.
     */
    private static final Map<String, Component> CACHE = new ConcurrentHashMap<>();

    /** Obergrenze, damit dynamisch erzeugte Strings den Cache nicht unbegrenzt fuellen. */
    private static final int CACHE_LIMIT = 4096;

    /** Legacy-String (&- oder §-Codes) zu Component. {@code null} wird zu {@link Component#empty()}. */
    public static Component of(String legacy) {
        if (legacy == null || legacy.isEmpty()) return Component.empty();
        Component cached = CACHE.get(legacy);
        if (cached != null) return cached;
        
        String input = legacy.indexOf('§') != -1 ? legacy.replace('§', '&') : legacy;
        Component parsed = LEGACY_AMPERSAND.deserialize(input);
        if (CACHE.size() < CACHE_LIMIT) CACHE.put(legacy, parsed);
        return parsed;
    }

    /**
     * Wie {@link #of(String)}, aber ohne den Kursiv-Standard, den Minecraft auf
     * Item-Namen und Lore legt. Fuer ItemMeta IMMER diese Variante nehmen.
     */
    public static Component ofItem(String legacy) {
        return of(legacy).decoration(TextDecoration.ITALIC, false);
    }

    /** Component zurueck in einen Legacy-Section-String (§) - nur fuer Alt-APIs, die noch String wollen. */
    public static String toLegacy(Component component) {
        return component == null ? "" : LEGACY_SECTION.serialize(component);
    }

    /** Farbcodes entfernen (Ersatz fuer ChatColor.stripColor). */
    public static String plain(String legacy) {
        return PlainTextComponentSerializer.plainText().serialize(of(legacy));
    }

    /**
     * Baut einen anklickbaren Chat-Button: Beschriftung, auszufuehrender Befehl,
     * Hover-Text. Alle drei Parameter sind Legacy-Strings aus den messages_*.yml.
     *
     * <p>Der Befehl wird mit fuehrendem "/" normalisiert, weil die Aufrufer in
     * diesem Projekt es mal so und mal so uebergeben haben.
     */
    public static Component button(String label, String command, String hover) {
        String cmd = command == null ? "" : (command.startsWith("/") ? command : "/" + command);
        Component c = of(label).clickEvent(ClickEvent.runCommand(cmd));
        if (hover != null && !hover.isEmpty()) {
            c = c.hoverEvent(HoverEvent.showText(of(hover)));
        }
        return c;
    }

    /** Wie {@link #button}, aber oeffnet eine URL statt einen Befehl auszufuehren. */
    public static Component link(String label, String url, String hover) {
        Component c = of(label).clickEvent(ClickEvent.openUrl(url));
        if (hover != null && !hover.isEmpty()) {
            c = c.hoverEvent(HoverEvent.showText(of(hover)));
        }
        return c;
    }

    /** Nur fuer Tests: Cache leeren, damit Testreihenfolge egal ist. */
    static void clearCache() { CACHE.clear(); }
}
