package dev.sayitem.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import dev.sayitem.net.Packets;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.util.*;

/** Native Minecraft controls; no companion window or external setup. */
final class SayItemSettingsScreen extends Screen {
    private RuleSlider difficulty, timer;
    SayItemSettingsScreen() { super(Component.literal("SayItem settings")); }
    @Override protected void init() {
        int rowWidth = Math.min(260, width - 32), left = (width - rowWidth) / 2;
        int top = Math.max(56, height / 2 - 52);
        var ids = SayItemClient.languages.keySet().stream().sorted().toList();
        var language = addRenderableWidget(Button.builder(languageLabel(), button -> {
            if (ids.isEmpty()) return;
            int next = (ids.indexOf(SayItemClient.settings.language) + 1) % ids.size();
            SayItemClient.chooseLanguage(ids.get(next)); button.setMessage(languageLabel());
        }).bounds(left, top, rowWidth, 20).build());
        language.active = !ids.isEmpty();
        List<String> microphones;
        try { microphones = SpeechCapture.microphones(); }
        catch (RuntimeException e) { microphones = List.of(""); }
        List<String> options = microphones;
        addRenderableWidget(Button.builder(microphoneLabel(), button -> {
            SayItemClient.speech.cancel();
            int next = (options.indexOf(SayItemClient.settings.microphoneName) + 1) % options.size();
            SayItemClient.settings.microphoneName = options.get(next);
            try { SayItemClient.settings.save(); } catch (IOException ignored) { SayItemClient.status = "Could not save microphone settings."; }
            button.setMessage(microphoneLabel());
        }).bounds(left, top + 24, rowWidth, 20).build());
        int half = (rowWidth - 4) / 2;
        difficulty = addRenderableWidget(new RuleSlider(left, top + 48, half, false));
        timer = addRenderableWidget(new RuleSlider(left + half + 4, top + 48, rowWidth - half - 4, true));
        syncRule(difficulty); syncRule(timer);
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds((width - 160) / 2, height - 28, 160, 20).build());
        if (ClientPlayNetworking.canSend(Packets.Request.TYPE)) ClientPlayNetworking.send(new Packets.Request());
    }
    @Override public void tick() {
        if (difficulty == null) return;
        syncRule(difficulty); syncRule(timer);
    }
    private void syncRule(RuleSlider slider) {
        slider.active = SayItemClient.canConfigure;
        if (!slider.changed || !slider.active) {
            slider.valueFromServer(); slider.changed = false;
        }
        slider.setTooltip(Tooltip.create(Component.literal(!slider.active
            ? "Only the world owner or a server operator can change this setting."
            : slider.timer ? "10–30 seconds for newly collected locked items. Active timers keep their deadline."
            : "60–100% recognized text match required to unlock an item.")));
        slider.updateMessage();
    }
    @Override public void onClose() {
        if (difficulty != null && difficulty.changed && SayItemClient.canConfigure
            && ClientPlayNetworking.canSend(Packets.SetAccuracy.TYPE))
            ClientPlayNetworking.send(new Packets.SetAccuracy(difficulty.percent()));
        if (timer != null && timer.changed && SayItemClient.canConfigure
            && ClientPlayNetworking.canSend(Packets.SetTimer.TYPE))
            ClientPlayNetworking.send(new Packets.SetTimer(timer.amount()));
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
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xED18232B);
        graphics.centeredText(font, title, width / 2, 18, 0xFFFFFFFF);
        int textWidth = Math.min(360, width - 32);
        var help = font.split(Component.literal("Speech runs inside Minecraft. Hold " + SayItemClient.key.getTranslatedKeyMessage().getString() + " to speak."), textWidth);
        for (int i = 0; i < Math.min(2, help.size()); i++) graphics.centeredText(font, help.get(i), width / 2, 34 + i * font.lineHeight, 0xFFCAD6DF);
        int statusY = Math.max(56, height / 2 - 52) + 78;
        drawLines(graphics, SayItemClient.engine.status(), statusY, textWidth, 0xFF8EE4CE);
        if (!SayItemClient.transcript.isBlank() && statusY + 28 + font.lineHeight < height - 36)
            drawLines(graphics, "Last heard: " + SayItemClient.transcript, statusY + 28, textWidth, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
    private void drawLines(GuiGraphicsExtractor graphics, String text, int y, int textWidth, int color) {
        var lines = font.split(Component.literal(text), textWidth);
        int count = Math.max(0, (height - 36 - y) / font.lineHeight);
        for (int i = 0; i < Math.min(count, lines.size()); i++) graphics.text(font, lines.get(i), (width - textWidth) / 2, y + i * font.lineHeight, color);
    }
}
