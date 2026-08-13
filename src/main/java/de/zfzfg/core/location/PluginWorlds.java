package de.zfzfg.core.location;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.model.EventConfig;
import de.zfzfg.pvpwager.models.Arena;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Beantwortet, ob eine Welt dem Plugin gehoert - also Event-, Lobby- oder Arenawelt ist.
 *
 * <p>Wird gebraucht, um gestrandete Spieler zu erkennen: wer in einer solchen Welt einloggt,
 * ohne dass dort noch etwas laeuft, kommt aus eigener Kraft nicht mehr heraus.</p>
 *
 * <p>Bewusst <b>nicht</b> zwischengespeichert: Arenen und Events lassen sich zur Laufzeit
 * ueber das Web-Panel anlegen und aendern, und die Frage wird nur beim Join eines Spielers
 * gestellt - ein paar Dutzend Zeichenkettenvergleiche fallen dort nicht ins Gewicht. Ein
 * Cache waere hier eine Fehlerquelle ohne Gegenwert.</p>
 */
public final class PluginWorlds {

    private final EventPlugin plugin;

    public PluginWorlds(EventPlugin plugin) {
        this.plugin = plugin;
    }

    /** Ob diese Welt zu einem Event, einer Lobby oder einer Arena gehoert. */
    public boolean isManaged(String worldName) {
        if (worldName == null || worldName.isEmpty()) {
            return false;
        }
        // Die Hauptwelt gehoert nie dem Plugin, auch wenn sie irgendwo als Lobby steht -
        // sonst wuerde der Gestrandeten-Pfad dort jeden Join anfassen.
        String mainWorld = plugin.getConfigManager() == null
                ? null
                : plugin.getConfigManager().getMainWorld();
        if (mainWorld != null && mainWorld.equalsIgnoreCase(worldName)) {
            return false;
        }
        return managedWorldNames().contains(worldName.toLowerCase(Locale.ROOT));
    }

    /** Alle Welten, die das Plugin fuer Events, Lobbys oder Arenen benutzt - kleingeschrieben. */
    public Set<String> managedWorldNames() {
        Set<String> names = new HashSet<>();

        try {
            if (plugin.getConfigManager() != null) {
                for (EventConfig config : plugin.getConfigManager().getAllEvents().values()) {
                    add(names, config.getEventWorld());
                    add(names, config.getLobbyWorld());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning(plugin.getConsoleMsg("managed-worlds-scan-failed",
                    "error", String.valueOf(e.getMessage())));
        }

        try {
            if (plugin.getArenaManager() != null) {
                for (Arena arena : plugin.getArenaManager().getArenas().values()) {
                    add(names, arena.getArenaWorld());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning(plugin.getConsoleMsg("managed-worlds-scan-failed",
                    "error", String.valueOf(e.getMessage())));
        }

        return names;
    }

    private void add(Set<String> names, String worldName) {
        if (worldName != null && !worldName.trim().isEmpty()) {
            names.add(worldName.trim().toLowerCase(Locale.ROOT));
        }
    }
}
