package com.oliver.lithotheca.mixin;

import com.oliver.lithotheca.client.AxiomCompat;
import com.oliver.lithotheca.client.ClientRack;
import com.oliver.lithotheca.client.LithothecaClient;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Keyboard.class, priority = 1100)
public abstract class LithothecaKeyboardMixin {
    @Shadow
    private void onKey(long window, int key, int scancode, int action, int modifiers) {
        throw new AssertionError();
    }

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void lithotheca$yieldAxiomContext(long window, int key, int scancode, int action, int modifiers,
                                            CallbackInfo callback) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (window != client.getWindow().getHandle()) {
            return;
        }
        if (action == GLFW.GLFW_PRESS
            && client.currentScreen == null && AxiomCompat.opensAxiomControls(key, scancode)
            && ClientRack.suspend(() -> {
                if (key != GLFW.GLFW_KEY_UNKNOWN && InputUtil.isKeyPressed(window, key)) {
                    onKey(window, key, scancode, action, modifiers);
                }
            })) {
            callback.cancel();
            return;
        }
        if (LithothecaClient.handleToggleKey(key, scancode, action, modifiers)) {
            callback.cancel();
        }
    }
}
