package de.zfzfg.core.world;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests fuer das Welt-Backup.
 *
 * <p>Hintergrund: das Backup vor dem Loeschen einer Welt entstand nicht, und der Fehler blieb
 * unbemerkt -- {@code backupWorld()} verschluckte jeden Fehlerfall (fehlender Weltordner,
 * Zip-Fehler, sogar jede einzelne unlesbare Datei) und der Aufrufer loeschte anschliessend
 * trotzdem. Diese Tests halten fest, dass ein Fehlschlag jetzt sichtbar wird.</p>
 */
class WorldBackupTest {

    private EventPlugin mockPlugin(File dataFolder) {
        EventPlugin plugin = Mockito.mock(EventPlugin.class);
        Mockito.when(plugin.getDataFolder()).thenReturn(dataFolder);
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getLogger("WorldBackupTest"));
        Mockito.when(plugin.getConsoleMsg(Mockito.anyString(), Mockito.<String>any()))
               .thenReturn("console message");
        return plugin;
    }

    private File makeWorld(Path container, String name) throws IOException {
        Path world = container.resolve(name);
        Files.createDirectories(world.resolve("region"));
        Files.writeString(world.resolve("level.dat"), "level");
        Files.writeString(world.resolve("region/r.0.0.mca"), "chunkdata");
        Files.writeString(world.resolve("session.lock"), "lock");
        return world.toFile();
    }

    @Test
    void writesAnArchiveContainingTheWorldFiles(@TempDir Path tmp) throws Exception {
        Path container = tmp.resolve("server");
        Files.createDirectories(container);
        makeWorld(container, "arena_1");
        File dataFolder = tmp.resolve("plugin").toFile();

        File archive;
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container.toFile());
            archive = new MultiverseHelper(mockPlugin(dataFolder)).createBackup("arena_1");
        }

        assertTrue(archive.isFile(), "Archiv muss existieren");
        assertTrue(archive.length() > 0, "Archiv darf nicht leer sein");
        assertEquals(new File(dataFolder, "backups"), archive.getParentFile(),
                "Backup gehoert nach plugins/<plugin>/backups/");
        assertTrue(archive.getName().startsWith("arena_1_") && archive.getName().endsWith(".zip"));

        Set<String> entries = new HashSet<>();
        try (ZipFile zip = new ZipFile(archive)) {
            for (ZipEntry e : zip.stream().toList()) {
                entries.add(e.getName());
            }
        }
        assertTrue(entries.contains("level.dat"), "level.dat fehlt: " + entries);
        assertTrue(entries.contains("region/r.0.0.mca"), "Regionsdatei fehlt: " + entries);
        assertFalse(entries.contains("session.lock"),
                "session.lock gehoert nicht ins Backup - sie ist auf Windows haeufig gesperrt");
    }

    /** Der Fall, der das Backup verschwinden liess: kein Weltordner, aber trotzdem geloescht. */
    @Test
    void failsLoudlyWhenTheWorldFolderIsMissing(@TempDir Path tmp) throws Exception {
        Path container = tmp.resolve("server");
        Files.createDirectories(container);
        File dataFolder = tmp.resolve("plugin").toFile();

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container.toFile());
            MultiverseHelper helper = new MultiverseHelper(mockPlugin(dataFolder));
            IOException error = assertThrows(IOException.class, () -> helper.createBackup("does_not_exist"));
            assertTrue(error.getMessage().contains("does_not_exist"), error.getMessage());
        }

        File backups = new File(dataFolder, "backups");
        File[] written = backups.listFiles();
        assertTrue(written == null || written.length == 0,
                "Bei einem Fehlschlag darf keine Datei zurueckbleiben, die wie ein Backup aussieht");
    }

    /** Ein Weltordner ohne lesbare Dateien darf kein leeres Archiv als "Backup" hinterlassen. */
    @Test
    void doesNotLeaveAnEmptyArchiveBehind(@TempDir Path tmp) throws Exception {
        Path container = tmp.resolve("server");
        Files.createDirectories(container.resolve("empty_world"));
        File dataFolder = tmp.resolve("plugin").toFile();

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container.toFile());
            MultiverseHelper helper = new MultiverseHelper(mockPlugin(dataFolder));
            assertThrows(IOException.class, () -> helper.createBackup("empty_world"));
        }

        File[] written = new File(dataFolder, "backups").listFiles();
        assertTrue(written == null || written.length == 0,
                "Ein leeres Archiv haette wie ein gueltiges Backup ausgesehen");
    }

    /**
     * Der Fall vom Server des Nutzers: die Welt liegt als Dimension in der Hauptwelt
     * ({@code world/dimensions/minecraft/newworld}), hat also weder einen Ordner direkt im
     * Container noch ein level.dat. Mit explizit aufgeloestem Ordner muss das Backup gelingen.
     */
    @Test
    void backsUpADimensionWorldViaResolvedFolder(@TempDir Path tmp) throws Exception {
        Path container = tmp.resolve("server");
        Path dimension = container.resolve("world/dimensions/minecraft/newworld");
        Files.createDirectories(dimension.resolve("region"));
        Files.writeString(container.resolve("world/level.dat"), "main level");
        Files.writeString(dimension.resolve("region/r.0.0.mca"), "chunkdata");
        File dataFolder = tmp.resolve("plugin").toFile();

        File archive;
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container.toFile());
            archive = new MultiverseHelper(mockPlugin(dataFolder))
                    .createBackup("newworld", dimension.toFile());
        }

        assertTrue(archive.isFile() && archive.length() > 0, "Archiv muss geschrieben sein");
        try (ZipFile zip = new ZipFile(archive)) {
            assertTrue(zip.stream().anyMatch(e -> e.getName().equals("region/r.0.0.mca")),
                    "Regionsdaten der Dimension fehlen im Archiv");
        }
    }

    /** Die alte, fehlertolerante Variante bleibt fuer die Event-Regeneration erhalten. */
    @Test
    void legacyBackupWorldStaysSilentOnFailure(@TempDir Path tmp) {
        Path container = tmp.resolve("server");
        File dataFolder = tmp.resolve("plugin").toFile();

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container.toFile());
            MultiverseHelper helper = new MultiverseHelper(mockPlugin(dataFolder));
            assertDoesNotThrow(() -> helper.backupWorld("does_not_exist"));
        }
    }
}
