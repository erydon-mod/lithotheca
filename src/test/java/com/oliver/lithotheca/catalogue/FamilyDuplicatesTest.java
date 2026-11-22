package com.oliver.lithotheca.catalogue;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FamilyDuplicatesTest {
    @Test
    void pairsAnInstalledExactCounterpartWhilePreservingBothIds() {
        CatalogueEntry erydon = entry("erydon:portorium_aged_slab", "Slab", "Core");
        CatalogueEntry themelios = entry("themelios:portorium_aged_slab", "Slab", "Core");
        Map<Identifier, CatalogueEntry> entries = entries(erydon, themelios);
        FamilyDuplicates.mark(entries);

        assertEquals(2, entries.size());
        assertEquals(erydon.id(), entries.get(erydon.id()).id());
        assertEquals(themelios.id(), entries.get(themelios.id()).id());
        assertSame(themelios.related(), entries.get(themelios.id()).related());
        assertEquals(erydon.id().toString(), entries.get(erydon.id()).duplicateGroup());
        assertEquals(erydon.id().toString(), entries.get(themelios.id()).duplicateGroup());
    }

    @Test
    void preservesThemeliosOnlyFormsAndSamePathsFromUnrelatedMods() {
        CatalogueEntry slab = entry("themelios:portorium_aged_slab", "Slab", "Core");
        CatalogueEntry cylinder = entry("themelios:portorium_aged_cylinder_small", "Small Cylinder", "Core");
        CatalogueEntry cornice = entry("themelios:portorium_aged_cornice_small", "Small Cornice", "Core");
        Map<Identifier, CatalogueEntry> entries = entries(slab, cylinder, cornice,
                entry("other:portorium_aged_slab", "Slab", "Core"),
                entry("erydon:portorium_aged_column_circular", "Column", "Core"),
                entry("erydon:portorium_aged_cornice_modern", "Cornice", "Modern"));
        FamilyDuplicates.mark(entries);

        assertEquals("", entries.get(slab.id()).duplicateGroup());
        assertEquals("", entries.get(cylinder.id()).duplicateGroup());
        assertEquals("", entries.get(cornice.id()).duplicateGroup());
    }

    @Test
    void preservesDistinctStylesOrAnUnknownFacetAtTheSamePath() {
        CatalogueEntry slab = entry("themelios:portorium_aged_slab", "Slab", "Core");
        CatalogueEntry block = entry("themelios:portorium_aged_block", "Block", "Core");
        Map<Identifier, CatalogueEntry> entries = entries(slab, block,
                entry("erydon:portorium_aged_slab", "Slab", "Modern"),
                entry("erydon:portorium_aged_block", CatalogueClassifier.UNKNOWN, "Core"));
        FamilyDuplicates.mark(entries);

        assertEquals("", entries.get(slab.id()).duplicateGroup());
        assertEquals("", entries.get(block.id()).duplicateGroup());
    }

    private static CatalogueEntry entry(String idText, String shape, String style) {
        Identifier id = new Identifier(idText);
        return new CatalogueEntry(id, id.getNamespace(), "Portorium", "Aged", shape, style, idText, "",
                RelatedBlocks.profile(id, "Portorium", "Aged", shape, id.getPath(), List.of()), id.getPath(), "");
    }

    private static Map<Identifier, CatalogueEntry> entries(CatalogueEntry... items) {
        Map<Identifier, CatalogueEntry> entries = new LinkedHashMap<>();
        for (CatalogueEntry item : items) entries.put(item.id(), item);
        return entries;
    }
}
