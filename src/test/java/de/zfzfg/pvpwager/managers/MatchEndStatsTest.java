package de.zfzfg.pvpwager.managers;

import de.zfzfg.core.config.CoreConfigManager;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Match;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Ein beendetes Match darf seine Statistik nicht erst dem Fuenf-Minuten-Task ueberlassen.
 */
class MatchEndStatsTest {

    @TempDir
    Path tempDir;

    @Test
    void endMatchWritesPvpStatsImmediately() {
        EventPlugin plugin = mock(EventPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("MatchEndStatsTest"));

        CoreConfigManager core = mock(CoreConfigManager.class);
        FileConfiguration messages = mock(FileConfiguration.class);
        when(core.getMessages()).thenReturn(messages);
        when(messages.getString(anyString(), nullable(String.class))).thenReturn(null);
        when(plugin.getCoreConfigManager()).thenReturn(core);

        StatsManager stats = new StatsManager();
        when(plugin.getStatsManager()).thenReturn(stats);

        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Player first = player(firstId, "Ada");
        Player second = player(secondId, "Bea");

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskLater(any(), any(Runnable.class), anyLong())).thenReturn(mock(BukkitTask.class));
        when(scheduler.runTaskAsynchronously(any(), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return mock(BukkitTask.class);
        });

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            Match match = new Match(first, second);
            match.setNoWagerMode(true);
            new MatchManager(plugin).endMatch(match, null, true);
        }

        assertEquals(1, stats.getStats(firstId).orElseThrow().getDraws());
        assertEquals(1, stats.getStats(secondId).orElseThrow().getDraws());
        assertTrue(tempDir.resolve("pvpstats.yml").toFile().isFile(),
                "pvpstats.yml muss am Match-Ende geschrieben werden, nicht erst nach fuenf Minuten");
    }

    private static Player player(UUID id, String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn(name);
        return player;
    }
}
