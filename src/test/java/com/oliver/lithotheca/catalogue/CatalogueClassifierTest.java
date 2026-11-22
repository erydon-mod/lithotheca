package com.oliver.lithotheca.catalogue;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogueClassifierTest {
    @Test
    void classifiesDaedalonByItsAuthoredFormsAndNamedStyles() {
        CatalogueClassifier.Facts lantern = classify("daedalon:portorium_aged_gothic_lantern");
        assertEquals("Portorium", lantern.material());
        assertEquals("Aged", lantern.texture());
        assertEquals("Lantern", lantern.shape());
        assertEquals("Gothic", lantern.style());

        CatalogueClassifier.Facts frieze = classify("daedalon:portorium_aged_byzantine_frieze");
        assertEquals("Frieze", frieze.shape());
        assertEquals("Byzantine", frieze.style());
        assertEquals("Panel", classify("daedalon:portorium_gothic_wall_panel").shape());
        assertEquals("Basin", classify("daedalon:portorium_georgian_fountain_basin").shape());
        assertEquals("Bench", classify("daedalon:portorium_hedra").shape());
    }

    @Test
    void distinguishesStyleAndMotifWhileKeepingSharedSearchVocabulary() {
        assertEquals("Gothic", classify("erydon:portorium_aged_arch_gothic").style());
        assertEquals("Gothic Ornate", classify("erydon:portorium_aged_surround_gothic_ornate").style());
        assertEquals("Byzantine", classify("erydon:portorium_aged_cornice_byzantine").style());

        Identifier id = new Identifier("erydon:portorium_guilloche_bronze_block");
        CatalogueClassifier.Facts inlay = classify(id.toString());
        assertEquals("Guilloche Bronze", inlay.texture());
        assertEquals("Core", inlay.style());
        CatalogueEntry entry = CatalogueClassifier.entry(id, inlay, List.of("style:byzantine"), "Portorium Bronze Guilloche Inlay");
        assertEquals("Core", entry.style());
        assertTrue(entry.searchText().contains("byzantine"));
    }

    @Test
    void keepsPatternFinishesSeparateAcrossMatchingShapes() {
        assertEquals("Herringbone Grout", classify("erydon:portorium_herringbone_grout_block").texture());
        assertEquals("Herringbone Grout", classify("themelios:portorium_herringbone_grout_slab").texture());
        assertEquals("Herringbone Bronze", classify("erydon:portorium_herringbone_bronze_slab").texture());
        assertEquals("Weave Grout", classify("erydon:calacattum_portorium_weave_grout_block").texture());
        assertEquals("Weave Bronze", classify("erydon:calacattum_portorium_weave_bronze_block").texture());
        assertEquals("Trim Bronze", classify("themelios:portorium_trim_bronze_block").texture());
        assertEquals("Trim Silver", classify("themelios:portorium_trim_silver_block").texture());
    }

    private static CatalogueClassifier.Facts classify(String id) {
        return CatalogueClassifier.classify(new Identifier(id), CatalogueClassifier.UNKNOWN, List.of());
    }
}
