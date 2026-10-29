package dev.sayitem.core;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Deliberately matches whole answers: stone must never unlock redstone. */
public final class AnswerMatcher {
    private AnswerMatcher() {}

    public static String normalize(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT)
                .replace("ł", "l").replace("œ", "oe").replace("æ", "ae");
        StringBuilder result = new StringBuilder();
        boolean latinBase = false;
        for (int point : decomposed.codePoints().toArray()) {
            int type = Character.getType(point);
            boolean mark = type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK;
            if (mark) {
                // Accent tolerance is useful for Latin answers; kana voicing is part of the word.
                if (!latinBase) result.appendCodePoint(point);
                continue;
            }
            latinBase = Character.UnicodeScript.of(point) == Character.UnicodeScript.LATIN;
            if (Character.isLetterOrDigit(point)) result.appendCodePoint(point);
        }
        return result.toString();
    }

    public static boolean matches(String transcript, String nativeText, List<String> aliases) {
        return score(transcript, nativeText, aliases) >= 0.9;
    }

    public static double score(String transcript, String nativeText, List<String> aliases) {
        if (transcript == null || transcript.length() > 256) return 0;
        String answer = normalize(transcript);
        if (answer.isEmpty()) return 0;
        double best = similarity(answer, normalize(nativeText));
        for (String alias : aliases) best = Math.max(best, similarity(answer, normalize(alias)));
        return best;
    }

    /** Unicode-code-point Levenshtein similarity, normalized by the longer answer. */
    private static double similarity(String answer, String expected) {
        int[] a = answer.codePoints().toArray(), b = expected.codePoints().toArray();
        if (a.length == 0 || b.length == 0) return 0;
        int[] previous = new int[b.length + 1];
        for (int j = 0; j <= b.length; j++) previous[j] = j;
        for (int i = 1; i <= a.length; i++) {
            int[] row = new int[b.length + 1]; row[0] = i;
            for (int j = 1; j <= b.length; j++) row[j] = Math.min(Math.min(row[j - 1] + 1, previous[j] + 1),
                    previous[j - 1] + (a[i - 1] == b[j - 1] ? 0 : 1));
            previous = row;
        }
        return 1.0 - (double) previous[b.length] / Math.max(a.length, b.length);
    }
}
