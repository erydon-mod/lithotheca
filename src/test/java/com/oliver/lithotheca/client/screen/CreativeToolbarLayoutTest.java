package com.oliver.lithotheca.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreativeToolbarLayoutTest {
    @Test
    void fullRackFitsAboveTheCreativeTabsAtMinimumGuiSize() {
        var layout = LithothecaCreativeToolbar.layout(62, 18, 136, 18);
        assertEquals(1, layout.rows());
        assertEquals(18, layout.columns());
        assertTrue(layout.top() >= 0);
        assertTrue(layout.left() + layout.columns() * layout.cell() <= 62 + 195);
        assertEquals(17, layout.slotAt(layout.left() + 17 * layout.cell(), layout.top()));
        assertEquals(-1, layout.slotAt(layout.left(), 18));
        assertEquals(-1, layout.slotAt(layout.left() - 1, layout.top()));
    }

    @Test
    void tallerScreensWrapAndDoNotCaptureTheUnusedCell() {
        var layout = LithothecaCreativeToolbar.layout(62, 33, 136, 17);
        assertEquals(2, layout.rows());
        assertEquals(9, layout.columns());
        int secondRow = layout.top() + layout.cell();
        assertEquals(16, layout.slotAt(layout.left() + 7 * layout.cell(), secondRow));
        assertEquals(-1, layout.slotAt(layout.left() + 8 * layout.cell(), secondRow));
        assertNull(LithothecaCreativeToolbar.layout(62, 33, 136, 0));
    }
}
