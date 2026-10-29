package dev.sayitem.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SayItemCoreTest {
    @TempDir Path temp;

    @Test void acceptsCasePunctuationAndLatinAccentsButNotSubstrings() {
        assertTrue(AnswerMatcher.matches(" ¡CARBÓN! ", "carbón", List.of()));
        assertTrue(AnswerMatcher.matches("kamien", "kamień", List.of()));
        assertTrue(AnswerMatcher.matches("boeuf cru", "bœuf cru", List.of()));
        assertFalse(AnswerMatcher.matches("redstone", "stone", List.of()));
        assertFalse(AnswerMatcher.matches("a stone", "stone", List.of()));
        assertFalse(AnswerMatcher.matches("", "stone", List.of()));
        assertFalse(AnswerMatcher.matches(null, "stone", List.of()));
        assertFalse(AnswerMatcher.matches("!", "stone", List.of()));
        assertFalse(AnswerMatcher.matches("x".repeat(257), "stone", List.of()));
    }
    @Test void preservesJapaneseVoicingAndUsesExplicitKanaAliases() {
        assertFalse(AnswerMatcher.matches("か", "が", List.of()));
        assertTrue(AnswerMatcher.matches("カ\u3099ラス。", "ガラス", List.of()));
        assertTrue(AnswerMatcher.matches("りんご。", "リンゴ", List.of("りんご", "林檎")));
        assertFalse(AnswerMatcher.matches("ringo", "リンゴ", List.of("りんご", "林檎")));
    }
    @Test void builtInPacksHaveMatchingCoverageAndJapaneseReadings() throws Exception {
        PackRepository repository = new PackRepository(temp.resolve("packs"));
        assertEquals(Set.of("en", "ja", "es", "fr", "de", "pl"), repository.ids());
        LanguagePack japanese = repository.get("ja");
        assertEquals(1657, japanese.items().size());
        for (String id : repository.ids()) {
            assertEquals(japanese.items().keySet(), repository.get(id).items().keySet());
            for (var entry : repository.get(id).items().values()) assertTrue(entry.accepts(entry.text()));
        }
        assertTrue(japanese.items().values().stream().allMatch(e -> !e.reading().isBlank()));
        assertTrue(japanese.items().get("minecraft:apple").accepts("りんご。"));
    }
    @Test void customPacksAreLoadedAndNeverOverwritten() throws Exception {
        Path directory = temp.resolve("packs");
        PackRepository initial = new PackRepository(directory);
        var entry = new LanguagePack.Entry("mela", "", List.of("una mela"));
        JsonFiles.write(directory.resolve("it.json"), new LanguagePack(1, "it", "Italiano", "it", Map.of("minecraft:apple", entry)));
        assertTrue(new PackRepository(directory).get("it").items().get("minecraft:apple").accepts("una mela"));
        assertEquals(initial.get("ja"), new PackRepository(directory).get("ja"));
    }
    @Test void invalidPackFailsWithItsFilename() throws Exception {
        Path directory = temp.resolve("packs");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("broken.json"), "{\"schemaVersion\":2}");
        var error = assertThrows(java.io.IOException.class, () -> new PackRepository(directory));
        assertTrue(error.getMessage().contains("broken.json"));
    }

    @Test void ninetyPercentBoundaryUsesUnroundedScore() {
        assertEquals(0.9, AnswerMatcher.score("abcdefghix", "abcdefghij", List.of()), 0.000001);
        assertTrue(AnswerMatcher.matches("abcdefghix", "abcdefghij", List.of()));
        assertFalse(AnswerMatcher.matches("abcdefgxxx", "abcdefghij", List.of()));
        assertFalse(AnswerMatcher.matches("ston", "stone", List.of()));
        assertEquals(1, AnswerMatcher.score("りんご", "リンゴ", List.of("りんご")));
    }

    @Test void timerStartsAtCollectionAndCannotBeExtendedByRetries() {
        var state = new RunState().player(UUID.randomUUID());
        var first = state.collect("minecraft:stone", 1000);
        assertEquals(16000, first.deadline());
        assertSame(first, state.collect("minecraft:stone", 14000));
        assertFalse(first.expired(15999));
        assertTrue(first.expired(16000));
        assertEquals(0, first.remaining(17000));
        state.rounds.remove(first.item());
        assertEquals(32000, state.collect(first.item(), 17000).deadline());
    }

    @Test void unlocksLastForRunAcrossLanguagesButNotPlayersOrNewRuns() {
        var run = new RunState(); UUID alice = UUID.randomUUID();
        var state = run.player(alice);
        state.collect("minecraft:stone", 0); state.unlock("minecraft:stone");
        state.language = "es";
        assertNull(state.collect("minecraft:stone", 100));
        assertTrue(state.rounds.isEmpty());
        assertNotNull(run.player(UUID.randomUUID()).collect("minecraft:stone", 100));
        assertNotNull(new RunState().player(alice).collect("minecraft:stone", 100));
    }

    @Test void missingCustomTranslationUsesEnglishButNeverBypassesALock() throws Exception {
        var packs = new PackRepository(temp.resolve("packs"));
        JsonFiles.write(temp.resolve("packs/it.json"), new LanguagePack(1, "it", "Italiano", "it",
                Map.of("minecraft:apple", new LanguagePack.Entry("mela", "", List.of()))));
        packs = new PackRepository(temp.resolve("packs"));
        assertEquals("en", packs.phrase("it", "minecraft:stone", "Stone").whisperLanguage());
        assertEquals("Stone", packs.phrase("it", "minecraft:stone", "Stone").entry().text());
    }
}
