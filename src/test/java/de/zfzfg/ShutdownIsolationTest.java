package de.zfzfg;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.manager.EventManager;
import de.zfzfg.core.inventory.InventorySessionManager;
import org.junit.jupiter.api.Test;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ShutdownIsolationTest {
    private static void field(Object owner, String name, Object value) throws Exception {
        var field = EventPlugin.class.getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
    @Test void eventFailureDoesNotPreventInventoryJournalShutdown() throws Exception {
        var plugin = mock(EventPlugin.class, CALLS_REAL_METHODS);
        doReturn(Logger.getLogger("shutdown-test")).when(plugin).getLogger();
        var events = mock(EventManager.class); doThrow(new IllegalStateException("test event failure")).when(events).stopAllEvents();
        var inventories = mock(InventorySessionManager.class);
        field(plugin, "eventManager", events); field(plugin, "inventorySessions", inventories); field(plugin, "externalDisplayRefreshTaskId", -1);
        plugin.onDisable();
        assertTrue(plugin.isStopping()); verify(inventories).shutdown();
    }
}
