package com.oliver.lithotheca.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AxiomWheelTest {
    @Test
    void builderSlotFollowsTheFullRackInsteadOfNativeSlotNine() {
        int last = 17;

        assertFalse(AxiomCompat.atBuilderEdge(8, last, -1));
        assertFalse(AxiomCompat.atBuilderEdge(16, last, -1));
        assertTrue(AxiomCompat.atBuilderEdge(last, last, -1));
        assertFalse(AxiomCompat.atBuilderEdge(last, last, 1));

        assertTrue(AxiomCompat.atBuilderEdge(0, last, 1));
        assertFalse(AxiomCompat.atBuilderEdge(0, last, -1));
        assertFalse(AxiomCompat.atBuilderEdge(1, last, 1));
    }

    @Test
    void nativeBuilderExitTargetsMapToTheTwoExpandedEdges() {
        int last = 26;

        assertEquals(last, AxiomCompat.builderExitSlot(8, last));
        assertEquals(0, AxiomCompat.builderExitSlot(0, last));
    }
}
