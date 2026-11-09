package dev.sayitem.speech;

import dev.speechlib.*;

import dev.sayitem.core.AnswerMatcher;
import java.io.BufferedInputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="speechlib.gpu.tests", matches="true")
class VulkanRecognitionTest {
    @TempDir Path cache;
    @Test void allGpuProfilesRunLocallyAndRecoverAfterCancellation() throws Exception {
        for (var profile : List.of(SpeechProfile.GPU_BASE, SpeechProfile.GPU_SMALL, SpeechProfile.GPU_TURBO)) {
            try (var engine = SpeechLib.builder().cacheDirectory(cache).profile(profile).build()) {
                engine.initialize().get(120, TimeUnit.SECONDS);
                assertTrue(engine.backend() != dev.speechlib.SpeechBackend.CPU, engine.status());
                assertFalse(engine.status().contains("using CPU"), engine.status());
                assertTrue(AnswerMatcher.matches(engine.transcribe(samples("diamond-sword"), "en").get(30, TimeUnit.SECONDS), "Diamond sword", List.of()));
                if (profile == SpeechProfile.GPU_SMALL)
                    assertTrue(AnswerMatcher.matches(engine.transcribe(samples("pl-stone"), "pl").get(30, TimeUnit.SECONDS), "Kamień", List.of()));
                String unrelated = engine.transcribe(samples("pl-unrelated"), "pl").get(30, TimeUnit.SECONDS);
                assertFalse(AnswerMatcher.matches(unrelated, "Diamentowy miecz", List.of()));
                assertEquals("", engine.transcribe(new float[16000], "pl").get(5, TimeUnit.SECONDS));
                // A late utterance in a full-length recording must reach the recognizer.
                float[] tail = samples("diamond-sword"), full = new float[AudioSamples.MAX_SAMPLES];
                System.arraycopy(tail, 0, full, full.length-tail.length, tail.length);
                assertTrue(AnswerMatcher.matches(engine.transcribe(full, "en").get(30, TimeUnit.SECONDS), "Diamond sword", List.of()));
                float[] original = samples("diamond-sword"), longer = new float[Math.min(96000, original.length*3)];
                for (int i=0;i<longer.length;i++) longer[i] = original[i%original.length];
                var cancelled = engine.transcribe(longer, "en"); Thread.sleep(100); cancelled.cancel(false);
                engine.whenIdle().get(20, TimeUnit.SECONDS);
                assertTrue(cancelled.isCancelled());
                assertTrue(AnswerMatcher.matches(engine.transcribe(original, "en").get(30, TimeUnit.SECONDS), "Diamond sword", List.of()));
            }
        }
    }
    private static float[] samples(String name) throws Exception {
        try (var input = VulkanRecognitionTest.class.getResourceAsStream("/audio/"+name+".wav");
             var audio = AudioSystem.getAudioInputStream(new BufferedInputStream(Objects.requireNonNull(input)))) {
            return AudioSamples.fromPcm16(audio.readAllBytes());
        }
    }
}
