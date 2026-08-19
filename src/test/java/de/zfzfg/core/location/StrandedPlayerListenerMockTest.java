package de.zfzfg.core.location;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.manager.ConfigManager;
import de.zfzfg.eventplugin.manager.EventManager;
import de.zfzfg.pvpwager.managers.MatchManager;
import de.zfzfg.test.MockBukkitTestBase;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.Mockito.*;

class StrandedPlayerListenerMockTest extends MockBukkitTestBase {

    private EventPlugin mockPlugin;
    private PluginWorlds mockPluginWorlds;
    private EventManager mockEventManager;
    private MatchManager mockMatchManager;
    private SafeLocationResolver mockSafeLocations;
    private ReturnLocationStore mockReturnLocations;
    private ConfigManager mockConfigManager;
    private StrandedPlayerListener listener;

    @BeforeEach
    void setUp() {
        mockPlugin = mock(EventPlugin.class);
        mockPluginWorlds = mock(PluginWorlds.class);
        mockEventManager = mock(EventManager.class);
        mockMatchManager = mock(MatchManager.class);
        mockSafeLocations = mock(SafeLocationResolver.class);
        mockReturnLocations = mock(ReturnLocationStore.class);
        mockConfigManager = mock(ConfigManager.class);

        when(mockPlugin.isEnabled()).thenReturn(true);
        when(mockPlugin.getName()).thenReturn("EventPlugin");
        when(mockPlugin.getPluginWorlds()).thenReturn(mockPluginWorlds);
        when(mockPlugin.getEventManager()).thenReturn(mockEventManager);
        when(mockPlugin.getMatchManager()).thenReturn(mockMatchManager);
        when(mockPlugin.getSafeLocations()).thenReturn(mockSafeLocations);
        when(mockPlugin.getReturnLocations()).thenReturn(mockReturnLocations);
        when(mockPlugin.getConfigManager()).thenReturn(mockConfigManager);
        when(mockConfigManager.getMessage(anyString())).thenReturn("Rescued from stranded world.");
        when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("StrandedPlayerTest"));
        when(mockPlugin.getConsoleMsg(anyString(), any())).thenReturn("mock msg");

        listener = new StrandedPlayerListener(mockPlugin);
    }

    @Test
    @DisplayName("Stranded player in managed world with no active session is rescued after 20 ticks")
    void testStrandedPlayerRescuedAfterJoinDelay() {
        WorldMock eventWorld = createWorld("event_world");
        WorldMock spawnWorld = createWorld("spawn_world");
        PlayerMock player = createPlayer("StrandedPlayer");
        player.teleport(new Location(eventWorld, 0, 60, 0));

        when(mockPluginWorlds.isManaged("event_world")).thenReturn(true);
        when(mockEventManager.isPlayerInEvent(player.getUniqueId())).thenReturn(false);
        when(mockMatchManager.getMatchIdByPlayer(player.getUniqueId())).thenReturn(null);

        Location spawnLoc = new Location(spawnWorld, 0, 64, 0);
        when(mockSafeLocations.resolve(player)).thenReturn(spawnLoc);
        when(mockSafeLocations.teleportSafely(player, spawnLoc)).thenReturn(true);

        PlayerJoinEvent joinEvent = new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null);
        listener.onPlayerJoin(joinEvent);

        // Before 20 ticks, no rescue
        verify(mockSafeLocations, never()).resolve(any());

        // Execute scheduled tasks up to 20 ticks
        tick(20);

        verify(mockSafeLocations, times(1)).resolve(player);
        verify(mockSafeLocations, times(1)).teleportSafely(player, spawnLoc);
        verify(mockReturnLocations, times(1)).consume(player.getUniqueId());
    }

    @Test
    @DisplayName("Player in managed world with active event session is NOT rescued")
    void testActiveEventParticipantNotRescued() {
        WorldMock eventWorld = createWorld("event_world");
        PlayerMock player = createPlayer("EventPlayer");
        player.teleport(new Location(eventWorld, 0, 60, 0));

        when(mockPluginWorlds.isManaged("event_world")).thenReturn(true);
        when(mockEventManager.isPlayerInEvent(player.getUniqueId())).thenReturn(true);

        PlayerJoinEvent joinEvent = new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null);
        listener.onPlayerJoin(joinEvent);

        tick(20);

        verify(mockSafeLocations, never()).resolve(any());
        verify(mockReturnLocations, never()).consume(any());
    }

    @Test
    @DisplayName("Player in managed world with active match session is NOT rescued")
    void testActiveMatchParticipantNotRescued() {
        WorldMock arenaWorld = createWorld("arena_world");
        PlayerMock player = createPlayer("MatchPlayer");
        player.teleport(new Location(arenaWorld, 0, 60, 0));

        when(mockPluginWorlds.isManaged("arena_world")).thenReturn(true);
        when(mockEventManager.isPlayerInEvent(player.getUniqueId())).thenReturn(false);
        when(mockMatchManager.getMatchIdByPlayer(player.getUniqueId())).thenReturn(UUID.randomUUID());

        PlayerJoinEvent joinEvent = new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null);
        listener.onPlayerJoin(joinEvent);

        tick(20);

        verify(mockSafeLocations, never()).resolve(any());
        verify(mockReturnLocations, never()).consume(any());
    }

    @Test
    @DisplayName("Player in regular unmanaged world is NOT rescued")
    void testPlayerInNormalWorldNotRescued() {
        WorldMock survivalWorld = createWorld("world");
        PlayerMock player = createPlayer("NormalPlayer");
        player.teleport(new Location(survivalWorld, 100, 64, 100));

        when(mockPluginWorlds.isManaged("world")).thenReturn(false);

        PlayerJoinEvent joinEvent = new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null);
        listener.onPlayerJoin(joinEvent);

        tick(20);

        verify(mockSafeLocations, never()).resolve(any());
        verify(mockReturnLocations, never()).consume(any());
    }
}
