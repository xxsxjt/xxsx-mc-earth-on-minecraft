package com.xxsx.earthonminecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runtime view of the versioned material catalog in
 * {@code data/earth_on_minecraft/materials}.
 *
 * <p>The catalog is a data record of real chemistry: elements, formulae, phase and
 * composition basis. It never claims a real material stores arcane field energy; that
 * belongs to the arcana mods. A malformed record is logged and skipped so one bad file
 * cannot take the server down.</p>
 */
public final class MaterialCatalog implements ResourceManagerReloadListener {
    private static final FileToIdConverter LISTER = FileToIdConverter.json("materials");
    private static volatile Map<Identifier, MaterialRecord> records = Map.of();

    public static void register(AddServerReloadListenersEvent event) {
        event.addListener(EarthOnMinecraft.id("materials"), new MaterialCatalog());
    }

    public static Optional<MaterialRecord> find(Identifier id) {
        return Optional.ofNullable(records.get(id));
    }

    public static Map<Identifier, MaterialRecord> records() {
        return records;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        Map<Identifier, MaterialRecord> parsed = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Resource> entry
                : LISTER.listMatchingResourcesFromNamespace(resourceManager, EarthOnMinecraft.MODID).entrySet()) {
            Identifier fileId = LISTER.fileToId(entry.getKey());
            if (fileId.getPath().endsWith("materials.schema")) {
                continue;
            }
            try (Reader reader = entry.getValue().openAsReader()) {
                MaterialRecord record = parse(JsonParser.parseReader(reader).getAsJsonObject());
                if (!record.id().equals(fileId.toString())) {
                    EarthOnMinecraft.LOGGER.error("Material record id {} does not match file {}", record.id(), fileId);
                    continue;
                }
                parsed.put(Identifier.parse(record.id()), record);
            } catch (Exception exception) {
                EarthOnMinecraft.LOGGER.error("Invalid material record {}", fileId, exception);
            }
        }
        records = Map.copyOf(parsed);
        EarthOnMinecraft.LOGGER.info("Loaded {} material catalog records", records.size());
    }

    private static MaterialRecord parse(JsonObject json) {
        String id = requireString(json, "id");
        String kind = requireString(json, "kind");
        String formula = requireString(json, "formula");
        String basis = requireString(json, "composition_basis");
        String phase = requireString(json, "phase");
        List<Constituent> constituents = new ArrayList<>();
        JsonArray array = json.has("constituents") && json.get("constituents").isJsonArray()
                ? json.getAsJsonArray("constituents")
                : new JsonArray();
        for (var element : array) {
            JsonObject entry = element.getAsJsonObject();
            constituents.add(new Constituent(
                    requireString(entry, "species"),
                    entry.has("fraction") ? entry.get("fraction").getAsDouble() : 0.0D,
                    entry.has("fraction_min") ? entry.get("fraction_min").getAsDouble() : 0.0D,
                    entry.has("fraction_max") ? entry.get("fraction_max").getAsDouble() : 1.0D));
        }
        return new MaterialRecord(id, kind, formula, basis, phase, List.copyOf(constituents));
    }

    private static String requireString(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing string field: " + key);
        }
        String value = json.get(key).getAsString().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Empty string field: " + key);
        }
        return value;
    }

    public record Constituent(String species, double fraction, double fractionMin, double fractionMax) {
    }

    public record MaterialRecord(String id, String kind, String formula, String compositionBasis,
                                 String phase, List<Constituent> constituents) {
        public boolean isMixture() {
            return "mixture".equals(kind) || "mass_fraction".equals(compositionBasis);
        }

        /**
         * Real-world chemical materials only ever expose chemical energy policy.
         * Arcane storage is owned by the arcana mods and must not leak in here.
         */
        public String energyPolicy() {
            return "chemical";
        }
    }
}
