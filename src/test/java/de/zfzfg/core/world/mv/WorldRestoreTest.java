package de.zfzfg.core.world.mv;

import de.zfzfg.core.tasks.TaskManager;
import de.zfzfg.eventplugin.EventPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests fuer die Backup-Wiederherstellung.
 *
 * <p>Der Entpack-Pfad verarbeitet Zip-Dateien, deren Namen aus dem Web kommen, und schreibt
 * ins Server-Dateisystem -- Zip-Slip und Pfad-Tricks im Dateinamen sind hier die relevanten
 * Angriffe, deshalb decken die Tests genau diese Faelle ab.</p>
 */
class WorldRestoreTest {

    // ============ extractBackup ============

    private File zip(Path dir, String name, Map<String, String> entries) throws IOException {
        File file = dir.resolve(name).toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new java.io.FileOutputStream(file))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return file;
    }

    @Test
    void extractsAValidWorldArchive(@TempDir Path tmp) throws Exception {
        File archive = zip(tmp, "arena_20260809_141200.zip", Map.of(
                "level.dat", "level",
                "region/r.0.0.mca", "chunks",
                "session.lock", "lock"));
        File target = tmp.resolve("restored").toFile();

        boolean hasLevelDat = MvWorldService.extractBackup(archive, target);

        assertTrue(hasLevelDat, "level.dat im Wurzelverzeichnis muss erkannt werden");
        assertTrue(new File(target, "level.dat").isFile());
        assertTrue(new File(target, "region/r.0.0.mca").isFile());
        assertFalse(new File(target, "session.lock").exists(),
                "session.lock darf nicht mit wiederhergestellt werden");
    }

    @Test
    void reportsMissingLevelDatForDimensionBackups(@TempDir Path tmp) throws Exception {
        File archive = zip(tmp, "dim_20260809_141200.zip", Map.of("region/r.0.0.mca", "chunks"));
        File target = tmp.resolve("restored").toFile();

        assertFalse(MvWorldService.extractBackup(archive, target),
                "Dimension-Backup ohne level.dat muss als solches gemeldet werden");
        assertTrue(new File(target, "region/r.0.0.mca").isFile(), "Daten trotzdem entpackt");
    }

    /** Der Angriff, gegen den der Schutz existiert: ein Eintrag will aus dem Zielordner ausbrechen. */
    @Test
    void blocksZipSlipEntries(@TempDir Path tmp) throws Exception {
        File archive = zip(tmp, "evil_20260809_141200.zip", Map.of(
                "level.dat", "level",
                "../evil.txt", "escaped"));
        File target = tmp.resolve("nested/restored").toFile();

        IOException error = assertThrows(IOException.class,
                () -> MvWorldService.extractBackup(archive, target));
        assertTrue(error.getMessage().contains("zip-slip"), error.getMessage());
        assertFalse(tmp.resolve("nested/evil.txt").toFile().exists(),
                "die ausgebrochene Datei darf nicht geschrieben worden sein");
    }

    @Test
    void rejectsEmptyArchives(@TempDir Path tmp) throws Exception {
        File archive = zip(tmp, "empty_20260809_141200.zip", Map.of());
        assertThrows(IOException.class,
                () -> MvWorldService.extractBackup(archive, tmp.resolve("out").toFile()));
    }

    // ============ Dateinamen-Validierung & Auflistung ============

    private MvWorldService serviceWithBackups(Path dataFolder) {
        EventPlugin plugin = Mockito.mock(EventPlugin.class);
        TaskManager taskManager = Mockito.mock(TaskManager.class);
        Mockito.when(plugin.getTaskManager()).thenReturn(taskManager);
        Mockito.when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getLogger("WorldRestoreTest"));
        return new MvWorldService(plugin);
    }

    @Test
    void rejectsPathTricksInBackupFileNames(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("backups"));
        MvWorldService service = serviceWithBackups(tmp);

        assertThrows(MvInputException.class, () -> service.requireValidBackupFile("../secrets.zip"));
        assertThrows(MvInputException.class, () -> service.requireValidBackupFile("a/b.zip"));
        assertThrows(MvInputException.class, () -> service.requireValidBackupFile("a\\b.zip"));
        assertThrows(MvInputException.class, () -> service.requireValidBackupFile("config.yml"));
        assertThrows(MvInputException.class, () -> service.requireValidBackupFile(""));
        assertThrows(MvInputException.class, () -> service.requireValidBackupFile("missing_20260809_141200.zip"),
                "auch ein formal gueltiger, aber nicht existierender Name wird abgelehnt");
    }

    @Test
    void acceptsARealBackupFile(@TempDir Path tmp) throws Exception {
        Path backups = Files.createDirectories(tmp.resolve("backups"));
        Files.writeString(backups.resolve("arena_1_20260809_141200.zip"), "zipdata");
        MvWorldService service = serviceWithBackups(tmp);

        File file = service.requireValidBackupFile("arena_1_20260809_141200.zip");
        assertTrue(file.isFile());
    }

    @Test
    void listsBackupsWithParsedMetadata(@TempDir Path tmp) throws Exception {
        Path backups = Files.createDirectories(tmp.resolve("backups"));
        Files.writeString(backups.resolve("arena_1_20260809_141200.zip"), "zipdata");
        Files.writeString(backups.resolve("strange.zip"), "unparseable");
        Files.writeString(backups.resolve("notes.txt"), "ignored");
        MvWorldService service = serviceWithBackups(tmp);

        List<Map<String, Object>> list = service.listBackups();

        assertEquals(2, list.size(), "nur Zips werden gelistet");
        Map<String, Object> parsed = list.stream()
                .filter(b -> "arena_1_20260809_141200.zip".equals(b.get("file"))).findFirst().orElseThrow();
        assertEquals("arena_1", parsed.get("worldName"), "Weltname trotz Unterstrich korrekt geparst");
        assertEquals("20260809_141200", parsed.get("timestamp"));
        Map<String, Object> unparsed = list.stream()
                .filter(b -> "strange.zip".equals(b.get("file"))).findFirst().orElseThrow();
        assertNull(unparsed.get("worldName"), "unparsebares Zip ohne geratene Metadaten");
    }

    @Test
    void deletesABackupFile(@TempDir Path tmp) throws Exception {
        Path backups = Files.createDirectories(tmp.resolve("backups"));
        Path zipFile = backups.resolve("arena_1_20260809_141200.zip");
        Files.writeString(zipFile, "zipdata");
        MvWorldService service = serviceWithBackups(tmp);

        assertTrue(service.deleteBackup("arena_1_20260809_141200.zip").isSuccess());
        assertFalse(Files.exists(zipFile));
    }
}
