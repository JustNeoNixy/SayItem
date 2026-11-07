package dev.sayitem.speech;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Consumer;

/** Ordinary Vulkan libraries/models downloaded into the game's speech cache, never Fabric mods. */
public final class GpuRuntime {
    private GpuRuntime() {}
    public static boolean supportedPlatform() {
        if (Boolean.getBoolean("sayitem.gpu.disabled")) return false;
        try { return RuntimeBundles.platform().equals("windows-x64"); }
        catch (IOException ignored) { return false; }
    }
    private static JsonObject index() throws IOException {
        try (var stream = GpuRuntime.class.getResourceAsStream("/sayitem-speech/vulkan-index.json")) {
            if (stream == null) throw new IOException("Missing Vulkan speech manifest");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    public static Path prepare(Path cache, Consumer<String> progress) throws IOException, InterruptedException {
        if (!supportedPlatform()) throw new IOException("Vulkan speech currently requires Windows x64");
        return RuntimeBundles.install(cache, "windows-x64-vulkan", index().getAsJsonObject("runtime"), "sayitem.gpu.bundle", progress);
    }
    public static Path model(Path cache, SpeechProfile profile, Consumer<String> progress) throws IOException, InterruptedException {
        String key = switch (profile) { case GPU_TURBO -> "turbo"; case GPU_SMALL -> "small"; default -> "base"; };
        var spec = index().getAsJsonObject("models").getAsJsonObject(key);
        String filename = spec.get("filename").getAsString();
        Path target = cache.resolve("gpu-models").resolve(spec.get("sha256").getAsString().substring(0, 12) + "-" + filename);
        String supplied = System.getProperty("sayitem.gpu.model.directory", "");
        AssetDownloads.ensure(target, URI.create(spec.get("url").getAsString()), spec.get("sha256").getAsString(),
            spec.get("bytes").getAsLong(), supplied.isBlank() ? null : Path.of(supplied).resolve(filename), progress, "Whisper " + key);
        return target;
    }
}
