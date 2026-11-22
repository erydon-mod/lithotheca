package com.oliver.lithotheca.catalogue;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Captures the renderer's active surface choices before the asynchronous catalogue build. */
final class FamilyFinishes {
    private static final Logger LOGGER = LoggerFactory.getLogger("lithotheca");
    private static Api api;
    private static boolean checked;

    private FamilyFinishes() {
    }

    static Snapshot capture(Collection<Identifier> ids) {
        if (!checked) {
            checked = true;
            if (FabricLoader.getInstance().isModLoaded("erydon")) {
                try {
                    ClassLoader loader = FamilyFinishes.class.getClassLoader();
                    api = new Api(loader.loadClass("com.oliver.erydon.client.ErydonHighPolish"),
                        loader.loadClass("com.oliver.erydon.client.PolishedStoneMaterials"));
                } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                    LOGGER.warn("Could not identify the active ERYDON surface finish API", failure);
                }
            }
        }
        if (api == null) return Snapshot.EMPTY;
        try {
            return api.capture(ids);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            LOGGER.warn("Could not capture the active ERYDON surface finishes", failure);
            api = null;
            return Snapshot.EMPTY;
        }
    }

    record Snapshot(Map<Identifier, String> finishes) {
        private static final Snapshot EMPTY = new Snapshot(Map.of());

        Snapshot {
            finishes = Map.copyOf(finishes);
        }

        String resolve(Identifier id) {
            return finishes.getOrDefault(id, "");
        }
    }

    static final class Api {
        private final Method activeSettings;
        private final Method includes;
        private final Method level;

        Api(Class<?> preferences, Class<?> materials) throws ReflectiveOperationException {
            activeSettings = preferences.getMethod("activeSettings");
            includes = materials.getMethod("includes", String.class, String.class);
            level = materials.getMethod("level", activeSettings.getReturnType(), String.class);
        }

        Snapshot capture(Collection<Identifier> ids) throws ReflectiveOperationException {
            Object settings = activeSettings.invoke(null);
            Map<Identifier, String> finishes = new LinkedHashMap<>();
            for (Identifier id : ids) {
                if (!CatalogueClassifier.hasStandardTexture(id)
                    || !Boolean.TRUE.equals(includes.invoke(null, id.getNamespace(), id.getPath()))) continue;
                Object value = level.invoke(null, settings, id.getPath());
                if (!(value instanceof Enum<?> finish)) continue;
                String surface = switch (finish.name()) {
                    case "HONED" -> "honed";
                    case "POLISHED" -> "polished";
                    case "MIRROR" -> "ganosis";
                    default -> "";
                };
                if (!surface.isEmpty()) finishes.put(id, surface);
            }
            return new Snapshot(finishes);
        }
    }
}
