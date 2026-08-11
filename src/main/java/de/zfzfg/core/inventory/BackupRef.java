package de.zfzfg.core.inventory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Provider-neutrale Referenz auf ein gespeichertes Inventar-Backup.
 *
 * <p>Bewusst ohne Items: das Laden kostet einen Dateizugriff und passiert nur dann, wenn
 * tatsaechlich wiederhergestellt wird. {@link #backupId()} ist stabil und darf persistiert
 * werden - genau darauf baut das {@link de.zfzfg.core.inventory.guard.InventoryGuard
 * Guard-Journal} auf, damit eine offene Sitzung einen Serverneustart ueberlebt.</p>
 */
public final class BackupRef {

    private final UUID ownerId;
    private final String backupId;
    private final String type;
    private final long createdAt;
    private final Map<String, String> metadata;

    public BackupRef(UUID ownerId, String backupId, String type, long createdAt,
                     Map<String, String> metadata) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.backupId = Objects.requireNonNull(backupId, "backupId");
        this.type = type == null ? "" : type;
        this.createdAt = createdAt;
        this.metadata = metadata == null || metadata.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public UUID ownerId() { return ownerId; }

    /** Dateiname bzw. stabile ID des Backups - persistierbar. */
    public String backupId() { return backupId; }

    public String type() { return type; }

    /** Erstellzeitpunkt in Millisekunden seit Epoch. */
    public long createdAt() { return createdAt; }

    public Map<String, String> metadata() { return metadata; }

    public String metadata(String key) { return metadata.get(key); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BackupRef)) return false;
        BackupRef other = (BackupRef) o;
        return ownerId.equals(other.ownerId) && backupId.equals(other.backupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerId, backupId);
    }

    @Override
    public String toString() {
        return "BackupRef{" + ownerId + "/" + backupId + ", type=" + type + "}";
    }
}
