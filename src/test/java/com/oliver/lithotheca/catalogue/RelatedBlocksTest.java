package com.oliver.lithotheca.catalogue;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelatedBlocksTest {
    @Test
    void texturePivotPreservesFormWhileVaryingColourAndFinish() {
        CatalogueEntry target = entry("conquest:dark_gray_limestone_cobble_stairs", "Stairs");
        CatalogueEntry related = entry("conquest:dark_limestone_ashlar_stairs", "Stairs");
        CatalogueEntry slab = entry("conquest:dark_gray_limestone_cobble_corner_slab", "Slab");
        CatalogueEntry unrelated = entry("conquest:light_gray_plaster_stairs", "Stairs");

        assertTrue(RelatedBlocks.score(target, related, true) > 0);
        assertEquals(0, RelatedBlocks.score(target, slab, true));
        assertEquals(0, RelatedBlocks.score(target, unrelated, true));
        assertTrue(RelatedBlocks.score(entry("demo:smooth_stone_stairs", "Stairs"),
            entry("demo:stone_bricks_stairs", "Stairs"), true) > 0);
    }

    @Test
    void shapePivotPrefersTheSamePaletteAndSource() {
        CatalogueEntry target = entry("conquest:dark_gray_limestone_cobble_stairs", "Stairs");
        CatalogueEntry local = entry("conquest:dark_gray_limestone_cobble_balustrade", "Unclassified");
        CatalogueEntry elsewhere = entry("other:dark_grey_limestone_cobble_balustrade", "Unclassified");
        CatalogueEntry otherFinish = entry("conquest:dark_limestone_ashlar_balustrade", "Unclassified");
        CatalogueEntry otherPigment = entry("conquest:gray_limestone_cobble_balustrade", "Unclassified");

        assertTrue(RelatedBlocks.score(target, local, false) > RelatedBlocks.score(target, elsewhere, false));
        assertTrue(RelatedBlocks.score(target, elsewhere, false) > 0);
        assertEquals(0, RelatedBlocks.score(target, otherFinish, false));
        assertEquals(0, RelatedBlocks.score(target, otherPigment, false));
    }

    @Test
    void plasterTexturesOfferDifferentPaintsWhileShapesKeepThePaint() {
        CatalogueEntry target = entry("conquest:brown_plaster_slab", "Slab");
        CatalogueEntry anotherPaint = entry("conquest:light_blue_plaster_slab", "Slab");
        CatalogueEntry anotherShape = entry("conquest:brown_plaster_stairs", "Stairs");
        CatalogueEntry slabVariant = entry("conquest:brown_plaster_corner_slab", "Slab");

        assertTrue(RelatedBlocks.score(target, anotherPaint, true) > 0);
        assertTrue(RelatedBlocks.score(target, anotherPaint, true) > RelatedBlocks.score(target, slabVariant, true));
        assertEquals(0, RelatedBlocks.score(target, anotherPaint, false));
        assertEquals(0, RelatedBlocks.score(target, anotherShape, true));
        assertTrue(RelatedBlocks.score(target, anotherShape, false) > 0);
    }

    @Test
    void infersOpaqueIdsFromReliableTagsOrDisplayNames() {
        CatalogueEntry tagged = entry("demo:building_527", "Block", "Building 527",
            List.of("material:limestone", "minecraft:stairs", "c:dyed/light_gray"));
        CatalogueEntry named = entry("demo:variant_872", "Block", "Light Grey Limestone Stairs", List.of());
        CatalogueEntry samePalette = entry("other:light_grey_limestone_cobble_stairs", "Stairs");
        CatalogueEntry wrongForm = entry("other:light_grey_limestone_cobble_slab", "Slab");

        assertTrue(RelatedBlocks.score(tagged, samePalette, true) > 0);
        assertTrue(RelatedBlocks.score(named, samePalette, true) > 0);
        assertEquals(0, RelatedBlocks.score(tagged, wrongForm, true));
        assertEquals(0, RelatedBlocks.score(named, wrongForm, true));
    }

    @Test
    void avoidsMatchingByOnlyColourFormOrGenericTags() {
        CatalogueEntry target = entry("demo:generic_grey_slab", "Slab", "Generic Grey Slab",
            List.of("minecraft:mineable/pickaxe"));
        CatalogueEntry candidate = entry("demo:basic_gray_slab", "Slab", "Basic Gray Slab",
            List.of("minecraft:mineable/pickaxe"));

        assertEquals(0, RelatedBlocks.score(target, candidate, true));
        assertEquals(0, RelatedBlocks.score(target, candidate, false));
        assertTrue(RelatedBlocks.score(target, target, true) > 0);
        assertEquals(0, RelatedBlocks.score(entry("demo:oak_plank_stairs", "Stairs"),
            entry("demo:oak_leaves", "Unclassified"), false));
    }

    @Test
    void recognizesAnUnclassifiedNamedFamilyWithoutInventingEquivalence() {
        CatalogueEntry stairs = entry("demo:azurun_engraved_stairs", "Stairs");
        CatalogueEntry column = entry("demo:azurun_engraved_column", "Unclassified");
        CatalogueEntry other = entry("demo:syverin_engraved_column", "Unclassified");

        assertTrue(RelatedBlocks.score(stairs, column, false) > 0);
        assertEquals(0, RelatedBlocks.score(stairs, other, false));
    }

    private static CatalogueEntry entry(String id, String shape) {
        return entry(id, shape, "", List.of());
    }

    private static CatalogueEntry entry(String id, String shape, String name, List<String> tags) {
        Identifier identifier = new Identifier(id);
        return CatalogueClassifier.entry(identifier, CatalogueClassifier.classify(identifier, shape, List.of()), tags, name);
    }
}
