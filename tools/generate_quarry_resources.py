#!/usr/bin/env python3
"""Emit resources for the quarry block."""

from __future__ import annotations

import json
import pathlib

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[1]
RES = ROOT / "neoforge-26.2/src/main/resources"
ASSETS = RES / "assets/earth_on_minecraft"
DATA = RES / "data/earth_on_minecraft"
LANG = ASSETS / "lang"

BLOCK_ID = "quarry"
FACINGS = ("north", "east", "south", "west")


def shade(colour, delta):
    return tuple(max(0, min(255, c + delta)) for c in colour)


def draw_texture(path: pathlib.Path, active: bool) -> None:
    base = (96, 92, 84)
    accent = (232, 168, 72) if active else (128, 122, 110)
    image = Image.new("RGBA", (128, 128), base + (255,))
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 127, 127), outline=shade(base, -42), width=4)
    draw.line((4, 4, 123, 4), fill=shade(base, 24), width=3)
    # Toothed cutting drum.
    draw.rectangle((18, 46, 109, 82), fill=shade(base, -26), outline=shade(base, 20), width=3)
    for x in range(24, 106, 10):
        draw.polygon([(x, 46), (x + 5, 32), (x + 10, 46)], fill=accent + (255,))
    # Conveyor chevrons below the drum.
    for y in range(88, 120, 10):
        for x in range(22, 104, 16):
            draw.line((x, y, x + 8, y + 5), fill=shade(base, 30), width=3)
            draw.line((x + 8, y + 5, x, y + 10), fill=shade(base, 30), width=3)
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)


def main() -> None:
    textures = ASSETS / "textures/block"
    draw_texture(textures / f"{BLOCK_ID}.png", active=False)
    draw_texture(textures / f"{BLOCK_ID}_active.png", active=True)

    variants = {}
    for facing in FACINGS:
        for active in (False, True):
            suffix = "_active" if active else ""
            variants[f"facing={facing},active={'true' if active else 'false'}"] = {
                "model": f"earth_on_minecraft:block/{BLOCK_ID}{suffix}"
            }
    (ASSETS / "blockstates" / f"{BLOCK_ID}.json").write_text(
        json.dumps({"variants": variants}, indent=2) + "\n", encoding="utf-8")

    for suffix, texture in (("", BLOCK_ID), ("_active", f"{BLOCK_ID}_active")):
        (ASSETS / "models/block" / f"{BLOCK_ID}{suffix}.json").write_text(json.dumps({
            "parent": "minecraft:block/cube_bottom_top",
            "textures": {
                "top": f"earth_on_minecraft:block/{texture}",
                "bottom": f"earth_on_minecraft:block/{texture}",
                "side": f"earth_on_minecraft:block/{texture}",
            },
        }, indent=2) + "\n", encoding="utf-8")

    (ASSETS / "items" / f"{BLOCK_ID}.json").write_text(json.dumps({
        "model": {"type": "minecraft:model", "model": f"earth_on_minecraft:block/{BLOCK_ID}"}
    }, indent=2) + "\n", encoding="utf-8")

    (DATA / "loot_table/blocks" / f"{BLOCK_ID}.json").write_text(json.dumps({
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": f"earth_on_minecraft:{BLOCK_ID}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    }, indent=2) + "\n", encoding="utf-8")

    (DATA / "recipe" / f"{BLOCK_ID}.json").write_text(json.dumps({
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["SSS", "ICI", "BBB"],
        "key": {
            "S": "earth_on_minecraft:steel",
            "I": "minecraft:iron_ingot",
            "C": "earth_on_minecraft:copper_wire",
            "B": "minecraft:stone",
        },
        "result": {"id": f"earth_on_minecraft:{BLOCK_ID}", "count": 1},
    }, indent=2) + "\n", encoding="utf-8")

    keys = {
        f"block.earth_on_minecraft.{BLOCK_ID}": ("Quarry", "采石场"),
        f"item.earth_on_minecraft.{BLOCK_ID}": ("Quarry", "采石场"),
        "tooltip.earth_on_minecraft.automation.quarry": (
            "Grinds out bulk rock from whatever it sits on. Ores are excluded, so a quarry never "
            "replaces a bore head.",
            "就地开采大宗石料。矿石被排除在外，采石场不会取代钻采头。",
        ),
        "screen.earth_on_minecraft.quarry.status": (
            "Quarry: %s items stored", "采石场：已存物品 %s"),
    }
    for lang, index in (("en_us", 0), ("zh_cn", 1)):
        path = LANG / f"{lang}.json"
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        added = 0
        for key, texts in keys.items():
            if key not in data:
                data[key] = texts[index]
                added += 1
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"{lang}: added {added}")

    print(f"QUARRY_RESOURCES_OK variants={len(variants)}")


if __name__ == "__main__":
    main()
