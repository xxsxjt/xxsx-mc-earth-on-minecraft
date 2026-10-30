#!/usr/bin/env python3
"""Add the fluid-tank status strings used by the processing machine screen."""

from __future__ import annotations

import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
LANG = ROOT / "neoforge-26.2/src/main/resources/assets/earth_on_minecraft/lang"

KEYS = {
    "screen.earth_on_minecraft.machine.missing_fluid_short":
        ("needs fluid", "需要流体"),
    "screen.earth_on_minecraft.machine.missing_fluid":
        ("This route needs more fluid in the machine tank than it holds.",
         "该路线需要机器罐内更多流体。"),
    "screen.earth_on_minecraft.machine.tank":
        ("Tank: %s / %s mB", "罐内：%s / %s mB"),
    "screen.earth_on_minecraft.machine.tank.tooltip":
        ("Fluid is drawn from and returned to the machine tank; pipes and buckets can fill it on any side.",
         "流体从机器罐抽取并回流；管线和桶可从任意一面注入。"),
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
