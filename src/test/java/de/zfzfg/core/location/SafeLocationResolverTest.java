package de.zfzfg.core.location;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.manager.ConfigManager;
import de.zfzfg.pvpwager.managers.MatchManager;
import de.zfzfg.pvpwager.models.Match;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SafeLocationResolverTest {

    @Test
    @DisplayName("isSafe returns false for null location or null world")
    void testIsSafeNulls() {
        EventPlugin plugin = mock(EventPlugin.class);
        SafeLocationResolver resolver = new SafeLocationResolver(plugin);

        assertThat(resolver.isSafe(null)).isFalse();
        assertThat(resolver.isSafe(new Location(null, 0, 64, 0))).isFalse();
    }

    @Test
    @DisplayName("isSafe returns false if world is not loaded in Bukkit or if y is below minHeight + 5")
    void testIsSafeVoidAndUnloadedWorld() {
        EventPlugin plugin = mock(EventPlugin.class);
        SafeLocationResolver resolver = new SafeLocationResolver(plugin);

        World world = mock(World.class);
        when(world.getName()).thenReturn("survival");
        when(world.getMinHeight()).thenReturn(-64);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("survival")).thenReturn(null);
            Location loc = new Location(world, 0, 64, 0);
            assertThat(resolver.isSafe(loc)).isFalse();

            // When world is loaded
            bukkit.when(() -> Bukkit.getWorld("survival")).thenReturn(world);

            // Too low (void: y < -64 + 5 = -59)
            Location voidLoc = new Location(world, 0, -60, 0);
            assertThat(resolver.isSafe(voidLoc)).isFalse();

            // Exactly at threshold (-59)
            Location thresholdLoc = new Location(world, 0, -59, 0);
            assertThat(resolver.isSafe(thresholdLoc)).isTrue();

            // Normal safe height
            Location safeLoc = new Location(world, 0, 64, 0);
            assertThat(resolver.isSafe(safeLoc)).isTrue();
        }
    }

    @Test
    @DisplayName("resolve prioritizes stored return location")
    void testResolvePrioritizesStoredReturn() {
        EventPlugin plugin = mock(EventPlugin.class);
        ReturnLocationStore returnStore = mock(ReturnLocationStore.class);
        when(plugin.getReturnLocations()).thenReturn(returnStore);

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getMinHeight()).thenReturn(0);

        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);

        StoredReturn stored = new StoredReturn(playerId, "world", 10.0, 65.0, 10.0, 0f, 0f, ReturnReason.EVENT, System.currentTimeMillis());
        when(returnStore.peek(playerId)).thenReturn(stored);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);

            SafeLocationResolver resolver = new SafeLocationResolver(plugin);
            Location resolved = resolver.resolve(player);

            assertThat(resolved).isNotNull();
            assertThat(resolved.getX()).isEqualTo(10.0);
            assertThat(resolved.getY()).isEqualTo(65.0);
            assertThat(resolved.getZ()).isEqualTo(10.0);
        }
    }

    @Test
    @DisplayName("resolve falls back to match origin when stored return is absent")
    void testResolveFallsBackToMatchOrigin() {
        EventPlugin plugin = mock(EventPlugin.class);
        ReturnLocationStore returnStore = mock(ReturnLocationStore.class);
        when(plugin.getReturnLocations()).thenReturn(returnStore);

        MatchManager matchManager = mock(MatchManager.class);
        when(plugin.getMatchManager()).thenReturn(matchManager);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        Match match = mock(Match.class);
        when(matchManager.getMatchByPlayer(player)).thenReturn(match);

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getMinHeight()).thenReturn(0);

        Location matchLoc = new Location(world, 100.0, 70.0, 100.0);
        when(match.getOriginalLocation(player)).thenReturn(matchLoc);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);

            SafeLocationResolver resolver = new SafeLocationResolver(plugin);
            Location resolved = resolver.resolve(player);

            assertThat(resolved).isNotNull();
            assertThat(resolved.getX()).isEqualTo(100.0);
            assertThat(resolved.getY()).isEqualTo(70.0);
        }
    }

    @Test
    @DisplayName("resolve falls back to player respawn location if no return location or match origin")
    void testResolveFallsBackToRespawnLocation() {
        EventPlugin plugin = mock(EventPlugin.class);
        when(plugin.getReturnLocations()).thenReturn(null);
        when(plugin.getMatchManager()).thenReturn(null);

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getMinHeight()).thenReturn(0);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        Location bedLoc = new Location(world, 25.0, 64.0, 25.0);
        when(player.getRespawnLocation()).thenReturn(bedLoc);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);

            SafeLocationResolver resolver = new SafeLocationResolver(plugin);
            Location resolved = resolver.resolve(player);

            assertThat(resolved).isNotNull();
            assertThat(resolved.getX()).isEqualTo(25.0);
        }
    }

    @Test
    @DisplayName("fallbackSpawn returns main world spawn or first loaded world spawn")
    void testFallbackSpawn() {
        EventPlugin plugin = mock(EventPlugin.class);
        ConfigManager cfgManager = mock(ConfigManager.class);
        when(plugin.getConfigManager()).thenReturn(cfgManager);
        when(cfgManager.getMainWorld()).thenReturn("main_world");

        World mainWorld = mock(World.class);
        when(mainWorld.getName()).thenReturn("main_world");
        Location mainSpawn = new Location(mainWorld, 0, 100, 0);
        when(mainWorld.getSpawnLocation()).thenReturn(mainSpawn);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("main_world")).thenReturn(mainWorld);

            SafeLocationResolver resolver = new SafeLocationResolver(plugin);
            Location spawn = resolver.fallbackSpawn();

            assertThat(spawn).isEqualTo(mainSpawn);
        }
    }
}
