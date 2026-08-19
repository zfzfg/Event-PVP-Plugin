package de.zfzfg.eventplugin.security;

import de.zfzfg.eventplugin.EventPlugin;
import de.zfzfg.eventplugin.manager.EventManager;
import de.zfzfg.eventplugin.model.EventConfig;
import de.zfzfg.eventplugin.session.EventSession;
import de.zfzfg.pvpwager.managers.ArenaManager;
import de.zfzfg.pvpwager.models.Arena;
import de.zfzfg.test.MockBukkitTestBase;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WorldProtectionListenerMockTest extends MockBukkitTestBase {

    private EventPlugin mockPlugin;
    private EventManager mockEventManager;
    private ArenaManager mockArenaManager;
    private WorldProtectionListener listener;

    @BeforeEach
    void setUp() {
        mockPlugin = mock(EventPlugin.class);
        mockEventManager = mock(EventManager.class);
        mockArenaManager = mock(ArenaManager.class);

        when(mockPlugin.getEventManager()).thenReturn(mockEventManager);
        when(mockPlugin.getArenaManager()).thenReturn(mockArenaManager);

        when(mockEventManager.getActiveSessions()).thenReturn(Collections.emptyMap());
        when(mockArenaManager.getArenas()).thenReturn(Collections.emptyMap());

        listener = new WorldProtectionListener(mockPlugin);
    }

    @Test
    @DisplayName("Block breaking is cancelled for regular players in protected event world")
    void testBlockBreakCancelledInProtectedWorld() {
        WorldMock world = createWorld("event_world");
        PlayerMock player = createPlayer("RegularPlayer");
        Block block = world.getBlockAt(0, 60, 0);
        block.setType(Material.STONE);

        EventSession session = mock(EventSession.class);
        EventConfig config = mock(EventConfig.class);
        when(config.getEventWorld()).thenReturn("event_world");
        when(config.getLobbyWorld()).thenReturn("lobby_world");
        when(config.isBuildAllowed()).thenReturn(false);
        when(session.getConfig()).thenReturn(config);

        Map<String, EventSession> activeSessions = new HashMap<>();
        activeSessions.put("event-1", session);
        when(mockEventManager.getActiveSessions()).thenReturn(activeSessions);

        BlockBreakEvent event = new BlockBreakEvent(block, player);
        listener.onBlockBreak(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Block placing is cancelled for regular players in protected arena world")
    void testBlockPlaceCancelledInProtectedWorld() {
        WorldMock world = createWorld("arena_world");
        PlayerMock player = createPlayer("RegularPlayer");
        Block block = world.getBlockAt(0, 60, 0);

        Arena arena = mock(Arena.class);
        when(arena.getArenaWorld()).thenReturn("arena_world");
        when(arena.isBuildAllowed()).thenReturn(false);

        Map<String, Arena> arenas = new HashMap<>();
        arenas.put("arena-1", arena);
        when(mockArenaManager.getArenas()).thenReturn(arenas);

        Block placedAgainst = world.getBlockAt(0, 59, 0);
        ItemStack itemInHand = new ItemStack(Material.DIRT);
        BlockPlaceEvent event = new BlockPlaceEvent(
                block,
                block.getState(),
                placedAgainst,
                itemInHand,
                player,
                true,
                EquipmentSlot.HAND
        );

        listener.onBlockPlace(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Operators bypass world protection")
    void testOpPlayerBypassesProtection() {
        WorldMock world = createWorld("event_world");
        PlayerMock opPlayer = createOpPlayer("Admin");
        Block block = world.getBlockAt(0, 60, 0);

        EventSession session = mock(EventSession.class);
        EventConfig config = mock(EventConfig.class);
        when(config.getEventWorld()).thenReturn("event_world");
        when(config.getLobbyWorld()).thenReturn("lobby_world");
        when(config.isBuildAllowed()).thenReturn(false);
        when(session.getConfig()).thenReturn(config);

        when(mockEventManager.getActiveSessions()).thenReturn(Collections.singletonMap("s1", session));

        BlockBreakEvent breakEvent = new BlockBreakEvent(block, opPlayer);
        listener.onBlockBreak(breakEvent);

        assertThat(breakEvent.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("Players with eventpvp.build permission bypass world protection")
    void testPermissionBypassesProtection() {
        WorldMock world = createWorld("event_world");
        PlayerMock player = createPlayer("Builder");
        player.addAttachment(org.mockbukkit.mockbukkit.MockBukkit.createMockPlugin("BuildPlugin"), "eventpvp.build", true);

        EventSession session = mock(EventSession.class);
        EventConfig config = mock(EventConfig.class);
        when(config.getEventWorld()).thenReturn("event_world");
        when(config.getLobbyWorld()).thenReturn("lobby_world");
        when(config.isBuildAllowed()).thenReturn(false);
        when(session.getConfig()).thenReturn(config);

        when(mockEventManager.getActiveSessions()).thenReturn(Collections.singletonMap("s1", session));

        BlockBreakEvent breakEvent = new BlockBreakEvent(world.getBlockAt(0, 60, 0), player);
        listener.onBlockBreak(breakEvent);

        assertThat(breakEvent.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("Modifications are allowed when isBuildAllowed is true in event config")
    void testBuildAllowedConfigRespected() {
        WorldMock world = createWorld("event_world");
        PlayerMock player = createPlayer("RegularPlayer");
        Block block = world.getBlockAt(0, 60, 0);

        EventSession session = mock(EventSession.class);
        EventConfig config = mock(EventConfig.class);
        when(config.getEventWorld()).thenReturn("event_world");
        when(config.getLobbyWorld()).thenReturn("lobby_world");
        when(config.isBuildAllowed()).thenReturn(true);
        when(session.getConfig()).thenReturn(config);

        when(mockEventManager.getActiveSessions()).thenReturn(Collections.singletonMap("s1", session));

        BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
        listener.onBlockBreak(breakEvent);

        assertThat(breakEvent.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("Entity explosions do not destroy blocks in protected worlds")
    void testEntityExplodeClearsBlockListInProtectedWorld() {
        WorldMock world = createWorld("event_world");
        PlayerMock player = createPlayer("Player");

        EventSession session = mock(EventSession.class);
        EventConfig config = mock(EventConfig.class);
        when(config.getEventWorld()).thenReturn("event_world");
        when(config.getLobbyWorld()).thenReturn("lobby_world");
        when(config.isBuildAllowed()).thenReturn(false);
        when(session.getConfig()).thenReturn(config);

        when(mockEventManager.getActiveSessions()).thenReturn(Collections.singletonMap("s1", session));

        List<Block> blocks = new ArrayList<>();
        blocks.add(world.getBlockAt(1, 60, 1));
        blocks.add(world.getBlockAt(1, 60, 2));

        EntityExplodeEvent explodeEvent = new EntityExplodeEvent(
                player,
                player.getLocation(),
                blocks,
                0.0f,
                null
        );

        listener.onEntityExplode(explodeEvent);

        assertThat(explodeEvent.blockList()).isEmpty();
    }
}
