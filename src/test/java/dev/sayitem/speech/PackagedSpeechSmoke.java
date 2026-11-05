package dev.sayitem.speech;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javax.sound.sampled.AudioSystem;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.zip.ZipFile;

/** Loads the engine from the actual distribution jar, isolated from loose development classes. */
public final class PackagedSpeechSmoke {
    public static void main(String[] args) throws Exception {
        Path mod = Path.of(args[0]), work = Path.of(args[1]);
        Files.createDirectories(work);
        try (var jar = new ZipFile(mod.toFile())) {
            JsonObject metadata;
            try (var reader = new java.io.InputStreamReader(jar.getInputStream(jar.getEntry("fabric.mod.json")), java.nio.charset.StandardCharsets.UTF_8)) {
                metadata = new Gson().fromJson(reader, JsonObject.class);
            }
            if (metadata.has("jars") && !metadata.getAsJsonArray("jars").isEmpty())
                throw new AssertionError("SayItem must not bundle other mods");
            if (!metadata.getAsJsonObject("depends").has("fabric-api"))
                throw new AssertionError("Fabric API must remain a required external dependency");
            if (jar.stream().anyMatch(entry -> entry.getName().endsWith(".jar")))
                throw new AssertionError("Unexpected nested jar");
            if (Files.size(mod) > 2 * 1024 * 1024) throw new AssertionError("Unexpected mod jar size");
            if (jar.stream().anyMatch(entry -> entry.getName().matches(".*\\.(dll|dylib|so|bin|onnx|part)$") || entry.getName().startsWith("ai/onnxruntime/")))
                throw new AssertionError("Heavy runtime files must remain outside the mod jar");
            if (jar.getEntry("sayitem-speech/runtime-index.json") == null || jar.getEntry("sayitem-speech/model.json") == null || jar.getEntry("sayitem-speech/vulkan-index.json") == null)
                throw new AssertionError("Missing speech download manifests");
        }
        URL gson = Gson.class.getProtectionDomain().getCodeSource().getLocation();
        URL jna = com.sun.jna.Native.class.getProtectionDomain().getCodeSource().getLocation();
        try (var loader = new URLClassLoader(new URL[]{mod.toUri().toURL(), gson, jna}, ClassLoader.getPlatformClassLoader())) {
            Class<?> type = loader.loadClass("dev.sayitem.speech.LocalWhisper");
            if (!type.getProtectionDomain().getCodeSource().getLocation().equals(mod.toUri().toURL())) throw new AssertionError("Engine not loaded from mod jar");
            Object engine = type.getConstructor(Path.class).newInstance(work.resolve("model-cache"));
            try {
                ((CompletableFuture<?>) type.getMethod("initialize").invoke(engine)).get(60, TimeUnit.SECONDS);
                try (var input = PackagedSpeechSmoke.class.getResourceAsStream("/audio/diamond-sword.wav");
                     var audio = AudioSystem.getAudioInputStream(new java.io.BufferedInputStream(input))) {
                    float[] samples = AudioSamples.fromPcm16(audio.readAllBytes());
                    String answer = (String) ((CompletableFuture<?>) type.getMethod("transcribe", float[].class, String.class)
                        .invoke(engine, samples, "en")).get(60, TimeUnit.SECONDS);
                    if (!dev.sayitem.core.AnswerMatcher.matches(answer, "Diamond sword", java.util.List.of())) throw new AssertionError(answer);
                }
            } finally { ((AutoCloseable) engine).close(); }
            if (Boolean.getBoolean("sayitem.gpu.tests")) {
                Class<?> profileType = loader.loadClass("dev.sayitem.speech.SpeechProfile");
                Object profile = profileType.getField("GPU_SMALL").get(null);
                Object gpu = type.getConstructor(Path.class, profileType).newInstance(work.resolve("gpu-model-cache"), profile);
                try {
                    ((CompletableFuture<?>) type.getMethod("initialize").invoke(gpu)).get(120, TimeUnit.SECONDS);
                    String status = (String) type.getMethod("status").invoke(gpu);
                    if (!status.contains("Vulkan")) throw new AssertionError("GPU must not silently fall back: " + status);
                    try (var input = PackagedSpeechSmoke.class.getResourceAsStream("/audio/diamond-sword.wav");
                         var audio = AudioSystem.getAudioInputStream(new java.io.BufferedInputStream(input))) {
                        float[] samples = AudioSamples.fromPcm16(audio.readAllBytes());
                        String answer = (String) ((CompletableFuture<?>) type.getMethod("transcribe", float[].class, String.class)
                            .invoke(gpu, samples, "en")).get(30, TimeUnit.SECONDS);
                        if (!dev.sayitem.core.AnswerMatcher.matches(answer, "Diamond sword", java.util.List.of())) throw new AssertionError(answer);
                    }
                    System.out.println("Packaged GPU speech verified: " + status);
                } finally { ((AutoCloseable) gpu).close(); }
            }
        }
        System.out.println("Mod smoke test passed: small jar, real speech through isolated runtime, no nested mods, external Fabric API.");
    }
}
