package dev.sayitem.speech.local;
import dev.sayitem.speech.*;

import com.sun.jna.*;
import java.io.IOException;
import java.lang.ref.Reference;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** The original faster-whisper CTranslate2 INT8 engine, called directly inside Minecraft. */
public final class CTranslateEngine implements SpeechEngine {
    public static final String MODEL_SHA = "d01c3014881c9c6f3133c182f3d2887eb6ca1c789a7538c5c007196857a0a6a9";
    private final Path cache;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "sayitem-whisper"); thread.setDaemon(true); return thread;
    });
    private final AtomicInteger nativeAborts = new AtomicInteger();
    private CompletableFuture<Void> loading;
    private CTranslateApi engine;
    private WhisperTokenizer tokenizer;
    private SpeechFilter filter;
    private Pointer context;
    private volatile String status = "Whisper is starting…";
    private volatile boolean ready, closed, recognizing;

    private final Path runtime;
    public CTranslateEngine(Path cache, Path runtime) { this.cache = cache; this.runtime = runtime; }
    public String status() { return status; }
    public boolean isReady() { return ready && !closed; }
    public boolean isRecognizing() { return recognizing; }
    public int cancelledJobs() { return nativeAborts.get(); }
    public CompletableFuture<Void> whenIdle() { return CompletableFuture.runAsync(() -> {}, worker); }

    public synchronized CompletableFuture<Void> initialize() {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Speech engine is closed"));
        if (loading != null) return loading;
        loading = CompletableFuture.runAsync(() -> {
            try {
                status = "Preparing bundled Whisper model…";
                var model = BundledModels.prepare(cache, text -> status = text);
                tokenizer = new WhisperTokenizer(model.model().resolve("tokenizer.json"));
                engine = Native.load(NativeLibraries.load(runtime).toString(), CTranslateApi.class,
                    Map.of(Library.OPTION_STRING_ENCODING, "UTF-8"));
                if (!engine.sayitem_ct2_version().equals("4.8.4")) throw new IOException("Speech engine version mismatch");
                byte[] error = new byte[2048];
                context = engine.sayitem_ct2_load(model.model().toString(), Math.max(1, Math.min(4,
                    Runtime.getRuntime().availableProcessors() - 1)), error, error.length);
                if (context == null) throw new IOException(Native.toString(error, "UTF-8"));
                filter = new SpeechFilter(model.vad());
                ready = true; status = "Whisper ready · CTranslate2 INT8 · fully local";
            } catch (Exception | LinkageError e) {
                status = "Whisper unavailable: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                if (context != null) { engine.sayitem_ct2_free(context); context = null; }
                throw new CompletionException(e);
            }
        }, worker);
        return loading;
    }

    public CompletableFuture<String> transcribe(float[] samples, String language) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Speech engine is closed"));
        if (samples.length < 3200 || samples.length > 96000)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Record 0.2 to 6 seconds of speech."));
        if (language == null || !language.matches("[a-z]{2,3}"))
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid speech language"));
        float[] normalized = AudioSamples.normalize(samples);
        if (!AudioSamples.hasSpeechEnergy(normalized)) return CompletableFuture.completedFuture("");
        var result = new CompletableFuture<String>();
        initialize().whenComplete((ignored, error) -> {
            if (error != null) { result.completeExceptionally(error); return; }
            if (result.isDone()) return;
            try { worker.execute(() -> recognize(normalized, language, result)); }
            catch (RejectedExecutionException e) { result.completeExceptionally(e); }
        });
        return result;
    }

    private void recognize(float[] samples, String language, CompletableFuture<String> result) {
        if (result.isDone()) return;
        if (closed) { result.completeExceptionally(new IllegalStateException("Speech engine is closed")); return; }
        try {
            int[] prefix = tokenizer.prefix(language);
            float[] speech = filter.speech(samples);
            if (result.isDone()) return;
            if (speech.length == 0) { result.complete(""); return; }
            float[] mel = MelSpectrogram.compute(speech);
            int[] tokens = new int[256]; float[] metrics = new float[2]; byte[] error = new byte[2048];
            CTranslateApi.Cancelled abort = data -> {
                boolean stop = closed || result.isCancelled();
                if (stop) nativeAborts.incrementAndGet();
                return stop ? 1 : 0;
            };
            recognizing = true;
            int count;
            try { count = engine.sayitem_ct2_transcribe(context, mel, prefix, prefix.length,
                    tokens, tokens.length, metrics, abort, null, error, error.length); }
            finally { recognizing = false; Reference.reachabilityFence(abort); }
            if (result.isCancelled()) return;
            if (closed) throw new IOException("Speech engine is closed");
            if (count < 0) throw new IOException(Native.toString(error, "UTF-8"));
            // Match faster-whisper's joint no-speech/low-logprob rejection.
            if (count == 0 || metrics[0] > 0.6 && metrics[1] * count / (count + 1) < -1) {
                result.complete(""); return;
            }
            String answer = tokenizer.decode(tokens, count);
            if (answer.length() > 256) throw new IOException("Say only the displayed item name.");
            result.complete(answer);
        } catch (Exception | LinkageError e) { result.completeExceptionally(e); }
        finally { recognizing = false; }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true; ready = false;
        worker.execute(() -> {
            if (filter != null) try { filter.close(); } catch (Exception ignored) { }
            if (context != null) { engine.sayitem_ct2_free(context); context = null; }
        });
        worker.shutdown();
    }
}
