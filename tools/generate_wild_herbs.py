#!/usr/bin/env python3
"""Inject wild herb gathering into the grass and fern loot tables.

The spec marks 136 herbs as `gather:wild_herb`, but ships no plant block, no loot table and no
worldgen. Propagation (tools/generate_herb_routes.py) makes herbs renewable once you own one, but
nothing in the world currently gives you the first one.

This adds a Neoforge global loot modifier so short grass and ferns can drop a herb. The pick is
driven by the herb's own catalog section so a swamp plant does not turn up in a desert. Every herb
gets a small, weighted chance; nothing is common enough to trivialise the chain.
"""

from __future__ import annotations

import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = pathlib.Path(r"D:\Codex\WORKSPACES\Projects\mc-agent\spec\eom")
DATA = ROOT / "neoforge-26.2/src/main/resources/data/earth_on_minecraft"
LANG = ROOT / "neoforge-26.2/src/main/resources/assets/earth_on_minecraft/lang"

# Section prefix in catalog/herbs.md -> the vanilla blocks that can carry it.
# Section headings in the spec are Chinese; match on the leading marker instead.
SECTION_BIOMES = {
    "一": ["minecraft:short_grass", "minecraft:fern"],
    "二": ["minecraft:short_grass", "minecraft:fern"],
    "三": ["minecraft:short_grass", "minecraft:fern", "minecraft:large_fern"],
    "四": ["minecraft:fern", "minecraft:large_fern"],
    "五": ["minecraft:short_grass", "minecraft:fern"],
    "六": ["minecraft:short_grass"],
    "七": ["minecraft:short_grass", "minecraft:fern"],
}

DEFAULT_BLOCKS = ["minecraft:short_grass", "minecraft:fern"]


def main() -> None:
    rows = [
        json.loads(line)
        for line in (SPEC / "items.jsonl").read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    herbs = [r for r in rows if r.get("family") == "herb"
             and (r.get("from") or []) == ["gather:wild_herb"]]
    herbs.sort(key=lambda r: r["id"])

    # Group herbs by which vanilla plant should carry them.
    by_block: dict[str, list[str]] = {}
    for herb in herbs:
        section = ((herb.get("catalog") or {}).get("section") or "").strip()
        marker = section[:1]
        blocks = SECTION_BIOMES.get(marker, DEFAULT_BLOCKS)
        name = herb["id"].split(":", 1)[1]
        for block in blocks:
            by_block.setdefault(block, []).append(name)

    # One loot modifier per vanilla plant, each with an equal-weight pool over its herbs.
    # NeoForge 26.1.2 discovers every file in this folder automatically.
    modifier_dir = DATA / "loot_modifiers"
    modifier_dir.mkdir(parents=True, exist_ok=True)

    modifier_ids = []
    for block, names in sorted(by_block.items()):
        slug = block.split(":", 1)[1]
        modifier_id = f"wild_herb_{slug}"
        modifier_ids.append(modifier_id)
        entries = [
            {"type": "minecraft:item", "name": f"earth_on_minecraft:{name}", "weight": 1}
            for name in names
        ]
        (modifier_dir / f"{modifier_id}.json").write_text(json.dumps({
            "type": "neoforge:add_table",
            "conditions": [
                {"condition": "neoforge:loot_table_id",
                 "loot_table_id": f"minecraft:blocks/{slug}"},
                # Only 1 in 8 broken plants yields a herb, so a field is not a herb farm.
                {"condition": "minecraft:random_chance", "chance": 0.125},
            ],
            "table": f"earth_on_minecraft:wild_herb/{slug}",
        }, indent=2) + "\n", encoding="utf-8")

        table_dir = DATA / "loot_table/wild_herb"
        table_dir.mkdir(parents=True, exist_ok=True)
        (table_dir / f"{slug}.json").write_text(json.dumps({
            "type": "minecraft:generic",
            "pools": [{
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": entries,
            }],
        }, indent=2) + "\n", encoding="utf-8")

    print(f"WILD_HERB_OK modifiers={len(modifier_ids)} herbs={len(herbs)}")
    for block, names in sorted(by_block.items()):
        print(f"  {block:24} {len(names)} herbs")


if __name__ == "__main__":
    main()
