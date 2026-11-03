#!/usr/bin/env python3
"""Emit SpecMachineRecipes.java: spec machine routes for the already-registered machines.

Routes whose primary input is already covered by the curated hard-coded iron-loop table are
skipped, so approved first-iron routes and their GameTests keep priority. Fluid-only routes are
emitted too; the block entity selects them from the machine tank contents.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "neoforge-26.2/src/main/java/com/xxsx/earthonminecraft"
OUTPUT = JAVA_ROOT / "SpecMachineRecipes.java"
MANIFEST = ROOT / "docs/quality/spec-machine-recipes.json"
DEFAULT_SPEC = ROOT.parents[1] / "spec" / "eom"

KIND_BY_MACHINE = {
    "jaw_crusher": "CRUSHER",
    "ball_mill": "BALL_MILL",
    "sieve": "SIEVE",
    "magnetic_separator": "MAGNETIC_SEPARATOR",
    "flotation_cell": "FLOTATION_CELL",
    "ore_roaster": "ROASTER",
    "reduction_furnace": "REDUCTION_FURNACE",
    "leaching_tank": "LEACHING_TANK",
    "electrolytic_cell": "ELECTROLYTIC_CELL",
    "powder_press": "POWDER_PRESS",
    "chemical_reactor": "CHEMICAL_REACTOR",
    "distillation_column": "DISTILLATION_COLUMN",
    "mixer": "MIXER",
    "crystallizer": "CRYSTALLIZER",
    "industrial_kiln": "INDUSTRIAL_KILN",
    "gas_separator": "GAS_SEPARATOR",
    "fertilizer_granulator": "FERTILIZER_GRANULATOR",
    "polymerizer": "POLYMERIZER",
    "steam_cracker": "STEAM_CRACKER",
    "synthesis_loop": "SYNTHESIS_LOOP",
    "absorption_tower": "ABSORPTION_TOWER",
    "anvil": "ANVIL",
    "bar_counter": "BAR_COUNTER",
    "campfire": "CAMPFIRE",
    "carpentry_bench": "CARPENTRY_BENCH",
    "casting_table": "CASTING_TABLE",
    "coffee_roaster": "COFFEE_ROASTER",
    "condenser": "CONDENSER",
    "converter": "CONVERTER",
    "curing_barn": "CURING_BARN",
    "fermenting_crock": "FERMENTING_CROCK",
    "filter_press": "FILTER_PRESS",
    "gem_cutter": "GEM_CUTTER",
    "herb_processing_table": "HERB_PROCESSING_TABLE",
    "loom": "LOOM",
    "mill": "MILL",
    "noodle_press": "NOODLE_PRESS",
    "oil_press": "OIL_PRESS",
    "rice_huller": "RICE_HULLER",
    "smelter": "SMELTER",
    "smokehouse": "SMOKEHOUSE",
    "steamer": "STEAMER",
    "steamer_basket": "STEAMER_BASKET",
    "still": "STILL",
    "stone_mill": "STONE_MILL",
    "stove": "STOVE",
    "tablet_press": "TABLET_PRESS",
    "tannery": "TANNERY",
    "tea_kiln": "TEA_KILN",
    "thickener": "THICKENER",
    "water_treatment_unit": "WATER_TREATMENT_UNIT",
    "bloomery_controller": "BLOOMERY",
    "bread_oven_controller": "BREAD_OVEN",
    "glass_kiln_controller": "GLASS_KILN",
    "lime_kiln_controller": "LIME_KILN",
    "pit_kiln_controller": "PIT_KILN",
    "shaft_furnace_controller": "SHAFT_FURNACE",
}

MAX_TOTAL_INPUTS = 5
FLUID_SCALE = 1.0


def normalize_item_batch(
    item_inputs: list[tuple[str, int]],
    item_outputs: list[tuple[str, int]],
) -> tuple[list[tuple[str, int]], list[tuple[str, int]]]:
    """Scale down per-mille alloy batches so every slot stays within a single 64-item stack."""
    all_counts = [count for _, count in item_inputs + item_outputs]
    max_count = max(all_counts, default=0)
    if max_count <= 64:
        return item_inputs, item_outputs
    divisor = max(2, (max_count + 9) // 10)
    scaled_inputs = [(item, max(1, round(count / divisor))) for item, count in item_inputs]
    total_in = sum(count for _, count in scaled_inputs)
    scaled_outputs = [
        (item, min(64, max(1, round(count / divisor), total_in if len(item_outputs) == 1 else 1)))
        for item, count in item_outputs
    ]
    return scaled_inputs, scaled_outputs


def split_inputs(record: dict) -> tuple[list[tuple[str, int]], list[tuple[str, int]]]:
    """Split a spec route's inputs into item inputs and fluid inputs (mB)."""
    items: list[tuple[str, int]] = []
    fluids: list[tuple[str, int]] = []
    for entry in record.get("inputs") or []:
        if "fluid" in entry:
            fluids.append((entry["fluid"], int(entry.get("amount") or entry.get("count") or 0)))
        elif "item" in entry:
            items.append((entry["item"], int(entry.get("count", 1))))
    return items, fluids


