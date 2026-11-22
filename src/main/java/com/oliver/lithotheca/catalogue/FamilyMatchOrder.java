package com.oliver.lithotheca.catalogue;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

/** Orders already-filtered Collection entries against a known block. */
public final class FamilyMatchOrder {
    private static final Pattern WORD_SEPARATOR = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> INLAY_VARIANTS = Set.of("bronze", "silver", "grout");

    private FamilyMatchOrder() {
    }

    public static boolean supports(CatalogueEntry target) {
        return switch (target.source()) {
            case "erydon", "themelios", "daedalon" -> true;
            default -> false;
        };
    }

    public static ToIntFunction<CatalogueEntry> scorer(CatalogueEntry target, boolean textures) {
        Set<String> targetPattern = patternWords(target.texture());
        Set<String> targetForm = formWords(target);

        return candidate -> {
            if (target.id().equals(candidate.id())) return Integer.MAX_VALUE;

            boolean sameTexture = sameKnownFacet(target.texture(), candidate.texture());
            boolean sameShape = sameKnownFacet(target.shape(), candidate.shape());
            int score = sameKnownFacet(target.material(), candidate.material()) ? 16_000 : 0;
            // Each pivot keeps one facet fixed by default, then prefers the exact value of the facet being explored.
            if (textures ? sameShape : sameTexture) score += 8_000;
            if (textures ? sameTexture : sameShape) score += 4_000;
            if (!targetPattern.isEmpty() && targetPattern.equals(patternWords(candidate.texture()))) score += 1_000;

            score += formSimilarity(targetForm, formWords(candidate));
            if (sameKnownFacet(target.style(), candidate.style())) score += 2;
            if (target.source().equals(candidate.source())) score++;
            return score;
        };
    }

    private static boolean sameKnownFacet(String first, String second) {
        return !first.equals(CatalogueClassifier.UNKNOWN) && first.equals(second);
    }

    private static Set<String> patternWords(String texture) {
        Set<String> words = words(texture);
        words.removeAll(INLAY_VARIANTS);
        return words;
    }

    private static Set<String> formWords(CatalogueEntry entry) {
        // Registry paths retain form descriptors, including legacy word order, across display-name and shader changes.
        Set<String> words = words(entry.id().getPath());
        removeWords(words, entry.material());
        removeWords(words, entry.texture());
        removeWords(words, entry.style());
        return words;
    }

    private static void removeWords(Set<String> words, String value) {
        for (String token : WORD_SEPARATOR.split(value.toLowerCase(Locale.ROOT))) {
            words.remove(token);
        }
    }

    private static Set<String> words(String value) {
        Set<String> words = new HashSet<>();
        for (String token : WORD_SEPARATOR.split(value.toLowerCase(Locale.ROOT))) {
            if (!token.isEmpty() && !token.equals("unclassified")) words.add(token);
        }
        return words;
    }

    private static int formSimilarity(Set<String> target, Set<String> candidate) {
        if (target.isEmpty() || candidate.isEmpty()) return 0;
        int shared = 0;
        for (String word : target) {
            if (candidate.contains(word)) shared++;
        }
        // A shallow vertical slope retains both words in "vertical slope". A plain slope retains only one.
        int coverage = 300 * shared / target.size();
        int overlap = 100 * shared / (target.size() + candidate.size() - shared);
        return coverage + overlap;
    }
}
