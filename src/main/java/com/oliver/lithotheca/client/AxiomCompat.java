package com.oliver.lithotheca.client;

import com.oliver.lithotheca.Lithotheca;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerInventory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Axiom's context menu can own the mouse even when Minecraft has no Screen open. */
public final class AxiomCompat {
    private static final boolean INSTALLED = FabricLoader.getInstance().isModLoaded("axiom");
    private static Hooks hooks;
    private static boolean checked;
    private static WheelHooks wheelHooks;
    private static boolean checkedWheel;
    private static PendingBuilderEntry pendingBuilderEntry;

    private AxiomCompat() {
    }

    public static boolean installed() {
        return INSTALLED;
    }

    public static boolean ownsGameplayInput() {
        return ownsInput(true);
    }

    public static boolean ownsHotbarLayout() {
        return ownsInput(false);
    }

    private static boolean ownsInput(boolean includeBuilderSlot) {
        if (!INSTALLED) {
            return false;
        }
        Hooks active = hooks();
        if (active == null) {
            return true;
        }
        try {
            return (boolean) active.editorActive().invoke(null)
                || (includeBuilderSlot && (boolean) active.toolActive().invoke(null))
                || (boolean) active.flightAdjustment().invoke(null)
                || (boolean) active.contextActive().invoke(active.contextInstance().invoke(null))
                || (active.contextKey().get(null) instanceof KeyBinding key && key.isPressed());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            disable(failure);
            return true;
        }
    }

    public static boolean builderSlotActive() {
        Hooks active = INSTALLED ? hooks() : null;
        if (active == null) return false;
        try {
            return (boolean) active.toolActive().invoke(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            disable(failure);
            return false;
        }
    }

    public static boolean scrollBuilderSlot(PlayerInventory inventory, double amount) {
        if (!INSTALLED || ClientRack.capacity() == 0 || amount == 0) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null || ownsHotbarLayout()) {
            pendingBuilderEntry = null;
            return false;
        }

        if (pendingBuilderEntry != null && pendingBuilderEntry.player() == client.player
                && ClientRack.pendingSelection()) {
            // Wait until the projected rack slot is restored before exposing it to Axiom.
            return true;
        }
        pendingBuilderEntry = null;
        WheelHooks wheel = wheelHooks();
        if (wheel == null) return false;

        int direction = (int) Math.signum(amount);
        int last = ClientRack.NATIVE_SLOTS + ClientRack.capacity() - 1;
        try {
            if (builderSlotActive()) {
                int previous = inventory.selectedSlot;
                int next = Math.floorMod(previous - direction, ClientRack.NATIVE_SLOTS);
                int nativeResult = wheel.scroll(previous, next, direction);
                if (nativeResult < 0) return false;
                if (!builderSlotActive()) {
                    ClientRack.select(builderExitSlot(nativeResult, last));
                }
                return true;
            }

            if (!atBuilderEdge(ClientRack.scrollCursor(), last, direction) || !wheel.visibleAndUsable()) {
                return false;
            }

            PendingBuilderEntry entry = new PendingBuilderEntry(client.player, direction);
            pendingBuilderEntry = entry;
            if (ClientRack.suspend(() -> completeBuilderEntry(entry, wheel))) return true;
            pendingBuilderEntry = null;
            return enterBuilderSlot(wheel, direction);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            disableWheel(failure);
            return false;
        }
    }

    static boolean atBuilderEdge(int selected, int last, int direction) {
        return direction > 0 ? selected == 0 : selected == last;
    }

    static int builderExitSlot(int nativeResult, int last) {
        return nativeResult == ClientRack.NATIVE_SLOTS - 1 ? last : nativeResult;
    }

    private static boolean enterBuilderSlot(WheelHooks wheel, int direction) throws ReflectiveOperationException {
        // Let Axiom use its own native wrap, permissions, tool reset and activation logic.
        int lastNative = ClientRack.NATIVE_SLOTS - 1;
        int previous = direction > 0 ? 0 : lastNative;
        int next = direction > 0 ? lastNative : 0;
        return wheel.scroll(previous, next, direction) >= 0 && builderSlotActive();
    }

