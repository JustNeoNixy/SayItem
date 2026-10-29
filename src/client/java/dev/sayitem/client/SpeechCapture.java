package dev.sayitem.client;

import dev.sayitem.core.JsonFiles;
import javax.sound.sampled.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Audio exists only in memory and is posted exclusively to IPv4 loopback. */
public final class SpeechCapture {
    private static final AudioFormat FORMAT = new AudioFormat(16_000, 16, 1, true, false);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ClientSettings settings;
    private volatile boolean recording;
    private volatile boolean cancelled;
    private volatile TargetDataLine line;
    private volatile CompletableFuture<HttpResponse<String>> request;
    private final AtomicBoolean busy = new AtomicBoolean();

    public SpeechCapture(ClientSettings settings) { this.settings = settings; }
    public boolean isRecording() { return recording; }
    public boolean isBusy() { return busy.get(); }

    public void start(String language, Consumer<String> status, Consumer<String> transcript, Consumer<String> error) {
        if (!busy.compareAndSet(false, true)) return;
        recording = true;
        cancelled = false;
        Thread.ofVirtual().name("sayitem-microphone").start(() -> {
            try {
                var info = new DataLine.Info(TargetDataLine.class, FORMAT);
                TargetDataLine active;
                if (settings.microphoneName.isBlank()) active = (TargetDataLine) AudioSystem.getLine(info);
                else {
                    var mixer = Arrays.stream(AudioSystem.getMixerInfo())
                            .filter(m -> m.getName().equals(settings.microphoneName)).findFirst()
                            .orElseThrow(() -> new IOException("Configured microphone not found: " + settings.microphoneName));
                    active = (TargetDataLine) AudioSystem.getMixer(mixer).getLine(info);
                }
                byte[] pcm;
                try (active) {
                    line = active;
                    if (cancelled) return;
                    active.open(FORMAT);
                    active.start();
                    status.accept("Listening… release your speak key when finished.");
                    var audio = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    while (recording && !cancelled && audio.size() < 6 * 32_000) {
                        int count = active.read(buffer, 0, Math.min(buffer.length, 6 * 32_000 - audio.size()));
                        if (count > 0) audio.write(buffer, 0, count);
                    }
                    pcm = audio.toByteArray();
                } finally { line = null; recording = false; }
                if (cancelled) return;
                if (pcm.length < 6_400) throw new IOException("Recording too short. Hold your speak key while talking.");
                byte[] wav;
                try (var output = new ByteArrayOutputStream(); var input = new AudioInputStream(
                        new ByteArrayInputStream(pcm), FORMAT, pcm.length / FORMAT.getFrameSize())) {
                    AudioSystem.write(input, AudioFileFormat.Type.WAVE, output);
                    wav = output.toByteArray();
                }
                status.accept("Recognizing locally…");
                var httpRequest = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + settings.port
                                + "/transcribe?language=" + language))
                        .timeout(Duration.ofSeconds(90))
                        .header("Authorization", "Bearer " + settings.token)
                        .header("Content-Type", "audio/wav")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(wav)).build();
                request = HTTP.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString());
                if (cancelled) { request.cancel(true); return; }
                var response = request.join();
                if (cancelled) return;
                var body = JsonFiles.GSON.fromJson(response.body(), com.google.gson.JsonObject.class);
                if (response.statusCode() != 200) throw new IOException(body.has("error")
                        ? body.get("error").getAsString() : "Speech service returned " + response.statusCode());
                String text = body.get("text").getAsString();
                if (text.length() > 256) throw new IOException("Speech result was too long. Say only the displayed words.");
                transcript.accept(text);
            } catch (Exception e) {
                if (!cancelled) {
                    Throwable cause = e;
                    while (cause.getCause() != null) cause = cause.getCause();
                    error.accept("Speech unavailable: " + (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage())
                            + ". Open SayItem and check your microphone.");
                }
            } finally { recording = false; busy.set(false); }
        });
    }
    public void finish() { recording = false; }
    public void cancel() {
        cancelled = true; recording = false;
        var active = line;
        if (active != null) active.close();
        var pending = request;
        if (pending != null) pending.cancel(true);
    }
}
