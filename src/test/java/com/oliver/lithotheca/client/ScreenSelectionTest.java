package com.oliver.lithotheca.client;

import org.junit.jupiter.api.Test;

import static com.oliver.lithotheca.rack.RackProtocol.HOTBAR_SIZE;
import static com.oliver.lithotheca.rack.RackProtocol.MAX_EXTRA_SLOTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ScreenSelectionTest {
    private static final int BRIDGE = HOTBAR_SIZE - 1;
    private static final int TOTAL = HOTBAR_SIZE + MAX_EXTRA_SLOTS;

    @Test
    void firstAndLastExtraSurviveMenuChainsAndRestoreBeforeTheNextWorldSelection() {
        for (int extra : new int[] { HOTBAR_SIZE, TOTAL - 1 }) {
            ScreenSelection selection = new ScreenSelection();
            selection.remember(extra, BRIDGE, false);
            selection.normalizeTo(BRIDGE);
            selection.remember(BRIDGE, BRIDGE, false);

            assertEquals(extra, selection.selected(BRIDGE));
            assertEquals(extra, selection.restoreTarget(BRIDGE, TOTAL));
            assertEquals(-1, selection.restoreTarget(extra, TOTAL));
            assertFalse(selection.active());
            assertEquals(2, selection.selected(2));
        }
    }

    @Test
    void openingWhileAnExtraSelectionIsPendingKeepsItsLatestLogicalTarget() {
        ScreenSelection selection = new ScreenSelection();
        selection.remember(TOTAL - 1, 2, false);
        selection.normalizeTo(2);
        selection.remember(HOTBAR_SIZE, BRIDGE, false);

        assertEquals(TOTAL - 1, selection.selected(2));
        assertEquals(TOTAL - 1, selection.restoreTarget(2, TOTAL));
    }

    @Test
    void aPendingPickSuppliesItsFinalSlotUnlessANewerExplicitSelectionWasQueued() {
        ScreenSelection selection = new ScreenSelection();
        selection.remember(2, 2, true);
        selection.normalizeTo(2);
        selection.picked(HOTBAR_SIZE);
        assertEquals(HOTBAR_SIZE, selection.restoreTarget(2, TOTAL));

        selection.clear();
        selection.remember(TOTAL - 1, 2, false);
        selection.normalizeTo(2);
        selection.picked(HOTBAR_SIZE);
        assertEquals(TOTAL - 1, selection.restoreTarget(2, TOTAL));
    }

    @Test
    void acceptedPickerPlacementWinsAcrossAnotherMenuNormalization() {
        ScreenSelection selection = new ScreenSelection();
        selection.remember(HOTBAR_SIZE, BRIDGE, false);
        assertEquals(HOTBAR_SIZE, selection.restoreTarget(BRIDGE, TOTAL));
        selection.choose(TOTAL - 1);
        selection.normalizeTo(BRIDGE);

        assertEquals(TOTAL - 1, selection.selected(BRIDGE));
        assertEquals(TOTAL - 1, selection.restoreTarget(BRIDGE, TOTAL));
    }

    @Test
    void anIntentionalNativeSelectionWinsOverTheRememberedExtra() {
        ScreenSelection selection = new ScreenSelection();
        selection.remember(TOTAL - 1, BRIDGE, false);
        selection.choose(3);

        assertEquals(-1, selection.restoreTarget(3, TOTAL));
        assertFalse(selection.active());
        assertEquals(3, selection.selected(3));
    }

    @Test
    void nativeSelectionChangesAndCapacityReductionCancelRestoration() {
        ScreenSelection selection = new ScreenSelection();
        selection.remember(HOTBAR_SIZE, BRIDGE, false);
        assertEquals(-1, selection.restoreTarget(1, TOTAL));
        assertFalse(selection.active());

        selection.remember(TOTAL - 1, BRIDGE, false);
        assertEquals(-1, selection.restoreTarget(BRIDGE, TOTAL - 1));
        assertFalse(selection.active());
    }

    @Test
    void clearingForAWorldOrPlayerChangeDiscardsTheRememberedSelection() {
        ScreenSelection selection = new ScreenSelection();
        selection.remember(TOTAL - 1, BRIDGE, false);
        selection.clear();

        assertEquals(-1, selection.restoreTarget(BRIDGE, TOTAL));
        assertEquals(0, selection.selected(0));
        assertFalse(selection.active());
    }

    @Test
    void anUnsuccessfulRestorationDoesNotKeepResendingTheSameSelection() {
        ScreenSelection selection = new ScreenSelection();
        selection.remember(HOTBAR_SIZE, BRIDGE, false);
        assertEquals(HOTBAR_SIZE, selection.restoreTarget(BRIDGE, TOTAL));
        assertEquals(-1, selection.restoreTarget(BRIDGE, TOTAL));
        assertFalse(selection.active());
    }
}
