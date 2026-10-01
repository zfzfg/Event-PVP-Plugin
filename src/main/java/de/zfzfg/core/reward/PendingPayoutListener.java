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
 * <p>Beim Join laufen zwei
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
 * Der erste Versuch erfolgt nach dem Guard-Netz; offene Wiederherstellungen blockieren
 * die Ausgabe unabhaengig von ihrer Dauer. Der Restore-Abschluss versucht erneut.</p>
 */
public final class PendingPayoutListener implements Listener {

    /** First attempt after join. Guard/pending state, rather than this delay, gates delivery. */
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
            deliverWhenSafe(plugin, event.getPlayer().getUniqueId());
        }, Time.ticks(DELAY_TICKS));
    }
    /** No payout while a restore can still overwrite it; errors retain stored rewards. */
    public static void deliverWhenSafe(EventPlugin plugin, java.util.UUID id) {
        if (!plugin.isEnabled()) return;
        var guard = plugin.getInventoryGuard();
        if (guard != null && guard.hasOpenSession(id)) return;
        var store = plugin.getPendingPayouts();
        if (store == null || !store.hasPending(id)) return;
        var service = plugin.getInventoryBackupService();
        if (service == null) return;
        if (!service.isAvailable() && plugin.getInventoryConfig() != null
                && plugin.getInventoryConfig().managedByPlugin()) return;
        service.hasPendingRestore(id).thenAcceptAsync(pending -> {
            if (pending || guard != null && guard.hasOpenSession(id)) return;
            var player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) return;
            int delivered = store.deliverAll(player);
            if (delivered > 0) player.sendMessage(de.zfzfg.eventplugin.util.ColorUtil.color(
                    plugin.getConfigManager().getMessage("rewards.delivered-on-join")));
        }, de.zfzfg.core.inventory.InventoryTasks.executor(plugin)).exceptionally(error -> {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "[Payouts] Restore check failed for " + id, error);
            return null;
        });
    }

}
