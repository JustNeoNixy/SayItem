package dev.sayitem.speech;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import javax.sound.sampled.AudioSystem;

class LocalWhisperTest {
    @TempDir Path cache;
    @Test void initializesActualBundledNativeEngineAndRejectsSilence() throws Exception {
        try (var engine = new LocalWhisper(cache)) {
            engine.initialize().get(60, TimeUnit.SECONDS);
            assertTrue(engine.isReady(), engine.status());
            assertEquals("", engine.transcribe(new float[16000], "ja").get(30, TimeUnit.SECONDS));
            try (var input = LocalWhisperTest.class.getResourceAsStream("/audio/diamond-sword.wav");
                 var audio = AudioSystem.getAudioInputStream(new java.io.BufferedInputStream(input))) {
                assertEquals(16000, audio.getFormat().getSampleRate());
                float[] samples = AudioSamples.fromPcm16(audio.readAllBytes());
                long started = System.nanoTime();
                String answer = engine.transcribe(samples, "en").get(90, TimeUnit.SECONDS);
                assertTrue(answer.toLowerCase(java.util.Locale.ROOT).contains("diamond"), answer);
                assertTrue(answer.toLowerCase(java.util.Locale.ROOT).contains("sword"), answer);
                assertTrue(dev.sayitem.core.AnswerMatcher.matches(answer, "Diamond sword", java.util.List.of()), answer);
                System.out.println("Bundled in-process Whisper recognized synthetic item speech in "
                    + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) + "ms");
                var obsolete = engine.transcribe(samples, "en");
                long waitUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!engine.isRecognizing() && System.nanoTime() < waitUntil) Thread.sleep(5);
                assertTrue(engine.isRecognizing());
                Thread.sleep(75);
                long cancelledAt = System.nanoTime();
                assertTrue(obsolete.cancel(false));
                engine.whenIdle().get(3, TimeUnit.SECONDS);
                assertTrue(engine.nativeAborts() > 0, "Cancellation must reach the native computation");
                System.out.println("Cancelled native speech job drained in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - cancelledAt) + "ms");
                String next = engine.transcribe(samples, "en").get(60, TimeUnit.SECONDS);
                assertTrue(dev.sayitem.core.AnswerMatcher.matches(next, "Diamond sword", java.util.List.of()), next);
            }
            assertTrue(engine.transcribe(new float[10], "ja").isCompletedExceptionally());
        }
    }
}
