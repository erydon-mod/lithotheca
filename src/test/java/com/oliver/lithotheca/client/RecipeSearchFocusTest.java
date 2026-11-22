package com.oliver.lithotheca.client;

import net.minecraft.client.gui.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecipeSearchFocusTest {
    private static FakeRuntime runtime;

    @AfterEach
    void resetRuntime() {
        runtime = null;
    }

    @Test
    void readsCustomOverlayFocusThroughThePublicApiAndMinecraftElement() throws ReflectiveOperationException {
        OverlayField field = new OverlayField();
        runtime = new FakeRuntime(field);
        var query = RecipeSearchFocus.reiQuery(RuntimeApi.class);

        assertFalse(query.isFocused());
        field.setFocused(true);
        assertTrue(query.isFocused());
        field.setFocused(false);
        assertFalse(query.isFocused());
    }

    @Test
    void hiddenOrUnavailableOverlayDoesNotCaptureTheShortcut() throws ReflectiveOperationException {
        OverlayField field = new OverlayField();
        field.setFocused(true);
        runtime = new FakeRuntime(field);
        var query = RecipeSearchFocus.reiQuery(RuntimeApi.class);

        runtime.visible = false;
        assertFalse(query.isFocused());
        runtime.visible = true;
        runtime.field = null;
        assertFalse(query.isFocused());
        runtime = null;
        assertFalse(query.isFocused());
    }

    public interface RuntimeApi {
        static RuntimeApi getInstance() {
            return runtime;
        }

        boolean isOverlayVisible();

        Object getSearchTextField();
    }

    private static final class FakeRuntime implements RuntimeApi {
        private boolean visible = true;
        private Element field;

        private FakeRuntime(Element field) {
            this.field = field;
        }

        @Override
        public boolean isOverlayVisible() {
            return visible;
        }

        @Override
        public Object getSearchTextField() {
            return field;
        }
    }

    private static final class OverlayField implements Element {
        private boolean focused;

        @Override
        public void setFocused(boolean focused) {
            this.focused = focused;
        }

        @Override
        public boolean isFocused() {
            return focused;
        }
    }
}
