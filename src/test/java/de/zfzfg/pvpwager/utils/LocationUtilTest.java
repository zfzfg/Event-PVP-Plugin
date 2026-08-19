package de.zfzfg.pvpwager.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocationUtilTest {

    @Test
    @DisplayName("serializeLocation formats location correctly")
    void testSerializeLocation() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("arena_world");

        Location loc = new Location(world, 10.5, 64.0, -20.5, 90.0f, 45.0f);
        String serialized = LocationUtil.serializeLocation(loc);

        assertThat(serialized).isEqualTo("arena_world,10.5,64.0,-20.5,90.0,45.0");
        assertThat(LocationUtil.serializeLocation(null)).isEmpty();
    }

    @Test
    @DisplayName("deserializeLocation parses valid location string")
    void testDeserializeLocation() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("arena_world");

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("arena_world")).thenReturn(world);

            Location loc = LocationUtil.deserializeLocation("arena_world,10.5,64.0,-20.5,90.0,45.0");
            assertThat(loc).isNotNull();
            assertThat(loc.getWorld()).isEqualTo(world);
            assertThat(loc.getX()).isEqualTo(10.5);
            assertThat(loc.getY()).isEqualTo(64.0);
            assertThat(loc.getZ()).isEqualTo(-20.5);
            assertThat(loc.getYaw()).isEqualTo(90.0f);
            assertThat(loc.getPitch()).isEqualTo(45.0f);
        }
    }

    @Test
    @DisplayName("deserializeLocation handles invalid inputs")
    void testDeserializeInvalidInputs() {
        assertThat(LocationUtil.deserializeLocation(null)).isNull();
        assertThat(LocationUtil.deserializeLocation("")).isNull();
        assertThat(LocationUtil.deserializeLocation("world,1,2")).isNull(); // < 4 parts

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("unknown_world")).thenReturn(null);
            assertThat(LocationUtil.deserializeLocation("unknown_world,1,2,3")).isNull();

            World world = mock(World.class);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            assertThat(LocationUtil.deserializeLocation("world,not_a_number,2,3")).isNull();
        }
    }

    @Test
    @DisplayName("getCenterLocation calculates midpoint between two locations in the same world")
    void testGetCenterLocation() {
        World world = mock(World.class);
        Location loc1 = new Location(world, 0.0, 50.0, 0.0);
        Location loc2 = new Location(world, 100.0, 70.0, 100.0);

        Location center = LocationUtil.getCenterLocation(loc1, loc2);
        assertThat(center).isNotNull();
        assertThat(center.getWorld()).isEqualTo(world);
        assertThat(center.getX()).isEqualTo(50.0);
        assertThat(center.getY()).isEqualTo(60.0);
        assertThat(center.getZ()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("getCenterLocation returns null for different worlds or null locations")
    void testGetCenterLocationDifferentWorldsOrNull() {
        World w1 = mock(World.class);
        World w2 = mock(World.class);

        Location loc1 = new Location(w1, 0, 0, 0);
        Location loc2 = new Location(w2, 10, 10, 10);

        assertThat(LocationUtil.getCenterLocation(loc1, loc2)).isNull();
        assertThat(LocationUtil.getCenterLocation(loc1, null)).isNull();
        assertThat(LocationUtil.getCenterLocation(null, loc2)).isNull();
    }
}
