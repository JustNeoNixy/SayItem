package dev.sayitem.core;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RandomLanguageTest {
    @TempDir Path directory;
    @Test void retriesHudUpdatesAndReconnectsKeepTheChoiceButTheNextItemGetsADraw() {
        var run = new RunState(); var id = UUID.randomUUID(); var player = run.player(id);
        player.selectLanguage(BuiltInLanguages.RANDOM);
        var first = player.collect("minecraft:stone", 0); player.collect("minecraft:apple", 1);
        var choices = new AtomicInteger(); List<String> pool = List.of("de", "it", "pl");
        assertEquals("it", player.challengeLanguage(pool, bound -> { choices.incrementAndGet(); return 1; }));
        assertEquals("it", run.player(id).challengeLanguage(pool, bound -> { fail("Must not reroll"); return 0; }));
        player.pause(500); player.resume(3000);
        assertEquals("it", player.challengeLanguage(pool, bound -> { fail("Must not reroll"); return 0; }));
        assertTrue(player.unlockCurrent(first.nonce(), 4000));
        assertEquals("pl", player.challengeLanguage(pool, bound -> { choices.incrementAndGet(); return 2; }));
        assertEquals(2, choices.get()); assertEquals(19000, player.current().deadline());
        var oldNonce = player.current().nonce(); player.selectLanguage("fr");
        assertFalse(player.unlockCurrent(oldNonce, 4001));
        assertEquals("fr", player.challengeLanguage(pool, bound -> { fail("Fixed language"); return 0; }));
    }
    @Test void aTimeoutAndRecollectionCanChooseAgainAndEmptyPoolsUseEnglish() {
        var player = new RunState().player(UUID.randomUUID()); player.selectLanguage("random");
        player.collect("minecraft:stone", 0, 10);
        assertEquals("de", player.challengeLanguage(List.of("de", "pl"), b -> 0));
        player.expireCurrent(10000); player.collect("minecraft:stone", 10001);
        assertEquals("pl", player.challengeLanguage(List.of("de", "pl"), b -> 1));
        player.selectLanguage("en"); player.selectLanguage("random");
        assertEquals("en", player.challengeLanguage(List.of(), b -> { fail("No random draw for an empty pool"); return 0; }));
    }
    @Test void randomPoolsOnlyUsePacksThatHaveTheItemAndFallbacksReportTheirActualLanguage() throws Exception {
        var packs = new PackRepository(directory);
        JsonFiles.write(directory.resolve("eo.json"), new LanguagePack(1, "eo", "Esperanto", "eo", Map.of(
            "minecraft:apple", new LanguagePack.Entry("pomo", "", List.of()))));
        packs = new PackRepository(directory);
        assertTrue(packs.languagesFor("minecraft:apple").contains("eo"));
        assertFalse(packs.languagesFor("minecraft:stone").contains("eo"));
        assertEquals("en", packs.phrase("eo", "minecraft:stone", "Stone").language());
        assertEquals("no_no", BuiltInLanguages.LOCALES.get("no"));
        assertTrue(BuiltInLanguages.validChoice("random")); assertFalse(BuiltInLanguages.validChoice(null));
    }
}
