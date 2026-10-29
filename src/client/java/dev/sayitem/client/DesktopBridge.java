package dev.sayitem.client;

import com.google.gson.JsonObject;
import dev.sayitem.core.JsonFiles;
import dev.sayitem.net.Packets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/** Authenticated heartbeat proves that this game instance actually has SayItem loaded. */
final class DesktopBridge {
    private final ClientSettings settings;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile boolean paired;
    DesktopBridge(ClientSettings settings) { this.settings = settings; }
    void update(Minecraft client, Packets.Challenge challenge, long remaining, String transcript, int accuracy, String result) {
        if (!busy.compareAndSet(false, true)) return;
        JsonObject body = new JsonObject();
        body.addProperty("version", SharedConstants.getCurrentVersion().name());
        body.addProperty("inWorld", client.player != null);
        body.addProperty("protocol", 2);
        body.add("languages", SayItemClient.languages.deepCopy());
        body.addProperty("selectedLanguage", SayItemClient.selectedLanguage);
        body.addProperty("transcript", transcript); body.addProperty("accuracy", accuracy); body.addProperty("result", result);
        if (challenge != null) {
            body.addProperty("target", challenge.text()); body.addProperty("reading", challenge.reading());
            body.addProperty("item", challenge.item()); body.addProperty("remainingMillis", remaining);
            body.addProperty("pendingCount", challenge.pendingCount());
        }
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + settings.port + (paired ? "/game" : "/hello")))
            .timeout(Duration.ofSeconds(2)).header("Authorization", "Bearer " + settings.token)
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            busy.set(false);
            if (error != null) { paired = false; return; }
            if (response.statusCode() == 401) { paired = false; return; }
            if (response.statusCode() != 200) return;
            paired = true;
            try {
                String language = JsonFiles.GSON.fromJson(response.body(), JsonObject.class).get("language").getAsString();
                client.execute(() -> {
                    if (client.player == null) return;
                    if (!language.equals(SayItemClient.selectedLanguage) && ClientPlayNetworking.canSend(Packets.SelectLanguage.TYPE)) {
                        ClientPlayNetworking.send(new Packets.SelectLanguage(language));
                    }
                });
            } catch (RuntimeException ignored) { /* The game remains usable without the desktop app. */ }
        });
    }
}
