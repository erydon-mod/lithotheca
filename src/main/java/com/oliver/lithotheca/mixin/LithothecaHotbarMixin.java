package com.oliver.lithotheca.mixin;

import com.oliver.lithotheca.client.AxiomCompat;
import com.oliver.lithotheca.client.ClientRack;
import com.oliver.lithotheca.client.HotbarLayout;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.oliver.lithotheca.client.HotbarLayout.CELL;

// Run after Axiom has drawn its builder slot, before replacing the vanilla hotbar.
@Mixin(value = InGameHud.class, priority = 1100)
public abstract class LithothecaHotbarMixin {
    private static final Identifier WIDGETS = new Identifier("minecraft", "textures/gui/widgets.png");

    @Shadow private int scaledWidth;
    @Shadow private int scaledHeight;

    @Shadow
    private void renderHotbarItem(DrawContext context, int x, int y, float tickDelta, PlayerEntity player,
                                  ItemStack stack, int seed) {
        throw new AssertionError();
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void lithotheca$drawHotbar(float tickDelta, DrawContext context, CallbackInfo callback) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!HotbarLayout.drawsExtendedBar(client)) {
            return;
        }
        PlayerEntity player = client.player;
        int total = ClientRack.NATIVE_SLOTS + ClientRack.capacity();
        int selected = ClientRack.selected();
        int right = total * CELL / 2 + 1;
        int left = -right;
        int y = -22;
        float scale = HotbarLayout.scale(client, scaledWidth, total);
        context.getMatrices().push();
        int bottom = scaledHeight - HotbarLayout.bottomInset(client.currentScreen);
        context.getMatrices().translate(scaledWidth / 2, bottom, 0);
        context.getMatrices().scale(scale, scale, 1);

        context.drawTexture(WIDGETS, left, y, 0, 0, 1, 22);
        for (int slot = 0; slot < total; slot++) {
            context.drawTexture(WIDGETS, left + 1 + slot * CELL, y, 1, 0, CELL, 22);
        }
        context.drawTexture(WIDGETS, right - 1, y, 181, 0, 1, 22);
        if (!AxiomCompat.builderSlotActive()) {
            context.drawTexture(WIDGETS, left - 1 + selected * CELL, y - 1, 0, 22, 24, 22);
        }

        for (int slot = 0; slot < total; slot++) {
            renderHotbarItem(context, left + 3 + slot * CELL, y + 3, tickDelta, player, ClientRack.stack(slot), slot + 1);
        }
        ItemStack offhand = player.getOffHandStack();
        if (!offhand.isEmpty()) {
            boolean leftArm = player.getMainArm().getOpposite() == Arm.LEFT;
            int x = leftArm ? left - 29 : right;
            context.drawTexture(WIDGETS, x, y - 1, leftArm ? 24 : 53, 22, 29, 24);
            renderHotbarItem(context, x + (leftArm ? 3 : 10), y + 3, tickDelta, player, offhand, total + 1);
        }
        context.getMatrices().pop();
        callback.cancel();
    }
}
