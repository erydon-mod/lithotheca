package com.oliver.lithotheca.catalogue;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class CatalogueCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("lithotheca");
    private static final Gson GSON = new Gson();
    private static final String SCHEMA = "4";

    private CatalogueCache() {
    }

    static Map<String, CatalogueClassifier.Facts> facts(String namespace, List<BlockCatalogue.Seed> seeds) {
        Path directory = FabricLoader.getInstance().getGameDir().resolve("lithotheca-cache").resolve("catalogue");
        return facts(directory, namespace, seeds);
    }

    static Map<String, CatalogueClassifier.Facts> facts(Path directory, String namespace, List<BlockCatalogue.Seed> seeds) {
        FabricLoader loader = FabricLoader.getInstance();
        Optional<ModContainer> container = loader.getModContainer(namespace);
        Optional<Path> vocabulary = container.flatMap(mod -> CatalogueMetadata.vocabularyPath(namespace, mod));

        String fingerprint = fingerprint(seeds, container, vocabulary);
        Path file = directory.resolve(namespace + ".json");
        CachedSource cached = read(file);
        if (cached != null && fingerprint.equals(cached.fingerprint()) && isComplete(cached.facts(), seeds)) {
            return cached.facts();
        }

        CatalogueMetadata metadata = CatalogueMetadata.read(namespace, vocabulary);
        Map<String, CatalogueClassifier.Facts> facts = new HashMap<>();
        for (BlockCatalogue.Seed seed : seeds) {
            Identifier id = seed.id();
            facts.put(id.getPath(), CatalogueClassifier.classify(id, seed.shapeHint(), metadata.terms(id)));
        }

        write(file, new CachedSource(fingerprint, facts));
        return facts;
    }

    private static String fingerprint(List<BlockCatalogue.Seed> seeds, Optional<ModContainer> mod,
                                      Optional<Path> vocabulary) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }

        add(digest, SCHEMA);
        FabricLoader loader = FabricLoader.getInstance();
        add(digest, loader.getModContainer("minecraft").map(CatalogueCache::version).orElse("minecraft"));
        mod.ifPresent(container -> addOrigin(digest, container));
        vocabulary.ifPresent(path -> addFileStamp(digest, path));
        for (BlockCatalogue.Seed seed : seeds) {
            add(digest, seed.id().toString());
            add(digest, seed.shapeHint());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void addOrigin(MessageDigest digest, ModContainer mod) {
        add(digest, version(mod));
        ModOrigin origin = mod.getOrigin();
        if (origin.getKind() == ModOrigin.Kind.PATH) {
            origin.getPaths().forEach(path -> addFileStamp(digest, path));
        } else if (origin.getKind() == ModOrigin.Kind.NESTED) {
            add(digest, origin.getParentSubLocation());
            mod.getContainingMod().ifPresent(parent -> addOrigin(digest, parent));
        }
    }

    private static String version(ModContainer mod) {
        return mod.getMetadata().getId() + "@" + mod.getMetadata().getVersion().getFriendlyString();
    }

    private static void addFileStamp(MessageDigest digest, Path path) {
        try {
            add(digest, Files.size(path) + "@" + Files.getLastModifiedTime(path).toMillis());
        } catch (IOException | RuntimeException exception) {
            add(digest, "unavailable");
        }
    }

    private static void add(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static CachedSource read(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try (Reader reader = Files.newBufferedReader(file)) {
            return GSON.fromJson(reader, CachedSource.class);
        } catch (IOException | RuntimeException exception) {
            LOGGER.debug("Rebuilding unreadable catalogue cache {}", file.getFileName(), exception);
            return null;
        }
    }

    private static boolean isComplete(Map<String, CatalogueClassifier.Facts> cached, List<BlockCatalogue.Seed> seeds) {
        if (cached == null || cached.size() != seeds.size()) return false;
        for (BlockCatalogue.Seed seed : seeds) {
            CatalogueClassifier.Facts facts = cached.get(seed.id().getPath());
            if (facts == null || facts.material() == null || facts.texture() == null || facts.shape() == null
                    || facts.style() == null || facts.searchTerms() == null) return false;
        }
        return true;
    }

    private static void write(Path file, CachedSource cache) {
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "index-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(cache, writer);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.debug("Could not save catalogue cache {}", file.getFileName(), exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException exception) {
                    LOGGER.debug("Could not remove temporary catalogue cache", exception);
                }
            }
        }
    }

    private record CachedSource(String fingerprint, Map<String, CatalogueClassifier.Facts> facts) {
    }
}
