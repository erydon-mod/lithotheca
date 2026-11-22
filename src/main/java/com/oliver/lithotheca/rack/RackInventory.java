package com.oliver.lithotheca.rack;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;

import static com.oliver.lithotheca.rack.RackProtocol.BRIDGE_SLOT;
import static com.oliver.lithotheca.rack.RackProtocol.DEFAULT_EXTRA_SLOTS;
import static com.oliver.lithotheca.rack.RackProtocol.HOTBAR_SIZE;
import static com.oliver.lithotheca.rack.RackProtocol.MAX_EXTRA_SLOTS;

public final class RackInventory {
    private final DefaultedList<ItemStack> extra = DefaultedList.ofSize(MAX_EXTRA_SLOTS, ItemStack.EMPTY);
    private int capacity = DEFAULT_EXTRA_SLOTS;
    private ItemStack pickerCursor = ItemStack.EMPTY;

    // While active, this cell parks the original vanilla stack and the bridge holds the rack stack.
    private int activeExtra = -1;

    public static RackInventory of(ServerPlayerEntity player) {
        return ((Holder) player).lithotheca$getRack();
    }

    int capacity() {
        return capacity;
    }

    boolean hasActiveExtra() {
        return activeExtra >= 0;
    }

    int selected(PlayerInventory inventory) {
        return hasActiveExtra() ? HOTBAR_SIZE + activeExtra : inventory.selectedSlot;
    }

    boolean contains(int slot) {
        return slot >= 0 && slot < HOTBAR_SIZE + capacity;
    }

    ItemStack pickerCursor() {
        return pickerCursor;
    }

    boolean takePickerItem(ItemStack expectedCursor, ItemStack granted) {
        if (!pickerCursor.isEmpty() || !ItemStack.areEqual(pickerCursor, expectedCursor)) return false;
        pickerCursor = granted;
        return true;
    }

    boolean exchangePickerSlot(PlayerInventory inventory, int slot, ItemStack expectedCursor, ItemStack expectedSlot) {
        if (!contains(slot) || !ItemStack.areEqual(pickerCursor, expectedCursor)
            || !ItemStack.areEqual(stack(inventory, slot), expectedSlot)) return false;
        int nextSelection = pickerCursor.isEmpty() ? selected(inventory) : slot;
        normalize(inventory);
        ItemStack displaced = stack(inventory, slot);
        setStack(inventory, slot, pickerCursor);
        pickerCursor = displaced;
        select(inventory, nextSelection);
        inventory.markDirty();
        return true;
    }

    boolean discardPickerCursor(ItemStack expectedCursor) {
        if (!ItemStack.areEqual(pickerCursor, expectedCursor)) return false;
        pickerCursor = ItemStack.EMPTY;
        return true;
    }

    ItemStack stack(PlayerInventory inventory, int slot) {
        if (hasActiveExtra()) {
            if (slot == BRIDGE_SLOT) {
                return extra.get(activeExtra);
            }
            if (slot == HOTBAR_SIZE + activeExtra) {
                return inventory.getStack(BRIDGE_SLOT);
            }
        }
        return slot < HOTBAR_SIZE ? inventory.getStack(slot) : extra.get(slot - HOTBAR_SIZE);
    }

    boolean normalize(PlayerInventory inventory) {
        if (!hasActiveExtra()) {
            return false;
        }

        ItemStack held = inventory.getStack(BRIDGE_SLOT);
        inventory.setStack(BRIDGE_SLOT, extra.get(activeExtra));
        extra.set(activeExtra, held);
        activeExtra = -1;
        inventory.markDirty();
        return true;
    }

    void select(PlayerInventory inventory, int slot) {
        normalize(inventory);
        if (slot < HOTBAR_SIZE) {
            inventory.selectedSlot = slot;
            return;
        }

        int extraSlot = slot - HOTBAR_SIZE;
        ItemStack parked = inventory.getStack(BRIDGE_SLOT);
        inventory.setStack(BRIDGE_SLOT, extra.get(extraSlot));
        extra.set(extraSlot, parked);
        activeExtra = extraSlot;
        inventory.selectedSlot = BRIDGE_SLOT;
        inventory.markDirty();
    }

    void place(PlayerInventory inventory, int slot, ItemStack stack) {
        normalize(inventory);
        setStack(inventory, slot, stack);
        inventory.markDirty();
    }

    int quickAddSlot(PlayerInventory inventory) {
        int last = HOTBAR_SIZE + capacity - 1;
        for (int slot = 0; slot < last; slot++) {
            if (stack(inventory, slot).isEmpty()) {
                return slot;
            }
        }
        return last;
    }

