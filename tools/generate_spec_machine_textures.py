#!/usr/bin/env python3
"""Draw front-face textures and base models for the spec recipe-station machines.

The art is deterministic template art in the same 128x128 style as the existing machine faces:
a dark steel panel, a family accent, a machine-specific working element, and an active variant
that lights that element. It is a placeholder for the approved art pipeline, not final art.
"""

from __future__ import annotations

import hashlib
import json
import pathlib

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / "neoforge-26.2/src/main/resources/assets/earth_on_minecraft"
TEXTURES = ASSETS / "textures/block"
MODELS = ASSETS / "models/block"
SIZE = 128

FAMILY = {
    "bloomery_controller": "thermal",
    "bread_oven_controller": "thermal",
    "glass_kiln_controller": "thermal",
    "lime_kiln_controller": "thermal",
    "pit_kiln_controller": "thermal",
    "shaft_furnace_controller": "thermal",
    "anvil": "forming", "bar_counter": "mixing", "campfire": "thermal", "carpentry_bench": "forming",
    "casting_table": "forming", "coffee_roaster": "thermal", "condenser": "column", "converter": "thermal",
    "curing_barn": "thermal", "fermenting_crock": "reaction", "filter_press": "wet_process",
    "gem_cutter": "forming", "herb_processing_table": "mixing", "loom": "forming", "mill": "comminution",
    "noodle_press": "forming", "oil_press": "forming", "rice_huller": "comminution", "smelter": "thermal",
    "smokehouse": "thermal", "steamer": "thermal", "steamer_basket": "thermal", "still": "column",
    "stone_mill": "comminution", "stove": "thermal", "tablet_press": "forming", "tannery": "wet_process",
    "tea_kiln": "thermal", "thickener": "wet_process", "water_treatment_unit": "wet_process",
}

ACCENT = {
    "comminution": (196, 158, 96),
    "classification": (104, 158, 186),
    "wet_process": (78, 176, 158),
    "thermal": (232, 122, 58),
    "electrochemical": (94, 156, 232),
    "forming": (172, 146, 206),
    "reaction": (112, 186, 124),
    "column": (128, 190, 204),
    "mixing": (176, 182, 96),
    "crystallization": (146, 176, 226),
}

SIDE = {
    "comminution": "modern_machine_side", "classification": "modern_machine_side",
    "forming": "modern_machine_side", "mixing": "modern_machine_side",
    "wet_process": "wet_machine_side", "electrochemical": "wet_machine_side",
    "reaction": "wet_machine_side", "crystallization": "wet_machine_side",
    "thermal": "hot_machine_side", "column": "tower_machine_side",
}

TOP = {key: value.replace("_side", "_top") for key, value in SIDE.items()}

# Each machine gets a working element drawn on the front face so the block reads as that machine.
ELEMENT = {
    "bloomery_controller": "bloomery_controller",
    "bread_oven_controller": "bread_oven_controller",
    "glass_kiln_controller": "glass_kiln_controller",
    "lime_kiln_controller": "lime_kiln_controller",
    "pit_kiln_controller": "pit_kiln_controller",
    "shaft_furnace_controller": "shaft_furnace_controller",
    "anvil": "anvil",
    "bar_counter": "bottles",
    "campfire": "flame",
    "carpentry_bench": "boards",
    "casting_table": "mold",
    "coffee_roaster": "drum",
    "condenser": "coil",
    "converter": "tuyere",
    "curing_barn": "louvers",
    "fermenting_crock": "crock",
    "filter_press": "plates",
    "gem_cutter": "facets",
    "herb_processing_table": "shelf",
    "loom": "warp",
    "mill": "millstone",
    "noodle_press": "rollers",
    "oil_press": "cage",
    "rice_huller": "rollers",
    "smelter": "taphole",
    "smokehouse": "smokedoor",
    "steamer": "steamlid",
    "steamer_basket": "tiers",
    "still": "potstill",
    "stone_mill": "quern",
    "stove": "burners",
    "tablet_press": "punch",
    "tannery": "vat",
    "tea_kiln": "wok",
    "thickener": "tank",
    "water_treatment_unit": "vessel",
}


