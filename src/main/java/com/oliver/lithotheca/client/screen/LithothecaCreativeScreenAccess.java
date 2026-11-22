package com.oliver.lithotheca.client.screen;

import org.jetbrains.annotations.Nullable;

/** Attaches the detached rack to its owning Creative screen. */
public interface LithothecaCreativeScreenAccess {
    @Nullable LithothecaCreativeToolbar lithotheca$getToolbar();

    void lithotheca$setToolbar(@Nullable LithothecaCreativeToolbar toolbar);
}
