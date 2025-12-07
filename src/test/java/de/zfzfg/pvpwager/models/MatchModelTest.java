package de.zfzfg.pvpwager.models;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MatchModelTest {

    @Test
    void testWagerConfirmationFlow() {
        Player p1 = Mockito.mock(Player.class);
        Player p2 = Mockito.mock(Player.class);
        Mockito.when(p1.getUniqueId()).thenReturn(UUID.randomUUID());
        Mockito.when(p2.getUniqueId()).thenReturn(UUID.randomUUID());

        Match match = new Match(p1, p2);

        match.confirmWager(p1);
        match.confirmWager(p2);

        // Simuliere Countdown bis 0
        for (int i = 0; i < 5; i++) {
            match.tickWagerConfirmation();
        }

        assertTrue(match.bothPlayersConfirmedWager(), "Beide Spieler sollten bestätigt haben");
    }

    @Test
    void testSkipVotes() {
        Player p1 = Mockito.mock(Player.class);
        Player p2 = Mockito.mock(Player.class);
        Mockito.when(p1.getUniqueId()).thenReturn(UUID.randomUUID());
        Mockito.when(p2.getUniqueId()).thenReturn(UUID.randomUUID());

        Match match = new Match(p1, p2);

        assertFalse(match.hasPlayerVotedToSkip(p1));
        match.addSkipVote(p1);
        assertTrue(match.hasPlayerVotedToSkip(p1));
        match.removeSkipVote(p1);
        assertFalse(match.hasPlayerVotedToSkip(p1));
    }

    @Test
    void testOriginalLocationStorage() {
        Player p1 = Mockito.mock(Player.class);
        Player p2 = Mockito.mock(Player.class);
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        Mockito.when(p1.getUniqueId()).thenReturn(u1);
        Mockito.when(p2.getUniqueId()).thenReturn(u2);

        Match match = new Match(p1, p2);
        Location loc1 = new Location(null, 10, 64, -5);
        Location loc2 = new Location(null, -2, 70, 42);

        match.getOriginalLocations().put(u1, loc1);
        match.getOriginalLocations().put(u2, loc2);

        assertEquals(loc1, match.getOriginalLocation(p1));
        assertEquals(loc2, match.getOriginalLocation(p2));
    }
}