def noise(seed: str, x: int, y: int) -> int:
    return int(hashlib.sha256(f"{seed}:{x}:{y}".encode()).hexdigest(), 16) % 17 - 8


def shade(color: tuple[int, int, int], delta: int) -> tuple[int, int, int]:
    return tuple(max(0, min(255, channel + delta)) for channel in color)


def panel(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int], base: tuple[int, int, int],
          raised: bool = True) -> None:
    draw.rectangle(box, fill=base, outline=shade(base, -34))
    if raised:
        x1, y1, x2, y2 = box
        draw.line((x1 + 1, y1 + 1, x2 - 1, y1 + 1), fill=shade(base, 26))
        draw.line((x1 + 1, y1 + 1, x1 + 1, y2 - 1), fill=shade(base, 18))
        draw.line((x1 + 1, y2 - 1, x2 - 1, y2 - 1), fill=shade(base, -22))
        draw.line((x2 - 1, y1 + 1, x2 - 1, y2 - 1), fill=shade(base, -22))


def bolt(draw: ImageDraw.ImageDraw, x: int, y: int, base: tuple[int, int, int]) -> None:
    draw.rectangle((x, y, x + 3, y + 3), fill=shade(base, 22), outline=shade(base, -40))


def draw_element(draw: ImageDraw.ImageDraw, element: str, accent: tuple[int, int, int], active: bool) -> None:
    lit = shade(accent, 40) if active else shade(accent, -58)
    glow = shade(accent, 78) if active else shade(accent, -36)
    if element == "anvil":
        draw.rectangle((30, 74, 98, 82), fill=shade(accent, -70))
        draw.rectangle((38, 60, 90, 74), fill=shade(accent, -54))
        draw.rectangle((52, 82, 76, 100), fill=shade(accent, -62))
        draw.polygon(((98, 62), (116, 70), (98, 74)), fill=shade(accent, -46))
        draw.rectangle((30, 100, 98, 106), fill=shade(accent, -78))
    elif element == "bottles":
        draw.rectangle((18, 44, 110, 54), fill=shade(accent, -68))
        for index, x in enumerate(range(24, 104, 14)):
            height = 20 + index % 3 * 6
            draw.rectangle((x, 54 - height, x + 7, 54), fill=lit)
            draw.rectangle((x + 2, 54 - height - 6, x + 5, 54 - height), fill=shade(lit, -20))
        draw.rectangle((24, 88, 104, 100), fill=shade(accent, -60))
    elif element == "flame":
        draw.ellipse((28, 82, 100, 108), fill=shade(accent, -72))
        draw.polygon(((64, 40), (82, 78), (72, 96), (56, 96), (46, 76)), fill=lit)
        draw.polygon(((64, 58), (74, 82), (64, 94), (54, 82)), fill=glow)
        for x in (24, 100):
            draw.rectangle((x, 66, x + 6, 104), fill=shade(accent, -78))
    elif element == "boards":
        for index, y in enumerate(range(40, 104, 12)):
            draw.rectangle((16, y, 112, y + 9), fill=shade(accent, -66 + index * 5))
            draw.line((16, y + 9, 112, y + 9), fill=shade(accent, -84))
        draw.rectangle((22, 34, 106, 40), fill=shade(accent, -50))
    elif element == "mold":
        draw.rectangle((18, 72, 110, 104), fill=shade(accent, -70))
        for index, x in enumerate(range(24, 104, 18)):
            draw.rectangle((x, 80, x + 12, 98), fill=shade(accent, -50))
            draw.rectangle((x + 2, 82, x + 10, 96), fill=lit if active else shade(accent, -76))
        draw.rectangle((52, 40, 76, 72), fill=shade(accent, -58))
    elif element == "drum":
        draw.ellipse((24, 44, 104, 100), fill=shade(accent, -66), outline=shade(accent, -84))
        draw.ellipse((38, 56, 90, 88), fill=shade(accent, -78))
        draw.rectangle((58, 66, 70, 78), fill=lit)
        draw.rectangle((24, 100, 104, 108), fill=shade(accent, -80))
    elif element == "coil":
        draw.rectangle((36, 36, 92, 104), fill=shade(accent, -70))
        for y in range(44, 100, 10):
            draw.arc((40, y, 88, y + 18), 200, 340, fill=lit, width=3)
        draw.rectangle((16, 46, 36, 58), fill=shade(accent, -58))
        draw.rectangle((92, 76, 112, 88), fill=shade(accent, -58))
    elif element == "tuyere":
        draw.ellipse((28, 40, 100, 104), fill=shade(accent, -68))
        draw.ellipse((42, 54, 86, 90), fill=lit if active else shade(accent, -80))
        for y in (60, 72, 84):
            draw.rectangle((14, y, 40, y + 5), fill=shade(accent, -52))
    elif element == "louvers":
        for index, y in enumerate(range(40, 104, 10)):
            draw.rectangle((22, y, 106, y + 6), fill=shade(accent, -62 + (index % 2) * 8))
        draw.rectangle((22, 30, 106, 36), fill=shade(accent, -48))
    elif element == "crock":
        draw.ellipse((30, 44, 98, 104), fill=shade(accent, -64), outline=shade(accent, -82))
        draw.rectangle((40, 34, 88, 50), fill=shade(accent, -52))
        draw.rectangle((58, 24, 70, 36), fill=lit)
        draw.ellipse((52, 60, 76, 78), fill=shade(accent, -78))
    elif element == "plates":
        for index, x in enumerate(range(20, 108, 12)):
            draw.rectangle((x, 44, x + 8, 100), fill=shade(accent, -66 + (index % 3) * 6))
        draw.rectangle((16, 36, 112, 44), fill=shade(accent, -48))
        draw.rectangle((16, 100, 112, 108), fill=shade(accent, -78))
    elif element == "facets":
        draw.polygon(((64, 34), (94, 58), (82, 96), (46, 96), (34, 58)), fill=shade(accent, -62))
        draw.polygon(((64, 44), (84, 60), (76, 86), (52, 86), (44, 60)), fill=lit)
        draw.line((64, 44, 64, 86), fill=glow)
        draw.line((44, 60, 84, 60), fill=glow)
    elif element == "shelf":
        draw.rectangle((16, 36, 112, 44), fill=shade(accent, -56))
        for x in (22, 52, 82):
            draw.rectangle((x, 46, x + 22, 66), fill=shade(accent, -68))
        draw.rectangle((16, 74, 112, 82), fill=shade(accent, -56))
        draw.rectangle((24, 84, 104, 104), fill=shade(accent, -72))
    elif element == "warp":
        for x in range(22, 108, 8):
            draw.line((x, 34, x, 104), fill=shade(accent, -60))
        draw.rectangle((18, 60, 110, 66), fill=shade(accent, -46))
        draw.rectangle((18, 80, 110, 86), fill=shade(accent, -46))
    elif element == "millstone":
        draw.ellipse((22, 34, 106, 106), fill=shade(accent, -66), outline=shade(accent, -84))
        draw.ellipse((46, 54, 82, 86), fill=shade(accent, -80))
        draw.line((64, 40, 64, 100), fill=shade(accent, -52))
        draw.line((28, 70, 100, 70), fill=shade(accent, -52))
    elif element == "rollers":
        draw.rectangle((16, 44, 52, 96), fill=shade(accent, -64))
        draw.rectangle((76, 44, 112, 96), fill=shade(accent, -64))
        for y in (52, 64, 76, 88):
            draw.line((20, y, 48, y), fill=lit)
            draw.line((80, y, 108, y), fill=lit)
        draw.rectangle((52, 60, 76, 82), fill=shade(accent, -78))
    elif element == "cage":
        draw.rectangle((24, 40, 104, 100), fill=shade(accent, -72))
        for x in range(28, 104, 10):
            draw.line((x, 40, x, 100), fill=shade(accent, -50))
        draw.rectangle((56, 24, 72, 44), fill=shade(accent, -56))
        draw.rectangle((40, 100, 88, 108), fill=lit if active else shade(accent, -78))
    elif element == "taphole":
        draw.rectangle((28, 32, 100, 104), fill=shade(accent, -70))
        draw.ellipse((52, 54, 76, 76), fill=glow)
        draw.rectangle((58, 76, 70, 104), fill=lit)
        draw.rectangle((24, 24, 104, 32), fill=shade(accent, -52))
    elif element == "smokedoor":
        draw.rectangle((28, 32, 100, 104), fill=shade(accent, -68))
        draw.rectangle((36, 40, 92, 96), fill=shade(accent, -78))
        for y in range(46, 92, 12):
            draw.line((36, y, 92, y), fill=shade(accent, -56))
        for index in range(5):
            draw.ellipse((44 + index * 8, 30 - index * 4, 52 + index * 8, 40), fill=shade(accent, 30) if active else shade(accent, -60))
    elif element == "steamlid":
        draw.ellipse((22, 52, 106, 100), fill=shade(accent, -66))
        draw.ellipse((36, 30, 92, 66), fill=shade(accent, -56))
        draw.rectangle((60, 20, 68, 32), fill=shade(accent, -44))
        if active:
            draw.ellipse((56, 12, 72, 24), fill=glow)
    elif element == "tiers":
        for index, y in enumerate(range(40, 96, 16)):
            draw.rectangle((28, y, 100, y + 12), fill=shade(accent, -62 + index * 4))
            draw.line((28, y + 12, 100, y + 12), fill=shade(accent, -80))
        draw.ellipse((38, 22, 90, 44), fill=shade(accent, -54))
    elif element == "potstill":
        draw.ellipse((26, 56, 88, 108), fill=shade(accent, -64))
        draw.rectangle((48, 34, 66, 60), fill=shade(accent, -56))
        draw.arc((60, 26, 112, 74), 90, 360, fill=shade(accent, -48), width=6)
        draw.rectangle((96, 62, 110, 100), fill=shade(accent, -70))
        draw.rectangle((100, 96, 106, 104), fill=lit)
    elif element == "quern":
        draw.ellipse((20, 36, 108, 108), fill=shade(accent, -64))
        draw.ellipse((40, 52, 88, 92), fill=shade(accent, -80))
        draw.ellipse((58, 64, 70, 76), fill=shade(accent, -52))
        draw.rectangle((100, 60, 116, 70), fill=shade(accent, -46))
    elif element == "burners":
        draw.rectangle((16, 36, 112, 104), fill=shade(accent, -66))
        for x in (30, 68):
            draw.ellipse((x, 52, x + 32, 84), outline=shade(accent, -48), width=4)
            draw.ellipse((x + 8, 60, x + 24, 76), fill=lit)
        draw.rectangle((16, 96, 112, 104), fill=shade(accent, -78))
    elif element == "punch":
        draw.rectangle((40, 24, 88, 44), fill=shade(accent, -56))
        draw.rectangle((54, 44, 74, 72), fill=shade(accent, -44))
        draw.rectangle((28, 72, 100, 96), fill=shade(accent, -66))
        draw.rectangle((54, 78, 74, 90), fill=lit if active else shade(accent, -80))
        draw.rectangle((44, 96, 84, 106), fill=shade(accent, -74))
    elif element == "vat":
        draw.rectangle((20, 56, 108, 104), fill=shade(accent, -66))
        draw.rectangle((26, 62, 102, 96), fill=lit if active else shade(accent, -80))
        draw.line((20, 56, 108, 56), fill=shade(accent, -44))
        draw.rectangle((30, 30, 98, 44), fill=shade(accent, -58))
    elif element == "wok":
        draw.ellipse((24, 56, 104, 100), fill=shade(accent, -66))
        draw.ellipse((36, 62, 92, 92), fill=shade(accent, -78))
        draw.rectangle((20, 96, 108, 108), fill=shade(accent, -74))
        draw.ellipse((58, 40, 70, 64), fill=lit)
    elif element == "tank":
        draw.rectangle((24, 36, 104, 104), fill=shade(accent, -66))
        draw.rectangle((30, 42, 98, 92), fill=shade(accent, -80))
        draw.line((30, 70, 98, 70), fill=lit if active else shade(accent, -56))
        draw.rectangle((56, 24, 72, 38), fill=shade(accent, -50))
    elif element == "vessel":
        draw.rectangle((34, 28, 94, 104), fill=shade(accent, -66))
        for y in (44, 60, 76):
            draw.line((34, y, 94, y), fill=shade(accent, -50))
        draw.rectangle((20, 52, 34, 64), fill=shade(accent, -56))
        draw.rectangle((94, 76, 110, 88), fill=shade(accent, -56))
        draw.ellipse((58, 34, 70, 46), fill=lit if active else shade(accent, -80))
    else:
        draw.rectangle((32, 40, 96, 96), fill=shade(accent, -64))


