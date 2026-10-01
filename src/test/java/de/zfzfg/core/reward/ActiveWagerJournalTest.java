package de.zfzfg.core.reward;

import de.zfzfg.eventplugin.EventPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActiveWagerJournalTest {

    @TempDir
    Path tempDir;

    private EventPlugin plugin;
    private ActiveWagerJournal journal;
    private PendingPayoutStore payouts;

    @BeforeEach
    void setUp() {
        plugin = mock(EventPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ActiveWagerJournalTest"));
        journal = new ActiveWagerJournal(plugin);
        journal.load();
        payouts = new PendingPayoutStore(plugin);
        payouts.load();
    }

    @Test
    void recordsMoneyAndRefundsEachPlayerOnce() {
        UUID matchId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(journal.record(matchId,
                new ActiveWagerJournal.Stake(first, matchId, null, 40),
                new ActiveWagerJournal.Stake(second, matchId, null, 15.5)));

        ActiveWagerJournal reloaded = new ActiveWagerJournal(plugin);
        reloaded.load();
        assertEquals(2, reloaded.openCount());

        assertEquals(2, reloaded.refundOpen(payouts));
        assertEquals(0, reloaded.openCount());
        assertEquals(40, payouts.snapshot().get(first).get(0).money(), 0.0001);
        assertEquals(15.5, payouts.snapshot().get(second).get(0).money(), 0.0001);
        assertTrue(payouts.snapshot().get(first).get(0).reason().startsWith(ActiveWagerJournal.CRASH_REFUND_PREFIX));

        assertEquals(0, reloaded.refundOpen(payouts), "ein zweiter Wiederanlauf darf nichts dazuerfinden");
        assertEquals(1, payouts.snapshot().get(first).size());
    }

    @Test
    void doesNotQueueAgainWhenThePayoutWasAlreadyWritten() {
        UUID matchId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        assertTrue(journal.record(matchId,
                new ActiveWagerJournal.Stake(playerId, matchId, null, 10)));

        payouts.queue(playerId, null, 10, ActiveWagerJournal.CRASH_REFUND_PREFIX + matchId);

        assertEquals(0, journal.refundOpen(payouts));
        assertEquals(0, journal.openCount());
        assertEquals(1, payouts.snapshot().get(playerId).size());
    }

    @Test
    void skipsEmptyStakes() {
        UUID matchId = UUID.randomUUID();
        assertTrue(journal.record(matchId,
                new ActiveWagerJournal.Stake(UUID.randomUUID(), matchId, null, 0)));
        assertEquals(0, journal.openCount());
    }

    @Test
    void refusesTheRecordWhenTheFileCannotBeWritten() throws Exception {
        Path blocked = tempDir.resolve("not-a-folder");
        Files.writeString(blocked, "x");
        when(plugin.getDataFolder()).thenReturn(blocked.toFile());

        ActiveWagerJournal blockedJournal = new ActiveWagerJournal(plugin);
        blockedJournal.load();
        UUID matchId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        assertFalse(blockedJournal.record(matchId,
                new ActiveWagerJournal.Stake(playerId, matchId, null, 99)));
        assertEquals(0, blockedJournal.openCount(), "ein nicht geschriebenes Journal darf beim Start nichts erstatten");
    }

    @Test
    void forgetRemovesOnlyThatPlayer() {
        UUID matchId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        journal.record(matchId,
                new ActiveWagerJournal.Stake(first, matchId, null, 5),
                new ActiveWagerJournal.Stake(second, matchId, null, 7));

        journal.forget(first);

        ActiveWagerJournal reloaded = new ActiveWagerJournal(plugin);
        reloaded.load();
        assertEquals(1, reloaded.openCount());
        assertEquals(1, reloaded.refundOpen(payouts));
        assertEquals(7, payouts.snapshot().get(second).get(0).money(), 0.0001);
        assertNull(payouts.snapshot().get(first));
    }
}
