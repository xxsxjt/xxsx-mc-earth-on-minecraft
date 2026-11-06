package com.xxsx.earthonminecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xxsx.earthlore.core.Gate;
import com.xxsx.earthlore.core.GraphValidator;
import com.xxsx.earthlore.core.NodeDef;
import com.xxsx.earthlore.core.Prereq;
import com.xxsx.earthlore.core.Reachability;
import com.xxsx.earthonminecraft.component.Grade;
import com.xxsx.earthonminecraft.component.OpticalGlassComponent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthLoreIntegrationTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources");

    @Test
    void all107NodesValidateAndAreReachable() throws IOException {
        Path nodeRoot = RESOURCES.resolve(Path.of("data", "earth_on_minecraft", "earth_lore", "knowledge_node"));
        assertTrue(Files.isDirectory(nodeRoot), "knowledge_node directory must exist");

        List<NodeDef> defs = new ArrayList<>();
        Map<String, Integer> tiers = new HashMap<>();
        Set<String> disciplines = Set.of(
                "earth_lore:geo",
                "earth_lore:che",
                "earth_lore:mat",
                "earth_lore:eng",
                "earth_lore:agr",
                "earth_lore:med"
        );

        try (Stream<Path> paths = Files.walk(nodeRoot)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                Path rel = nodeRoot.relativize(file);
                String pathId = rel.toString().replace('\\', '/').replaceAll("\\.json$", "");
                String fullId = "earth_on_minecraft:" + pathId;
                JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();

                String discipline = json.get("discipline").getAsString();
                int tier = json.get("tier").getAsInt();
                Gate gate = Gate.parse(json.get("gate").getAsString()).orElseThrow();
                tiers.put(fullId, tier);

                List<Prereq> prereqs = new ArrayList<>();
                for (JsonElement reqEl : json.getAsJsonArray("requires")) {
                    JsonObject reqObj = reqEl.getAsJsonObject();
                    if (reqObj.has("all")) {
                        prereqs.add(new Prereq.All(reqObj.get("all").getAsString()));
                    } else {
                        List<String> members = new ArrayList<>();
                        for (JsonElement m : reqObj.getAsJsonArray("any")) {
                            members.add(m.getAsString());
                        }
                        prereqs.add(new Prereq.Any(members));
                    }
                }

                List<String> unlockKeys = new ArrayList<>();
                for (JsonElement unEl : json.getAsJsonArray("unlocks")) {
                    JsonObject unObj = unEl.getAsJsonObject();
                    assertEquals(1, unObj.size(), "Each unlock ref must have a single key: " + fullId);
                    Map.Entry<String, JsonElement> entry = unObj.entrySet().iterator().next();
                    String kind = entry.getKey();
                    String val = entry.getValue().getAsString();
                    String key = "capstone".equals(kind)
                            ? "capstone:" + val + "@earth_on_minecraft"
                            : kind + ":" + val;
                    unlockKeys.add(key);
                }

                defs.add(new NodeDef(fullId, discipline, tier, gate, prereqs, unlockKeys, false));
            }
        }

        assertEquals(107, defs.size(), "Earth on Minecraft must ship exactly 107 knowledge nodes");

        GraphValidator.Result validation = GraphValidator.validate(
                defs, disciplines, (owner, other) -> owner.equals(other) || "earth_lore".equals(other));
        assertEquals(Map.of(), validation.rejected(), "Zero nodes may be rejected by GraphValidator");
        assertEquals(107, validation.accepted().size(), "All 107 nodes must be accepted");

        Reachability.Report reach = Reachability.compute(validation.accepted());
        assertEquals(Set.of(), reach.unreachable(), "Zero nodes may be unreachable in standalone Earth + Lore");
        assertEquals(107, reach.learnable().size(), "All 107 nodes must be learnable");

        // Validate teaching data map against loaded nodes and tier 1..2 rule
        Path teachingPath = RESOURCES.resolve(
                Path.of("data", "earth_lore", "data_maps", "villager_profession", "teaching.json"));
        assertTrue(Files.isRegularFile(teachingPath), "teaching.json data map must exist");
        JsonObject teachingJson = JsonParser.parseString(
                Files.readString(teachingPath, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject values = teachingJson.getAsJsonObject("values");
        assertFalse(values.isEmpty(), "teaching.json must map vanilla villager professions");
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
            assertTrue(entry.getKey().startsWith("minecraft:"),
                    "VillagerProfession data map keys must be vanilla professions: " + entry.getKey());
            JsonArray arr = entry.getValue().getAsJsonArray();
            for (JsonElement item : arr) {
                String nodeId = item.getAsJsonObject().get("node").getAsString();
                Integer tier = tiers.get(nodeId);
                assertTrue(tier != null && tier >= 1 && tier <= 2,
                        "Taught node must exist and be tier 1..2: " + nodeId);
            }
        }
    }

    @Test
    void opticalGlassAndGradeBridgeBehaveDeterministically() {
        OpticalGlassComponent lowBubble = new OpticalGlassComponent(1, 1.52, 1);
        assertFalse(lowBubble.hasHighBubbles());
        assertEquals(Grade.FINE, lowBubble.capGrade(Grade.FINE));

        OpticalGlassComponent highBubble = new OpticalGlassComponent(1, 1.51, 6);
        assertTrue(highBubble.hasHighBubbles());
        assertEquals(Grade.COMMON, highBubble.capGrade(Grade.FINE));

        for (Grade g : Grade.values()) {
            assertEquals(g, LoreIntegration.fromLoreGrade(LoreIntegration.toLoreGrade(g)));
        }
    }
}
