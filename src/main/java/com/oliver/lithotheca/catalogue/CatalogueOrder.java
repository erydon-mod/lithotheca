package com.oliver.lithotheca.catalogue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

/** Keeps related forms together after the picker has applied its filters. */
public final class CatalogueOrder {
    private static final Pattern WORDS = Pattern.compile("[^\\p{L}\\p{Nd}]+");
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final List<String> TYPES = List.of(
        "block", "slab", "stairs", "wall", "fence", "fence gate", "door", "trapdoor", "pane", "window",
        "layer", "slice", "slope", "roof", "post", "beam", "column", "cylinder", "capital", "arch", "surround",
        "cornice", "coping", "ceiling", "corbel", "frieze", "panel", "cover", "chimney", "light", "lamp",
        "pendant", "lantern", "statue", "bust", "monument", "plinth", "finial", "urn", "planter", "basin",
        "fountain", "bench", "dome", "log", "foliage", "sapling", "flower", "groundcover", "ore", "fluid", "portal"
    );

    private CatalogueOrder() {
    }

    public static void sort(List<CatalogueEntry> entries, CatalogueEntry target, String query,
                            ToIntFunction<CatalogueEntry> matchScore) {
        if (entries.size() < 2) return;
        String phrase = SPACE.matcher(query.strip().toLowerCase(Locale.ROOT)).replaceAll(" ");
        Set<String> terms = words(phrase);
        Map<String, Group> groups = new HashMap<>();
        boolean targetIncluded = false;
        for (CatalogueEntry entry : entries) {
            int textRank = terms.isEmpty() ? 0 : textRank(entry, phrase, terms);
            int matchRank = matchScore == null ? 0 : matchScore.applyAsInt(entry);
            groups.computeIfAbsent(type(entry), Group::new).add(new Ranked(entry, textRank, matchRank));
            if (target != null && entry.id().equals(target.id())) targetIncluded = true;
        }

        String targetType = target == null ? "" : type(target);
        boolean knownTargetType = target != null && !targetType.equals("unclassified");
        boolean pinTargetGroup = targetIncluded;
        Comparator<Group> groupOrder = Comparator
            .comparingInt((Group group) -> pinTargetGroup && group.type.equals(targetType) ? 0 : 1)
            .thenComparing(Comparator.comparingInt((Group group) -> group.textRank).reversed())
            .thenComparingInt(group -> knownTargetType && group.type.equals(targetType) ? 0 : 1)
            .thenComparing(Comparator.comparingInt((Group group) -> group.matchRank).reversed())
            .thenComparingInt(group -> typeIndex(group.type))
            .thenComparing(group -> group.type, String.CASE_INSENSITIVE_ORDER);
        Comparator<Ranked> withinGroup = Comparator
            .comparingInt((Ranked ranked) -> target != null && ranked.entry.id().equals(target.id()) ? 0 : 1)
            .thenComparing(Comparator.comparingInt(Ranked::textRank).reversed())
            .thenComparing(Comparator.comparingInt(Ranked::matchRank).reversed())
            .thenComparing(ranked -> ranked.entry.shape(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ranked -> ranked.entry.material(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ranked -> ranked.entry.texture(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ranked -> ranked.entry.style(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ranked -> ranked.entry.displayName(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ranked -> ranked.entry.id());

        List<Group> orderedGroups = new ArrayList<>(groups.values());
        orderedGroups.sort(groupOrder);
        entries.clear();
        for (Group group : orderedGroups) {
            group.entries.sort(withinGroup);
            for (Ranked ranked : group.entries) entries.add(ranked.entry);
        }
    }

    private static String type(CatalogueEntry entry) {
        String shape = entry.shape().toLowerCase(Locale.ROOT);
        if (shape.equals("block") || shape.equals("unclassified")) {
            String inferred = entry.related().shape();
            if (!inferred.isEmpty() && !inferred.equals("block")) shape = inferred;
            else if (!entry.related().kind().isEmpty()) shape = entry.related().kind();
        }
        return switch (shape) {
            case "small cornice", "large cornice" -> "cornice";
            case "small cylinder", "large cylinder" -> "cylinder";
            case "horizontal slice", "vertical slice" -> "slice";
            case "vertical layer", "multiface layer" -> "layer";
            case "pillar" -> "column";
            case "", "unclassified" -> "unclassified";
            default -> shape;
        };
    }

    private static int typeIndex(String type) {
        int index = TYPES.indexOf(type);
        return index < 0 ? TYPES.size() : index;
    }

    private static int textRank(CatalogueEntry entry, String phrase, Set<String> terms) {
        String name = entry.displayName().toLowerCase(Locale.ROOT);
        String path = entry.id().getPath().replace('_', ' ');
        int phraseRank = name.equals(phrase) ? 4 : name.startsWith(phrase + " ") ? 3
            : containsPhrase(name, phrase) ? 2 : containsPhrase(path, phrase) ? 1 : 0;
        Set<String> nameWords = words(name);
        Set<String> pathWords = words(path);
        int exact = 0;
        int prefixes = 0;
        for (String term : terms) {
            if (nameWords.contains(term)) exact += 2;
            else if (hasPrefix(nameWords, term)) prefixes += 2;
            if (pathWords.contains(term)) exact++;
            else if (hasPrefix(pathWords, term)) prefixes++;
        }
        return phraseRank * 10_000 + exact * 100 + prefixes;
    }

    private static boolean containsPhrase(String text, String phrase) {
        int start = text.indexOf(phrase);
        while (start >= 0) {
            int end = start + phrase.length();
            boolean left = start == 0 || !Character.isLetterOrDigit(text.charAt(start - 1));
            boolean right = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (left && right) return true;
            start = text.indexOf(phrase, start + 1);
        }
        return false;
    }

    private static boolean hasPrefix(Set<String> words, String prefix) {
        for (String word : words) {
            if (word.startsWith(prefix)) return true;
        }
        return false;
    }

    private static Set<String> words(String value) {
        Set<String> words = new HashSet<>();
        for (String word : WORDS.split(value)) {
            if (!word.isEmpty()) words.add(word);
        }
        return words;
    }

    private record Ranked(CatalogueEntry entry, int textRank, int matchRank) {
    }

    private static final class Group {
        private final String type;
        private final List<Ranked> entries = new ArrayList<>();
        private int textRank = Integer.MIN_VALUE;
        private int matchRank = Integer.MIN_VALUE;

        private Group(String type) {
            this.type = type;
        }

        private void add(Ranked entry) {
            entries.add(entry);
            if (entry.textRank > textRank || entry.textRank == textRank && entry.matchRank > matchRank) {
                textRank = entry.textRank;
                matchRank = entry.matchRank;
            }
        }
    }
}
