package de.zfzfg.pvpwager.listeners;

import de.zfzfg.core.location.ReturnLocationStore;
import de.zfzfg.core.location.SafeLocationResolver;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.manager.EventManager;
import de.zfzfg.eventplugin.session.EventSession;
import de.zfzfg.pvpwager.managers.MatchManager;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.test.MockBukkitTestBase;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.Optional;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SpectatorRecoveryListenerMockTest extends MockBukkitTestBase {

    private EventPlugin mockPlugin;
    private MatchManager mockMatchManager;
    private EventManager mockEventManager;
    private SafeLocationResolver mockSafeLocations;
    private ReturnLocationStore mockReturnLocations;
    private SpectatorRecoveryListener listener;

    @BeforeEach
    void setUp() {
        mockPlugin = mock(EventPlugin.class);
        mockMatchManager = mock(MatchManager.class);
        mockEventManager = mock(EventManager.class);
        mockSafeLocations = mock(SafeLocationResolver.class);
        mockReturnLocations = mock(ReturnLocationStore.class);

        when(mockPlugin.getMatchManager()).thenReturn(mockMatchManager);
        when(mockPlugin.getEventManager()).thenReturn(mockEventManager);
        when(mockPlugin.getSafeLocations()).thenReturn(mockSafeLocations);
        when(mockPlugin.getReturnLocations()).thenReturn(mockReturnLocations);
        when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("SpectatorRecoveryTest"));
        when(mockPlugin.getConsoleMsg(anyString(), any())).thenReturn("mock msg");

        listener = new SpectatorRecoveryListener(mockPlugin);
    }

    @Test
    @DisplayName("Orphaned spectator player is recovered to survival mode on join")
    void testSpectatorRecoveredWhenNoActiveMatch() {
        WorldMock world = createWorld("spawn_world");
        PlayerMock player = createPlayer("OrphanedSpectator");
        player.setGameMode(GameMode.SPECTATOR);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 1000, 1));

        when(mockMatchManager.getMatchByPlayer(player)).thenReturn(null);
        when(mockEventManager.getPlayerSession(player)).thenReturn(Optional.empty());

        Location safeLoc = new Location(world, 100, 64, 100);
        when(mockSafeLocations.resolve(player)).thenReturn(safeLoc);
        when(mockSafeLocations.teleportSafely(player, safeLoc)).thenReturn(true);

        PlayerJoinEvent joinEvent = new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null);
        listener.onPlayerJoin(joinEvent);

        assertThat(player.getGameMode()).isEqualTo(GameMode.SURVIVAL);
        assertThat(player.hasPotionEffect(PotionEffectType.INVISIBILITY)).isFalse();
        verify(mockReturnLocations).consume(player.getUniqueId());
    }

    @Test
    @DisplayName("Spectator player in active match is NOT recovered")
    void testSpectatorRemainsWhenActiveMatch() {
        PlayerMock player = createPlayer("ActiveSpectator");
        player.setGameMode(GameMode.SPECTATOR);

        Match match = mock(Match.class);
        when(mockMatchManager.getMatchByPlayer(player)).thenReturn(match);

        PlayerJoinEvent joinEvent = new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null);
        listener.onPlayerJoin(joinEvent);

        assertThat(player.getGameMode()).isEqualTo(GameMode.SPECTATOR);
        verify(mockSafeLocations, never()).resolve(any());
        verify(mockReturnLocations, never()).consume(any());
    }

    @Test
    @DisplayName("Spectator player in active event is NOT recovered")
    void testSpectatorRemainsWhenActiveEvent() {
        PlayerMock player = createPlayer("EventSpectator");
        player.setGameMode(GameMode.SPECTATOR);

        EventSession session = mock(EventSession.class);
        when(mockMatchManager.getMatchByPlayer(player)).thenReturn(null);
        when(mockEventManager.getPlayerSession(player)).thenReturn(Optional.of(session));

        PlayerJoinEvent joinEvent = new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null);
        listener.onPlayerJoin(joinEvent);

        assertThat(player.getGameMode()).isEqualTo(GameMode.SPECTATOR);
        verify(mockSafeLocations, never()).resolve(any());
    }

    @Test
    @DisplayName("Survival player is ignored on join")
    void testSurvivalPlayerIgnored() {
        PlayerMock player = createPlayer("NormalPlayer");
        player.setGameMode(GameMode.SURVIVAL);

        PlayerJoinEvent joinEvent = new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null);
        listener.onPlayerJoin(joinEvent);

        assertThat(player.getGameMode()).isEqualTo(GameMode.SURVIVAL);
        verify(mockMatchManager, never()).getMatchByPlayer(any());
    }
}
