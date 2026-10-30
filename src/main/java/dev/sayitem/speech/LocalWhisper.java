package dev.sayitem.speech;

import com.sun.jna.*;
import java.io.IOException;
import java.lang.ref.Reference;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** One native model and worker inside Minecraft, with cooperative native computation cancellation. */
public final class LocalWhisper implements AutoCloseable {
    public static final String MODEL_SHA = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898";
    private static final WhisperApi.LogCallback LOG_SINK = (level, text, data) -> {};
    private final Path cache;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "sayitem-whisper"); thread.setDaemon(true); return thread;
    });
    private final AtomicInteger nativeAborts = new AtomicInteger();
    private CompletableFuture<Void> loading;
    private WhisperApi whisper;
    private Pointer context;
    private volatile String status = "Whisper is starting…";
    private volatile boolean ready;
    private volatile boolean closed;
    private volatile boolean recognizing;

    public LocalWhisper(Path cache) { this.cache = cache; }
    public String status() { return status; }
    public boolean isReady() { return ready && !closed; }
    public boolean isRecognizing() { return recognizing; }
    int nativeAborts() { return nativeAborts.get(); }
    CompletableFuture<Void> whenIdle() { return CompletableFuture.runAsync(() -> {}, worker); }

    public synchronized CompletableFuture<Void> initialize() {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Speech engine is closed"));
        if (loading != null) return loading;
        loading = CompletableFuture.runAsync(() -> {
            try {
                status = "Preparing bundled Whisper model…";
                Path model = BundledFiles.extract(LocalWhisper.class, "/sayitem-speech/ggml-base-q5_1.bin",
                    cache.resolve("base-q5_1-" + MODEL_SHA.substring(0, 12) + ".bin"), MODEL_SHA);
                Path library = NativeLibraries.load();
                whisper = Native.load(library.toString(), WhisperApi.class, Map.of(Library.OPTION_STRING_ENCODING, "UTF-8"));
                whisper.whisper_log_set(LOG_SINK, null);
                Pointer nativeParameters = whisper.whisper_context_default_params_by_ref();
                try {
                    var parameters = new WhisperApi.ContextParams(nativeParameters);
                    parameters.useGpu = 0;
                    if (parameters.size() != whisper.sayitem_context_params_size()) throw new IOException("Native context ABI mismatch");
                    context = whisper.whisper_init_from_file_with_params(model.toAbsolutePath().toString(), parameters);
                } finally { whisper.whisper_free_context_params(nativeParameters); }
                if (context == null || whisper.whisper_is_multilingual(context) == 0)
                    throw new IOException("Cannot initialize the bundled multilingual model.");
                ready = true; status = "Whisper ready · fully local";
            } catch (IOException | RuntimeException | LinkageError e) {
                status = "Whisper unavailable: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
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
        if (!AudioSamples.hasSpeechEnergy(samples)) return CompletableFuture.completedFuture("");
        var result = new CompletableFuture<String>();
        initialize().whenComplete((ignored, error) -> {
            if (error != null) { result.completeExceptionally(error); return; }
            if (result.isDone()) return;
            try { worker.execute(() -> recognize(samples, language, result)); }
            catch (RejectedExecutionException e) { result.completeExceptionally(e); }
        });
        return result;
    }

    private void recognize(float[] samples, String language, CompletableFuture<String> result) {
        if (result.isDone()) return;
        if (closed) { result.completeExceptionally(new IllegalStateException("Speech engine is closed")); return; }
        Pointer nativeParameters = null;
        try {
            if (whisper.whisper_lang_id(language) < 0) throw new IOException("Unsupported speech language: " + language);
            nativeParameters = whisper.whisper_full_default_params_by_ref(1); // beam search
            var parameters = new WhisperApi.FullParams(nativeParameters);
            if (parameters.size() != whisper.sayitem_full_params_size()) throw new IOException("Native transcription ABI mismatch");
            parameters.language = language; parameters.translate = 0; parameters.detectLanguage = 0;
            parameters.threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
            parameters.noContext = 1; parameters.maxTextContext = 0; parameters.noTimestamps = 1;
            parameters.printProgress = 0; parameters.printRealtime = 0; parameters.printTimestamps = 0;
            parameters.singleSegment = 1; parameters.suppressNonSpeech = 1;
            parameters.beamSize = 3; parameters.temperatureIncrement = 0;
            parameters.audioContext = 384; // 7.68 seconds, above the six-second recording cap
            WhisperApi.AbortCallback abort = data -> {
                boolean stop = closed || result.isCancelled();
                if (stop) nativeAborts.incrementAndGet();
                return (byte) (stop ? 1 : 0);
            };
            parameters.abortCallback = abort;
            float[] padded = new float[Math.max(16000, samples.length + 3200)];
            System.arraycopy(samples, 0, padded, 1600, samples.length);
            recognizing = true;
            int code;
            try { code = whisper.whisper_full(context, parameters, padded, padded.length); }
            finally { recognizing = false; Reference.reachabilityFence(abort); }
            if (result.isCancelled()) return;
            if (closed) throw new IOException("Speech engine is closed");
            if (code != 0) throw new IOException("Whisper recognition failed (code " + code + ").");
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < whisper.whisper_full_n_segments(context); i++)
                text.append(whisper.whisper_full_get_segment_text(context, i)).append(' ');
            String answer = text.toString().trim();
            if (answer.length() > 256) throw new IOException("Say only the displayed item name.");
            result.complete(answer.matches("^[\\[(].*[\\])]$") ? "" : answer);
        } catch (IOException | RuntimeException | LinkageError e) {
            result.completeExceptionally(e);
        } finally {
            recognizing = false;
            if (nativeParameters != null) whisper.whisper_free_params(nativeParameters);
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true; ready = false;
        worker.execute(() -> { if (context != null) whisper.whisper_free(context); });
        worker.shutdown();
    }
}
