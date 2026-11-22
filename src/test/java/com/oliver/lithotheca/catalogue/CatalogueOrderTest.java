package com.oliver.lithotheca.catalogue;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogueOrderTest {
    @Test
    void copingMatchKeepsTheTargetAndRelatedCopingFirstAndSculptureTypesTogether() {
        CatalogueEntry target = entry("erydon:striatus_aged_coping_georgian", "Striatus Aged Georgian Coping");
        CatalogueEntry grand = entry("erydon:striatus_aged_coping_georgian_grand", "Striatus Aged Georgian Grand Coping");
        List<CatalogueEntry> entries = new ArrayList<>(List.of(
            entry("daedalon:striatus_aged_aphrodite_bust", "Striatus Aged Aphrodite Bust"),
            entry("daedalon:striatus_aged_aphrodite_statue", "Striatus Aged Aphrodite Statue"),
            entry("daedalon:striatus_aged_amphora_urn", "Striatus Aged Amphora Urn"),
            entry("daedalon:striatus_aged_apollo_bust", "Striatus Aged Apollo Bust"),
            target,
            entry("daedalon:striatus_aged_diota_urn", "Striatus Aged Diota Urn"),
            grand,
            entry("daedalon:statue_spartan_promachos_striatus_aged", "Striatus Aged Spartan Promachos Statue"),
            entry("daedalon:striatus_aged_apollo_statue", "Striatus Aged Apollo Statue")
        ));
        var admitted = new HashSet<>(entries);

        CatalogueOrder.sort(entries, target, "", FamilyMatchOrder.scorer(target, false));

        assertEquals(List.of(target, grand), entries.subList(0, 2));
        assertContiguous(entries, "Statue", 3);
        assertContiguous(entries, "Bust", 2);
        assertContiguous(entries, "Urn", 2);
        assertEquals(admitted, new HashSet<>(entries));
        assertEquals(admitted.size(), entries.size());
    }

    @Test
    void directSearchWordsRankBeforeIncidentalVocabularyAndAlphabeticNames() {
        CatalogueEntry matching = entry("daedalon:striatus_aged_zeus_statue", "Striatus Aged Zeus Statue");
        CatalogueEntry incidental = entry("daedalon:striatus_aged_apollo_statue", "Striatus Aged Apollo Statue", "zeus");
        CatalogueEntry weakType = entry("daedalon:striatus_aged_amphora_urn", "Striatus Aged Amphora Urn", "zeus");
        List<CatalogueEntry> entries = new ArrayList<>(List.of(incidental, weakType, matching));

        CatalogueOrder.sort(entries, null, "zeus", null);

        assertEquals(List.of(matching, incidental, weakType), entries);
    }

    @Test
    void searchingForADifferentTypeOutranksTheExcludedTargetTypeWithoutAddingResults() {
        CatalogueEntry target = entry("erydon:striatus_aged_coping_georgian", "Striatus Aged Georgian Coping");
        CatalogueEntry incidentalCoping = entry("erydon:striatus_aged_coping_georgian_grand",
            "Striatus Aged Georgian Grand Coping", "urn base");
        CatalogueEntry urn = entry("daedalon:striatus_aged_amphora_urn", "Striatus Aged Amphora Urn");
        List<CatalogueEntry> entries = new ArrayList<>(List.of(incidentalCoping, urn));

        CatalogueOrder.sort(entries, target, "urn", FamilyMatchOrder.scorer(target, false));

        assertEquals(List.of(urn, incidentalCoping), entries);
        assertFalse(entries.contains(target));
    }

    @Test
    void thirdPartyDecorationsWithGenericBlockHintsStillGroupWithTheirActualForm() {
        CatalogueEntry thirdParty = entry("conquest:marble_athena_statue", "Marble Athena Statue");
        CatalogueEntry statue = entry("daedalon:striatus_aged_aphrodite_statue", "Striatus Aged Aphrodite Statue");
        CatalogueEntry bust = entry("daedalon:striatus_aged_aphrodite_bust", "Striatus Aged Aphrodite Bust");
        List<CatalogueEntry> entries = new ArrayList<>(List.of(statue, bust, thirdParty));

        CatalogueOrder.sort(entries, null, "", null);

        assertTrue(Math.abs(entries.indexOf(thirdParty) - entries.indexOf(statue)) == 1);
        assertTrue(entries.indexOf(bust) > entries.indexOf(thirdParty));
        assertTrue(entries.indexOf(bust) > entries.indexOf(statue));
    }

    private static void assertContiguous(List<CatalogueEntry> entries, String shape, int count) {
        int first = -1;
        int last = -1;
        int found = 0;
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).shape().equals(shape)) {
                if (first < 0) first = index;
                last = index;
                found++;
            }
        }
        assertEquals(count, found);
        assertEquals(count, last - first + 1);
    }

    private static CatalogueEntry entry(String idText, String name, String... vocabulary) {
        Identifier id = new Identifier(idText);
        String hint = id.getNamespace().equals("conquest") ? "Block" : CatalogueClassifier.UNKNOWN;
        var facts = CatalogueClassifier.classify(id, hint, List.of(vocabulary));
        return CatalogueClassifier.entry(id, facts, List.of(), name);
    }
}
