package com.oliver.lithotheca.client.screen;

import com.oliver.lithotheca.client.ClientRack;
import com.oliver.lithotheca.mixin.LithothecaHandledScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** Extra storage above the Creative tabs, outside the native slot and overlay lanes. */
public final class LithothecaCreativeToolbar {
    private static final int BUTTON_WIDTH = 54;
    private static final int BUTTON_HEIGHT = 17;
    private static final int MAX_CELL = 16;

    private final MinecraftClient client;
    private final CreativeInventoryScreen screen;
    private final LithothecaHandledScreenAccessor bounds;
    private final CreativeRackSlotActions actions;
    private final LithothecaTheme theme;
    private final int left;
    private final int bottom;
    private final int rackWidth;
    private boolean rackQuickCraft;

    private LithothecaCreativeToolbar(MinecraftClient client, CreativeInventoryScreen screen,
                                      LithothecaHandledScreenAccessor bounds, int bottom) {
        this.client = client;
        this.screen = screen;
        this.bounds = bounds;
        this.actions = new CreativeRackSlotActions(client, screen);
        this.theme = new LithothecaTheme(client);
        this.left = bounds.lithotheca$getLeft();
        this.bottom = bottom;
        this.rackWidth = bounds.lithotheca$getBackgroundWidth() - BUTTON_WIDTH - 5;
    }

    public static void attach(MinecraftClient client, CreativeInventoryScreen screen) {
        LithothecaHandledScreenAccessor bounds = (LithothecaHandledScreenAccessor) screen;
        LithothecaCreativeScreenAccess access = (LithothecaCreativeScreenAccess) screen;
        int bottom = bounds.lithotheca$getTop() - 34;
        if (bottom < BUTTON_HEIGHT) {
            access.lithotheca$setToolbar(null);
            return;
        }
        LithothecaCreativeToolbar toolbar = new LithothecaCreativeToolbar(client, screen, bounds, bottom);
        access.lithotheca$setToolbar(toolbar);
        ButtonWidget logo = toolbar.logoButton();
        Screens.getButtons(screen).add(logo);
        ScreenEvents.beforeRender(screen).register((rendered, draw, mouseX, mouseY, delta) ->
            logo.setY(toolbar.logoTop(toolbar.layout())));
        ScreenEvents.afterRender(screen).register((rendered, draw, mouseX, mouseY, delta) ->
            toolbar.renderEmptySlotTooltip(draw, mouseX, mouseY));
    }

    private int logoTop(Layout layout) {
        int rackHeight = layout == null ? BUTTON_HEIGHT : layout.rows() * layout.cell();
        return bottom - (Math.max(BUTTON_HEIGHT, rackHeight) + BUTTON_HEIGHT) / 2;
    }

    private ButtonWidget logoButton() {
        Text message = Text.translatable("lithotheca.open");
        ButtonWidget logo = new ButtonWidget(left, logoTop(layout()), BUTTON_WIDTH, BUTTON_HEIGHT, message,
            button -> client.setScreen(new LithothecaScreen(screen)), text -> text.get()) {
            @Override
            protected void renderButton(DrawContext draw, int mouseX, int mouseY, float delta) {
                int center = getX() + width / 2;
                if (theme.drawPlaque(draw, center, getY(), width, height)) {
                    if (hovered || isFocused()) {
                        draw.drawBorder(getX(), getY(), width, height, LithothecaTheme.BRONZE);
                    }
                    return;
                }
                theme.control(draw, getX(), getY(), width, height, hovered || isFocused(), false, true);
                Text label = LithothecaTheme.fit(client.textRenderer, theme.branded(getMessage()), width - 6);
                draw.drawCenteredTextWithShadow(client.textRenderer, label, center, getY() + 5,
                    LithothecaTheme.CONTROL_TEXT);
            }
        };
        logo.setTooltip(Tooltip.of(message));
        return logo;
    }

