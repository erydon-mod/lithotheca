package com.oliver.lithotheca.client;

import com.oliver.lithotheca.Lithotheca;
import com.oliver.lithotheca.catalogue.BlockCatalogue;
import com.oliver.lithotheca.client.screen.LithothecaCreativeToolbar;
import com.oliver.lithotheca.client.screen.LithothecaScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LithothecaClient implements ClientModInitializer {
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        ClientRack.initialize();
        BlockCatalogue.initialize();
        KeyBinding open = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.lithotheca.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, "key.categories.lithotheca"
        ));
        openKey = open;
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> migrateOpenKey(client, open));
        ClientTickEvents.START_CLIENT_TICK.register(ClientRack::tick);
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof CreativeInventoryScreen creative) {
                LithothecaCreativeToolbar.attach(client, creative);
            }
        });
    }

    public static boolean matchesOpenKey(int keyCode, int scanCode) {
        return openKey != null && openKey.matchesKey(keyCode, scanCode);
    }

    public static boolean handleToggleKey(int keyCode, int scanCode, int action, int modifiers) {
        int shortcuts = GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER;
        if ((action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT)
            || (modifiers & shortcuts) != 0 || !matchesOpenKey(keyCode, scanCode)) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        Screen screen = client.currentScreen;
        boolean picker = screen instanceof LithothecaScreen;
        if (screen != null && !picker && !(screen instanceof CreativeInventoryScreen)) {
            return false;
        }
        // Creative's search box manages its own focus without setting Screen.getFocused().
        if (screen != null && screen.children().stream().anyMatch(child -> child instanceof TextFieldWidget text
            && text.isFocused() && text.isVisible() && text.isActive())) {
            return false;
        }
        if (screen instanceof CreativeInventoryScreen && RecipeSearchFocus.isFocused()) {
            return false;
        }
        if (!picker && (client.player == null || client.interactionManager == null
            || !client.interactionManager.hasCreativeInventory()
            || screen == null && (AxiomCompat.ownsGameplayInput() || AxiomCompat.opensAxiomControls(keyCode, scanCode)))) {
            return false;
        }
        if (action == GLFW.GLFW_PRESS) {
            if (picker) {
                screen.close();
            } else {
                client.setScreen(new LithothecaScreen(screen));
            }
        }
        return true;
    }

    private static void migrateOpenKey(MinecraftClient client, KeyBinding open) {
        Path marker = FabricLoader.getInstance().getConfigDir().resolve("lithotheca-open-key-migrated");
        if (Files.exists(marker)) {
            return;
        }
        try {
            Files.createDirectories(marker.getParent());
            if (open.matchesKey(GLFW.GLFW_KEY_B, 0)) {
                open.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_L));
                KeyBinding.updateKeysByCode();
                client.options.write();
            }
            Files.createFile(marker);
        } catch (IOException failure) {
            Lithotheca.LOGGER.warn("Could not record the Lithotheca key migration.", failure);
        }
    }
}
