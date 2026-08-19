package de.zfzfg.eventplugin.listeners;

import de.zfzfg.core.location.ReturnLocationStore;
import de.zfzfg.core.location.SafeLocationResolver;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.manager.ConfigManager;
import de.zfzfg.eventplugin.manager.EventManager;
import de.zfzfg.eventplugin.model.EventConfig;
import de.zfzfg.eventplugin.session.EventSession;
import de.zfzfg.pvpwager.managers.MatchManager;
import de.zfzfg.test.MockBukkitTestBase;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.Optional;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class VoidProtectionListenerMockTest extends MockBukkitTestBase {

    private EventPlugin mockPlugin;
    private EventManager mockEventManager;
    private MatchManager mockMatchManager;
    private SafeLocationResolver mockSafeLocations;
    private ReturnLocationStore mockReturnLocations;
    private ConfigManager mockConfigManager;
    private VoidProtectionListener listener;

    @BeforeEach
    void setUp() {
        mockPlugin = mock(EventPlugin.class);
        mockEventManager = mock(EventManager.class);
        mockMatchManager = mock(MatchManager.class);
        mockSafeLocations = mock(SafeLocationResolver.class);
        mockReturnLocations = mock(ReturnLocationStore.class);
        mockConfigManager = mock(ConfigManager.class);

        when(mockPlugin.getEventManager()).thenReturn(mockEventManager);
        when(mockPlugin.getMatchManager()).thenReturn(mockMatchManager);
        when(mockPlugin.getSafeLocations()).thenReturn(mockSafeLocations);
        when(mockPlugin.getReturnLocations()).thenReturn(mockReturnLocations);
        when(mockPlugin.getConfigManager()).thenReturn(mockConfigManager);
        when(mockConfigManager.getMessage(anyString())).thenReturn("Void protected");
        when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("VoidProtectionTest"));
        when(mockPlugin.getConsoleMsg(anyString(), any())).thenReturn("mock msg");

        listener = new VoidProtectionListener(mockPlugin);
    }

    @Test
    @DisplayName("Void damage in correct event world is NOT cancelled (normal void death)")
    void testVoidDamageAllowedInCorrectEventWorld() {
        WorldMock eventWorld = createWorld("event_world");
        PlayerMock player = createPlayer("EventParticipant");
        player.teleport(new Location(eventWorld, 0, 10, 0));

        EventSession session = mock(EventSession.class);
        EventConfig config = mock(EventConfig.class);
        when(config.getEventWorld()).thenReturn("event_world");
        when(config.getLobbyWorld()).thenReturn("lobby_world");
        when(session.getConfig()).thenReturn(config);
        when(session.getState()).thenReturn(EventSession.EventState.RUNNING);

        when(mockEventManager.getPlayerSession(player)).thenReturn(Optional.of(session));

        DamageSource damageSource = DamageSource.builder(DamageType.OUT_OF_WORLD).build();
        EntityDamageEvent event = createDamageEvent(
                player, EntityDamageEvent.DamageCause.VOID, damageSource, 20.0
        );

        listener.onVoidDamage(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("Void damage in wrong world while registered in event IS cancelled and rescued")
    void testVoidDamageCancelledInWrongWorld() {
        WorldMock wrongWorld = createWorld("wrong_world");
        WorldMock spawnWorld = createWorld("spawn_world");
        PlayerMock player = createPlayer("StrandedParticipant");
        player.teleport(new Location(wrongWorld, 0, -10, 0));

        EventSession session = mock(EventSession.class);
        EventConfig config = mock(EventConfig.class);
        when(config.getEventWorld()).thenReturn("event_world");
        when(config.getLobbyWorld()).thenReturn("lobby_world");
        when(session.getConfig()).thenReturn(config);
        when(session.getState()).thenReturn(EventSession.EventState.RUNNING);

        when(mockEventManager.getPlayerSession(player)).thenReturn(Optional.of(session));
        when(mockSafeLocations.resolve(eq(player)))
                .thenReturn(new Location(spawnWorld, 0, 64, 0));

        DamageSource damageSource = DamageSource.builder(DamageType.OUT_OF_WORLD).build();
        EntityDamageEvent event = createDamageEvent(
                player, EntityDamageEvent.DamageCause.VOID, damageSource, 20.0
        );

        listener.onVoidDamage(event);

        assertThat(event.isCancelled()).isTrue();
    }
}
