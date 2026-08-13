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
        if (sender == null) return;
        sender.sendMessage(Text.of(message));
    }

    public static void send(Player player, String message) {
        if (player == null) return;
        player.sendMessage(Text.of(message));
    }

    /** Direktversand einer fertigen Component. */
    public static void send(CommandSender sender, Component message) {
        if (sender == null || message == null) return;
        sender.sendMessage(message);
    }
}