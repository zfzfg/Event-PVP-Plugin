package de.zfzfg.pvpwager.managers;

import de.zfzfg.eventplugin.EventPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MatchManagerTest {

    @Test
    void indexAndGetMatchIdByPlayer() {
        MatchManager matchManager = new MatchManager(null);

        UUID matchId = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        matchManager.indexPlayer(p1, matchId);
        matchManager.indexPlayer(p2, matchId);

        assertEquals(matchId, matchManager.getMatchIdByPlayer(p1));
        assertEquals(matchId, matchManager.getMatchIdByPlayer(p2));
    }

    @Test
    void clearTransientStateClearsIndex() {
        MatchManager matchManager = new MatchManager(null);

        UUID matchId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        matchManager.indexPlayer(playerId, matchId);
        assertNotNull(matchManager.getMatchIdByPlayer(playerId));

        matchManager.clearTransientState();
        assertNull(matchManager.getMatchIdByPlayer(playerId));
    }
}