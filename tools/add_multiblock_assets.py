"""Add the six spec multiblocks to every machine-asset generator table.

Each generator keys off its own dict, so this script appends one entry per multiblock to the
FACES/CUES/PARTICLES/FAMILIES/ORDER tables in the profile generator, the FAMILY/ELEMENT tables in
the texture generator, the MACHINES table in the resource generator and the SHAPES table in the
Blockbench generator. It is idempotent.
"""

from __future__ import annotations

import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
PROFILES = (
    ROOT
    / "neoforge-26.2/src/main/resources/data/earth_on_minecraft/earth/quality/machine_profiles.json"
)

MULTIBLOCKS = [
    {
        "id": "bloomery",
        "kind": "BLOOMERY",
        "family": "thermal",
        "en": "Bloomery",
        "zh": "块炼炉",
        "faces": (
            "tuyere and slag notch",
            "bellows linkage",
            "charging mouth",
            "tap arch",
            "flue opening",
            "ash pit",
        ),
        "idle": "cold stack and empty tuyere",
        "running": "blast roars, charge sinks, slag runs from the arch",
        "fault": "blast stopped or the stack choked with ash",
        "particles": "bloomery_sparks",
        "prototype": ("a clay-and-stone shaft with a tuyere and a tap arch", "土石竖穴炉，带风口与出渣口"),
        "silhouette": "tapered clay shaft on a squat stone plinth",
        "shape": [
            ("plinth", (1, 0, 1), (15, 3, 15)),
            ("shaft_low", (3, 3, 3), (13, 9, 13)),
            ("shaft_high", (4, 9, 4), (12, 15, 12)),
            ("tuyere", (0, 5, 6), (4, 8, 10)),
            ("tap_arch", (5, 3, 0), (11, 6, 4)),
            ("flue", (5, 15, 5), (11, 16, 11)),
        ],
    },
    {
        "id": "bread_oven",
        "kind": "BREAD_OVEN",
        "family": "thermal",
        "en": "Bread Oven",
        "zh": "面包窑",
        "faces": (
            "arched mouth and peel rest",
            "ash rake door",
            "wood store",
            "brick cheek",
            "dome crown",
            "hearth floor",
        ),
        "idle": "cold dome and swept hearth",
        "running": "embers glow under the dome and loaves rise",
        "fault": "dome cooled and the batch raw",
        "particles": "oven_embers",
        "prototype": ("a brick dome oven with an arched mouth and a peel", "砖砌穹顶窑，带拱形窑口与面包铲"),
        "silhouette": "low brick dome on a square plinth",
        "shape": [
            ("plinth", (1, 0, 1), (15, 3, 15)),
            ("hearth", (2, 3, 2), (14, 5, 14)),
            ("dome_low", (3, 5, 3), (13, 11, 13)),
            ("dome_cap", (5, 11, 5), (11, 14, 11)),
            ("mouth_arch", (5, 3, 0), (11, 8, 4)),
            ("peel", (0, 4, 12), (4, 5, 15)),
        ],
    },
    {
        "id": "glass_kiln",
        "kind": "GLASS_KILN",
        "family": "thermal",
        "en": "Glass Kiln",
        "zh": "玻璃窑",
        "faces": (
            "crucible mouth and gather hole",
            "flue and damper",
            "batch charging chute",
            "glass gather port",
            "crown and vent",
            "firebox and ash",
        ),
        "idle": "cold crown and empty crucible",
        "running": "batch melts to a bright pool under the crown",
        "fault": "crown too cold and the batch frozen",
        "particles": "kiln_heat_shimmer",
        "prototype": ("a domed kiln with a central crucible and a gathering port", "穹顶窑，中央坩埚与取料口"),
        "silhouette": "tall domed kiln with a side gather port",
        "shape": [
            ("base", (1, 0, 1), (15, 4, 15)),
            ("barrel", (2, 4, 2), (14, 12, 14)),
            ("crown", (3, 12, 3), (13, 16, 13)),
            ("gather_port", (12, 5, 6), (16, 10, 10)),
            ("charging_chute", (6, 12, 0), (10, 16, 4)),
            ("firebox", (0, 1, 6), (4, 5, 10)),
        ],
    },
    {
        "id": "lime_kiln",
        "kind": "LIME_KILN",
        "family": "thermal",
        "en": "Lime Kiln",
        "zh": "石灰窑",
        "faces": (
            "draw arch and grate",
            "flue stack",
            "charging opening",
            "stoke hole",
            "stack crown",
            "ash drawer",
        ),
        "idle": "cold stack and empty grate",
        "running": "charge settles as lime drops through the grate",
        "fault": "flue blocked and the draw arch cold",
        "particles": "lime_dust",
        "prototype": ("a square masonry shaft with a draw arch at the base", "方形砖砌竖窑，底部有出灰拱口"),
        "silhouette": "narrow square masonry shaft",
        "shape": [
            ("plinth", (2, 0, 2), (14, 3, 14)),
            ("shaft", (3, 3, 3), (13, 14, 13)),
            ("stack_cap", (4, 14, 4), (12, 16, 12)),
            ("draw_arch", (5, 1, 0), (11, 4, 3)),
            ("charging_opening", (6, 8, 12), (10, 12, 16)),
            ("ash_drawer", (5, 0, 3), (11, 2, 6)),
        ],
    },
    {
        "id": "pit_kiln",
        "kind": "PIT_KILN",
        "family": "thermal",
        "en": "Charcoal Pit Kiln",
        "zh": "炭窑",
        "faces": (
            "vent hole and lit base",
            "sealed earth mound",
            "earth ring",
            "smoke vent",
            "capped crown",
            "ground",
        ),
        "idle": "capped mound and cold vent",
        "running": "smoke seeps from the vents while the wood chars",
        "fault": "air leaking and the wood burning to ash",
        "particles": "pit_kiln_smoke",
        "prototype": ("a stacked wood mound sealed under earth with vent holes", "木材堆覆土封窑，留通气孔"),
        "silhouette": "low rounded earthen mound",
        "shape": [
            ("earth_ring", (0, 0, 0), (16, 4, 16)),
            ("mound_low", (2, 4, 2), (14, 9, 14)),
            ("mound_cap", (5, 9, 5), (11, 13, 11)),
            ("vent_base", (6, 2, 0), (10, 5, 3)),
            ("vent_top", (7, 12, 7), (9, 15, 9)),
            ("earth_cap", (4, 13, 4), (12, 14, 12)),
        ],
    },
    {
        "id": "shaft_furnace",
        "kind": "SHAFT_FURNACE",
        "family": "thermal",
        "en": "Shaft Furnace",
        "zh": "竖炉",
        "faces": (
            "tap hole and slag notch",
            "hot blast main",
            "double tuyere bank",
            "charging bell",
            "off-gas offtake",
            "hearth bottom",
        ),
        "idle": "cold shell and plugged tap",
        "running": "tuyeres blast and iron runs from the tap hole",
        "fault": "bath frozen and the tap blocked",
        "particles": "furnace_iron_sparks",
        "prototype": ("a tall brick shaft with a hot blast main and a taphole", "高型砖砌竖炉，配热风管与出铁口"),
        "silhouette": "tall brick shaft on a broad plinth",
        "shape": [
            ("plinth", (1, 0, 1), (15, 4, 15)),
            ("shaft_low", (2, 4, 2), (14, 12, 14)),
            ("shaft_high", (3, 12, 3), (13, 20, 13)),
            ("blast_main", (0, 5, 6), (4, 10, 10)),
            ("tap_hole", (5, 3, 0), (11, 6, 4)),
            ("charging_bell", (5, 20, 5), (11, 24, 11)),
        ],
    },
]


