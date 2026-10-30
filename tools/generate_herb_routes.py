#!/usr/bin/env python3
"""Give every wild herb a renewable source.

The spec marks 136 herbs as `from: ["gather:wild_herb"]` but ships no plant block, no loot table
and no worldgen, so those items are currently unobtainable. This emits a propagation route on the
herb processing table: one cutting plus a nutrient input yields two cuttings. Every herb stays
faithful to its `gather:wild_herb` origin (you still need the first one), and the chain becomes
renewable instead of dead.

Nutrient choice follows what the herb actually is:
  * mineral herbs (alum, borax, cinnabar, ...) take potash
  * woody/resin herbs take bone meal
  * everything else takes bone meal, the cheapest farm input
"""

from __future__ import annotations

import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = pathlib.Path(r"D:\Codex\WORKSPACES\Projects\mc-agent\spec\eom")
OUT = ROOT / "neoforge-26.2/src/main/resources/data/earth_on_minecraft/earth/quality/herb_routes.json"
JAVA = ROOT / "neoforge-26.2/src/main/java/com/xxsx/earthonminecraft"

MINERAL_HERBS = {
    "alum", "borax_ore", "cinnabar_ore", "gypsum", "realgar", "orpiment", "mirabilite",
    "natron", "sulfur", "salphur", "quartz", "fluorite", "mica", "talc", "kaolin",
}
WOODY_HERBS = {
    "agarwood", "sappanwood", "cinnamon_bark", "cinnamon_twig", "eucommia_bark",
    "cortex_moutan", "cortex_dictamni", "benzoin", "pine_resin", "resin", "camphor",
    "borneol", "sandalwood", "lignum_aquilariae",
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
    herbs = sorted(
        r["id"].split(":", 1)[1]
        for r in rows
        if r.get("family") == "herb"
        and (r.get("from") or []) == ["gather:wild_herb"]
        and r["id"].split(":", 1)[1] in known
    )

    routes = []
    for herb in herbs:
        if herb in MINERAL_HERBS:
            nutrient = "potash"
            note = "mineral herb: propagate on a potash bed"
        elif herb in WOODY_HERBS:
            nutrient = "bone_meal"
            note = "woody herb: propagate on a bone meal bed"
        else:
            nutrient = "bone_meal"
            note = "propagate a cutting on a bone meal bed"
        routes.append({
            "id": f"herb/{herb}",
            "machine": "herb_processing_table",
            "inputs": [
                {"item": f"earth_on_minecraft:{herb}", "count": 1},
                {"item": f"earth_on_minecraft:{nutrient}", "count": 1},
            ],
            "outputs": [{"item": f"earth_on_minecraft:{herb}", "count": 2}],
            "ticks": 200,
            "energy": 0,
            "note": note,
        })

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({
        "schema_version": 1,
        "source": str(SPEC),
        "routes": routes,
        "counts": {"total": len(routes), "machines": ["herb_processing_table"]},
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"HERB_ROUTES_OK routes={len(routes)}")
    print(f"  potash bed  {sum(1 for r in routes if r['note'].startswith('mineral'))}")
    print(f"  bone meal   {len(routes) - sum(1 for r in routes if r['note'].startswith('mineral'))}")


if __name__ == "__main__":
    main()
