package dev.sayitem.client;

import dev.sayitem.core.JsonFiles;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class ClientSettings {
    public String microphoneName = "";
    public String language = "ja";
    public String speechProfile = "cpu";
    public boolean showReadings = true;
    public boolean syncMinecraftLanguage = false;
    public String previousMinecraftLanguage = "";
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("sayitem/client.json"); }
    public static ClientSettings load() throws IOException {
        if (!Files.isRegularFile(file())) return new ClientSettings();
        try (var reader = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
            ClientSettings settings = JsonFiles.GSON.fromJson(reader, ClientSettings.class);
            if (settings == null) return new ClientSettings();
            if (settings.microphoneName == null) settings.microphoneName = "";
            if (!dev.sayitem.core.BuiltInLanguages.validChoice(settings.language)) settings.language = "ja";
            if (!dev.sayitem.core.GameLanguageSync.validLocale(settings.previousMinecraftLanguage)) settings.previousMinecraftLanguage = "";
            settings.speechProfile = dev.sayitem.speech.SpeechProfile.parse(settings.speechProfile).id();
            if (!dev.sayitem.speech.GpuRuntime.supportedPlatform()) settings.speechProfile = dev.sayitem.speech.SpeechProfile.CPU.id();
            return settings;
        }
    }
    public void save() throws IOException { JsonFiles.write(file(), this); }
}
