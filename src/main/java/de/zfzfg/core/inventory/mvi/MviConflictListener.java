package de.zfzfg.core.inventory.mvi;

import de.zfzfg.core.inventory.CapturedInventory;
import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Haelt den Inventarzustand ueber einen Weltwechsel hinweg fest, solange eine Match- oder
 * Event-Sitzung offen ist.
 *
 * <p>Das Problem: Multiverse-Inventories tauscht beim Weltwechsel aus, was ein Spieler
 * traegt. Waehrend eines Matches ist das genau der falsche Moment - dort entscheidet dieses
 * Plugin, was der Spieler bei sich hat, und der Tausch macht daraus einen fremden
 * Gruppenzustand.</p>
 *
 * <p>Die Loesung braucht kein Raten mehr ueber Tick-Abstaende, sondern nutzt die
 * Event-Prioritaeten: bei {@link EventPriority#LOWEST} - also bevor Multiverse-Inventories
 * dran ist - wird der Zustand gemerkt, bei {@link EventPriority#MONITOR} - also nachdem alle
 * anderen fertig sind - wird verglichen und noetigenfalls wiederhergestellt.</p>
 *
 * <p>Angefasst werden ausschliesslich Spieler mit offener Guard-Sitzung. Alle uebrigen
 * Weltwechsel laufen unberuehrt durch: ausserhalb von Matches und Events darf
 * Multiverse-Inventories seine Arbeit machen.</p>
 */
public final class MviConflictListener implements Listener {

    private final EventPlugin plugin;
    private final MultiverseInventoriesBridge bridge;

    /** Zustand vor dem Weltwechsel, nur fuer die Dauer eines einzigen Events. */
    private final Map<UUID, CapturedInventory> inTransit = new ConcurrentHashMap<>();

    public MviConflictListener(EventPlugin plugin, MultiverseInventoriesBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    /**
     * Vor allen anderen Handlern: festhalten, was der Spieler beim Wechsel trug.
     *
     * <p>{@code PlayerChangedWorldEvent} laeuft, wenn der Wechsel bereits vollzogen ist -
     * der Abzug zeigt also den Zustand, den der Spieler in die neue Welt mitgebracht hat.
     * Genau der soll erhalten bleiben.</p>
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldChangeEarly(PlayerChangedWorldEvent event) {
        if (!shouldProtect(event.getPlayer())) {
            return;
        }
        inTransit.put(event.getPlayer().getUniqueId(), CapturedInventory.of(event.getPlayer()));
    }

    /**
     * Nach allen anderen Handlern: vergleichen und, falls jemand dazwischengegangen ist,
     * den festgehaltenen Zustand wiederherstellen.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChangeLate(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        CapturedInventory before = inTransit.remove(player.getUniqueId());
        if (before == null || !player.isOnline()) {
            return;
        }
        if (before.fingerprint().equals(CapturedInventory.of(player).fingerprint())) {
            return;
        }

        // Zwischen LOWEST und MONITOR hat ein anderes Plugin das Inventar getauscht. In
        // einer offenen Sitzung gibt es dafuer keinen legitimen Grund: Kit und Backup
        // gehoeren hier diesem Plugin.
        before.applyTo(player, true);
        bridge.countRecovery();
        plugin.getLogger().warning(plugin.getConsoleMsg("inventory-mvinv-worldchange-recovered",
                "player", player.getName(), "world", player.getWorld().getName()));
    }

    /** Ein Spieler, der mitten im Wechsel geht, hinterlaesst keinen Abzug im Speicher. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        inTransit.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Nur Spieler mit offener Sitzung und nur, solange der Schutz eingeschaltet ist.
     *
     * <p>Die Pruefung sitzt bewusst hier und nicht bei der Registrierung: der Provider laesst
     * sich im Web-Panel zur Laufzeit umstellen, der Listener bleibt dabei registriert.</p>
     */
    private boolean shouldProtect(Player player) {
        if (!bridge.conflictGuardActive()) {
            return false;
        }
        return plugin.getInventoryGuard() != null
                && plugin.getInventoryGuard().hasOpenSession(player.getUniqueId());
    }
}
