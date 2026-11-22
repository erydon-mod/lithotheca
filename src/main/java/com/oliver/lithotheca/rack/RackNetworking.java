package com.oliver.lithotheca.rack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

import net.minecraft.block.entity.BlockEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.OperatorOnlyBlockItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import static com.oliver.lithotheca.rack.RackProtocol.BRIDGE_SLOT;
import static com.oliver.lithotheca.rack.RackProtocol.HOTBAR_SIZE;

public final class RackNetworking {
    private RackNetworking() {
    }

    public static void initialize() {
        register(RackProtocol.REQUEST_STATE, (player, buffer) -> () -> true);
        register(RackProtocol.SELECT, (player, buffer) -> {
            int slot = buffer.readVarInt();
            return () -> select(player, slot);
        });
        register(RackProtocol.PLACE, (player, buffer) -> {
            Identifier itemId = buffer.readIdentifier();
            int slot = buffer.readVarInt();
            return () -> place(player, itemId, slot, false);
        });
        register(RackProtocol.QUICK_ADD, (player, buffer) -> {
            Identifier itemId = buffer.readIdentifier();
            return () -> place(player, itemId, RackInventory.of(player).quickAddSlot(player.getInventory()), true);
        });
        register(RackProtocol.PICK, (player, buffer) -> {
            ItemStack stack = buffer.readItemStack();
            return () -> pick(player, stack);
        });
        register(RackProtocol.CREATIVE_EDIT, (player, buffer) -> {
            int size = buffer.readVarInt();
            if (size < 1 || size > RackProtocol.MAX_EXTRA_SLOTS) {
                return () -> false;
            }
            Map<Integer, ItemStack> changes = new LinkedHashMap<>();
            for (int index = 0; index < size; index++) {
                int slot = buffer.readVarInt();
                if (changes.putIfAbsent(slot, buffer.readItemStack()) != null) {
                    return () -> false;
                }
            }
            return () -> editCreativeSlots(player, changes);
        });
        register(RackProtocol.CURSOR_ACTION, (player, buffer) -> {
            int action = buffer.readVarInt();
            ItemStack expectedCursor = buffer.readItemStack();
            return switch (action) {
                case RackProtocol.CURSOR_TAKE -> {
                    Identifier item = buffer.readIdentifier();
                    yield () -> takePickerItem(player, item, expectedCursor);
                }
                case RackProtocol.CURSOR_EXCHANGE -> {
                    int slot = buffer.readVarInt();
                    ItemStack expectedSlot = buffer.readItemStack();
                    yield () -> exchangePickerSlot(player, slot, expectedCursor, expectedSlot);
                }
                case RackProtocol.CURSOR_DISCARD -> () -> discardPickerCursor(player, expectedCursor);
                default -> () -> false;
            };
        });
        register(RackProtocol.SWAP, (player, buffer) -> {
            int first = buffer.readVarInt();
            int second = buffer.readVarInt();
            return () -> swap(player, first, second);
        });
        register(RackProtocol.REMOVE, (player, buffer) -> {
            int slot = buffer.readVarInt();
            return () -> remove(player, slot);
        });
        register(RackProtocol.CAPACITY, (player, buffer) -> {
            int capacity = buffer.readVarInt();
            return () -> resize(player, capacity);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (normalizeWhenIneligible(player)) {
                    publishState(player);
                }
            }
        });
    }

    private static void register(Identifier channel,
                                 BiFunction<ServerPlayerEntity, PacketByteBuf, BooleanSupplier> decoder) {
        ServerPlayNetworking.registerGlobalReceiver(channel, (server, player, handler, buffer, response) -> {
            int requestId;
            try {
                requestId = buffer.readVarInt();
            } catch (RuntimeException malformed) {
                return;
            }
            if (requestId < 0) {
                return;
            }

            BooleanSupplier action = decode(player, buffer, decoder);
            server.execute(() -> {
                if (player.isRemoved() || player.isDisconnected() || player.networkHandler != handler) {
                    return;
                }
                if (normalizeWhenIneligible(player)) {
                    syncInventory(player);
                }
                sendState(player, requestId, action.getAsBoolean());
            });
        });
    }

    private static BooleanSupplier decode(ServerPlayerEntity player, PacketByteBuf buffer,
                                          BiFunction<ServerPlayerEntity, PacketByteBuf, BooleanSupplier> decoder) {
        try {
            BooleanSupplier action = decoder.apply(player, buffer);
            return buffer.isReadable() ? () -> false : action;
        } catch (RuntimeException malformed) {
            return () -> false;
        }
    }

    private static boolean select(ServerPlayerEntity player, int slot) {
        RackInventory rack = RackInventory.of(player);
        PlayerInventory inventory = player.getInventory();
        if (!isEnabled(player) || !rack.contains(slot) || (slot >= HOTBAR_SIZE && !isPlayerMenu(player))) {
            return false;
        }
        if (rack.selected(inventory) != slot) {
            clearMainHandUse(player);
            rack.select(inventory, slot);
            syncInventory(player);
        }
        return true;
    }

    private static boolean place(ServerPlayerEntity player, Identifier itemId, int slot, boolean quickAdd) {
        RackInventory rack = RackInventory.of(player);
        PlayerInventory inventory = player.getInventory();
        if (!canEdit(player) || !rack.contains(slot) || (!quickAdd && !rack.stack(inventory, slot).isEmpty())) {
            return false;
        }

        Item item = Registries.ITEM.get(itemId);
        ItemStack stack = new ItemStack(item);
        if (!(item instanceof BlockItem) || !isCreativeStackAllowed(player, stack) || !canGrant(player, stack)) {
            return false;
        }

        clearMainHandUse(player);
        rack.place(inventory, slot, stack);
        syncInventory(player);
        return true;
    }

    private static boolean pick(ServerPlayerEntity player, ItemStack stack) {
        if (!canEdit(player) || stack.isEmpty() || !isCreativeStackAllowed(player, stack) || !canGrant(player, stack)) {
            return false;
        }
        // Match Creative's server-side refresh for a pick that carries block-entity coordinates.
        NbtCompound blockData = BlockItem.getBlockEntityNbt(stack);
        if (blockData != null && blockData.contains("x") && blockData.contains("y") && blockData.contains("z")) {
            BlockPos position = BlockEntity.posFromNbt(blockData);
            if (player.getWorld().canSetBlock(position)) {
                BlockEntity blockEntity = player.getWorld().getBlockEntity(position);
                if (blockEntity != null) {
                    blockEntity.setStackNbt(stack);
                }
            }
        }
        RackInventory rack = RackInventory.of(player);
        PlayerInventory inventory = player.getInventory();
        clearMainHandUse(player);
        int movedSlot = rack.pick(inventory, stack);
        syncInventory(player);
        if (movedSlot >= 0) {
            // The Creative catalogue ignores ordinary updates to main inventory slots.
            player.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(
                ScreenHandlerSlotUpdateS2CPacket.UPDATE_PLAYER_INVENTORY_SYNC_ID,
                player.playerScreenHandler.getRevision(), movedSlot, inventory.getStack(movedSlot)));
        }
        return true;
    }

    private static boolean editCreativeSlots(ServerPlayerEntity player, Map<Integer, ItemStack> changes) {
        RackInventory rack = RackInventory.of(player);
        if (!canEdit(player) || rack.hasActiveExtra()) {
            return false;
        }
        for (var change : changes.entrySet()) {
            int slot = change.getKey();
            if (slot < HOTBAR_SIZE || !rack.contains(slot) || !isCreativeStackAllowed(player, change.getValue())) {
                return false;
            }
        }
        PlayerInventory inventory = player.getInventory();
        changes.forEach((slot, stack) -> rack.place(inventory, slot, stack));
        return true;
    }

    private static boolean isCreativeStackAllowed(ServerPlayerEntity player, ItemStack stack) {
        return stack.isEmpty() || stack.getCount() <= 64 && stack.getDamage() >= 0
            && stack.isItemEnabled(player.getWorld().getEnabledFeatures());
    }

    private static boolean takePickerItem(ServerPlayerEntity player, Identifier itemId, ItemStack expectedCursor) {
        if (!canEdit(player)) return false;
        Item item = Registries.ITEM.get(itemId);
        ItemStack granted = new ItemStack(item);
        if (!(item instanceof BlockItem) || !isCreativeStackAllowed(player, granted) || !canGrant(player, granted)) {
            return false;
        }
        boolean accepted = RackInventory.of(player).takePickerItem(expectedCursor, granted);
        syncInventory(player);
        return accepted;
    }

    private static boolean exchangePickerSlot(ServerPlayerEntity player, int slot, ItemStack expectedCursor, ItemStack expectedSlot) {
        if (!canEdit(player)) return false;
        boolean accepted = RackInventory.of(player).exchangePickerSlot(player.getInventory(), slot, expectedCursor, expectedSlot);
        if (accepted) clearMainHandUse(player);
        syncInventory(player);
        return accepted;
    }

    private static boolean discardPickerCursor(ServerPlayerEntity player, ItemStack expectedCursor) {
        if (!canEdit(player)) return false;
        boolean accepted = RackInventory.of(player).discardPickerCursor(expectedCursor);
        syncInventory(player);
        return accepted;
    }

    private static boolean canGrant(ServerPlayerEntity player, ItemStack stack) {
        Item item = stack.getItem();
        // These three ordinary BlockItems are also restricted to vanilla's Operator Utilities tab.
        boolean operatorOnly = item instanceof OperatorOnlyBlockItem
                || item == Items.BARRIER || item == Items.LIGHT || item == Items.STRUCTURE_VOID;
        return !operatorOnly || player.isCreativeLevelTwoOp();
    }

    private static boolean swap(ServerPlayerEntity player, int first, int second) {
        RackInventory rack = RackInventory.of(player);
        if (!canEdit(player) || !rack.contains(first) || !rack.contains(second)) {
            return false;
        }
        if (first != second) {
            clearMainHandUse(player);
            rack.swap(player.getInventory(), first, second);
            syncInventory(player);
        }
        return true;
    }

    private static boolean remove(ServerPlayerEntity player, int slot) {
        RackInventory rack = RackInventory.of(player);
        if (!canEdit(player) || !rack.contains(slot)) {
            return false;
        }
        clearMainHandUse(player);
        rack.place(player.getInventory(), slot, ItemStack.EMPTY);
        syncInventory(player);
        return true;
    }

    private static boolean resize(ServerPlayerEntity player, int capacity) {
        if (!canEdit(player) || !RackInventory.of(player).resize(player.getInventory(), capacity)) {
            return false;
        }
        clearMainHandUse(player);
        syncInventory(player);
        return true;
    }

    public static boolean normalize(ServerPlayerEntity player) {
        if (!RackInventory.of(player).normalize(player.getInventory())) {
            return false;
        }
        clearMainHandUse(player);
        return true;
    }

    public static void onNativeSelection(ServerPlayerEntity player, int slot) {
        if (!PlayerInventory.isValidHotbarIndex(slot) || player.getInventory().selectedSlot != slot) {
            return;
        }

        // The client may echo this slot when vanilla synchronizes the projected hand.
        // An intentional selection of the same physical slot arrives through SELECT.
        if (slot == BRIDGE_SLOT && RackInventory.of(player).hasActiveExtra()) {
            return;
        }
        if (normalize(player)) {
            publishState(player);
        }
    }

    public static void publishState(ServerPlayerEntity player) {
        syncInventory(player);
        sendState(player, -1, true);
    }

    private static boolean normalizeWhenIneligible(ServerPlayerEntity player) {
        RackInventory rack = RackInventory.of(player);
        if (!rack.hasActiveExtra()) {
            return false;
        }
        return (!canEdit(player) || player.getInventory().selectedSlot != BRIDGE_SLOT) && normalize(player);
    }

    private static boolean isEnabled(ServerPlayerEntity player) {
        return player.isCreative() && player.isAlive() && !player.isRemoved();
    }

    private static boolean isPlayerMenu(ServerPlayerEntity player) {
        return player.currentScreenHandler == player.playerScreenHandler;
    }

    private static boolean canEdit(ServerPlayerEntity player) {
        return isEnabled(player) && isPlayerMenu(player);
    }

    private static void clearMainHandUse(ServerPlayerEntity player) {
        if (player.isUsingItem() && player.getActiveHand() == net.minecraft.util.Hand.MAIN_HAND) {
            player.clearActiveItem();
        }
    }

    private static void syncInventory(ServerPlayerEntity player) {
        if (player.networkHandler != null && !player.isDisconnected()) {
            player.playerScreenHandler.sendContentUpdates();
            if (!isPlayerMenu(player)) {
                player.currentScreenHandler.sendContentUpdates();
            }
        }
    }

    private static void sendState(ServerPlayerEntity player, int requestId, boolean accepted) {
        if (player.networkHandler == null || player.isDisconnected() || !ServerPlayNetworking.canSend(player, RackProtocol.STATE)) {
            return;
        }

        RackInventory rack = RackInventory.of(player);
        PlayerInventory inventory = player.getInventory();
        boolean enabled = isEnabled(player);
        int selected = enabled ? rack.selected(inventory) : inventory.selectedSlot;
        if (!rack.contains(selected)) {
            selected = 0;
        }

        PacketByteBuf state = PacketByteBufs.create();
        state.writeVarInt(requestId);
        state.writeBoolean(enabled);
        state.writeVarInt(rack.capacity());
        state.writeVarInt(selected);
        for (int slot = 0; slot < HOTBAR_SIZE + rack.capacity(); slot++) {
            state.writeItemStack(enabled || slot < HOTBAR_SIZE ? rack.stack(inventory, slot) : ItemStack.EMPTY);
        }
        state.writeBoolean(accepted);
        state.writeItemStack(enabled ? rack.pickerCursor() : ItemStack.EMPTY);
        ServerPlayNetworking.send(player, RackProtocol.STATE, state);
    }
}
