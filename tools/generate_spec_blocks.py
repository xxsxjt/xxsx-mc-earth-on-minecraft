#!/usr/bin/env python3
"""Generate the spec block layer from spec/eom/blocks.jsonl and worldgen.jsonl."""

from __future__ import annotations

import argparse
import colorsys
import hashlib
import json
import pathlib
import re

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "neoforge-26.2/src/main/java/com/xxsx/earthonminecraft"
RES = ROOT / "neoforge-26.2/src/main/resources"
ASSETS = RES / "assets/earth_on_minecraft"
BLOCK_TEXTURES = ASSETS / "textures/block"
BLOCK_MODELS = ASSETS / "models/block"
BLOCKSTATES = ASSETS / "blockstates"
ITEM_DEFS = ASSETS / "items"
LANG = ASSETS / "lang"
LOOT = RES / "data/earth_on_minecraft/loot_table/blocks"
BLOCK_TAGS = RES / "data/minecraft/tags/block"
DATA_TAGS = RES / "data/earth_on_minecraft/tags/block"
WORLDGEN = RES / "data/earth_on_minecraft/worldgen"
BIOME_MODIFIER = RES / "data/earth_on_minecraft/neoforge/biome_modifier/add_overworld_deposits.json"
SPEC_BLOCKS_JAVA = JAVA_ROOT / "SpecBlocks.java"
ENTRYPOINT = JAVA_ROOT / "EarthOnMinecraft.java"
MANIFEST = ROOT / "docs/quality/spec-block-layer.json"
DEFAULT_SPEC = pathlib.Path(r"D:\Codex\WORKSPACES\Projects\mc-agent\spec\eom")

FAMILY_PALETTE = {
    "ore_block": (105, 100, 95),
    "multiblock": (125, 105, 90),
    "building": (125, 110, 85),
    "device": (120, 125, 130),
}


def load(name: str, spec: pathlib.Path):
    return [json.loads(line) for line in (spec / name).read_text(encoding="utf-8").splitlines() if line.strip()]


def existing_ids() -> set[str]:
    text = ENTRYPOINT.read_text(encoding="utf-8")
    ids = set(re.findall(r'\b(?:machineBlock|oreBlock|supportBlock|controlPanelBlock|structureProjectionBlock|machineInterfaceBlock|energyGeneratorBlock|powerCableBlock|batteryBoxBlock|conveyorBeltBlock|settlementBoardBlock)\(\s*"([a-z0-9_]+)"', text))
    ids |= set(re.findall(r'BLOCKS\.registerBlock\(\s*"([a-z0-9_]+)"', text))
    return ids


def color(seed: str, base: tuple[int, int, int], spread: int = 18) -> tuple[int, int, int]:
    digest = int(hashlib.sha256(seed.encode("utf-8")).hexdigest(), 16)
    delta = (digest % (spread * 2 + 1)) - spread
    return tuple(max(0, min(255, channel + delta)) for channel in base)


def draw_block(slug: str, family: str) -> Image.Image:
    image = Image.new("RGBA", (32, 32), (0, 0, 0, 255))
    draw = ImageDraw.Draw(image)
    base = color(slug, FAMILY_PALETTE.get(family, (120, 118, 112)))
    for y in range(32):
        for x in range(32):
            value = int(hashlib.sha256(f"{slug}:{x}:{y}".encode()).hexdigest(), 16) % 25 - 12
            draw.point((x, y), fill=tuple(max(0, min(255, c + value)) for c in base))

    if family == "ore_block":
        mineral = color(slug, tuple(int(c * 0.8) for c in base), 60)
        for index in range(7):
            digest = int(hashlib.sha256(f"{slug}:ore:{index}".encode()).hexdigest(), 16)
            x = 4 + digest % 24
            y = 4 + (digest >> 8) % 24
            radius = 1 + (digest >> 16) % 2
            draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=mineral)
            highlight = tuple(min(255, channel + 40) for channel in mineral)
            draw.point((x, y), fill=highlight)
    elif family == "multiblock":
        mortar = tuple(int(c * 0.72) for c in base)
        for row in range(4):
            y0 = row * 8
            draw.line((0, y0, 31, y0), fill=mortar)
            offset = 0 if row % 2 == 0 else 8
            for col in range(-1, 5):
                x = col * 16 + offset
                draw.line((x, y0, x, y0 + 7), fill=mortar)
    elif family == "device":
        panel = tuple(min(255, int(c * 1.15)) for c in base)
        draw.rectangle((1, 1, 30, 30), outline=tuple(int(c * 0.55) for c in base), width=2)
        draw.rectangle((4, 4, 27, 27), outline=panel, width=1)
        for x, y in ((4, 4), (26, 4), (4, 26), (26, 26)):
            draw.point((x, y), fill=panel)
            draw.point((x + 1, y), fill=panel)
        draw.rectangle((12, 12, 19, 19), fill=panel, outline=tuple(int(c * 0.5) for c in base))
    else:
        for index in range(4):
            digest = int(hashlib.sha256(f"{slug}:grain:{index}".encode()).hexdigest(), 16)
            x = digest % 28
            y = (digest >> 8) % 28
            draw.line((x, y, min(31, x + 2), min(31, y + 1)), fill=tuple(int(c * 0.85) for c in base))

    return image.resize((128, 128), Image.Resampling.NEAREST)


