package dev.sayitem.speech.local;

import com.sun.jna.*;
import dev.sayitem.speech.*;
import java.io.IOException;
import java.lang.ref.Reference;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs Whisper directly on the local GPU; no process, Python interpreter or network audio service. */
public final class VulkanEngine implements SpeechEngine {
    private final Path cache;
    private final SpeechProfile profile;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(job -> {
        Thread thread = new Thread(job, "sayitem-vulkan"); thread.setDaemon(true); return thread;
    });
    private final AtomicInteger aborted = new AtomicInteger();
    private VulkanApi api;
    private Pointer context;
    private SpeechFilter filter;
    private CompletableFuture<Void> loading;
    private volatile String status = "Preparing GPU speech…";
    private volatile boolean ready, closed, recognizing;
    public VulkanEngine(Path cache, SpeechProfile profile) { this.cache = cache; this.profile = profile; }
    @Override public synchronized CompletableFuture<Void> initialize() {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Speech engine is closed"));
        if (loading != null) return loading;
        loading = CompletableFuture.runAsync(() -> {
            try {
                Path root = GpuRuntime.prepare(cache, text -> status = text);
                api = VulkanApi.load(root);
                if (!api.sayitem_vk_version().equals("whisper.cpp-v1.9.5-d1be6fde")) throw new IOException("GPU engine version mismatch");
                String device = api.sayitem_vk_device();
                if (device == null || device.isBlank()) throw new IOException("No compatible Vulkan GPU");
                Path model = GpuRuntime.model(cache, profile, text -> status = text);
                byte[] error = new byte[2048];
                context = api.sayitem_vk_load(model.toString(), 1, Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()-1)), error, error.length);
                if (context == null) throw new IOException(Native.toString(error, "UTF-8"));
                filter = new SpeechFilter(BundledModels.vad(cache, text -> status = text));
                status = "Warming GPU speech…";
                var warmup = new CompletableFuture<String>();
                if (closed) warmup.cancel(false);
                infer(new float[16000], "en", warmup);
                warmup.join();
                if (closed) throw new IOException("Speech engine is closed");
                ready = true; status = profile.label() + " ready · Vulkan · " + device;
            } catch (Exception | LinkageError error) {
                release(); status = "GPU speech unavailable";
                throw new CompletionException(error);
            }
        }, worker);
        return loading;
    }
    @Override public CompletableFuture<String> transcribe(float[] samples, String language) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Speech engine is closed"));
        if (samples.length < 3200 || samples.length > 96000 || language == null || !language.matches("[a-z]{2,3}"))
            return CompletableFuture.failedFuture(new IllegalArgumentException("Record 0.2 to 6 seconds in a supported language"));
        float[] normalized = AudioSamples.normalize(samples);
        if (!AudioSamples.hasSpeechEnergy(normalized)) return CompletableFuture.completedFuture("");
        var result = new CompletableFuture<String>();
        initialize().whenComplete((ignored, error) -> {
            if (error != null) { result.completeExceptionally(error); return; }
            if (result.isDone()) return;
            try { worker.execute(() -> {
                if (result.isDone()) return;
                try {
                    float[] speech = filter.speech(normalized);
                    if (speech.length == 0) { result.complete(""); return; }
                    infer(speech.length < 3200 ? Arrays.copyOf(speech, 3200) : speech, language, result);
                } catch (Exception | LinkageError failure) { result.completeExceptionally(failure); }
            }); } catch (RejectedExecutionException failure) { result.completeExceptionally(failure); }
        });
        return result;
    }
    private void infer(float[] samples, String language, CompletableFuture<String> result) {
        if (result.isDone()) return;
        if (closed) { result.completeExceptionally(new IllegalStateException("Speech engine is closed")); return; }
        byte[] text = new byte[2048], error = new byte[2048];
        VulkanApi.Cancelled cancel = data -> {
            boolean stop = closed || result.isCancelled();
            if (stop) aborted.incrementAndGet();
            return stop ? 1 : 0;
        };
        recognizing = true;
        try {
            int count = api.sayitem_vk_transcribe(context, samples, samples.length, language, 5, text, text.length, cancel, null, error, error.length);
            if (result.isCancelled()) return;
            if (count < 0) throw new IOException(Native.toString(error, "UTF-8"));
            String answer = Native.toString(text, "UTF-8").trim();
            if (answer.length() > 256) throw new IOException("Say only the displayed item name");
            result.complete(answer);
        } catch (Exception | LinkageError failure) { result.completeExceptionally(failure); }
        finally { recognizing = false; Reference.reachabilityFence(cancel); }
    }
    private void release() {
        ready = false;
        if (filter != null) try { filter.close(); } catch (Exception ignored) { }
        filter = null;
        if (context != null) { api.sayitem_vk_free(context); context = null; }
    }
    @Override public String status() { return status; }
    @Override public boolean isReady() { return ready && !closed; }
    @Override public boolean isRecognizing() { return recognizing; }
    @Override public int cancelledJobs() { return aborted.get(); }
    @Override public CompletableFuture<Void> whenIdle() { return CompletableFuture.runAsync(() -> {}, worker); }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true; ready = false; worker.execute(this::release); worker.shutdown();
    }
}
