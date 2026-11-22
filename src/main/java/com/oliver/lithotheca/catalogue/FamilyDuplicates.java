package com.oliver.lithotheca.catalogue;

import net.minecraft.util.Identifier;

import java.util.Map;

/** Pairs the two namespaced copies of the same Collection catalogue entry. */
final class FamilyDuplicates {
    private FamilyDuplicates() {
    }

    static void mark(Map<Identifier, CatalogueEntry> entries) {
        entries.replaceAll((id, entry) -> mark(entry, entries));
    }

    private static CatalogueEntry mark(CatalogueEntry entry, Map<Identifier, CatalogueEntry> entries) {
        String counterpartNamespace = switch (entry.id().getNamespace()) {
            case "erydon" -> "themelios";
            case "themelios" -> "erydon";
            default -> "";
        };
        if (counterpartNamespace.isEmpty()) return entry;

        Identifier counterpartId = new Identifier(counterpartNamespace, entry.id().getPath());
        CatalogueEntry counterpart = entries.get(counterpartId);
        if (counterpart == null
                || !sameKnownFacet(entry.material(), counterpart.material())
                || !sameKnownFacet(entry.texture(), counterpart.texture())
                || !sameKnownFacet(entry.shape(), counterpart.shape())
                || !sameKnownFacet(entry.style(), counterpart.style())) {
            return entry;
        }

        String group = counterpartNamespace.equals("erydon") ? counterpartId.toString() : entry.id().toString();
        return entry.withDuplicateGroup(group);
    }

    private static boolean sameKnownFacet(String first, String second) {
        return !first.equals(CatalogueClassifier.UNKNOWN) && first.equals(second);
    }
}
