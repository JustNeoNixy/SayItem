package dev.sayitem.speech.local;

/** The same Slaney log-mel input used by faster-whisper: 16 kHz, FFT 400, hop 160, 80 bands. */
final class MelSpectrogram {
    private static final int FFT = 400, BANDS = 80, FRAMES = 3000;
    private static final double[] COS = new double[FFT], SIN = new double[FFT];
    private static final float[] WINDOW = new float[FFT];
    private static final float[][] FILTERS = filters();
    static {
        for (int i = 0; i < FFT; i++) {
            COS[i] = Math.cos(2 * Math.PI * i / FFT); SIN[i] = -Math.sin(2 * Math.PI * i / FFT);
            WINDOW[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / FFT));
        }
    }
    private MelSpectrogram() {}

    static float[] compute(float[] audio) {
        int frames = Math.min(FRAMES, (audio.length + 160) / 160);
        float[] result = new float[BANDS * FRAMES];
        float[] input = new float[FFT], magnitudes = new float[FFT / 2 + 1];
        double[] real = new double[FFT], imag = new double[FFT];
        float maximum = -10;
        for (int frame = 0; frame < frames; frame++) {
            for (int i = 0; i < FFT; i++) {
                int index = frame * 160 + i - FFT / 2;
                int length = audio.length + 160;
                if (index < 0) index = -index;
                if (index >= length) index = 2 * length - 2 - index;
                input[i] = (index >= 0 && index < audio.length ? audio[index] : 0) * WINDOW[i];
            }
            // Cooley-Tukey 400 = 16*25. Only real-input bins 0..200 are needed.
            for (int residue = 0; residue < 16; residue++) for (int k = 0; k < 25; k++) {
                double re = 0, im = 0;
                for (int m = 0; m < 25; m++) {
                    int angle = (16 * m * k) % FFT;
                    float sample = input[16 * m + residue];
                    re += sample * COS[angle]; im += sample * SIN[angle];
                }
                real[residue * 25 + k] = re; imag[residue * 25 + k] = im;
            }
            for (int k = 0; k <= FFT / 2; k++) {
                double re = 0, im = 0;
                for (int residue = 0; residue < 16; residue++) {
                    int index = residue * 25 + k % 25, angle = residue * k % FFT;
                    re += real[index] * COS[angle] - imag[index] * SIN[angle];
                    im += real[index] * SIN[angle] + imag[index] * COS[angle];
                }
                float r = (float) re, j = (float) im;
                magnitudes[k] = r * r + j * j;
            }
            for (int band = 0; band < BANDS; band++) {
                double sum = 0;
                for (int bin = 0; bin < magnitudes.length; bin++) sum += FILTERS[band][bin] * magnitudes[bin];
                float value = (float) Math.log10(Math.max(1e-10, sum));
                result[band * FRAMES + frame] = value;
                maximum = Math.max(maximum, value);
            }
        }
        for (int band = 0; band < BANDS; band++) for (int frame = 0; frame < frames; frame++) {
            int index = band * FRAMES + frame;
            result[index] = (Math.max(result[index], maximum - 8) + 4) / 4;
        }
        // Padding occurs after normalization, as in faster-whisper's pad_or_trim.
        return result;
    }

    private static float[][] filters() {
        double[] frequencies = new double[BANDS + 2];
        for (int i = 0; i < frequencies.length; i++) {
            double mel = i * 45.245640471924965 / (BANDS + 1);
            frequencies[i] = mel < 15 ? (200.0 / 3) * mel : 1000 * Math.exp(Math.log(6.4) / 27 * (mel - 15));
        }
        float[][] filters = new float[BANDS][FFT / 2 + 1];
        for (int band = 0; band < BANDS; band++) for (int bin = 0; bin <= FFT / 2; bin++) {
            double hz = bin * 40.0;
            double lower = (hz - frequencies[band]) / (frequencies[band + 1] - frequencies[band]);
            double upper = (frequencies[band + 2] - hz) / (frequencies[band + 2] - frequencies[band + 1]);
            filters[band][bin] = (float) (Math.max(0, Math.min(lower, upper)) * 2 / (frequencies[band + 2] - frequencies[band]));
        }
        return filters;
    }
}