def java_string(value: str) -> str:
    return value.replace("\\", "\\\\").replace('"', '\\"')


def generate_java(entries: list[dict]) -> str:
    lines = [
        "package com.xxsx.earthonminecraft;",
        "",
        "import net.minecraft.world.level.block.Block;",
        "import net.minecraft.world.level.block.SoundType;",
        "import net.minecraft.world.level.block.state.BlockBehaviour;",
        "import net.minecraft.world.level.material.MapColor;",
        "import net.neoforged.neoforge.registries.DeferredBlock;",
        "",
        "import java.util.LinkedHashMap;",
        "import java.util.List;",
        "import java.util.Map;",
        "",
        "public final class SpecBlocks {",
        "    public record BlockEntry(String id, String family, float hardness) {",
        "    }",
        "",
        "    private static final List<BlockEntry> ENTRIES = List.of(",
    ]
    entry_lines = [
        '            new BlockEntry("%s", "%s", %.1fF)' % (
            java_string(entry["id"]), java_string(entry["family"]), entry["hardness"])
        for entry in entries
    ]
    lines.append(",\n".join(entry_lines))
    lines.extend([
        "    );",
        "",
        "    public static final Map<String, DeferredBlock<Block>> BY_ID = new LinkedHashMap<>();",
        "    private static boolean registered;",
        "",
        "    private SpecBlocks() {",
        "    }",
        "",
        "    public static void register() {",
        "        if (registered) {",
        "            return;",
        "        }",
        "        registered = true;",
        "        for (BlockEntry entry : ENTRIES) {",
        "            DeferredBlock<Block> block = EarthOnMinecraft.BLOCKS.registerSimpleBlock(entry.id(),",
        "                    () -> BlockBehaviour.Properties.of()",
        "                            .mapColor(MapColor.STONE)",
        "                            .strength(entry.hardness(), entry.hardness() * 2.0F)",
        "                            .requiresCorrectToolForDrops()",
        "                            .sound(entry.family().equals(\"device\") ? SoundType.METAL : SoundType.STONE));",
        "            var item = EarthOnMinecraft.ITEMS.registerItem(entry.id(),",
        "                    props -> new GuidedBlockItem(block.get(), props, entry.family().equals(\"ore_block\")",
        "                            ? \"tooltip.earth_on_minecraft.natural_source\"",
        "                            : \"tooltip.earth_on_minecraft.block.header\"),",
        "                    props -> props);",
        "            BY_ID.put(entry.id(), block);",
        "            EarthOnMinecraft.addTabItem(item);",
        "        }",
        "    }",
        "}",
        "",
    ])
    return "\n".join(lines)


