#!/usr/bin/env python3
"""Report spec coverage against the mod source tree without launching Minecraft."""

from __future__ import annotations

import argparse
import json
import pathlib
import re
from collections import Counter

ROOT = pathlib.Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "neoforge-26.2/src/main/java"
OUTPUT = ROOT / "docs/quality/spec-coverage.json"
DEFAULT_SPEC = pathlib.Path(r"D:\Codex\WORKSPACES\Projects\mc-agent\spec\eom")


def load(name: str, root: pathlib.Path):
    return [json.loads(line) for line in (root / name).read_text(encoding="utf-8").splitlines() if line.strip()]


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


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--spec", default=str(DEFAULT_SPEC))
    args = parser.parse_args()
    spec = pathlib.Path(args.spec)
    known = registered_ids()
    items = load("items.jsonl", spec)
    blocks = load("blocks.jsonl", spec)
    machines = load("machines.jsonl", spec)
    recipes = [r for r in load("recipes.jsonl", spec) if r.get("state") != "retired"]

    def covered(record: dict) -> bool:
        return record["id"].split(":", 1)[1] in known

    item_missing = [r["id"].split(":", 1)[1] for r in items if not covered(r)]
    block_missing = [r["id"].split(":", 1)[1] for r in blocks if not covered(r)]
    machine_missing = [r["id"].split(":", 1)[1] for r in machines if not covered(r)]

    def recipe_ready(record: dict) -> bool:
        refs = [entry.get("item") for entry in (record.get("inputs") or []) + (record.get("outputs") or [])
                if entry.get("item")]
        if any(ref.startswith("earth_on_minecraft:") and ref.split(":", 1)[1] not in known for ref in refs):
            return False
        if str(record.get("type", "")).startswith("machine:"):
            return record["type"].split(":", 1)[1] in known
        return True

    machine_recipes = [r for r in recipes if str(r.get("type", "")).startswith("machine:")]
    vanilla_recipes = [r for r in recipes if str(r.get("type", "")).startswith("minecraft:")]
    generated_machine_routes = 0
    generated_spec_routes = JAVA_ROOT / "com/xxsx/earthonminecraft/SpecMachineRecipes.java"
    if generated_spec_routes.exists():
        generated_machine_routes = len(re.findall(r'new Route\(', generated_spec_routes.read_text(encoding="utf-8")))
    report = {
        "schema_version": 1,
        "spec": str(spec),
        "items": {"spec": len(items), "registered": len(items) - len(item_missing), "missing": item_missing},
        "blocks": {"spec": len(blocks), "registered": len(blocks) - len(block_missing), "missing": block_missing},
        "machines": {"spec": len(machines), "registered": len(machines) - len(machine_missing), "missing": machine_missing},
        "vanilla_recipes": {"spec": len(vanilla_recipes), "item_ready": sum(recipe_ready(r) for r in vanilla_recipes)},
        "machine_recipes": {
            "spec": len(machine_recipes),
            "item_and_machine_ready": sum(recipe_ready(r) for r in machine_recipes),
            "generated_spec_routes": generated_machine_routes,
            "curated_code_routes": 0,
        },
        "families": dict(Counter(r.get("family") for r in items)),
    }
    output_path = OUTPUT
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"SPEC_COVERAGE items={report['items']['registered']}/{report['items']['spec']} "
          f"blocks={report['blocks']['registered']}/{report['blocks']['spec']} "
          f"machines={report['machines']['registered']}/{report['machines']['spec']} "
          f"vanilla={report['vanilla_recipes']['item_ready']}/{report['vanilla_recipes']['spec']} "
          f"machineRecipes={report['machine_recipes']['item_and_machine_ready']}/{report['machine_recipes']['spec']} "
          f"generatedRoutes={generated_machine_routes}")


if __name__ == "__main__":
    main()
