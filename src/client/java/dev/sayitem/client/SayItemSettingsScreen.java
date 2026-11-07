package dev.sayitem.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import dev.sayitem.net.Packets;
import dev.sayitem.speech.GpuRuntime;
import dev.sayitem.speech.SpeechProfile;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.util.*;

/** Native Minecraft controls; no companion window or external setup. */
final class SayItemSettingsScreen extends Screen {
    private RuleSlider difficulty, timer;
    private Button sharingButton;
    private boolean sharingChoice = SayItemClient.sharing, sharingChanged;
    SayItemSettingsScreen() { super(Component.literal("SayItem settings")); }
    @Override protected void init() {
        RuleSlider previousDifficulty = difficulty, previousTimer = timer;
        int rowWidth = Math.min(260, width - 32), left = (width - rowWidth) / 2;
        int top = Math.max(56, height / 2 - 52);
        var language = addRenderableWidget(Button.builder(languageLabel(), button -> {
            ClientUi.open(net.minecraft.client.Minecraft.getInstance(), new LanguageSettingsScreen(this));
        }).bounds(left, top, rowWidth - 92, 20).build());
        language.active = !SayItemClient.languages.isEmpty();
        language.setTooltip(Tooltip.create(Component.literal("Choose a language, random mode, or match Minecraft's language.")));
        addRenderableWidget(Button.builder(readingsLabel(), button -> {
            SayItemClient.settings.showReadings = !SayItemClient.settings.showReadings;
            try { SayItemClient.settings.save(); }
            catch (IOException error) { SayItemClient.status = "Could not save reading settings."; }
            button.setMessage(readingsLabel());
        }).bounds(left + rowWidth - 88, top, 88, 20).tooltip(Tooltip.create(Component.literal(
            "Show approximate Latin readings. German: ai = eye, ee = see, oy = boy. u(e)/o(e) are single rounded vowels. Packs can provide their own guides."))).build());
        List<String> microphones;
        try { microphones = SpeechCapture.microphones(); }
        catch (RuntimeException e) { microphones = List.of(""); }
        List<String> options = microphones;
        addRenderableWidget(Button.builder(microphoneLabel(), button -> {
            SayItemClient.cancelSpeech();
            int next = (options.indexOf(SayItemClient.settings.microphoneName) + 1) % options.size();
            SayItemClient.settings.microphoneName = options.get(next);
            try { SayItemClient.settings.save(); } catch (IOException ignored) { SayItemClient.status = "Could not save microphone settings."; }
            button.setMessage(microphoneLabel());
        }).bounds(left, top + 24, rowWidth, 20).build());
        int half = (rowWidth - 4) / 2;
        difficulty = addRenderableWidget(new RuleSlider(left, top + 48, half, false));
        timer = addRenderableWidget(new RuleSlider(left + half + 4, top + 48, rowWidth - half - 4, true));
        difficulty.keepChoice(previousDifficulty); timer.keepChoice(previousTimer);
        syncRule(difficulty); syncRule(timer);
        var backend = addRenderableWidget(Button.builder(backendLabel(), button -> {
            SayItemClient.settings.speechProfile = SpeechProfile.parse(SayItemClient.settings.speechProfile).next().id();
            try { SayItemClient.settings.save(); }
            catch (IOException error) { SayItemClient.status = "Could not save speech settings."; }
            button.setMessage(backendLabel());
        }).bounds(left, top + 72, half, 20).build());
        backend.active = GpuRuntime.supportedPlatform();
        backend.setTooltip(Tooltip.create(Component.literal(backend.active
            ? "CPU Base, GPU Base, GPU Small, or GPU Turbo. Turbo uses a larger model for accuracy and takes longer. Rejoin to apply speech changes."
            : "GPU speech currently requires Windows x64 and a Vulkan-capable graphics driver.")));
        sharingButton = addRenderableWidget(Button.builder(sharingLabel(), button -> {
            sharingChoice = !sharingChoice; sharingChanged = true; button.setMessage(sharingLabel());
        }).bounds(left + half + 4, top + 72, rowWidth - half - 4, 20).build());
        syncSharing();
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds((width - 160) / 2, height - 28, 160, 20).build());
        if (ClientPlayNetworking.canSend(Packets.Request.TYPE)) ClientPlayNetworking.send(new Packets.Request());
    }
    @Override public void tick() {
        if (difficulty == null) return;
        syncRule(difficulty); syncRule(timer);
        syncSharing();
    }
    private void syncSharing() {
        sharingButton.active = SayItemClient.canConfigure && ClientPlayNetworking.canSend(Packets.SetSharing.TYPE);
        if (!sharingChanged || !sharingButton.active) { sharingChoice = SayItemClient.sharing; sharingChanged = false; }
        sharingButton.setMessage(sharingLabel());
        sharingButton.setTooltip(Tooltip.create(Component.literal(sharingButton.active
            ? "Share unlocks with every player. Turning sharing off keeps unlocks already received."
            : "Only the world owner or a server operator can change sharing on an updated SayItem server.")));
    }
    private Component sharingLabel() { return Component.literal("Sharing: " + (sharingChoice ? "On" : "Off")); }
    private Component backendLabel() {
        return Component.literal("Speech: " + SpeechProfile.parse(SayItemClient.settings.speechProfile).label());
    }
    private void syncRule(RuleSlider slider) {
        slider.active = SayItemClient.canConfigure;
        if (!slider.changed || !slider.active) {
            slider.valueFromServer(); slider.changed = false;
        }
        slider.setTooltip(Tooltip.create(Component.literal(!slider.active
            ? "Only the world owner or a server operator can change this setting."
            : slider.timer ? "10 to 30 seconds for newly collected locked items. Active timers keep their deadline."
            : "60 to 100% recognized text match required to unlock an item.")));
        slider.updateMessage();
    }
    @Override public void onClose() {
        if (difficulty != null && difficulty.changed && SayItemClient.canConfigure
            && ClientPlayNetworking.canSend(Packets.SetAccuracy.TYPE))
            ClientPlayNetworking.send(new Packets.SetAccuracy(difficulty.percent()));
        if (timer != null && timer.changed && SayItemClient.canConfigure
            && ClientPlayNetworking.canSend(Packets.SetTimer.TYPE))
            ClientPlayNetworking.send(new Packets.SetTimer(timer.amount()));
        if (sharingChanged && sharingChoice != SayItemClient.sharing && SayItemClient.canConfigure
            && ClientPlayNetworking.canSend(Packets.SetSharing.TYPE))
            ClientPlayNetworking.send(new Packets.SetSharing(sharingChoice));
        super.onClose();
    }
    private static final class RuleSlider extends AbstractSliderButton {
        private boolean changed;
        private final boolean timer;
        RuleSlider(int x, int y, int width, boolean timer) {
            super(x, y, width, 20, Component.empty(), timer ? (SayItemClient.timerSeconds - 10) / 20.0 : (SayItemClient.requiredAccuracy - 60) / 40.0);
            this.timer = timer;
            updateMessage();
        }
        int amount() { return timer ? 10 + (int) Math.round(value * 20) : 60 + (int) Math.round(value * 40); }
        void keepChoice(RuleSlider previous) {
            if (previous != null && previous.changed) { value = previous.value; changed = true; updateMessage(); }
        }
        int percent() { return amount(); }
        void valueFromServer() { value = timer ? (SayItemClient.timerSeconds - 10) / 20.0 : (SayItemClient.requiredAccuracy - 60) / 40.0; }
        @Override protected void updateMessage() {
            setMessage(Component.literal(timer ? "Timer: " + amount() + "s" : "Match: " + amount() + "%"));
        }
        @Override protected void applyValue() { changed = true; updateMessage(); }
    }
    private Component languageLabel() {
        String id = SayItemClient.settings.language;
        return Component.literal("Language: " + (SayItemClient.languages.has(id) ? SayItemClient.languages.get(id).getAsString() : "Join a SayItem world"));
    }
    private Component microphoneLabel() {
        return Component.literal("Microphone: " + (SayItemClient.settings.microphoneName.isBlank() ? "System default" : SayItemClient.settings.microphoneName));
    }
    private Component readingsLabel() { return Component.literal("Readings: " + (SayItemClient.settings.showReadings ? "On" : "Off")); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xED18232B);
        String count = SayItemClient.totalItemCount > 0 ? " · " + SayItemClient.unlockedCount + "/" + SayItemClient.totalItemCount : "";
        graphics.centeredText(font, Component.literal(title.getString() + count), width / 2, 18, 0xFFFFFFFF);
        int textWidth = Math.min(360, width - 32);
        var help = font.split(Component.literal("Speech runs inside Minecraft. Hold " + SayItemClient.key.getTranslatedKeyMessage().getString() + " to speak."), textWidth);
        for (int i = 0; i < Math.min(2, help.size()); i++) graphics.centeredText(font, help.get(i), width / 2, 34 + i * font.lineHeight, 0xFFCAD6DF);
        int statusY = Math.max(56, height / 2 - 52) + 102;
        String engineStatus = SayItemClient.engine.status();
        if (net.minecraft.client.Minecraft.getInstance().player == null) engineStatus = "Join a world to prepare speech.";
        else if (!SayItemClient.activeSpeechProfile.equals(SayItemClient.settings.speechProfile))
            engineStatus = "Rejoin to use " + SpeechProfile.parse(SayItemClient.settings.speechProfile).label() + ".";
        drawLines(graphics, engineStatus, statusY, textWidth, 0xFF8EE4CE, 2);
        if (!SayItemClient.transcript.isBlank() && statusY + 28 + font.lineHeight < height - 36)
            drawLines(graphics, "Last heard: " + SayItemClient.transcript, statusY + 28, textWidth, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
    private void drawLines(GuiGraphicsExtractor graphics, String text, int y, int textWidth, int color) {
        drawLines(graphics, text, y, textWidth, color, Integer.MAX_VALUE);
    }
    private void drawLines(GuiGraphicsExtractor graphics, String text, int y, int textWidth, int color, int limit) {
        var lines = font.split(Component.literal(text), textWidth);
        int count = Math.max(0, (height - 36 - y) / font.lineHeight);
        for (int i = 0; i < Math.min(limit, Math.min(count, lines.size())); i++) graphics.text(font, lines.get(i), (width - textWidth) / 2, y + i * font.lineHeight, color);
    }
}
