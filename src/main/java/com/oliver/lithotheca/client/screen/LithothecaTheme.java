package com.oliver.lithotheca.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

final class LithothecaTheme {
    static final int INK = 0xff201c18;
    static final int MUTED = 0xff504335;
    static final int BRONZE = 0xffa77c4b;
    static final int INSET = 0xffd9c9ae;
    static final int SELECTED = 0xffd8bd92;
    static final int CONTROL_TEXT = 0xfff8f3e8;
    private static final int LIGHT_BRONZE = 0xffe1c995;

    private static final Identifier CINZEL = new Identifier("erydon", "cinzel");
    private static final Identifier CINZEL_RESOURCE = new Identifier("erydon", "font/cinzel.json");
    private static final Identifier ICON = new Identifier("lithotheca", "icon.png");
    private static final Identifier MARBLE = new Identifier("erydon", "textures/gui/glacium_config_background.png");
    private static final Identifier NERIUM = new Identifier("erydon", "textures/gui/nerium_control_background.png");
    private static final Identifier PORTORIUM = new Identifier("erydon", "textures/gui/portorium_control_background.png");
    private static final Identifier PORTORIUM_FRAME = new Identifier("erydon", "textures/gui/portorium_control_frame.png");
    private static final Identifier SLIDER_HANDLE = new Identifier("erydon", "textures/gui/slider_handle_slim.png");

    private final boolean marble;
    private final boolean cinzel;
    private final boolean backdrop;
    private final boolean controls;
    private final boolean frame;
    private final boolean sliderHandle;
    private final LogoAnimation plaque;

    LithothecaTheme(MinecraftClient client) {
        var resources = client.getResourceManager();
        marble = resources.getResource(MARBLE).isPresent();
        cinzel = resources.getResource(CINZEL_RESOURCE).isPresent();
        backdrop = resources.getResource(NERIUM).isPresent();
        controls = resources.getResource(PORTORIUM).isPresent();
        frame = resources.getResource(PORTORIUM_FRAME).isPresent();
        sliderHandle = resources.getResource(SLIDER_HANDLE).isPresent();
        plaque = LogoAnimation.plaque(resources);
    }

    void backdrop(DrawContext draw, int width, int height) {
        if (backdrop) {
            crop(draw, NERIUM, 0, 0, width, height, 0, 0, 1024, 1024, 1024, 1024);
            draw.fill(0, 0, width, height, 0xe4000000);
        } else {
            draw.fill(0, 0, width, height, 0xff201914);
        }
    }

    void heading(DrawContext draw, TextRenderer renderer, Text title, int left, int top, int width, int height) {
        if (drawPlaque(draw, left + width / 2, top, Math.min(144, width), height)) {
            return;
        }
        int iconSize = Math.min(18, height);
        Text label = fit(renderer, branded(title), width - iconSize - 5);
        int labelWidth = renderer.getWidth(label);
        int start = left + (width - iconSize - 5 - labelWidth) / 2;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        draw.drawTexture(ICON, start, top + (height - iconSize) / 2, iconSize, iconSize,
                0f, 0f, 256, 256, 256, 256);
        RenderSystem.disableBlend();
        draw.drawText(renderer, label, start + iconSize + 5, textY(top, height), INK, false);
    }

    boolean drawPlaque(DrawContext draw, int center, int top, int width, int height) {
        if (plaque == null) {
            return false;
        }
        plaque.drawCentered(draw, center, top, width, height);
        return true;
    }

    void panel(DrawContext draw, int left, int top, int width, int height) {
        draw.fill(left - 3, top + 2, left + width + 3, top + height + 4, 0x88000000);
        draw.fill(left, top, left + width, top + height, 0xfff1e6d4);
        if (marble) {
            slices(draw, MARBLE, left, top, width, height, 720, 376, 12, 6, true);
        }
        draw.drawBorder(left, top, width, height, INK);
        draw.drawBorder(left + 2, top + 2, width - 4, height - 4, BRONZE);
    }

    void inset(DrawContext draw, int left, int top, int width, int height) {
        if (width <= 0 || height <= 0) return;
        draw.fill(left, top, left + width, top + height, 0x9f806547);
        draw.fill(left + 2, top + 2, left + width - 2, top + height - 2, 0xe3d6c5a9);
        draw.drawBorder(left, top, width, height, MUTED);
    }

