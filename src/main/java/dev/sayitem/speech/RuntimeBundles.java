package dev.sayitem.speech;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipInputStream;

final class RuntimeBundles {
    record Loaded(Class<?> implementation, Path root) {
        SpeechEngine create(Path cache) throws ReflectiveOperationException {
            return (SpeechEngine) implementation.getConstructor(Path.class, Path.class).newInstance(cache, root);
        }
    }
    private static Loaded loaded;
    static synchronized Loaded load(Path cache, Consumer<String> progress) throws Exception {
        if (loaded != null) return loaded;
        String platform = platform();
        JsonObject spec;
        try (var stream = RuntimeBundles.class.getResourceAsStream("/sayitem-speech/runtime-index.json")) {
            if (stream == null) throw new IOException("Missing speech runtime index");
            var index = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            spec = index.getAsJsonObject(platform);
            if (spec == null) throw new IOException("Unsupported speech platform: " + platform);
        }
        String override = System.getProperty("sayitem.runtime.cache", "");
        Path root = (override.isBlank() ? cache.resolve("runtimes") : Path.of(override))
            .resolve(platform + "-" + spec.get("sha256").getAsString().substring(0, 12)).toAbsolutePath();
        var files = spec.getAsJsonObject("files");
        if (!valid(root, files)) {
            Path supplied = System.getProperty("sayitem.runtime.bundle", "").isBlank() ? null : Path.of(System.getProperty("sayitem.runtime.bundle"));
            Path archive = root.resolveSibling(root.getFileName() + ".zip");
            AssetDownloads.ensure(archive, URI.create(spec.get("url").getAsString()), spec.get("sha256").getAsString(),
                spec.get("bytes").getAsLong(), supplied, progress, "speech runtime");
            Files.createDirectories(root);
            try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
                long total = 0;
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    Path target = root.resolve(entry.getName()).normalize();
                    if (!target.startsWith(root) || entry.isDirectory() || !files.has(entry.getName())) throw new IOException("Invalid runtime archive entry");
                    Files.createDirectories(target.getParent());
                    try (var output = Files.newOutputStream(target)) {
                        byte[] buffer = new byte[65536]; int count;
                        while ((count = zip.read(buffer)) >= 0) {
                            total += count; if (total > 512L * 1024 * 1024) throw new IOException("Speech runtime archive is too large");
                            output.write(buffer, 0, count);
                        }
                    }
                }
            } finally { Files.deleteIfExists(archive); }
            if (!valid(root, files)) throw new IOException("Speech runtime is damaged; restart Minecraft to retry");
        }
        URL source = LocalWhisper.class.getProtectionDomain().getCodeSource().getLocation();
        var loader = new URLClassLoader(new URL[]{source, root.resolve("onnxruntime.jar").toUri().toURL()}, LocalWhisper.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("dev.sayitem.speech.local.") || name.startsWith("ai.onnxruntime.")) {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> type = findLoadedClass(name);
                        if (type == null) type = findClass(name);
                        if (resolve) resolveClass(type);
                        return type;
                    }
                }
                return super.loadClass(name, resolve);
            }
            @Override public URL getResource(String name) {
                if (name.startsWith("ai/onnxruntime/")) { URL own = findResource(name); if (own != null) return own; }
                return super.getResource(name);
            }
        };
        loaded = new Loaded(loader.loadClass("dev.sayitem.speech.local.CTranslateEngine"), root);
        return loaded;
    }
    private static boolean valid(Path root, JsonObject files) throws IOException {
        for (var entry : files.entrySet()) {
            Path path = root.resolve(entry.getKey()).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path) || !BundledFiles.sha256(path).equals(entry.getValue().getAsString())) return false;
        }
        return true;
    }
    static String platform() throws IOException {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT), arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String cpu = arch.equals("amd64") || arch.equals("x86_64") ? "x64" : arch.equals("aarch64") || arch.equals("arm64") ? "arm64" : "";
        String system = os.startsWith("windows") ? "windows" : os.contains("linux") ? "linux" : os.contains("mac") || os.contains("darwin") ? "macos" : "";
        if (cpu.isBlank() || system.isBlank() || system.equals("windows") && !cpu.equals("x64")) throw new IOException("Unsupported speech platform: " + os + "/" + arch);
        return system + "-" + cpu;
    }
}
