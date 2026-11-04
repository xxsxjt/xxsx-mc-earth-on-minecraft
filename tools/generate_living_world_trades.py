#!/usr/bin/env python3
"""Generate NeoForge 26.2 villager trades from Living World role data."""

from __future__ import annotations

import json
import shutil
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "neoforge-26.2" / "src" / "main" / "resources"
ROLE_DIR = RESOURCES / "data" / "earth_on_minecraft" / "settlements" / "resident_roles"
TRADE_DIR = RESOURCES / "data" / "earth_on_minecraft" / "villager_trade" / "resident"
TAG_DIR = RESOURCES / "data" / "minecraft" / "tags" / "villager_trade"


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def role_predicate(role_id: str) -> dict[str, object]:
    nbt = f'{{NeoForgeData:{{EarthOnMinecraftResidentRoleId:"{role_id}"}}}}'
    return {
        "condition": "minecraft:entity_properties",
        "entity": "this",
        "predicate": {"minecraft:nbt": nbt},
    }


def item_stack(item_id: str, count: int) -> dict[str, object]:
    value: dict[str, object] = {"id": item_id}
    if count != 1:
        value["count"] = count
    return value


def main() -> None:
    if TRADE_DIR.exists():
        shutil.rmtree(TRADE_DIR)
    tags: dict[tuple[str, int], list[str]] = defaultdict(list)
    trade_count = 0

    for role_file in sorted(ROLE_DIR.glob("*.json")):
        role = json.loads(role_file.read_text(encoding="utf-8"))
        role_name = role_file.stem
        role_id = f"earth_on_minecraft:{role_name}"
        professions = [
            value.split(":", 1)[1]
            for value in role.get("vanilla_professions", [])
            if value.startswith("minecraft:") and value != "minecraft:none"
        ]

        for index, trade in enumerate(role.get("trades", []), start=1):
            level = max(1, min(5, int(trade["level"])))
            trade_path = f"resident/{role_name}/level_{level}_{index}"
            trade_id = f"earth_on_minecraft:{trade_path}"
            payload = {
                "wants": item_stack(trade["input"], int(trade["input_count"])),
                "gives": item_stack(trade["output"], int(trade["output_count"])),
                "max_uses": int(trade.get("max_uses", 12)),
                "reputation_discount": float(trade.get("price_multiplier", 0.05)),
                "xp": int(trade.get("xp", 2)),
                "merchant_predicate": role_predicate(role_id),
            }
            write_json(TRADE_DIR / role_name / f"level_{level}_{index}.json", payload)
            for profession in professions:
                tags[(profession, level)].append(trade_id)
            trade_count += 1

    for (profession, level), values in sorted(tags.items()):
        write_json(
            TAG_DIR / profession / f"level_{level}.json",
            {"replace": False, "values": sorted(set(values))},
        )

    print(f"Generated {trade_count} role trades and {len(tags)} profession-level tags")


if __name__ == "__main__":
    main()
