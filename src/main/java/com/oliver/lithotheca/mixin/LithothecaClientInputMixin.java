package com.oliver.lithotheca.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.oliver.lithotheca.client.AxiomCompat;
import com.oliver.lithotheca.client.ClientRack;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MinecraftClient.class, priority = 900)
public abstract class LithothecaClientInputMixin {
    @Unique private static final boolean lithotheca$HAS_CONQUEST = FabricLoader.getInstance().isModLoaded("conquest");

    @Shadow public HitResult crosshairTarget;

    @Shadow
    private void doItemPick() {
        throw new AssertionError();
    }

    @Inject(method = "doItemPick", at = @At("HEAD"), cancellable = true)
    private void lithotheca$prepareSpecialPick(CallbackInfo callback) {
        // Conquest's Ctrl-pick and Axiom's infinite-reach pick can write a vanilla slot before addPickBlock.
        HitResult hit = crosshairTarget;
        boolean earlyPick = (lithotheca$HAS_CONQUEST && Screen.hasControlDown())
            || (AxiomCompat.installed() && (hit == null || hit.getType() == HitResult.Type.MISS));
        if (earlyPick && lithotheca$deferNativePick()) {
            callback.cancel();
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void lithotheca$openScreen(Screen next, CallbackInfo callback) {
        ClientRack.beforeScreenChange(next);
    }

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void lithotheca$restoreScreenSelection(Screen next, CallbackInfo callback) {
        ClientRack.afterScreenChange();
    }

    @Inject(method = "doItemPick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/player/PlayerInventory;addPickBlock(Lnet/minecraft/item/ItemStack;)V"),
        cancellable = true)
    private void lithotheca$pickIntoRack(CallbackInfo callback, @Local ItemStack stack) {
        if (ClientRack.pickBlock(stack) || lithotheca$deferNativePick()) {
            callback.cancel();
        }
    }

    @Unique
    private boolean lithotheca$deferNativePick() {
        HitResult hit = crosshairTarget;
        return ClientRack.suspend(() -> {
            if (crosshairTarget == hit
                || hit instanceof BlockHitResult before && crosshairTarget instanceof BlockHitResult after
                    && before.getBlockPos().equals(after.getBlockPos()) && before.getSide() == after.getSide()
                || hit instanceof EntityHitResult before && crosshairTarget instanceof EntityHitResult after
                    && before.getEntity() == after.getEntity()) {
                doItemPick();
            }
        });
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void lithotheca$awaitPlacement(CallbackInfo callback) {
        if (ClientRack.pendingSelection()) {
            callback.cancel();
        }
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void lithotheca$awaitAttack(CallbackInfoReturnable<Boolean> callback) {
        if (ClientRack.pendingSelection()) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void lithotheca$awaitBreaking(boolean breaking, CallbackInfo callback) {
        if (breaking && ClientRack.pendingSelection()) {
            callback.cancel();
        }
    }

    @WrapOperation(method = "handleInputEvents", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/option/KeyBinding;wasPressed()Z"),
        slice = @Slice(from = @At(value = "FIELD",
            target = "Lnet/minecraft/client/option/GameOptions;hotbarKeys:[Lnet/minecraft/client/option/KeyBinding;"),
            to = @At(value = "FIELD",
                target = "Lnet/minecraft/client/option/GameOptions;socialInteractionsKey:Lnet/minecraft/client/option/KeyBinding;")))
    private boolean lithotheca$trackHotbarKey(KeyBinding binding, Operation<Boolean> original) {
        boolean pressed = original.call(binding);
        if (pressed) {
            ClientRack.trackHotbarKey(binding);
        }
        return pressed;
    }

    @WrapOperation(method = "handleInputEvents", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/network/ClientPlayerEntity;dropSelectedItem(Z)Z"))
    private boolean lithotheca$awaitDrop(ClientPlayerEntity player, boolean entireStack, Operation<Boolean> original) {
        return !ClientRack.pendingSelection() && original.call(player, entireStack);
    }
}
