package com.oliver.lithotheca;

import com.oliver.lithotheca.client.screen.LithothecaCreativeScreenAccess;
import com.oliver.lithotheca.mixin.LithothecaHandledScreenAccessor;
import com.oliver.lithotheca.rack.RackInventory;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryListener;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MixinBindingsTest {
    @Test
    void clientAndServerHooksApplyToMinecraft1201() {
        assertTrue(LithothecaHandledScreenAccessor.class.isAssignableFrom(HandledScreen.class));
        assertTrue(LithothecaCreativeScreenAccess.class.isAssignableFrom(CreativeInventoryScreen.class));
        assertTrue(RackInventory.Holder.class.isAssignableFrom(ServerPlayerEntity.class));
        assertHook(Mouse.class, "lithotheca$scrollInWorld");
        assertHook(Keyboard.class, "lithotheca$yieldAxiomContext");
        assertHook(MinecraftClient.class, "lithotheca$openScreen");
        assertHook(MinecraftClient.class, "lithotheca$restoreScreenSelection");
        assertHook(MinecraftClient.class, "lithotheca$trackHotbarKey");
        assertHook(MinecraftClient.class, "lithotheca$pickIntoRack");
        assertHook(InGameHud.class, "lithotheca$drawHotbar");
        assertHook(HandledScreen.class, "lithotheca$findRackSlot");
        assertHook(HandledScreen.class, "lithotheca$renderRack");
        assertHook(CreativeInventoryScreen.class, "lithotheca$clickRack");
        assertHook(CreativeInventoryListener.class, "lithotheca$protectParkedSlot");
        assertHook(ServerPlayNetworkHandler.class, "lithotheca$normalizeBeforeNativeMenuAction");
    }

    @Test
    void optionalAxiomHookAppliesWhenAxiomIsLoaded() throws ClassNotFoundException {
        org.junit.jupiter.api.Assumptions.assumeTrue(FabricLoader.getInstance().isModLoaded("axiom"));
        Class<?> hook = Class.forName("com.moulberry.axiom.hooks.ScreenRenderHook", false, getClass().getClassLoader());
        assertHook(hook, "lithotheca$moveBuilderSlot");
        assertHook(hook, "lithotheca$raiseBuilderSlotForChat");
    }

    private static void assertHook(Class<?> target, String hook) {
        assertTrue(Arrays.stream(target.getDeclaredMethods()).anyMatch(method -> method.getName().contains(hook)),
            () -> "Missing " + hook + " in " + target.getSimpleName());
    }
}
