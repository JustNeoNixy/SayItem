package dev.sayitem.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.util.*;

/** Native Minecraft controls; no companion window or external setup. */
final class SayItemSettingsScreen extends Screen {
    SayItemSettingsScreen() { super(Component.literal("SayItem settings")); }
    @Override protected void init() {
        int left = width / 2 - 130;
        var ids = SayItemClient.languages.keySet().stream().sorted().toList();
        var language = addRenderableWidget(Button.builder(languageLabel(), button -> {
            if (ids.isEmpty()) return;
            int next = (ids.indexOf(SayItemClient.settings.language) + 1) % ids.size();
            SayItemClient.chooseLanguage(ids.get(next)); button.setMessage(languageLabel());
        }).bounds(left, height / 2 - 35, 260, 20).build());
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
        }).bounds(left, height / 2 - 10, 260, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(left + 50, height - 34, 160, 20).build());
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
        drawLines(graphics, SayItemClient.engine.status(), height / 2 + 20, textWidth, 0xFF8EE4CE);
        if (!SayItemClient.transcript.isBlank() && height / 2 + 46 + font.lineHeight < height - 42)
            drawLines(graphics, "Last heard: " + SayItemClient.transcript, height / 2 + 46, textWidth, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
    private void drawLines(GuiGraphicsExtractor graphics, String text, int y, int textWidth, int color) {
        var lines = font.split(Component.literal(text), textWidth);
        int count = Math.max(0, (height - 42 - y) / font.lineHeight);
        for (int i = 0; i < Math.min(count, lines.size()); i++) graphics.text(font, lines.get(i), (width - textWidth) / 2, y + i * font.lineHeight, color);
    }
}
