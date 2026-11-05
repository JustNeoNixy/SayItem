package dev.sayitem.core;

import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TimerLimitTest {
    @TempDir Path directory;
    @Test void eachTimerExpiresAtItsExactDeadlineAndRetriesDoNotResetIt() {
        for (int seconds = 10; seconds <= 30; seconds++) {
            var state = new RunState().player(UUID.randomUUID());
            var round = state.collect("minecraft:stone", 1000, seconds);
            assertEquals(1000 + seconds * 1000L, round.deadline());
            assertFalse(round.expired(round.deadline() - 1));
            assertTrue(round.expired(round.deadline()));
            assertSame(round, state.collect("minecraft:stone", 2000, 30));
        }
        assertThrows(IllegalArgumentException.class, () -> TimerLimit.millis(9));
        assertThrows(IllegalArgumentException.class, () -> TimerLimit.millis(31));
    }
    @Test void changingSettingsOnlyAffectsNewRoundsAndDoesNotRelockUnlockedTypes() {
        var state = new RunState().player(UUID.randomUUID());
        var first = state.collect("minecraft:stone", 0, 10);
        var second = state.collect("minecraft:apple", 5000, 30);
        assertEquals(10000, first.deadline()); assertFalse(second.active());
        assertEquals(30000, second.remaining(100000)); assertFalse(second.expired(100000));
        assertSame(first, state.collect("minecraft:stone", 9000, 30));
        assertEquals(first, state.expireCurrent(10000));
        assertEquals(40000, state.current().deadline());
        var retry = state.collect("minecraft:stone", 11000, 30);
        assertFalse(retry.active());
        assertTrue(state.unlockCurrent(state.current().nonce(), 12000));
        assertEquals(42000, state.current().deadline());
        assertTrue(state.unlockCurrent(state.current().nonce(), 12001));
        assertNull(state.collect("minecraft:stone", 12002, 10));
    }
    @Test void oldRulesKeepFifteenSecondsAndBothSettingsPersistIndependently() throws Exception {
        Path file = directory.resolve("rules.json");
        Files.writeString(file, "{\"requiredAccuracy\":72}");
        var rules = ServerRules.load(file);
        assertEquals(15, rules.timerSeconds); assertEquals(72, rules.requiredAccuracy);
        var timerEdit = rules.copy(); timerEdit.timerSeconds = 30; timerEdit.save(file);
        var accuracyEdit = ServerRules.load(file).copy(); accuracyEdit.requiredAccuracy = 100; accuracyEdit.save(file);
        var loaded = ServerRules.load(file);
        assertEquals(30, loaded.timerSeconds); assertEquals(100, loaded.requiredAccuracy);
        assertEquals(15, rules.timerSeconds); assertEquals(72, rules.requiredAccuracy);
        loaded.timerSeconds = 9; loaded.save(file); assertEquals(10, ServerRules.load(file).timerSeconds);
        loaded.timerSeconds = 31; loaded.save(file); assertEquals(30, ServerRules.load(file).timerSeconds);
    }
}
