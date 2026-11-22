package com.oliver.lithotheca.client;

import com.oliver.lithotheca.rack.RackProtocol;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PickerCursorQueueTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void rapidSwapsKeepTheSamePreviewAfterPartialAcknowledgementAndRejectDropsOnlyQueuedIntentions() {
        PickerCursorQueue queue = new PickerCursorQueue();
        Map<Integer, ItemStack> authoritative = new HashMap<>();
        ItemStack displaced = new ItemStack(Items.GLASS, 13);
        displaced.getOrCreateNbt().putString("TestMarker", "displaced");
        authoritative.put(0, displaced);
        queue.offer(new PickerCursorQueue.Action(RackProtocol.CURSOR_TAKE, new Identifier("minecraft", "stone"), -1));
        queue.offer(new PickerCursorQueue.Action(RackProtocol.CURSOR_EXCHANGE, null, 0));
        queue.offer(new PickerCursorQueue.Action(RackProtocol.CURSOR_EXCHANGE, null, 9));
        rebase(queue, ItemStack.EMPTY, authoritative);
        assertTrue(queue.cursor().isEmpty());
        assertTrue(ItemStack.areEqual(new ItemStack(Items.STONE), queue.slot(0)));
        assertTrue(ItemStack.areEqual(displaced, queue.slot(9)));
        assertTrue(ItemStack.areEqual(displaced, authoritative.get(0)));

        queue.acknowledge(true);
        rebase(queue, new ItemStack(Items.STONE), authoritative);
        assertTrue(queue.cursor().isEmpty());
        assertEquals("displaced", queue.slot(9).getNbt().getString("TestMarker"));

        queue.acknowledge(false);
        rebase(queue, new ItemStack(Items.STONE), authoritative);
        assertTrue(queue.isEmpty());
        assertNull(queue.slot(0));
        assertNull(queue.slot(9));
        assertTrue(ItemStack.areEqual(new ItemStack(Items.STONE), queue.cursor()));
        assertTrue(ItemStack.areEqual(displaced, authoritative.get(0)));
    }

    @Test
    void queueLimitIncludesTheInflightActionAndDiscardReplaysAfterAMove() {
        PickerCursorQueue queue = new PickerCursorQueue();
        for (int index = 0; index < PickerCursorQueue.MAX_ACTIONS - 1; index++) {
            assertTrue(queue.offer(new PickerCursorQueue.Action(RackProtocol.CURSOR_EXCHANGE, null, index % 2)));
        }
        assertFalse(queue.offer(new PickerCursorQueue.Action(RackProtocol.CURSOR_EXCHANGE, null, 0)));
        assertTrue(queue.offer(new PickerCursorQueue.Action(RackProtocol.CURSOR_DISCARD, null, -1)));
        assertFalse(queue.offer(new PickerCursorQueue.Action(RackProtocol.CURSOR_DISCARD, null, -1)));
        rebase(queue, new ItemStack(Items.STONE, 19), Map.of());
        assertTrue(queue.cursor().isEmpty());
        queue.clear();
        assertTrue(queue.isEmpty());
        assertNull(queue.slot(0));
    }

    private static void rebase(PickerCursorQueue queue, ItemStack cursor, Map<Integer, ItemStack> slots) {
        queue.rebase(cursor, 18, slot -> slots.getOrDefault(slot, ItemStack.EMPTY), item -> new ItemStack(Items.STONE));
    }
}
