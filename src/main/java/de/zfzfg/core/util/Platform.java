package de.zfzfg.core.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Zentrale Laufzeit-Erkennung für die Server-Plattform.
 *
 * <p>Ermöglicht es dem Plugin, auf Paper-, Purpur- und Pufferfish-Servern
 * 100% native Adventure- und asynchrone APIs zu nutzen, während auf
 * reinem Vanilla Spigot nahtlos auf Legacy- und Standard-Bukkit-APIs
 * zurückgegriffen wird.</p>
 */
public final class Platform {

    private static final boolean IS_PAPER;
    private static final boolean IS_PURPUR;

    static {
        boolean paper = false;
        try {
            // Prüft, ob Player die native Adventure-sendMessage-Methode besitzt
            Player.class.getMethod("sendMessage", net.kyori.adventure.text.Component.class);
            paper = true;
        } catch (Throwable ignored) {
            paper = false;
        }
        IS_PAPER = paper;

        boolean purpur = false;
        try {
            Class.forName("org.purpurmc.purpur.PurpurConfig");
            purpur = true;
        } catch (Throwable ignored) {
            try {
                String version = Bukkit.getVersion();
                if (version != null && version.toLowerCase().contains("purpur")) {
                    purpur = true;
                }
            } catch (Throwable ignored2) {}
        }
        IS_PURPUR = purpur;
    }

    private Platform() {}

    /**
     * Gibt an, ob der Server auf Paper (oder einem Paper-Fork wie Purpur/Pufferfish) läuft.
     * Wenn true, sind native Kyori Adventure Component-Methoden auf Bukkit-Entities vorhanden.
     */
    public static boolean isPaper() {
        return IS_PAPER;
    }

    /**
     * Gibt an, ob der Server auf Purpur läuft.
     */
    public static boolean isPurpur() {
        return IS_PURPUR;
    }
}
