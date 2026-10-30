#!/usr/bin/env python3
"""Register the 37 depleted lode blocks named by spec/eom/worldgen.jsonl.

Each lode declares `depletes: true` and a `depleted_block`. When a miner works a lode dry the
block is replaced with its depleted form, which still yields `depleted_yield` at
`releach_efficiency`. Those blocks never existed, so a worked-out deposit had nowhere to go.
"""

from __future__ import annotations

import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = pathlib.Path(r"D:\Codex\WORKSPACES\Projects\mc-agent\spec\eom")
ASSETS = ROOT / "neoforge-26.2/src/main/resources/assets/earth_on_minecraft"
DATA = ROOT / "neoforge-26.2/src/main/resources/data/earth_on_minecraft"
JAVA = ROOT / "neoforge-26.2/src/main/java/com/xxsx/earthonminecraft"

# Depleted rock looks like the host rock, not like ore, so most share a few base palettes.
PALETTE = {
    "andesite": (130, 130, 132),
    "basalt": (72, 70, 74),
    "deepslate": (78, 78, 82),
    "diorite": (200, 200, 202),
    "granite": (154, 110, 92),
    "kimberlite": (96, 120, 100),
    "limestone": (206, 200, 184),
    "sandstone": (216, 198, 152),
    "tuff": (108, 112, 104),
    "laterite": (150, 84, 58),
    "carbonatite": (176, 170, 156),
    "quartz": (222, 214, 200),
    "default": (120, 118, 114),
}


def host_palette(lode: str) -> tuple[int, int, int]:
    for key, colour in PALETTE.items():
        if key != "default" and key in lode:
            return colour
    return PALETTE["default"]


def main() -> None:
    rows = [
        json.loads(line)
        for line in (SPEC / "worldgen.jsonl").read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]

    entries: list[tuple[str, str, int, int, int]] = []
    for row in rows:
        lode = row["id"].split(":", 1)[1].split("/", 1)[1]
        depleted = row.get("depleted_block", "")
        if not depleted:
            continue
        name = depleted.split(":", 1)[1]
        r, g, b = host_palette(lode)
        entries.append((name, lode, r, g, b))

    entries.sort()

    # 1. Java registration table.
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
        "/**",
        " * The 37 depleted lode blocks named by {@code spec/eom/worldgen.jsonl}.",
        " *",
        " * <p>A lode that a miner has worked dry is replaced by its depleted form. These blocks look",
        " * like spent host rock, are slightly softer than the ore they came from, and still yield",
        " * the spec's {@code depleted_yield} when processed, so a worked-out region keeps a small",
        " * residual value instead of becoming dead terrain.</p>",
        " */",
        "public final class SpecDepletedBlocks {",
        "    public record Entry(String id, String lode) {",
        "    }",
        "",
        "    private static final List<Entry> ENTRIES = List.of(",
    ]
    body = ",\n".join(f'            new Entry("{name}", "{lode}")' for name, lode, *_ in entries)
    lines.append(body)
    lines.extend([
        "    );",
        "",
        "    public static final Map<String, DeferredBlock<Block>> BY_ID = new LinkedHashMap<>();",
        "    private static boolean registered;",
        "",
        "    private SpecDepletedBlocks() {",
        "    }",
        "",
        "    public static void register() {",
        "        if (registered) {",
        "            return;",
        "        }",
        "        registered = true;",
        "        for (Entry entry : ENTRIES) {",
        "            DeferredBlock<Block> block = EarthOnMinecraft.BLOCKS.registerSimpleBlock(entry.id(),",
        "                    () -> BlockBehaviour.Properties.of()",
        "                            .mapColor(MapColor.STONE)",
        "                            .strength(2.0F, 4.0F)",
        "                            .requiresCorrectToolForDrops()",
        "                            .sound(SoundType.STONE));",
        "            var item = EarthOnMinecraft.ITEMS.registerItem(entry.id(),",
        "                    props -> new GuidedBlockItem(block.get(), props,",
        "                            \"tooltip.earth_on_minecraft.depleted_lode\"),",
        "                    props -> props);",
        "            BY_ID.put(entry.id(), block);",
        "            EarthOnMinecraft.addTabItem(item);",
        "        }",
        "    }",
        "",
        "    public static List<Entry> entries() {",
        "        return ENTRIES;",
        "    }",
        "}",
        "",
    ])
    (JAVA / "SpecDepletedBlocks.java").write_text("\n".join(lines), encoding="utf-8")

    # 2. Blockstates, models, item definitions, loot tables.
    (ASSETS / "blockstates").mkdir(parents=True, exist_ok=True)
    (ASSETS / "models/block").mkdir(parents=True, exist_ok=True)
    (ASSETS / "items").mkdir(parents=True, exist_ok=True)
    (DATA / "loot_table/blocks").mkdir(parents=True, exist_ok=True)
    (ASSETS / "textures/block").mkdir(parents=True, exist_ok=True)

    from PIL import Image, ImageDraw
    import hashlib

    for name, lode, r, g, b in entries:
        # Blockstate: a single cube_all variant.
        (ASSETS / "blockstates" / f"{name}.json").write_text(json.dumps({
            "variants": {"": {"model": f"earth_on_minecraft:block/{name}"}}
        }, indent=2) + "\n", encoding="utf-8")

        (ASSETS / "models/block" / f"{name}.json").write_text(json.dumps({
            "parent": "minecraft:block/cube_all",
            "textures": {"all": f"earth_on_minecraft:block/{name}"}
        }, indent=2) + "\n", encoding="utf-8")

        (ASSETS / "items" / f"{name}.json").write_text(json.dumps({
            "model": {"type": "minecraft:model", "model": f"earth_on_minecraft:block/{name}"}
        }, indent=2) + "\n", encoding="utf-8")

        # Loot table: drops one of itself, like the ore block it replaces.
        (DATA / "loot_table/blocks" / f"{name}.json").write_text(json.dumps({
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": [{"type": "minecraft:item",
                             "name": f"earth_on_minecraft:{name}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}]
            }]
        }, indent=2) + "\n", encoding="utf-8")

        # 32x32 texture: host-rock colour with a deterministic speckle.
        image = Image.new("RGBA", (32, 32), (r, g, b, 255))
        draw = ImageDraw.Draw(image)
        seed = int(hashlib.sha256(name.encode()).hexdigest(), 16)
        for index in range(90):
            value = (seed >> (index % 60)) & 0xFF
            x = (value * 7 + index * 11) % 32
            y = (value * 13 + index * 5) % 32
            delta = ((value >> 3) % 30) - 15
            shade = (max(0, min(255, r + delta)),
                     max(0, min(255, g + delta)),
                     max(0, min(255, b + delta)), 255)
            draw.point((x, y), fill=shade)
        image.save(ASSETS / "textures/block" / f"{name}.png", optimize=True)

    print(f"SPEC_DEPLETED_BLOCKS_OK blocks={len(entries)}")
    for name, lode, *_ in entries[:5]:
        print(f"  {name}  (from {lode})")


if __name__ == "__main__":
    main()
