package com.oliver.lithotheca.catalogue;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FamilyMatchOrderTest {
    @Test
    void silverTrimVerticalSlopesLeadTheExistingTextureResults() {
        CatalogueEntry target = entry("glacium_trim_silver_slope_vertical", "Glacium Silver Trim Vertical Slope (Ganosis)");
        CatalogueEntry silverBroad = entry("glacium_trim_silver_slope_vertical_shallow_broad", "Glacium Silver Trim Broad Shallow Vertical Slope");
        CatalogueEntry silverNarrow = entry("glacium_trim_silver_slope_vertical_shallow_narrow", "Glacium Silver Trim Narrow Shallow Vertical Slope");
        CatalogueEntry silverPlain = entry("glacium_trim_silver_slope", "Glacium Silver Trim Slope");
        CatalogueEntry bronze = entry("glacium_trim_bronze_slope_vertical", "Glacium Bronze Trim Vertical Slope");
        CatalogueEntry ordinary = entry("glacium_slope_vertical", "Glacium Vertical Slope");

        var score = FamilyMatchOrder.scorer(target, true);
        List<CatalogueEntry> ordered = new ArrayList<>(List.of(ordinary, silverNarrow, silverPlain, bronze, target, silverBroad));
        CatalogueOrder.sort(ordered, target, "", score);

        assertEquals(List.of(target, silverBroad, silverNarrow, silverPlain, bronze, ordinary), ordered);
    }

    @Test
    void verticalSliceAliasHasTheSameFormSimilarityAsItsCanonicalId() {
        CatalogueEntry target = entry("glacium_aged_slice_vertical", "Glacium Aged Vertical Slice");
        CatalogueEntry canonical = entry("glacium_ashlar_slice_vertical", "Glacium Ashlar Vertical Slice");
        CatalogueEntry alias = entry("glacium_ashlar_vertical_slice", "Glacium Ashlar Vertical Slice");
        CatalogueEntry horizontal = entry("glacium_ashlar_slice_horizontal", "Glacium Ashlar Horizontal Slice");
        var score = FamilyMatchOrder.scorer(target, true);

        assertEquals(score.applyAsInt(canonical), score.applyAsInt(alias));
        assertTrue(score.applyAsInt(canonical) > score.applyAsInt(horizontal));
    }

    @Test
    void shapesPrioritiseTheExistingFinishAndThirdPartyScoringStaysSeparate() {
        CatalogueEntry target = entry("glacium_trim_silver_slope_vertical", "Glacium Silver Trim Vertical Slope");
        CatalogueEntry silverStairs = entry("glacium_trim_silver_stairs", "Glacium Silver Trim Stairs");
        CatalogueEntry bronzeSlope = entry("glacium_trim_bronze_slope_vertical", "Glacium Bronze Trim Vertical Slope");
        var score = FamilyMatchOrder.scorer(target, false);

        assertTrue(score.applyAsInt(silverStairs) > score.applyAsInt(bronzeSlope));
        assertFalse(FamilyMatchOrder.supports(entry(new Identifier("conquest", "brown_plaster_slab"), "Brown Plaster Slab")));
        assertFalse(FamilyMatchOrder.supports(entry(new Identifier("minecraft", "stone"), "Stone")));
    }

    private static CatalogueEntry entry(String path, String name) {
        return entry(new Identifier("erydon", path), name);
    }

    private static CatalogueEntry entry(Identifier id, String name) {
        var facts = CatalogueClassifier.classify(id, CatalogueClassifier.UNKNOWN, List.of());
        return CatalogueClassifier.entry(id, facts, List.of(), name);
    }
}
