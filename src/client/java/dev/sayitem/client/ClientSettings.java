package dev.sayitem.client;

import dev.sayitem.core.JsonFiles;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class ClientSettings {
    public String microphoneName = "";
    public String language = "ja";
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("sayitem/client.json"); }
    public static ClientSettings load() throws IOException {
        if (!Files.isRegularFile(file())) return new ClientSettings();
        try (var reader = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
            ClientSettings settings = JsonFiles.GSON.fromJson(reader, ClientSettings.class);
            if (settings == null) return new ClientSettings();
            if (settings.microphoneName == null) settings.microphoneName = "";
            if (settings.language == null || !settings.language.matches("[a-z]{2,3}(-[a-z0-9]+)?")) settings.language = "ja";
            return settings;
        }
    }
    public void save() throws IOException { JsonFiles.write(file(), this); }
}
