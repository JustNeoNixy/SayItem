package dev.sayitem.client;

import dev.sayitem.core.JsonFiles;
import dev.sayitem.net.Packets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import com.google.gson.JsonObject;

public final class SayItemClient implements ClientModInitializer {
    static Packets.Challenge challenge;
    static String transcript = "";
    static String status = "Collect an item to begin.";
    static String selectedLanguage = "ja";
    static JsonObject languages = new JsonObject();
    static int accuracy = -1;
    static long deadline;
    static long resultUntil;
    static SpeechCapture speech;
    static KeyMapping key;
    private DesktopBridge bridge;
    private long lastHeartbeat;
    private String recordingNonce = "";
    private boolean wasDown;
    private boolean awaitingAnswer;
    private long submittedAt;

    static long remaining() { return Math.max(0, (deadline - System.nanoTime()) / 1_000_000); }

    @Override public void onInitializeClient() {
        var settings = new ClientSettings();
        bridge = new DesktopBridge(settings);
        speech = new SpeechCapture(settings);
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sayitem.speak", InputConstants.KEY_V, KeyMapping.Category.GAMEPLAY));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("sayitem", "challenge"), (graphics, delta) -> ChallengeHud.draw(graphics));
        ClientPlayNetworking.registerGlobalReceiver(Packets.PackInfo.TYPE, (packet, context) -> {
            languages = JsonFiles.GSON.fromJson(packet.languagesJson(), JsonObject.class);
            selectedLanguage = packet.selected();
        });
        ClientPlayNetworking.registerGlobalReceiver(Packets.Challenge.TYPE, (packet, context) -> {
            if (challenge == null || !challenge.nonce().equals(packet.nonce())) {
                speech.cancel(); awaitingAnswer = false;
                status = "Hold " + key.getTranslatedKeyMessage().getString() + " and say the words.";
                transcript = ""; accuracy = -1;
            }
            challenge = packet; deadline = System.nanoTime() + packet.remainingMillis() * 1_000_000;
        });
        ClientPlayNetworking.registerGlobalReceiver(Packets.Result.TYPE, (packet, context) -> {
            // Results for other independently expired item types must not cancel this recording.
            boolean current = challenge != null && challenge.nonce().equals(packet.nonce());
            if (current || challenge == null || packet.nonce().isEmpty()) {
                status = packet.message(); accuracy = packet.accuracy(); resultUntil = System.nanoTime() + 4_000_000_000L;
            }
            if (current) {
                awaitingAnswer = false;
                if (packet.finished()) { speech.cancel(); challenge = null; }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            speech.cancel(); challenge = null; awaitingAnswer = false; languages = new JsonObject(); selectedLanguage = "ja";
            status = "Collect an item to begin."; transcript = ""; accuracy = -1;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long clock = System.nanoTime();
            if (clock - lastHeartbeat >= 1_000_000_000L) {
                lastHeartbeat = clock;
                bridge.update(client, challenge, remaining(), transcript, accuracy, status);
            }
            boolean down = key.isDown() && client.player != null && ClientUi.screen(client) == null;
            if (down && !wasDown && challenge != null && remaining() > 0 && !speech.isBusy() && !awaitingAnswer) {
                recordingNonce = challenge.nonce();
                String nonce = recordingNonce;
                speech.start(challenge.whisperLanguage(), message -> client.execute(() -> {
                    if (challenge != null && challenge.nonce().equals(nonce)) status = message;
                }), heard -> client.execute(() -> {
                    if (challenge == null || !challenge.nonce().equals(nonce) || remaining() <= 0) return;
                    transcript = heard; status = "Checking…"; awaitingAnswer = true; submittedAt = System.nanoTime();
                    ClientPlayNetworking.send(new Packets.Attempt(nonce, heard));
                }), error -> client.execute(() -> {
                    if (challenge != null && challenge.nonce().equals(nonce)) status = error;
                }));
            }
            if (!down && wasDown && speech.isRecording()) speech.finish();
            wasDown = down;
            if (challenge != null && remaining() <= 0) speech.cancel();
            if (awaitingAnswer && clock - submittedAt > 3_000_000_000L) { awaitingAnswer = false; status = "No server response. Release the key and try again."; }
            while (key.consumeClick()) { /* Hold state drives recording; consume clicks to avoid a queue. */ }
        });
    }
}
