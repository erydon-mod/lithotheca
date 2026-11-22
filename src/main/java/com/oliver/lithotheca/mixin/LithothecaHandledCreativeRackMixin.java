package com.oliver.lithotheca.mixin;

import com.oliver.lithotheca.client.screen.LithothecaCreativeScreenAccess;
import com.oliver.lithotheca.client.screen.LithothecaCreativeToolbar;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
abstract class LithothecaHandledCreativeRackMixin {
    @Shadow @Nullable protected Slot focusedSlot;
    @Shadow protected int x;
    @Shadow protected int y;

    @Inject(method = "getSlotAt", at = @At("HEAD"), cancellable = true)
    private void lithotheca$findRackSlot(double mouseX, double mouseY, CallbackInfoReturnable<Slot> result) {
        if (this instanceof LithothecaCreativeScreenAccess access) {
            LithothecaCreativeToolbar toolbar = access.lithotheca$getToolbar();
            Slot slot = toolbar == null ? null : toolbar.slotAt(mouseX, mouseY);
            if (slot != null) {
                result.setReturnValue(slot);
            }
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;drawForeground(Lnet/minecraft/client/gui/DrawContext;II)V"))
    private void lithotheca$renderRack(DrawContext draw, int mouseX, int mouseY, float delta, CallbackInfo info) {
        if (this instanceof LithothecaCreativeScreenAccess access) {
            LithothecaCreativeToolbar toolbar = access.lithotheca$getToolbar();
            if (toolbar != null) {
                draw.getMatrices().push();
                draw.getMatrices().translate(-x, -y, 0);
                toolbar.renderSlots(draw, mouseX, mouseY);
                draw.getMatrices().pop();
                Slot slot = toolbar.slotAt(mouseX, mouseY);
                if (slot != null) {
                    focusedSlot = slot;
                }
            }
        }
    }
}