def split_outputs(record: dict) -> tuple[list[tuple[str, int]], list[tuple[str, int]]]:
    items: list[tuple[str, int]] = []
    fluids: list[tuple[str, int]] = []
    for entry in record.get("outputs") or []:
        if "fluid" in entry:
            fluids.append((entry["fluid"], int(entry.get("amount") or entry.get("count") or 0)))
        elif "item" in entry:
            items.append((entry["item"], int(entry.get("count", 1))))
    return items, fluids


def load(name: str, spec: pathlib.Path):
    return [json.loads(line) for line in (spec / name).read_text(encoding="utf-8").splitlines() if line.strip()]


def java_string(value: str) -> str:
    return value.replace("\\", "\\\\").replace('"', '\\"')


def registered_ids() -> set[str]:
    text = "\n".join(p.read_text(encoding="utf-8", errors="replace") for p in JAVA_ROOT.rglob("*.java"))
    ids = set(re.findall(r'\b(?:item|notebookItem|machineBlock|oreBlock|supportBlock|controlPanelBlock|structureProjectionBlock|machineInterfaceBlock|energyGeneratorBlock|powerCableBlock|batteryBoxBlock|conveyorBeltBlock|settlementBoardBlock)\(\s*"([a-z0-9_]+)"', text))
    ids |= set(re.findall(r'ITEMS\.registerItem\(\s*"([a-z0-9_]+)"', text))
    ids |= set(re.findall(r'BLOCKS\.registerBlock\(\s*"([a-z0-9_]+)"', text))
    for name in ("SpecItems.java", "SpecBlocks.java"):
        path = JAVA_ROOT / name
        if path.exists():
            ids |= set(re.findall(r'new (?:Entry|BlockEntry)\("([a-z0-9_]+)"', path.read_text(encoding="utf-8")))
    return ids


def fluid_ids(spec: pathlib.Path) -> set[str]:
    """Fluid ids that SpecFluids registers; the generator runs after the fluid layer exists."""
    path = spec / "fluids.jsonl"
    if not path.exists():
        return set()
    return {
        json.loads(line)["id"].split(":", 1)[1]
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    }


