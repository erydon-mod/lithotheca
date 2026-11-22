package com.oliver.lithotheca.catalogue;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FamilyFinishesTest {
    @BeforeEach
    void resetApi() {
        Preferences.settings = new Settings(true, Map.of());
        Preferences.reads = 0;
        Materials.includesReads = 0;
        Materials.levelReads = 0;
    }

    @Test
    void capturesOwningApiLevelsAcrossAllThreeSourcesAndPreservesTheSnapshot() throws ReflectiveOperationException {
        Identifier honed = new Identifier("erydon", "glacium_block");
        Identifier polished = new Identifier("themelios", "portorium_block");
        Identifier ganosis = new Identifier("daedalon", "statue_spartan_promachos_striatus");
        Preferences.settings = new Settings(true, Map.of(honed.getPath(), Level.HONED,
            polished.getPath(), Level.POLISHED, ganosis.getPath(), Level.MIRROR));
        FamilyFinishes.Api api = new FamilyFinishes.Api(Preferences.class, Materials.class);

        FamilyFinishes.Snapshot snapshot = api.capture(List.of(honed, polished, ganosis));
        assertEquals("honed", snapshot.resolve(honed));
        assertEquals("polished", snapshot.resolve(polished));
        assertEquals("ganosis", snapshot.resolve(ganosis));
        assertEquals(1, Preferences.reads);
        assertEquals(3, Materials.levelReads);

        Preferences.settings = new Settings(false, Map.of());
        assertEquals("ganosis", snapshot.resolve(ganosis));
        assertEquals(3, Materials.levelReads);
        assertEquals("honed", api.capture(List.of(ganosis)).resolve(ganosis));
        assertEquals(2, Preferences.reads);
    }

    @Test
    void excludesAuthoredTexturesAndHonoursOwningApiExclusions() throws ReflectiveOperationException {
        List<Identifier> ids = List.of(new Identifier("erydon", "glacium_aged_block"),
            new Identifier("erydon", "glacium_trim_silver_slope_vertical"),
            new Identifier("erydon", "glacium_herringbone_grout_block"),
            new Identifier("themelios", "glacium_weave_bronze_block"),
            new Identifier("demo", "glacium_block"), new Identifier("minecraft", "stone"),
            new Identifier("erydon", "glacium_excluded_block"));
        FamilyFinishes.Snapshot snapshot = new FamilyFinishes.Api(Preferences.class, Materials.class).capture(ids);

        assertTrue(snapshot.finishes().isEmpty());
        assertEquals(1, Materials.includesReads);
        assertEquals(0, Materials.levelReads);
        assertEquals("", snapshot.resolve(ids.get(0)));
    }

    @Test
    void entryKeepsItsTextureAndCarriesFinishThroughDuplicateMarking() {
        Identifier id = new Identifier("erydon", "glacium_block");
        var facts = CatalogueClassifier.classify(id, "Block", List.of());
        CatalogueEntry entry = CatalogueClassifier.entry(id, facts, List.of(), "Glacium Block (Ganosis)", "ganosis");
        CatalogueEntry marked = entry.withDuplicateGroup(id.toString());

        assertEquals("Standard", entry.texture());
        assertEquals("ganosis", marked.surfaceFinish());
        assertEquals(entry.displayName(), marked.displayName());
        assertSame(entry.related(), marked.related());
        Identifier agedId = new Identifier("erydon", "glacium_aged_block");
        var agedFacts = CatalogueClassifier.classify(agedId, "Block", List.of());
        assertEquals("", CatalogueClassifier.entry(agedId, agedFacts, List.of(), "Glacium Aged Block", "ganosis").surfaceFinish());
    }

    public record Settings(boolean enabled, Map<String, Level> levels) {
    }

    public enum Level { HONED, POLISHED, MIRROR }

    public static final class Preferences {
        private static Settings settings;
        private static int reads;

        public static Settings activeSettings() {
            reads++;
            return settings;
        }
    }

    public static final class Materials {
        private static int includesReads;
        private static int levelReads;

        public static boolean includes(String namespace, String path) {
            includesReads++;
            return !path.contains("_excluded_");
        }

        public static Level level(Settings settings, String path) {
            levelReads++;
            return settings.enabled() ? settings.levels().getOrDefault(path, Level.MIRROR) : Level.HONED;
        }
    }
}
