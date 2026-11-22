package com.oliver.lithotheca.client;

import com.oliver.lithotheca.Lithotheca;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.TextFieldWidget;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Recipe overlays keep their text fields outside the native screen's children. */
final class RecipeSearchFocus {
    private static FocusQuery rei;
    private static FocusQuery emi;
    private static boolean checked;

    private RecipeSearchFocus() {
    }

    static boolean isFocused() {
        if (!checked) initialize();
        if (rei != null) {
            try {
                if (rei.isFocused()) return true;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                rei = null;
                Lithotheca.LOGGER.debug("Could not query REI search focus.", failure);
            }
        }
        if (emi != null) {
            try {
                return emi.isFocused();
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                emi = null;
                Lithotheca.LOGGER.debug("Could not query EMI search focus.", failure);
            }
        }
        return false;
    }

    private static void initialize() {
        checked = true;
        if (FabricLoader.getInstance().isModLoaded("roughlyenoughitems")) {
            try {
                rei = reiQuery(Class.forName("me.shedaniel.rei.api.client.REIRuntime"));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                Lithotheca.LOGGER.debug("Could not find the REI search focus API.", failure);
            }
        }
        if (FabricLoader.getInstance().isModLoaded("emi")) {
            try {
                Method focused = Class.forName("dev.emi.emi.api.EmiApi").getMethod("isSearchFocused");
                Class<?> screenManager = Class.forName("dev.emi.emi.screen.EmiScreenManager");
                Method disabled = screenManager.getMethod("isDisabled");
                Field search = screenManager.getField("search");
                emi = () -> !Boolean.TRUE.equals(disabled.invoke(null))
                    && search.get(null) instanceof TextFieldWidget field && field.isVisible()
                    && Boolean.TRUE.equals(focused.invoke(null));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                Lithotheca.LOGGER.debug("Could not find the EMI search focus API.", failure);
            }
        }
    }

    static FocusQuery reiQuery(Class<?> api) throws ReflectiveOperationException {
        Method instance = api.getMethod("getInstance");
        Method visible = api.getMethod("isOverlayVisible");
        Method search = api.getMethod("getSearchTextField");
        return () -> {
            Object runtime = instance.invoke(null);
            if (runtime == null || !Boolean.TRUE.equals(visible.invoke(runtime))) return false;
            // isFocused is a remapped Minecraft method on REI's widget.
            // Calling Element preserves the correct name in both development and packaged JARs.
            return search.invoke(runtime) instanceof Element field && field.isFocused();
        };
    }

    @FunctionalInterface
    interface FocusQuery {
        boolean isFocused() throws ReflectiveOperationException;
    }
}
