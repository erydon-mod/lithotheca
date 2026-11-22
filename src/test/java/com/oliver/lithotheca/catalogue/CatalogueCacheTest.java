package com.oliver.lithotheca.catalogue;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CatalogueCacheTest {
    @TempDir Path cache;

    @Test
    void retainsAnAbsentSourceAndReusesItWhenTheSameBlocksReturn() throws IOException {
        List<BlockCatalogue.Seed> original = List.of(seed("returning_test", "marble_stairs", "stairs"));
        var first = CatalogueCache.facts(cache, "returning_test", original);
        Path saved = cache.resolve("returning_test.json");
        Files.setLastModifiedTime(saved, FileTime.fromMillis(1_600_000_000_000L));
        FileTime unchanged = Files.getLastModifiedTime(saved);

        CatalogueCache.facts(cache, "other_test", List.of(seed("other_test", "marble_slab", "slab")));
        assertEquals(unchanged, Files.getLastModifiedTime(saved));
        assertEquals(first, CatalogueCache.facts(cache, "returning_test", original));
        assertEquals(unchanged, Files.getLastModifiedTime(saved));

        var updated = CatalogueCache.facts(cache, "returning_test",
            List.of(seed("returning_test", "marble_slab", "slab")));
        assertNotEquals(first.keySet(), updated.keySet());
        assertNotEquals(unchanged, Files.getLastModifiedTime(saved));
    }

    private static BlockCatalogue.Seed seed(String namespace, String path, String shape) {
        return new BlockCatalogue.Seed(new Identifier(namespace, path), shape, path, List.of());
    }
}
