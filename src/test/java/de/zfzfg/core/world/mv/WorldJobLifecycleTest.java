package de.zfzfg.core.world.mv;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.core.tasks.TaskManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class WorldJobLifecycleTest {
    @TempDir Path directory;
    @Test void competingRestoresCannotShareOrRemoveTarget() throws Exception {
        Path container = Files.createDirectory(directory.resolve("worlds"));
        Path data = Files.createDirectory(directory.resolve("plugin"));
        Path backups = Files.createDirectory(data.resolve("backups"));
        try (var zip = new ZipOutputStream(Files.newOutputStream(backups.resolve("world.zip")))) {
            zip.putNextEntry(new ZipEntry("level.dat")); zip.write("original".getBytes()); zip.closeEntry();
        }
        EventPlugin plugin = mock(EventPlugin.class); TaskManager tasks = mock(TaskManager.class);
        when(plugin.getTaskManager()).thenReturn(tasks); when(plugin.getDataFolder()).thenReturn(data.toFile());
        when(plugin.getConfig()).thenReturn(new YamlConfiguration()); when(plugin.getLogger()).thenReturn(Logger.getLogger("world-job-test"));
        Queue<Runnable> main = new ConcurrentLinkedQueue<>();
        when(tasks.run(any())).thenAnswer(inv -> { main.add(inv.getArgument(0)); return null; });
        MvWorldBackend backend = mock(MvWorldBackend.class);
        when(backend.importWorld("target")).thenReturn(MvResult.ok());
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container.toFile());
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
            var service = new MvWorldService(plugin); service.useBackendForTests(backend);
            try {
                var first = service.restoreBackup("world.zip", "target");
                var second = service.restoreBackup("world.zip", "target");
                main.remove().run(); main.remove().run();
                assertEquals("FAILED", second.status);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (first.status.equals("RUNNING") && System.nanoTime() < deadline) {
                    Runnable action = main.poll(); if (action != null) action.run(); else Thread.sleep(5);
                }
                assertEquals("SUCCESS", first.status);
                assertEquals("original", Files.readString(container.resolve("target/level.dat")));
                verify(backend, times(1)).importWorld("target");
            } finally { service.shutdown(); }
        }
    }
    @Test void shutdownRejectsScheduledWorkBeforeBackendMutation() throws Exception {
        EventPlugin plugin = mock(EventPlugin.class); TaskManager tasks = mock(TaskManager.class);
        when(plugin.getTaskManager()).thenReturn(tasks); when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("world-job-test"));
        Queue<Runnable> main = new ArrayDeque<>(); when(tasks.run(any())).thenAnswer(inv -> { main.add(inv.getArgument(0)); return null; });
        var backend = mock(MvWorldBackend.class); var service = new MvWorldService(plugin); service.useBackendForTests(backend);
        var job = service.loadWorld("target"); service.shutdown(); main.remove().run();
        assertEquals("FAILED", job.status); verifyNoInteractions(backend);
        assertTrue(Files.isRegularFile(directory.resolve("interrupted-world-jobs.yml")));
    }
}
