package de.zfzfg.core.inventory;

import de.zfzfg.core.inventory.guard.GuardContext;
import de.zfzfg.core.inventory.guard.GuardEntry;
import de.zfzfg.core.inventory.guard.GuardPhase;
import de.zfzfg.core.inventory.guard.InventoryGuard;
import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Die Fassade, mit der PvP- und Event-Modul arbeiten.
 *
 * <p>Buendelt Provider, Guard-Journal und Konfiguration, damit die Invarianten des
 * Integrationsplans an genau einer Stelle liegen statt verteilt ueber sechs Listener:</p>
 *
 * <ul>
 *   <li><b>I1/I2</b> - {@link #begin} erfasst den Abzug synchron; der Aufrufer darf danach
 *       sofort leeren, ohne auf die Platte zu warten.</li>
 *   <li><b>I4</b> - {@link #finish} wertet das Ergebnis aus und meldet Fehlschlaege,
 *       statt sie still verschwinden zu lassen.</li>
 *   <li><b>I5</b> - die Ausschuettung laeuft im Erfolgs-Callback von {@link #finish}.</li>
 *   <li><b>I6</b> - Wiederherstellung und Ausschuettung gehen durch die Exactly-Once-Tueren
 *       des Guards.</li>
 *   <li><b>I7</b> - {@link #begin} verweigert eine zweite Sitzung ueber einer offenen.</li>
 * </ul>
 */
public final class InventorySessionManager {

    private final EventPlugin plugin;
    private final InventoryGuard guard;

    /**
     * Im Speicher gehaltene Abzuege als Rueckfallebene.
     *
     * <p>Gefuellt beim {@link #begin}, geleert beim Schliessen der Sitzung. Wenn das Schreiben
     * auf die Platte scheitert oder das Backup beim Wiederherstellen nicht lesbar ist, ist das
     * hier die letzte Rettung - solange der Server nicht neu startet.</p>
     */
    private final Map<UUID, CapturedInventory> fallbacks = new ConcurrentHashMap<>();

    public InventorySessionManager(EventPlugin plugin, InventoryGuard guard) {
        this.plugin = plugin;
        this.guard = guard;
    }

    private InventoryBackupService service() {
        return plugin.getInventoryBackupService();
    }

    private InventoryManagementConfig config() {
        return plugin.getInventoryConfig();
    }

    /** Ob das Plugin die Inventare selbst verwaltet (also nicht im Legacy-Modus laeuft). */
    public boolean isManaged() {
        return config().managedByPlugin() && service().isAvailable();
    }

    /**
     * Ob die Verwaltung eingeschaltet, aber kaputt ist.
     *
     * <p>Tritt ein, wenn InventoryBackup zwar geladen wurde, seine API aber nicht bereitsteht.
     * Dieser Zustand darf nicht wie der Legacy-Betrieb behandelt werden: dort uebernimmt
     * Multiverse-Inventories, hier uebernimmt niemand. Wer jetzt ein Inventar leert, loescht
     * es endgueltig.</p>
     */
    public boolean isBroken() {
        return config().managedByPlugin() && !service().isAvailable();
    }

    /**
     * Ob nur gesichert, aber nie automatisch wiederhergestellt wird.
     *
     * <p>Das ist der Legacy-Betrieb mit Netz: den Inventartausch macht
     * Multiverse-Inventories, das Plugin legt vor jedem Arena- und Lobby-Teleport zusaetzlich
     * eine Kopie ueber InventoryBackup an. Sie wird nie von selbst angewendet - im
     * Schadensfall holt ein Admin sie ueber das Web-Panel oder {@code /inv} zurueck.</p>
     */
    public boolean isSafetyOnly() {
        return !config().managedByPlugin()
                && config().legacySafetyBackups()
                && service().isAvailable();
    }

    public InventoryGuard guard() {
        return guard;
    }

    // ------------------------------------------------------------------ beginnen

    /**
     * Ergebnis von {@link #begin}.
     */
    public enum BeginResult {
        /** Abzug erfasst, Sitzung offen - der Aufrufer darf leeren und das Kit anlegen. */
        STARTED,
        /** Fuer diesen Spieler ist bereits eine Sitzung offen (I7) - nicht fortfahren. */
        ALREADY_OPEN,
        /**
         * Die Verwaltung ist eingeschaltet, aber kein Provider da - nicht fortfahren.
         * Leeren waere hier endgueltiger Verlust.
         */
        UNAVAILABLE,
        /**
         * Legacy-Betrieb mit Netz: eine Sicherungskopie wurde angelegt, wiederhergestellt
         * wird sie nie automatisch. Der Aufrufer macht normal weiter.
         */
        BACKUP_ONLY,
        /** Legacy-Betrieb: es wird nichts verwaltet, der Aufrufer macht wie bisher weiter. */
        NOT_MANAGED
    }

    /**
     * Beginnt eine Inventar-Sitzung, bevor der Spieler in eine andere Welt teleportiert wird.
     *
     * <p>Muss <b>vor</b> dem Teleport aufgerufen werden (Invariante I1): nach dem Weltwechsel
     * haette Multiverse-Inventories das Survival-Inventar bereits ausgetauscht, und der Abzug
     * enthielte das leere Arena-Inventar.</p>
     *
     * <p>Der Abzug ist beim Zurueckkehren dieser Methode bereits genommen. Das Schreiben laeuft
     * asynchron; {@code onPersisted} meldet, ob es geklappt hat.</p>
     *
     * @param onPersisted wird auf dem Haupt-Thread aufgerufen, sobald feststeht, ob das Backup
     *                    auf der Platte liegt; darf null sein
     */
    public BeginResult begin(Player player, GuardContext context, String refId,
                             BackupContext backupContext, Consumer<Boolean> onPersisted) {
        if (isBroken()) {
            return BeginResult.UNAVAILABLE;
        }
        if (isSafetyOnly()) {
            backupOnly(player, backupContext);
            return BeginResult.BACKUP_ONLY;
        }
        if (!isManaged()) {
            return BeginResult.NOT_MANAGED;
        }
        UUID id = player.getUniqueId();
        if (guard.hasOpenSession(id)) {
            return BeginResult.ALREADY_OPEN;
        }

        // Erst erfassen, dann alles Weitere - ab hier darf der Aufrufer leeren.
        CapturedInventory snapshot = CapturedInventory.of(player);
        fallbacks.put(id, snapshot);
        String world = player.getWorld() == null ? "" : player.getWorld().getName();
        guard.openWithoutBackup(id, context, refId, world);

        service().backup(player, backupContext).thenAccept(ref -> {
            if (ref.isPresent()) {
                guard.attachBackup(id, ref.get().backupId());
                if (onPersisted != null) {
                    onPersisted.accept(true);
                }
            } else {
                plugin.getLogger().severe(plugin.getConsoleMsg("inventory-backup-not-persisted",
                        "player", player.getName()));
                if (onPersisted != null) {
                    onPersisted.accept(false);
                }
            }
        });
        return BeginResult.STARTED;
    }

    /**
     * Legt im Legacy-Betrieb nur die Sicherungskopie an.
     *
     * <p>Bewusst <b>ohne</b> Guard-Eintrag: ein Journal-Eintrag wuerde beim naechsten
     * Serverstart wiederhergestellt, und genau das soll hier nicht passieren. Ohne Eintrag
     * bleibt die Kopie ein reines Archiv, aus dem nur von Hand geholt wird.</p>
     *
     * <p>Ein Fehlschlag bricht nichts ab: den eigentlichen Schutz leistet im Legacy-Betrieb
     * Multiverse-Inventories, diese Kopie ist das Netz darunter.</p>
     */
    private void backupOnly(Player player, BackupContext backupContext) {
        service().backup(player, withSafetyMarker(backupContext)).thenAccept(ref -> {
            if (ref.isEmpty()) {
                plugin.getLogger().warning(plugin.getConsoleMsg("inventory-safety-backup-failed",
                        "player", player.getName()));
            }
        });
    }

    /**
     * Uebernimmt die Metadaten des Aufrufers und markiert die Kopie als Legacy-Sicherung.
     *
     * <p>Match- bzw. Event-ID bleiben erhalten, damit ein Admin die Kopie spaeter zuordnen
     * kann; {@code mode=legacy-safety} unterscheidet sie von einem Backup, das automatisch
     * zurueckgespielt worden waere.</p>
     */
    private BackupContext withSafetyMarker(BackupContext source) {
        BackupContext.Builder builder = BackupContext.builder(source.type());
        for (Map.Entry<String, String> entry : source.metadata().entrySet()) {
            builder.meta(entry.getKey(), entry.getValue());
        }
        return builder.meta("mode", "legacy-safety").build();
    }

    /** Meldet, dass das Kit sitzt und das Match/Event laeuft. */
    public void markActive(UUID playerId) {
        guard.phase(playerId, GuardPhase.ACTIVE);
    }

    // ---------------------------------------------------------------- beenden

    /**
     * Stellt das Inventar wieder her und schliesst die Sitzung.
     *
     * <p>Beansprucht die Wiederherstellung ueber den Guard - laufen Tod, Match-Ende und
     * Disconnect im selben Tick, gewinnt genau einer (I6). {@code onRestored} laeuft nur beim
     * ersten erfolgreichen Durchlauf und ist der Ort fuer Gewinne und Belohnungen (I5).</p>
     *
     * @param onRestored erhaelt das Ergebnis; laeuft auf dem Haupt-Thread. Bei
     *                   {@link RestoreOutcome#QUEUED_FOR_JOIN} traegt der Spieler die Items
     *                   noch nicht - Gewinne muessen dann eingereiht statt uebergeben werden.
     */
    public void finish(UUID playerId, Consumer<RestoreOutcome> onRestored) {
        if (!isManaged()) {
            if (onRestored != null) {
                onRestored.accept(RestoreOutcome.UNAVAILABLE);
            }
            return;
        }
        GuardEntry entry = guard.get(playerId);
        if (entry == null) {
            if (onRestored != null) {
                onRestored.accept(RestoreOutcome.NOT_FOUND);
            }
            return;
        }
        if (!guard.tryBeginRestore(playerId)) {
            // Ein anderer Pfad ist bereits dabei - hier nichts tun, sonst wird dupliziert.
            return;
        }

        if (!entry.hasBackup()) {
            completeWithFallback(playerId, onRestored);
            return;
        }

        service().resolve(playerId, entry.backupId()).thenAccept(ref -> {
            if (ref.isEmpty()) {
                completeWithFallback(playerId, onRestored);
                return;
            }
            service().restore(playerId, ref.get(), RestoreMode.all()).thenAccept(outcome -> {
                if (outcome == RestoreOutcome.QUEUED_FOR_JOIN) {
                    guard.phase(playerId, GuardPhase.QUEUED);
                    if (onRestored != null) {
                        onRestored.accept(outcome);
                    }
                    return;
                }
                if (outcome.isSuccess()) {
                    cleanupAfterRestore(playerId, ref.get());
                    verifyRestore(playerId, () -> {
                        if (onRestored != null) {
                            onRestored.accept(outcome);
                        }
                    });
                    return;
                }
                plugin.getLogger().warning(plugin.getConsoleMsg("inventory-restore-degraded",
                        "player", playerId.toString(), "reason", outcome.name()));
                completeWithFallback(playerId, onRestored);
            });
        });
    }

    /**
     * Prueft einen Tick nach der Wiederherstellung, ob der Zustand noch der gewollte ist.
     *
     * <p>Laeuft Multiverse-Inventories parallel, kann dessen Weltwechsel-Hook das gerade
     * Wiederhergestellte ueberschreiben. Statt darauf mit geratenen Tick-Abstaenden zu
     * hoffen, wird hier schlicht nachgesehen und noetigenfalls ein zweites Mal angewendet -
     * {@link CapturedInventory#applyTo} leert vorher, ist also wiederholbar, ohne zu
     * duplizieren.</p>
     *
     * <p>{@code onDone} laeuft <b>immer</b> und immer erst nach der Kontrolle. Das ist keine
     * Feinheit, sondern Pflicht: an {@code onDone} haengt die Ausschuettung der Gewinne, und
     * eine Nachkorrektur danach wuerde die gerade uebergebenen Items wieder loeschen.</p>
     */
    private void verifyRestore(UUID playerId, Runnable onDone) {
        de.zfzfg.core.inventory.mvi.MultiverseInventoriesBridge bridge = plugin.getMviBridge();
        Player player = Bukkit.getPlayer(playerId);
        // Beim Herunterfahren nimmt der Scheduler nichts mehr an; die Kontrolle entfaellt
        // dann, der Aufrufer muss aber trotzdem weiterlaufen - an ihm haengt die
        // Ausschuettung.
        if (bridge == null || !bridge.conflictGuardActive() || !plugin.isEnabled()
                || player == null || !player.isOnline()) {
            onDone.run();
            return;
        }

        CapturedInventory applied = CapturedInventory.of(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player later = Bukkit.getPlayer(playerId);
            if (later != null && later.isOnline()
                    && !applied.fingerprint().equals(CapturedInventory.of(later).fingerprint())) {
                applied.applyTo(later, true);
                bridge.countRecovery();
                plugin.getLogger().warning(plugin.getConsoleMsg("inventory-mvinv-overwrite-recovered",
                        "player", later.getName()));
            }
            onDone.run();
        }, 1L);
    }

    /**
     * Letzte Rettung: den im Speicher gehaltenen Abzug anwenden.
     *
     * <p>Greift, wenn das Backup nie geschrieben wurde oder nicht mehr lesbar ist. Ist der
     * Spieler offline oder gibt es keinen Abzug mehr, bleibt die Sitzung bewusst offen -
     * der Wiederanlauf beim naechsten Start soll sie sehen.</p>
     */
    private void completeWithFallback(UUID playerId, Consumer<RestoreOutcome> onRestored) {
        CapturedInventory fallback = fallbacks.get(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (fallback != null && player != null && player.isOnline()) {
            fallback.applyTo(player, true);
            fallbacks.remove(playerId);
            guard.close(playerId);
            plugin.getLogger().warning(plugin.getConsoleMsg("inventory-restore-fallback",
                    "player", player.getName()));
            if (onRestored != null) {
                onRestored.accept(RestoreOutcome.FALLBACK_APPLIED);
            }
            return;
        }
        guard.releaseRestore(playerId, GuardPhase.ORPHANED);
        plugin.getLogger().severe(plugin.getConsoleMsg("inventory-restore-orphaned",
                "player", playerId.toString()));
        if (onRestored != null) {
            onRestored.accept(RestoreOutcome.FAILED);
        }
    }

    /** Sitzung schliessen und das temporaere Backup je nach Konfiguration entfernen. */
    private void cleanupAfterRestore(UUID playerId, BackupRef ref) {
        fallbacks.remove(playerId);
        guard.close(playerId);
        if (config().cleanupAfterMatch()) {
            service().delete(ref);
        }
    }

    /**
     * Reiht die Wiederherstellung fuer den naechsten Join ein - der Weg fuer Spieler, die
     * mitten im Match oder Event die Verbindung verlieren.
     *
     * <p>Prueft vorher, ob bereits etwas eingereiht ist: {@code queueRestoreOnJoin} <b>ersetzt</b>
     * einen vorhandenen Eintrag, und der waere im Zweifel der wertvollere (I7).</p>
     */
    public void queueForJoin(UUID playerId) {
        if (!isManaged()) {
            return;
        }
        GuardEntry entry = guard.get(playerId);
        if (entry == null || !entry.hasBackup() || entry.phase() == GuardPhase.QUEUED) {
            return;
        }
        service().hasPendingRestore(playerId).thenAccept(pending -> {
            if (pending) {
                guard.phase(playerId, GuardPhase.QUEUED);
                return;
            }
            service().resolve(playerId, entry.backupId()).thenAccept(ref -> {
                if (ref.isEmpty()) {
                    guard.releaseRestore(playerId, GuardPhase.ORPHANED);
                    return;
                }
                service().queueOnJoin(playerId, ref.get(), RestoreMode.all()).thenAccept(ok -> {
                    if (ok) {
                        guard.phase(playerId, GuardPhase.QUEUED);
                    } else {
                        plugin.getLogger().warning(plugin.getConsoleMsg("inventory-queue-rejected",
                                "player", playerId.toString()));
                    }
                });
            });
        });
    }

    /**
     * Beansprucht die Ausschuettung fuer genau einen Aufrufer.
     *
     * <p>Wichtig fuer Event-Belohnungen mit Konsolenbefehlen: die sind nicht idempotent.</p>
     */
    public boolean claimPayout(UUID playerId) {
        return guard.tryMarkPayout(playerId);
    }

    /** Verwirft Sitzung und Abzug, ohne wiederherzustellen - z. B. wenn ein Match nie startete. */
    public void discard(UUID playerId) {
        fallbacks.remove(playerId);
        guard.close(playerId);
    }

    /** Ob fuer diesen Spieler ein Abzug im Speicher liegt. */
    public boolean hasFallback(UUID playerId) {
        return fallbacks.containsKey(playerId);
    }

    /** Beim Herunterfahren: Abzuege verwerfen, Journal sichern. */
    public void shutdown() {
        fallbacks.clear();
        guard.shutdown();
    }
}
