package com.oliver.lithotheca.mixin;

import java.util.OptionalInt;

import com.oliver.lithotheca.rack.RackInventory;
import com.oliver.lithotheca.rack.RackNetworking;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class LithothecaPlayerDataMixin implements RackInventory.Holder {
    @Unique
    private static final String LITHOTHECA_RACK_KEY = "LithothecaRack";

    @Unique
    private final RackInventory lithotheca$rack = new RackInventory();

    @Override
    public RackInventory lithotheca$getRack() {
        return lithotheca$rack;
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void lithotheca$readRack(NbtCompound nbt, CallbackInfo ci) {
        lithotheca$rack.readNbt(nbt.getCompound(LITHOTHECA_RACK_KEY));
        RackNetworking.normalize((ServerPlayerEntity) (Object) this);
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void lithotheca$writeRack(NbtCompound nbt, CallbackInfo ci) {
        nbt.put(LITHOTHECA_RACK_KEY, lithotheca$rack.writeNbt());
    }

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void lithotheca$restoreBeforeDeath(DamageSource source, CallbackInfo ci) {
        RackNetworking.normalize((ServerPlayerEntity) (Object) this);
    }

    @Inject(method = "copyFrom", at = @At("HEAD"))
    private void lithotheca$restoreBeforeCopy(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
        RackNetworking.normalize(oldPlayer);
    }

    @Inject(method = "copyFrom", at = @At("TAIL"))
    private void lithotheca$copyRack(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
        lithotheca$rack.copyFrom(RackInventory.of(oldPlayer));
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void lithotheca$restoreBeforeDisconnect(CallbackInfo ci) {
        RackNetworking.normalize((ServerPlayerEntity) (Object) this);
    }

    @Inject(method = "changeGameMode", at = @At("HEAD"))
    private void lithotheca$restoreBeforeLeavingCreative(GameMode gameMode, CallbackInfoReturnable<Boolean> cir) {
        if (gameMode != GameMode.CREATIVE) {
            RackNetworking.normalize((ServerPlayerEntity) (Object) this);
        }
    }

    @Inject(method = "changeGameMode", at = @At("RETURN"))
    private void lithotheca$syncGameMode(GameMode gameMode, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            RackNetworking.publishState((ServerPlayerEntity) (Object) this);
        }
    }

    @Inject(method = "openHandledScreen", at = @At("HEAD"))
    private void lithotheca$restoreBeforeOpeningMenu(NamedScreenHandlerFactory factory,
                                                     CallbackInfoReturnable<OptionalInt> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (RackNetworking.normalize(player)) {
            RackNetworking.publishState(player);
        }
    }
}