def insert_before(path: pathlib.Path, anchor: str, addition: str, marker: str) -> bool:
    text = path.read_text(encoding="utf-8")
    if marker in text:
        return False
    index = text.find(anchor)
    if index < 0:
        raise SystemExit(f"anchor not found in {path.name}: {anchor!r}")
    text = text[:index] + addition + text[index:]
    path.write_text(text, encoding="utf-8")
    return True


def patch_profiles() -> None:
    faces = "".join(
        f'    "{m["id"]}": ({", ".join(json.dumps(part, ensure_ascii=False) for part in m["faces"])}),\n'
        for m in MULTIBLOCKS
    )
    cues = "".join(
        f'    "{m["id"]}": ({json.dumps(m["idle"], ensure_ascii=False)}, '
        f'{json.dumps(m["running"], ensure_ascii=False)}, {json.dumps(m["fault"], ensure_ascii=False)}),\n'
        for m in MULTIBLOCKS
    )
    particles = "".join(
        f'    "{m["id"]}": "{m["particles"]}",\n' for m in MULTIBLOCKS
    )
    families = "".join(
        f'    "{m["id"]}": "{m["family"]}",\n' for m in MULTIBLOCKS
    )
    order = "".join(f'"{m["id"]}", ' for m in MULTIBLOCKS)

    path = TOOLS / "generate_spec_machine_profiles.py"
    text = path.read_text(encoding="utf-8")
    if '"bloomery": (' not in text:
        text = text.replace('FACES = {\n', 'FACES = {\n' + faces, 1)
        text = text.replace('CUES = {\n', 'CUES = {\n' + cues, 1)
        text = text.replace('PARTICLES = {\n', 'PARTICLES = {\n' + particles, 1)
        text = text.replace('FAMILIES = {\n', 'FAMILIES = {\n' + families, 1)
        text = text.replace('ORDER = [\n', 'ORDER = [\n    ' + order + '\n    ', 1)
        text = text.replace(
            '    "tablet_press", "tannery", "tea_kiln", "thickener", "water_treatment_unit",\n',
            '    "tablet_press", "tannery", "tea_kiln", "thickener", "water_treatment_unit",\n',
        )
        path.write_text(text, encoding="utf-8")
        print("profiles generator patched")
    else:
        print("profiles generator already patched")


