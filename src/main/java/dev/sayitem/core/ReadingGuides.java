package dev.sayitem.core;

import com.ibm.icu.text.Transliterator;
import java.util.Locale;

/** Display-only Latin readings. These never become accepted speech answers. */
public final class ReadingGuides {
    private ReadingGuides() {}
    // Only the Minecraft client supplies ICU. Keep it behind a lazy nested class
    // so dedicated servers can produce Cyrillic/Latin guides without that library.
    private static final class Universal {
        static final ThreadLocal<Transliterator> LATIN = ThreadLocal.withInitial(
            () -> Transliterator.getInstance("Any-Latin; Latin-ASCII"));
    }
    private static final String CYRILLIC = "абвгдеёжзийклмнопрстуфхцчшщъыьэюяѝ";
    private static final String[] RUSSIAN = {
        "a", "b", "v", "g", "d", "e", "yo", "zh", "z", "i", "y", "k", "l", "m", "n",
        "o", "p", "r", "s", "t", "u", "f", "kh", "ts", "ch", "sh", "shch", "'", "y", "'", "e", "yu", "ya", "i"
    };
    private static final String GERMAN_VOWELS = "aeiouäöü";

    public static String forText(String text, String language) {
        return forText(text, language, true);
    }
    private static String forText(String text, String language, boolean universal) {
        if (!language.equals("de") && text.codePoints().noneMatch(c -> Character.isLetter(c) && c > 127)) return "";
        // Generic Han transliteration is Chinese, not Japanese. Japanese packs
        // supply contextual romaji for kanji instead of guessing a Chinese reading.
        if (language.equals("ja") && text.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN)) return "";
        String source = text;
        int[] lowerCodePoints = source.toLowerCase(Locale.ROOT).codePoints().toArray();
        if (language.equals("ru") || language.equals("bg")) {
            var latin = new StringBuilder();
            for (int codePoint : lowerCodePoints) {
                int index = CYRILLIC.indexOf(codePoint);
                if (index < 0) latin.appendCodePoint(codePoint);
                else if (language.equals("bg") && codePoint == 'щ') latin.append("sht");
                else if (language.equals("bg") && codePoint == 'ъ') latin.append("uh");
                else latin.append(RUSSIAN[index]);
            }
            source = latin.toString();
        }

        lowerCodePoints = source.toLowerCase(Locale.ROOT).codePoints().toArray();
        if (language.equals("de")) {
            var pronunciation = new StringBuilder();
            for (int i = 0; i < lowerCodePoints.length; i++) {
                int codePoint = lowerCodePoints[i];
                boolean hasNext = i + 1 < lowerCodePoints.length;

                if (codePoint == 'ä') pronunciation.append("e");
		else if (codePoint == 'ü') pronunciation.append("u(e)");
		else if (codePoint == 'ö') pronunciation.append("o(e)");
		else if (codePoint == 'z') pronunciation.append("ts");
		else if (codePoint == 'e') {
                    if (hasNext && lowerCodePoints[i + 1] == 'i') {
                        pronunciation.append("a");
		    } else if (i > 0 && lowerCodePoints[i - 1] == 'i') {
                        pronunciation.append("i");
                    } else {
                        pronunciation.append("e");
		    }
		}
		else if (codePoint == 'j') {
                    if (hasNext && GERMAN_VOWELS.indexOf(lowerCodePoints[i + 1]) >= 0) {
                        pronunciation.append("y");
                    } else {
                        pronunciation.append("j");
		    }
		}
		else if (codePoint == 's') {
                    if (hasNext && GERMAN_VOWELS.indexOf(lowerCodePoints[i + 1]) >= 0) {
                        pronunciation.append("z");
                    } else {
                        pronunciation.append("s");
		    }
		} else pronunciation.appendCodePoint(codePoint);
            }
            source = pronunciation.toString();
        }

        String reading = java.text.Normalizer.normalize(source, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").replace("ł", "l").replace("Ł", "L")
            .replace("ß", "ss").replace("œ", "oe").replace("Œ", "Oe")
            .replace("æ", "ae").replace("Æ", "Ae").strip();

        if (reading.codePoints().anyMatch(c -> Character.isLetter(c) && c > 127)) {
            if (!universal) return "";
            try { reading = Universal.LATIN.get().transliterate(source).strip(); }
            catch (LinkageError | IllegalArgumentException unavailable) { return ""; }
        }
        // Keep the existing reading/packet length contract. Pack authors can supply
        // a shorter, more precise reading when automatic conversion cannot fit.
        if (reading.isBlank() || reading.equals(text) || reading.length() > 128
            || reading.codePoints().anyMatch(c -> Character.isLetter(c) && c > 127)) return "";
        return reading;
    }

    static LanguagePack fillMissing(LanguagePack pack) {
        var items = new java.util.HashMap<String, LanguagePack.Entry>();
        pack.items().forEach((id, entry) -> items.put(id, entry.reading().isBlank()
            ? new LanguagePack.Entry(entry.text(), forText(entry.text(), pack.whisperLanguage(), false), entry.aliases()) : entry));
        return new LanguagePack(pack.schemaVersion(), pack.id(), pack.name(), pack.whisperLanguage(), items);
    }
}
