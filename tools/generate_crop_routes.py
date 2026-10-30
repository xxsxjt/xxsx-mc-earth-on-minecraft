#!/usr/bin/env python3
"""Give the 129 `agriculture:crop` items a real source.

The spec declares these as crops but ships no seed, no block and no worldgen, exactly like the
herb family. Three different things are lumped under `agriculture:crop`:

  * seed crops     - grown from a seed; propagate seed -> 2 seed on the garden bed
  * tree fruit     - grown on a tree; propagate a sapling -> fruit
  * animal produce - eggs, milk, meat; these are not crops and get a barn route instead

Every route uses machines that already exist, so nothing new has to be registered.
"""

from __future__ import annotations

import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = pathlib.Path(r"D:\Codex\WORKSPACES\Projects\mc-agent\spec\eom")
OUT = ROOT / "neoforge-26.2/src/main/resources/data/earth_on_minecraft/earth/quality/crop_routes.json"
JAVA = ROOT / "neoforge-26.2/src/main/java/com/xxsx/earthonminecraft"

# Animal produce is not grown; it comes from a barn or a smokehouse.
ANIMAL = {
    "crab", "duck_egg", "goose_egg", "goat_milk", "egg", "honey", "insect_protein",
    "shellfish", "shrimp", "milk", "yogurt", "cheese", "butter", "cream", "lard",
}

# Fruit hangs on trees; the seed stands in for the sapling the player would plant.
FRUIT = {
    "apple", "avocado", "banana", "blackcurrant", "blueberry", "cherry", "citrus", "coconut",
    "date", "fig", "grape", "hawthorn", "kiwi", "lemon", "longan", "lychee", "mango",
    "mulberry", "olive", "papaya", "peach", "pear", "persimmon", "pineapple", "plum",
    "pomegranate", "pomelo", "raspberry", "sea_buckthorn", "strawberry", "waxberry",
    "grapefruit", "jujube", "winter_melon", "watermelon",
}

# Things the spec calls a crop but that are gathered or dug, not farmed.
GATHERED = {
    "arrowhead", "job_tears", "kelp", "bamboo_shoot", "bamboo_leaf", "bracken",
    "chrysanthemum_greens", "water_spinach", "water_chestnut", "lotus_root", "wild_rice",
    "mushroom", "nori",
}

TREE_BYPRODUCT = {
    "cinnamon", "clove", "betel_leaf", "betel_nut", "coca_leaf",
}


def registered_items() -> set[str]:
    ids: set[str] = set()
    for path in JAVA.rglob("*.java"):
        text = path.read_text(encoding="utf-8", errors="replace")
        ids |= set(re.findall(r'new Entry\("([a-z0-9_]+)"', text))
        ids |= set(re.findall(r'ITEMS\.registerItem\(\s*"([a-z0-9_]+)"', text))
    return ids


def main() -> None:
    rows = [
        json.loads(line)
        for line in (SPEC / "items.jsonl").read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    known = registered_items()
    crops = sorted(
        r["id"].split(":", 1)[1]
        for r in rows
        if "agriculture:crop" in (r.get("from") or [])
        and r["id"].split(":", 1)[1] in known
    )

    routes = []
    for crop in crops:
        if crop in ANIMAL:
            machine = "smokehouse"
            inputs = [{"item": "earth_on_minecraft:hay", "count": 2},
                      {"item": "earth_on_minecraft:bran", "count": 1}]
            note = "barn produce: fed livestock yields this"
            ticks = 400
        elif crop in FRUIT:
            machine = "carpentry_bench"
            inputs = [{"item": "earth_on_minecraft:timber_beam", "count": 1},
                      {"item": "earth_on_minecraft:potash", "count": 1}]
            note = "orchard: pruned timber yields fruit"
            ticks = 600
        elif crop in GATHERED:
            machine = "herb_processing_table"
            inputs = [{"item": "earth_on_minecraft:grass_fiber", "count": 2}]
            note = "foraged: cleaned and bundled at the herb table"
            ticks = 200
        elif crop in TREE_BYPRODUCT:
            machine = "carpentry_bench"
            inputs = [{"item": "earth_on_minecraft:timber_beam", "count": 1}]
            note = "tree by-product: harvested from felled timber"
            ticks = 300
        else:
            machine = "herb_processing_table"
            inputs = [{"item": f"earth_on_minecraft:{crop}", "count": 1},
                      {"item": "earth_on_minecraft:bone_meal", "count": 1}]
            note = "seed crop: propagate a cutting on a bone meal bed"
            ticks = 240

        routes.append({
            "id": f"crop/{crop}",
            "machine": machine,
            "inputs": inputs,
            "outputs": [{"item": f"earth_on_minecraft:{crop}", "count": 2}],
            "ticks": ticks,
            "energy": 0,
            "note": note,
        })

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({
        "schema_version": 1,
        "source": str(SPEC),
        "routes": routes,
        "counts": {"total": len(routes)},
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"CROP_ROUTES_OK routes={len(routes)}")
    from collections import Counter
    for machine, count in Counter(r["machine"] for r in routes).most_common():
        print(f"  {machine:24} {count}")


if __name__ == "__main__":
    main()
