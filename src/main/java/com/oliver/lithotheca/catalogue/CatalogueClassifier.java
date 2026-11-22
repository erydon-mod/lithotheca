package com.oliver.lithotheca.catalogue;

import net.minecraft.block.AbstractGlassBlock;
import net.minecraft.block.Block;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class CatalogueClassifier {
    static final String UNKNOWN = "Unclassified";

    private static final Map<String, String> FAMILY_STONES = Map.ofEntries(
            Map.entry("aganite", "agate gemstone"),
            Map.entry("aterzon", "panda marble"),
            Map.entry("borealis", "labradorite gemstone"),
            Map.entry("brectite", "breche de vendome marble"),
            Map.entry("calacattum", "calacatta oro marble"),
            Map.entry("chalstrom", "tempest gold marble"),
            Map.entry("chrysonyx", "black and gold onyx"),
            Map.entry("etruscus", "dark green etruscan marble"),
            Map.entry("gelastrum", "azul macaubas quartzite"),
            Map.entry("glacium", "statuario marble"),
            Map.entry("hesperion", "sodalite blue stone"),
            Map.entry("imperium", "emperador dark marble"),
            Map.entry("kelastrion", "belgian blue limestone"),
            Map.entry("kylorion", "rainforest marble"),
            Map.entry("latmion", "limestone"),
            Map.entry("laurentium", "noir saint laurent marble"),
            Map.entry("mielonyx", "honey onyx"),
            Map.entry("nerium", "nero marquina marble"),
            Map.entry("noxoplis", "black opal"),
            Map.entry("porphyros", "rosso orobico marble"),
            Map.entry("portorium", "portoro marble"),
            Map.entry("psamatheon", "sandstone"),
            Map.entry("rosinium", "rosa portugalo marble"),
            Map.entry("sanguenite", "rosso levanto marble"),
            Map.entry("selenephos", "pink alabaster"),
            Map.entry("solistra", "giallo siena marble"),
            Map.entry("striatus", "striped travertine")
    );
    private static final Set<String> FAMILY_SOURCES = Set.of("erydon", "themelios", "daedalon");
    private static final String DAEDALON_SPARTAN_PREFIX = "statue_spartan_promachos_";
    private static final Set<String> BROAD_MATERIALS = Set.of("Stone", "Wood", "Metal");
    private static final Set<String> MATERIAL_TAG_NAMESPACES = Set.of("material", "c", "forge", "minecraft");
    private static final Map<String, String> MATERIAL_TAGS = Map.ofEntries(
            Map.entry("stone", "Stone"), Map.entry("stones", "Stone"),
            Map.entry("wood", "Wood"), Map.entry("logs", "Wood"),
            Map.entry("planks", "Wood"), Map.entry("wooden_slabs", "Wood"),
            Map.entry("glass", "Glass"), Map.entry("glass_blocks", "Glass"),
            Map.entry("glass_panes", "Glass"), Map.entry("metal", "Metal"),
            Map.entry("terracotta", "Terracotta"), Map.entry("wool", "Wool"),
            Map.entry("sand", "Sand"), Map.entry("dirt", "Dirt"),
            Map.entry("sandstone", "Sandstone"), Map.entry("limestone", "Limestone"),
            Map.entry("marble", "Marble"), Map.entry("gemstone", "Gemstone"),
            Map.entry("leaves", "Leaves")
    );
    private static final List<String> MATERIAL_TOKENS = List.of(
            "dark_oak", "warped", "crimson", "mangrove", "cherry", "bamboo",
            "acacia", "birch", "spruce", "jungle", "oak", "copper", "bronze", "silver",
            "gold", "iron", "netherite", "lapis", "quartz", "marble", "limestone",
            "sandstone", "granite", "diorite", "andesite", "deepslate", "blackstone", "prismarine",
            "cobblestone", "basalt", "obsidian", "tuff", "calcite", "terracotta", "concrete",
            "glass", "wool", "dirt", "sand", "gravel", "clay", "brick", "stone", "wood"
    );
    private static final List<String> TEXTURE_TOKENS = List.of(
            "weathered", "oxidized", "exposed", "mossy", "cracked", "chiseled",
            "polished", "smooth", "stripped", "cut", "glazed", "rusticated", "ashlar", "hewn", "aged"
    );
    private static final List<String> FAMILY_TEXTURES = List.of(
            "herringbone_bronze", "herringbone_grout", "weave_bronze", "weave_grout",
            "trim_bronze", "trim_silver", "guilloche_bronze", "guilloche_silver",
            "quatrefoil_bronze", "quatrefoil_silver", "rosette_bronze", "rosette_silver",
            "aged", "ashlar", "hewn", "rusticated", "rock", "herringbone", "weave",
            "trim", "guilloche", "quatrefoil", "rosette", "diaphanes"
    );
    // Daedalon's catalogue groups named designs by their form suffix.
    private static final List<String> DAEDALON_FORMS = List.of(
            "statue", "bust", "urn", "capital", "corbel", "plinth", "finial", "frieze",
            "panel", "bench", "planter", "dome", "basin", "fountain", "monument", "lantern"
    );
    private static final List<String> ARCHITECTURAL_STYLES = List.of(
            "gothic_ornate", "french_georgian", "byzantine", "romanesque", "georgian", "gothic", "modern"
    );
    private static final List<String> DAEDALON_STYLES = List.of(
            "greek_ionic", "roman_composite", "corinthian", "renaissance", "baroque", "ionic", "tuscan", "greek"
    );

    private static final List<String> SHAPE_TOKENS = List.of(
            "trapdoor", "fence_gate", "stairs", "slab", "pane", "window", "surround",
            "cornice", "alcove", "arch", "column", "cylinder", "coping", "chimney",
            "ceiling", "pendant", "light", "lamp", "fence", "door", "wall", "post", "layer",
            "slice", "slope", "statue", "bust", "plinth", "planter", "urn", "capital",
            "finial", "corbel", "cover", "block"
    );
    private static final Map<String, String> SHAPE_TAGS = Map.ofEntries(
            Map.entry("slab", "Slab"), Map.entry("slabs", "Slab"),
            Map.entry("wooden_slabs", "Slab"), Map.entry("stairs", "Stairs"),
            Map.entry("walls", "Wall"), Map.entry("fences", "Fence"),
            Map.entry("fence_gates", "Fence Gate"), Map.entry("doors", "Door"),
            Map.entry("trapdoors", "Trapdoor"), Map.entry("glass_panes", "Pane")
    );

    private CatalogueClassifier() {
    }

    static String shapeHint(Block block) {
        if (block instanceof SlabBlock) return "Slab";
        if (block instanceof StairsBlock) return "Stairs";
        if (block instanceof WallBlock) return "Wall";
        if (block instanceof FenceGateBlock) return "Fence Gate";
        if (block instanceof FenceBlock) return "Fence";
        if (block instanceof TrapdoorBlock) return "Trapdoor";
        if (block instanceof DoorBlock) return "Door";
        if (block instanceof PaneBlock) return "Pane";
        if (block.getClass() == Block.class || block instanceof AbstractGlassBlock) return "Block";
        return UNKNOWN;
    }

    static Facts classify(Identifier id, String shapeHint, List<String> aliases) {
        String namespace = id.getNamespace();
        String path = id.getPath();
        boolean family = FAMILY_SOURCES.contains(namespace);
        List<String> stones = family ? familyStones(namespace, path) : List.of();
        String authoredShape = namespace.equals("daedalon") ? daedalonShape(path) : UNKNOWN;
        String shape = knownOrElse(authoredShape, shape(path, shapeHint));
        String style = style(namespace, path, stones);
        String texture = family ? familyTexture(path, stones) : firstToken(path, TEXTURE_TOKENS);
        String material = stones.isEmpty()
                ? firstToken(path, MATERIAL_TOKENS)
                : String.join(" / ", stones.stream().map(CatalogueClassifier::title).toList());

        if (family && path.startsWith("glazing_")) material = "Glass";

        LinkedHashSet<String> terms = new LinkedHashSet<>(aliases);
        stones.forEach(stone -> terms.add(FAMILY_STONES.get(stone)));
        if (family) {
            if (texture.equals("Standard")) terms.add("polished honed mirror ganosis");
            if (texture.equals("Aged")) terms.add("weathered");
            if (style.equals("Modern")) terms.add("simple clean plain minimal contemporary");
            if (style.equals("Byzantine") || contains(path, "guilloche")) {
                terms.add("byzantine guilloche motif");
            }
            if (shape.equals("Column") && namespace.equals("erydon")) {
                terms.add("pillar guilloche capital byzantine capital");
            }
        }
        return new Facts(material, texture, shape, style, List.copyOf(terms));
    }

    static CatalogueEntry entry(Identifier id, Facts facts, List<String> tags, String name) {
        return entry(id, facts, tags, name, "");
    }

    static CatalogueEntry entry(Identifier id, Facts facts, List<String> tags, String name, String surfaceFinish) {
        boolean family = FAMILY_SOURCES.contains(id.getNamespace());
        String taggedMaterial = taggedMaterial(tags);
        String material = facts.material();
        if ((!family || material.equals(UNKNOWN)) && !taggedMaterial.equals(UNKNOWN)
                && (!BROAD_MATERIALS.contains(taggedMaterial) || material.equals(UNKNOWN))) {
            material = taggedMaterial;
        }
        String shape = knownOrElse(facts.shape(), taggedShape(tags));
        String style = family && !facts.style().equals(UNKNOWN)
                ? facts.style()
                : knownOrElse(taggedStyle(tags), facts.style());
        String searchText = String.join(" ", id.toString(), title(id.getPath()), name,
                material, facts.texture(), shape, style, String.join(" ", facts.searchTerms()));
        return new CatalogueEntry(id, id.getNamespace(), material, facts.texture(), shape, style,
                searchText.toLowerCase(Locale.ROOT), "",
                RelatedBlocks.profile(id, material, facts.texture(), shape, name, tags), name,
                facts.texture().equals("Standard") ? surfaceFinish : "");
    }

    static boolean hasStandardTexture(Identifier id) {
        return FAMILY_SOURCES.contains(id.getNamespace())
            && familyTexture(id.getPath(), familyStones(id.getNamespace(), id.getPath())).equals("Standard");
    }

    private static List<String> familyStones(String namespace, String path) {
        if (namespace.equals("daedalon") && path.startsWith(DAEDALON_SPARTAN_PREFIX)) {
            String material = path.substring(DAEDALON_SPARTAN_PREFIX.length());
            if (material.endsWith("_aged")) material = material.substring(0, material.length() - 5);
            return FAMILY_STONES.containsKey(material) ? List.of(material) : List.of();
        }

        List<String> stones = new ArrayList<>(2);
        String[] tokens = path.split("_", 3);
        if (FAMILY_STONES.containsKey(tokens[0])) stones.add(tokens[0]);
        if (contains(path, "weave") && tokens.length > 1 && FAMILY_STONES.containsKey(tokens[1])
                && !stones.contains(tokens[1])) {
            stones.add(tokens[1]);
        }
        return stones;
    }

    private static String familyTexture(String path, List<String> stones) {
        String texture = firstToken(path, FAMILY_TEXTURES);
        if (!texture.equals(UNKNOWN)) return texture;
        if (stones.isEmpty()) return firstToken(path, TEXTURE_TOKENS);
        return "Standard";
    }

    private static String style(String namespace, String path, List<String> stones) {
        if (namespace.equals("minecraft")) return "Core";
        if (namespace.equals("erydon") || namespace.equals("daedalon")) {
            String style = firstToken(path, ARCHITECTURAL_STYLES);
            if (namespace.equals("daedalon") && style.equals(UNKNOWN)) {
                style = firstToken(path, DAEDALON_STYLES);
            }
            if (!style.equals(UNKNOWN)) return style;
        }
        return stones.isEmpty() ? UNKNOWN : "Core";
    }

    private static String daedalonShape(String path) {
        if (path.startsWith(DAEDALON_SPARTAN_PREFIX)) return "Statue";
        if (path.endsWith("_hedra") || path.endsWith("_exedra")) return "Bench";
        for (String form : DAEDALON_FORMS) {
            if (path.endsWith("_" + form)) return title(form);
        }
        return UNKNOWN;
    }

    private static String shape(String path, String hint) {
        if (!hint.equals(UNKNOWN)) return hint;
        if (contains(path, "layer_vertical")) return "Vertical Layer";
        if (contains(path, "layer_multiface")) return "Multiface Layer";
        if (contains(path, "slice_horizontal") || contains(path, "horizontal_slice")) return "Horizontal Slice";
        if (contains(path, "slice_vertical") || contains(path, "vertical_slice")) return "Vertical Slice";
        if (contains(path, "cornice_small")) return "Small Cornice";
        if (contains(path, "cornice_large")) return "Large Cornice";
        if (contains(path, "cylinder_small")) return "Small Cylinder";
        if (contains(path, "cylinder_large")) return "Large Cylinder";
        return firstToken(path, SHAPE_TOKENS);
    }

    private static String taggedMaterial(List<String> tags) {
        String generic = UNKNOWN;
        for (String tag : tags) {
            int colon = tag.indexOf(':');
            if (!MATERIAL_TAG_NAMESPACES.contains(tag.substring(0, colon))) continue;
            String material = MATERIAL_TAGS.get(tag.substring(colon + 1));
            if (material == null) continue;
            if (!BROAD_MATERIALS.contains(material)) return material;
            generic = material;
        }
        return generic;
    }

    static String taggedShape(List<String> tags) {
        for (String tag : tags) {
            int colon = tag.indexOf(':');
            if (!MATERIAL_TAG_NAMESPACES.contains(tag.substring(0, colon))) continue;
            String shape = SHAPE_TAGS.get(tag.substring(colon + 1));
            if (shape != null) return shape;
        }
        return UNKNOWN;
    }

    private static String taggedStyle(List<String> tags) {
        List<String> candidates = tags.stream()
                .filter(tag -> tag.startsWith("style:"))
                .map(tag -> tag.substring("style:".length()))
                .filter(style -> !style.equals("guilloche"))
                .distinct()
                .toList();
        return candidates.size() == 1 ? title(candidates.get(0)) : UNKNOWN;
    }

    private static String knownOrElse(String value, String alternative) {
        return value.equals(UNKNOWN) ? alternative : value;
    }

    private static String firstToken(String path, List<String> tokens) {
        for (String token : tokens) {
            if (contains(path, token)) return title(token);
        }
        return UNKNOWN;
    }

    private static boolean contains(String path, String token) {
        for (int start = path.indexOf(token); start >= 0; start = path.indexOf(token, start + 1)) {
            int end = start + token.length();
            if ((start == 0 || path.charAt(start - 1) == '_')
                    && (end == path.length() || path.charAt(end) == '_')) return true;
        }
        return false;
    }

    private static String title(String words) {
        StringBuilder title = new StringBuilder(words.length());
        for (String word : words.split("[_/]+")) {
            if (word.isEmpty()) continue;
            if (!title.isEmpty()) title.append(' ');
            title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return title.toString();
    }

    record Facts(String material, String texture, String shape, String style, List<String> searchTerms) {
    }
}
