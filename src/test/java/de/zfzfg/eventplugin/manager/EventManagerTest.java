package de.zfzfg.eventplugin.manager;

import de.zfzfg.core.location.ReturnLocationStore;
import de.zfzfg.core.location.ReturnReason;
import de.zfzfg.core.location.StoredReturn;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.model.EventConfig;
import de.zfzfg.eventplugin.session.EventSession;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EventManagerTest {

    private EventPlugin plugin;
    private ConfigManager configManager;
    private ReturnLocationStore returnLocationStore;
    private EventManager eventManager;

    @BeforeEach
    void setUp() {
        plugin = mock(EventPlugin.class);
        configManager = mock(ConfigManager.class);
        returnLocationStore = mock(ReturnLocationStore.class);

        when(plugin.getConfigManager()).thenReturn(configManager);
        when(plugin.getReturnLocations()).thenReturn(returnLocationStore);

        eventManager = new EventManager(plugin);
    }

    @Test
    @DisplayName("player indexing and querying")
    void testPlayerIndexing() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        eventManager.indexPlayer("event_1", p1);
        eventManager.indexPlayer("event_2", p2);

        // Before creating sessions, activeSessions does not contain the event
        assertThat(eventManager.isPlayerInEvent(p1)).isFalse();

        eventManager.unindexPlayer(p1);
        assertThat(eventManager.isPlayerInEvent(p1)).isFalse();
    }

    @Test
    @DisplayName("savePlayerLocation, getSavedLocation, and clearSavedLocation")
    void testPlayerSavedLocation() {
        UUID playerId = UUID.randomUUID();
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");

        Location loc = new Location(world, 100, 64, 200);

        eventManager.savePlayerLocation(playerId, loc);

        Location saved = eventManager.getSavedLocation(playerId);
        assertThat(saved).isNotNull();
        assertThat(saved.getX()).isEqualTo(100.0);
        assertThat(saved.getY()).isEqualTo(64.0);
        assertThat(saved.getZ()).isEqualTo(200.0);

        verify(returnLocationStore).remember(eq(playerId), eq(loc), eq(ReturnReason.EVENT));

        eventManager.clearSavedLocation(playerId);
        verify(returnLocationStore).forget(playerId);
    }

    @Test
    @DisplayName("getSavedLocation falls back to ReturnLocationStore when in-memory cache is empty")
    void testGetSavedLocationFallback() {
        UUID playerId = UUID.randomUUID();
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");

        StoredReturn stored = new StoredReturn(playerId, "world", 50.0, 70.0, 50.0, 0f, 0f, ReturnReason.EVENT, System.currentTimeMillis());
        when(returnLocationStore.peek(playerId)).thenReturn(stored);

        try (org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getWorld("world")).thenReturn(world);

            Location result = eventManager.getSavedLocation(playerId);
            assertThat(result).isNotNull();
            assertThat(result.getX()).isEqualTo(50.0);
            assertThat(result.getY()).isEqualTo(70.0);
        }
    }

    @Test
    @DisplayName("isEventActive returns false for unknown events")
    void testIsEventActive() {
        assertThat(eventManager.isEventActive("unknown_event")).isFalse();
        assertThat(eventManager.getActiveSessions()).isEmpty();
    }
}
