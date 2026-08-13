package de.zfzfg.core.util;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class CommandCooldownManagerTest {

    @Test
    void testCooldownCheckAndApply() {
        CommandCooldownManager manager = new CommandCooldownManager(2000L);
        Player player = Mockito.mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);

        // Erster Aufruf: erlaubt
        assertTrue(manager.checkAndApply(player, "pvp"));

        // Sofortiger zweiter Aufruf: blockiert
        assertFalse(manager.checkAndApply(player, "pvp"));

        // Anderer Befehl: erlaubt
        assertTrue(manager.checkAndApply(player, "event"));

        // Anderer Spieler: erlaubt
        Player otherPlayer = Mockito.mock(Player.class);
        when(otherPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        assertTrue(manager.checkAndApply(otherPlayer, "pvp"));
    }

    @Test
    void testRemovePlayer() {
        CommandCooldownManager manager = new CommandCooldownManager(5000L);
        Player player = Mockito.mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);

        assertTrue(manager.checkAndApply(player, "pvp"));
        assertFalse(manager.checkAndApply(player, "pvp"));

        manager.removePlayer(uuid);
        assertTrue(manager.checkAndApply(player, "pvp"));
    }
}
