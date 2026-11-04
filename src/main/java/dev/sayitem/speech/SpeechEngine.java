package dev.sayitem.speech;

import java.util.concurrent.CompletableFuture;

public interface SpeechEngine extends AutoCloseable {
    CompletableFuture<Void> initialize();
    CompletableFuture<String> transcribe(float[] samples, String language);
    String status();
    boolean isReady();
    boolean isRecognizing();
    int cancelledJobs();
    CompletableFuture<Void> whenIdle();
    @Override void close();
}
