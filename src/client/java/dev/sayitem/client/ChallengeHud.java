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
        if (challenge == null) {
            String message = SayItemClient.engine.isReady()
                ? System.nanoTime() > SayItemClient.resultUntil ? progressLabel() : SayItemClient.status
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
        var reading = challenge.reading().isBlank() ? java.util.List.<net.minecraft.util.FormattedCharSequence>of()
            : font.split(Component.literal(challenge.reading()), width - 24);
        var feedback = font.split(Component.literal(SayItemClient.status), width - 24);
        int feedbackLines = Math.min(2, feedback.size());
        int counterHeight = font.lineHeight + 4;
        int height = 47 + counterHeight + (language.size() + text.size() + reading.size() + feedbackLines) * font.lineHeight;
        // Long custom phrases scale together to stay within even a small game's viewport.
        float scale = Math.min(1f, (float) (client.getWindow().getGuiScaledHeight() - 62) / height);
        graphics.pose().pushMatrix();
        graphics.pose().translate(screenWidth / 2f, y);
        graphics.pose().scale(Math.max(0.4f, scale));
        int left = -width / 2;
        graphics.fill(left, 0, left + width, height, 0xE318232B);
        String timer = (SayItemClient.remaining() + 999) / 1000 + "s · " + SayItemClient.requiredAccuracy + "% required"
            + (challenge.pendingCount() > 0 ? " · " + challenge.pendingCount() + " queued" : "");
        graphics.text(font, progressLabel(), left + 12, 9, 0xFFCAD6DF);
        graphics.text(font, timer, left + 12, 9 + counterHeight, 0xFF8EE4CE);
        int row = 26 + counterHeight;
        for (var line : language) { graphics.centeredText(font, line, 0, row, 0xFFCAD6DF); row += font.lineHeight; }
        row += 4;
        for (var line : text) { graphics.centeredText(font, line, 0, row, 0xFFFFFFFF); row += font.lineHeight; }
        for (var line : reading) { graphics.centeredText(font, line, 0, row, 0xFF8EE4CE); row += font.lineHeight; }
        row += 8;
        for (int i = 0; i < feedbackLines; i++) { graphics.text(font, feedback.get(i), left + 12, row, 0xFFCAD6DF); row += font.lineHeight; }
        graphics.pose().popMatrix();
    }
    private static String progressLabel() {
        if (SayItemClient.totalItemCount == 0) return "Unlock progress loading…";
        return (SayItemClient.sharing ? "Shared unlocks: " : "Unlocked: ")
            + SayItemClient.unlockedCount + " / " + SayItemClient.totalItemCount;
    }
}
