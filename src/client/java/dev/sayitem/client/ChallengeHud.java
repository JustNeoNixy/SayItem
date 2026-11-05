package dev.sayitem.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** A passive HUD: the player keeps control of the game while speaking. */
final class ChallengeHud {
    private ChallengeHud() {}
    static void draw(GuiGraphicsExtractor graphics) {
        var client = Minecraft.getInstance();
        if (client.player == null || ClientUi.hudHidden(client)) return;
        var challenge = SayItemClient.challenge;
        var font = client.font;
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int width = Math.min(330, screenWidth - 24);
        int y = 12;
        if (!(ClientUi.screen(client) instanceof net.minecraft.client.gui.screens.ChatScreen)) drawProgress(graphics, client);
        if (challenge == null) {
            if (SayItemClient.engine.isReady() && System.nanoTime() > SayItemClient.resultUntil) return;
            String message = SayItemClient.engine.isReady()
                ? SayItemClient.status
                : SayItemClient.engine.status() + " · K: settings";
            int messageWidth = Math.min(width, font.width(message) + 20);
            int messageX = (screenWidth - messageWidth) / 2;
            var lines = font.split(Component.literal(message), messageWidth - 20);
            graphics.fill(messageX, y, messageX + messageWidth, y + 16 + lines.size() * font.lineHeight, 0xE318232B);
            int row = y + 8;
            for (var line : lines) { graphics.text(font, line, messageX + 10, row, 0xFFFFFFFF); row += font.lineHeight; }
            return;
        }
        var language = font.split(Component.literal(challenge.languageName()), width - 24);
        var text = font.split(Component.literal(challenge.text()), width - 24);
        var reading = !SayItemClient.settings.showReadings || SayItemClient.displayReading.isBlank() ? java.util.List.<net.minecraft.util.FormattedCharSequence>of()
            : font.split(Component.literal(SayItemClient.displayReading), width - 24);
        var feedback = font.split(Component.literal(SayItemClient.status), width - 24);
        int feedbackLines = Math.min(2, feedback.size());
        int height = 47 + (language.size() + text.size() + reading.size() + feedbackLines) * font.lineHeight;
        // Long custom phrases scale together to stay within even a small game's viewport.
        float scale = Math.min(1f, (float) (client.getWindow().getGuiScaledHeight() - 62) / height);
        graphics.pose().pushMatrix();
        graphics.pose().translate(screenWidth / 2f, y);
        graphics.pose().scale(Math.max(0.4f, scale));
        int left = -width / 2;
        graphics.fill(left, 0, left + width, height, 0xE318232B);
        String timer = (SayItemClient.remaining() + 999) / 1000 + "s · " + SayItemClient.requiredAccuracy + "% required"
            + (challenge.pendingCount() > 0 ? " · " + challenge.pendingCount() + " queued" : "");
        graphics.text(font, timer, left + 12, 9, 0xFF8EE4CE);
        int row = 26;
        for (var line : language) { graphics.centeredText(font, line, 0, row, 0xFFCAD6DF); row += font.lineHeight; }
        row += 4;
        for (var line : text) { graphics.centeredText(font, line, 0, row, 0xFFFFFFFF); row += font.lineHeight; }
        for (var line : reading) { graphics.centeredText(font, line, 0, row, 0xFF8EE4CE); row += font.lineHeight; }
        row += 8;
        for (int i = 0; i < feedbackLines; i++) { graphics.text(font, feedback.get(i), left + 12, row, 0xFFCAD6DF); row += font.lineHeight; }
        graphics.pose().popMatrix();
    }
    private static void drawProgress(GuiGraphicsExtractor graphics, Minecraft client) {
        var font = client.font;
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        String label = progressLabel();
        int width = Math.min(screenWidth - 16, font.width(label) + 16);
        var lines = font.split(Component.literal(label), width - 16);
        int height = 12 + lines.size() * font.lineHeight;
        // On small GUI viewports, lift the counter above the centered hotbar/stats.
        int bottomInset = 8 + width > screenWidth / 2 - 99 ? 64 : 8;
        int top = Math.max(8, screenHeight - height - bottomInset);
        graphics.fill(8, top, 8 + width, top + height, 0xE318232B);
        for (int i = 0; i < lines.size(); i++) graphics.text(font, lines.get(i), 16, top + 6 + i * font.lineHeight, 0xFFCAD6DF);
    }
    private static String progressLabel() {
        if (SayItemClient.totalItemCount == 0) return "Unlock progress loading…";
        return (SayItemClient.sharing ? "Shared unlocks: " : "Unlocked: ")
            + SayItemClient.unlockedCount + " / " + SayItemClient.totalItemCount;
    }
}
