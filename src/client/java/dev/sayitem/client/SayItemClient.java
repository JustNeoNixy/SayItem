package dev.sayitem.client;

import dev.sayitem.core.JsonFiles;
import dev.sayitem.net.Packets;
import dev.speechlib.SpeechLib;
import dev.speechlib.SpeechEngine;
import dev.speechlib.SpeechProfile;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import com.google.gson.JsonObject;
import java.io.IOException;

public final class SayItemClient implements ClientModInitializer {
    static Packets.Challenge challenge;
    static String transcript = "";
    static String displayReading = "";
    static String status = "Collect an item to begin.";
    static String selectedLanguage = "ja";
    static JsonObject languages = new JsonObject();
    static int accuracy = -1;
    static int requiredAccuracy = 90;
    static int timerSeconds = dev.sayitem.core.TimerLimit.DEFAULT;
    static boolean canConfigure;
    static int unlockedCount, totalItemCount;
    static boolean sharing;
    static long deadline;
    static long resultUntil;
    static SpeechCapture speech;
    static KeyMapping key;
    static KeyMapping settingsKey;
    static ClientSettings settings;
    static SpeechEngine engine;
    static String activeSpeechProfile = "cpu";
    static dev.sayitem.core.GameLanguageSync gameLanguage;
    private static boolean joined;
    private boolean readinessSent;
    private boolean preferenceSent;
    private String recordingNonce = "";
    private boolean wasDown;
    private boolean awaitingAnswer;
    private long submittedAt;
    private static String processingNonce = "";
    private static long processingRemaining, captureGeneration;

    static long remaining() { return processingNonce.isEmpty() ? Math.max(0, (deadline - System.nanoTime()) / 1_000_000) : processingRemaining; }
    static void cancelSpeech() {
        captureGeneration++;
        if (speech != null) speech.cancel();
        if (!processingNonce.isEmpty()) {
            if (joined && Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(Packets.RecognitionCancelled.TYPE))
                ClientPlayNetworking.send(new Packets.RecognitionCancelled(processingNonce));
            deadline = System.nanoTime() + processingRemaining * 1_000_000;
            processingNonce = ""; processingRemaining = 0;
        }
    }

