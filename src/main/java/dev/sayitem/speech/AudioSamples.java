package dev.sayitem.speech;

/** 16 kHz mono signed PCM16 input, normalized to the float samples Whisper expects. */
public final class AudioSamples {
    public static final int MAX_RECORDING_SECONDS = 12;
    public static final int MAX_SAMPLES = MAX_RECORDING_SECONDS * 16000;
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
    /** Preserve the full recording for Silero; only correct DC offset and quiet microphone gain. */
    public static float[] normalize(float[] input) {
        double mean = 0;
        for (float sample : input) mean += sample;
        mean /= input.length;
        float[] result = new float[input.length];
        double energy = 0, peak = 0;
        for (int i = 0; i < input.length; i++) {
            result[i] = (float) (input[i] - mean);
            energy += result[i] * result[i]; peak = Math.max(peak, Math.abs(result[i]));
        }
        if (peak == 0) return result;
        double gain = Math.min(0.95 / peak, Math.max(1, Math.min(8, 0.04 / Math.sqrt(energy / input.length))));
        for (int i = 0; i < result.length; i++) result[i] *= (float) gain;
        return result;
    }

    /** Keep quiet consonants, trim idle microphone time, and raise quiet input without clipping. */
    public static float[] prepare(float[] input) {
        if (input.length < 3200) return new float[0];
        double mean = 0;
        for (float sample : input) mean += sample;
        mean /= input.length;
        float[] centered = new float[input.length];
        double[] frames = new double[(input.length + 319) / 320];
        double loudest = 0;
        for (int frame = 0; frame < frames.length; frame++) {
            int start = frame * 320, end = Math.min(input.length, start + 320);
            double energy = 0;
            for (int i = start; i < end; i++) {
                centered[i] = (float) (input[i] - mean);
                energy += centered[i] * centered[i];
            }
            frames[frame] = Math.sqrt(energy / (end - start));
            loudest = Math.max(loudest, frames[frame]);
        }
        double threshold = Math.max(0.0012, Math.min(0.006, loudest * 0.08));
        int first = -1, last = -1, active = 0;
        for (int frame = 0; frame < frames.length; frame++) if (frames[frame] >= threshold) {
            if (first < 0) first = frame;
            last = frame; active++;
        }
        if (active < 3) return new float[0];
        int start = Math.max(0, first * 320 - 2560);
        int end = Math.min(input.length, (last + 1) * 320 + 2560);
        float[] result = java.util.Arrays.copyOfRange(centered, start, end);
        double energy = 0, peak = 0;
        for (float sample : result) { energy += sample * sample; peak = Math.max(peak, Math.abs(sample)); }
        double gain = Math.min(8, Math.min(0.08 / Math.sqrt(energy / result.length), 0.9 / peak));
        for (int i = 0; i < result.length; i++) result[i] *= (float) gain;
        return result;
    }
}
