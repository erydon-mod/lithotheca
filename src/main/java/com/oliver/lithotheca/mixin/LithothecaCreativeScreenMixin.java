package com.oliver.lithotheca.mixin;

import com.oliver.lithotheca.client.screen.LithothecaCreativeScreenAccess;
import com.oliver.lithotheca.client.screen.LithothecaCreativeToolbar;
import com.oliver.lithotheca.client.ClientRack;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.item.ItemGroup;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreativeInventoryScreen.class)
abstract class LithothecaCreativeScreenMixin implements LithothecaCreativeScreenAccess {
    @Shadow private static ItemGroup selectedTab;
    @Shadow @Nullable private Slot deleteItemSlot;
    @Shadow private boolean lastClickOutsideBounds;
    @Unique @Nullable private LithothecaCreativeToolbar lithotheca$toolbar;

    @Override
    public @Nullable LithothecaCreativeToolbar lithotheca$getToolbar() {
        return lithotheca$toolbar;
    }

    @Override
    public void lithotheca$setToolbar(@Nullable LithothecaCreativeToolbar toolbar) {
        lithotheca$toolbar = toolbar;
    }

    @Inject(method = "isClickOutsideBounds", at = @At("HEAD"), cancellable = true)
    private void lithotheca$insideRack(double mouseX, double mouseY, int left, int top, int button,
                                       CallbackInfoReturnable<Boolean> result) {
        if (lithotheca$toolbar != null && lithotheca$toolbar.containsSlot(mouseX, mouseY)) {
            lastClickOutsideBounds = false;
            result.setReturnValue(false);
        }
    }

    @Inject(method = "onMouseClick", at = @At("HEAD"), cancellable = true)
    private void lithotheca$clickRack(@Nullable Slot slot, int slotId, int button, SlotActionType type,
                                      CallbackInfo info) {
        if (ClientRack.nativeMenuTransition()) {
            info.cancel();
            return;
        }
        if (lithotheca$toolbar != null && lithotheca$toolbar.handleSlotAction(slot, slotId, button, type,
            selectedTab.getType() == ItemGroup.Type.INVENTORY, deleteItemSlot)) {
            info.cancel();
        }
    }

    @Inject(method = "setSelectedTab", at = @At("HEAD"))
    private void lithotheca$resetRackDrag(ItemGroup group, CallbackInfo info) {
        if (lithotheca$toolbar != null) {
            lithotheca$toolbar.reset();
        }
    }
}