    private static void completeBuilderEntry(PendingBuilderEntry entry, WheelHooks wheel) {
        if (pendingBuilderEntry != entry) return;
        pendingBuilderEntry = null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != entry.player() || client.currentScreen != null || ClientRack.capacity() == 0
                || ownsHotbarLayout() || builderSlotActive()) {
            return;
        }
        try {
            if (enterBuilderSlot(wheel, entry.direction())) return;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            disableWheel(failure);
        }
        // The slot may have been disabled while the server was restoring the native hotbar.
        ClientRack.select(entry.direction() > 0 ? ClientRack.NATIVE_SLOTS + ClientRack.capacity() - 1 : 0);
    }

    public static boolean opensAxiomControls(int keyCode, int scanCode) {
        if (!INSTALLED || hooks() == null) {
            return false;
        }
        try {
            return matches(hooks.contextKey(), keyCode, scanCode)
                || matches(hooks.editorKey(), keyCode, scanCode)
                || matches(hooks.flightKey(), keyCode, scanCode);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            disable(failure);
            return false;
        }
    }

    private static boolean matches(Field field, int keyCode, int scanCode) throws IllegalAccessException {
        return field.get(null) instanceof KeyBinding key && key.matchesKey(keyCode, scanCode);
    }

    private static Hooks hooks() {
        if (!checked) {
            checked = true;
            try {
                Class<?> context = Class.forName("com.moulberry.axiom.ContextMenuManager");
                Class<?> keys = Class.forName("com.moulberry.axiom.ClientEvents");
                hooks = new Hooks(
                    Class.forName("com.moulberry.axiom.editor.EditorUI").getMethod("isActive"),
                    Class.forName("com.moulberry.axiom.buildertools.BuilderToolManager").getMethod("isToolSlotActive"),
                    Class.forName("com.moulberry.axiom.FlightSpeedAdjustment").getMethod("isEnabled"),
                    context.getMethod("getInstance"), context.getMethod("isActive"),
                    keys.getField("contextMenuKeyBind"), keys.getField("toggleEditorUiKeyBind"),
                    keys.getField("toggleFlightSpeedAdjustment")
                );
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                disable(failure);
            }
        }
        return hooks;
    }

    private static WheelHooks wheelHooks() {
        if (!checkedWheel) {
            checkedWheel = true;
            try {
                Class<?> manager = Class.forName("com.moulberry.axiom.buildertools.BuilderToolManager");
                Field config = Class.forName("com.moulberry.axiom.Axiom").getField("configuration");
                Field builderTools = config.getType().getField("builderTools");
                wheelHooks = new WheelHooks(manager.getMethod("scroll", int.class, int.class, int.class),
                    manager.getMethod("canUseBuilderTools"), config, builderTools,
                    builderTools.getType().getField("showBuilderToolSlot"));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                disableWheel(failure);
            }
        }
        return wheelHooks;
    }

    private static void disableWheel(Throwable failure) {
        Lithotheca.LOGGER.info("Axiom's wheel slot could not be identified. Its native controls remain available.", failure);
        wheelHooks = null;
        pendingBuilderEntry = null;
    }

    private static void disable(Throwable failure) {
        Lithotheca.LOGGER.info("Axiom controls could not be identified. Lithotheca will yield the gameplay hotbar to Axiom.", failure);
        hooks = null;
    }

    private record PendingBuilderEntry(ClientPlayerEntity player, int direction) {
    }

    private record WheelHooks(Method scrollMethod, Method canUse, Field config, Field builderTools, Field visible) {
        boolean visibleAndUsable() throws ReflectiveOperationException {
            if (!(boolean) canUse.invoke(null)) return false;
            Object settings = config.get(null);
            return settings != null && visible.getBoolean(builderTools.get(settings));
        }

        int scroll(int previous, int next, int direction) throws ReflectiveOperationException {
            return (int) scrollMethod.invoke(null, previous, next, direction);
        }
    }

    private record Hooks(Method editorActive, Method toolActive, Method flightAdjustment,
                         Method contextInstance, Method contextActive, Field contextKey, Field editorKey, Field flightKey) {
    }
}
