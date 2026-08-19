package de.zfzfg.core.location;

import de.zfzfg.eventplugin.EventPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReturnLocationStoreTest {

    @TempDir
    Path tempDir;

    private EventPlugin plugin;
    private ReturnLocationStore store;
    private World world;

    @BeforeEach
    void setUp() {
        plugin = mock(EventPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ReturnLocationStoreTest"));

        world = mock(World.class);
        when(world.getName()).thenReturn("world_nether");

        store = new ReturnLocationStore(plugin);
        store.load();
    }

    @Test
    @DisplayName("remember, peek, has, and size")
    void testRememberAndPeek() {
        UUID playerId = UUID.randomUUID();
        Location loc = new Location(world, 100.5, 64.0, -200.5, 90.0f, 45.0f);

        assertThat(store.has(playerId)).isFalse();
        assertThat(store.size()).isZero();

        store.remember(playerId, loc, ReturnReason.PVP_MATCH);

        assertThat(store.has(playerId)).isTrue();
        assertThat(store.size()).isEqualTo(1);

        StoredReturn stored = store.peek(playerId);
        assertThat(stored).isNotNull();
        assertThat(stored.playerId()).isEqualTo(playerId);
        assertThat(stored.worldName()).isEqualTo("world_nether");
        assertThat(stored.x()).isEqualTo(100.5);
        assertThat(stored.y()).isEqualTo(64.0);
        assertThat(stored.z()).isEqualTo(-200.5);
        assertThat(stored.yaw()).isEqualTo(90.0f);
        assertThat(stored.pitch()).isEqualTo(45.0f);
        assertThat(stored.reason()).isEqualTo(ReturnReason.PVP_MATCH);
    }

    @Test
    @DisplayName("remember should not overwrite existing entry for the same player")
    void testRememberDoesNotOverwrite() {
        UUID playerId = UUID.randomUUID();
        Location firstLoc = new Location(world, 10.0, 64.0, 10.0);
        Location secondLoc = new Location(world, 999.0, 100.0, 999.0);

        store.remember(playerId, firstLoc, ReturnReason.EVENT);
        store.remember(playerId, secondLoc, ReturnReason.PVP_MATCH);

        StoredReturn stored = store.peek(playerId);
        assertThat(stored).isNotNull();
        assertThat(stored.x()).isEqualTo(10.0);
        assertThat(stored.reason()).isEqualTo(ReturnReason.EVENT);
    }

    @Test
    @DisplayName("remember ignores null player, location, or null world")
    void testRememberNulls() {
        store.remember(null, new Location(world, 0, 0, 0), ReturnReason.EVENT);
        store.remember(UUID.randomUUID(), null, ReturnReason.EVENT);
        store.remember(UUID.randomUUID(), new Location(null, 0, 0, 0), ReturnReason.EVENT);

        assertThat(store.size()).isZero();
    }

    @Test
    @DisplayName("consume should return entry and remove it from store")
    void testConsume() {
        UUID playerId = UUID.randomUUID();
        Location loc = new Location(world, 50.0, 70.0, 50.0);
        store.remember(playerId, loc, ReturnReason.EVENT);

        StoredReturn consumed = store.consume(playerId);
        assertThat(consumed).isNotNull();
        assertThat(consumed.x()).isEqualTo(50.0);

        assertThat(store.has(playerId)).isFalse();
        assertThat(store.peek(playerId)).isNull();
        assertThat(store.size()).isZero();
    }

    @Test
    @DisplayName("forget removes entry without returning it")
    void testForget() {
        UUID playerId = UUID.randomUUID();
        Location loc = new Location(world, 50.0, 70.0, 50.0);
        store.remember(playerId, loc, ReturnReason.EVENT);

        store.forget(playerId);
        assertThat(store.has(playerId)).isFalse();
        assertThat(store.size()).isZero();
    }

    @Test
    @DisplayName("persistence round-trip across new instance via load")
    void testPersistenceRoundTrip() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        store.remember(p1, new Location(world, 10.0, 60.0, 20.0, 0.0f, 0.0f), ReturnReason.PVP_MATCH);
        store.remember(p2, new Location(world, 30.0, 65.0, 40.0, 180.0f, -10.0f), ReturnReason.EVENT);

        // Verify file was written
        File savedFile = new File(tempDir.toFile(), "player-return-locations.yml");
        assertThat(savedFile).exists();

        // Create new store instance from same directory
        ReturnLocationStore newStore = new ReturnLocationStore(plugin);
        newStore.load();

        assertThat(newStore.size()).isEqualTo(2);
        assertThat(newStore.has(p1)).isTrue();
        assertThat(newStore.has(p2)).isTrue();

        StoredReturn r1 = newStore.peek(p1);
        assertThat(r1.worldName()).isEqualTo("world_nether");
        assertThat(r1.x()).isEqualTo(10.0);
        assertThat(r1.reason()).isEqualTo(ReturnReason.PVP_MATCH);

        StoredReturn r2 = newStore.peek(p2);
        assertThat(r2.worldName()).isEqualTo("world_nether");
        assertThat(r2.x()).isEqualTo(30.0);
        assertThat(r2.reason()).isEqualTo(ReturnReason.EVENT);
    }

    @Test
    @DisplayName("all and countOlderThan")
    void testAllAndCountOlderThan() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        store.remember(p1, new Location(world, 0, 64, 0), ReturnReason.EVENT);
        store.remember(p2, new Location(world, 10, 64, 10), ReturnReason.PVP_MATCH);

        Collection<StoredReturn> all = store.all();
        assertThat(all).hasSize(2);

        // Saved just now, should not be older than 1 minute
        assertThat(store.countOlderThan(60_000L)).isZero();
    }
}
