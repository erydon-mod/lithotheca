package com.oliver.lithotheca.client.screen;

import com.oliver.lithotheca.client.ClientRack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.LinkedHashMap;
import java.util.Map;

/** Runs vanilla slot actions without adding slots to CreativeInventoryScreen's native handler. */
public final class CreativeRackSlotActions {
    private static final int OUTSIDE = -999;
    private static final int FIRST_PLAYER_SLOT = 5;
    private static final int FIRST_MAIN_SLOT = 9;
    private static final int FIRST_HOTBAR_SLOT = 36;
    private static final int OFFHAND_SLOT = 45;

    private final MinecraftClient client;
    private final CreativeInventoryScreen screen;
    private final ClientPlayerEntity player;
    private final MirrorHandler handler;

    public CreativeRackSlotActions(MinecraftClient client, CreativeInventoryScreen screen) {
        this.client = client;
        this.screen = screen;
        this.player = client.player;
        this.handler = new MirrorHandler();
    }

    public Slot extraSlot(int logicalIndex) {
        int index = logicalIndex - ClientRack.NATIVE_SLOTS;
        return index >= 0 && index < ClientRack.capacity() ? handler.extraSlots[index] : null;
    }

    public boolean isExtra(Slot slot) {
        return slot != null && slot.inventory == handler.extraInventory;
    }

    public void reset() {
        if (client.player == player) {
            // An invalid stage ends vanilla's quick craft and clears its remembered slots.
            handler.onSlotClick(OUTSIDE, ScreenHandler.packQuickCraftData(3, 0), SlotActionType.QUICK_CRAFT, player);
        }
    }

    public boolean handle(Slot slot, int slotId, int button, SlotActionType type, boolean inventoryTab, Slot deleteSlot) {
        boolean extra = isExtra(slot);
        if (client.player != player || !ClientRack.creativeEditingAvailable()) {
            if (type == SlotActionType.QUICK_CRAFT) reset();
            return extra;
        }
        handler.inventoryTab = inventoryTab;
        if (inventoryTab && slot != null && slot == deleteSlot) {
            if (type == SlotActionType.QUICK_MOVE) clearExtras();
            return false;
        }

        Slot actionSlot = extra ? slot : nativeSlot(slot, inventoryTab);
        if (type == SlotActionType.QUICK_CRAFT) {
            int stage = ScreenHandler.unpackQuickCraftStage(button);
            if (stage == 2) {
                apply(null, OUTSIDE, button, type);
            } else if (stage == 0 || stage == 1 && actionSlot != null && actionSlot.isEnabled()) {
                handler.onSlotClick(actionSlot == null ? OUTSIDE : actionSlot.id, button, type, player);
            }
            return true;
        }

        boolean rackPickupAll = type == SlotActionType.PICKUP_ALL && actionSlot != null && rackCanCombine();
        if (!extra && !rackPickupAll) return false;
        if (actionSlot.isEnabled() && (type != SlotActionType.SWAP || validSwapButton(button))) {
            apply(actionSlot, actionSlot.id, button, type);
        }
        return true;
    }

    private Slot nativeSlot(Slot visible, boolean inventoryTab) {
        if (visible == null || visible.inventory != player.getInventory()) return null;
        int index = visible.getIndex();
        if (!inventoryTab) {
            if (!PlayerInventory.isValidHotbarIndex(index)) return null;
            index += FIRST_HOTBAR_SLOT;
        }
        return index >= FIRST_PLAYER_SLOT && index <= OFFHAND_SLOT ? handler.nativeSlots[index] : null;
    }

    private boolean rackCanCombine() {
        ItemStack cursor = handler.getCursorStack();
        if (cursor.isEmpty() || cursor.getCount() >= cursor.getMaxCount()) return false;
        for (int index = 0; index < ClientRack.capacity(); index++) {
            ItemStack stack = ClientRack.stack(ClientRack.NATIVE_SLOTS + index);
            if (!stack.isEmpty() && ItemStack.canCombine(cursor, stack)) return true;
        }
        return false;
    }

    private static boolean validSwapButton(int button) {
        return PlayerInventory.isValidHotbarIndex(button) || button == PlayerInventory.OFF_HAND_SLOT;
    }

    private void clearExtras() {
        Map<Integer, ItemStack> changes = new LinkedHashMap<>();
        for (int index = 0; index < ClientRack.capacity(); index++) {
            int logical = ClientRack.NATIVE_SLOTS + index;
            if (!ClientRack.stack(logical).isEmpty()) changes.put(logical, ItemStack.EMPTY);
        }
        ClientRack.editCreativeSlots(changes);
    }

