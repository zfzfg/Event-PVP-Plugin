package de.zfzfg.pvpwager.listeners;

import de.zfzfg.core.config.CoreConfigManager;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.managers.MatchManager;
import de.zfzfg.pvpwager.models.Match;
import de.zfzfg.pvpwager.models.MatchState;
import de.zfzfg.test.MockBukkitTestBase;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Collections;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PvPListenerMockTest extends MockBukkitTestBase {

    private EventPlugin mockPlugin;
    private MatchManager mockMatchManager;
    private CoreConfigManager mockCoreConfig;
    private PvPListener pvpListener;

    @BeforeEach
    void setUp() {
        mockPlugin = mock(EventPlugin.class);
        mockMatchManager = mock(MatchManager.class);
        mockCoreConfig = mock(CoreConfigManager.class);

        YamlConfiguration emptyMessages = new YamlConfiguration();
        when(mockCoreConfig.getMessages()).thenReturn(emptyMessages);
        when(mockPlugin.getCoreConfigManager()).thenReturn(mockCoreConfig);
        when(mockPlugin.getMatchManager()).thenReturn(mockMatchManager);
        when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("PvPListenerTest"));

        pvpListener = new PvPListener(mockPlugin);
    }

    @Test
    @DisplayName("Damage is cancelled if match is in STARTING state")
    void testDamageCancelledDuringCountdown() {
        PlayerMock attacker = createPlayer("Attacker");
        PlayerMock victim = createPlayer("Victim");

        Match match = mock(Match.class);
        when(match.getState()).thenReturn(MatchState.STARTING);
        when(match.getSpectators()).thenReturn(Collections.emptySet());
        when(mockMatchManager.getMatch(attacker, victim)).thenReturn(match);
        when(mockMatchManager.getMatchByPlayer(attacker)).thenReturn(match);
        when(mockMatchManager.getMatchByPlayer(victim)).thenReturn(match);

        DamageSource damageSource = DamageSource.builder(DamageType.PLAYER_ATTACK)
                .withDirectEntity(attacker)
                .withCausingEntity(attacker)
                .build();
        EntityDamageByEntityEvent event = createDamageByEntityEvent(
                attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damageSource, 5.0
        );

        pvpListener.onPlayerDamage(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Damage is allowed when match is in FIGHTING state")
    void testDamageAllowedDuringFighting() {
        PlayerMock attacker = createPlayer("Attacker");
        PlayerMock victim = createPlayer("Victim");

        Match match = mock(Match.class);
        when(match.getState()).thenReturn(MatchState.FIGHTING);
        when(match.getSpectators()).thenReturn(Collections.emptySet());
        when(mockMatchManager.getMatch(attacker, victim)).thenReturn(match);
        when(mockMatchManager.getMatchByPlayer(attacker)).thenReturn(match);
        when(mockMatchManager.getMatchByPlayer(victim)).thenReturn(match);

        DamageSource damageSource = DamageSource.builder(DamageType.PLAYER_ATTACK)
                .withDirectEntity(attacker)
                .withCausingEntity(attacker)
                .build();
        EntityDamageByEntityEvent event = createDamageByEntityEvent(
                attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damageSource, 5.0
        );

        pvpListener.onPlayerDamage(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("Damage by spectator is cancelled")
    void testSpectatorCannotAttack() {
        PlayerMock spectator = createPlayer("Spectator");
        PlayerMock victim = createPlayer("Victim");

        Match match = mock(Match.class);
        when(match.getSpectators()).thenReturn(Collections.singleton(spectator.getUniqueId()));
        when(mockMatchManager.getMatchByPlayer(spectator)).thenReturn(match);

        DamageSource damageSource = DamageSource.builder(DamageType.PLAYER_ATTACK)
                .withDirectEntity(spectator)
                .withCausingEntity(spectator)
                .build();
        EntityDamageByEntityEvent event = createDamageByEntityEvent(
                spectator, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damageSource, 5.0
        );

        pvpListener.onPlayerDamage(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Spectator is protected from all entity damage")
    void testSpectatorDamageProtected() {
        PlayerMock spectator = createPlayer("Spectator");

        Match match = mock(Match.class);
        when(match.getSpectators()).thenReturn(Collections.singleton(spectator.getUniqueId()));
        when(mockMatchManager.getMatchByPlayer(spectator)).thenReturn(match);

        DamageSource damageSource = DamageSource.builder(DamageType.FALL).build();
        EntityDamageEvent event = createDamageEvent(
                spectator, EntityDamageEvent.DamageCause.FALL, damageSource, 10.0
        );

        pvpListener.onSpectatorDamage(event);

        assertThat(event.isCancelled()).isTrue();
    }
}
