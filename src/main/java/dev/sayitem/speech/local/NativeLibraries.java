package dev.sayitem.speech.local;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;

/** Load native libraries from the checksum-verified runtime cache. */
final class NativeLibraries {
    private static Path loaded;
    private NativeLibraries() {}
    static synchronized Path load(Path runtime) throws IOException {
        if (loaded != null) return loaded;
        Path root = runtime.resolve("native");
        JsonObject manifest = JsonParser.parseString(Files.readString(root.resolve("source.json"))).getAsJsonObject();
        if (System.getProperty("os.name").startsWith("Windows")) {
            for (String name : manifest.getAsJsonObject("files").keySet())
                if (name.endsWith("libiomp5md.dll")) System.load(root.resolve(name).toString());
        }
        System.load(root.resolve(manifest.get("library").getAsString()).toString());
        loaded = root.resolve(manifest.get("wrapper").getAsString());
        return loaded;
    }
}
