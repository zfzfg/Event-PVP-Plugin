package de.zfzfg.core.world.mv;

import java.util.List;

/**
 * Abstraktion ueber die verfuegbare Multiverse-Generation.
 *
 * <p>Es gibt zwei Implementierungen: {@link Mv5WorldBackend} spricht die typisierte
 * Multiverse-Core-5-API, {@link LegacyCommandWorldBackend} setzt {@code mv ...}-Konsolenbefehle
 * ab und traegt damit MV4-Server und Server ganz ohne Multiverse.
 * {@link MvWorldService} waehlt beim Start aus.</p>
 *
 * <p>Alle Methoden ausser {@link #isAvailable()} muessen auf dem Server-Main-Thread laufen.</p>
 */
public interface MvWorldBackend {

    /** Kennung fuer das Panel: {@code MV5}, {@code LEGACY} oder {@code NONE}. */
    String getBackendId();

    /** Ob ueberhaupt eine Multiverse-Installation ansprechbar ist. */
    boolean isAvailable();

    /** Ob dieses Backend Biome- und Generator-Settings unterstuetzt (nur MV5). */
    boolean supportsAdvancedCreateOptions();

    /** Alle dem Server bekannten Welten -- geladen, entladen und nur auf der Platte liegend. */
    List<MvWorldInfo> listWorlds();

    MvResult create(MvCreateSpec spec);

    MvResult load(String worldName);

    MvResult unload(String worldName);

    MvResult delete(String worldName);

    /** Registriert einen bereits auf der Platte liegenden Weltordner bei Multiverse und laedt ihn. */
    MvResult importWorld(String worldName);

    /**
     * Fragt Multiverse nach dem tatsaechlichen Weltordner.
     *
     * <p>Auf modernen Servern liegen Welten oft als Dimension <em>innerhalb</em> der Hauptwelt
     * ({@code world/dimensions/minecraft/<name>}) statt unter {@code container/<name>} --
     * Multiverse-Core 5.7+ kennt diese Aufloesung selbst. {@code null}, wenn das Backend es
     * nicht weiss (Legacy, aeltere MV5); dann greifen die Fallback-Ketten in
     * {@link MvWorldService#resolveWorldFolder(String)}.</p>
     */
    java.io.File resolveWorldFolder(String worldName);
}
