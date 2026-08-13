package de.zfzfg.core.inventory.guard;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Eine offene Inventar-Sitzung im Guard-Journal. */
public final class GuardEntry {

    private final UUID playerId;
    private final GuardContext context;
    private final String refId;
    private volatile String backupId;
    private volatile GuardPhase phase;
    private final String originWorld;
    private final long openedAt;
    private volatile boolean payoutDone;

    public GuardEntry(UUID playerId, GuardContext context, String refId, String backupId,
                      GuardPhase phase, String originWorld, long openedAt, boolean payoutDone) {
        this.playerId = playerId;
        this.context = context;
        this.refId = refId == null ? "" : refId;
        this.backupId = backupId;
        this.phase = phase == null ? GuardPhase.BACKED_UP : phase;
        this.originWorld = originWorld == null ? "" : originWorld;
        this.openedAt = openedAt;
        this.payoutDone = payoutDone;
    }

    public UUID playerId() { return playerId; }
    public GuardContext context() { return context; }

    /** Match- bzw. Event-ID, zu der diese Sitzung gehoert. */
    public String refId() { return refId; }

    /** ID des Backups, oder null wenn das Schreiben fehlgeschlagen ist. */
    public String backupId() { return backupId; }
    void backupId(String backupId) { this.backupId = backupId; }

    public GuardPhase phase() { return phase; }
    void phase(GuardPhase phase) { this.phase = phase; }

    public String originWorld() { return originWorld; }
    public long openedAt() { return openedAt; }

    /** Ob Gewinne bzw. Belohnungen fuer diese Sitzung bereits ausgeschuettet wurden. */
    public boolean payoutDone() { return payoutDone; }
    void payoutDone(boolean done) { this.payoutDone = done; }

    /** Ob ein Backup vorliegt, aus dem wiederhergestellt werden kann. */
    public boolean hasBackup() {
        return backupId != null && !backupId.isEmpty();
    }

    /** Fuer das Web-Panel und {@code /eventpvp rescue list}. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("player", playerId.toString());
        map.put("context", context.id());
        map.put("refId", refId);
        map.put("backupId", backupId == null ? "" : backupId);
        map.put("phase", phase.id());
        map.put("originWorld", originWorld);
        map.put("openedAt", openedAt);
        map.put("payoutDone", payoutDone);
        return map;
    }
}
