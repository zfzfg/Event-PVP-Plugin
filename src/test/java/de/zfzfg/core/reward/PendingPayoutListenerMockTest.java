package de.zfzfg.core.reward;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.manager.ConfigManager;
import de.zfzfg.test.MockBukkitTestBase;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.mockito.Mockito.*;

class PendingPayoutListenerMockTest extends MockBukkitTestBase {

    private EventPlugin mockPlugin;
    private PendingPayoutStore mockStore;
    private ConfigManager mockConfigManager;
    private PendingPayoutListener listener;

    @BeforeEach
    void setUp() {
        mockPlugin = mock(EventPlugin.class);
        mockStore = mock(PendingPayoutStore.class);
        mockConfigManager = mock(ConfigManager.class);

        when(mockPlugin.isEnabled()).thenReturn(true);
        when(mockPlugin.getName()).thenReturn("EventPlugin");
        when(mockPlugin.getPendingPayouts()).thenReturn(mockStore);
        when(mockPlugin.getConfigManager()).thenReturn(mockConfigManager);
        when(mockConfigManager.getMessage("rewards.delivered-on-join")).thenReturn("&aYour pending rewards have been delivered!");

        listener = new PendingPayoutListener(mockPlugin);
    }

    @Test
    @DisplayName("Pending rewards are delivered 30 ticks after player joins")
    void testPendingPayoutDeliveredAfterDelay() {
        PlayerMock player = createPlayer("WinnerPlayer");

        when(mockStore.hasPending(player.getUniqueId())).thenReturn(true);
        when(mockStore.deliverAll(player)).thenReturn(1);

        PlayerJoinEvent joinEvent = new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null);
        listener.onPlayerJoin(joinEvent);

        // Before 30 ticks, deliverAll should not have been called yet
        verify(mockStore, never()).deliverAll(player);

        // Execute scheduled tasks up to 30 ticks
        tick(30);

        verify(mockStore, times(1)).deliverAll(player);
    }

    @Test
    @DisplayName("Player without pending rewards triggers no scheduler task")
    void testNoPendingPayoutIgnored() {
        PlayerMock player = createPlayer("NormalPlayer");

        when(mockStore.hasPending(player.getUniqueId())).thenReturn(false);

        PlayerJoinEvent joinEvent = new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null);
        listener.onPlayerJoin(joinEvent);

        tick(30);

        verify(mockStore, never()).deliverAll(any());
    }

    @Test
    @DisplayName("If store is null on join, listener exits cleanly")
    void testNullStoreIgnored() {
        when(mockPlugin.getPendingPayouts()).thenReturn(null);

        PlayerMock player = createPlayer("Player");
        PlayerJoinEvent joinEvent = new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null);

        listener.onPlayerJoin(joinEvent);
        tick(30);

        verify(mockStore, never()).deliverAll(any());
    }
}
