package dev.sayitem.speech.local;

import com.google.gson.*;
import dev.sayitem.speech.AudioSamples;
import javax.sound.sampled.AudioSystem;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SpeechPreprocessingTest {
    @TempDir Path directory;
    @Test void javaLogMelMatchesThePreviousPythonFeatureExtractor() throws Exception {
        float[] actual;
        try (var stream = getClass().getResourceAsStream("/audio/diamond-sword.wav");
             var audio = AudioSystem.getAudioInputStream(new BufferedInputStream(stream))) {
            actual = MelSpectrogram.compute(AudioSamples.fromPcm16(audio.readAllBytes()));
        }
        var golden = json("mel-reference.json");
        for (var sample : golden.getAsJsonArray("samples")) {
            var point = sample.getAsJsonObject();
            int index = point.get("band").getAsInt() * 3000 + point.get("frame").getAsInt();
            assertEquals(point.get("value").getAsFloat(), actual[index], 0.0004f, "mel index " + index);
        }
        int frames = golden.get("frames").getAsInt();
        for (int band = 0; band < 80; band++) assertEquals(0, actual[band * 3000 + frames]);
    }
    @Test void byteVocabularyPreservesPolishAndJapaneseAndValidatesLanguage() throws Exception {
        var golden = json("tokenizer-reference.json");
        Path file = directory.resolve("tokenizer.json"); Files.writeString(file, golden.toString());
        var tokenizer = new WhisperTokenizer(file);
        for (var value : golden.getAsJsonArray("cases")) {
            var sample = value.getAsJsonObject();
            int[] ids = sample.getAsJsonArray("ids").asList().stream().mapToInt(JsonElement::getAsInt).toArray();
            assertEquals(sample.get("text").getAsString(), tokenizer.decode(ids, ids.length));
        }
        assertEquals(4, tokenizer.prefix("pl").length);
        assertEquals(4, tokenizer.prefix("ja").length);
        assertThrows(IllegalArgumentException.class, () -> tokenizer.prefix("zzz"));
    }
    private JsonObject json(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/audio/" + name)) {
            return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
