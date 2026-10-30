"""Add the six kiln structure hints and block names used by the multiblock screens."""

from __future__ import annotations

import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
LANG = ROOT / "neoforge-26.2/src/main/resources/assets/earth_on_minecraft/lang"

KEYS = {
    "tooltip.earth_on_minecraft.multiblock.bloomery":
        ("Lay a 3x3 shell of firebrick four blocks tall, put a tuyere on one side and the "
         "controller in the front ring, then cap it with a chimney.",
         "用耐火砖砌 3x3、四格高的窑体，一侧装风口、前环装控制器，顶部加烟囱。"),
    "screen.earth_on_minecraft.machine.structure.bloomery":
        ("Bloomery: 3x3 firebrick shell, 4 high, tuyere on a side wall, chimney on top.",
         "块炼炉：3x3 耐火砖窑体，高 4 格，侧壁装风口，顶部装烟囱。"),
    "tooltip.earth_on_minecraft.multiblock.bread_oven":
        ("Build a 3x3 brick dome three blocks tall with the controller in the front ring and an "
         "oven mouth facing out.",
         "用砖砌 3x3、三格高的穹顶，前环装控制器，窑口朝外。"),
    "screen.earth_on_minecraft.machine.structure.bread_oven":
        ("Bread oven: 3x3 brick dome, 3 high, controller in the front ring, oven mouth outward.",
         "面包窑：3x3 砖砌穹顶，高 3 格，前环装控制器，窑口朝外。"),
    "tooltip.earth_on_minecraft.multiblock.glass_kiln":
        ("Raise a 3x3 firebrick kiln four blocks tall with a tuyere low on one wall, an oven "
         "mouth opposite, and a chimney on the crown.",
         "砌 3x3、四格高的耐火砖窑，一侧下部装风口，对侧装窑口，顶冠加烟囱。"),
    "screen.earth_on_minecraft.machine.structure.glass_kiln":
        ("Glass kiln: 3x3 firebrick kiln, 4 high, low tuyere, opposite oven mouth, crown chimney.",
         "玻璃窑：3x3 耐火砖窑，高 4 格，下部风口、对侧窑口、顶冠烟囱。"),
    "tooltip.earth_on_minecraft.multiblock.lime_kiln":
        ("A 2x2 firebrick shaft four blocks tall: controller in the front ring, chimney on top.",
         "2x2 耐火砖竖窑，高四格：前环装控制器，顶部装烟囱。"),
    "screen.earth_on_minecraft.machine.structure.lime_kiln":
        ("Lime kiln: 2x2 firebrick shaft, 4 high, controller in the front ring, chimney on top.",
         "石灰窑：2x2 耐火砖竖窑，高 4 格，前环装控制器，顶部烟囱。"),
    "tooltip.earth_on_minecraft.multiblock.pit_kiln":
        ("Ring a 3x3 mound of packed earth two blocks tall around the controller and leave the "
         "top open as the air inlet.",
         "在控制器周围用夯实土围出 3x3、两格高的土堆，顶部留作进气口。"),
    "screen.earth_on_minecraft.machine.structure.pit_kiln":
        ("Charcoal pit kiln: 3x3 packed-earth ring, 2 high, open air inlet on top.",
         "炭窑：3x3 夯实土圈，高 2 格，顶部留进气口。"),
    "tooltip.earth_on_minecraft.multiblock.shaft_furnace":
        ("Build a 3x3 firebrick shaft five blocks tall with two tuyere rings, an oven mouth at "
         "the base and a chimney on top.",
         "砌 3x3、五格高的耐火砖竖炉，两道风口环、底部出铁口、顶部烟囱。"),
    "screen.earth_on_minecraft.machine.structure.shaft_furnace":
        ("Shaft furnace: 3x3 firebrick shaft, 5 high, two tuyere rings, base oven mouth, top chimney.",
         "竖炉：3x3 耐火砖竖炉，高 5 格，两道风口环，底部出铁口，顶部烟囱。"),
}


def main() -> None:
    for lang, index in (("en_us", 0), ("zh_cn", 1)):
        path = LANG / f"{lang}.json"
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        added = 0
        for key, texts in KEYS.items():
            if key not in data:
                data[key] = texts[index]
                added += 1
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"{lang} added={added}")


if __name__ == "__main__":
    main()
