package dev.sayitem.speech;

import java.nio.file.Path;
import java.util.concurrent.*;

/** Tiny facade. Downloads local-only speech libraries once, without adding them to Fabric's mod list. */
public final class LocalWhisper implements SpeechEngine {
    public static final String MODEL_SHA = "d01c3014881c9c6f3133c182f3d2887eb6ca1c789a7538c5c007196857a0a6a9";
    private final Path cache;
    private final SpeechProfile profile;
    private CompletableFuture<Void> loading;
    private volatile SpeechEngine delegate;
    private volatile String message = "Preparing local speech…";
    private volatile boolean closed;
    private volatile boolean gpuFallback;
    public LocalWhisper(Path cache) { this(cache, SpeechProfile.CPU); }
    public LocalWhisper(Path cache, SpeechProfile profile) { this.cache = cache; this.profile = profile; }
    public synchronized CompletableFuture<Void> initialize() {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Speech engine is closed"));
        if (loading != null) return loading;
        loading = CompletableFuture.runAsync(() -> {
            try {
                var runtime = RuntimeBundles.load(cache, text -> message = text);
                synchronized (this) {
                    if (closed) return;
                    delegate = runtime.create(cache, profile);
                }
                try { delegate.initialize().join(); }
                catch (Exception | LinkageError gpuError) {
                    if (!profile.gpu() || closed) throw gpuError;
                    delegate.close(); gpuFallback = true;
                    synchronized (this) {
                        if (closed) return;
                        delegate = runtime.create(cache);
                    }
                    delegate.initialize().join();
                }
                if (closed) delegate.close();
            } catch (Exception | LinkageError e) {
                Throwable cause = e; while (cause.getCause() != null) cause = cause.getCause();
                message = "Speech unavailable: " + cause.getMessage();
                throw new CompletionException(cause);
            }
        }, job -> Thread.ofVirtual().name("sayitem-runtime").start(job));
        return loading;
    }
    public CompletableFuture<String> transcribe(float[] samples, String language) {
        var result = new CompletableFuture<String>();
        initialize().whenComplete((ignored, failure) -> {
            if (failure != null) { result.completeExceptionally(failure); return; }
            if (result.isDone()) return;
            if (closed) { result.completeExceptionally(new IllegalStateException("Speech engine is closed")); return; }
            var inner = delegate.transcribe(samples, language);
            result.whenComplete((text, error) -> { if (result.isCancelled()) inner.cancel(false); });
            inner.whenComplete((text, error) -> {
                if (error != null) result.completeExceptionally(error); else result.complete(text);
            });
        });
        return result;
    }
    public String status() { return delegate == null ? message : (gpuFallback ? "GPU unavailable · using CPU · " : "") + delegate.status(); }
    public boolean isReady() { return !closed && delegate != null && delegate.isReady(); }
    public boolean isRecognizing() { return delegate != null && delegate.isRecognizing(); }
    public int cancelledJobs() { return delegate == null ? 0 : delegate.cancelledJobs(); }
    int nativeAborts() { return cancelledJobs(); }
    public CompletableFuture<Void> whenIdle() { return delegate == null ? CompletableFuture.completedFuture(null) : delegate.whenIdle(); }
    @Override public synchronized void close() { closed = true; if (delegate != null) delegate.close(); }
}
