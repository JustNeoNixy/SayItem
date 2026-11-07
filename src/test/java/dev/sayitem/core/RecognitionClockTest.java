package dev.sayitem.core;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecognitionClockTest {
    @Test void speechCompletedBeforeTheDeadlineCanFinishRecognitionAfterIt() {
        var player = new RunState().player(UUID.randomUUID()); var round = player.collect("minecraft:stone", 0, 10);
        assertTrue(player.beginRecognition(round.nonce(), 9000));
        assertEquals(1000, player.remaining(15000)); assertNull(player.expireCurrent(15000));
        player.resume(15000); assertEquals(1000, player.remaining(15000));
        assertTrue(player.finishRecognition(round.nonce(), 16000));
        assertEquals(1000, player.remaining(16000));
        assertTrue(player.unlockCurrent(round.nonce(), 16001));
    }
    @Test void staleQueuedLateAndDuplicateSubmissionsCannotExtendTheGrace() {
        var player = new RunState().player(UUID.randomUUID()); var first = player.collect("minecraft:stone", 0, 10);
        var second = player.collect("minecraft:apple", 1);
        assertFalse(player.beginRecognition(second.nonce(), 100));
        assertFalse(player.beginRecognition("stale", 100));
        assertTrue(player.beginRecognition(first.nonce(), 1000));
        assertFalse(player.beginRecognition(first.nonce(), 5000));
        assertFalse(player.finishRecognition(second.nonce(), 6000));
        assertNull(player.expireRecognition(15999));
        assertEquals(first.nonce(), player.expireRecognition(16000).nonce());
        assertEquals(9000, player.remaining(16000));
        assertNotNull(player.expireCurrent(25000));
        assertFalse(player.beginRecognition(first.nonce(), 25001));
    }
    @Test void aFailedAttemptKeepsOnlyTheSpeakingTimeAndNextQueueItemGetsItsFullTimer() {
        var player = new RunState().player(UUID.randomUUID()); var first = player.collect("minecraft:stone", 0, 10);
        player.collect("minecraft:apple", 1, 30);
        assertTrue(player.beginRecognition(first.nonce(), 4000));
        assertTrue(player.finishRecognition(first.nonce(), 7000));
        assertEquals(6000, player.remaining(7000));
        assertEquals(first.nonce(), player.expireCurrent(13000).nonce());
        assertEquals(30000, player.remaining(13000));
    }
    @Test void disconnectAndLanguageReloadCancelRecognitionButKeepTheirOwnPause() {
        var player = new RunState().player(UUID.randomUUID()); var first = player.collect("minecraft:stone", 0, 10);
        player.beginRecognition(first.nonce(), 4000);
        player.cancelRecognition(); player.pause(5000);
        assertFalse(player.finishRecognition(first.nonce(), 6000));
        assertNull(player.expireCurrent(100000));
        player.resume(100000); assertEquals(6000, player.remaining(100000));
    }
    @Test void sharedUnlockWhileRecognizingStartsTheNextItemWithoutAPauseLeak() {
        var progress = new UnlockProgress(); progress.setSharing(true); var id = UUID.randomUUID();
        var player = new RunState(progress).player(id); var first = player.collect("minecraft:stone", 0, 10);
        player.collect("minecraft:apple", 1, 30); player.beginRecognition(first.nonce(), 1000);
        progress.grant(UUID.randomUUID(), "minecraft:stone");
        assertEquals(first.nonce(), player.skipUnlocked(6000).nonce());
        assertEquals(30000, player.remaining(6000));
        assertFalse(player.finishRecognition(first.nonce(), 7000));
        assertTrue(player.beginRecognition(player.current().nonce(), 7000));
    }
    @Test void aDelayedServerTickOrResultCannotExtendTheBoundedProcessingPause() {
        var run = new RunState(); var scan = run.player(UUID.randomUUID());
        var round = scan.collect("minecraft:stone", 0, 10);
        scan.beginRecognition(round.nonce(), 9000);
        assertNotNull(scan.expireRecognition(26000));
        assertNotNull(scan.expireCurrent(26000));
        var result = run.player(UUID.randomUUID()); round = result.collect("minecraft:stone", 0, 10);
        result.beginRecognition(round.nonce(), 9000);
        assertTrue(result.finishRecognition(round.nonce(), 26000));
        assertFalse(result.unlockCurrent(round.nonce(), 26000));
    }
    @Test void aDelayedSharedUnlockStillGivesTheNextItemItsFullTimer() {
        var progress = new UnlockProgress(); progress.setSharing(true);
        var player = new RunState(progress).player(UUID.randomUUID());
        var first = player.collect("minecraft:stone", 0, 10); player.collect("minecraft:apple", 0, 30);
        player.beginRecognition(first.nonce(), 9000); progress.grant(UUID.randomUUID(), "minecraft:stone");
        player.skipUnlocked(26000); assertEquals(30000, player.remaining(26000));
    }
}
