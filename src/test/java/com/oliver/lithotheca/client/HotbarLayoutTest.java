package com.oliver.lithotheca.client;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.SleepingChatScreen;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotbarLayoutTest {
    @Test
    void chatAndSlashInputKeepTheWorldHotbarAndMakeRoomForTheInputField() {
        ChatScreen chat = new ChatScreen("");
        ChatScreen command = new ChatScreen("/");
        assertTrue(HotbarLayout.keepsWorldHotbar(chat));
        assertTrue(HotbarLayout.keepsWorldHotbar(command));
        assertTrue(HotbarLayout.bottomInset(chat) > 14);
        assertEquals(HotbarLayout.bottomInset(chat), HotbarLayout.bottomInset(command));
    }

    @Test
    void ordinaryMenusAndSleepingUseTheirExistingLayout() {
        assertTrue(HotbarLayout.keepsWorldHotbar(null));
        assertEquals(0, HotbarLayout.bottomInset(null));
        assertFalse(HotbarLayout.keepsWorldHotbar(new GameMenuScreen(false)));
        assertFalse(HotbarLayout.keepsWorldHotbar(new SleepingChatScreen()));
    }
}
