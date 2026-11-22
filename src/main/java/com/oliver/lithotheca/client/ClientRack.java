package com.oliver.lithotheca.client;

import com.oliver.lithotheca.Lithotheca;
import com.oliver.lithotheca.client.screen.LithothecaScreen;
import com.oliver.lithotheca.rack.RackProtocol;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.BlockItem;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ClientRack {
    public static final int NATIVE_SLOTS = RackProtocol.HOTBAR_SIZE;
    public static final int MAX_EXTRA_SLOTS = RackProtocol.MAX_EXTRA_SLOTS;
    private static final int BRIDGE = RackProtocol.BRIDGE_SLOT;
    private static final int MAX_CUSTOM_PAYLOAD = 32_767;
    private static final Path PREFERENCE = FabricLoader.getInstance().getConfigDir().resolve("lithotheca-slots.txt");
    private static final ArrayDeque<Identifier> QUICK_ADDS = new ArrayDeque<>();
    private static final Map<Integer, ItemStack> CREATIVE_PREDICTIONS = new HashMap<>();
    private static final PickerCursorQueue PICKER_ACTIONS = new PickerCursorQueue();
    private static final ScreenSelection SCREEN_SELECTION = new ScreenSelection();

    private static State state = State.EMPTY;
    private static int preferredCapacity = loadPreference();
    private static int sequence;
    private static int pendingSelection = -1;
    private static int targetSelection = -1;
    private static int queuedSelection = -1;
    private static boolean queuedNormalization;
    private static int pendingMutation = -1;
    private static int latestCreativeEdit = -1;
    private static int pendingPickerAction = -1;
    private static boolean pendingPickerSelectsSlot;
    private static int deferredCapacity = -1;
    private static boolean pickingBlock;
    private static ItemStack queuedPick = ItemStack.EMPTY;
    private static boolean creativeLastTick;
    private static boolean capacityOffered;
    private static ClientPlayerEntity observedPlayer;
    private static ClientPlayerEntity screenPlayer;
    private static ClientWorld screenWorld;
    private static Text status = Text.empty();
    private static Runnable afterNative;

    private ClientRack() {
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(RackProtocol.STATE, (client, handler, buffer, sender) -> {
            int request = buffer.readVarInt();
            boolean enabled = buffer.readBoolean();
            int capacity = buffer.readVarInt();
            int selected = buffer.readVarInt();
            if (capacity < 0 || capacity > MAX_EXTRA_SLOTS || selected < 0 || selected >= NATIVE_SLOTS + capacity) {
                Lithotheca.LOGGER.warn("Ignoring an invalid Lithotheca slot snapshot.");
                return;
            }
            List<ItemStack> stacks = new ArrayList<>(NATIVE_SLOTS + capacity);
            for (int index = 0; index < NATIVE_SLOTS + capacity; index++) {
                stacks.add(buffer.readItemStack());
            }
            boolean accepted = buffer.readBoolean();
            ItemStack cursor = buffer.isReadable() ? buffer.readItemStack() : ItemStack.EMPTY;
            State incoming = new State(enabled, capacity, selected, List.copyOf(stacks), cursor);
            client.execute(() -> {
                if (client.getNetworkHandler() == handler) {
                    receive(client, request, accepted, incoming);
                }
            });
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            reset();
            observedPlayer = client.player;
            creativeLastTick = creative(client);
            if (ClientPlayNetworking.canSend(RackProtocol.CAPACITY) && creativeLastTick) {
                offerPreferredCapacity();
            } else {
                refresh();
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static boolean available() {
        return state.enabled() && creative(MinecraftClient.getInstance());
    }

    public static int capacity() {
        return available() ? state.capacity() : 0;
    }

    public static int selected() {
        int actual = actualSelection();
        return available() ? SCREEN_SELECTION.selected(actual) : actual;
    }

    private static int actualSelection() {
        PlayerInventory inventory = inventory();
        if (inventory == null) {
            return 0;
        }
        return available() && projected() && inventory.selectedSlot == BRIDGE
            ? state.selected()
            : inventory.selectedSlot;
    }

    public static ItemStack stack(int index) {
        ItemStack prediction = PICKER_ACTIONS.slot(index);
        if (prediction != null && available()) return prediction;
        return baseStack(index);
    }

    private static ItemStack baseStack(int index) {
        PlayerInventory inventory = inventory();
        if (inventory == null || index < 0 || index >= NATIVE_SLOTS + capacity()) {
            return ItemStack.EMPTY;
        }
        if (index == BRIDGE && available() && projected()) {
            return state.stacks().get(BRIDGE);
        }
        if (index < NATIVE_SLOTS) {
            return inventory.getStack(index);
        }
        if (index == state.selected() && inventory.selectedSlot == BRIDGE) {
            return inventory.getStack(BRIDGE);
        }
        return CREATIVE_PREDICTIONS.getOrDefault(index, state.stacks().get(index));
    }

    public static ItemStack pickerCursor() {
        return available() ? PICKER_ACTIONS.cursor() : ItemStack.EMPTY;
    }

    public static boolean pickerCursorAvailable() {
        return available() && ClientPlayNetworking.canSend(RackProtocol.CURSOR_ACTION);
    }

    public static void takePickerItem(Identifier item) {
        if (!(Registries.ITEM.get(item) instanceof BlockItem) || !pickerCursor().isEmpty()) return;
        enqueuePickerAction(new PickerCursorQueue.Action(RackProtocol.CURSOR_TAKE, item, -1));
    }

    public static void exchangePickerSlot(int index) {
        if (index < 0 || index >= NATIVE_SLOTS + capacity()) return;
        enqueuePickerAction(new PickerCursorQueue.Action(RackProtocol.CURSOR_EXCHANGE, null, index));
    }

    public static void discardPickerCursor() {
        if (!pickerCursor().isEmpty()) {
            enqueuePickerAction(new PickerCursorQueue.Action(RackProtocol.CURSOR_DISCARD, null, -1));
        }
    }

    public static void refresh() {
        send(RackProtocol.REQUEST_STATE, buffer -> {});
    }

    public static void place(Identifier item, int index) {
        mutate(RackProtocol.PLACE, buffer -> {
            buffer.writeIdentifier(item);
            buffer.writeVarInt(index);
        });
    }

    public static void quickAdd(Identifier item) {
        if (available() && ClientPlayNetworking.canSend(RackProtocol.QUICK_ADD)
            && QUICK_ADDS.size() < NATIVE_SLOTS + MAX_EXTRA_SLOTS) {
            QUICK_ADDS.addLast(item);
            sendNextQuickAdd();
        }
    }

    public static boolean pickBlock(ItemStack stack) {
        if (capacity() == 0 || AxiomCompat.ownsGameplayInput() || !ClientPlayNetworking.canSend(RackProtocol.PICK)) {
            return false;
        }
        if (pendingSelection >= 0 || pendingMutation >= 0 || !PICKER_ACTIONS.isEmpty()) {
            queuedPick = stack.copy();
            return true;
        }
        int request = send(RackProtocol.PICK, buffer -> buffer.writeItemStack(stack));
        if (request < 0) {
            return false;
        }
        // Pick Block changes the selected slot and can also change the hotbar contents.
        pendingMutation = request;
        pendingSelection = request;
        targetSelection = selected();
        pickingBlock = true;
        return true;
    }

    public static boolean pickingBlock() {
        return pickingBlock;
    }

    public static void swap(int from, int to) {
        mutate(RackProtocol.SWAP, buffer -> {
            buffer.writeVarInt(from);
            buffer.writeVarInt(to);
        });
    }

    public static void remove(int index) {
        mutate(RackProtocol.REMOVE, buffer -> buffer.writeVarInt(index));
    }

    public static boolean creativeEditingAvailable() {
        return capacity() > 0 && !projected() && pendingSelection < 0 && pendingMutation < 0
            && PICKER_ACTIONS.isEmpty()
            && ClientPlayNetworking.canSend(RackProtocol.CREATIVE_EDIT);
    }

    public static boolean editCreativeSlots(Map<Integer, ItemStack> changes) {
        if (changes.isEmpty()) {
            return true;
        }
        if (!creativeEditingAvailable()) {
            return false;
        }
        int request = send(RackProtocol.CREATIVE_EDIT, buffer -> {
            buffer.writeVarInt(changes.size());
            changes.forEach((slot, stack) -> {
                buffer.writeVarInt(slot);
                buffer.writeItemStack(stack);
            });
        });
        if (request < 0) {
            MinecraftClient.getInstance().inGameHud.setOverlayMessage(Text.translatable("lithotheca.rack.rejected"), false);
            return false;
        }
        changes.forEach((slot, stack) -> CREATIVE_PREDICTIONS.put(slot, stack.copy()));
        latestCreativeEdit = request;
        return true;
    }

    public static void select(int index) {
        select(index, false);
    }

    private static void select(int index, boolean normalizing) {
        if (!available() || index < 0 || index >= NATIVE_SLOTS + capacity()) {
            return;
        }
        if (!normalizing) {
            SCREEN_SELECTION.choose(index);
        }
        if (pendingSelection >= 0) {
            queuedSelection = index;
            queuedNormalization = normalizing;
            return;
        }
        if (index != actualSelection() || index < NATIVE_SLOTS && projected()) {
            requestSelection(index, normalizing);
        }
    }

    public static boolean scroll(double amount) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!available() || capacity() == 0 || amount == 0 || client.currentScreen != null
            || AxiomCompat.ownsGameplayInput()) {
            return false;
        }
        int from = scrollCursor();
        int to = Math.floorMod(from - (int) Math.signum(amount), NATIVE_SLOTS + capacity());
        if (pendingSelection < 0 && !projected() && from < NATIVE_SLOTS && to < NATIVE_SLOTS) {
            return false;
        }
        select(to);
        return true;
    }

    public static int scrollCursor() {
        if (queuedSelection >= 0 && !queuedNormalization) {
            return queuedSelection;
        }
        if (SCREEN_SELECTION.active()) {
            return selected();
        }
        return pendingSelection >= 0 ? targetSelection : selected();
    }

    public static void trackHotbarKey(KeyBinding binding) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!available() || client.currentScreen != null || AxiomCompat.ownsGameplayInput()
            || (!projected() && pendingSelection < 0 && !SCREEN_SELECTION.active())
            || client.options.saveToolbarActivatorKey.isPressed() || client.options.loadToolbarActivatorKey.isPressed()) {
            return;
        }
        for (int index = 0; index < client.options.hotbarKeys.length; index++) {
            if (client.options.hotbarKeys[index] == binding) {
                select(index);
                return;
            }
        }
    }

    public static boolean pendingSelection() {
        return pendingSelection >= 0;
    }

    public static boolean nativeMenuTransition() {
        return projected() || pendingSelection >= 0 || pendingPickerSelectsSlot;
    }

    public static boolean suppressCreativeSlotUpdate(int slot) {
        return slot == 36 + BRIDGE && nativeMenuTransition();
    }

    public static void suspend() {
        suspend(null);
    }

    public static boolean suspend(Runnable actionAfterNative) {
        clearScreenSelection();
        return normalizeSelection(actionAfterNative);
    }

    private static boolean normalizeSelection(Runnable actionAfterNative) {
        if (!available() || (!projected() && pendingSelection < 0)) {
            return false;
        }
        if (actionAfterNative != null) {
            afterNative = actionAfterNative;
        }
        PlayerInventory inventory = inventory();
        int nativeSlot = inventory == null ? BRIDGE : inventory.selectedSlot;
        if (pendingSelection < 0) {
            requestSelection(nativeSlot, true);
        } else if (pickingBlock || targetSelection >= NATIVE_SLOTS || queuedSelection >= NATIVE_SLOTS) {
            queuedSelection = nativeSlot;
            queuedNormalization = true;
            SCREEN_SELECTION.normalizeTo(nativeSlot);
        }
        return true;
    }

    public static void beforeScreenChange(Screen next) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!available() || AxiomCompat.ownsGameplayInput()) {
            clearScreenSelection();
            return;
        }
        if (!supportsRackSelection(next)) {
            rememberScreenSelection(client);
            normalizeSelection(null);
        }
    }

    public static void afterScreenChange() {
        resumeScreenSelection(MinecraftClient.getInstance());
    }

    private static boolean supportsRackSelection(Screen screen) {
        return HotbarLayout.keepsWorldHotbar(screen) || screen instanceof LithothecaScreen;
    }

    private static void rememberScreenSelection(MinecraftClient client) {
        if (screenPlayer != client.player || screenWorld != client.world) {
            clearScreenSelection();
        }
        boolean pickCanSelect = pickingBlock && (queuedSelection < 0 || queuedNormalization);
        SCREEN_SELECTION.remember(scrollCursor(), client.player.getInventory().selectedSlot, pickCanSelect);
        screenPlayer = client.player;
        screenWorld = client.world;
    }

    private static void resumeScreenSelection(MinecraftClient client) {
        if (!SCREEN_SELECTION.active()) {
            return;
        }
        if (!available() || screenPlayer != client.player || screenWorld != client.world
            || AxiomCompat.ownsGameplayInput()) {
            clearScreenSelection();
            return;
        }
        if (!supportsRackSelection(client.currentScreen) || pendingSelection >= 0 || pendingMutation >= 0
            || !PICKER_ACTIONS.isEmpty()) {
            return;
        }
        int target = SCREEN_SELECTION.restoreTarget(actualSelection(), NATIVE_SLOTS + capacity());
        if (target >= 0) {
            requestSelection(target, false);
        } else {
            clearScreenSelection();
        }
    }

    private static void clearScreenSelection() {
        SCREEN_SELECTION.clear();
        screenPlayer = null;
        screenWorld = null;
    }

    public static void tick(MinecraftClient client) {
        if (client.player != observedPlayer) {
            clearPlayerState();
            observedPlayer = client.player;
        }
        if (!(client.currentScreen instanceof LithothecaScreen) || !available()) {
            QUICK_ADDS.clear();
        }
        if (client.currentScreen != null || !available()) {
            queuedPick = ItemStack.EMPTY;
        }
        boolean creative = creative(client);
        if (creative && !capacityOffered && ClientPlayNetworking.canSend(RackProtocol.CAPACITY)) {
            offerPreferredCapacity();
        }
        if (creative != creativeLastTick) {
            creativeLastTick = creative;
            if (creative) {
                refresh();
            }
        }
        sendNextPickerAction();
        resumeScreenSelection(client);
        if (available() && !supportsRackSelection(client.currentScreen)) {
            if (AxiomCompat.ownsGameplayInput()) {
                suspend();
            } else {
                rememberScreenSelection(client);
                normalizeSelection(null);
            }
            return;
        }
        if (available() && (projected() || pendingSelection >= 0) && AxiomCompat.ownsGameplayInput()) {
            suspend();
        }
    }

    public static int preferredCapacity() {
        return preferredCapacity;
    }

    public static void setPreferredCapacity(int count) {
        if (count < 0 || count > MAX_EXTRA_SLOTS) {
            return;
        }
        if (count == preferredCapacity) {
            if (available() && count != capacity() && pendingMutation < 0) {
                changeCapacity(count);
            }
            return;
        }
        try {
            Files.createDirectories(PREFERENCE.getParent());
            Path temporary = Files.createTempFile(PREFERENCE.getParent(), "lithotheca-slots-", ".tmp");
            try {
                Files.writeString(temporary, count + System.lineSeparator());
                try {
                    Files.move(temporary, PREFERENCE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, PREFERENCE, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            preferredCapacity = count;
            if (available()) {
                changeCapacity(count);
            }
        } catch (IOException failure) {
            status = Text.translatable("lithotheca.rack.config_error");
            Lithotheca.LOGGER.warn("Could not save the Lithotheca slot count.", failure);
        }
    }

    public static Text message() {
        if (pendingMutation >= 0 || pendingSelection >= 0) {
            return Text.translatable("lithotheca.rack.awaiting");
        }
        if (!available() && status.getString().isEmpty()) {
            return Text.translatable("lithotheca.rack.unavailable");
        }
        return status;
    }

    private static void receive(MinecraftClient client, int request, boolean accepted, State incoming) {
        boolean previousProjection = projected();
        boolean selectionReply = request >= 0 && request == pendingSelection;
        boolean mutationReply = request >= 0 && request == pendingMutation;
        boolean creativeReply = request >= 0 && request == latestCreativeEdit;
        boolean pickerReply = request >= 0 && request == pendingPickerAction;
        boolean pickerSelected = pickerReply && accepted && pendingPickerSelectsSlot;
        state = incoming;
        if (pickerSelected) {
            SCREEN_SELECTION.choose(incoming.selected());
        } else if (accepted && selectionReply && pickingBlock) {
            SCREEN_SELECTION.picked(incoming.selected());
        }
        if (!incoming.enabled() || SCREEN_SELECTION.selected(-1) >= NATIVE_SLOTS + incoming.capacity()) {
            clearScreenSelection();
        }
        if (!incoming.enabled() || creativeReply) {
            CREATIVE_PREDICTIONS.clear();
            latestCreativeEdit = -1;
        }
        if (creativeReply && !accepted) {
            client.inGameHud.setOverlayMessage(Text.translatable("lithotheca.rack.rejected"), false);
        }
        if (client.player != null) {
            PlayerInventory inventory = client.player.getInventory();
            if (previousProjection || projected() || mutationReply) {
                for (int index = 0; index < NATIVE_SLOTS; index++) {
                    int logicalIndex = index == BRIDGE && projected() ? incoming.selected() : index;
                    inventory.setStack(index, incoming.stacks().get(logicalIndex).copy());
                }
            }
            if (previousProjection || projected() || selectionReply || pickerReply && accepted) {
                inventory.selectedSlot = projected() ? BRIDGE : incoming.selected();
            }
        }
        if (mutationReply) {
            pendingMutation = -1;
            status = accepted ? Text.empty() : Text.translatable("lithotheca.rack.rejected");
        }
        if (pickerReply) {
            pendingPickerAction = -1;
            pendingPickerSelectsSlot = false;
            PICKER_ACTIONS.acknowledge(accepted);
        }
        if (!incoming.enabled()) {
            if (pendingMutation == pendingPickerAction) pendingMutation = -1;
            pendingPickerAction = -1;
            pendingPickerSelectsSlot = false;
            PICKER_ACTIONS.clear();
        }
        if (selectionReply) {
            pendingSelection = -1;
            targetSelection = -1;
            pickingBlock = false;
            int next = queuedSelection;
            boolean normalizing = queuedNormalization;
            queuedSelection = -1;
            queuedNormalization = false;
            if (!accepted) {
                status = Text.translatable("lithotheca.rack.rejected");
            }
            if (available() && next >= 0) {
                select(next, normalizing);
            }
        }
        if (!projected() && pendingSelection < 0 && afterNative != null) {
            Runnable action = afterNative;
            afterNative = null;
            if (client.currentScreen == null && available()) {
                action.run();
            }
        }
        rebuildPickerPreview();
        sendNextPickerAction();
        flushDeferredCapacity();
        if (client.currentScreen instanceof LithothecaScreen && available()) {
            sendNextQuickAdd();
        } else {
            QUICK_ADDS.clear();
        }
        if (!queuedPick.isEmpty() && pendingMutation < 0 && pendingSelection < 0 && PICKER_ACTIONS.isEmpty()) {
            ItemStack next = queuedPick;
            queuedPick = ItemStack.EMPTY;
            if (client.currentScreen == null) {
                pickBlock(next);
            }
        }
        resumeScreenSelection(client);
    }

    private static boolean projected() {
        return state.selected() >= NATIVE_SLOTS;
    }

    private static void requestSelection(int index, boolean normalizing) {
        targetSelection = index;
        pendingSelection = send(RackProtocol.SELECT, buffer -> buffer.writeVarInt(index));
        if (pendingSelection >= 0 && normalizing) {
            SCREEN_SELECTION.normalizeTo(index);
        }
    }

    private static void changeCapacity(int count) {
        if (pendingMutation >= 0 || pendingSelection >= 0 || !PICKER_ACTIONS.isEmpty()) {
            deferredCapacity = count;
            return;
        }
        pendingMutation = send(RackProtocol.CAPACITY, buffer -> buffer.writeVarInt(count));
    }

    private static void flushDeferredCapacity() {
        if (deferredCapacity >= 0 && available() && pendingMutation < 0 && pendingSelection < 0 && PICKER_ACTIONS.isEmpty()) {
            int count = deferredCapacity;
            deferredCapacity = -1;
            changeCapacity(count);
        }
    }

    private static void offerPreferredCapacity() {
        changeCapacity(preferredCapacity);
        capacityOffered = pendingMutation >= 0;
    }

    private static void mutate(Identifier channel, Consumer<PacketByteBuf> write) {
        if (available() && pendingMutation < 0 && pendingSelection < 0 && PICKER_ACTIONS.isEmpty()) {
            pendingMutation = send(channel, write);
        }
    }

    private static void sendNextQuickAdd() {
        if (pendingMutation < 0 && pendingSelection < 0 && PICKER_ACTIONS.isEmpty()) {
            Identifier item = QUICK_ADDS.pollFirst();
            if (item != null) {
                mutate(RackProtocol.QUICK_ADD, buffer -> buffer.writeIdentifier(item));
            }
        }
    }

    private static int send(Identifier channel, Consumer<PacketByteBuf> write) {
        if (!ClientPlayNetworking.canSend(channel)) {
            return -1;
        }
        int request = sequence++;
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeVarInt(request);
        write.accept(buffer);
        if (buffer.readableBytes() > MAX_CUSTOM_PAYLOAD) {
            buffer.release();
            return -1;
        }
        ClientPlayNetworking.send(channel, buffer);
        return request;
    }

    private static void enqueuePickerAction(PickerCursorQueue.Action action) {
        if (!pickerCursorAvailable()) return;
        if (!PICKER_ACTIONS.offer(action)) {
            status = Text.translatable("lithotheca.rack.rejected");
            return;
        }
        rebuildPickerPreview();
        sendNextPickerAction();
    }

    private static void rebuildPickerPreview() {
        PICKER_ACTIONS.rebase(state.pickerCursor(), NATIVE_SLOTS + capacity(), ClientRack::pickerBaseStack,
            item -> new ItemStack(Registries.ITEM.get(item)));
    }

    private static ItemStack pickerBaseStack(int index) {
        // Native slot packets can arrive before the matching cursor acknowledgement.
        return index >= 0 && index < state.stacks().size() ? state.stacks().get(index) : baseStack(index);
    }

    private static void sendNextPickerAction() {
        PickerCursorQueue.Action action = PICKER_ACTIONS.first();
        if (action == null || !pickerCursorAvailable() || pendingMutation >= 0 || pendingSelection >= 0) return;
        ItemStack expectedCursor = state.pickerCursor().copy();
        ItemStack expectedSlot = action.type() == RackProtocol.CURSOR_EXCHANGE ? pickerBaseStack(action.slot()).copy() : ItemStack.EMPTY;
        int request = send(RackProtocol.CURSOR_ACTION, buffer -> {
            buffer.writeVarInt(action.type());
            buffer.writeItemStack(expectedCursor);
            switch (action.type()) {
                case RackProtocol.CURSOR_TAKE -> buffer.writeIdentifier(action.item());
                case RackProtocol.CURSOR_EXCHANGE -> {
                    buffer.writeVarInt(action.slot());
                    buffer.writeItemStack(expectedSlot);
                }
                case RackProtocol.CURSOR_DISCARD -> { }
                default -> throw new IllegalStateException("Unknown picker action " + action.type());
            }
        });
        if (request < 0) {
            PICKER_ACTIONS.clear();
            rebuildPickerPreview();
            status = Text.translatable("lithotheca.rack.rejected");
            return;
        }
        pendingPickerAction = request;
        pendingPickerSelectsSlot = action.type() == RackProtocol.CURSOR_EXCHANGE && !expectedCursor.isEmpty();
        pendingMutation = request;
    }

    private static boolean creative(MinecraftClient client) {
        return client.player != null && client.interactionManager != null && client.interactionManager.hasCreativeInventory();
    }

    private static PlayerInventory inventory() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player == null ? null : client.player.getInventory();
    }

    private static int loadPreference() {
        try {
            if (Files.isRegularFile(PREFERENCE)) {
                return Math.max(0, Math.min(MAX_EXTRA_SLOTS, Integer.parseInt(Files.readString(PREFERENCE).strip())));
            }
        } catch (IOException | NumberFormatException failure) {
            Lithotheca.LOGGER.warn("Could not read the Lithotheca slot count. Using the default.", failure);
        }
        return RackProtocol.DEFAULT_EXTRA_SLOTS;
    }

    private static void reset() {
        sequence = 0;
        observedPlayer = null;
        clearPlayerState();
    }

    private static void clearPlayerState() {
        state = State.EMPTY;
        pendingSelection = -1;
        targetSelection = -1;
        queuedSelection = -1;
        queuedNormalization = false;
        clearScreenSelection();
        pendingMutation = -1;
        latestCreativeEdit = -1;
        pendingPickerAction = -1;
        pendingPickerSelectsSlot = false;
        deferredCapacity = -1;
        PICKER_ACTIONS.clear();
        CREATIVE_PREDICTIONS.clear();
        pickingBlock = false;
        queuedPick = ItemStack.EMPTY;
        QUICK_ADDS.clear();
        creativeLastTick = false;
        capacityOffered = false;
        afterNative = null;
        status = Text.empty();
    }

    private record State(boolean enabled, int capacity, int selected, List<ItemStack> stacks, ItemStack pickerCursor) {
        private static final State EMPTY = new State(false, 0, 0, List.of(), ItemStack.EMPTY);
    }
}
