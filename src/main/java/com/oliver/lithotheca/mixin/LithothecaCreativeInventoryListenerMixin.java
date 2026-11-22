package com.oliver.lithotheca.mixin;

import com.oliver.lithotheca.client.ClientRack;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryListener;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeInventoryListener.class)
abstract class LithothecaCreativeInventoryListenerMixin {
    @Inject(method = "onSlotUpdate", at = @At("HEAD"), cancellable = true)
    private void lithotheca$protectParkedSlot(ScreenHandler handler, int slot, ItemStack stack, CallbackInfo info) {
        // Adding Creative's listener flushes tracked changes before our normalization acknowledgement.
        if (ClientRack.suppressCreativeSlotUpdate(slot)) {
            info.cancel();
        }
    }
}
