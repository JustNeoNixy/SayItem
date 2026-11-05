package dev.sayitem.core;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChallengeQueueTest {
    private RunState.PlayerRun player() { return new RunState().player(UUID.randomUUID()); }
    @Test void reconnectAndModelWarmupPreserveRemainingTimeWithoutResettingIt() {
        var state = player(); var first = state.collect("minecraft:stone", 0, 10);
        state.collect("minecraft:apple", 1, 30);
        state.pause(4000); state.pause(5000);
        assertNull(state.expireCurrent(100000));
        assertFalse(state.unlockCurrent(first.nonce(), 5000));
        state.resume(100000);
        assertEquals(6000, state.current().remaining(100000));
        state.resume(101000); assertEquals(106000, state.current().deadline());
        assertEquals(first.nonce(), state.expireCurrent(106000).nonce());
        assertEquals(30000, state.current().remaining(106000));
    }
    @Test void sharedUnlockWhileOfflineDoesNotStartTheNextTimerUntilReady() {
        var progress = new UnlockProgress(); progress.setSharing(true); var id = UUID.randomUUID();
        var state = new RunState(progress).player(id);
        state.collect("minecraft:stone", 0, 10); state.collect("minecraft:apple", 1, 30);
        state.pause(4000); progress.grant(UUID.randomUUID(), "minecraft:stone"); state.skipUnlocked(5000);
        assertFalse(state.current().active()); state.resume(100000);
        assertEquals(130000, state.current().deadline());
    }
    @Test void disconnectingAfterTheDeadlineCannotReviveAnExpiredChallenge() {
        var state = player(); state.collect("minecraft:stone", 0, 10);
        state.pause(11000); state.resume(100000);
        assertNotNull(state.expireCurrent(100000));
    }

    @Test void waitingTypesKeepTheirWholeDurationAndStartInCollectionOrder() {
        var state = player();
        var stone = state.collect("minecraft:stone", 1000, 10);
        var apple = state.collect("minecraft:apple", 2000, 30);
        var dirt = state.collect("minecraft:dirt", 3000, 15);
        assertEquals(stone, state.current()); assertEquals(2, state.pendingCount());
        assertFalse(apple.active()); assertFalse(dirt.active());
        assertFalse(apple.expired(100000)); assertFalse(dirt.expired(100000));
        assertEquals(30000, apple.remaining(100000));
        assertTrue(state.unlockCurrent(stone.nonce(), 5000));
        assertEquals(apple.nonce(), state.current().nonce());
        assertEquals(35000, state.current().deadline()); assertEquals(1, state.pendingCount());
        assertTrue(state.unlockCurrent(apple.nonce(), 6000));
        assertEquals(dirt.nonce(), state.current().nonce()); assertEquals(21000, state.current().deadline());
        assertTrue(state.unlockCurrent(dirt.nonce(), 7000));
        assertNull(state.current()); assertEquals(0, state.pendingCount());
    }

    @Test void aLateTickExpiresOnlyTheActiveTypeAndStartsTheNextAtTheActualTime() {
        var state = player();
        var stone = state.collect("minecraft:stone", 0, 10);
        var apple = state.collect("minecraft:apple", 1, 10);
        assertNull(state.expireCurrent(9999));
        assertEquals(stone, state.expireCurrent(100000));
        assertEquals(apple.nonce(), state.current().nonce());
        assertEquals(110000, state.current().deadline());
        assertNull(state.expireCurrent(100000));
        assertFalse(state.unlocked.contains(stone.item()));
        assertEquals(apple.item(), state.expireCurrent(110000).item());
        assertNull(state.current());
    }

    @Test void retriesAndDuplicateStacksDoNotResetOrReorderTheQueue() {
        var state = player();
        var stone = state.collect("minecraft:stone", 0, 10);
        var apple = state.collect("minecraft:apple", 1, 15);
        assertSame(stone, state.collect(stone.item(), 9000, 30));
        assertSame(apple, state.collect(apple.item(), 9000, 30));
        assertEquals(1, state.pendingCount());
        assertEquals(stone, state.expireCurrent(10000));
        var retry = state.collect(stone.item(), 10001, 30);
        assertFalse(retry.active());
        assertEquals(apple.nonce(), state.current().nonce());
        assertTrue(state.unlockCurrent(apple.nonce(), 12000));
        assertEquals(retry.nonce(), state.current().nonce()); assertEquals(42000, state.current().deadline());
    }

    @Test void queuedStaleAndExpiredNoncesCannotSkipAheadOrUnlockAnything() {
        var state = player();
        var stone = state.collect("minecraft:stone", 0, 10);
        var apple = state.collect("minecraft:apple", 1, 15);
        assertNull(state.current(apple.nonce()));
        assertFalse(state.unlockCurrent(apple.nonce(), 1000));
        assertFalse(state.unlockCurrent("stale", 1000));
        assertFalse(state.unlockCurrent(stone.nonce(), 10000));
        assertTrue(state.unlocked.isEmpty()); assertEquals(stone, state.current());
        state.expireCurrent(10000);
        assertFalse(state.unlockCurrent(stone.nonce(), 10001));
        assertEquals(apple.nonce(), state.current().nonce());
    }

    @Test void changingLanguageKeepsTheQueueAndDurationsButInvalidatesOldSpeech() {
        var state = player();
        var first = state.collect("minecraft:stone", 0, 10);
        var waiting = state.collect("minecraft:apple", 1, 30);
        state.selectLanguage("pl");
        assertEquals(first.deadline(), state.current().deadline());
        assertFalse(state.unlockCurrent(first.nonce(), 2000));
        assertTrue(state.unlockCurrent(state.current().nonce(), 2000));
        assertEquals(waiting.item(), state.current().item());
        assertNotEquals(waiting.nonce(), state.current().nonce());
        assertEquals(32000, state.current().deadline());
        assertFalse(state.unlockCurrent(waiting.nonce(), 3000));
    }

    @Test void reconnectKeepsTheQueueAndUnlocksWhileEachPlayerAndFreshWorldIsIndependent() {
        var run = new RunState(); var id = UUID.randomUUID();
        var state = run.player(id);
        var stone = state.collect("minecraft:stone", 0, 10);
        state.collect("minecraft:apple", 1, 15);
        assertSame(state, run.player(id)); assertEquals(1, run.player(id).pendingCount());
        assertTrue(state.unlockCurrent(stone.nonce(), 1000));
        assertNull(run.player(id).collect(stone.item(), 2000, 30));
        assertNotNull(run.player(UUID.randomUUID()).collect(stone.item(), 2000, 30));
        assertNotNull(new RunState().player(id).collect(stone.item(), 2000, 30));
    }
}
