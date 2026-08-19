package de.zfzfg.pvpwager.models;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BoundariesTest {

    @Test
    @DisplayName("isInside returns true if location, min, or max is null")
    void testNullBoundaries() {
        World world = mock(World.class);
        Location loc = new Location(world, 10, 64, 10);

        Boundaries b1 = new Boundaries(null, null);
        assertThat(b1.isInside(loc)).isTrue();

        Boundaries b2 = new Boundaries(new Location(world, 0, 0, 0), null);
        assertThat(b2.isInside(loc)).isTrue();

        Boundaries b3 = new Boundaries(new Location(world, 0, 0, 0), new Location(world, 100, 100, 100));
        assertThat(b3.isInside(null)).isTrue();
    }

    @Test
    @DisplayName("isInside handles points inside, on edge, and outside the boundary")
    void testInsideAndOutside() {
        World world = mock(World.class);
        Location min = new Location(world, -10.0, 0.0, -10.0);
        Location max = new Location(world, 10.0, 100.0, 10.0);
        Boundaries boundaries = new Boundaries(min, max);

        // Inside
        assertThat(boundaries.isInside(new Location(world, 0.0, 50.0, 0.0))).isTrue();
        assertThat(boundaries.isInside(new Location(world, 5.0, 20.0, -5.0))).isTrue();

        // Exactly on edges/corners
        assertThat(boundaries.isInside(new Location(world, -10.0, 0.0, -10.0))).isTrue();
        assertThat(boundaries.isInside(new Location(world, 10.0, 100.0, 10.0))).isTrue();
        assertThat(boundaries.isInside(new Location(world, -10.0, 50.0, 10.0))).isTrue();

        // Outside X
        assertThat(boundaries.isInside(new Location(world, 10.1, 50.0, 0.0))).isFalse();
        assertThat(boundaries.isInside(new Location(world, -10.1, 50.0, 0.0))).isFalse();

        // Outside Y
        assertThat(boundaries.isInside(new Location(world, 0.0, -0.1, 0.0))).isFalse();
        assertThat(boundaries.isInside(new Location(world, 0.0, 100.1, 0.0))).isFalse();

        // Outside Z
        assertThat(boundaries.isInside(new Location(world, 0.0, 50.0, 10.1))).isFalse();
        assertThat(boundaries.isInside(new Location(world, 0.0, 50.0, -10.1))).isFalse();
    }

    @Test
    @DisplayName("isInside handles inverted min/max coordinates correctly")
    void testInvertedCoordinates() {
        World world = mock(World.class);
        // Min has larger coordinates than Max
        Location p1 = new Location(world, 100.0, 200.0, 100.0);
        Location p2 = new Location(world, 0.0, 50.0, 0.0);
        Boundaries boundaries = new Boundaries(p1, p2);

        assertThat(boundaries.isInside(new Location(world, 50.0, 100.0, 50.0))).isTrue();
        assertThat(boundaries.isInside(new Location(world, 0.0, 50.0, 0.0))).isTrue();
        assertThat(boundaries.isInside(new Location(world, 100.0, 200.0, 100.0))).isTrue();
        assertThat(boundaries.isInside(new Location(world, 101.0, 100.0, 50.0))).isFalse();
    }
}
