package com.oliver.lithotheca.client.screen;

import com.oliver.lithotheca.client.ClientRack;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

final class LithothecaSettingsScreen extends Screen {
    private static final int MAX_EXTRA_SLOTS = 18;
    private static final int PANEL_HEIGHT = 142;

    private final Screen parent;
    private int pending;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private LithothecaTheme theme;

    LithothecaSettingsScreen(Screen parent) {
        super(Text.translatable("screen.lithotheca.settings"));
        this.parent = parent;
        this.pending = ClientRack.preferredCapacity();
    }

    @Override
    protected void init() {
        theme = new LithothecaTheme(client);
        panelWidth = Math.min(width - 28, 270);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;

        int sliderWidth = panelWidth - 42;
        int sliderX = (width - sliderWidth) / 2;
        addDrawableChild(new SliderWidget(sliderX, panelTop + 58, sliderWidth, 22,
                slotsText(), (double) pending / MAX_EXTRA_SLOTS) {
            @Override
            protected void updateMessage() {
                setMessage(Text.translatable("screen.lithotheca.extra_slots", (int) Math.round(value * MAX_EXTRA_SLOTS)));
            }

            @Override
            protected void applyValue() {
                pending = (int) Math.round(value * MAX_EXTRA_SLOTS);
            }

            @Override
            public void renderButton(DrawContext draw, int mouseX, int mouseY, float delta) {
                theme.slider(draw, textRenderer, getMessage(), getX(), getY(), width, height, value,
                        isMouseOver(mouseX, mouseY) || isFocused());
            }
        });
        int buttonWidth = Math.min(92, panelWidth / 2 - 14);
        addDrawableChild(theme.new Button(textRenderer, width / 2 - buttonWidth - 3, panelTop + 111,
                buttonWidth, 18, Text.translatable("gui.done"), button -> {
                    ClientRack.setPreferredCapacity(pending);
                    close();
                }));
        addDrawableChild(theme.new Button(textRenderer, width / 2 + 3, panelTop + 111,
                buttonWidth, 18, Text.translatable("gui.cancel"), button -> close()));
    }

    private Text slotsText() {
        return Text.translatable("screen.lithotheca.extra_slots", pending);
    }

    @Override
    public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        theme.backdrop(draw, width, height);
        theme.panel(draw, panelLeft, panelTop, panelWidth, PANEL_HEIGHT);
        theme.heading(draw, textRenderer, Text.translatable("screen.lithotheca.title"), panelLeft + 15, panelTop + 10, panelWidth - 30, 30);
        Text settings = LithothecaTheme.fit(textRenderer, theme.branded(title), panelWidth - 30);
        draw.drawText(textRenderer, settings, (width - textRenderer.getWidth(settings)) / 2,
                panelTop + 44, LithothecaTheme.INK, false);
        draw.drawTextWrapped(textRenderer, Text.translatable("screen.lithotheca.extra_slots_hint"),
                panelLeft + 17, panelTop + 88, panelWidth - 34, LithothecaTheme.MUTED);
        super.render(draw, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
