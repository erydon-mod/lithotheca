package com.oliver.lithotheca.catalogue;

import net.minecraft.util.Identifier;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Evidence used to rank a third-party palette. A shared colour or generic form is insufficient. */
public final class RelatedBlocks {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{Nd}]+");
    private static final Set<String> BROAD_MATERIALS = Set.of("stone", "wood", "metal", "cloth");
    private static final Set<String> MATERIALS = Set.of(
        "oak", "spruce", "birch", "jungle", "acacia", "mangrove", "cherry", "bamboo", "warped", "crimson",
        "walnut", "mahogany", "maple", "ebony", "fir", "pine", "cedar", "teak", "ash", "willow",
        "granite", "diorite", "andesite", "limestone", "sandstone", "marble", "slate", "shale", "travertine",
        "deepslate", "blackstone", "prismarine", "basalt", "obsidian", "tuff", "calcite", "quartz", "cobblestone",
        "soapstone", "mudstone", "alabaster", "chalk", "serpentine", "flint", "schist", "gabbro", "dolomite",
        "terracotta", "concrete", "glass", "wool", "dirt", "sand", "gravel", "clay", "mud", "plaster", "ceramic",
        "porcelain", "adobe", "thatch", "wicker", "leaves", "copper", "bronze", "silver", "gold", "iron",
        "steel", "lead", "zinc", "tin", "netherite", "lapis", "ice", "snow", "brick"
    );
    private static final List<String> COMPOUND_MATERIALS = List.of(
        "red nether brick", "nether brick", "mud brick", "end stone", "soul sand", "dark oak", "dark prismarine"
    );
    private static final List<String> COMPOUND_PIGMENTS = List.of(
        "light gray", "dark gray", "light blue", "dark blue", "light green", "dark green", "off white"
    );
    private static final Set<String> PIGMENTS = Set.of(
        "white", "gray", "black", "brown", "beige", "tan", "cream", "red", "orange", "yellow", "green", "lime",
        "cyan", "blue", "purple", "magenta", "pink", "maroon", "teal", "turquoise", "ochre", "olive", "navy",
        "rose", "peach", "salmon", "lilac"
    );
    private static final Set<String> FINISHES = Set.of(
        "aged", "weathered", "oxidized", "exposed", "mossy", "cracked", "chiseled", "polished", "smooth", "stripped",
        "cut", "glazed", "rusticated", "ashlar", "hewn", "rough", "carved", "cobble", "brick", "tile", "plank",
        "board", "herringbone", "weave", "checkered", "engraved", "embossed", "woven", "stained", "tinted",
        "fluted", "ornate", "plain", "painted", "varnished", "parquet", "shingle", "dirty", "gravel", "debris",
        "boulder", "overgrown", "sanded"
    );
    private static final Set<String> FORMS = Set.of(
        "slab", "stairs", "wall", "fence", "door", "trapdoor", "pane", "window", "arch", "column", "balustrade",
        "arrowslit", "capital", "cornice", "coping", "chimney", "ceiling", "lamp", "lantern", "post", "layer",
        "slice", "slope", "roof", "statue", "bust", "plinth", "planter", "urn", "finial", "corbel", "cover", "panel",
        "beam", "carpet", "mosaic", "grate", "paving", "counter", "pedestal", "pillar", "fountain", "bench", "block", "gate"
    );
    private static final Map<String, String> SPECIAL_KINDS = Map.ofEntries(
        Map.entry("ore", "ore"), Map.entry("leaves", "foliage"), Map.entry("leaf", "foliage"),
        Map.entry("sapling", "sapling"), Map.entry("flower", "flower"), Map.entry("grass", "groundcover"),
        Map.entry("log", "log"), Map.entry("bark", "log"), Map.entry("branch", "branch"),
        Map.entry("fluid", "fluid"), Map.entry("portal", "portal")
    );
    private static final Set<String> FILLER = Set.of(
        "unknown", "unclassified", "none", "generic", "standard", "basic", "normal", "default", "building", "decorative", "natural",
        "decoration", "item", "variant", "piece", "part", "type", "model", "color", "colored", "small", "large", "medium", "tiny",
        "thick", "thin", "tall", "wide", "upper", "lower", "top", "bottom", "left", "right", "side", "front", "back",
        "horizontal", "vertical", "diagonal", "double", "single", "corner", "eighth", "quarter", "half", "full",
        "light", "dark", "of", "the", "and", "with", "from", "for", "de", "la", "le"
    );
    private static final Map<String, String> SPELLINGS = Map.ofEntries(
        Map.entry("grey", "gray"), Map.entry("colour", "color"), Map.entry("coloured", "colored"),
        Map.entry("chiselled", "chiseled"), Map.entry("steps", "stairs"), Map.entry("slabs", "slab"),
        Map.entry("bricks", "brick"), Map.entry("tiles", "tile"), Map.entry("planks", "plank"),
        Map.entry("boards", "board"), Map.entry("blocks", "block"), Map.entry("walls", "wall"),
        Map.entry("panes", "pane"), Map.entry("arches", "arch"), Map.entry("pillars", "pillar"),
        Map.entry("columns", "column"), Map.entry("logs", "log"), Map.entry("shingles", "shingle")
    );

    private RelatedBlocks() {
    }

    public static int score(CatalogueEntry target, CatalogueEntry candidate, boolean textures) {
        if (target.id().equals(candidate.id())) return 1000;

        Profile reference = target.related();
        Profile other = candidate.related();
        if (!reference.kind().equals(other.kind())) return 0;
        boolean sameSource = target.source().equals(candidate.source());
        boolean sameMaterial = sameKnown(reference.material(), other.material());
        boolean specificMaterial = sameMaterial && !BROAD_MATERIALS.contains(reference.material());
        if (precise(reference.material()) && precise(other.material()) && !sameMaterial) return 0;

        boolean sameShape = sameKnown(reference.shape(), other.shape());
        boolean samePigment = sameKnown(reference.pigment(), other.pigment());
        boolean pigmentConflict = known(reference.pigment()) && known(other.pigment()) && !samePigment;
        boolean sharedFinish = overlaps(reference.finish(), other.finish());
        boolean sameFinish = sharedFinish && reference.finish().equals(other.finish());
        int shared = sharedWords(reference.family(), other.family());
        int largest = Math.max(reference.family().size(), other.family().size());
        boolean sameFamily = shared > 0 && shared == largest;
        boolean closeFamily = sameFamily && (sameSource || shared > 1 || sameMaterial)
            || shared >= 2 && shared * 3 >= largest * 2;
        boolean namedBroadMaterial = sameMaterial && reference.namedMaterial() && other.namedMaterial()
            && (textures ? sameShape : sharedFinish);

        if (!specificMaterial && !closeFamily && !namedBroadMaterial) return 0;
        if (textures && known(reference.shape()) && known(other.shape()) && !sameShape) return 0;
        if (!textures) {
            if (pigmentConflict) return 0;
            if (!reference.finish().isEmpty() && !sharedFinish
                && (!other.finish().isEmpty() || !closeFamily || !samePigment)) return 0;
            if (known(reference.pigment()) && !known(other.pigment()) && (!closeFamily || !sameFinish)) return 0;
        }

        int rank = (specificMaterial ? 40 : sameMaterial ? 15 : 0) + (closeFamily ? 35 : 0);
        if (sameSource) rank += 12;
        if (samePigment) rank += textures ? 8 : 18;
        if (sameShape) rank += textures ? 24 : 4;
        if (textures && sameShape && reference.shapeDetail().equals(other.shapeDetail())) rank += 16;
        if (sharedFinish) rank += textures ? 5 : sameFinish ? 27 : 12;
        return rank;
    }

    static Profile profile(Identifier id, String declaredMaterial, String declaredFinish, String declaredShape,
                           String name, List<String> tags) {
        List<String> path = words(id.getPath());
        List<String> label = words(name);
        String materialInPath = materialIn(path);
        String materialInLabel = materialIn(label);
        String material = material(materialInPath, materialInLabel, normalized(declaredMaterial), tags);
        boolean namedMaterial = known(material) && (material.equals(materialInPath) || material.equals(materialInLabel));
        String shape = normalized(declaredShape);
        if (shape.isEmpty() || shape.equals("block")) {
            shape = firstKnown(normalized(CatalogueClassifier.taggedShape(tags)), form(path), form(label), shape);
        }
        Set<String> finish = new HashSet<>();
        path.stream().filter(FINISHES::contains).forEach(finish::add);
        if (finish.isEmpty()) label.stream().filter(FINISHES::contains).forEach(finish::add);
        String classifiedFinish = normalized(declaredFinish);
        if (finish.isEmpty() && known(classifiedFinish)) finish.add(classifiedFinish);

        List<String> source = words(id.getNamespace());
        Set<String> family = family(path, source);
        if (family.isEmpty()) family = family(label, source);
        return new Profile(material, namedMaterial, shape, shapeDetail(shape, path, label),
            firstKnown(taggedPigment(tags), pigment(path), pigment(label)), Set.copyOf(finish),
            firstKnown(kind(path), kind(label)), Set.copyOf(family));
    }

    private static String material(String fromPath, String fromLabel, String declared, List<String> tags) {
        for (String tag : tags) {
            if (tag.equals("minecraft:wool_carpets")) return "wool";
            if (tag.startsWith("material:") || tag.startsWith("c:") || tag.startsWith("forge:")) {
                String value = normalized(tag.substring(tag.indexOf(':') + 1));
                if (MATERIALS.contains(value) && !value.equals("brick")) return value;
            }
        }

        if (precise(fromPath)) return fromPath;
        if (declared.equals("brick") && known(fromPath)) return fromPath;
        if (precise(declared)) return declared;
        return firstKnown(fromLabel, fromPath, declared);
    }

    private static String materialIn(List<String> tokens) {
        String compound = phrase(tokens, COMPOUND_MATERIALS);
        if (known(compound)) return compound;
        String generic = "";
        for (String token : tokens) {
            if (MATERIALS.contains(token) && !token.equals("brick")) return token;
            if (BROAD_MATERIALS.contains(token)) generic = token;
        }
        return firstKnown(generic, tokens.contains("brick") ? "brick" : "");
    }

    private static String form(List<String> tokens) {
        if (tokens.contains("fence") && tokens.contains("gate")) return "fence gate";
        if (tokens.contains("slab")) return "slab";
        if (tokens.contains("stairs")) return "stairs";
        for (int index = tokens.size() - 1; index >= 0; index--) {
            String token = tokens.get(index);
            if (!token.equals("block") && FORMS.contains(token)) return token;
        }
        return "";
    }

    private static String shapeDetail(String shape, List<String> path, List<String> label) {
        if (!shape.equals("slab")) return "";
        List<String> tokens = path.contains("slab") ? path : label;
        String direction = tokens.contains("vertical") ? "vertical " : "";
        for (String part : List.of("corner", "eighth", "quarter", "half")) {
            if (tokens.contains(part)) return direction + part;
        }
        return direction;
    }

    private static String kind(List<String> tokens) {
        for (String token : tokens) {
            String kind = SPECIAL_KINDS.get(token);
            if (kind != null) return kind;
        }
        return "";
    }

    private static String pigment(List<String> tokens) {
        return firstKnown(phrase(tokens, COMPOUND_PIGMENTS), tokens.stream().filter(PIGMENTS::contains).findFirst().orElse(""));
    }

    private static String taggedPigment(List<String> tags) {
        String result = "";
        for (String tag : tags) {
            if (!tag.startsWith("c:dyed/") && !tag.startsWith("forge:dyed/")) continue;
            String value = normalized(tag.substring(tag.indexOf('/') + 1));
            if (!PIGMENTS.contains(value) && !COMPOUND_PIGMENTS.contains(value)) continue;
            if (known(result) && !result.equals(value)) return "";
            result = value;
        }
        return result;
    }

    private static String phrase(List<String> tokens, List<String> options) {
        String padded = " " + String.join(" ", tokens) + " ";
        for (String option : options) {
            if (padded.contains(" " + option + " ")) return option;
        }
        return "";
    }

    private static Set<String> family(List<String> tokens, List<String> source) {
        Set<String> family = new HashSet<>();
        for (String token : tokens) {
            if (token.length() >= 3 && Character.isLetter(token.codePointAt(0)) && !source.contains(token)
                && !FILLER.contains(token) && !BROAD_MATERIALS.contains(token) && !FINISHES.contains(token)
                && !FORMS.contains(token) && !PIGMENTS.contains(token) && !SPECIAL_KINDS.containsKey(token)) {
                family.add(token);
            }
        }
        return family;
    }

    private static List<String> words(String value) {
        String plain = MARKS.matcher(Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)).replaceAll("");
        return Arrays.stream(SEPARATORS.split(plain)).filter(word -> !word.isEmpty())
            .map(word -> SPELLINGS.getOrDefault(word, word)).toList();
    }

    private static String normalized(String value) {
        String normalized = String.join(" ", words(value));
        return FILLER.contains(normalized) ? "" : normalized;
    }

    private static boolean known(String value) {
        return !value.isEmpty();
    }

    private static boolean precise(String value) {
        return known(value) && !BROAD_MATERIALS.contains(value);
    }

    private static boolean sameKnown(String first, String second) {
        return known(first) && first.equals(second);
    }

    private static String firstKnown(String... values) {
        for (String value : values) {
            if (known(value)) return value;
        }
        return "";
    }

    private static boolean overlaps(Set<String> first, Set<String> second) {
        for (String token : first) {
            if (second.contains(token)) return true;
        }
        return false;
    }

    private static int sharedWords(Set<String> first, Set<String> second) {
        int count = 0;
        for (String token : first) {
            if (second.contains(token)) count++;
        }
        return count;
    }

    public record Profile(String material, boolean namedMaterial, String shape, String shapeDetail, String pigment,
                          Set<String> finish, String kind, Set<String> family) {
    }
}
