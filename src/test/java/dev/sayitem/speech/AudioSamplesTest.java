package dev.sayitem.speech;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AudioSamplesTest {
    @Test void decodesSignedLittleEndianPcmWithoutChangingSampleCount() {
        float[] samples = AudioSamples.fromPcm16(new byte[]{0, 0, -1, 127, 0, -128, -1, -1});
        assertArrayEquals(new float[]{0, 32767 / 32768f, -1f, -1 / 32768f}, samples);
        assertThrows(IllegalArgumentException.class, () -> AudioSamples.fromPcm16(new byte[]{0}));
    }
    @Test void silenceAndSingleClicksAreNotSpeech() {
        assertFalse(AudioSamples.hasSpeechEnergy(new float[16000]));
        var click = new float[16000]; click[100] = 0.8f;
        assertFalse(AudioSamples.hasSpeechEnergy(click));
        var speech = new float[16000];
        for (int i = 3200; i < 9600; i++) speech[i] = (float) (0.1 * Math.sin(i * 0.1));
        assertTrue(AudioSamples.hasSpeechEnergy(speech));
    }
}