def draw_face(machine_id: str, active: bool) -> Image.Image:
    family = FAMILY[machine_id]
    accent = ACCENT[family]
    base = (52, 55, 59) if family != "thermal" else (58, 50, 48)
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 255))
    draw = ImageDraw.Draw(image)
    for y in range(SIZE):
        for x in range(SIZE):
            draw.point((x, y), fill=shade(base, noise(machine_id, x // 4, y // 4)))
    panel(draw, (4, 4, 123, 123), shade(base, -12))
    panel(draw, (10, 10, 117, 117), shade(base, 6))
    panel(draw, (16, 16, 111, 30), shade(base, 16))
    for x, y in ((7, 7), (116, 7), (7, 116), (116, 116)):
        bolt(draw, x, y, base)
    draw_element(draw, ELEMENT[machine_id], accent, active)
    draw.rectangle((16, 108, 111, 116), fill=shade(base, -30))
    if active:
        draw.rectangle((18, 18, 24, 24), fill=shade(accent, 70))
    else:
        draw.rectangle((18, 18, 24, 24), fill=shade(base, -44))
    return image


def write_model(machine_id: str) -> None:
    family = FAMILY[machine_id]
    textures = {
        "particle": f"earth_on_minecraft:block/{machine_id}",
        "front": f"earth_on_minecraft:block/{machine_id}",
        "back": f"earth_on_minecraft:block/{SIDE[family]}",
        "left": f"earth_on_minecraft:block/{SIDE[family]}",
        "right": f"earth_on_minecraft:block/{SIDE[family]}",
        "top": f"earth_on_minecraft:block/{TOP[family]}",
        "bottom": f"earth_on_minecraft:block/{TOP[family]}",
        "fault": "minecraft:block/redstone_lamp_on",
    }
    for suffix, active in (("", False), ("_active", True)):
        model = {
            "parent": "minecraft:block/block",
            "ambientocclusion": False,
            "gui_light": "side",
            "textures": {
                **textures,
                "particle": f"earth_on_minecraft:block/{machine_id}{suffix}",
                "front": f"earth_on_minecraft:block/{machine_id}{suffix}",
            },
            "elements": [
                {
                    "name": "body",
                    "from": [0, 0, 0],
                    "to": [16, 16, 16],
                    "faces": {
                        "north": {"uv": [0, 0, 16, 16], "texture": "#front"},
                        "south": {"uv": [0, 0, 16, 16], "texture": "#back"},
                        "west": {"uv": [0, 0, 16, 16], "texture": "#left"},
                        "east": {"uv": [0, 0, 16, 16], "texture": "#right"},
                        "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
                        "down": {"uv": [0, 0, 16, 16], "texture": "#bottom"},
                    },
                }
            ],
        }
        (MODELS / f"{machine_id}{suffix}.json").write_text(
            json.dumps(model, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    drawn = 0
    for machine_id in sorted(FAMILY):
        for suffix, active in (("", False), ("_active", True)):
            path = TEXTURES / f"{machine_id}{suffix}.png"
            draw_face(machine_id, active).save(path, optimize=True)
            drawn += 1
        write_model(machine_id)
    print(f"SPEC_MACHINE_TEXTURES_OK machines={len(FAMILY)} textures={drawn}")


if __name__ == "__main__":
    main()
