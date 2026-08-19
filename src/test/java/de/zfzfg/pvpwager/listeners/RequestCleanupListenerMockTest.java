package de.zfzfg.pvpwager.listeners;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.commands.PvPWagerGuiCommand;
import de.zfzfg.pvpwager.managers.CommandRequestManager;
import de.zfzfg.pvpwager.managers.RequestManager;
import de.zfzfg.test.MockBukkitTestBase;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.mockito.Mockito.*;

class RequestCleanupListenerMockTest extends MockBukkitTestBase {

    private EventPlugin mockPlugin;
    private RequestManager mockRequestManager;
    private CommandRequestManager mockCommandRequestManager;
    private PvPWagerGuiCommand mockPvpWagerGuiCommand;
    private RequestCleanupListener listener;

    @BeforeEach
    void setUp() {
        mockPlugin = mock(EventPlugin.class);
        mockRequestManager = mock(RequestManager.class);
        mockCommandRequestManager = mock(CommandRequestManager.class);
        mockPvpWagerGuiCommand = mock(PvPWagerGuiCommand.class);

        when(mockPlugin.getRequestManager()).thenReturn(mockRequestManager);
        when(mockPlugin.getCommandRequestManager()).thenReturn(mockCommandRequestManager);
        when(mockPlugin.getPvpWagerGuiCommand()).thenReturn(mockPvpWagerGuiCommand);

        listener = new RequestCleanupListener(mockPlugin);
    }

    @Test
    @DisplayName("All open requests and wager sessions are cleaned up when player quits")
    void testPlayerQuitCleansUpAllRequests() {
        PlayerMock player = createPlayer("LeavingPlayer");

        PlayerQuitEvent quitEvent = new PlayerQuitEvent(player, (net.kyori.adventure.text.Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED);
        listener.onPlayerQuit(quitEvent);

        verify(mockRequestManager, times(1)).removeRequestsForPlayer(player);
        verify(mockCommandRequestManager, times(1)).removeRequestsForPlayer(player);
        verify(mockPvpWagerGuiCommand, times(1)).removeRequestsForPlayer(player);
    }

    @Test
    @DisplayName("Handles null managers gracefully on player quit")
    void testHandlesNullManagersGracefully() {
        when(mockPlugin.getRequestManager()).thenReturn(null);
        when(mockPlugin.getCommandRequestManager()).thenReturn(null);
        when(mockPlugin.getPvpWagerGuiCommand()).thenReturn(null);

        PlayerMock player = createPlayer("LeavingPlayer2");
        PlayerQuitEvent quitEvent = new PlayerQuitEvent(player, (net.kyori.adventure.text.Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED);

        // Should not throw any exception
        listener.onPlayerQuit(quitEvent);
    }
}
