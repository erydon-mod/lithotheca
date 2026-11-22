package com.oliver.lithotheca.catalogue;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public record CatalogueEntry(
        Identifier id,
        String source,
        String material,
        String texture,
        String shape,
        String style,
        String searchText,
        String duplicateGroup,
        RelatedBlocks.Profile related,
        String displayName,
        String surfaceFinish
) {
    CatalogueEntry withDuplicateGroup(String group) {
        return new CatalogueEntry(id, source, material, texture, shape, style, searchText, group, related, displayName, surfaceFinish);
    }

    public ItemStack stack() {
        return Registries.ITEM.get(id).getDefaultStack().copyWithCount(1);
    }

    public boolean inferred() {
        return switch (source) {
            case "minecraft", "erydon", "themelios", "daedalon" -> false;
            default -> true;
        };
    }
}