def merge_lang(slug: str, zh: str, en: str) -> None:
    for locale, value in (("zh_cn", zh), ("en_us", en)):
        path = LANG / f"{locale}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        data[f"block.earth_on_minecraft.{slug}"] = value
        data[f"item.earth_on_minecraft.{slug}"] = value
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def merge_tag(path: pathlib.Path, slug: str) -> None:
    data = {"replace": False, "values": []}
    if path.exists():
        data = json.loads(path.read_text(encoding="utf-8"))
    entry = f"earth_on_minecraft:{slug}"
    values = data.setdefault("values", [])
    if entry not in values:
        values.append(entry)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_loot(record: dict) -> None:
    slug = record["id"].split(":", 1)[1]
    drops = record.get("drops") or [{"item": record["id"], "min": 1, "max": 1}]
    children = []
    for drop in drops:
        name = drop.get("item")
        if not name:
            continue
        minimum = int(drop.get("min", 1))
        maximum = int(drop.get("max", minimum))
        entry = {
            "type": "minecraft:item",
            "name": name,
            "functions": [{
                "function": "minecraft:set_count",
                "count": {"type": "minecraft:uniform", "min": float(minimum), "max": float(maximum)},
            }, {"function": "minecraft:explosion_decay"}],
        }
        children.append(entry)
    silk = {"type": "minecraft:item", "name": f"earth_on_minecraft:{slug}",
            "conditions": [{"condition": "minecraft:match_tool", "predicate": {
                "predicates": {"minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]}
    pool_entry = {"type": "minecraft:alternatives", "children": [silk] + children}
    data = {
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "entries": [pool_entry]}],
        "random_sequence": f"earth_on_minecraft:blocks/{slug}",
    }
    (LOOT / f"{slug}.json").write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def worldgen_records(spec: pathlib.Path) -> dict[str, dict]:
    records = {}
    for record in load("worldgen.jsonl", spec):
        local = record["id"].split(":", 1)[1]
        records[local.replace("lode/", "")] = record
    return records


def write_worldgen(block: dict, record: dict) -> None:
    slug = block["id"].split(":", 1)[1]
    feature = record.get("feature_json") or {"type": "minecraft:ore", "config": {
        "size": max(3, int(record.get("feature_size_blocks", 8))),
        "discard_chance_on_air_exposure": 0.0,
        "targets": [{"state": {"Name": block["id"]},
                     "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}}],
    }}
    configured = WORLDGEN / "configured_feature" / f"{slug}.json"
    configured.parent.mkdir(parents=True, exist_ok=True)
    configured.write_text(json.dumps(feature, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    height = record.get("height_range") or {}
    minimum = int(height.get("min", -64))
    maximum = int(height.get("max", 80))
    count = int(record.get("lodes_per_region", 8))
    placed = {
        "feature": f"earth_on_minecraft:{slug}",
        "placement": [
            {"type": "minecraft:rarity_filter", "chance": max(1, count)},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {
                "type": "minecraft:trapezoid",
                "min_inclusive": {"absolute": minimum},
                "max_inclusive": {"absolute": maximum},
            }},
            {"type": "minecraft:biome"},
        ],
    }
    path = WORLDGEN / "placed_feature" / f"{slug}.json"
    path.write_text(json.dumps(placed, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    if BIOME_MODIFIER.exists():
        data = json.loads(BIOME_MODIFIER.read_text(encoding="utf-8"))
        features = data.setdefault("features", [])
        entry = f"earth_on_minecraft:{slug}"
        if entry not in features:
            features.append(entry)
            BIOME_MODIFIER.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--spec", default=str(DEFAULT_SPEC))
    parser.add_argument("--skip-art", action="store_true")
    args = parser.parse_args()
    spec = pathlib.Path(args.spec)
    records = load("blocks.jsonl", spec)
    wg = worldgen_records(spec)
    taken = existing_ids()
    entries = []
    skipped = []
    generated_art = 0
    worldgen_written = 0

    for record in records:
        slug = record["id"].split(":", 1)[1]
        if slug in taken:
            skipped.append({"id": slug, "reason": "already_registered"})
            continue
        family = record.get("family") or "building"
        hardness = float(record.get("hardness", 3.0))
        entries.append({"id": slug, "family": family, "hardness": hardness})
        if not args.skip_art:
            texture = BLOCK_TEXTURES / f"{slug}.png"
            if not texture.exists():
                draw_block(slug, family).save(texture)
                generated_art += 1
            (BLOCKSTATES / f"{slug}.json").write_text(json.dumps({
                "variants": {"": {"model": f"earth_on_minecraft:block/{slug}"}}
            }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            (BLOCK_MODELS / f"{slug}.json").write_text(json.dumps({
                "parent": "minecraft:block/cube_all",
                "textures": {"all": f"earth_on_minecraft:block/{slug}"}
            }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            (ITEM_DEFS / f"{slug}.json").write_text(json.dumps({
                "model": {"type": "minecraft:model", "model": f"earth_on_minecraft:block/{slug}"}
            }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        merge_lang(slug, record.get("zh") or slug, record.get("en") or slug)
        write_loot(record)
        for tag in record.get("tags") or []:
            if tag.startswith("earth_on_minecraft:"):
                tag_path = tag.split(":", 1)[1]
                if tag_path.startswith("block/"):
                    tag_path = tag_path[len("block/"):]
                merge_tag(DATA_TAGS / (tag_path + ".json"), slug)
        tool = str(record.get("tool", "minecraft:pickaxe")).removeprefix("minecraft:")
        if tool in ("pickaxe", "axe", "shovel"):
            merge_tag(BLOCK_TAGS / "mineable" / f"{tool}.json", slug)
        if tool == "iron_pickaxe":
            merge_tag(BLOCK_TAGS / "needs_iron_tool.json", slug)
        elif tool in ("stone_pickaxe", "pickaxe"):
            merge_tag(BLOCK_TAGS / "needs_stone_tool.json", slug)
        refs = [ref for ref in (record.get("worldgen") or []) if ref.startswith("earth_on_minecraft:lode/")]
        for ref in refs:
            local = ref.split("lode/", 1)[1]
            if local in wg:
                write_worldgen(record, wg[local])
                worldgen_written += 1
                break

    SPEC_BLOCKS_JAVA.write_text(generate_java(entries), encoding="utf-8")
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST.write_text(json.dumps({
        "schema_version": 1,
        "source": str(spec),
        "generated_blocks": len(entries),
        "generated_textures": generated_art,
        "worldgen_features": worldgen_written,
        "skipped": skipped,
        "template_art": True,
        "note": "Block textures are deterministic template art pending the approved art pipeline."
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"SPEC_BLOCKS generated={len(entries)} textures={generated_art} worldgen={worldgen_written} skipped={len(skipped)}")


if __name__ == "__main__":
    main()
