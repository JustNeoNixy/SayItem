package dev.sayitem.speech;

import dev.sayitem.core.*;
import java.io.BufferedInputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioSystem;

/** Benchmarks the same Java/native path shipped in the mod; no Python or companion process. */
public final class SpeechBenchmark {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[1]), cache = Path.of(args[2]);
        var packs = new PackRepository(cache.resolve("packs"));
        var cases = new LinkedHashMap<String, String>(); cases.put("diamond-sword", "en");
        for (String word : List.of("stone", "cobblestone", "diamond-sword", "oak-planks", "iron-pickaxe", "apple", "unrelated")) cases.put("pl-"+word, "pl");
        for (String language : List.of("bg", "ru")) for (String word : List.of("stone", "apple", "diamond-sword", "iron-pickaxe", "unrelated")) cases.put(language+"-"+word, language);
        var rows = new ArrayList<Map<String, Object>>();
        for (String id : args[0].split(",")) {
            var profile = SpeechProfile.parse(id);
            try (var engine = new LocalWhisper(cache, profile)) {
                engine.initialize().get(120, TimeUnit.SECONDS);
                if (profile.gpu() && !engine.status().contains("Vulkan")) throw new IllegalStateException(engine.status());
                System.out.println(engine.status());
                engine.transcribe(samples("diamond-sword"), "en").get(30, TimeUnit.SECONDS);
                for (var entry : cases.entrySet()) for (int repeat=0;repeat<2;repeat++) {
                    float[] audio = samples(entry.getKey()); long started = System.nanoTime();
                    String heard = engine.transcribe(audio, entry.getValue()).get(60, TimeUnit.SECONDS);
                    long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
                    String item = entry.getKey().startsWith(entry.getValue()+"-") ? entry.getKey().substring(entry.getValue().length()+1) : entry.getKey();
                    var expected = packs.get(entry.getValue()).items().get("minecraft:"+item.replace('-', '_'));
                    var row = new LinkedHashMap<String, Object>(); row.put("profile", profile.id()); row.put("clip", entry.getKey());
                    row.put("repeat", repeat); row.put("text", heard); row.put("elapsed_ms", elapsed);
                    if (expected != null) row.put("match_percent", Math.round(1000*AnswerMatcher.score(heard, expected.text(), expected.aliases()))/10.0);
                    rows.add(row); System.out.println(JsonFiles.GSON.toJson(row)); JsonFiles.write(output, rows);
                }
            }
        }
    }
    private static float[] samples(String name) throws Exception {
        try (var input = SpeechBenchmark.class.getResourceAsStream("/audio/"+name+".wav");
             var audio = AudioSystem.getAudioInputStream(new BufferedInputStream(Objects.requireNonNull(input)))) {
            return AudioSamples.fromPcm16(audio.readAllBytes());
        }
    }
}
