package dev.sayitem.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GermanReadingGuideTest {
    @Test void contributorGuideHandlesAsciiWordsAndAppendsCharactersRatherThanNumbers() {
        assertEquals("ai", ReadingGuides.forText("Ei", "de"));
        assertEquals("ain", ReadingGuides.forText("ein", "de"));
        assertEquals("epfel", ReadingGuides.forText("Äpfel", "de"));
        assertFalse(ReadingGuides.forText("Eisen", "de").isBlank());
        assertFalse(ReadingGuides.forText("Brötchen", "de").matches(".*[0-9].*"));
        assertEquals("", ReadingGuides.forText("Stone", "en"));
    }
    @Test void diphthongsRespectKnownPrefixBoundariesWithoutBreakingNativeReinWords() {
        assertEquals(ReadingGuides.forText("re-indexieren", "de"), ReadingGuides.forText("reindexieren", "de"));
        assertTrue(ReadingGuides.forText("reindexieren", "de").startsWith("re-in"));
        assertTrue(ReadingGuides.forText("Reimport", "de").startsWith("re-im"));
        assertTrue(ReadingGuides.forText("Reintegration", "de").startsWith("re-in"));
        assertTrue(ReadingGuides.forText("reinigen", "de").startsWith("rai"));
        assertFalse(ReadingGuides.forText("reinigen", "de").contains("re-"));
        assertEquals("tseegel", ReadingGuides.forText("Ziegel", "de"));
        assertEquals("foyer", ReadingGuides.forText("Feuer", "de"));
        assertEquals("hoyzer", ReadingGuides.forText("Häuser", "de"));
    }
    @Test void commonConsonantsAndCompoundOnsetsDoNotCorruptSsOrMedialSt() {
        assertEquals("shtain", ReadingGuides.forText("Stein", "de"));
        assertEquals("shtainshtufe", ReadingGuides.forText("Steinstufe", "de"));
        assertEquals("shpits-hake", ReadingGuides.forText("Spitz-Hake", "de"));
        assertEquals("vasser", ReadingGuides.forText("Wasser", "de"));
        assertEquals("ast", ReadingGuides.forText("Ast", "de"));
        assertFalse(ReadingGuides.forText("Rüstung", "de").contains("sht"));
    }
}
