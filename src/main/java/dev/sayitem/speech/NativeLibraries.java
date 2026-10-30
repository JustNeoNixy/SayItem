package dev.sayitem.speech;

import dev.sayitem.core.JsonFiles;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Extracts the jar's checksum-verified, self-contained engine for this platform. */
final class NativeLibraries {
    private static Path loaded;
    private NativeLibraries() {}

    static synchronized Path load() throws IOException {
        if (loaded != null) return loaded;
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        boolean x64 = arch.equals("amd64") || arch.equals("x86_64");
        boolean arm64 = arch.equals("aarch64") || arch.equals("arm64");
        String platform;
        String extension;
        if (os.startsWith("windows") && x64) {
            platform = "windows-x64";
            extension = "dll";
        } else if (os.contains("linux") && (x64 || arm64)) {
            platform = x64 ? "linux-x64" : "linux-arm64";
            extension = "so";
        } else if ((os.contains("mac") || os.contains("darwin")) && (x64 || arm64)) {
            platform = x64 ? "macos-x64" : "macos-arm64";
            extension = "dylib";
        } else throw new IOException("Unsupported speech platform: " + os + " / " + arch);
        String folder = "/sayitem-speech/native/" + platform + "/";
        String filename = "sayitem-whisper." + extension;
        String hash;
        try (var input = NativeLibraries.class.getResourceAsStream(folder + "source.json")) {
            if (input == null) throw new IOException("Missing bundled native-engine manifest for " + platform);
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\uFEFF", "");
            hash = JsonFiles.GSON.fromJson(json, com.google.gson.JsonObject.class).get("sha256").getAsString();
        }
        Path directory = Files.createTempDirectory("sayitem-whisper-");
        directory.toFile().deleteOnExit();
        loaded = BundledFiles.extract(NativeLibraries.class, folder + filename, directory.resolve(filename), hash).toAbsolutePath();
        loaded.toFile().deleteOnExit();
        return loaded;
    }
}
