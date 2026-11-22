package com.oliver.lithotheca.client;

import com.oliver.lithotheca.rack.RackProtocol;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;

/** Keeps click intentions, including the in-flight click, separate from their immediate display. */
final class PickerCursorQueue {
    static final int MAX_ACTIONS = 32;

    private final ArrayDeque<Action> actions = new ArrayDeque<>();
    private final Map<Integer, ItemStack> slots = new HashMap<>();
    private ItemStack cursor = ItemStack.EMPTY;

    boolean offer(Action action) {
        // Keep room for a final explicit discard when the user closes the picker during a click burst.
        int limit = action.type() == RackProtocol.CURSOR_DISCARD ? MAX_ACTIONS : MAX_ACTIONS - 1;
        if (actions.size() >= limit) return false;
        actions.addLast(action);
        return true;
    }

    Action first() {
        return actions.peekFirst();
    }

    boolean isEmpty() {
        return actions.isEmpty();
    }

    ItemStack cursor() {
        return cursor;
    }

    ItemStack slot(int index) {
        return slots.get(index);
    }

    void acknowledge(boolean accepted) {
        if (accepted) actions.pollFirst();
        else actions.clear();
    }

    void clear() {
        actions.clear();
        slots.clear();
        cursor = ItemStack.EMPTY;
    }

    void rebase(ItemStack authoritativeCursor, int totalSlots, IntFunction<ItemStack> base,
                Function<Identifier, ItemStack> grant) {
        slots.clear();
        cursor = authoritativeCursor.copy();
        for (Action action : actions) {
            switch (action.type()) {
                case RackProtocol.CURSOR_TAKE -> {
                    if (cursor.isEmpty()) cursor = grant.apply(action.item()).copy();
                }
                case RackProtocol.CURSOR_EXCHANGE -> {
                    if (action.slot() < 0 || action.slot() >= totalSlots) continue;
                    ItemStack displaced = slots.getOrDefault(action.slot(), base.apply(action.slot())).copy();
                    slots.put(action.slot(), cursor);
                    cursor = displaced;
                }
                case RackProtocol.CURSOR_DISCARD -> cursor = ItemStack.EMPTY;
                default -> throw new IllegalStateException("Unknown picker action " + action.type());
            }
        }
    }

    record Action(int type, Identifier item, int slot) {
    }
}
