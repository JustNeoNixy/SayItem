package dev.sayitem.speech.local;

import com.google.gson.*;
import dev.sayitem.speech.BundledFiles;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Decode the original Whisper byte-level vocabulary; recognition never receives the challenge answer. */
final class WhisperTokenizer {
    private final Map<String, Integer> ids = new HashMap<>();
    private final Map<Integer, String> tokens = new HashMap<>();
    private final Map<Integer, Integer> bytes = new HashMap<>();
    WhisperTokenizer(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : root.getAsJsonObject("model").getAsJsonObject("vocab").entrySet())
                add(entry.getKey(), entry.getValue().getAsInt());
            for (var entry : root.getAsJsonArray("added_tokens")) {
                var value = entry.getAsJsonObject(); add(value.get("content").getAsString(), value.get("id").getAsInt());
            }
        }
        int next = 256;
        for (int b = 0; b < 256; b++) {
            boolean printable = b >= 33 && b <= 126 || b >= 161 && b <= 172 || b >= 174;
            bytes.put(printable ? b : next++, b);
        }
    }
    private void add(String text, int id) { ids.put(text, id); tokens.put(id, text); }
    int[] prefix(String language) {
        return new int[]{id("<|startoftranscript|>"), id("<|" + language + "|>"), id("<|transcribe|>"), id("<|notimestamps|>")};
    }
    private int id(String token) {
        Integer value = ids.get(token);
        if (value == null) throw new IllegalArgumentException("Unsupported speech language or token: " + token);
        return value;
    }
    String decode(int[] sequence, int count) throws IOException {
        var output = new ByteArrayOutputStream();
        for (int i = 0; i < count; i++) {
            if (sequence[i] >= id("<|endoftext|>")) continue;
            String text = tokens.get(sequence[i]);
            if (text == null) throw new IOException("Unknown speech token");
            for (int point : text.codePoints().toArray()) {
                Integer b = bytes.get(point);
                if (b == null) throw new IOException("Invalid speech token byte");
                output.write(b);
            }
        }
        return output.toString(StandardCharsets.UTF_8).trim();
    }
}
