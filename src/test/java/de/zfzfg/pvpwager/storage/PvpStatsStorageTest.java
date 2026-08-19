package de.zfzfg.pvpwager.storage;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.PlayerStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PvpStatsStorageTest {

    @TempDir
    Path tempDir;

    private EventPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = mock(EventPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("PvpStatsStorageTest"));
    }

    @Test
    @DisplayName("load returns empty map if file does not exist")
    void testLoadNonExisting() {
        Map<UUID, PlayerStats> map = PvpStatsStorage.load(plugin);
        assertThat(map).isEmpty();
    }

    @Test
    @DisplayName("save and load round-trip")
    void testSaveAndLoad() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();

        PlayerStats s1 = new PlayerStats(u1);
        s1.addWins(10);
        s1.addLosses(3);
        s1.addDraws(2);

        PlayerStats s2 = new PlayerStats(u2);
        s2.addWins(5);
        s2.addLosses(8);
        s2.addDraws(1);

        Map<UUID, PlayerStats> toSave = new HashMap<>();
        toSave.put(u1, s1);
        toSave.put(u2, s2);

        PvpStatsStorage.save(plugin, toSave);

        File file = new File(tempDir.toFile(), "pvpstats.yml");
        assertThat(file).exists();

        Map<UUID, PlayerStats> loaded = PvpStatsStorage.load(plugin);
        assertThat(loaded).hasSize(2);

        PlayerStats l1 = loaded.get(u1);
        assertThat(l1).isNotNull();
        assertThat(l1.getWins()).isEqualTo(10);
        assertThat(l1.getLosses()).isEqualTo(3);
        assertThat(l1.getDraws()).isEqualTo(2);

        PlayerStats l2 = loaded.get(u2);
        assertThat(l2).isNotNull();
        assertThat(l2.getWins()).isEqualTo(5);
        assertThat(l2.getLosses()).isEqualTo(8);
        assertThat(l2.getDraws()).isEqualTo(1);
    }
}