    public void renderSlots(DrawContext draw, int mouseX, int mouseY) {
        Layout layout = layout();
        if (layout == null) {
            return;
        }
        int hover = layout.slotAt(mouseX, mouseY);
        Set<Slot> dragSlots = bounds.lithotheca$getCursorDragSlots();
        ItemStack cursor = screen.getScreenHandler().getCursorStack();
        boolean dragging = bounds.lithotheca$isCursorDragging() && !cursor.isEmpty();
        for (int index = 0; index < layout.slots(); index++) {
            int x = layout.left() + index % layout.columns() * layout.cell();
            int y = layout.top() + index / layout.columns() * layout.cell();
            int size = layout.cell() - 1;
            Slot slot = actions.extraSlot(ClientRack.NATIVE_SLOTS + index);
            ItemStack stack = slot.getStack();
            boolean preview = dragging && dragSlots.contains(slot) && slot.canInsert(cursor)
                && ScreenHandler.canInsertItemIntoSlot(slot, cursor, true);
            if (preview && dragSlots.size() > 1) {
                int count = ScreenHandler.calculateStackSize(dragSlots, bounds.lithotheca$getHeldButtonType(), cursor);
                stack = cursor.copyWithCount(Math.min(stack.getCount() + count, slot.getMaxItemCount(cursor)));
            }
            boolean highlight = index == hover || preview;
            draw.fill(x, y, x + size, y + size, highlight ? LithothecaTheme.SELECTED : LithothecaTheme.INSET);
            draw.drawBorder(x, y, size, size, highlight ? LithothecaTheme.BRONZE : LithothecaTheme.MUTED);
            if (!stack.isEmpty()) {
                draw.getMatrices().push();
                draw.getMatrices().translate(x + 1, y + 1, 0);
                float scale = (size - 2) / 16f;
                draw.getMatrices().scale(scale, scale, 1);
                draw.drawItem(stack, 0, 0);
                if (layout.cell() >= 14) {
                    draw.drawItemInSlot(client.textRenderer, stack, 0, 0);
                }
                draw.getMatrices().pop();
            }
        }
    }

    private void renderEmptySlotTooltip(DrawContext draw, int mouseX, int mouseY) {
        int index = cellAt(mouseX, mouseY);
        if (index >= 0 && screen.getScreenHandler().getCursorStack().isEmpty()
            && ClientRack.stack(ClientRack.NATIVE_SLOTS + index).isEmpty()) {
            Text label = Text.translatable("screen.lithotheca.creative_slot", ClientRack.NATIVE_SLOTS + index + 1);
            draw.drawTooltip(client.textRenderer, label, mouseX, mouseY);
        }
    }

    public @Nullable Slot slotAt(double mouseX, double mouseY) {
        int index = ClientRack.creativeEditingAvailable() ? cellAt(mouseX, mouseY) : -1;
        return index < 0 ? null : actions.extraSlot(ClientRack.NATIVE_SLOTS + index);
    }

    public boolean containsSlot(double mouseX, double mouseY) {
        return cellAt(mouseX, mouseY) >= 0;
    }

    private int cellAt(double mouseX, double mouseY) {
        Layout layout = layout();
        return layout == null ? -1 : layout.slotAt(mouseX, mouseY);
    }

    public boolean handleSlotAction(@Nullable Slot slot, int slotId, int button, SlotActionType type,
                                    boolean inventoryTab, @Nullable Slot deleteSlot) {
        if (type == SlotActionType.QUICK_CRAFT) {
            int stage = ScreenHandler.unpackQuickCraftStage(button);
            if (stage == 0) {
                rackQuickCraft = bounds.lithotheca$getCursorDragSlots().stream().anyMatch(actions::isExtra);
            }
            if (rackQuickCraft) {
                if (stage == 2) {
                    rackQuickCraft = false;
                }
                actions.handle(slot, slotId, button, type, inventoryTab, deleteSlot);
                return true;
            }
            return false;
        }
        if (actions.isExtra(slot)) {
            actions.handle(slot, slotId, button, type, inventoryTab, deleteSlot);
            return true;
        }
        boolean clearTrash = inventoryTab && slot != null && slot == deleteSlot && type == SlotActionType.QUICK_MOVE;
        return (type == SlotActionType.PICKUP_ALL || clearTrash)
            && actions.handle(slot, slotId, button, type, inventoryTab, deleteSlot);
    }

    public void reset() {
        rackQuickCraft = false;
        actions.reset();
    }

    private Layout layout() {
        return layout(left, bottom, rackWidth, ClientRack.capacity());
    }

    static Layout layout(int left, int bottom, int rackWidth, int slots) {
        if (slots == 0) {
            return null;
        }
        int rows = slots > ClientRack.NATIVE_SLOTS && bottom >= 2 * MAX_CELL ? 2 : 1;
        int columns = (slots + rows - 1) / rows;
        int cell = Math.min(MAX_CELL, rackWidth / columns);
        return cell < 5 ? null : new Layout(left + BUTTON_WIDTH + 5, bottom - rows * cell, slots, columns, rows, cell);
    }

    record Layout(int left, int top, int slots, int columns, int rows, int cell) {
        int slotAt(double x, double y) {
            if (x < left || y < top || x >= left + columns * cell || y >= top + rows * cell) {
                return -1;
            }
            int index = ((int) y - top) / cell * columns + ((int) x - left) / cell;
            return index < slots ? index : -1;
        }
    }
}
