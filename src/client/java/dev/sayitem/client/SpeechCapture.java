package dev.sayitem.client;

import dev.sayitem.speech.AudioSamples;
import dev.sayitem.speech.LocalWhisper;
import javax.sound.sampled.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Microphone audio goes directly into the in-process model and is never saved or uploaded. */
public final class SpeechCapture {
    private static final AudioFormat FORMAT = new AudioFormat(16_000, 16, 1, true, false);
    private final ClientSettings settings;
    private final LocalWhisper engine;
    private volatile boolean recording;
    private volatile boolean cancelled;
    private volatile TargetDataLine line;
    private volatile CompletableFuture<String> request;
    private final AtomicBoolean busy = new AtomicBoolean();

    public SpeechCapture(ClientSettings settings, LocalWhisper engine) { this.settings = settings; this.engine = engine; }
    public boolean isRecording() { return recording; }
    public boolean isBusy() { return busy.get(); }
    public static List<String> microphones() {
        var names = new ArrayList<String>(); names.add("");
        var info = new DataLine.Info(TargetDataLine.class, FORMAT);
        for (var mixer : AudioSystem.getMixerInfo()) if (AudioSystem.getMixer(mixer).isLineSupported(info)) names.add(mixer.getName());
        return List.copyOf(names);
    }
    public void start(String language, Consumer<String> status, Consumer<String> transcript, Consumer<String> error) {
        if (!engine.isReady()) { error.accept(engine.status()); return; }
        if (!busy.compareAndSet(false, true)) return;
        recording = true; cancelled = false;
        Thread.ofVirtual().name("sayitem-microphone").start(() -> {
            try {
                var info = new DataLine.Info(TargetDataLine.class, FORMAT);
                TargetDataLine active;
                if (settings.microphoneName.isBlank()) active = (TargetDataLine) AudioSystem.getLine(info);
                else {
                    var mixer = Arrays.stream(AudioSystem.getMixerInfo()).filter(m -> m.getName().equals(settings.microphoneName))
                        .findFirst().orElseThrow(() -> new IOException("Selected microphone not found. Choose another in SayItem settings (K)."));
                    active = (TargetDataLine) AudioSystem.getMixer(mixer).getLine(info);
                }
                byte[] pcm;
                try (active) {
                    line = active;
                    if (cancelled) return;
                    active.open(FORMAT); active.start();
                    status.accept("Listening… release your speak key when finished.");
                    var audio = new ByteArrayOutputStream(); byte[] buffer = new byte[1024];
                    while (recording && !cancelled && audio.size() < 6 * 32_000) {
                        int count = active.read(buffer, 0, Math.min(buffer.length, 6 * 32_000 - audio.size()));
                        if (count > 0) audio.write(buffer, 0, count);
                    }
                    pcm = audio.toByteArray();
                } finally { line = null; recording = false; }
                if (cancelled) return;
                if (pcm.length < 6400) throw new IOException("Recording too short. Hold your speak key while talking.");
                status.accept("Recognizing locally…");
                request = engine.transcribe(AudioSamples.fromPcm16(pcm), language);
                if (cancelled) { request.cancel(false); return; }
                String answer = request.join();
                if (!cancelled) transcript.accept(answer);
            } catch (Exception e) {
                if (!cancelled) {
                    Throwable cause = e;
                    while (cause.getCause() != null) cause = cause.getCause();
                    error.accept("Speech unavailable: " + (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage())
                        + ". Check your microphone in SayItem settings (K).");
                }
            } finally { recording = false; busy.set(false); }
        });
    }
    public void finish() { recording = false; }
    public void cancel() {
        cancelled = true; recording = false;
        var active = line; if (active != null) active.close();
        var pending = request; if (pending != null) pending.cancel(false);
    }
}
