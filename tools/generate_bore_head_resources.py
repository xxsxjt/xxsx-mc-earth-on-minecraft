#!/usr/bin/env python3
"""Emit resources for the bore head: blockstates, models, item definition, loot, recipe, lang."""

from __future__ import annotations

import json
import pathlib

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[1]
RES = ROOT / "neoforge-26.2/src/main/resources"
ASSETS = RES / "assets/earth_on_minecraft"
DATA = RES / "data/earth_on_minecraft"
LANG = ASSETS / "lang"

BLOCK_ID = "bore_head"
FACINGS = ("north", "east", "south", "west")


def steel(colour: tuple[int, int, int], delta: int) -> tuple[int, int, int]:
    return tuple(max(0, min(255, c + delta)) for c in colour)


def draw_texture(path: pathlib.Path, active: bool) -> None:
    base = (78, 84, 92)
    accent = (226, 122, 48) if active else (120, 126, 134)
    image = Image.new("RGBA", (128, 128), base + (255,))
    draw = ImageDraw.Draw(image)
    # Panel bevel.
    draw.rectangle((0, 0, 127, 127), outline=steel(base, -40), width=4)
    draw.line((4, 4, 123, 4), fill=steel(base, 26), width=3)
    # Central bore ring.
    draw.ellipse((34, 34, 93, 93), fill=steel(base, -22), outline=steel(base, 30), width=4)
    draw.ellipse((48, 48, 79, 79), fill=accent + (255,), outline=steel(base, -30), width=3)
    # Corner bolts.
    for cx, cy in ((16, 16), (111, 16), (16, 111), (111, 111)):
        draw.ellipse((cx - 5, cy - 5, cx + 5, cy + 5), fill=steel(base, 34), outline=steel(base, -50))
    # Vent slots along the top and bottom.
    for x in range(30, 100, 12):
        draw.rectangle((x, 14, x + 6, 24), fill=steel(base, -30))
        draw.rectangle((x, 103, x + 6, 113), fill=steel(base, -30))
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)


def main() -> None:
    textures = ASSETS / "textures/block"
    draw_texture(textures / f"{BLOCK_ID}.png", active=False)
    draw_texture(textures / f"{BLOCK_ID}_active.png", active=True)

    # Blockstate: facing x active, 8 variants (this is a device, not a processing machine).
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

    # Crafting recipe: steel frame, a jaw crusher core, and two copper coils.
    (DATA / "recipe" / f"{BLOCK_ID}.json").write_text(json.dumps({
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["SSS", "CJC", "SBS"],
        "key": {
            "S": "earth_on_minecraft:steel",
            "C": "earth_on_minecraft:copper_wire",
            "J": "earth_on_minecraft:jaw_crusher",
            "B": "earth_on_minecraft:simple_battery_cell",
        },
        "result": {"id": f"earth_on_minecraft:{BLOCK_ID}", "count": 1},
    }, indent=2) + "\n", encoding="utf-8")

    keys = {
        f"block.earth_on_minecraft.{BLOCK_ID}": ("Industrial Bore Head", "工业钻采头"),
        "tooltip.earth_on_minecraft.automation.bore_head": (
            "Works nearby lodes without a player: draws EOU, extracts ore, and swaps a spent lode "
            "for its depleted form. Right-click to read the reserve it can reach.",
            "无需玩家即可开采附近矿脉：消耗 EOU 抽取矿石，采空后把矿脉换成贫矿块。右键查看可采储量。",
        ),
        "screen.earth_on_minecraft.miner.status": (
            "Bore head: %s ore in reach, %s items stored, %s lodes tracked",
            "钻采头：可采储量 %s，已存物品 %s，追踪矿脉 %s",
        ),
        "screen.earth_on_minecraft.deposit.status": (
            "Ore body: %s / %s units remaining",
            "矿体：剩余 %s / %s 单位",
        ),
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

    print(f"BORE_HEAD_RESOURCES_OK textures=2 variants={len(variants)}")


if __name__ == "__main__":
    main()
