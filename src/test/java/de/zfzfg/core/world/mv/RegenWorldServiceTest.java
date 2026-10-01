package de.zfzfg.core.world.mv;

import de.zfzfg.core.tasks.TaskManager;
import de.zfzfg.eventplugin.EventPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Die Regeneration darf nicht mehr {@code mv regen} und {@code mv confirm} an die Konsole
 * schicken, sobald ein Backend da ist. Das Double hier zaehlt Befehle und wuerde den Test
 * rot faerben, wenn der Service doch einen Konsolenweg ginge.
 */
class RegenWorldServiceTest {

    @Test
    void regenerateWorldNowDelegatesToTheBackend() {
        EventPlugin plugin = mock(EventPlugin.class);
        when(plugin.getTaskManager()).thenReturn(mock(TaskManager.class));
        when(plugin.getLogger()).thenReturn(Logger.getLogger("RegenWorldServiceTest"));

        MvWorldService service = new MvWorldService(plugin);
        RecordingBackend backend = new RecordingBackend();
        service.useBackendForTests(backend);

        MvResult result = service.regenerateWorldNow("arena");

        assertTrue(result.isSuccess());
        assertEquals("arena", backend.regenerated);
        assertEquals(0, backend.commands, "weder mv regen noch mv confirm");
    }

    @Test
    void rejectsABlankNameBeforeTouchingTheBackend() {
        EventPlugin plugin = mock(EventPlugin.class);
        when(plugin.getTaskManager()).thenReturn(mock(TaskManager.class));
        when(plugin.getLogger()).thenReturn(Logger.getLogger("RegenWorldServiceTest"));

        MvWorldService service = new MvWorldService(plugin);
        RecordingBackend backend = new RecordingBackend();
        service.useBackendForTests(backend);

        assertFalse(service.regenerateWorldNow("  ").isSuccess());
        assertNull(backend.regenerated);
    }

    private static final class RecordingBackend implements MvWorldBackend {
        private String regenerated;
        private int commands;

        @Override public String getBackendId() { return "TEST"; }
        @Override public boolean isAvailable() { return true; }
        @Override public boolean supportsAdvancedCreateOptions() { return false; }
        @Override public List<MvWorldInfo> listWorlds() { return List.of(); }
        @Override public MvResult create(MvCreateSpec spec) { return MvResult.ok(); }
        @Override public MvResult load(String worldName) { return MvResult.ok(); }
        @Override public MvResult unload(String worldName) { return MvResult.ok(); }
        @Override public MvResult delete(String worldName) { return MvResult.ok(); }
        @Override public MvResult importWorld(String worldName) { return MvResult.ok(); }
        @Override public java.io.File resolveWorldFolder(String worldName) { return null; }

        @Override
        public MvResult regen(String worldName) {
            if (worldName.contains("mv regen") || worldName.contains("mv confirm")) {
                commands++;
            }
            regenerated = worldName;
            return MvResult.ok();
        }
    }
}
