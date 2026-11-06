package dev.sayitem.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.util.Comparator;

/** Paged native buttons keep every language reachable at small GUI sizes. */
final class LanguageSettingsScreen extends Screen {
    private final Screen parent;
    private int page;
    private int pages = 1;
    private Button syncButton;
    LanguageSettingsScreen(Screen parent) { super(Component.literal("SayItem languages")); this.parent = parent; }
    @Override protected void init() {
        int rowWidth = Math.min(360, width - 32), left = (width - rowWidth) / 2;
        syncButton = addRenderableWidget(Button.builder(syncLabel(), button -> {
            SayItemClient.settings.syncMinecraftLanguage = !SayItemClient.settings.syncMinecraftLanguage;
            try { SayItemClient.settings.save(); }
            catch (IOException error) { SayItemClient.status = "Could not save language settings."; }
            SayItemClient.syncGameLanguage(); button.setMessage(syncLabel());
        }).bounds(left, 36, rowWidth, 20).tooltip(Tooltip.create(Component.literal(
            "Match Minecraft's menus and item names to the challenge language. Timers pause during reloads. Turning this off or leaving restores your previous language."))).build());
        var ids = SayItemClient.languages.keySet().stream().sorted(Comparator.comparing(id ->
            id.equals(dev.sayitem.core.BuiltInLanguages.RANDOM) ? "" : SayItemClient.languages.get(id).getAsString())).toList();
        int columns = width >= 300 ? 2 : 1;
        int rows = Math.max(1, Math.min(6, (height - 130) / 24));
        int pageSize = rows * columns;
        pages = Math.max(1, (ids.size() + pageSize - 1) / pageSize); page = Math.clamp(page, 0, pages - 1);
        int buttonWidth = (rowWidth - (columns - 1) * 4) / columns;
        for (int index = page * pageSize; index < Math.min(ids.size(), (page + 1) * pageSize); index++) {
            String id = ids.get(index), name = SayItemClient.languages.get(id).getAsString();
            int position = index % pageSize;
            var choice = addRenderableWidget(Button.builder(Component.literal(name), button -> {
                SayItemClient.chooseLanguage(id); rebuildWidgets();
            }).bounds(left + (position % columns) * (buttonWidth + 4), 66 + (position / columns) * 24, buttonWidth, 20)
                .tooltip(Tooltip.create(Component.literal(id.equals("random")
                    ? "Choose a language for each item challenge. Retries keep the same choice."
                    : name + " (" + id + ")"))).build());
            choice.active = !SayItemClient.settings.language.equals(id);
        }
        var previous = addRenderableWidget(Button.builder(Component.literal("Previous"), b -> { page--; rebuildWidgets(); })
            .bounds(left, height - 56, 80, 20).build()); previous.active = page > 0;
        var next = addRenderableWidget(Button.builder(Component.literal("Next"), b -> { page++; rebuildWidgets(); })
            .bounds(left + rowWidth - 80, height - 56, 80, 20).build()); next.active = page + 1 < pages;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds((width - 160) / 2, height - 28, 160, 20).build());
    }
    private Component syncLabel() {
        return Component.literal("Match Minecraft language: " + (SayItemClient.settings.syncMinecraftLanguage ? "On" : "Off"));
    }
    @Override public void tick() { if (syncButton != null) syncButton.setMessage(syncLabel()); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { ClientUi.open(Minecraft.getInstance(), parent); }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xED18232B);
        graphics.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal((page + 1) + " / " + pages), width / 2, height - 50, 0xFFCAD6DF);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
