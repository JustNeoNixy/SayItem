package dev.sayitem.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class UnlockProgressTest {
    @TempDir Path directory;
    @Test void personalUnlocksSurviveRestartAndStaySeparateForEachPlayerAndWorld() throws Exception {
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        Path file = directory.resolve("world/data/sayitem-unlocks.json");
        var progress = new UnlockProgress();
        var state = new RunState(progress).player(alice);
        var round = state.collect("minecraft:stone", 0);
        assertTrue(state.unlockCurrent(round.nonce(), 1));
        assertTrue(progress.dirty()); progress.save(file); assertFalse(progress.dirty());
        var restored = UnlockProgress.load(file);
        assertNull(new RunState(restored).player(alice).collect("minecraft:stone", 100000));
        assertTrue(restored.unlocked(bob).isEmpty());
        assertTrue(UnlockProgress.load(directory.resolve("another-world/data/sayitem-unlocks.json")).unlocked(alice).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> restored.unlocked(alice).clear());
    }
    @Test void sharingMergesHistoryAndGrantsOfflineAndFuturePlayersWithoutRelockingOnDisable() throws Exception {
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID(), newcomer = UUID.randomUUID();
        var progress = new UnlockProgress();
        progress.grant(alice, "minecraft:stone"); progress.grant(bob, "minecraft:apple");
        progress.setSharing(true);
        assertEquals(Set.of("minecraft:stone", "minecraft:apple"), progress.unlocked(alice));
        assertEquals(progress.unlocked(alice), progress.unlocked(newcomer));
        progress.grant(alice, "minecraft:dirt");
        assertTrue(progress.unlocked(bob).contains("minecraft:dirt"));
        Path file = directory.resolve("unlocks.json"); progress.save(file);
        progress = UnlockProgress.load(file); assertTrue(progress.sharing());
        progress.setSharing(false); progress.grant(alice, "minecraft:diamond");
        assertTrue(progress.unlocked(bob).contains("minecraft:dirt"));
        assertFalse(progress.unlocked(bob).contains("minecraft:diamond"));
        assertTrue(progress.unlocked(UUID.randomUUID()).isEmpty());
        progress.save(file); assertFalse(UnlockProgress.load(file).sharing());
    }
    @Test void sharedUnlocksRemoveQueuedTypesAndAdvanceTheCurrentOneWithItsWholeTimer() {
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        var progress = new UnlockProgress(); progress.setSharing(true);
        var run = new RunState(progress); var first = run.player(alice); var second = run.player(bob);
        var apple = first.collect("minecraft:apple", 0);
        var stone = second.collect("minecraft:stone", 0, 10);
        second.collect("minecraft:apple", 1); second.collect("minecraft:dirt", 2, 30);
        assertTrue(first.unlockCurrent(apple.nonce(), 1000));
        assertNull(second.skipUnlocked(1000));
        assertEquals(stone, second.current()); assertEquals(1, second.pendingCount());
        var firstStone = first.collect("minecraft:stone", 1000);
        assertTrue(first.unlockCurrent(firstStone.nonce(), 2000));
        assertEquals(stone, second.skipUnlocked(2000));
        assertEquals("minecraft:dirt", second.current().item()); assertEquals(32000, second.current().deadline());
        assertFalse(second.unlockCurrent(stone.nonce(), 2001));
    }
    @Test void countersOnlyIncludeRegisteredItemsButPreserveMissingModProgress() throws Exception {
        var progress = new UnlockProgress(); UUID player = UUID.randomUUID();
        progress.grant(player, "minecraft:stone"); progress.grant(player, "oldmod:wand");
        assertEquals(1, progress.count(player, Set.of("minecraft:stone", "minecraft:apple")));
        Path file = directory.resolve("unlocks.json"); progress.save(file);
        assertEquals(2, UnlockProgress.load(file).count(player, Set.of("minecraft:stone", "oldmod:wand")));
    }
    @Test void malformedOrUnsupportedDataIsReportedWithoutOverwritingTheWorldFile() throws Exception {
        Path file = directory.resolve("unlocks.json");
        for (String broken : List.of("{", "{\"schemaVersion\":2}", "{\"players\":{\"invalid-uuid\":[]}}")) {
            Files.writeString(file, broken);
            assertThrows(IOException.class, () -> UnlockProgress.load(file));
            assertEquals(broken, Files.readString(file));
        }
    }
    @Test void failedSavesKeepProgressDirtyForRetryAndDoNotOverwriteOtherFiles() throws Exception {
        var progress = new UnlockProgress(); progress.grant(UUID.randomUUID(), "minecraft:stone");
        Path blocked = directory.resolve("blocked"); Files.createDirectories(blocked);
        Files.writeString(blocked.resolve("keep.txt"), "preserved");
        assertThrows(IOException.class, () -> progress.save(blocked));
        assertTrue(progress.dirty()); assertEquals("preserved", Files.readString(blocked.resolve("keep.txt")));
        progress.save(directory.resolve("unlocks.json")); assertFalse(progress.dirty());
    }
}
