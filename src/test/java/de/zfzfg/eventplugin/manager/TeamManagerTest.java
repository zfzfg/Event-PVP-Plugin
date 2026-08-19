package de.zfzfg.eventplugin.manager;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamManagerTest {

    private TeamManager teamManager;

    @BeforeEach
    void setUp() {
        teamManager = new TeamManager();
    }

    @Test
    @DisplayName("assignTeams with 2 teams and even number of players")
    void testAssignTeamsTwoTeamsEven() {
        List<UUID> players = List.of(
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID()
        );

        teamManager.assignTeams(players, 2);

        Set<UUID> red = teamManager.getTeamMembers(TeamManager.Team.RED);
        Set<UUID> blue = teamManager.getTeamMembers(TeamManager.Team.BLUE);
        Set<UUID> green = teamManager.getTeamMembers(TeamManager.Team.GREEN);

        assertThat(red).hasSize(2);
        assertThat(blue).hasSize(2);
        assertThat(green).isEmpty();

        for (UUID p : players) {
            assertThat(teamManager.getPlayerTeam(p)).isIn(TeamManager.Team.RED, TeamManager.Team.BLUE);
        }
    }

    @Test
    @DisplayName("assignTeams with 2 teams and odd number of players discards excess player")
    void testAssignTeamsTwoTeamsOdd() {
        List<UUID> players = List.of(
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID() // 5 players
        );

        teamManager.assignTeams(players, 2);

        Set<UUID> red = teamManager.getTeamMembers(TeamManager.Team.RED);
        Set<UUID> blue = teamManager.getTeamMembers(TeamManager.Team.BLUE);

        assertThat(red).hasSize(2);
        assertThat(blue).hasSize(2);
        assertThat(teamManager.getAllAssignments()).hasSize(4); // 4 assigned, 1 discarded
    }

    @Test
    @DisplayName("assignTeams with 3 teams")
    void testAssignTeamsThreeTeams() {
        List<UUID> players = List.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
        );

        teamManager.assignTeams(players, 3);

        assertThat(teamManager.getTeamMembers(TeamManager.Team.RED)).hasSize(2);
        assertThat(teamManager.getTeamMembers(TeamManager.Team.BLUE)).hasSize(2);
        assertThat(teamManager.getTeamMembers(TeamManager.Team.GREEN)).hasSize(2);
    }

    @Test
    @DisplayName("areTeammates returns true for same team, false for different or unassigned")
    void testAreTeammates() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();

        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);
        Player p3 = mock(Player.class);
        when(p1.getUniqueId()).thenReturn(u1);
        when(p2.getUniqueId()).thenReturn(u2);
        when(p3.getUniqueId()).thenReturn(u3);

        teamManager.assignTeams(List.of(u1, u2), 2);
        // In 2-team split of 2 players, one is RED, one is BLUE
        assertThat(teamManager.areTeammates(p1, p2)).isFalse();

        // Check unassigned player
        assertThat(teamManager.areTeammates(p1, p3)).isFalse();
    }

    @Test
    @DisplayName("getWinningTeam returns winner when only one team remains alive")
    void testGetWinningTeam() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();
        UUID p4 = UUID.randomUUID();

        teamManager.assignTeams(List.of(p1, p2, p3, p4), 2);

        TeamManager.Team teamP1 = teamManager.getPlayerTeam(p1);

        // Only p1 is alive
        Set<UUID> alive = Set.of(p1);
        TeamManager.Team winner = teamManager.getWinningTeam(alive);
        assertThat(winner).isEqualTo(teamP1);

        // Nobody alive
        assertThat(teamManager.getWinningTeam(Collections.emptySet())).isNull();

        // Both p1 and p2 (from different teams) alive
        TeamManager.Team teamP2 = teamManager.getPlayerTeam(p2);
        if (teamP1 != teamP2) {
            assertThat(teamManager.getWinningTeam(Set.of(p1, p2))).isNull();
        }
    }

    @Test
    @DisplayName("getAliveTeamCount counts distinct alive teams")
    void testGetAliveTeamCount() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();
        UUID p4 = UUID.randomUUID();

        teamManager.assignTeams(List.of(p1, p2, p3, p4), 2);

        assertThat(teamManager.getAliveTeamCount(Set.of(p1, p2, p3, p4))).isEqualTo(2);
        assertThat(teamManager.getAliveTeamCount(Set.of(p1))).isEqualTo(1);
        assertThat(teamManager.getAliveTeamCount(Collections.emptySet())).isZero();
    }

    @Test
    @DisplayName("removePlayer and clear")
    void testRemovePlayerAndClear() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        teamManager.assignTeams(List.of(p1, p2), 2);
        TeamManager.Team team = teamManager.getPlayerTeam(p1);

        teamManager.removePlayer(p1);
        assertThat(teamManager.getPlayerTeam(p1)).isNull();
        assertThat(teamManager.getTeamMembers(team)).doesNotContain(p1);

        teamManager.clear();
        assertThat(teamManager.getAllAssignments()).isEmpty();
        assertThat(teamManager.getTeamMembers(TeamManager.Team.RED)).isEmpty();
        assertThat(teamManager.getTeamMembers(TeamManager.Team.BLUE)).isEmpty();
    }
}
