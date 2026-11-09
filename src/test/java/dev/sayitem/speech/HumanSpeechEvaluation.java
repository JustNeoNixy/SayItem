package dev.sayitem.speech;

import dev.speechlib.*;

import com.google.gson.*;
import dev.sayitem.core.*;
import java.io.BufferedInputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioSystem;

/** Human recordings through the real mod path. Sources and limited coverage stay explicit. */
public final class HumanSpeechEvaluation {
    public static void main(String[] args) throws Exception {
        Path manifest = Path.of(args[1]), audioDirectory = Path.of(args[2]), cache = Path.of(args[3]), output = Path.of(args[4]);
        var sources = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        var cases = sources.getAsJsonArray("cases").deepCopy();
        if (sources.has("supplementalManifest")) {
            Path extra = manifest.resolveSibling(sources.get("supplementalManifest").getAsString()).normalize();
            if (!extra.startsWith(manifest.getParent())) throw new IllegalArgumentException("Invalid supplemental source path");
            cases.addAll(JsonParser.parseString(Files.readString(extra)).getAsJsonObject().getAsJsonArray("cases"));
        }
        var packs = new PackRepository(cache.resolve("packs"));
        var rows = new ArrayList<Map<String, Object>>();
        for (String id : args[0].split(",")) {
            var profile = SpeechProfile.parse(id);
            try (var engine = SpeechLib.builder().cacheDirectory(cache).profile(profile).build()) {
                engine.initialize().get(300, TimeUnit.SECONDS);
                if (profile.gpu() && engine.backend() == dev.speechlib.SpeechBackend.CPU) throw new IllegalStateException("GPU test fell back: " + engine.status());
                System.out.println(engine.status());
                try (var input = HumanSpeechEvaluation.class.getResourceAsStream("/audio/diamond-sword.wav");
                     var audio = AudioSystem.getAudioInputStream(new BufferedInputStream(Objects.requireNonNull(input)))) {
                    engine.transcribe(AudioSamples.fromPcm16(audio.readAllBytes()), "en").get(60, TimeUnit.SECONDS);
                }
                int count = 0;
                for (var value : cases) {
                    var source = value.getAsJsonObject();
                    var row = new LinkedHashMap<String, Object>();
                    String language = source.get("language").getAsString();
                    String item = source.get("item").isJsonNull() ? null : source.get("item").getAsString();
                    String name = source.get("id").getAsString();
                    row.put("id", name); row.put("language", language); row.put("item", item); row.put("profile", profile.id());
                    row.put("kind", item == null ? "general_speech" : "item_name");
                    Path path = audioDirectory.resolve(source.get("wav").getAsString()).normalize();
                    if (!path.startsWith(audioDirectory)) throw new IllegalArgumentException("Invalid audio path");
                    float[] samples;
                    try (var audio = AudioSystem.getAudioInputStream(path.toFile())) {
                        if (audio.getFormat().getSampleRate() != 16000 || audio.getFormat().getChannels() != 1 || audio.getFormat().getSampleSizeInBits() != 16)
                            throw new IllegalArgumentException("Expected 16 kHz mono PCM16");
                        samples = AudioSamples.fromPcm16(audio.readAllBytes());
                    }
                    row.put("audio_ms", samples.length / 16);
                    if (samples.length < 3200 || samples.length > AudioSamples.MAX_SAMPLES) row.put("skip", "Whole recording outside mod's recording limit");
                    else {
                        long started = System.nanoTime();
                        String heard = engine.transcribe(samples, language).get(90, TimeUnit.SECONDS);
                        row.put("elapsed_ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
                        var expected = item == null ? new LanguagePack.Entry(source.get("expected").getAsString(), "", List.of()) : packs.get(language).items().get(item);
                        row.put("expected", expected.text()); row.put("heard", heard);
                        double score = AnswerMatcher.score(heard, expected.text(), expected.aliases());
                        row.put("match_percent", Math.round(score*1000)/10.0); row.put("accepted", AccuracyRequirement.accepts(score, 90));
                        var wrong = new ArrayList<String>();
                        for (var entry : packs.get(language).items().entrySet())
                            if (!entry.getKey().equals(item) && !entry.getValue().text().equals(expected.text())
                                && AccuracyRequirement.accepts(AnswerMatcher.score(heard, entry.getValue().text(), entry.getValue().aliases()), 90)) wrong.add(entry.getKey());
                        row.put("other_matching_items", wrong);
                    }
                    rows.add(row); JsonFiles.write(output, rows);
                    System.out.println(profile.id() + " " + (++count) + "/" + cases.size());
                }
            }
        }
    }
}
