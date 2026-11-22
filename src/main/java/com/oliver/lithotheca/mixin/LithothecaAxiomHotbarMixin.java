package com.oliver.lithotheca.mixin;

import com.oliver.lithotheca.client.ClientRack;
import com.oliver.lithotheca.client.HotbarLayout;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.hooks.ScreenRenderHook", remap = false)
public abstract class LithothecaAxiomHotbarMixin {
    @Dynamic("Optional Axiom builder-tool HUD hook")
    @ModifyVariable(method = "renderHotbar", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 0, remap = false)
    private static int lithotheca$moveBuilderSlot(int width) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!HotbarLayout.drawsExtendedBar(client)) {
            return width;
        }
        int slots = ClientRack.NATIVE_SLOTS + ClientRack.capacity();
        int halfWidth = Math.round((slots * HotbarLayout.CELL / 2 + 1) * HotbarLayout.scale(client, width, slots));
        int vanillaHalfWidth = ClientRack.NATIVE_SLOTS * HotbarLayout.CELL / 2 + 1;
        int side = client.player.getMainArm() == Arm.LEFT ? -1 : 1;
        // Axiom uses width / 2 as its anchor, then adds its vanilla hotbar offset.
        return width + 2 * side * (halfWidth - vanillaHalfWidth);
    }

    @Dynamic("Optional Axiom builder-tool HUD hook")
    @ModifyVariable(method = "renderHotbar", at = @At("HEAD"), ordinal = 1, argsOnly = true, require = 0, remap = false)
    private static int lithotheca$raiseBuilderSlotForChat(int height) {
        MinecraftClient client = MinecraftClient.getInstance();
        int inset = HotbarLayout.bottomInset(client.currentScreen);
        return inset > 0 && HotbarLayout.drawsExtendedBar(client) ? height - inset : height;
    }
}
