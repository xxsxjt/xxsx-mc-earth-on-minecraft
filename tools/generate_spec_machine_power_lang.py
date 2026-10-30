#!/usr/bin/env python3
"""Add the new PowerMode labels and the passive-process tooltip line."""

from __future__ import annotations

import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
LANG = ROOT / "neoforge-26.2/src/main/resources/assets/earth_on_minecraft/lang"

KEYS = {
    "screen.earth_on_minecraft.machine.power.manual_or_heat":
        ("Power: manual hammering or a hot forge", "动力：手工锻打或炉火加热"),
    "screen.earth_on_minecraft.machine.power.manual_or_grid":
        ("Power: manual operation or EOU grid", "动力：手工操作或 EOU 电网"),
    "screen.earth_on_minecraft.machine.power.kinetic":
        ("Power: hand crank, water wheel or animal drive", "动力：手摇、水轮或畜力"),
    "screen.earth_on_minecraft.machine.power.manual":
        ("Power: manual operation only", "动力：仅手工操作"),
    "screen.earth_on_minecraft.machine.power.heat_only":
        ("Power: fire and fuel only", "动力：仅明火与燃料"),
    "screen.earth_on_minecraft.machine.power.passive":
        ("Power: none, the process runs on its own", "动力：无需供能，过程自行进行"),
    "screen.earth_on_minecraft.machine.passive.tooltip":
        ("This station works without fuel or electricity; only time, temperature and sealing matter.",
         "该工位不需要燃料或电力，只需要时间、温度与密封。"),
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
