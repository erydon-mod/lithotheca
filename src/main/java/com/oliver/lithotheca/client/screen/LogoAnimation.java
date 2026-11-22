package com.oliver.lithotheca.client.screen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.oliver.lithotheca.Lithotheca;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.Util;


import java.io.IOException;

record LogoAnimation(Identifier texture, int frameWidth, int frameHeight, int columns,
                     int frameCount, int fps, int textureWidth, int textureHeight) {
    private static final Identifier PLAQUE = Lithotheca.id("textures/gui/lithotheca-logo-gui.png");
    private static final Identifier PLAQUE_LAYOUT = Lithotheca.id("textures/gui/lithotheca-logo-gui.json");

    static LogoAnimation plaque(ResourceManager resources) {
        var layout = resources.getResource(PLAQUE_LAYOUT);
        if (layout.isEmpty() || resources.getResource(PLAQUE).isEmpty()) {
            return null;
        }
        try (var reader = layout.get().getReader()) {
            JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
            return checked(new LogoAnimation(PLAQUE, JsonHelper.getInt(data, "frame_width"),
                JsonHelper.getInt(data, "frame_height"), JsonHelper.getInt(data, "columns"),
                JsonHelper.getInt(data, "frames"), JsonHelper.getInt(data, "fps"),
                JsonHelper.getInt(data, "texture_width"), JsonHelper.getInt(data, "texture_height")));
        } catch (IOException | RuntimeException exception) {
            Lithotheca.LOGGER.debug("Could not read the animated plaque layout", exception);
            return null;
        }
    }

    private static LogoAnimation checked(LogoAnimation animation) {
        int textureLimit = RenderSystem.maxSupportedTextureSize();
        if (animation.frameWidth <= 0 || animation.frameHeight <= 0 || animation.columns <= 0
            || animation.frameCount <= 0 || animation.fps <= 0 || animation.fps > 60
            || animation.textureWidth <= 0 || animation.textureWidth > textureLimit
            || animation.textureHeight <= 0 || animation.textureHeight > textureLimit
            || (long) animation.columns * animation.frameWidth != animation.textureWidth
            || animation.textureHeight % animation.frameHeight != 0
            || (long) animation.columns * (animation.textureHeight / animation.frameHeight) < animation.frameCount) {
            return null;
        }
        return animation;
    }

    void draw(DrawContext draw, int x, int y, int width, int height) {
        int frame = (int) ((Util.getMeasuringTimeMs() * fps / 1_000L) % frameCount);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        draw.drawTexture(texture, x, y, width, height, (float) ((frame % columns) * frameWidth),
            (float) ((frame / columns) * frameHeight), frameWidth, frameHeight, textureWidth, textureHeight);
        RenderSystem.disableBlend();
    }

    void drawCentered(DrawContext draw, int center, int top, int maxWidth, int maxHeight) {
        int width = Math.min(maxWidth, maxHeight * frameWidth / frameHeight);
        int height = Math.max(1, width * frameHeight / frameWidth);
        draw(draw, center - width / 2, top, width, height);
    }
}
