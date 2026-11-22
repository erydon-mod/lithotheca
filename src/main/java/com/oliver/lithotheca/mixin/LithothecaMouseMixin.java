package com.oliver.lithotheca.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.oliver.lithotheca.client.AxiomCompat;
import com.oliver.lithotheca.client.ClientRack;
import net.minecraft.client.Mouse;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Mouse.class)
public abstract class LithothecaMouseMixin {
    @WrapOperation(method = "onMouseScroll", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/player/PlayerInventory;scrollInHotbar(D)V"))
    private void lithotheca$scrollInWorld(PlayerInventory inventory, double amount, Operation<Void> original) {
        if (!ClientRack.pickingBlock() && !AxiomCompat.scrollBuilderSlot(inventory, amount) && !ClientRack.scroll(amount)) {
            original.call(inventory, amount);
        }
    }
}
