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
            boolean fabricBundled = false;
            for (var nested : metadata.getAsJsonArray("jars")) {
                String name = nested.getAsJsonObject().get("file").getAsString();
                if (name.contains("fabric-api")) {
                    fabricBundled = true;
                    try (var zip = new java.util.zip.ZipInputStream(jar.getInputStream(jar.getEntry(name)))) {
                        boolean foundMetadata = false;
                        for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                            if (entry.getName().equals("fabric.mod.json")) foundMetadata = true;
                        if (!foundMetadata) throw new AssertionError("Invalid bundled Fabric API");
                    }
                }
            }
            if (!fabricBundled) throw new AssertionError("Distribution is missing bundled Fabric API");
            for (String platform : java.util.List.of("windows-x64", "linux-x64", "linux-arm64", "macos-x64", "macos-arm64")) {
                String extension = platform.startsWith("windows") ? "dll" : platform.startsWith("linux") ? "so" : "dylib";
                if (jar.getEntry("sayitem-speech/native/" + platform + "/sayitem-whisper." + extension) == null ||
                    jar.getEntry("sayitem-speech/native/" + platform + "/source.json") == null)
                    throw new AssertionError("Missing native platform: " + platform);
            }
            var model = jar.getEntry("sayitem-speech/ggml-base-q5_1.bin");
            if (model == null || model.getSize() != 59707625) throw new AssertionError("Distribution is missing the model");
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
        }
        System.out.println("Single-jar smoke test passed: bundled engine + model, real speech, bundled Fabric API.");
    }
}
