package dev.sayitem.core;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PronunciationPackTest {
    @TempDir Path directory;
    @Test void scriptAndEstablishedSpellingVariantsKeepCorrectSpeechFromBeingRejected() throws Exception {
        var packs = new PackRepository(directory);
        assertTrue(packs.get("zh").items().get("minecraft:apple").accepts("蘋果"));
        assertTrue(packs.get("zh").items().get("minecraft:diamond").accepts("鑽石"));
        assertTrue(packs.get("el").items().get("minecraft:egg").accepts("αυγό"));
        assertFalse(packs.get("el").items().get("minecraft:egg").accepts("φυγό"));
        assertFalse(packs.get("zh").items().get("minecraft:apple").accepts("石頭"));
    }
    @Test void japaneseSwordReadingsAcceptKenAndTsurugiWhileUnrelatedItemsStayLocked() throws Exception {
        var pack = new PackRepository(directory).get("ja");
        var names = Map.of("wooden", "き", "stone", "いし", "iron", "てつ", "golden", "きん",
            "diamond", "だいやもんど", "netherite", "ねざらいと", "copper", "どう");
        for (var name : names.entrySet()) {
            var entry = pack.items().get("minecraft:" + name.getKey() + "_sword");
            assertTrue(entry.accepts(name.getValue() + "のけん"));
            assertTrue(entry.accepts(name.getValue() + "のつるぎ"));
        }
        assertEquals("ringo", pack.items().get("minecraft:apple").reading());
        assertEquals("ishi", pack.items().get("minecraft:stone").reading());
        assertEquals("tetsu no tsuruhashi", pack.items().get("minecraft:iron_pickaxe").reading());
        assertFalse(pack.items().get("minecraft:diamond_sword").accepts("だいやもんどのつるはし"));
    }
    @Test void existingPacksReceiveAliasFixesWithoutChangingCustomWordsOrTheFile() throws Exception {
        new PackRepository(directory);
        Path file = directory.resolve("ja.json");
        var previous = new LanguagePack(1, "ja", "Japanese", "ja", Map.of(
            "minecraft:diamond_sword", new LanguagePack.Entry("ダイヤモンドの剣", "daiyamondo no ken", List.of("だいやもんどのつるぎ")),
            "minecraft:apple", new LanguagePack.Entry("Custom apple", "custom reading", List.of("my apple"))));
        JsonFiles.write(file, previous); String before = Files.readString(file);
        var loaded = new PackRepository(directory).get("ja");
        assertTrue(loaded.items().get("minecraft:diamond_sword").accepts("だいやもんどのけん"));
        assertEquals(previous.items().get("minecraft:apple"), loaded.items().get("minecraft:apple"));
        assertEquals(before, Files.readString(file));
    }
    @Test void aCustomEntryWithTheMaximumNumberOfAliasesRemainsValid() throws Exception {
        new PackRepository(directory);
        var aliases = java.util.stream.IntStream.range(0, 32).mapToObj(i -> "custom alias " + i).toList();
        var custom = new LanguagePack.Entry("ダイヤモンドの剣", "my reading", aliases);
        JsonFiles.write(directory.resolve("ja.json"), new LanguagePack(1, "ja", "Japanese", "ja",
            Map.of("minecraft:diamond_sword", custom)));
        assertEquals(custom, new PackRepository(directory).get("ja").items().get("minecraft:diamond_sword"));
    }
}
