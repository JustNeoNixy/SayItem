package dev.sayitem.core;

import java.util.List;
import java.util.Map;

public record LanguagePack(int schemaVersion, String id, String name, String whisperLanguage,
                           Map<String, Entry> items) {
    public record Entry(String text, String reading, List<String> aliases) {
        public Entry {
            if (text == null || text.isBlank() || text.length() > 128)
                throw new IllegalArgumentException("Entry text must contain 1–128 characters");
            reading = reading == null ? "" : reading;
            if (reading.length() > 128) throw new IllegalArgumentException("Reading is too long");
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            if (aliases.size() > 32 || aliases.stream().anyMatch(a -> a.isBlank() || a.length() > 128))
                throw new IllegalArgumentException("Invalid aliases");
        }
        public boolean accepts(String transcript) { return AnswerMatcher.matches(transcript, text, aliases); }
    }

    public LanguagePack {
        if (schemaVersion != 1) throw new IllegalArgumentException("Expected schemaVersion 1");
        if (id == null || id.length() > 32 || !id.matches("[a-z]{2,3}(-[a-z0-9]+)?"))
            throw new IllegalArgumentException("Invalid language pack id");
        if (name == null || name.isBlank() || name.length() > 64)
            throw new IllegalArgumentException("Invalid language name");
        if (whisperLanguage == null || !whisperLanguage.matches("[a-z]{2,3}"))
            throw new IllegalArgumentException("Invalid Whisper language code");
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Pack has no items");
        items.forEach((key, entry) -> {
            if (!key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || entry == null)
                throw new IllegalArgumentException("Invalid item: " + key);
        });
        items = Map.copyOf(items);
    }
}
