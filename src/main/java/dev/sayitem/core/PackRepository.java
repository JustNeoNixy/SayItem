package dev.sayitem.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class PackRepository {
    private final Map<String, LanguagePack> packs;

    public PackRepository(Path directory) throws IOException {
        Files.createDirectories(directory);
        Map<String, LanguagePack> defaults = new HashMap<>();
        for (String id : List.of("en", "ja", "es", "fr", "de", "pl")) {
            Path file = directory.resolve(id + ".json");
            if (!Files.exists(file)) {
                try (var input = PackRepository.class.getResourceAsStream("/languages/" + id + ".json")) {
                    if (input == null) throw new IOException("Missing built-in pack: " + id);
                    Files.copy(input, file);
                }
            }
        }
        for (String id : List.of("en", "ja", "es", "fr", "de", "pl")) {
            try (var input = PackRepository.class.getResourceAsStream("/languages/" + id + ".json");
                 var reader = new java.io.InputStreamReader(Objects.requireNonNull(input), StandardCharsets.UTF_8)) {
                defaults.put(id, JsonFiles.GSON.fromJson(reader, LanguagePack.class));
            }
        }
        Map<String, LanguagePack> loaded = new TreeMap<>();
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    LanguagePack pack = JsonFiles.GSON.fromJson(reader, LanguagePack.class);
                    if (pack == null) throw new IllegalArgumentException("Empty pack");
                    LanguagePack bundled = defaults.get(pack.id());
                    if (bundled != null) {
                        var combined = new HashMap<>(bundled.items()); combined.putAll(pack.items());
                        pack = new LanguagePack(1, pack.id(), pack.name(), pack.whisperLanguage(), combined);
                    }
                    if (loaded.putIfAbsent(pack.id(), pack) != null)
                        throw new IllegalArgumentException("Duplicate pack id: " + pack.id());
                } catch (RuntimeException exception) {
                    throw new IOException("Invalid language pack " + path + ": " + exception.getMessage(), exception);
                }
            }
        }
        packs = Map.copyOf(loaded);
    }
    public LanguagePack get(String id) { return packs.get(id); }
    public Set<String> ids() { return packs.keySet(); }
    public record Phrase(LanguagePack.Entry entry, String whisperLanguage, String languageName) {}
    public Phrase phrase(String language, String item, String fallback) {
        LanguagePack pack = packs.getOrDefault(language, packs.get("ja"));
        LanguagePack.Entry entry = pack.items().get(item);
        if (entry != null) return new Phrase(entry, pack.whisperLanguage(), pack.name());
        entry = packs.get("en").items().get(item);
        if (entry == null) entry = new LanguagePack.Entry(fallback, "", List.of());
        return new Phrase(entry, "en", "English · missing " + pack.id() + " translation");
    }
}
