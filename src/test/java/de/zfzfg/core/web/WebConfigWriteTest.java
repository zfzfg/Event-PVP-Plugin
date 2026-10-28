package de.zfzfg.core.web;
import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.Server;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class WebConfigWriteTest {
    @TempDir Path directory;
    EventPlugin plugin; Server server; WebConfigManager config; WebApiHandler api;
    @BeforeEach void setup() throws Exception {
        plugin = mock(EventPlugin.class); server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server); when(server.isPrimaryThread()).thenReturn(true);
        when(plugin.getDataFolder()).thenReturn(directory.toFile()); when(plugin.getLogger()).thenReturn(Logger.getLogger("web-save-test"));
        config = mock(WebConfigManager.class, CALLS_REAL_METHODS);
        var field = WebConfigManager.class.getDeclaredField("plugin"); field.setAccessible(true); field.set(config, plugin);
        api = new WebApiHandler(plugin, config);
    }
    @Test void ioFailureIsNotReportedAsSaved() throws Exception {
        Files.createDirectory(directory.resolve("worlds.yml")); Files.writeString(directory.resolve("worlds.yml/sentinel"), "keep");
        var response = api.saveWorlds(Map.of("data", Map.of("worlds", Map.of())));
        assertEquals(false, response.get("success")); assertNotEquals(true, response.get("saved"));
    }
    @Test void staleRevisionIsAConflictAndDoesNotOverwrite() {
        api.saveConfig(Map.of("data", Map.of("settings", Map.of("language", "en"))));
        String revision = config.revision("config.yml");
        de.zfzfg.core.config.ConfigWriteCoordinator.patch(directory.resolve("config.yml"), c -> c.set("settings.language", "de"));
        var response = api.saveConfig(Map.of("revision", revision, "data", Map.of("settings", Map.of("language", "fr"))));
        assertEquals(409, response.get("httpStatus")); assertEquals(false, response.get("success"));
        assertEquals("de", ((Map<?, ?>)config.getConfigAsMap().get("settings")).get("language"));
    }
    @Test void malformedShapeCannotReplaceValidFile() {
        assertEquals(false, api.saveWorlds(Map.of("data", Map.of("worlds", "invalid"))).get("success"));
        assertFalse(Files.exists(directory.resolve("worlds.yml")));
    }
    @Test void bukkitMutationRunsInsideSchedulerBoundary() throws Exception {
        when(server.isPrimaryThread()).thenReturn(false); var scheduler = mock(BukkitScheduler.class); when(server.getScheduler()).thenReturn(scheduler);
        AtomicReference<String> mutationThread = new AtomicReference<>();
        doAnswer(inv -> { mutationThread.set(Thread.currentThread().getName()); return null; }).when(plugin).reloadConfig();
        try (var main = Executors.newSingleThreadExecutor(r -> new Thread(r, "test-main"))) {
            when(scheduler.callSyncMethod(eq(plugin), any())).thenAnswer(inv -> main.submit((Callable<Object>)inv.getArgument(1)));
            var result = api.saveConfig(Map.of("data", Map.of("settings", Map.of())));
            assertEquals(true, result.get("success")); assertEquals("test-main", mutationThread.get());
        }
    }
}
