package dev.sayitem.speech;

/** 16 kHz mono signed PCM16 input, normalized to the float samples Whisper expects. */
public final class AudioSamples {
    private AudioSamples() {}
    public static float[] fromPcm16(byte[] pcm) {
        if (pcm.length % 2 != 0) throw new IllegalArgumentException("Incomplete PCM16 sample");
        float[] samples = new float[pcm.length / 2];
        for (int i = 0; i < samples.length; i++) {
            short sample = (short) ((pcm[i * 2] & 255) | (pcm[i * 2 + 1] << 8));
            samples[i] = sample / 32768f;
        }
        return samples;
    }
    public static boolean hasSpeechEnergy(float[] samples) {
        int activeFrames = 0;
        double total = 0;
        for (int offset = 0; offset < samples.length; offset += 320) {
            int end = Math.min(samples.length, offset + 320);
            double energy = 0;
            for (int i = offset; i < end; i++) energy += samples[i] * samples[i];
            total += energy;
            if (Math.sqrt(energy / (end - offset)) >= 0.006) activeFrames++;
        }
        return samples.length >= 3200 && activeFrames >= 3 && Math.sqrt(total / samples.length) >= 0.003;
    }
}