def curated_primary_items() -> set[tuple[str, str]]:
    """(machineId, primary item id) pairs already covered by the hand-written table."""
    text = (JAVA_ROOT / "ProcessingMachineBlock.java").read_text(encoding="utf-8")
    body = text[text.index("private static List<Recipe> createRecipes()"):]
    entrypoint = (JAVA_ROOT / "EarthOnMinecraft.java").read_text(encoding="utf-8")
    const_to_slug = {}
    for match in re.finditer(r'DeferredItem<[^>]+>\s+([A-Z0-9_]+)\s*=\s*item\("([a-z0-9_]+)"', entrypoint):
        const_to_slug[match.group(1)] = match.group(2)
    for match in re.finditer(
            r'DeferredBlock<[^>]+>\s+([A-Z0-9_]+)\s*=\s*'
            r'(?:oreBlock|machineBlock|supportBlock|energyGeneratorBlock|powerCableBlock|batteryBoxBlock|conveyorBeltBlock)'
            r'\("([a-z0-9_]+)"', entrypoint):
        const_to_slug[match.group(1)] = match.group(2)

    pairs: set[tuple[str, str]] = set()
    for match in re.finditer(r'r(?:WithCoke)?\(Kind\.([A-Z_]+),\s*([^,]+),', body):
        kind = match.group(1)
        machine = next((m for m, k in KIND_BY_MACHINE.items() if k == kind), None)
        if machine is None:
            continue
        source = match.group(2).strip()
        if source.startswith("EarthOnMinecraft."):
            const = source.split(".")[1].split("::")[0]
            item = "earth_on_minecraft:" + const_to_slug.get(const, const.lower())
        elif source.startswith("() -> Blocks."):
            snake = re.sub(r"(?<!^)(?=[A-Z])", "_", source.split("Blocks.")[1].rstrip(")")).lower()
            item = "minecraft:" + snake
        elif source.startswith("() -> Items."):
            snake = re.sub(r"(?<!^)(?=[A-Z])", "_", source.split("Items.")[1].rstrip(")")).lower()
            item = "minecraft:" + snake
        else:
            continue
        pairs.add((machine, item))
    return pairs


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--spec", default=str(DEFAULT_SPEC))
    args = parser.parse_args()
    spec = pathlib.Path(args.spec)
    known = registered_ids()
    known_fluids = fluid_ids(spec)
    curated = curated_primary_items()
    rows = load("recipes.jsonl", spec)

    entries = []
    skipped = {"retired": 0, "foreign_machine": 0, "fluid_only": 0, "no_item_input": 0,
               "too_many_inputs": 0, "unknown_item": 0, "unknown_fluid": 0,
               "curated_route": 0, "no_item_output": 0}
    seen: set[tuple] = set()

    for record in rows:
        if record.get("state") == "retired":
            skipped["retired"] += 1
            continue
        recipe_type = str(record.get("type", ""))
        if recipe_type.startswith("multiblock:"):
            machine = recipe_type.split(":", 1)[1] + "_controller"
        elif recipe_type.startswith("machine:"):
            machine = recipe_type.split(":", 1)[1]
        else:
            continue
        if machine not in KIND_BY_MACHINE:
            skipped["foreign_machine"] += 1
            continue
        inputs = record.get("inputs") or []
        outputs = record.get("outputs") or []
        item_inputs, fluid_inputs = split_inputs(record)
        item_outputs, fluid_outputs = split_outputs(record)
        item_inputs, item_outputs = normalize_item_batch(item_inputs, item_outputs)
        # Fluid-only conversions have no primary item. They are still emitted: the block entity
        # selects them from the machine tank contents instead of the input slot.
        if not item_inputs and not fluid_inputs:
            skipped["no_item_input"] += 1
            continue
        if len(item_inputs) > MAX_TOTAL_INPUTS:
            skipped["too_many_inputs"] += 1
            continue
        if not item_outputs and not fluid_outputs:
            skipped["no_item_output"] += 1
            continue
        refs = [item for item, _ in item_inputs + item_outputs]
        if any(ref.startswith("earth_on_minecraft:") and ref.split(":", 1)[1] not in known for ref in refs):
            skipped["unknown_item"] += 1
            continue
        if any(fluid.startswith("earth_on_minecraft:") and fluid.split(":", 1)[1] not in known_fluids
               for fluid, _ in fluid_inputs + fluid_outputs):
            skipped["unknown_fluid"] += 1
            continue
        primary = item_inputs[0][0] if item_inputs else ""
        if primary and (machine, primary) in curated:
            skipped["curated_route"] += 1
            continue
        key = (machine, tuple(item_inputs), tuple(item_outputs), tuple(fluid_inputs), tuple(fluid_outputs))
        if key in seen:
            skipped["curated_route"] += 1
            continue
        seen.add(key)
        entries.append({
            "kind": KIND_BY_MACHINE[machine],
            "machine": machine,
            "source": record["id"],
            "note": record.get("note") or "",
            "ticks": int(record.get("ticks") or 0),
            "energy": int(record.get("energy") or 0),
            "inputs": item_inputs,
            "outputs": item_outputs,
            "fluidInputs": fluid_inputs,
            "fluidOutputs": fluid_outputs,
        })

    # Authored chains live outside the spec (the spec left these as family placeholders).
    # tools/generate_drink_routes.py and tools/generate_medicine_routes.py write them.
    for chain_file in ("drink_routes.json", "medicine_routes.json", "herb_routes.json",
                       "crop_routes.json"):
        chain_path = (ROOT / "neoforge-26.2/src/main/resources/data/earth_on_minecraft"
                      / "earth/quality" / chain_file)
        if not chain_path.exists():
            continue
        chain_data = json.loads(chain_path.read_text(encoding="utf-8"))
        for route in chain_data.get("routes", []):
            machine = route["machine"]
            if machine not in KIND_BY_MACHINE:
                skipped["foreign_machine"] += 1
                continue
            item_inputs = [(e["item"], int(e.get("count", 1))) for e in route.get("inputs") or []
                           if e.get("item")]
            item_outputs = [(e["item"], int(e.get("count", 1))) for e in route.get("outputs") or []
                            if e.get("item")]
            if not item_inputs:
                skipped["no_item_input"] += 1
                continue
            if not item_outputs:
                skipped["no_item_output"] += 1
                continue
            if len(item_inputs) > 9:
                skipped["too_many_inputs"] += 1
                continue
            refs = [item for item, _ in item_inputs + item_outputs]
            if any(ref.startswith("earth_on_minecraft:") and ref.split(":", 1)[1] not in known
                   for ref in refs):
                skipped["unknown_item"] += 1
                continue
            key = (machine, tuple(item_inputs), tuple(item_outputs), (), ())
            if key in seen:
                skipped["curated_route"] += 1
                continue
            seen.add(key)
            entries.append({
                "kind": KIND_BY_MACHINE[machine],
                "machine": machine,
                "source": route["id"],
                "note": route.get("note") or "",
                "ticks": int(route.get("ticks") or 0),
                "energy": int(route.get("energy") or 0),
                "inputs": item_inputs,
                "outputs": item_outputs,
                "fluidInputs": [],
                "fluidOutputs": [],
            })

    entries.sort(key=lambda entry: (entry["machine"], entry["source"]))
    header = [
        "package com.xxsx.earthonminecraft;",
        "",
        "import java.util.ArrayList;",
        "import java.util.List;",
        "",
        "final class SpecMachineRecipes {",
        "    record Route(String kind, String machine, String sourceId, String note, int ticks, int energy,",
        "                 String[] inputs, int[] inputCounts, String[] outputs, int[] outputCounts,",
        "                 String[] fluidInputs, int[] fluidInputAmounts,",
        "                 String[] fluidOutputs, int[] fluidOutputAmounts) {",
        "    }",
        "",
    ]

    # javac rejects any method body over 64 KB, so the table is emitted as chunks.
    CHUNK = 100
    chunks = [entries[i:i + CHUNK] for i in range(0, len(entries), CHUNK)]

    lines = list(header)
    for index, chunk in enumerate(chunks):
        lines.append(f"    private static void add{index}(List<Route> routes) {{")
        lines.append("        List<Route> batch = new ArrayList<Route>();")
        for entry in chunk:
            inputs = ", ".join(f'"{java_string(item)}"' for item, _ in entry["inputs"])
            input_counts = ", ".join(str(count) for _, count in entry["inputs"])
            outputs = ", ".join(f'"{java_string(item)}"' for item, _ in entry["outputs"])
            output_counts = ", ".join(str(count) for _, count in entry["outputs"])
            fluid_inputs = ", ".join(f'"{java_string(fluid)}"' for fluid, _ in entry["fluidInputs"])
            fluid_input_amounts = ", ".join(str(amount) for _, amount in entry["fluidInputs"])
            fluid_outputs = ", ".join(f'"{java_string(fluid)}"' for fluid, _ in entry["fluidOutputs"])
            fluid_output_amounts = ", ".join(str(amount) for _, amount in entry["fluidOutputs"])
            lines.append(
                '            batch.add(new Route("%s", "%s", "%s", "%s", %d, %d, new String[]{%s}, new int[]{%s}, '
                'new String[]{%s}, new int[]{%s}, new String[]{%s}, new int[]{%s}, new String[]{%s}, new int[]{%s}));'
                % (entry["kind"], entry["machine"], java_string(entry["source"]),
                   java_string(entry["note"]), entry["ticks"], entry["energy"], inputs, input_counts,
                   outputs, output_counts, fluid_inputs, fluid_input_amounts, fluid_outputs,
                   fluid_output_amounts)
            )
        lines.append("        routes.addAll(batch);")
        lines.append("    }")
        lines.append("")

    lines.append("    static final List<Route> ROUTES = buildRoutes();")
    lines.append("")
    lines.append("    private static List<Route> buildRoutes() {")
    lines.append(f"        List<Route> routes = new ArrayList<Route>({len(entries)});")
    for index in range(len(chunks)):
        lines.append(f"        add{index}(routes);")
    lines.append("        return List.copyOf(routes);")
    lines.append("    }")
    lines.append("")
    lines.append("    private SpecMachineRecipes() {")
    lines.append("    }")
    lines.append("}")
    lines.append("")

    OUTPUT.write_text("\n".join(lines), encoding="utf-8")
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST.write_text(json.dumps({
        "schema_version": 1,
        "source": str(spec),
        "routes": len(entries),
        "skipped": skipped,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"SPEC_MACHINE_RECIPES routes={len(entries)} skipped={skipped}")


if __name__ == "__main__":
    main()