def patch_textures() -> None:
    path = TOOLS / "generate_spec_machine_textures.py"
    text = path.read_text(encoding="utf-8")
    if '"bloomery": "thermal"' in text:
        print("texture generator already patched")
        return
    family = "".join(f'    "{m["id"]}": "{m["family"]}",\n' for m in MULTIBLOCKS)
    element = "".join(f'    "{m["id"]}": "{m["id"]}",\n' for m in MULTIBLOCKS)
    text = text.replace('FAMILY = {\n', 'FAMILY = {\n' + family, 1)
    text = text.replace('ELEMENT = {\n', 'ELEMENT = {\n' + element, 1)
    path.write_text(text, encoding="utf-8")
    print("texture generator patched")


def patch_shapes() -> None:
    path = TOOLS / "generate_blockbench_machine_models.py"
    text = path.read_text(encoding="utf-8")
    if '"bloomery": [' in text:
        print("blockbench generator already patched")
        return
    chunks = []
    for machine in MULTIBLOCKS:
        boxes = ", ".join(
            f'B("{name}", {list(start)}, {list(end)})' for name, start, end in machine["shape"]
        )
        chunks.append(f'    "{machine["id"]}": [\n        {boxes},\n    ],\n')
    addition = "".join(chunks)
    text = text.replace('SHAPES: dict[str, list[dict[str, object]]] = {\n',
                        'SHAPES: dict[str, list[dict[str, object]]] = {\n' + addition, 1)
    path.write_text(text, encoding="utf-8")
    print("blockbench generator patched")


def patch_resources() -> None:
    path = TOOLS / "generate_spec_machine_resources.py"
    text = path.read_text(encoding="utf-8")
    if '"bloomery_controller"' in text:
        print("resource generator already patched")
        return
    entries = {
        "bloomery_controller": (
            "Bloomery", "块炼炉", "pickaxe", "stone",
            ["BBB", "BTB", "SSS"], {"B": "minecraft:clay", "T": "minecraft:campfire", "S": "minecraft:stone"},
        ),
        "bread_oven_controller": (
            "Bread Oven", "面包窑", "pickaxe", "stone",
            ["BBB", "B B", "SSS"], {"B": "minecraft:brick", "S": "minecraft:smooth_stone"},
        ),
        "glass_kiln_controller": (
            "Glass Kiln", "玻璃窑", "pickaxe", "iron",
            ["BBB", "BFB", "SSS"], {"B": "minecraft:brick", "F": "minecraft:blast_furnace",
                                    "S": "minecraft:smooth_stone"},
        ),
        "lime_kiln_controller": (
            "Lime Kiln", "石灰窑", "pickaxe", "stone",
            ["BBB", "B B", "BBB"], {"B": "minecraft:brick"},
        ),
        "pit_kiln_controller": (
            "Charcoal Pit Kiln", "炭窑", "shovel", "none",
            ["DDD", "D D", "DDD"], {"D": "minecraft:dirt"},
        ),
        "shaft_furnace_controller": (
            "Shaft Furnace", "竖炉", "pickaxe", "iron",
            ["BBB", "BFB", "BBB"], {"B": "minecraft:brick", "F": "minecraft:blast_furnace"},
        ),
    }
    lines = []
    for machine_id, (en, zh, tool, tier, pattern, keys) in entries.items():
        pattern_literal = ", ".join(json.dumps(row) for row in pattern)
        keys_literal = ", ".join(f'"{k}": "{v}"' for k, v in keys.items())
        lines.append(
            f'    "{machine_id}": ("{en}", "{zh}", "{tool}", "{tier}",\n'
            f"                      [{pattern_literal}], {{{keys_literal}}}, False),\n"
        )
    text = text.replace('MACHINES = {\n', 'MACHINES = {\n' + "".join(lines), 1)
    path.write_text(text, encoding="utf-8")
    print("resource generator patched")


def main() -> None:
    patch_profiles()
    patch_textures()
    patch_shapes()
    patch_resources()


if __name__ == "__main__":
    main()