    private void apply(Slot actionSlot, int slotId, int button, SlotActionType type) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] nativeBefore = new ItemStack[inventory.size()];
        for (int index = 0; index < nativeBefore.length; index++) nativeBefore[index] = inventory.getStack(index).copy();
        ItemStack cursorBefore = handler.getCursorStack().copy();
        ItemStack[] rackBefore = new ItemStack[ClientRack.capacity()];
        for (int index = 0; index < rackBefore.length; index++) {
            rackBefore[index] = ClientRack.stack(ClientRack.NATIVE_SLOTS + index).copy();
            handler.extraInventory.setStack(index, rackBefore[index].copy());
        }

        ItemStack thrown = ItemStack.EMPTY;
        handler.simulating = true;
        try {
            ItemStack before = type == SlotActionType.THROW && actionSlot != null
                ? actionSlot.getStack().copy() : ItemStack.EMPTY;
            handler.onSlotClick(slotId, button, type, player);
            if (!before.isEmpty()) {
                int removed = before.getCount() - actionSlot.getStack().getCount();
                if (removed > 0) thrown = before.copyWithCount(removed);
            }
        } finally {
            handler.simulating = false;
        }

        Map<Integer, ItemStack> changes = new LinkedHashMap<>();
        for (int index = 0; index < rackBefore.length; index++) {
            ItemStack stack = handler.extraInventory.getStack(index);
            if (!ItemStack.areEqual(rackBefore[index], stack)) changes.put(ClientRack.NATIVE_SLOTS + index, stack);
        }
        if (!ClientRack.editCreativeSlots(changes)) {
            handler.setCursorStack(cursorBefore);
            for (int index = 0; index < nativeBefore.length; index++) {
                if (!ItemStack.areEqual(nativeBefore[index], inventory.getStack(index))) {
                    inventory.setStack(index, nativeBefore[index]);
                }
            }
            return;
        }

        // The Creative screen's installed listener sends native storage updates using native slot ids.
        player.playerScreenHandler.sendContentUpdates();
        if (!thrown.isEmpty()) client.interactionManager.dropCreativeStack(thrown);
    }

    private final class MirrorHandler extends ScreenHandler {
        private final SimpleInventory extraInventory = new SimpleInventory(ClientRack.MAX_EXTRA_SLOTS);
        private final Slot[] nativeSlots = new Slot[OFFHAND_SLOT + 1];
        private final Slot[] extraSlots = new Slot[ClientRack.MAX_EXTRA_SLOTS];
        private boolean inventoryTab;
        private boolean simulating;

        private MirrorHandler() {
            super(null, 0);
            for (int index = FIRST_PLAYER_SLOT; index <= OFFHAND_SLOT; index++) {
                nativeSlots[index] = addSlot(new NativeStorageSlot(player.playerScreenHandler.getSlot(index), index));
            }
            for (int index = 0; index < extraSlots.length; index++) {
                extraSlots[index] = addSlot(new RackSlot(index));
            }
        }

        @Override
        public boolean canUse(PlayerEntity player) {
            return player == CreativeRackSlotActions.this.player;
        }

        @Override
        public ItemStack getCursorStack() {
            return screen.getScreenHandler().getCursorStack();
        }

        @Override
        public void setCursorStack(ItemStack stack) {
            screen.getScreenHandler().setCursorStack(stack);
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int slotIndex) {
            Slot slot = getSlot(slotIndex);
            if (!isExtra(slot) || !slot.hasStack()) return ItemStack.EMPTY;
            if (!inventoryTab) {
                // Shift-clicking vanilla's own hotbar in a catalogue tab clears that slot.
                slot.setStack(ItemStack.EMPTY);
            } else if (insertItem(slot.getStack(), nativeSlots[FIRST_MAIN_SLOT].id, nativeSlots[FIRST_HOTBAR_SLOT].id, false)) {
                if (slot.getStack().isEmpty()) slot.setStack(ItemStack.EMPTY);
                else slot.markDirty();
            }
            return ItemStack.EMPTY;
        }

        private final class RackSlot extends Slot {
            private final int logicalIndex;

            private RackSlot(int index) {
                super(extraInventory, index, 0, 0);
                logicalIndex = ClientRack.NATIVE_SLOTS + index;
            }

            @Override
            public ItemStack getStack() {
                return simulating ? super.getStack() : ClientRack.stack(logicalIndex);
            }

            @Override
            public boolean isEnabled() {
                return getIndex() < ClientRack.capacity();
            }

            @Override
            public boolean canInsert(ItemStack stack) {
                return isEnabled();
            }

            @Override
            public boolean canTakeItems(PlayerEntity player) {
                return isEnabled();
            }
        }

        private final class NativeStorageSlot extends Slot {
            private final Slot delegate;
            private final int playerSlot;

            private NativeStorageSlot(Slot delegate, int playerSlot) {
                super(delegate.inventory, delegate.getIndex(), delegate.x, delegate.y);
                this.delegate = delegate;
                this.playerSlot = playerSlot;
            }

            @Override
            public boolean isEnabled() {
                return (inventoryTab || playerSlot >= FIRST_HOTBAR_SLOT && playerSlot < OFFHAND_SLOT) && delegate.isEnabled();
            }

            @Override
            public boolean canInsert(ItemStack stack) {
                return isEnabled() && delegate.canInsert(stack);
            }

            @Override
            public boolean canTakeItems(PlayerEntity player) {
                return isEnabled() && delegate.canTakeItems(player);
            }

            @Override
            public int getMaxItemCount(ItemStack stack) {
                return delegate.getMaxItemCount(stack);
            }

            @Override
            public void setStack(ItemStack stack) {
                delegate.setStack(stack);
            }

            @Override
            public void onTakeItem(PlayerEntity player, ItemStack stack) {
                delegate.onTakeItem(player, stack);
            }
        }
    }
}
