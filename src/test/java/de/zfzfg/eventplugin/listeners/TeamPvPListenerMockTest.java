package de.zfzfg.eventplugin.listeners;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.manager.ConfigManager;
import de.zfzfg.eventplugin.manager.EventManager;
import de.zfzfg.eventplugin.manager.TeamManager;
import de.zfzfg.eventplugin.model.EventConfig;
import de.zfzfg.eventplugin.session.EventSession;
import de.zfzfg.test.MockBukkitTestBase;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Arrow;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TeamPvPListenerMockTest extends MockBukkitTestBase {

    private EventPlugin mockPlugin;
    private EventManager mockEventManager;
    private ConfigManager mockConfigManager;
    private TeamPvPListener listener;

    @BeforeEach
    void setUp() {
        mockPlugin = mock(EventPlugin.class);
        mockEventManager = mock(EventManager.class);
        mockConfigManager = mock(ConfigManager.class);

        when(mockPlugin.getEventManager()).thenReturn(mockEventManager);
        when(mockPlugin.getConfigManager()).thenReturn(mockConfigManager);
        when(mockConfigManager.getPrefix()).thenReturn("[Event]");
        when(mockConfigManager.getMessage("no-friendly-fire")).thenReturn("Friendly fire is disabled!");

        listener = new TeamPvPListener(mockPlugin);
    }

    @Test
    @DisplayName("Friendly fire is blocked between teammates in running team event")
    void testFriendlyFireBlockedBetweenTeammates() {
        PlayerMock attacker = createPlayer("Attacker");
        PlayerMock victim = createPlayer("Victim");

        EventSession session = mock(EventSession.class);
        EventConfig config = mock(EventConfig.class);
        EventConfig.TeamSettings teamSettings = mock(EventConfig.TeamSettings.class);
        TeamManager teamManager = mock(TeamManager.class);
        TeamManager.Team team = mock(TeamManager.Team.class);

        when(config.getGameMode()).thenReturn(EventConfig.GameMode.TEAM_2);
        when(config.getTeamSettings()).thenReturn(teamSettings);
        when(teamSettings.isFriendlyFire()).thenReturn(false);
        when(session.getConfig()).thenReturn(config);
        when(session.getState()).thenReturn(EventSession.EventState.RUNNING);
        when(session.getTeamManager()).thenReturn(teamManager);

        when(teamManager.areTeammates(attacker, victim)).thenReturn(true);
        when(teamManager.getPlayerTeam(attacker)).thenReturn(team);

        when(mockEventManager.getPlayerSession(attacker)).thenReturn(Optional.of(session));
        when(mockEventManager.getPlayerSession(victim)).thenReturn(Optional.of(session));

        DamageSource damageSource = DamageSource.builder(DamageType.PLAYER_ATTACK)
                .withDirectEntity(attacker)
                .withCausingEntity(attacker)
                .build();
        EntityDamageByEntityEvent event = createDamageByEntityEvent(
                attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damageSource, 5.0
        );

        listener.onPlayerDamage(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Damage is allowed between players in opposing teams")
    void testDamageAllowedBetweenOpposingTeams() {
        PlayerMock attacker = createPlayer("Attacker");
        PlayerMock enemy = createPlayer("Enemy");

        EventSession session = mock(EventSession.class);
        EventConfig config = mock(EventConfig.class);
        EventConfig.TeamSettings teamSettings = mock(EventConfig.TeamSettings.class);
        TeamManager teamManager = mock(TeamManager.class);

        when(config.getGameMode()).thenReturn(EventConfig.GameMode.TEAM_2);
        when(config.getTeamSettings()).thenReturn(teamSettings);
        when(teamSettings.isFriendlyFire()).thenReturn(false);
        when(session.getConfig()).thenReturn(config);
        when(session.getState()).thenReturn(EventSession.EventState.RUNNING);
        when(session.getTeamManager()).thenReturn(teamManager);

        when(teamManager.areTeammates(attacker, enemy)).thenReturn(false);

        when(mockEventManager.getPlayerSession(attacker)).thenReturn(Optional.of(session));
        when(mockEventManager.getPlayerSession(enemy)).thenReturn(Optional.of(session));

        DamageSource damageSource = DamageSource.builder(DamageType.PLAYER_ATTACK)
                .withDirectEntity(attacker)
                .withCausingEntity(attacker)
                .build();
        EntityDamageByEntityEvent event = createDamageByEntityEvent(
                attacker, enemy, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damageSource, 5.0
        );

        listener.onPlayerDamage(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("Friendly fire is allowed when friendly fire is enabled in config")
    void testFriendlyFireAllowedWhenConfigured() {
        PlayerMock attacker = createPlayer("Attacker");
        PlayerMock victim = createPlayer("Victim");

        EventSession session = mock(EventSession.class);
        EventConfig config = mock(EventConfig.class);
        EventConfig.TeamSettings teamSettings = mock(EventConfig.TeamSettings.class);
        TeamManager teamManager = mock(TeamManager.class);

        when(config.getGameMode()).thenReturn(EventConfig.GameMode.TEAM_2);
        when(config.getTeamSettings()).thenReturn(teamSettings);
        when(teamSettings.isFriendlyFire()).thenReturn(true);
        when(session.getConfig()).thenReturn(config);
        when(session.getState()).thenReturn(EventSession.EventState.RUNNING);
        when(session.getTeamManager()).thenReturn(teamManager);

        when(mockEventManager.getPlayerSession(attacker)).thenReturn(Optional.of(session));
        when(mockEventManager.getPlayerSession(victim)).thenReturn(Optional.of(session));

        DamageSource damageSource = DamageSource.builder(DamageType.PLAYER_ATTACK)
                .withDirectEntity(attacker)
                .withCausingEntity(attacker)
                .build();
        EntityDamageByEntityEvent event = createDamageByEntityEvent(
                attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damageSource, 5.0
        );

        listener.onPlayerDamage(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("Projectile friendly fire (e.g. arrow shot by teammate) is blocked")
    void testFriendlyFireBlockedForProjectiles() {
        PlayerMock shooter = createPlayer("Shooter");
        PlayerMock victim = createPlayer("Victim");

        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(shooter);

        EventSession session = mock(EventSession.class);
        EventConfig config = mock(EventConfig.class);
        EventConfig.TeamSettings teamSettings = mock(EventConfig.TeamSettings.class);
        TeamManager teamManager = mock(TeamManager.class);
        TeamManager.Team team = mock(TeamManager.Team.class);

        when(config.getGameMode()).thenReturn(EventConfig.GameMode.TEAM_3);
        when(config.getTeamSettings()).thenReturn(teamSettings);
        when(teamSettings.isFriendlyFire()).thenReturn(false);
        when(session.getConfig()).thenReturn(config);
        when(session.getState()).thenReturn(EventSession.EventState.RUNNING);
        when(session.getTeamManager()).thenReturn(teamManager);

        when(teamManager.areTeammates(shooter, victim)).thenReturn(true);
        when(teamManager.getPlayerTeam(shooter)).thenReturn(team);

        when(mockEventManager.getPlayerSession(shooter)).thenReturn(Optional.of(session));
        when(mockEventManager.getPlayerSession(victim)).thenReturn(Optional.of(session));

        DamageSource damageSource = DamageSource.builder(DamageType.ARROW)
                .withDirectEntity(arrow)
                .withCausingEntity(shooter)
                .build();
        EntityDamageByEntityEvent event = createDamageByEntityEvent(
                arrow, victim, EntityDamageEvent.DamageCause.PROJECTILE, damageSource, 7.0
        );

        listener.onPlayerDamage(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Damage is ignored when players are not in the same event")
    void testIgnoredWhenNotInSameEvent() {
        PlayerMock attacker = createPlayer("Attacker");
        PlayerMock victim = createPlayer("Victim");

        EventSession session1 = mock(EventSession.class);
        EventSession session2 = mock(EventSession.class);

        when(mockEventManager.getPlayerSession(attacker)).thenReturn(Optional.of(session1));
        when(mockEventManager.getPlayerSession(victim)).thenReturn(Optional.of(session2));

        DamageSource damageSource = DamageSource.builder(DamageType.PLAYER_ATTACK)
                .withDirectEntity(attacker)
                .withCausingEntity(attacker)
                .build();
        EntityDamageByEntityEvent event = createDamageByEntityEvent(
                attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damageSource, 5.0
        );

        listener.onPlayerDamage(event);

        assertThat(event.isCancelled()).isFalse();
    }
}
