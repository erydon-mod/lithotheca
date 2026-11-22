package com.oliver.lithotheca.client;

import static com.oliver.lithotheca.rack.RackProtocol.HOTBAR_SIZE;

/** Remembers the logical slot while a menu needs the real vanilla hotbar. */
final class ScreenSelection {
    private int selection = -1;
    private int nativeSlot = -1;
    private boolean restorationRequested;
    private boolean awaitingPick;

    boolean active() {
        return selection >= 0;
    }

    void remember(int logicalSlot, int physicalSlot, boolean pickCanSelect) {
        if (!active()) {
            selection = logicalSlot;
            nativeSlot = physicalSlot;
            awaitingPick = pickCanSelect;
        }
    }

    void choose(int logicalSlot) {
        if (active()) {
            selection = logicalSlot;
            restorationRequested = false;
            awaitingPick = false;
        }
    }

    void picked(int logicalSlot) {
        if (awaitingPick) {
            choose(logicalSlot);
        }
    }

    void normalizeTo(int physicalSlot) {
        if (active()) {
            nativeSlot = physicalSlot;
            restorationRequested = false;
        }
    }

    int selected(int actual) {
        return active() ? selection : actual;
    }

    int restoreTarget(int actual, int totalSlots) {
        if (!active()) {
            return -1;
        }
        if (selection < HOTBAR_SIZE || selection >= totalSlots || actual == selection || actual != nativeSlot
            || restorationRequested) {
            clear();
            return -1;
        }
        restorationRequested = true;
        return selection;
    }

    void clear() {
        selection = -1;
        nativeSlot = -1;
        restorationRequested = false;
        awaitingPick = false;
    }
}
