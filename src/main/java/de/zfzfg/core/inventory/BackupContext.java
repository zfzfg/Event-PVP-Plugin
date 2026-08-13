package de.zfzfg.core.inventory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typ und Metadaten eines anzulegenden Backups.
 *
 * <p>Der Typ wird auf {@code [a-z0-9-]} normalisiert und auf 32 Zeichen gekuerzt - identisch
 * zu {@code BackupType.normalize} der InventoryBackup-API, damit ein spaeteres Filtern nach
 * Typ dieselbe Zeichenkette trifft, egal welcher Adapter geschrieben hat.</p>
 */
public final class BackupContext {

    /** Zustand eines Spielers unmittelbar vor dem Arena-Teleport. */
    public static final String TYPE_PVP_PRE_MATCH = "pvp-pre-match";
    /** Zustand nach einem Restore - Grundlage fuer {@code /inventoryrestore undo}. */
    public static final String TYPE_PVP_POST_MATCH = "pvp-post-match";
    /** Zustand eines Spielers unmittelbar vor dem Lobby-Teleport. */
    public static final String TYPE_EVENT_PRE_JOIN = "event-pre-join";
    /** Zustand nach einem Event-Restore. */
    public static final String TYPE_EVENT_POST = "event-post";
    /** Beim Wiederanlauf uebernommene Altlast aus einer abgestuerzten Sitzung. */
    public static final String TYPE_GUARD_RECOVERY = "guard-recovery";
    /** Aus dem alten InventorySnapshotStorage uebernommener Eintrag. */
    public static final String TYPE_LEGACY_IMPORT = "legacy-import";

    private final String type;
    private final Map<String, String> metadata;

    private BackupContext(String type, Map<String, String> metadata) {
        this.type = type;
        this.metadata = metadata;
    }

    public static BackupContext of(String type) {
        return new BackupContext(normalize(type), Collections.emptyMap());
    }

    public static Builder builder(String type) {
        return new Builder(type);
    }

    public String type() { return type; }

    public Map<String, String> metadata() { return metadata; }

    /**
     * Normalisiert einen Typ auf {@code [a-z0-9-]}, maximal 32 Zeichen.
     * Leere Eingaben werden zu {@code manual}, damit nie ein Backup ohne Typ entsteht.
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "manual";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (char c : raw.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
            } else if (out.length() > 0 && out.charAt(out.length() - 1) != '-') {
                out.append('-');
            }
            if (out.length() >= 32) {
                break;
            }
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '-') {
            out.setLength(out.length() - 1);
        }
        return out.length() == 0 ? "manual" : out.toString();
    }

    public static final class Builder {
        private final String type;
        private final Map<String, String> metadata = new LinkedHashMap<>();

        private Builder(String type) {
            this.type = normalize(type);
        }

        public Builder meta(String key, String value) {
            if (key != null && !key.isEmpty() && value != null) {
                metadata.put(key, value);
            }
            return this;
        }

        public BackupContext build() {
            return new BackupContext(type, Collections.unmodifiableMap(new LinkedHashMap<>(metadata)));
        }
    }
}
