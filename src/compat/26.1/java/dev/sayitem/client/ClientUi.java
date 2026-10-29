package dev.sayitem.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** The only client calls that changed between the supported 26.x targets. */
final class ClientUi {
    private ClientUi() {}
    static Screen screen(Minecraft client) { return client.screen; }
    static boolean hudHidden(Minecraft client) { return client.options.hideGui; }
}
