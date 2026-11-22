package com.oliver.lithotheca.catalogue;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public final class BlockCatalogue {
    private static final Logger LOGGER = LoggerFactory.getLogger("lithotheca");
    private static final Comparator<Seed> ID_ORDER = Comparator.comparing(Seed::id);

    private static volatile Catalogue catalogue = new Catalogue(List.of(), Map.of());
    private static volatile String status = "Preparing block catalogue...";
    private static Map<String, List<RegisteredBlock>> registered = Map.of();
    private static Map<String, Map<String, CatalogueClassifier.Facts>> factsBySource = Map.of();
    private static boolean initialized;
    private static boolean started;
    private static boolean building;
    private static boolean refreshPending;
    private static boolean useSyncedTags;

    private BlockCatalogue() {
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            registered = collectRegisteredBlocks();
            started = true;
            refresh(client);
        });
        CommonLifecycleEvents.TAGS_LOADED.register((registries, clientSide) -> {
            if (!clientSide) return;
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                useSyncedTags = true;
                refresh(client);
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            useSyncedTags = false;
            refresh(client);
        });
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new NameReloadListener());
    }

    public static List<CatalogueEntry> entries() {
        return catalogue.entries();
    }

    public static CatalogueEntry find(Identifier id) {
        return id == null ? null : catalogue.byId().get(id);
    }

    public static String status() {
        return status;
    }

    private static Map<String, List<RegisteredBlock>> collectRegisteredBlocks() {
        Map<String, List<RegisteredBlock>> sources = new TreeMap<>();
        for (Item item : Registries.ITEM) {
            if (!(item instanceof BlockItem blockItem) || item == Items.AIR) continue;
            Identifier id = Registries.ITEM.getId(item);
            Block block = blockItem.getBlock();
            sources.computeIfAbsent(id.getNamespace(), namespace -> new ArrayList<>())
                    .add(new RegisteredBlock(id, item, block, CatalogueClassifier.shapeHint(block)));
        }
        return sources;
    }

    private static void refresh(MinecraftClient client) {
        if (!started) return;
        if (building) {
            refreshPending = true;
            return;
        }
        building = true;
        refreshPending = false;

        Map<String, List<Seed>> sources = new LinkedHashMap<>();
        registered.forEach((namespace, blocks) -> sources.put(namespace, snapshot(blocks)));
        FamilyFinishes.Snapshot finishes = FamilyFinishes.capture(sources.values().stream()
            .flatMap(List::stream).map(Seed::id).toList());
        Map<String, Map<String, CatalogueClassifier.Facts>> previousFacts = factsBySource;
        CompletableFuture.supplyAsync(() -> build(sources, previousFacts, finishes)).whenComplete((result, error) ->
                client.execute(() -> {
                    building = false;
                    if (error == null && !refreshPending) {
                        catalogue = result.catalogue();
                        status = "";
                    } else if (error != null) {
                        LOGGER.error("Could not build the block catalogue", error);
                        status = "Block catalogue unavailable";
                    }
                    if (error == null) factsBySource = result.facts();
                    if (refreshPending) refresh(client);
                })
        );
    }

    private static List<Seed> snapshot(List<RegisteredBlock> blocks) {
        List<Seed> seeds = new ArrayList<>(blocks.size());
        for (RegisteredBlock registeredBlock : blocks) {
            Item item = registeredBlock.item();
            Block block = registeredBlock.block();
            List<String> tags = useSyncedTags
                    ? Stream.concat(block.getRegistryEntry().streamTags(), item.getRegistryEntry().streamTags())
                            .map(TagKey::id).map(Identifier::toString).distinct().sorted().toList()
                    : List.of();
            seeds.add(new Seed(registeredBlock.id(), registeredBlock.shapeHint(), item.getName().getString(), tags));
        }
        seeds.sort(ID_ORDER);
        return seeds;
    }

    private static BuildResult build(Map<String, List<Seed>> sources,
                                     Map<String, Map<String, CatalogueClassifier.Facts>> previousFacts,
                                     FamilyFinishes.Snapshot finishes) {
        Map<Identifier, CatalogueEntry> byId = new LinkedHashMap<>();
        Map<String, Map<String, CatalogueClassifier.Facts>> sourceFacts = new LinkedHashMap<>();

        sources.forEach((namespace, seeds) -> {
            Map<String, CatalogueClassifier.Facts> facts = previousFacts.get(namespace);
            if (facts == null) facts = CatalogueCache.facts(namespace, seeds);
            sourceFacts.put(namespace, facts);
            for (Seed seed : seeds) {
                CatalogueEntry entry = CatalogueClassifier.entry(seed.id(), facts.get(seed.id().getPath()),
                        seed.tags(), seed.name(), finishes.resolve(seed.id()));
                byId.put(seed.id(), entry);
            }
        });
        FamilyDuplicates.mark(byId);
        return new BuildResult(new Catalogue(List.copyOf(byId.values()), Map.copyOf(byId)), sourceFacts);
    }

    record Seed(Identifier id, String shapeHint, String name, List<String> tags) {
    }

    private record RegisteredBlock(Identifier id, Item item, Block block, String shapeHint) {
    }

    private record Catalogue(List<CatalogueEntry> entries, Map<Identifier, CatalogueEntry> byId) {
    }

    private record BuildResult(Catalogue catalogue, Map<String, Map<String, CatalogueClassifier.Facts>> facts) {
    }

    private static final class NameReloadListener implements SimpleSynchronousResourceReloadListener {
        @Override
        public Identifier getFabricId() {
            return new Identifier("lithotheca", "catalogue_names");
        }

        @Override
        public List<Identifier> getFabricDependencies() {
            return List.of(ResourceReloadListenerKeys.LANGUAGES);
        }

        @Override
        public void reload(ResourceManager manager) {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> refresh(client));
        }
    }
}
