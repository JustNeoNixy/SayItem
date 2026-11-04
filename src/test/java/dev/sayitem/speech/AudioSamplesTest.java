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
    @Test void trimsIdleTimeAndAmplifiesQuietSpeechWithoutClipping() {
        var quiet = new float[96000];
        for (int i = 32000; i < 48000; i++) quiet[i] = (float) (0.002 * Math.sin(i * 0.1));
        float[] prepared = AudioSamples.prepare(quiet);
        assertTrue(prepared.length >= 16000, "Do not cut off the spoken word");
        assertTrue(prepared.length < 24000, "Idle time should not reach the encoder");
        assertTrue(AudioSamples.hasSpeechEnergy(prepared));
        for (float sample : prepared) assertTrue(Math.abs(sample) <= 0.9f);
    }
    @Test void dcOffsetAndClicksDoNotBecomeLoudSpeech() {
        float[] offset = new float[16000]; java.util.Arrays.fill(offset, 0.1f);
        assertEquals(0, AudioSamples.prepare(offset).length);
        offset[8000] += 0.4f;
        assertEquals(0, AudioSamples.prepare(offset).length);
    }
}