    @Override public void onInitializeClient() {
        try { settings = ClientSettings.load(); }
        catch (IOException | RuntimeException e) { settings = new ClientSettings(); }
        prepareSpeech();
        gameLanguage = new dev.sayitem.core.GameLanguageSync(new MinecraftLanguageHost());
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> gameLanguage.restore());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            joined = true;
            if (!activeSpeechProfile.equals(settings.speechProfile)) prepareSpeech();
            engine.initialize();
        });
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sayitem.speak", InputConstants.KEY_V, KeyMapping.Category.GAMEPLAY));
        settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sayitem.settings", InputConstants.KEY_K, KeyMapping.Category.GAMEPLAY));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { cancelSpeech(); engine.close(); });
        ClientPlayNetworking.registerGlobalReceiver(Packets.Progress.TYPE, (packet, context) -> {
            totalItemCount = Math.max(0, packet.total());
            unlockedCount = Math.clamp(packet.unlocked(), 0, totalItemCount);
            sharing = packet.sharing();
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("sayitem", "challenge"), (graphics, delta) -> ChallengeHud.draw(graphics));
        ClientPlayNetworking.registerGlobalReceiver(Packets.PackInfo.TYPE, (packet, context) -> {
            requiredAccuracy = dev.sayitem.core.AccuracyRequirement.clamp(packet.requiredAccuracy());
            timerSeconds = dev.sayitem.core.TimerLimit.clamp(packet.timerSeconds());
            canConfigure = packet.canConfigure();
            languages = JsonFiles.GSON.fromJson(packet.languagesJson(), JsonObject.class);
            if (!selectedLanguage.equals(packet.selected()) && challenge != null) {
                cancelSpeech(); challenge = null; awaitingAnswer = false;
            }
            selectedLanguage = packet.selected();
            if (!preferenceSent) {
                preferenceSent = true;
                if (languages.has(settings.language) && !settings.language.equals(selectedLanguage))
                {
                    ClientPlayNetworking.send(new Packets.SelectLanguage(settings.language));
                    return;
                }
            }
            settings.language = selectedLanguage;
            try { settings.save(); } catch (IOException ignored) { status = "Could not save SayItem language preference."; }
            syncGameLanguage();
        });
        ClientPlayNetworking.registerGlobalReceiver(Packets.Challenge.TYPE, (packet, context) -> {
            if (challenge == null || !challenge.nonce().equals(packet.nonce())) {
                cancelSpeech(); awaitingAnswer = false;
                status = "Hold " + key.getTranslatedKeyMessage().getString() + " and say the words.";
                accuracy = -1;
                displayReading = packet.reading().isBlank()
                    ? dev.sayitem.core.ReadingGuides.forText(packet.text(), packet.whisperLanguage()) : packet.reading();
            }
            challenge = packet; deadline = System.nanoTime() + packet.remainingMillis() * 1_000_000;
            if (processingNonce.equals(packet.nonce())) processingRemaining = packet.remainingMillis();
            syncGameLanguage();
        });
        ClientPlayNetworking.registerGlobalReceiver(Packets.Result.TYPE, (packet, context) -> {
            // A delayed result for an earlier challenge must not cancel the current recording.
            boolean current = challenge != null && challenge.nonce().equals(packet.nonce());
            if (current || challenge == null || packet.nonce().isEmpty()) {
                status = packet.message(); accuracy = packet.accuracy(); resultUntil = System.nanoTime() + 4_000_000_000L;
            }
            if (current) {
                awaitingAnswer = false;
                if (processingNonce.equals(packet.nonce())) cancelSpeech();
                if (packet.finished()) { cancelSpeech(); challenge = null; }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            joined = false;
            cancelSpeech(); challenge = null; awaitingAnswer = false; languages = new JsonObject(); selectedLanguage = "ja";
            readinessSent = false; preferenceSent = false;
            status = "Collect an item to begin."; transcript = ""; displayReading = ""; accuracy = -1;
            requiredAccuracy = 90; timerSeconds = dev.sayitem.core.TimerLimit.DEFAULT; canConfigure = false;
            unlockedCount = 0; totalItemCount = 0; sharing = false;
            gameLanguage.restore();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long clock = System.nanoTime();
            if (!readinessSent && joined && preferenceSent && !gameLanguage.busy() && engine.isReady() && client.player != null && ClientPlayNetworking.canSend(Packets.SpeechReady.TYPE)) {
                ClientPlayNetworking.send(new Packets.SpeechReady()); readinessSent = true;
            }
            while (settingsKey.consumeClick()) if (ClientUi.screen(client) == null) ClientUi.open(client, new SayItemSettingsScreen());
            boolean down = key.isDown() && client.player != null && !gameLanguage.busy() && ClientUi.screen(client) == null;
            if (down && !wasDown && challenge != null && remaining() > 0 && !speech.isBusy() && !awaitingAnswer) {
                recordingNonce = challenge.nonce();
                String nonce = recordingNonce;
                long capture = ++captureGeneration;
                speech.start(challenge.whisperLanguage(), () -> client.execute(() -> {
                    if (capture != captureGeneration || challenge == null || !challenge.nonce().equals(nonce) || remaining() <= 0) return;
                    if (ClientPlayNetworking.canSend(Packets.RecognitionStarted.TYPE)) {
                        processingRemaining = remaining(); processingNonce = nonce;
                        ClientPlayNetworking.send(new Packets.RecognitionStarted(nonce));
                    }
                }), message -> client.execute(() -> {
                    if (capture == captureGeneration && challenge != null && challenge.nonce().equals(nonce)) status = message;
                }), heard -> client.execute(() -> {
                    if (capture != captureGeneration || challenge == null || !challenge.nonce().equals(nonce) || remaining() <= 0) return;
                    transcript = heard; status = "Checking…"; awaitingAnswer = true; submittedAt = System.nanoTime();
                    ClientPlayNetworking.send(new Packets.Attempt(nonce, heard));
                }), error -> client.execute(() -> {
                    if (capture == captureGeneration && challenge != null && challenge.nonce().equals(nonce)) { cancelSpeech(); status = error; }
                }));
            }
            if (!down && wasDown && speech.isRecording()) speech.finish();
            wasDown = down;
            if (challenge != null && remaining() <= 0) cancelSpeech();
            if (awaitingAnswer && clock - submittedAt > 3_000_000_000L) { awaitingAnswer = false; cancelSpeech(); status = "No server response. Release the key and try again."; }
            while (key.consumeClick()) { /* Hold state drives recording; consume clicks to avoid a queue. */ }
        });
    }
    static void chooseLanguage(String language) {
        if (!languages.has(language)) return;
        settings.language = language;
        try { settings.save(); } catch (IOException ignored) { status = "Could not save SayItem settings."; }
        if (ClientPlayNetworking.canSend(Packets.SelectLanguage.TYPE)) ClientPlayNetworking.send(new Packets.SelectLanguage(language));
    }
    static void syncGameLanguage() {
        if (!settings.syncMinecraftLanguage || !joined) { gameLanguage.restore(); return; }
        if (challenge != null) gameLanguage.follow(challenge.language(), challenge.whisperLanguage());
        else if (!selectedLanguage.equals(dev.sayitem.core.BuiltInLanguages.RANDOM)) gameLanguage.follow(selectedLanguage, selectedLanguage);
    }
    private final class MinecraftLanguageHost implements dev.sayitem.core.GameLanguageSync.Host {
        private final Minecraft client = Minecraft.getInstance();
        public String current() { return client.getLanguageManager().getSelected(); }
        public boolean available(String locale) { return client.getLanguageManager().getLanguage(locale) != null; }
        public String previous() { return settings.previousMinecraftLanguage; }
        public void previous(String locale) {
            settings.previousMinecraftLanguage = locale;
            try { settings.save(); } catch (IOException error) { status = "Could not save Minecraft language preference."; }
        }
        public java.util.concurrent.CompletableFuture<Void> change(String locale) {
            String before = current();
            var manager = client.getLanguageManager(); manager.setSelected(locale); client.options.languageCode = locale;
            return client.reloadResourcePacks().handleAsync((ignored, error) -> {
                if (error == null) { client.options.save(); return java.util.concurrent.CompletableFuture.<Void>completedFuture(null); }
                manager.setSelected(before); client.options.languageCode = before;
                return client.reloadResourcePacks().<Void>handleAsync((rolledBack, rollbackError) -> {
                    throw new java.util.concurrent.CompletionException(error);
                }, client::execute);
            }, client::execute).thenCompose(result -> result);
        }
        public void execute(Runnable task) { client.execute(task); }
        public void busy(boolean loading) {
            if (!loading) return;
            cancelSpeech(); readinessSent = false;
            if (joined && client.player != null && ClientPlayNetworking.canSend(Packets.LanguageLoading.TYPE))
                ClientPlayNetworking.send(new Packets.LanguageLoading());
        }
        public void failed() {
            settings.syncMinecraftLanguage = false;
            try { settings.save(); } catch (IOException ignored) { /* Recovery language remains in memory. */ }
            status = "Could not switch Minecraft's language. Matching turned off.";
        }
    }
    private static void prepareSpeech() {
        if (speech != null) cancelSpeech();
        if (engine != null) engine.close();
        activeSpeechProfile = settings.speechProfile;
        engine = SpeechLib.create(SpeechProfile.parse(activeSpeechProfile));
        speech = new SpeechCapture(settings, engine);
    }
}
