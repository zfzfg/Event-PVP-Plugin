package de.zfzfg.pvpwager.gui.livetrade;

import de.zfzfg.core.config.CoreConfigManager;
import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.pvpwager.models.Arena;
import de.zfzfg.pvpwager.models.EquipmentSet;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiveTradeSessionTest {

    private EventPlugin plugin;
    private CoreConfigManager coreConfigManager;
    private Player p1;
    private Player p2;
    private UUID u1;
    private UUID u2;
    private Location loc1;
    private Location loc2;

    @BeforeEach
    void setUp() {
        plugin = mock(EventPlugin.class);
        coreConfigManager = mock(CoreConfigManager.class);
        when(plugin.getCoreConfigManager()).thenReturn(coreConfigManager);
        when(coreConfigManager.getMessages()).thenReturn(new YamlConfiguration());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("LiveTradeSessionTest"));

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");

        u1 = UUID.randomUUID();
        u2 = UUID.randomUUID();

        p1 = mock(Player.class);
        p2 = mock(Player.class);
        when(p1.getUniqueId()).thenReturn(u1);
        when(p2.getUniqueId()).thenReturn(u2);
        when(p1.getName()).thenReturn("Alice");
        when(p2.getName()).thenReturn("Bob");

        loc1 = new Location(world, 10, 64, 10);
        loc2 = new Location(world, 20, 64, 20);
        when(p1.getLocation()).thenReturn(loc1);
        when(p2.getLocation()).thenReturn(loc2);
    }

    @Test
    @DisplayName("session initialization captures original locations and players")
    void testSessionInit() {
        LiveTradeSession session = new LiveTradeSession(plugin, p1, p2);

        assertThat(session.getSessionId()).isNotNull();
        assertThat(session.isEnded()).isFalse();
        assertThat(session.isConfirmed()).isFalse();
        assertThat(session.isCountdownActive()).isFalse();

        assertThat(session.getPlayer1().getPlayer()).isEqualTo(p1);
        assertThat(session.getPlayer2().getPlayer()).isEqualTo(p2);

        assertThat(session.getPlayer1OriginalLocation()).isEqualTo(loc1);
        assertThat(session.getPlayer2OriginalLocation()).isEqualTo(loc2);

        assertThat(session.getTradePlayer(p1)).isEqualTo(session.getPlayer1());
        assertThat(session.getTradePlayer(p2)).isEqualTo(session.getPlayer2());
        assertThat(session.getOtherPlayer(p1)).isEqualTo(p2);
        assertThat(session.getOtherPlayer(p2)).isEqualTo(p1);
    }

    @Test
    @DisplayName("arena and equipment selection and configuration complete check")
    void testArenaAndEquipmentSelection() {
        LiveTradeSession session = new LiveTradeSession(plugin, p1, p2);

        assertThat(session.isConfigurationComplete()).isFalse();

        Arena arena = mock(Arena.class);
        EquipmentSet equipment = mock(EquipmentSet.class);

        session.setSelectedArena(arena);
        assertThat(session.getSelectedArena()).isEqualTo(arena);
        assertThat(session.isConfigurationComplete()).isFalse();

        session.setSelectedEquipment(equipment);
        assertThat(session.getSelectedEquipment()).isEqualTo(equipment);
        assertThat(session.isConfigurationComplete()).isTrue();
    }

    @Test
    @DisplayName("isExpired returns false right after creation")
    void testIsExpired() {
        LiveTradeSession session = new LiveTradeSession(plugin, p1, p2);
        assertThat(session.isExpired()).isFalse();
    }
}
