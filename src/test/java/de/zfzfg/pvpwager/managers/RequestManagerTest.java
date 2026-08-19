package de.zfzfg.pvpwager.managers;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RequestManagerTest {

    private EventPlugin plugin;
    private ConfigManager pvpConfigManager;
    private MatchManager matchManager;
    private Server server;
    private BukkitScheduler scheduler;
    private RequestManager requestManager;

    @BeforeEach
    void setUp() {
        plugin = mock(EventPlugin.class);
        pvpConfigManager = mock(ConfigManager.class);
        matchManager = mock(MatchManager.class);
        server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);

        when(plugin.getPvpConfigManager()).thenReturn(pvpConfigManager);
        when(plugin.getMatchManager()).thenReturn(matchManager);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);

        when(pvpConfigManager.getMessage(any())).thenReturn("msg");
        when(pvpConfigManager.getMessage(any(), any(), any())).thenReturn("msg with placeholder");

        BukkitTask mockTask = mock(BukkitTask.class);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenReturn(mockTask);

        requestManager = new RequestManager(plugin);
    }

    @Test
    @DisplayName("sendRequest and hasPendingRequest")
    void testSendRequest() {
        Player sender = mock(Player.class);
        Player target = mock(Player.class);
        UUID senderId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        when(sender.getUniqueId()).thenReturn(senderId);
        when(sender.getName()).thenReturn("Alice");
        when(target.getUniqueId()).thenReturn(targetId);
        when(target.getName()).thenReturn("Bob");

        assertThat(requestManager.hasPendingRequest(sender)).isFalse();
        assertThat(requestManager.hasPendingRequest(target)).isFalse();

        requestManager.sendRequest(sender, target);

        assertThat(requestManager.hasPendingRequest(sender)).isTrue();
        assertThat(requestManager.hasPendingRequest(target)).isTrue();
    }

    @Test
    @DisplayName("acceptRequest starts match setup and removes request")
    void testAcceptRequest() {
        Player sender = mock(Player.class);
        Player target = mock(Player.class);
        UUID senderId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        when(sender.getUniqueId()).thenReturn(senderId);
        when(sender.getName()).thenReturn("Alice");
        when(target.getUniqueId()).thenReturn(targetId);
        when(target.getName()).thenReturn("Bob");

        requestManager.sendRequest(sender, target);

        boolean accepted = requestManager.acceptRequest(target, sender);
        assertThat(accepted).isTrue();

        verify(matchManager).startMatchSetup(sender, target);
        assertThat(requestManager.hasPendingRequest(sender)).isFalse();
        assertThat(requestManager.hasPendingRequest(target)).isFalse();
    }

    @Test
    @DisplayName("acceptRequest returns false if no request exists")
    void testAcceptRequestNoPending() {
        Player sender = mock(Player.class);
        Player target = mock(Player.class);
        when(sender.getUniqueId()).thenReturn(UUID.randomUUID());
        when(target.getUniqueId()).thenReturn(UUID.randomUUID());

        boolean accepted = requestManager.acceptRequest(target, sender);
        assertThat(accepted).isFalse();
        verify(matchManager, never()).startMatchSetup(any(), any());
    }

    @Test
    @DisplayName("cancelRequest removes pending request")
    void testCancelRequest() {
        Player sender = mock(Player.class);
        Player target = mock(Player.class);
        UUID senderId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        when(sender.getUniqueId()).thenReturn(senderId);
        when(sender.getName()).thenReturn("Alice");
        when(target.getUniqueId()).thenReturn(targetId);
        when(target.getName()).thenReturn("Bob");

        requestManager.sendRequest(sender, target);
        assertThat(requestManager.hasPendingRequest(sender)).isTrue();

        requestManager.cancelRequest(senderId, targetId);
        assertThat(requestManager.hasPendingRequest(sender)).isFalse();
        assertThat(requestManager.hasPendingRequest(target)).isFalse();
    }

    @Test
    @DisplayName("removeRequestsForPlayer cleans up both sender and target requests")
    void testRemoveRequestsForPlayer() {
        Player sender = mock(Player.class);
        Player target = mock(Player.class);
        UUID senderId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        when(sender.getUniqueId()).thenReturn(senderId);
        when(sender.getName()).thenReturn("Alice");
        when(target.getUniqueId()).thenReturn(targetId);
        when(target.getName()).thenReturn("Bob");

        requestManager.sendRequest(sender, target);
        assertThat(requestManager.hasPendingRequest(target)).isTrue();

        requestManager.removeRequestsForPlayer(target);
        assertThat(requestManager.hasPendingRequest(sender)).isFalse();
        assertThat(requestManager.hasPendingRequest(target)).isFalse();
    }
}
