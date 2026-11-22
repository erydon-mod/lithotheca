package com.oliver.lithotheca.rack;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.oliver.lithotheca.rack.RackProtocol.BRIDGE_SLOT;
import static com.oliver.lithotheca.rack.RackProtocol.HOTBAR_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RackInventoryTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void switchingExtraSlotsPreservesTheParkedVanillaStackAndCurrentHeldStack() {
        PlayerInventory inventory = new PlayerInventory(null);
        RackInventory rack = new RackInventory();
        inventory.setStack(BRIDGE_SLOT, new ItemStack(Items.STONE, 24));
        rack.place(inventory, HOTBAR_SIZE, new ItemStack(Items.OAK_PLANKS, 17));
        rack.place(inventory, HOTBAR_SIZE + 1, new ItemStack(Items.GLASS, 8));

        rack.select(inventory, HOTBAR_SIZE);
        assertEquals(HOTBAR_SIZE, rack.selected(inventory));
        assertStack(inventory.getStack(BRIDGE_SLOT), Items.OAK_PLANKS, 17);
        assertStack(rack.stack(inventory, BRIDGE_SLOT), Items.STONE, 24);

        inventory.getStack(BRIDGE_SLOT).decrement(1);
        rack.select(inventory, HOTBAR_SIZE + 1);
        assertStack(rack.stack(inventory, HOTBAR_SIZE), Items.OAK_PLANKS, 16);
        assertStack(inventory.getStack(BRIDGE_SLOT), Items.GLASS, 8);

        rack.select(inventory, BRIDGE_SLOT);
        assertEquals(BRIDGE_SLOT, rack.selected(inventory));
        assertStack(inventory.getStack(BRIDGE_SLOT), Items.STONE, 24);
        assertStack(rack.stack(inventory, HOTBAR_SIZE + 1), Items.GLASS, 8);
    }

    @Test
    void projectedSaveRestoresBothStacksOnReadback() {
        PlayerInventory before = new PlayerInventory(null);
        RackInventory rack = new RackInventory();
        before.setStack(BRIDGE_SLOT, new ItemStack(Items.STONE, 24));
        ItemStack extra = new ItemStack(Items.OAK_PLANKS, 17);
        extra.getOrCreateNbt().putString("TestMarker", "persisted");
        rack.place(before, HOTBAR_SIZE, extra);
        rack.select(before, HOTBAR_SIZE);

        PlayerInventory after = new PlayerInventory(null);
        after.readNbt(before.writeNbt(new NbtList()));
        RackInventory loaded = new RackInventory();
        loaded.readNbt(rack.writeNbt());

        assertTrue(loaded.normalize(after));
        assertFalse(loaded.normalize(after));
        assertStack(after.getStack(BRIDGE_SLOT), Items.STONE, 24);
        assertStack(loaded.stack(after, HOTBAR_SIZE), Items.OAK_PLANKS, 17);
        assertEquals("persisted", loaded.stack(after, HOTBAR_SIZE).getNbt().getString("TestMarker"));
    }

    @Test
    void shrinkRequiresEmptyRemovedSlotsAndSwapMovesWholeStacks() {
        PlayerInventory inventory = new PlayerInventory(null);
        RackInventory rack = new RackInventory();
        inventory.setStack(BRIDGE_SLOT, new ItemStack(Items.GLASS, 8));
        assertTrue(rack.resize(inventory, 18));
        rack.place(inventory, HOTBAR_SIZE + 17, new ItemStack(Items.STONE, 32));
        rack.select(inventory, HOTBAR_SIZE + 17);
        assertFalse(rack.resize(inventory, 17));
        assertEquals(18, rack.capacity());
        assertEquals(HOTBAR_SIZE + 17, rack.selected(inventory));

        rack.swap(inventory, HOTBAR_SIZE + 17, 1);
        assertStack(inventory.getStack(1), Items.STONE, 32);
        assertStack(inventory.getStack(BRIDGE_SLOT), Items.GLASS, 8);
        assertTrue(rack.resize(inventory, 0));
        assertFalse(rack.contains(HOTBAR_SIZE));
    }

    @Test
    void quickAddFindsTheFirstEmptySlotThenReplacesTheFinalSlotWhenFull() {
        PlayerInventory inventory = new PlayerInventory(null);
        RackInventory rack = new RackInventory();
        assertTrue(rack.resize(inventory, 2));
        int last = HOTBAR_SIZE + 1;
        for (int slot = 0; slot <= last; slot++) {
            assertEquals(slot, rack.quickAddSlot(inventory));
            rack.place(inventory, slot, new ItemStack(Items.STONE, 2));
        }

        rack.select(inventory, HOTBAR_SIZE);
        assertEquals(last, rack.quickAddSlot(inventory));
        rack.place(inventory, rack.quickAddSlot(inventory), new ItemStack(Items.GLASS));
        assertStack(rack.stack(inventory, last), Items.GLASS, 1);
        assertStack(rack.stack(inventory, BRIDGE_SLOT), Items.STONE, 2);
        assertStack(rack.stack(inventory, HOTBAR_SIZE), Items.STONE, 2);
    }

    @Test
    void quickAddUsesTheLogicalNativeSlotAndWorksWithoutExtras() {
        PlayerInventory inventory = new PlayerInventory(null);
        RackInventory rack = new RackInventory();
        for (int slot = 0; slot < HOTBAR_SIZE - 1; slot++) {
            inventory.setStack(slot, new ItemStack(Items.STONE));
        }
        rack.place(inventory, HOTBAR_SIZE, new ItemStack(Items.GLASS));
        rack.select(inventory, HOTBAR_SIZE);
        assertEquals(BRIDGE_SLOT, rack.quickAddSlot(inventory));

        RackInventory nativeOnly = new RackInventory();
        PlayerInventory nativeInventory = new PlayerInventory(null);
        assertTrue(nativeOnly.resize(nativeInventory, 0));
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            nativeInventory.setStack(slot, new ItemStack(Items.STONE));
        }
        assertEquals(HOTBAR_SIZE - 1, nativeOnly.quickAddSlot(nativeInventory));
    }

    @Test
    void removingTheSelectedExtraLeavesTheParkedNativeStackIntact() {
        PlayerInventory inventory = new PlayerInventory(null);
        RackInventory rack = new RackInventory();
        inventory.setStack(BRIDGE_SLOT, new ItemStack(Items.STONE, 8));
        rack.place(inventory, HOTBAR_SIZE, new ItemStack(Items.GLASS, 2));
        rack.select(inventory, HOTBAR_SIZE);

        rack.place(inventory, HOTBAR_SIZE, ItemStack.EMPTY);

        assertTrue(rack.stack(inventory, HOTBAR_SIZE).isEmpty());
        assertStack(inventory.getStack(BRIDGE_SLOT), Items.STONE, 8);
        assertFalse(rack.hasActiveExtra());
    }

    @Test
    void pickFillsNativeThenExtraSlotsAndKeepsMatchingStacksInPlace() {
        PlayerInventory inventory = new PlayerInventory(null);
        RackInventory rack = new RackInventory();
        for (int slot = 0; slot < HOTBAR_SIZE - 1; slot++) {
            inventory.setStack(slot, new ItemStack(Items.STONE, 8));
        }
        rack.pick(inventory, new ItemStack(Items.GLASS));
        assertEquals(BRIDGE_SLOT, rack.selected(inventory));
        rack.pick(inventory, new ItemStack(Items.CHEST));
        assertEquals(HOTBAR_SIZE, rack.selected(inventory));
        assertStack(rack.stack(inventory, BRIDGE_SLOT), Items.GLASS, 1);

        rack.select(inventory, 0);
        inventory.setStack(0, ItemStack.EMPTY);
        assertEquals(-1, rack.pick(inventory, new ItemStack(Items.CHEST)));
        assertEquals(HOTBAR_SIZE, rack.selected(inventory));
        assertTrue(rack.stack(inventory, 0).isEmpty());
        assertTrue(rack.stack(inventory, HOTBAR_SIZE + 1).isEmpty());
    }

    @Test
    void pickPreservesStackDataAndMovesAnExactMatchFromMainInventory() {
        PlayerInventory inventory = new PlayerInventory(null);
        RackInventory rack = new RackInventory();
        inventory.setStack(0, new ItemStack(Items.CHEST));
        ItemStack chest = new ItemStack(Items.CHEST);
        chest.getOrCreateNbt().putString("TestMarker", "custom");
        inventory.setStack(HOTBAR_SIZE, chest.copyWithCount(3));

        assertEquals(HOTBAR_SIZE, rack.pick(inventory, chest));

        assertEquals(1, rack.selected(inventory));
        assertTrue(inventory.getStack(HOTBAR_SIZE).isEmpty());
        assertStack(rack.stack(inventory, 1), Items.CHEST, 3);
        assertEquals("custom", rack.stack(inventory, 1).getNbt().getString("TestMarker"));
    }

    @Test
    void fullRackPickSkipsAnEnchantedSelectionAndParksTheDisplacedStack() {
        PlayerInventory inventory = new PlayerInventory(null);
        RackInventory rack = new RackInventory();
        fillRack(inventory, rack);
        ItemStack enchanted = enchantedPickaxe();
        ItemStack displaced = new ItemStack(Items.GRANITE, 12);
        displaced.getOrCreateNbt().putString("TestMarker", "displaced");
        rack.place(inventory, HOTBAR_SIZE, enchanted);
        rack.place(inventory, HOTBAR_SIZE + 1, displaced);
        inventory.setStack(HOTBAR_SIZE, new ItemStack(Items.CHEST));
        rack.select(inventory, HOTBAR_SIZE);

        assertEquals(HOTBAR_SIZE + 1, rack.pick(inventory, new ItemStack(Items.GLASS)));

        assertEquals(HOTBAR_SIZE + 1, rack.selected(inventory));
        assertStack(inventory.getStack(BRIDGE_SLOT), Items.GLASS, 1);
        assertSame(enchanted, rack.stack(inventory, HOTBAR_SIZE));
        assertStack(rack.stack(inventory, BRIDGE_SLOT), Items.STONE, BRIDGE_SLOT + 1);
        assertSame(displaced, inventory.getStack(HOTBAR_SIZE + 1));
        assertTrue(inventory.getStack(HOTBAR_SIZE + 2).isEmpty());
    }

    @Test
    void fullRackPickWrapsAndSwapsWithTheExactMainInventorySource() {
        PlayerInventory inventory = new PlayerInventory(null);
        RackInventory rack = new RackInventory();
        fillRack(inventory, rack);
        ItemStack displaced = inventory.getStack(0);
        ItemStack chest = new ItemStack(Items.CHEST, 5);
        chest.getOrCreateNbt().putString("TestMarker", "source");
        int source = HOTBAR_SIZE + 2;
        inventory.setStack(HOTBAR_SIZE + 1, new ItemStack(Items.CHEST));
        inventory.setStack(source, chest);
        rack.place(inventory, HOTBAR_SIZE + 1, enchantedPickaxe());
        rack.select(inventory, HOTBAR_SIZE + 1);

        assertEquals(source, rack.pick(inventory, chest.copyWithCount(1)));

        assertEquals(0, rack.selected(inventory));
        assertSame(chest, inventory.getStack(0));
        assertSame(displaced, inventory.getStack(source));
        assertTrue(inventory.getStack(HOTBAR_SIZE).isEmpty());
        assertStack(inventory.getStack(HOTBAR_SIZE + 1), Items.CHEST, 1);
        assertStack(inventory.getStack(BRIDGE_SLOT), Items.STONE, BRIDGE_SLOT + 1);
    }

    @Test
    void fullInventoryPickReplacesSelectedExtraWhenAllHotbarStacksAreEnchanted() {
        PlayerInventory inventory = new PlayerInventory(null);
        RackInventory rack = new RackInventory();
        assertTrue(rack.resize(inventory, 2));
        for (int slot = 0; slot < HOTBAR_SIZE + rack.capacity(); slot++) {
            rack.place(inventory, slot, enchantedPickaxe());
        }
        for (int slot = HOTBAR_SIZE; slot < inventory.main.size(); slot++) {
            inventory.setStack(slot, new ItemStack(Items.STONE, slot));
        }
        rack.select(inventory, HOTBAR_SIZE + 1);

        assertEquals(-1, rack.pick(inventory, new ItemStack(Items.GLASS)));

        assertEquals(HOTBAR_SIZE + 1, rack.selected(inventory));
        assertStack(inventory.getStack(BRIDGE_SLOT), Items.GLASS, 1);
        for (int slot = 0; slot < HOTBAR_SIZE + 1; slot++) {
            assertTrue(rack.stack(inventory, slot).hasEnchantments());
        }
        for (int slot = HOTBAR_SIZE; slot < inventory.main.size(); slot++) {
            assertStack(inventory.getStack(slot), Items.STONE, slot);
        }
    }

    @Test
    void pickerExchangesWholeNativeAndExtraStacksAndRejectsAStaleDestination() {
        PlayerInventory inventory = new PlayerInventory(null);
        inventory.selectedSlot = 5;
        RackInventory rack = new RackInventory();
        ItemStack stone = new ItemStack(Items.STONE, 23);
        stone.getOrCreateNbt().putString("TestMarker", "native");
        ItemStack glass = new ItemStack(Items.GLASS, 7);
        inventory.setStack(2, stone);
        rack.place(inventory, HOTBAR_SIZE, glass);

        assertTrue(rack.exchangePickerSlot(inventory, 2, ItemStack.EMPTY, stone.copy()));
        assertSame(stone, rack.pickerCursor());
        assertTrue(inventory.getStack(2).isEmpty());
        assertEquals(5, rack.selected(inventory));
        assertFalse(rack.exchangePickerSlot(inventory, HOTBAR_SIZE, stone.copy(), glass.copyWithCount(6)));
        assertSame(stone, rack.pickerCursor());
        assertSame(glass, rack.stack(inventory, HOTBAR_SIZE));
        assertEquals(5, rack.selected(inventory));

        assertTrue(rack.exchangePickerSlot(inventory, HOTBAR_SIZE, stone.copy(), glass.copy()));
        assertSame(stone, rack.stack(inventory, HOTBAR_SIZE));
        assertSame(glass, rack.pickerCursor());
        assertEquals(HOTBAR_SIZE, rack.selected(inventory));
        assertSame(stone, inventory.getStack(BRIDGE_SLOT));
        assertTrue(rack.exchangePickerSlot(inventory, 2, glass.copy(), ItemStack.EMPTY));
        assertTrue(rack.pickerCursor().isEmpty());
        assertSame(glass, inventory.getStack(2));
        assertEquals(2, rack.selected(inventory));
        assertEquals(2, inventory.selectedSlot);
        assertEquals("native", rack.stack(inventory, HOTBAR_SIZE).getNbt().getString("TestMarker"));
    }

    @Test
    void pickerCursorPersistsCopiesAndDiscardsOnlyTheExpectedStack() {
        PlayerInventory inventory = new PlayerInventory(null);
        RackInventory rack = new RackInventory();
        ItemStack tool = enchantedPickaxe();
        tool.getOrCreateNbt().putString("TestMarker", "held");
        inventory.setStack(0, tool);
        assertTrue(rack.exchangePickerSlot(inventory, 0, ItemStack.EMPTY, tool.copy()));

        RackInventory loaded = new RackInventory();
        loaded.readNbt(rack.writeNbt());
        assertTrue(ItemStack.areEqual(tool, loaded.pickerCursor()));
        RackInventory respawn = new RackInventory();
        respawn.copyFrom(loaded);
        loaded.pickerCursor().getOrCreateNbt().putString("TestMarker", "changed");
        assertEquals("held", respawn.pickerCursor().getNbt().getString("TestMarker"));
        assertFalse(respawn.discardPickerCursor(loaded.pickerCursor()));
        assertTrue(respawn.discardPickerCursor(tool.copy()));
        assertTrue(respawn.pickerCursor().isEmpty());

        loaded.readNbt(new net.minecraft.nbt.NbtCompound());
        assertTrue(loaded.pickerCursor().isEmpty());
    }

    @Test
    void pickerTakeRequiresEmptyExpectedCursorAndExchangeNormalizesTheBridge() {
        PlayerInventory inventory = new PlayerInventory(null);
        RackInventory rack = new RackInventory();
        inventory.setStack(BRIDGE_SLOT, new ItemStack(Items.STONE, 8));
        rack.place(inventory, HOTBAR_SIZE, new ItemStack(Items.GLASS, 4));
        rack.select(inventory, HOTBAR_SIZE);
        ItemStack granted = new ItemStack(Items.CHEST);
        assertTrue(rack.takePickerItem(ItemStack.EMPTY, granted));
        assertEquals(HOTBAR_SIZE, rack.selected(inventory));
        assertFalse(rack.takePickerItem(granted.copy(), new ItemStack(Items.OAK_PLANKS)));
        assertEquals(HOTBAR_SIZE, rack.selected(inventory));

        assertTrue(rack.exchangePickerSlot(inventory, BRIDGE_SLOT, granted.copy(), new ItemStack(Items.STONE, 8)));
        assertFalse(rack.hasActiveExtra());
        assertSame(granted, inventory.getStack(BRIDGE_SLOT));
        assertStack(rack.pickerCursor(), Items.STONE, 8);
        assertStack(rack.stack(inventory, HOTBAR_SIZE), Items.GLASS, 4);
    }

    @Test
    void pickerPickupClearDiscardAndRejectedSwapKeepTheExistingExtraSelection() {
        PlayerInventory inventory = new PlayerInventory(null);
        RackInventory rack = new RackInventory();
        ItemStack parked = new ItemStack(Items.STONE, 8);
        ItemStack heldExtra = new ItemStack(Items.GLASS, 4);
        ItemStack nativeStack = new ItemStack(Items.CHEST, 3);
        inventory.setStack(BRIDGE_SLOT, parked);
        inventory.setStack(2, nativeStack);
        rack.place(inventory, HOTBAR_SIZE, heldExtra);
        rack.select(inventory, HOTBAR_SIZE);

        assertTrue(rack.exchangePickerSlot(inventory, 2, ItemStack.EMPTY, nativeStack.copy()));
        assertEquals(HOTBAR_SIZE, rack.selected(inventory));
        assertSame(heldExtra, inventory.getStack(BRIDGE_SLOT));
        assertSame(parked, rack.stack(inventory, BRIDGE_SLOT));
        assertTrue(rack.discardPickerCursor(nativeStack.copy()));
        assertEquals(HOTBAR_SIZE, rack.selected(inventory));

        assertFalse(rack.exchangePickerSlot(inventory, HOTBAR_SIZE, ItemStack.EMPTY, heldExtra.copyWithCount(3)));
        assertEquals(HOTBAR_SIZE, rack.selected(inventory));
        assertSame(heldExtra, inventory.getStack(BRIDGE_SLOT));

        assertTrue(rack.exchangePickerSlot(inventory, HOTBAR_SIZE, ItemStack.EMPTY, heldExtra.copy()));
        assertEquals(HOTBAR_SIZE, rack.selected(inventory));
        assertTrue(inventory.getStack(BRIDGE_SLOT).isEmpty());
        assertSame(heldExtra, rack.pickerCursor());
        assertSame(parked, rack.stack(inventory, BRIDGE_SLOT));
    }

    private static void fillRack(PlayerInventory inventory, RackInventory rack) {
        assertTrue(rack.resize(inventory, 2));
        for (int slot = 0; slot < HOTBAR_SIZE + rack.capacity(); slot++) {
            rack.place(inventory, slot, new ItemStack(Items.STONE, slot + 1));
        }
    }

    private static ItemStack enchantedPickaxe() {
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        stack.addEnchantment(Enchantments.UNBREAKING, 1);
        return stack;
    }

    private static void assertStack(ItemStack stack, Item item, int count) {
        assertSame(item, stack.getItem());
        assertEquals(count, stack.getCount());
    }
}
