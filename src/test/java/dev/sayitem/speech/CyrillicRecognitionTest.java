package dev.sayitem.speech;

import dev.speechlib.*;

import dev.sayitem.core.AnswerMatcher;
import java.io.BufferedInputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CyrillicRecognitionTest {
    @TempDir Path cache;
    @Test void recognizesBulgarianAndRussianPhrasesAndRejectsUnrelatedSpeech() throws Exception {
        // The Base model can mishear Russian iron-pickaxe speech below 90% on
        // Windows. Keep that quality limitation in speech-accuracy.json, rather
        // than relaxing matching or treating it as a native integration failure.
        var words = Map.of("bg", Map.entry("iron-pickaxe", "Желязна кирка"),
                           "ru", Map.entry("diamond-sword", "Алмазный меч"));
        try (var engine = SpeechLib.builder().cacheDirectory(cache).build()) {
            engine.initialize().get(60, TimeUnit.SECONDS);
            for (var language : words.entrySet()) {
                String heard = engine.transcribe(samples(language.getKey() + "-" + language.getValue().getKey()), language.getKey()).get(60, TimeUnit.SECONDS);
                System.out.println(language.getKey() + ": " + heard);
                assertTrue(AnswerMatcher.matches(heard, language.getValue().getValue(), List.of()), language.getKey() + ": " + heard);
                String unrelated = engine.transcribe(samples(language.getKey() + "-unrelated"), language.getKey()).get(60, TimeUnit.SECONDS);
                assertFalse(AnswerMatcher.matches(unrelated, language.getValue().getValue(), List.of()), unrelated);
            }
        }
    }
    private float[] samples(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/audio/" + name + ".wav");
             var audio = AudioSystem.getAudioInputStream(new BufferedInputStream(Objects.requireNonNull(input)))) {
            assertEquals(16000, audio.getFormat().getSampleRate());
            return AudioSamples.fromPcm16(audio.readAllBytes());
        }
    }
}
