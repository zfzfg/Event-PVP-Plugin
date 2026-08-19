package de.zfzfg.core.web;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WebAuthManagerTest {

    private JavaPlugin plugin;
    private Server server;
    private BukkitScheduler scheduler;
    private WebAuthManager authManager;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("WebAuthManagerTest"));

        BukkitTask task = mock(BukkitTask.class);
        when(scheduler.runTaskTimerAsynchronously(eq(plugin), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(task);

        authManager = new WebAuthManager(plugin, "eventpvp.web.admin");
    }

    @Test
    @DisplayName("generateToken returns null if player lacks permission and is not op")
    void testGenerateTokenNoPermission() {
        Player player = mock(Player.class);
        when(player.hasPermission("eventpvp.web.admin")).thenReturn(false);
        when(player.isOp()).thenReturn(false);

        String token = authManager.generateToken(player);
        assertThat(token).isNull();
    }

    @Test
    @DisplayName("generateToken returns 16-char token when player has permission")
    void testGenerateTokenWithPermission() {
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("AdminPlayer");
        when(player.hasPermission("eventpvp.web.admin")).thenReturn(true);

        String token = authManager.generateToken(player);
        assertThat(token).isNotNull().hasSize(16);
    }

    @Test
    @DisplayName("validateTokenAndCreateSession creates session and consumes one-time token")
    void testTokenValidationAndSessionCreation() {
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("AdminPlayer");
        when(player.hasPermission("eventpvp.web.admin")).thenReturn(true);

        String token = authManager.generateToken(player);
        assertThat(token).isNotNull();

        // First validation should succeed and return session ID
        String sessionId = authManager.validateTokenAndCreateSession(token, "127.0.0.1");
        assertThat(sessionId).isNotNull().hasSize(32);
        assertThat(authManager.getActiveSessionCount()).isEqualTo(1);

        // Second validation with same token must fail (consumed)
        String secondAttempt = authManager.validateTokenAndCreateSession(token, "127.0.0.1");
        assertThat(secondAttempt).isNull();
    }

    @Test
    @DisplayName("validateSession returns session and extends expiry")
    void testValidateSession() {
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("AdminPlayer");
        when(player.hasPermission("eventpvp.web.admin")).thenReturn(true);

        String token = authManager.generateToken(player);
        String sessionId = authManager.validateTokenAndCreateSession(token, "192.168.1.10");

        WebAuthManager.AuthSession session = authManager.validateSession(sessionId, "192.168.1.10");
        assertThat(session).isNotNull();
        assertThat(session.playerUuid).isEqualTo(playerId);
        assertThat(session.playerName).isEqualTo("AdminPlayer");
        assertThat(session.clientIp).isEqualTo("192.168.1.10");
    }

    @Test
    @DisplayName("invalidateSession removes active session")
    void testInvalidateSession() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Admin");
        when(player.isOp()).thenReturn(true);

        String token = authManager.generateToken(player);
        String sessionId = authManager.validateTokenAndCreateSession(token, "127.0.0.1");

        assertThat(authManager.getActiveSessionCount()).isEqualTo(1);
        authManager.invalidateSession(sessionId);

        assertThat(authManager.getActiveSessionCount()).isZero();
        assertThat(authManager.validateSession(sessionId, "127.0.0.1")).isNull();
    }

    @Test
    @DisplayName("invalidateAllPlayerSessions removes all sessions for a specific player")
    void testInvalidateAllPlayerSessions() {
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("MultiSessionAdmin");
        when(player.isOp()).thenReturn(true);

        String t1 = authManager.generateToken(player);
        String s1 = authManager.validateTokenAndCreateSession(t1, "127.0.0.1");

        String t2 = authManager.generateToken(player);
        String s2 = authManager.validateTokenAndCreateSession(t2, "192.168.1.50");

        assertThat(authManager.getActiveSessionCount()).isEqualTo(2);

        authManager.invalidateAllPlayerSessions(playerId);
        assertThat(authManager.getActiveSessionCount()).isZero();
    }
}
