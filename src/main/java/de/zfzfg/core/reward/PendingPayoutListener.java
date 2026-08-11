package de.zfzfg.core.reward;

import de.zfzfg.core.util.Time;
import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Reicht offene Gewinne und Belohnungen beim Join nach.
 *
 * <p><b>Die Verzoegerung ist der eigentliche Punkt dieser Klasse.</b> Beim Join laufen zwei
 * Dinge an, die sich gegenseitig ausloeschen koennen:</p>
 * <ol>
 *   <li>Die eingereihte Wiederherstellung des Survival-Inventars - entweder ueber den
 *       Join-Hook von InventoryBackup oder ueber {@code InventoryGuardListener} nach
 *       10 Ticks. Sie setzt das Inventar auf den gesicherten Stand zurueck.</li>
 *   <li>Die Ausgabe der offenen Posten aus {@link PendingPayoutStore}.</li>
 * </ol>
 *
 * <p>Laeuft die Ausgabe zuerst, loescht die nachfolgende Wiederherstellung sie im selben
 * Moment wieder - genau der Fehler, den die Reihenfolge nach Match und Event vermeidet.
 * Deshalb wartet diese Klasse bewusst laenger als das Guard-Netz.</p>
 */
public final class PendingPayoutListener implements Listener {

    /**
     * Abstand zum Join in Ticks.
     *
     * <p>Muss ueber den 10 Ticks des {@code InventoryGuardListener} liegen. 30 Ticks
     * (1,5 Sekunden) lassen zusaetzlich Luft fuer die Wiederherstellung durch
     * InventoryBackup selbst, die einen eigenen Zeitpunkt waehlt.</p>
     */
    private static final int DELAY_TICKS = 30;

    private final EventPlugin plugin;

    public PendingPayoutListener(EventPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        PendingPayoutStore store = plugin.getPendingPayouts();
        if (store == null || !store.hasPending(event.getPlayer().getUniqueId())) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) {
                // Wieder weg - die Posten bleiben in der Datei und kommen beim naechsten Mal.
                return;
            }
            int delivered = store.deliverAll(event.getPlayer());
            if (delivered > 0) {
                event.getPlayer().sendMessage(de.zfzfg.eventplugin.util.ColorUtil.color(
                        plugin.getConfigManager().getMessage("rewards.delivered-on-join")));
            }
        }, Time.ticks(DELAY_TICKS));
    }
}
