package de.zfzfg.core.inventory.guard;

import de.zfzfg.core.inventory.BackupRef;
import de.zfzfg.core.inventory.InventoryBackupService;
import de.zfzfg.core.inventory.InventoryManagementConfig;
import de.zfzfg.core.inventory.RestoreMode;
import de.zfzfg.core.inventory.RestoreOutcome;
import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistentes Journal aller offenen Inventar-Sitzungen.
 *
 * <p>Ohne dieses Journal lebt die Zuordnung "wem gehoert welches Backup" nur im
 * {@code Match}- bzw. {@code EventSession}-Objekt, also im Arbeitsspeicher. Ein Serverabsturz
 * waehrend eines Matches wuerde das Backup zwar auf der Platte zuruecklassen, aber niemand
 * wuesste mehr, dass es offen war - genau der Verlust, den die Umstellung verhindern soll.</p>
 *
 * <p>Geschrieben wird <b>synchron</b>. Die Datei enthaelt nur offene Sitzungen (im
 * Normalbetrieb also fast immer nichts), und Crash-Sicherheit ist hier mehr wert als die
 * eingesparten Millisekunden.</p>
 *
 * <h2>Exactly-Once</h2>
 * {@link #tryBeginRestore(UUID)} und {@link #tryMarkPayout(UUID)} sind die einzigen Tueren zu
 * Wiederherstellung und Ausschuettung. Beide geben nur dem ersten Aufrufer ein {@code true} -
 * damit koennen Tod, Match-Ende und Quit gleichzeitig feuern, ohne zu duplizieren.
 */
public final class InventoryGuard {

    private static final String FILE_NAME = "inventory-guard.yml";

    private final EventPlugin plugin;
    private final Map<UUID, GuardEntry> sessions = new ConcurrentHashMap<>();
    private final Object fileLock = new Object();
    private volatile boolean loaded;

    public InventoryGuard(EventPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ oeffnen

    /**
     * Oeffnet eine Sitzung mit bereits vorliegendem Backup.
     *
     * <p>Eine bestehende Sitzung wird <b>nicht</b> ueberschrieben (Invariante I7): das zweite
     * Backup entstuende ueber dem Kit-Zustand und machte das erste wertlos.</p>
     *
     * @return false, wenn bereits eine Sitzung offen war
     */
    public boolean open(UUID playerId, GuardContext context, String refId, String backupId,
                        String originWorld) {
        if (sessions.containsKey(playerId)) {
            return false;
        }
        GuardEntry entry = new GuardEntry(playerId, context, refId, backupId,
                GuardPhase.BACKED_UP, originWorld, System.currentTimeMillis(), false);
        sessions.put(playerId, entry);
        save();
        return true;
    }

    /**
     * Oeffnet eine Sitzung, deren Backup nicht geschrieben werden konnte.
     *
     * <p>Der Eintrag ist bewusst trotzdem vorhanden: er verhindert ein zweites Backup ueber
     * dem Kit-Zustand und macht den Zustand im Web-Panel sichtbar.</p>
     */
    public boolean openWithoutBackup(UUID playerId, GuardContext context, String refId,
                                     String originWorld) {
        return open(playerId, context, refId, null, originWorld);
    }

    /** Traegt die Backup-ID nach, sobald das asynchrone Schreiben fertig ist. */
    public void attachBackup(UUID playerId, String backupId) {
        GuardEntry entry = sessions.get(playerId);
        if (entry != null) {
            entry.backupId(backupId);
            save();
        }
    }

    /** Setzt die Phase, z. B. auf {@link GuardPhase#ACTIVE}, sobald das Kit sitzt. */
    public void phase(UUID playerId, GuardPhase phase) {
        GuardEntry entry = sessions.get(playerId);
        if (entry != null && entry.phase() != phase) {
            entry.phase(phase);
            save();
        }
    }

    // ------------------------------------------------------------- exactly-once

    /**
     * Beansprucht die Wiederherstellung fuer genau einen Aufrufer.
     *
     * <p>Tod, Match-Ende und Disconnect koennen im selben Tick alle drei
     * wiederherstellen wollen. Nur der erste bekommt hier ein {@code true}.</p>
     */
    public synchronized boolean tryBeginRestore(UUID playerId) {
        GuardEntry entry = sessions.get(playerId);
        if (entry == null || entry.phase() == GuardPhase.RESTORING) {
            return false;
        }
        entry.phase(GuardPhase.RESTORING);
        save();
        return true;
    }

    /**
     * Gibt eine beanspruchte, aber gescheiterte Wiederherstellung wieder frei, damit ein
     * spaeterer Versuch (Join, Wiederanlauf) sie erneut aufnehmen kann.
     */
    public void releaseRestore(UUID playerId, GuardPhase newPhase) {
        GuardEntry entry = sessions.get(playerId);
        if (entry != null) {
            entry.phase(newPhase);
            save();
        }
    }

    /**
     * Beansprucht die Ausschuettung von Gewinnen bzw. Belohnungen.
     *
     * <p>Wichtig fuer Event-Belohnungen, die Konsolenbefehle ausfuehren: die sind nicht
     * idempotent, ein zweiter Durchlauf zahlt doppelt aus.</p>
     */
    public synchronized boolean tryMarkPayout(UUID playerId) {
        GuardEntry entry = sessions.get(playerId);
        if (entry == null) {
            // Ohne Sitzung gibt es nichts zu schuetzen - der Aufrufer darf ausschuetten.
            return true;
        }
        if (entry.payoutDone()) {
            return false;
        }
        entry.payoutDone(true);
        save();
        return true;
    }

    /** Schliesst eine Sitzung. Idempotent. */
    public boolean close(UUID playerId) {
        GuardEntry removed = sessions.remove(playerId);
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------- lesen

    public boolean hasOpenSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public GuardEntry get(UUID playerId) {
        return sessions.get(playerId);
    }

    public Collection<GuardEntry> openSessions() {
        return new ArrayList<>(sessions.values());
    }

    public int openCount() {
        return sessions.size();
    }

    // -------------------------------------------------------------- persistenz

    /** Liest das Journal von der Platte. Einmal beim Start. */
    public void load() {
        synchronized (fileLock) {
            sessions.clear();
            File file = file();
            if (file.exists()) {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                ConfigurationSection root = cfg.getConfigurationSection("sessions");
                if (root != null) {
                    for (String key : root.getKeys(false)) {
                        try {
                            UUID id = UUID.fromString(key);
                            ConfigurationSection sec = root.getConfigurationSection(key);
                            if (sec == null) {
                                continue;
                            }
                            sessions.put(id, new GuardEntry(id,
                                    GuardContext.from(sec.getString("context")),
                                    sec.getString("ref-id", ""),
                                    sec.getString("backup-id", null),
                                    GuardPhase.from(sec.getString("phase")),
                                    sec.getString("origin-world", ""),
                                    sec.getLong("opened-at", System.currentTimeMillis()),
                                    sec.getBoolean("payout-done", false)));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning(plugin.getConsoleMsg("guard-entry-invalid",
                                    "entry", key));
                        }
                    }
                }
            }
            loaded = true;
        }
    }

    private void save() {
        if (!loaded) {
            return;
        }
        synchronized (fileLock) {
            try {
                YamlConfiguration cfg = new YamlConfiguration();
                cfg.set("version", 1);
                for (GuardEntry entry : sessions.values()) {
                    String key = "sessions." + entry.playerId();
                    cfg.set(key + ".context", entry.context().name());
                    cfg.set(key + ".ref-id", entry.refId());  // i18n-ignore: YAML-Pfadfragment in inventory-guard.yml
                    cfg.set(key + ".backup-id", entry.backupId());  // i18n-ignore: YAML-Pfadfragment in inventory-guard.yml
                    cfg.set(key + ".phase", entry.phase().name());
                    cfg.set(key + ".origin-world", entry.originWorld());  // i18n-ignore: YAML-Pfadfragment in inventory-guard.yml
                    cfg.set(key + ".opened-at", entry.openedAt());  // i18n-ignore: YAML-Pfadfragment in inventory-guard.yml
                    cfg.set(key + ".payout-done", entry.payoutDone());  // i18n-ignore: YAML-Pfadfragment in inventory-guard.yml
                }
                File dir = plugin.getDataFolder();
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                cfg.save(file());
            } catch (Exception e) {
                plugin.getLogger().severe(plugin.getConsoleMsg("guard-save-failed",
                        "error", String.valueOf(e.getMessage())));
            }
        }
    }

    private File file() {
        return new File(plugin.getDataFolder(), FILE_NAME);
    }

    // ------------------------------------------------------------ wiederanlauf

    /**
     * Arbeitet beim Serverstart alle Sitzungen ab, die ein vorheriger Lauf offen gelassen hat.
     *
     * <p>Wird nach dem Laden aufgerufen. Online-Spieler bekommen ihr Inventar sofort zurueck,
     * offline stehende werden fuer den naechsten Join eingereiht. Ein Eintrag ohne
     * auffindbares Backup wird als {@link GuardPhase#ORPHANED} markiert und bleibt stehen -
     * er soll einem Admin auffallen und nicht still verschwinden.</p>
     */
    public void recoverOpenSessions() {
        InventoryManagementConfig config = plugin.getInventoryConfig();
        if (!config.guardEnabled() || !config.restoreOrphansOnStart()) {
            return;
        }
        List<GuardEntry> open = new ArrayList<>(sessions.values());
        if (open.isEmpty()) {
            return;
        }

        int orphaned = 0;
        int handled = 0;
        for (GuardEntry entry : open) {
            if (!entry.hasBackup()) {
                entry.phase(GuardPhase.ORPHANED);
                orphaned++;
                continue;
            }
            if (entry.phase() == GuardPhase.QUEUED) {
                // Bereits eingereiht - der Join-Pfad des Providers erledigt das.
                continue;
            }
            handled++;
            recoverSingle(entry);
        }
        save();

        plugin.getLogger().info(plugin.getConsoleMsg("guard-recovery-summary",
                "handled", String.valueOf(handled), "orphaned", String.valueOf(orphaned)));
        if (orphaned > 0) {
            plugin.getLogger().warning(plugin.getConsoleMsg("guard-recovery-orphans",
                    "count", String.valueOf(orphaned)));
        }
    }

    private void recoverSingle(GuardEntry entry) {
        InventoryBackupService service = plugin.getInventoryBackupService();
        UUID playerId = entry.playerId();
        service.resolve(playerId, entry.backupId()).thenAccept(ref -> {
            if (ref.isEmpty()) {
                entry.phase(GuardPhase.ORPHANED);
                plugin.getLogger().warning(plugin.getConsoleMsg("guard-backup-missing",
                        "player", playerId.toString(), "backup", String.valueOf(entry.backupId())));
                save();
                return;
            }
            restoreFor(playerId, ref.get(), GuardPhase.BACKED_UP);
        });
    }

    /**
     * Stellt wieder her und schliesst die Sitzung bei Erfolg.
     *
     * <p>Der gemeinsame Weg fuer Wiederanlauf, Join-Sicherheitsnetz und die Aufraeumpfade der
     * Module - deshalb hier und nicht in jedem Listener einzeln.</p>
     *
     * @param fallbackPhase Phase, auf die zurueckgefallen wird, wenn es schiefgeht
     */
    public void restoreFor(UUID playerId, BackupRef ref, GuardPhase fallbackPhase) {
        InventoryBackupService service = plugin.getInventoryBackupService();
        service.restore(playerId, ref, RestoreMode.all()).thenAccept(outcome -> {
            if (outcome == RestoreOutcome.QUEUED_FOR_JOIN) {
                phase(playerId, GuardPhase.QUEUED);
                return;
            }
            if (outcome.isSuccess()) {
                close(playerId);
                // Gleiche Regel wie InventorySessionManager.cleanupAfterRestore: das Inventar ist
                // zurueck, das temporaere Backup wird nicht mehr gebraucht. Nur auf dem
                // Erfolgspfad - bei einem Fehlschlag bleibt es als letzte Rettung liegen.
                if (plugin.getInventoryConfig().cleanupAfterMatch()) {
                    service.delete(ref);
                }
                return;
            }
            plugin.getLogger().warning(plugin.getConsoleMsg("guard-restore-failed",
                    "player", playerId.toString(), "reason", outcome.name()));
            releaseRestore(playerId, fallbackPhase);
        });
    }

    /**
     * Sicherheitsnetz beim Join: haengt fuer diesen Spieler noch eine Sitzung, deren
     * Match/Event laengst vorbei ist, wird jetzt nachgeholt.
     */
    public void handleJoin(Player player) {
        UUID id = player.getUniqueId();
        GuardEntry entry = sessions.get(id);
        if (entry == null) {
            return;
        }
        if (entry.phase() == GuardPhase.ACTIVE && isSessionStillRunning(entry)) {
            // Rejoin in ein laufendes Match/Event - die Module regeln das selbst.
            return;
        }
        if (!entry.hasBackup()) {
            entry.phase(GuardPhase.ORPHANED);
            save();
            return;
        }
        if (!tryBeginRestore(id)) {
            return;
        }
        plugin.getInventoryBackupService().resolve(id, entry.backupId()).thenAccept(ref -> {
            if (ref.isPresent()) {
                restoreFor(id, ref.get(), GuardPhase.BACKED_UP);
            } else {
                releaseRestore(id, GuardPhase.ORPHANED);
            }
        });
    }

    /** Ob das Match bzw. Event zu dieser Sitzung noch laeuft. */
    private boolean isSessionStillRunning(GuardEntry entry) {
        try {
            if (entry.context() == GuardContext.PVP_MATCH) {
                return plugin.getMatchManager() != null
                        && plugin.getMatchManager().getMatchIdByPlayer(entry.playerId()) != null;
            }
            if (entry.context() == GuardContext.EVENT) {
                return plugin.getEventManager() != null
                        && plugin.getEventManager().isPlayerInEvent(entry.playerId());
            }
        } catch (Exception e) {
            // Im Zweifel als beendet behandeln: lieber einmal zu frueh wiederherstellen als
            // ein Inventar dauerhaft haengen lassen.
            return false;
        }
        return false;
    }

    /**
     * Verwaiste Sitzung eines Spielers, der laengst weg ist.
     *
     * @return wie viele Eintraege aelter als {@code maxAgeMillis} sind
     */
    public int countStale(long maxAgeMillis) {
        long cutoff = System.currentTimeMillis() - maxAgeMillis;
        int count = 0;
        for (GuardEntry entry : sessions.values()) {
            if (entry.openedAt() < cutoff) {
                count++;
            }
        }
        return count;
    }

    /** Beim Herunterfahren: nichts wiederherstellen, nur sicherstellen, dass alles auf Platte liegt. */
    public void shutdown() {
        save();
        if (!sessions.isEmpty()) {
            plugin.getLogger().info(plugin.getConsoleMsg("guard-shutdown-open",
                    "count", String.valueOf(sessions.size())));
        }
    }

    /** Ob der Server gerade laeuft - fuer Aufrufer, die keinen Scheduler mehr nutzen duerfen. */
    public boolean serverRunning() {
        return plugin.isEnabled() && Bukkit.getServer() != null;
    }
}
