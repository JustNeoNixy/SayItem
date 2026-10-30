package dev.sayitem.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Used for the Gui/Hud API introduced in Minecraft 26.2. */
final class ClientUi {
    private ClientUi() {}
    static Screen screen(Minecraft client) { return client.gui.screen(); }
    static void open(Minecraft client, Screen screen) { client.gui.setScreen(screen); }
    static boolean hudHidden(Minecraft client) { return client.gui.hud.isHidden(); }
}
