package dev.sayitem.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AccuracyRequirementTest {
    @TempDir Path directory;
    @Test void everySupportedBoundaryIsAppliedWithoutWeakeningPerfectMatch() {
        for (int requirement = 60; requirement <= 100; requirement++) {
            assertTrue(AccuracyRequirement.accepts(requirement / 100.0, requirement));
            assertFalse(AccuracyRequirement.accepts((requirement - 1) / 100.0, requirement));
        }
        assertFalse(AccuracyRequirement.accepts(0.999, 100));
        assertThrows(IllegalArgumentException.class, () -> AccuracyRequirement.accepts(1, 59));
        assertThrows(IllegalArgumentException.class, () -> AccuracyRequirement.accepts(1, 101));
    }
    @Test void difficultyPersistsAcrossServerRestartsAndBadRangesAreClamped() throws Exception {
        Path file = directory.resolve("rules.json");
        assertEquals(90, ServerRules.load(file).requiredAccuracy);
        var rules = new ServerRules(); rules.requiredAccuracy = 73; rules.save(file);
        assertEquals(73, ServerRules.load(file).requiredAccuracy);
        rules.requiredAccuracy = 20; rules.save(file);
        assertEquals(60, ServerRules.load(file).requiredAccuracy);
        rules.requiredAccuracy = 200; rules.save(file);
        assertEquals(100, ServerRules.load(file).requiredAccuracy);
    }
    @Test void switchingLanguagesInvalidatesStaleSpeechWithoutExtendingTheTimerOrRelockingItems() {
        var state = new RunState().player(java.util.UUID.randomUUID());
        var apple = state.collect("minecraft:apple", 0);
        assertTrue(state.unlockCurrent(apple.nonce(), 1));
        var before = state.collect("minecraft:stone", 1000);
        state.selectLanguage("pl");
        var after = state.current();
        assertNotEquals(before.nonce(), after.nonce());
        assertEquals(before.deadline(), after.deadline());
        assertTrue(state.unlocked.contains("minecraft:apple"));
        state.selectLanguage("pl");
        assertEquals(after.nonce(), state.current().nonce());
    }
}
