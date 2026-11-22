package com.oliver.lithotheca.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SleepingChatScreen;

public final class HotbarLayout {
    public static final int CELL = 20;
    private static final int SIDE_ICON_MARGIN = 32;
    private static final int CHAT_BOTTOM_INSET = 16;

    private HotbarLayout() {
    }

    public static boolean drawsExtendedBar(MinecraftClient client) {
        return client.player != null && client.getCameraEntity() == client.player
            && keepsWorldHotbar(client.currentScreen) && ClientRack.capacity() > 0 && !AxiomCompat.ownsHotbarLayout();
    }

    public static boolean keepsWorldHotbar(Screen screen) {
        return screen == null || (screen instanceof ChatScreen && !(screen instanceof SleepingChatScreen));
    }

    public static int bottomInset(Screen screen) {
        return screen != null && keepsWorldHotbar(screen) ? CHAT_BOTTOM_INSET : 0;
    }

    public static float scale(MinecraftClient client, int screenWidth, int slots) {
        int margin = AxiomCompat.installed() || (client.player != null && !client.player.getOffHandStack().isEmpty())
            ? SIDE_ICON_MARGIN : 2;
        return Math.min(1.0F, Math.max(1, screenWidth - 2 * margin) / (float) (slots * CELL + 2));
    }
}
