package de.zfzfg.core.util;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Zentraler Ausgang fuer Spielernachrichten.
 *
 * <p>Historisch hat diese Klasse Legacy-Strings mit ChatColor uebersetzt und als String
 * verschickt. Seit der Umstellung auf Purpur 26.2 laeuft das Verschicken ueber Adventure
 * ({@link Text}); die String-Signaturen bleiben aber erhalten, damit die rund 700
 * Aufrufstellen im Plugin unveraendert bleiben konnten.
 *
 * <p>{@link #color(String)} gibt weiterhin einen Legacy-String zurueck - er wird an vielen
 * Stellen weiterverarbeitet (Item-Namen, Konfigvergleiche, Logausgaben) und darf deshalb
 * keine Component werden. Neuer Code sollte stattdessen direkt {@link Text#of(String)}
 * benutzen.
 */
public class TextUtil {

    /**
     * Uebersetzt &-Codes in Section-Codes. Bewusst weiterhin ueber den Adventure-Serializer,
     * damit es exakt EINE Parser-Implementierung im Plugin gibt und &#RRGGBB genauso
     * behandelt wird wie beim Verschicken.
     */
    public static String color(String text) {
        if (text == null) return "";
        return Text.toLegacy(Text.of(text));
    }

    public static String strip(String text) {
        return Text.plain(text);
    }

    /** Component-Variante fuer neuen Code. */
    public static Component component(String text) {
        return Text.of(text);
    }

    public static void send(CommandSender sender, String message) {
        if (sender == null || message == null) return;
        if (Platform.isPaper()) {
            sender.sendMessage(Text.of(message));
        } else {
            sender.sendMessage(color(message));
        }
    }

    public static void send(Player player, String message) {
        if (player == null || message == null) return;
        if (Platform.isPaper()) {
            player.sendMessage(Text.of(message));
        } else {
            player.sendMessage(color(message));
        }
    }

    /** Direktversand einer fertigen Component. */
    public static void send(CommandSender sender, Component message) {
        if (sender == null || message == null) return;
        if (Platform.isPaper()) {
            sender.sendMessage(message);
        } else {
            sender.sendMessage(Text.toLegacy(message));
        }
    }

    /** Direktversand einer fertigen Component an einen Player. */
    public static void send(Player player, Component message) {
        if (player == null || message == null) return;
        if (Platform.isPaper()) {
            player.sendMessage(message);
        } else {
            player.sendMessage(Text.toLegacy(message));
        }
    }

    /**
     * Sendet einen Titel plattformunabhängig an einen Spieler.
     * Auf Paper/Purpur wird Adventure Title mit RGB genutzt, auf Spigot die Bukkit-Titelmethode.
     */
    @SuppressWarnings("deprecation")
    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null) return;
        if (Platform.isPaper()) {
            player.showTitle(net.kyori.adventure.title.Title.title(
                    Text.of(title != null ? title : ""),
                    Text.of(subtitle != null ? subtitle : ""),
                    net.kyori.adventure.title.Title.Times.times(
                            java.time.Duration.ofMillis(fadeIn * 50L),
                            java.time.Duration.ofMillis(stay * 50L),
                            java.time.Duration.ofMillis(fadeOut * 50L))));
        } else {
            player.sendTitle(color(title), color(subtitle), fadeIn, stay, fadeOut);
        }
    }

    /**
     * Sendet einen Titel mit Standard-Timing (10 Ticks FadeIn, 60 Ticks Stay, 10 Ticks FadeOut).
     */
    public static void sendTitle(Player player, String title, String subtitle) {
        sendTitle(player, title, subtitle, 10, 60, 10);
    }
}