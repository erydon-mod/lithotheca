package com.oliver.lithotheca.mixin;

import com.oliver.lithotheca.rack.RackNetworking;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class LithothecaServerNetworkMixin {
    @Shadow public ServerPlayerEntity player;

    @Unique
    private boolean lithotheca$nativeMenuNormalized;

    @Inject(method = "onUpdateSelectedSlot", at = @At("TAIL"))
    private void lithotheca$normalizeAfterNativeSelection(UpdateSelectedSlotC2SPacket packet, CallbackInfo ci) {
        RackNetworking.onNativeSelection(player, packet.getSelectedSlot());
    }

    @Inject(method = {"onClickSlot", "onCreativeInventoryAction"}, at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V",
            shift = At.Shift.AFTER))
    private void lithotheca$normalizeBeforeNativeMenuAction(CallbackInfo ci) {
        lithotheca$nativeMenuNormalized = RackNetworking.normalize(player);
    }

    @Inject(method = {"onClickSlot", "onCreativeInventoryAction"}, at = @At("RETURN"))
    private void lithotheca$publishAfterNativeMenuAction(CallbackInfo ci) {
        if (lithotheca$nativeMenuNormalized) {
            lithotheca$nativeMenuNormalized = false;
            RackNetworking.publishState(player);
        }
    }
}
