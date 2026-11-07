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
}
