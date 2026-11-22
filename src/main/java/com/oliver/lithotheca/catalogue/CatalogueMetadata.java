package com.oliver.lithotheca.catalogue;

import com.google.gson.Gson;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class CatalogueMetadata {
    private static final Logger LOGGER = LoggerFactory.getLogger("lithotheca");
    private static final Gson GSON = new Gson();
    private static final String SPARTAN_PREFIX = "statue_spartan_promachos_";

    private final Map<String, List<String>> aliasTerms;
    private final DaedalonVocabulary daedalon;

    private CatalogueMetadata(Map<String, List<String>> aliasTerms, DaedalonVocabulary daedalon) {
        this.aliasTerms = aliasTerms;
        this.daedalon = daedalon;
    }

    static Optional<Path> vocabularyPath(String source, ModContainer container) {
        return switch (source) {
            case "erydon", "themelios" -> container.findPath("data/" + source + "/id_migration.tsv");
            case "daedalon" -> container.findPath("assets/daedalon/search_vocabulary.json");
            default -> Optional.empty();
        };
    }

    static CatalogueMetadata read(String source, Optional<Path> path) {
        if (path.isPresent()) {
            try (BufferedReader reader = Files.newBufferedReader(path.get())) {
                if (source.equals("daedalon")) {
                    return new CatalogueMetadata(Map.of(), GSON.fromJson(reader, DaedalonVocabulary.class));
                }
                return new CatalogueMetadata(readAliases(reader), null);
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Could not read {} catalogue vocabulary", source, exception);
            }
        }
        return new CatalogueMetadata(Map.of(), null);
    }

    private static Map<String, List<String>> readAliases(BufferedReader reader) throws IOException {
        String header = reader.readLine();
        if (header == null) return Map.of();

        List<String> columns = Arrays.asList(header.replace("\uFEFF", "").split("\t", -1));
        int canonical = columns.indexOf("canonical_path");
        int mode = columns.indexOf("mode");
        if (canonical < 0) return Map.of();

        int oldPath = columns.indexOf("old_path");
        int oldName = columns.indexOf("old_display_name");
        int newName = columns.indexOf("canonical_display_name");
        Map<String, List<String>> aliases = new HashMap<>();
        String row;
        while ((row = reader.readLine()) != null) {
            String[] fields = row.split("\t", -1);
            if (fields.length < columns.size() || fields[canonical].isBlank()) continue;

            List<String> terms = new ArrayList<>(4);
            if (mode < 0 || fields[mode].equalsIgnoreCase("PERMANENT_ALIAS")) {
                addField(terms, fields, oldPath);
                addField(terms, fields, oldName);
            }
            addField(terms, fields, newName);
            if (!terms.isEmpty()) {
                aliases.computeIfAbsent(fields[canonical], key -> new ArrayList<>()).addAll(terms);
            }
        }
        return aliases;
    }

    private static void addField(List<String> terms, String[] fields, int index) {
        if (index < 0 || fields[index].isBlank()) return;
        terms.add(fields[index]);
        if (fields[index].contains("_")) terms.add(fields[index].replace('_', ' '));
    }

    List<String> terms(Identifier id) {
        String path = id.getPath();
        if (daedalon == null || daedalon.families() == null || daedalon.materials() == null) {
            return aliasTerms.getOrDefault(path, List.of());
        }

        String[] materialAndFamily = path.split("_", 2);
        if (materialAndFamily.length < 2) return List.of();
        String material = materialAndFamily[0];
        String family = materialAndFamily[1].replaceFirst("^aged_", "");
        if (path.startsWith(SPARTAN_PREFIX)) {
            material = path.substring(SPARTAN_PREFIX.length()).replaceFirst("_aged$", "");
            family = "spartan_promachos_statue";
        }

        LinkedHashSet<String> terms = new LinkedHashSet<>();
        terms.add("deadalon");
        terms.addAll(daedalon.materials().getOrDefault(material, List.of()));
        terms.addAll(daedalon.families().getOrDefault(family, List.of()));
        return terms.stream().map(term -> term.replace('_', ' ')).toList();
    }

    private record DaedalonVocabulary(Map<String, List<String>> families, Map<String, List<String>> materials) {
    }
}
