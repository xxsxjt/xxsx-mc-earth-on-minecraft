package com.xxsx.earthonminecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldgenAndInteractionAuditTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Set<String> SHALLOW_DEPOSITS = Set.of(
            "bauxite_laterite_deposit",
            "cassiterite_placer_deposit"
    );

    @Test
    void undergroundWorldgenFeaturesStayDeepAndAirSuppressed() throws IOException {
        Path pfRoot = RESOURCES.resolve(Path.of("data", "earth_on_minecraft", "worldgen", "placed_feature"));
        Path cfRoot = RESOURCES.resolve(Path.of("data", "earth_on_minecraft", "worldgen", "configured_feature"));
        try (Stream<Path> paths = Files.list(pfRoot)) {
            for (Path pfFile : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = pfFile.getFileName().toString().replace(".json", "");
                if ("lode_surface_signal".equals(name)) {
                    continue;
                }
                JsonObject pf = JsonParser.parseString(Files.readString(pfFile, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonArray placements = pf.getAsJsonArray("placement");
                int chance = -1;
                Integer maxY = null;
                for (JsonElement el : placements) {
                    JsonObject obj = el.getAsJsonObject();
                    String type = obj.get("type").getAsString();
                    if ("minecraft:rarity_filter".equals(type)) {
                        chance = obj.get("chance").getAsInt();
                    } else if ("minecraft:height_range".equals(type)) {
                        maxY = obj.getAsJsonObject("height")
                                .getAsJsonObject("max_inclusive")
                                .get("absolute").getAsInt();
                    }
                }
                assertTrue(chance >= 12, "rarity_filter chance must be >= 12 for " + name + ", got " + chance);
                assertTrue(maxY != null, "height_range max_inclusive missing for " + name);
                if (!SHALLOW_DEPOSITS.contains(name)) {
                    assertTrue(maxY <= 36, "Underground feature " + name + " max_inclusive must be <= 36, got " + maxY);
                }

                Path cfFile = cfRoot.resolve(name + ".json");
                assertTrue(Files.isRegularFile(cfFile), "Configured feature missing for " + name);
                JsonObject cf = JsonParser.parseString(Files.readString(cfFile, StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals("minecraft:ore", cf.get("type").getAsString(),
                        "Mineral/strata feature must use minecraft:ore with air discard: " + name);
                double discard = cf.getAsJsonObject("config").get("discard_chance_on_air_exposure").getAsDouble();
                assertTrue(discard >= 0.75 && discard <= 0.85,
                        "discard_chance_on_air_exposure must be in [0.75, 0.85] for " + name + ", got " + discard);
            }
        }
    }

    @Test
    void lodeSurfaceSignalUsesRestrainedThresholds() {
        assertEquals(18, LodeSurfaceSignalFeature.MIN_BLOCKS, "MIN_BLOCKS must be 18");
        assertEquals(1, LodeSurfaceSignalFeature.MAX_SIGNALS, "MAX_SIGNALS must be 1");
        assertTrue(LodeSurfaceSignalFeature.SIGNAL_CHANCE >= 0.35F && LodeSurfaceSignalFeature.SIGNAL_CHANCE <= 0.45F,
                "SIGNAL_CHANCE must be in 35%..45%");
        assertEquals(2, LodeSurfaceSignalFeature.MIN_SURFACE_BLOCKS);
        assertEquals(3, LodeSurfaceSignalFeature.MAX_SURFACE_BLOCKS);

        List<LodeSectionScan.Hit> hits = new ArrayList<>();
        for (int i = 0; i < 17; i++) {
            hits.add(new LodeSectionScan.Hit(new BlockPos(i, -20, 4), "chalcopyrite_vein"));
        }
        assertTrue(LodeSectionScan.planSignals(hits).isEmpty(),
                "17 blocks (< MIN_BLOCKS=18) must not plan a surface signal");

        for (int i = 0; i < 18; i++) {
            hits.add(new LodeSectionScan.Hit(new BlockPos(3, -30 + i, 5), "magnetite_lens"));
        }
        for (int i = 0; i < 22; i++) {
            hits.add(new LodeSectionScan.Hit(new BlockPos(6, -40 + i, 8), "galena_lens"));
        }
        var planned = LodeSectionScan.planSignals(hits);
        assertEquals(1, planned.size(), "At most MAX_SIGNALS=1 signal per chunk");
        assertEquals("galena_lens", planned.getFirst().lodeId(), "Largest qualifying lode wins");
    }

    @Test
    void allOreBlockLootTablesIncludeTailingsDustAndMiningTiersAreDisjoint() throws IOException {
        Path lootRoot = RESOURCES.resolve(Path.of("data", "earth_on_minecraft", "loot_table", "blocks"));
        try (Stream<Path> paths = Files.list(lootRoot)) {
            List<Path> oreFiles = paths.filter(p -> p.getFileName().toString().endsWith("_ore.json")).sorted().toList();
            assertFalse(oreFiles.isEmpty(), "Must find *_ore.json loot tables");
            for (Path oreFile : oreFiles) {
                JsonObject json = JsonParser.parseString(Files.readString(oreFile, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonArray pools = json.getAsJsonArray("pools");
                assertTrue(pools.size() >= 2, oreFile.getFileName() + " must have primary + gangue pools");
                boolean hasTailings = false;
                for (JsonElement poolEl : pools) {
                    for (JsonElement entryEl : poolEl.getAsJsonObject().getAsJsonArray("entries")) {
                        JsonObject entry = entryEl.getAsJsonObject();
                        if (entry.has("name") && "earth_on_minecraft:tailings_dust".equals(entry.get("name").getAsString())) {
                            hasTailings = true;
                        }
                    }
                }
                assertTrue(hasTailings, oreFile.getFileName() + " must drop earth_on_minecraft:tailings_dust");
            }
        }

        Path tagsRoot = RESOURCES.resolve(Path.of("data", "minecraft", "tags", "block"));
        JsonArray stoneValues = JsonParser.parseString(
                Files.readString(tagsRoot.resolve("needs_stone_tool.json"), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("values");
        JsonArray ironValues = JsonParser.parseString(
                Files.readString(tagsRoot.resolve("needs_iron_tool.json"), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("values");
        Set<String> stoneSet = new HashSet<>();
        for (JsonElement el : stoneValues) {
            stoneSet.add(el.getAsString());
        }
        for (JsonElement el : ironValues) {
            String blockId = el.getAsString();
            assertFalse(stoneSet.contains(blockId),
                    "Block must not be duplicated in both needs_stone_tool and needs_iron_tool: " + blockId);
        }
    }

    @Test
    void manualButtonLangAndAE2CrankTooltipAreConsistent() throws IOException {
        Path langRoot = RESOURCES.resolve(Path.of("assets", "earth_on_minecraft", "lang"));
        JsonObject zh = JsonParser.parseString(
                Files.readString(langRoot.resolve("zh_cn.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject en = JsonParser.parseString(
                Files.readString(langRoot.resolve("en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();

        assertEquals("手摇", zh.get("screen.earth_on_minecraft.machine.manual.button").getAsString());
        assertEquals("Crank", en.get("screen.earth_on_minecraft.machine.manual.button").getAsString());
        assertTrue(zh.get("screen.earth_on_minecraft.machine.manual.tooltip").getAsString().contains("AE2"));
        assertTrue(en.get("screen.earth_on_minecraft.machine.manual.tooltip").getAsString().contains("AE2"));
    }
}
