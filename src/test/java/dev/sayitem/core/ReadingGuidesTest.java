package dev.sayitem.core;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReadingGuidesTest {
    @TempDir Path directory;
    @Test void dedicatedServersCanGenerateReadingsWithoutClientIcuLibraries() throws Exception {
        var classes = ReadingGuides.class.getProtectionDomain().getCodeSource().getLocation();
        try (var isolated = new java.net.URLClassLoader(new java.net.URL[]{classes}, ClassLoader.getPlatformClassLoader())) {
            var type = isolated.loadClass("dev.sayitem.core.ReadingGuides");
            var convert = type.getMethod("forText", String.class, String.class);
            assertEquals("kamuhk", convert.invoke(null, "Камък", "bg"));
            assertEquals("almaznyy mech", convert.invoke(null, "Алмазный меч", "ru"));
            assertEquals("Zelazny kilof", convert.invoke(null, "Żelazny kilof", "pl"));
            assertEquals("", convert.invoke(null, "πέτρα", "el"));
        }
    }
    @Test void cyrillicPacksHaveLatinReadingsForEveryItemWithoutChangingAcceptedAnswers() throws Exception {
        var packs = new PackRepository(directory);
        for (String language : List.of("ru", "bg")) {
            for (var entry : packs.get(language).items().values()) {
                if (entry.text().codePoints().noneMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.CYRILLIC)) continue;
                assertFalse(entry.reading().isBlank(), entry.text());
                assertTrue(entry.reading().codePoints().noneMatch(c -> Character.isLetter(c) && c > 127), entry.reading());
                assertFalse(entry.accepts(entry.reading()), entry.text());
            }
        }
        assertEquals("almaznyy mech", packs.get("ru").items().get("minecraft:diamond_sword").reading());
        assertEquals("kamuhk", packs.get("bg").items().get("minecraft:stone").reading());
        assertEquals("shch yo kh y", ReadingGuides.forText("Щ ё х ы", "ru"));
        assertEquals("sht uh zh", ReadingGuides.forText("Щ ъ ж", "bg"));
    }
    @Test void customPacksCanOverrideReadingsOrUseOtherScriptsWithoutChangingTheirFiles() throws Exception {
        new PackRepository(directory);
        Path file = directory.resolve("ru.json");
        JsonFiles.write(file, new LanguagePack(1, "ru", "Russian", "ru", Map.of(
            "minecraft:stone", new LanguagePack.Entry("Камень", "KAH-men", List.of()),
            "minecraft:apple", new LanguagePack.Entry("Яблоко", "", List.of()))));
        String before = Files.readString(file);
        var pack = new PackRepository(directory).get("ru");
        assertEquals("KAH-men", pack.items().get("minecraft:stone").reading());
        assertEquals("yabloko", pack.items().get("minecraft:apple").reading());
        assertEquals(before, Files.readString(file));
        for (var sample : Map.of("el", "πέτρα", "ko", "사과", "zh", "苹果", "ar", "تفاحة").entrySet()) {
            String reading = ReadingGuides.forText(sample.getValue(), sample.getKey());
            assertFalse(reading.isBlank(), sample.getKey());
            assertTrue(reading.codePoints().noneMatch(c -> Character.isLetter(c) && c > 127));
        }
        assertEquals("", ReadingGuides.forText("Stone", "en"));
        assertEquals("Zelazny kilof", ReadingGuides.forText("Żelazny kilof", "pl"));
        assertEquals("", ReadingGuides.forText("я".repeat(128), "ru"));
        assertEquals("", ReadingGuides.forText("未知の漢字", "ja"));
        JsonFiles.write(directory.resolve("ja.json"), new LanguagePack(1, "ja", "Japanese", "ja", Map.of(
            "minecraft:stone", new LanguagePack.Entry("石", "", List.of()))));
        assertEquals("ishi", new PackRepository(directory).get("ja").items().get("minecraft:stone").reading());
    }
}
