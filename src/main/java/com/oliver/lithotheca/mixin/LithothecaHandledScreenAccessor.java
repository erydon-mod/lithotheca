package com.oliver.lithotheca.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(HandledScreen.class)
public interface LithothecaHandledScreenAccessor {
    @Accessor("x")
    int lithotheca$getLeft();

    @Accessor("y")
    int lithotheca$getTop();

    @Accessor("backgroundWidth")
    int lithotheca$getBackgroundWidth();

    @Accessor("cursorDragSlots")
    Set<Slot> lithotheca$getCursorDragSlots();

    @Accessor("cursorDragging")
    boolean lithotheca$isCursorDragging();

    @Accessor("heldButtonType")
    int lithotheca$getHeldButtonType();
}
