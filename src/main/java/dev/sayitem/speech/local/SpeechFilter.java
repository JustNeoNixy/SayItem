package dev.sayitem.speech.local;

import ai.onnxruntime.*;
import java.nio.file.Path;
import java.util.*;

/** Silero v6 with faster-whisper's 512-sample windows, 0.5/0.35 thresholds and 400 ms padding. */
final class SpeechFilter implements AutoCloseable {
    private final OrtEnvironment environment;
    private final OrtSession session;
    SpeechFilter(Path model) throws OrtException {
        environment = OrtEnvironment.getEnvironment();
        try (var options = new OrtSession.SessionOptions()) {
            options.setInterOpNumThreads(1); options.setIntraOpNumThreads(1);
            options.setCPUArenaAllocator(false);
            session = environment.createSession(model.toString(), options);
        }
    }
    float[] speech(float[] audio) throws OrtException {
        int chunks = audio.length / 512 + 1;
        float[][] batch = new float[chunks][576];
        for (int chunk = 0; chunk < chunks; chunk++) {
            int start = chunk * 512;
            if (start >= 64) System.arraycopy(audio, start - 64, batch[chunk], 0, 64);
            if (start < audio.length) System.arraycopy(audio, start, batch[chunk], 64, Math.min(512, audio.length - start));
        }
        float[][][] zero = new float[1][1][128];
        float[] probabilities;
        try (var input = OnnxTensor.createTensor(environment, batch);
             var h = OnnxTensor.createTensor(environment, zero);
             var c = OnnxTensor.createTensor(environment, zero);
             var result = session.run(Map.of("input", input, "h", h, "c", c))) {
            var values = ((OnnxTensor) result.get(0)).getFloatBuffer();
            probabilities = new float[values.remaining()];
            values.get(probabilities);
        }
        List<int[]> segments = new ArrayList<>();
        int start = -1, possibleEnd = -1;
        for (int i = 0; i < probabilities.length; i++) {
            float p = probabilities[i];
            if (p >= 0.5f) { if (start < 0) start = i * 512; possibleEnd = -1; }
            else if (p < 0.35f && start >= 0) {
                if (possibleEnd < 0) possibleEnd = i * 512;
                if (i * 512 - possibleEnd >= 32000) {
                    segments.add(new int[]{Math.max(0, start - 6400), Math.min(audio.length, possibleEnd + 6400)});
                    start = -1; possibleEnd = -1;
                }
            }
        }
        if (start >= 0) segments.add(new int[]{Math.max(0, start - 6400), audio.length});
        List<int[]> merged = new ArrayList<>();
        for (int[] segment : segments) {
            if (!merged.isEmpty() && segment[0] <= merged.getLast()[1]) merged.getLast()[1] = Math.max(merged.getLast()[1], segment[1]);
            else merged.add(segment);
        }
        int length = merged.stream().mapToInt(s -> s[1] - s[0]).sum();
        float[] result = new float[length]; int offset = 0;
        for (int[] segment : merged) {
            int count = segment[1] - segment[0]; System.arraycopy(audio, segment[0], result, offset, count); offset += count;
        }
        return result;
    }
    @Override public void close() throws OrtException { session.close(); }
}