    // Return the changed main inventory slot, or -1 when the changes stay in the hotbar.
    int pick(PlayerInventory inventory, ItemStack picked) {
        int empty = -1;
        for (int slot = 0; slot < HOTBAR_SIZE + capacity; slot++) {
            ItemStack existing = stack(inventory, slot);
            if (existing.isEmpty() && empty < 0) {
                empty = slot;
            } else if (!existing.isEmpty() && ItemStack.canCombine(existing, picked)) {
                select(inventory, slot);
                return -1;
            }
        }

        int destination = empty >= 0 ? empty : pickReplacementSlot(inventory);
        normalize(inventory);
        ItemStack displaced = stack(inventory, destination);
        int source = inventory.getSlotWithStack(picked);
        int changedMainSlot = -1;
        ItemStack replacement = picked;
        if (source >= HOTBAR_SIZE) {
            replacement = inventory.getStack(source);
            changedMainSlot = source;
        } else if (!displaced.isEmpty()) {
            // A displaced stack means the logical hotbar was full, so any vacancy is in the main inventory.
            changedMainSlot = inventory.getEmptySlot();
        }
        if (changedMainSlot >= 0) {
            inventory.setStack(changedMainSlot, displaced);
        }
        setStack(inventory, destination, replacement);
        inventory.markDirty();
        select(inventory, destination);
        return changedMainSlot;
    }

    private int pickReplacementSlot(PlayerInventory inventory) {
        int selected = selected(inventory);
        int size = HOTBAR_SIZE + capacity;
        for (int offset = 0; offset < size; offset++) {
            int slot = (selected + offset) % size;
            if (!stack(inventory, slot).hasEnchantments()) {
                return slot;
            }
        }
        return selected;
    }

    void swap(PlayerInventory inventory, int first, int second) {
        normalize(inventory);
        ItemStack original = stack(inventory, first);
        setStack(inventory, first, stack(inventory, second));
        setStack(inventory, second, original);
        inventory.markDirty();
    }

    boolean resize(PlayerInventory inventory, int newCapacity) {
        if (newCapacity < 0 || newCapacity > MAX_EXTRA_SLOTS) {
            return false;
        }
        for (int i = newCapacity; i < capacity; i++) {
            if (!stack(inventory, HOTBAR_SIZE + i).isEmpty()) {
                return false;
            }
        }

        normalize(inventory);
        capacity = newCapacity;
        return true;
    }

    private void setStack(PlayerInventory inventory, int slot, ItemStack stack) {
        if (slot < HOTBAR_SIZE) {
            inventory.setStack(slot, stack);
        } else {
            extra.set(slot - HOTBAR_SIZE, stack);
        }
    }

    public NbtCompound writeNbt() {
        NbtCompound data = new NbtCompound();
        data.putInt("Capacity", capacity);
        if (!pickerCursor.isEmpty()) data.put("PickerCursor", pickerCursor.writeNbt(new NbtCompound()));
        if (hasActiveExtra()) {
            data.putInt("ActiveExtra", activeExtra);
        }

        NbtList entries = new NbtList();
        for (int slot = 0; slot < capacity; slot++) {
            ItemStack stack = extra.get(slot);
            if (!stack.isEmpty()) {
                NbtCompound entry = stack.writeNbt(new NbtCompound());
                entry.putInt("Slot", slot);
                entries.add(entry);
            }
        }
        data.put("Items", entries);
        return data;
    }

    public void readNbt(NbtCompound data) {
        extra.clear();
        pickerCursor = data.contains("PickerCursor", NbtElement.COMPOUND_TYPE)
            ? ItemStack.fromNbt(data.getCompound("PickerCursor")) : ItemStack.EMPTY;
        int savedCapacity = data.contains("Capacity", NbtElement.NUMBER_TYPE)
                ? data.getInt("Capacity") : DEFAULT_EXTRA_SLOTS;
        capacity = Math.max(0, Math.min(MAX_EXTRA_SLOTS, savedCapacity));

        NbtList entries = data.getList("Items", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < entries.size(); i++) {
            NbtCompound entry = entries.getCompound(i);
            int slot = entry.getInt("Slot");
            if (entry.contains("Slot", NbtElement.NUMBER_TYPE) && slot >= 0 && slot < capacity) {
                extra.set(slot, ItemStack.fromNbt(entry));
            }
        }

        int savedActive = data.contains("ActiveExtra", NbtElement.NUMBER_TYPE) ? data.getInt("ActiveExtra") : -1;
        activeExtra = savedActive >= 0 && savedActive < capacity ? savedActive : -1;
    }

    public void copyFrom(RackInventory old) {
        capacity = old.capacity;
        pickerCursor = old.pickerCursor.copy();
        activeExtra = -1;
        for (int i = 0; i < MAX_EXTRA_SLOTS; i++) {
            extra.set(i, old.extra.get(i).copy());
        }
    }

    public interface Holder {
        RackInventory lithotheca$getRack();
    }
}
