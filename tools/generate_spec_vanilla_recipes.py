#!/usr/bin/env python3
"""Emit vanilla crafting recipes from spec/eom/recipes.jsonl."""

from __future__ import annotations

import argparse
import json
import pathlib
import re
from collections import Counter

ROOT = pathlib.Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "neoforge-26.2/src/main/java"
RES = ROOT / "neoforge-26.2/src/main/resources"
RECIPE_DIR = RES / "data/earth_on_minecraft/recipe"
MANIFEST = ROOT / "docs/quality/spec-vanilla-recipes.json"
DEFAULT_SPEC = pathlib.Path(r"D:\Codex\WORKSPACES\Projects\mc-agent\spec\eom")


def load(name: str, spec: pathlib.Path):
    return [json.loads(line) for line in (spec / name).read_text(encoding="utf-8").splitlines() if line.strip()]


def registered_ids() -> set[str]:
    text = "\n".join(p.read_text(encoding="utf-8", errors="replace") for p in JAVA_ROOT.rglob("*.java"))
    ids = set(re.findall(r'\b(?:item|notebookItem|machineBlock|oreBlock|supportBlock|controlPanelBlock|structureProjectionBlock|machineInterfaceBlock|energyGeneratorBlock|powerCableBlock|batteryBoxBlock|conveyorBeltBlock|settlementBoardBlock)\(\s*"([a-z0-9_]+)"', text))
    ids |= set(re.findall(r'ITEMS\.registerItem\(\s*"([a-z0-9_]+)"', text))
    ids |= set(re.findall(r'BLOCKS\.registerBlock\(\s*"([a-z0-9_]+)"', text))
    for name in ("SpecItems.java", "SpecBlocks.java"):
        path = JAVA_ROOT / "com/xxsx/earthonminecraft" / name
        if path.exists():
            ids |= set(re.findall(r'new (?:Entry|BlockEntry)\("([a-z0-9_]+)"', path.read_text(encoding="utf-8")))
    return ids


def slug_for(recipe_id: str) -> str:
    tail = recipe_id.split("/", 1)[1] if "/" in recipe_id else recipe_id
    tail = tail.split(":")[-1]
    return re.sub(r"[^a-z0-9_]+", "_", tail.lower()).strip("_")


def shaped(inputs: list[dict]) -> dict | None:
    cells = []
    for entry in inputs:
        for _ in range(int(entry.get("count", 1))):
            cells.append(entry["item"])
    if len(cells) > 9:
        return None
    grid = [cells[index * 3:(index + 1) * 3] for index in range(3)]
    while grid and not any(grid[-1]):
        grid.pop()
    keys = {}
    alphabet = "ABCDEFGHI"
    lines = []
    for row in grid:
        line = ""
        for index, value in enumerate(row):
            if value is None:
                line += " "
                continue
            key = keys.setdefault(value, alphabet[len(keys)])
            line += key
        lines.append(line)
    return {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": lines,
        "key": {key: item for item, key in keys.items()},
    }


def shapeless(inputs: list[dict]) -> dict | None:
    ingredients = []
    for entry in inputs:
        for _ in range(int(entry.get("count", 1))):
            ingredients.append(entry["item"])
    if len(ingredients) > 9:
        return None
    return {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": ingredients,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--spec", default=str(DEFAULT_SPEC))
    args = parser.parse_args()
    spec = pathlib.Path(args.spec)
    recipes = [r for r in load("recipes.jsonl", spec)
               if r.get("state") != "retired" and str(r.get("type", "")).startswith("minecraft:")]
    known = registered_ids()
    written = []
    skipped = []
    for record in recipes:
        refs = [entry.get("item") for entry in (record.get("inputs") or []) + (record.get("outputs") or [])
                if entry.get("item")]
        if any(ref.startswith("earth_on_minecraft:") and ref.split(":", 1)[1] not in known for ref in refs):
            skipped.append({"id": record["id"], "reason": "missing_item"})
            continue
        payload = None
        if record["type"] == "minecraft:crafting_shaped":
            payload = shaped(record.get("inputs") or [])
        elif record["type"] == "minecraft:crafting_shapeless":
            payload = shapeless(record.get("inputs") or [])
        if payload is None:
            skipped.append({"id": record["id"], "reason": "recipe_too_large"})
            continue
        outputs = record.get("outputs") or []
        result = {"id": outputs[0]["item"]}
        if int(outputs[0].get("count", 1)) > 1:
            result["count"] = int(outputs[0]["count"])
        payload["result"] = result
        slug = slug_for(record["id"])
        path = RECIPE_DIR / f"{slug}.json"
        if path.exists():
            skipped.append({"id": record["id"], "reason": "file_exists"})
            continue
        path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        written.append(slug)
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST.write_text(json.dumps({
        "schema_version": 1,
        "source": str(spec),
        "written": written,
        "skipped": skipped,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"SPEC_VANILLA_RECIPES written={len(written)} skipped={len(skipped)}")
    if skipped:
        print(Counter(item["reason"] for item in skipped))


if __name__ == "__main__":
    main()
