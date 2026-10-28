package de.zfzfg.core.world.mv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
class WorldOperationSafetyTest {
    @TempDir Path directory;
    @Test void overlappingPathsStayReservedUntilCompletion() {
        var guard = new WorldOperationGuard(); Path root = directory.resolve("world");
        assertTrue(guard.acquire("restore", root));
        assertFalse(guard.acquire("delete", root));
        assertFalse(guard.acquire("dimension", root.resolve("dimensions/nether")));
        assertFalse(guard.acquire("parent", directory));
        assertTrue(guard.acquire("other", directory.resolve("other")));
        guard.release("restore"); assertTrue(guard.acquire("delete", root));
    }
    Path archive() throws Exception {
        Path zip = directory.resolve("test.zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("level.dat")); out.write(new byte[64]); out.closeEntry();
            out.putNextEntry(new ZipEntry("region/a")); out.write(new byte[64]); out.closeEntry();
        } return zip;
    }
    @Test void expandedByteLimitIsEnforced() throws Exception {
        Path zip = archive();
        assertThrows(IOException.class, () -> MvWorldService.extractBackup(zip.toFile(), directory.resolve("target").toFile(), 100, 10));
    }
    @Test void entryLimitIsEnforced() throws Exception {
        Path zip = archive();
        assertThrows(IOException.class, () -> MvWorldService.extractBackup(zip.toFile(), directory.resolve("target").toFile(), 1000, 1));
    }
    @Test void existingFilesAreNeverOverwritten() throws Exception {
        Path zip = archive(); Path target = Files.createDirectory(directory.resolve("target"));
        Files.writeString(target.resolve("level.dat"), "keep");
        assertThrows(IOException.class, () -> MvWorldService.extractBackup(zip.toFile(), target.toFile()));
        assertEquals("keep", Files.readString(target.resolve("level.dat")));
    }
    @Test void ordinaryTreeDeletionIsScoped() throws Exception {
        Path target = Files.createDirectories(directory.resolve("target/nested")); Files.writeString(target.resolve("file"), "delete");
        Files.writeString(directory.resolve("keep"), "keep"); SafeFileTree.delete(target.getParent());
        assertFalse(Files.exists(target)); assertTrue(Files.exists(directory.resolve("keep")));
    }
}
