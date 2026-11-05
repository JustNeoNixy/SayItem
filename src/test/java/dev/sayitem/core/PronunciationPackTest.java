package dev.sayitem.core;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PronunciationPackTest {
    @TempDir Path directory;
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
}
