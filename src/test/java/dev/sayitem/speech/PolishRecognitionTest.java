package dev.sayitem.speech;

import dev.speechlib.*;

import dev.sayitem.core.AnswerMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class PolishRecognitionTest {
    @TempDir Path cache;

    @Test void handlesRecordedPolishWithTheConfiguredRequirementAndRejectsOtherItems() throws Exception {
        Map<String, String> phrases = new LinkedHashMap<>();
        phrases.put("stone", "Kamień"); phrases.put("cobblestone", "Bruk");
        phrases.put("diamond-sword", "Diamentowy miecz"); phrases.put("oak-planks", "Dębowe deski");
        phrases.put("iron-pickaxe", "Żelazny kilof"); phrases.put("apple", "Jabłko");
        List<String> failures = new ArrayList<>();
        try (var engine = SpeechLib.builder().cacheDirectory(cache).build()) {
            engine.initialize().get(60, TimeUnit.SECONDS);
            System.out.println(engine.status());
            for (var phrase : phrases.entrySet()) {
                long started = System.nanoTime();
                String answer = engine.transcribe(samples(phrase.getKey()), "pl").get(60, TimeUnit.SECONDS);
                long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                System.out.println(phrase.getValue() + " -> " + answer + " (" + elapsed + "ms)");
                int requirement = phrase.getKey().equals("diamond-sword") ? 90 : 60;
                if (!dev.sayitem.core.AccuracyRequirement.accepts(AnswerMatcher.score(answer, phrase.getValue(), List.of()), requirement))
                    failures.add(phrase.getValue() + " -> " + answer);
                for (String other : phrases.values()) if (!other.equals(phrase.getValue()))
                    assertFalse(dev.sayitem.core.AccuracyRequirement.accepts(AnswerMatcher.score(answer, other, List.of()), requirement), "A different item must stay locked: " + answer);
            }
            String unrelated = engine.transcribe(samples("unrelated"), "pl").get(60, TimeUnit.SECONDS);
            for (String expected : phrases.values()) assertFalse(AnswerMatcher.matches(unrelated, expected, List.of()), unrelated);
        }
        assertTrue(failures.isEmpty(), String.join("; ", failures));
    }

    private float[] samples(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/audio/pl-" + name + ".wav");
             var audio = AudioSystem.getAudioInputStream(new BufferedInputStream(Objects.requireNonNull(input)))) {
            assertEquals(16000, audio.getFormat().getSampleRate());
            return AudioSamples.fromPcm16(audio.readAllBytes());
        }
    }
}