    void control(DrawContext draw, int x, int y, int width, int height, boolean highlighted, boolean selected, boolean enabled) {
        draw.fill(x, y, x + width, y + height, 0xff493421);
        if (controls) {
            int sampleWidth = Math.min(1024, width * 4);
            int sampleHeight = Math.min(1024, height * 4);
            int sourceX = Math.floorMod(x * 13 + y * 7, 1025 - sampleWidth);
            int sourceY = Math.floorMod(x * 5 + y * 17, 1025 - sampleHeight);
            draw.drawTexture(PORTORIUM, x, y, width, height, (float) sourceX, (float) sourceY,
                    sampleWidth, sampleHeight, 1024, 1024);
        }
        int shade = enabled ? highlighted ? 0x4c000000 : 0x77000000 : 0x9f000000;
        draw.fill(x, y, x + width, y + height, shade);
        if (frame) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            slices(draw, PORTORIUM_FRAME, x, y, width, height, 128, 80, 16, 4, false);
            RenderSystem.disableBlend();
        } else {
            draw.drawBorder(x, y, width, height, BRONZE);
        }
        if (selected) {
            draw.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0x32e5a01d);
            draw.drawBorder(x + 1, y + 1, width - 2, height - 2, LIGHT_BRONZE);
        }
    }

    void slider(DrawContext draw, TextRenderer renderer, Text message, int x, int y, int width, int height,
                double value, boolean highlighted) {
        control(draw, x, y, width, height, highlighted, false, true);
        int knobX = x + 5 + (int) Math.round((width - 10) * value);
        int handleTop = y + (height - 11) / 2;
        if (sliderHandle) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            draw.drawTexture(SLIDER_HANDLE, knobX - 3, handleTop, 5, 11, 0f, 0f, 18, 40, 18, 40);
            RenderSystem.disableBlend();
        } else {
            draw.fill(knobX - 2, handleTop, knobX + 3, handleTop + 11, LIGHT_BRONZE);
        }
        Text label = fit(renderer, branded(message), width - 20);
        int labelY = textY(y, height);
        int labelX = x + (width - renderer.getWidth(label)) / 2;
        draw.drawTextWithShadow(renderer, label, labelX, labelY, CONTROL_TEXT);
    }

    private static void slices(DrawContext draw, Identifier texture, int x, int y, int width, int height,
                               int textureWidth, int textureHeight, int sourceBorder, int border, boolean fillCenter) {
        int edgeX = Math.min(border, width / 2);
        int edgeY = Math.min(border, height / 2);
        for (int row = 0; row < 3; row++) {
            int top = row == 0 ? y : row == 1 ? y + edgeY : y + height - edgeY;
            int h = row == 1 ? height - edgeY * 2 : edgeY;
            int v = row == 0 ? 0 : row == 1 ? sourceBorder : textureHeight - sourceBorder;
            int sourceHeight = row == 1 ? textureHeight - sourceBorder * 2 : sourceBorder;
            for (int column = 0; column < 3; column++) {
                int left = column == 0 ? x : column == 1 ? x + edgeX : x + width - edgeX;
                int w = column == 1 ? width - edgeX * 2 : edgeX;
                int u = column == 0 ? 0 : column == 1 ? sourceBorder : textureWidth - sourceBorder;
                int sourceWidth = column == 1 ? textureWidth - sourceBorder * 2 : sourceBorder;
                if (w <= 0 || h <= 0) continue;
                if (row == 1 && column == 1) {
                    if (fillCenter) crop(draw, texture, left, top, w, h, u, v, sourceWidth, sourceHeight, textureWidth, textureHeight);
                } else {
                    draw.drawTexture(texture, left, top, w, h, (float) u, (float) v, sourceWidth, sourceHeight, textureWidth, textureHeight);
                }
            }
        }
    }

    private static void crop(DrawContext draw, Identifier texture, int x, int y, int width, int height,
                             int u, int v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0) return;
        float ratio = width / (float) height;
        int sourceWidth = Math.max(1, Math.min(regionWidth, Math.round(regionHeight * ratio)));
        int sourceHeight = Math.max(1, Math.min(regionHeight, Math.round(regionWidth / ratio)));
        draw.drawTexture(texture, x, y, width, height,
                u + (regionWidth - sourceWidth) / 2f, v + (regionHeight - sourceHeight) / 2f,
                sourceWidth, sourceHeight, textureWidth, textureHeight);
    }

    Text branded(Text text) {
        return cinzel ? text.copy().styled(style -> style.withFont(CINZEL)) : text;
    }

    private int textY(int y, int height) {
        // The visible Cinzel capitals sit above the nominal Minecraft line box.
        return y + Math.max(1, (height - 9) / 2 + (cinzel ? 4 : 1));
    }

    static Text fit(TextRenderer renderer, Text text, int room) {
        if (renderer.getWidth(text) <= room) return text;
        Text ellipsis = Text.literal("…").setStyle(text.getStyle());
        int width = room - renderer.getWidth(ellipsis);
        if (width < 1) return Text.empty();
        return Text.literal(renderer.trimToWidth(text, width).getString() + "…").setStyle(text.getStyle());
    }

    final class Button extends ButtonWidget {
        private final TextRenderer renderer;
        boolean selected;

        Button(TextRenderer renderer, int x, int y, int width, int height, Text message, PressAction action) {
            super(x, y, width, height, message, action, DEFAULT_NARRATION_SUPPLIER);
            this.renderer = renderer;
        }

        @Override
        protected void renderButton(DrawContext draw, int mouseX, int mouseY, float delta) {
            control(draw, getX(), getY(), width, height, hovered || isFocused(), selected, active || selected);
            Text label = fit(renderer, branded(getMessage()), width - 10);
            int x = getX() + (width - renderer.getWidth(label)) / 2;
            int y = textY(getY(), height);
            draw.drawTextWithShadow(renderer, label, x, y, active || selected ? CONTROL_TEXT : 0xffc6bda9);
        }
    }
}
