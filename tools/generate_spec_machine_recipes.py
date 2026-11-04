#!/usr/bin/env python3
"""Emit SpecMachineRecipes.java: spec machine routes for the already-registered machines.

Routes whose primary input is already covered by the curated early mineral-dressing / first-iron
table are skipped so approved first-iron routes and their GameTests keep priority. Chemical and
reaction machines match exact input/output signatures instead of blocking on primary item alone,
allowing balanced multi-reactant spec reactions (with catalyst, temperature_k, pressure_kpa, and
equation metadata from reactions.jsonl) to take effect.
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

PRIMARY_CURATED_MACHINES = {
    "jaw_crusher",
    "ball_mill",
    "sieve",
    "magnetic_separator",
    "flotation_cell",
    "ore_roaster",
    "leaching_tank",
}

STRICT_STOICHIOMETRIC_MACHINES = {
    "chemical_reactor",
    "synthesis_loop",
    "absorption_tower",
    "polymerizer",
    "electrolytic_cell",
    "steam_cracker",
    "mixer",
}

ALLOWED_TRANSCRIBED_IDS = {
    "machine:electrolytic_cell/080-minecraft:water_bucket",
    "machine:electrolytic_cell/electrorefine_copper",
    "machine:chemical_reactor/123-hard_water_sample",
    "machine:mixer/260-steel_bloom",
    "machine:mixer/281-hematite_dust",
    "machine:mixer/353-nuclear_fuel_pellet",
    "machine:mixer/knead_dough",
}

REJECTED_LEGACY_IDS = {
    "machine:crystallizer/157-sodium_hydroxide",
    "machine:crystallizer/158-ammonium_nitrate",
    "machine:crystallizer/159-gypsum_dust",
    "machine:fertilizer_granulator/175-fertilizer_blend",
    "machine:fertilizer_granulator/176-ammonium_nitrate",
    "machine:powder_press/204-softened_water",
    "machine:powder_press/307-acrylic_resin",
    "machine:powder_press/324-lead_ingot",
    "machine:flotation_cell/320-galena_dust",
    "machine:distillation_column/095-hydrochloric_acid",
    "machine:gas_separator/089-chlorine_gas_cell",
    "machine:gas_separator/091-oxygen_gas_cell",
}

CATALYST_MAP = {
    "Fe": "earth_on_minecraft:iron_catalyst",
    "Fe2O3": "earth_on_minecraft:iron_catalyst",
    "Fe/HCl": "earth_on_minecraft:iron_catalyst",
    "V2O5": "earth_on_minecraft:vanadium_catalyst",
    "Bi/Mo": "earth_on_minecraft:vanadium_catalyst",
    "Pt-Rh": "earth_on_minecraft:platinum_group_catalyst",
    "Pd": "earth_on_minecraft:platinum_group_catalyst",
    "Ag": "earth_on_minecraft:platinum_group_catalyst",
    "Rh/I": "earth_on_minecraft:platinum_group_catalyst",
    "Ni": "earth_on_minecraft:nickel_catalyst",
    "Cu": "earth_on_minecraft:nickel_catalyst",
    "Cu/ZnO": "earth_on_minecraft:nickel_catalyst",
    "AlCl3": "earth_on_minecraft:catalyst",
    "HgCl2": "earth_on_minecraft:catalyst",
    "H2SO4": "earth_on_minecraft:sulfuric_acid",
    "NaOH": "earth_on_minecraft:sodium_hydroxide",
    "cryolite": "earth_on_minecraft:calcium_fluoride",
    "Aspergillus niger": "",
    "Lactobacillus": "",
}

SPECIFIED_REACTION_OVERRIDES = {
    "machine:absorption_tower/131-sulfur_dust": ("earth_on_minecraft:vanadium_catalyst", 720, 101, "2S + 3O2 + 2H2O -> 2H2SO4"),
    "machine:absorption_tower/132-sulfur_dioxide_cell": ("earth_on_minecraft:vanadium_catalyst", 720, 101, "2SO2 + O2 + 2H2O -> 2H2SO4"),
    "machine:absorption_tower/286-vanadium_catalyst": ("earth_on_minecraft:vanadium_catalyst", 720, 101, "2SO2 + O2 + 2H2O -> 2H2SO4"),
    "machine:absorption_tower/289-chlorine_gas_cell": ("", 303, 101, "Cl2 + NaOH -> NaClO + HCl"),
    "machine:absorption_tower/341-carbon_monoxide_cell": ("earth_on_minecraft:iron_catalyst", 673, 1000, "CO + H2O -> CO2 + H2"),
    "machine:chemical_reactor/101-sulfur_dust": ("earth_on_minecraft:vanadium_catalyst", 720, 101, "2S + 3O2 + 2H2O -> 2H2SO4"),
    "machine:chemical_reactor/103-nitric_acid": ("", 353, 101, "HNO3 + NH3 -> NH4NO3"),
    "machine:chemical_reactor/105-lime_dust": ("", 353, 101, "CaO + H2O -> Ca(OH)2"),
    "machine:chemical_reactor/106-soda_ash": ("", 313, 101, "Na2CO3 + CO2 + H2O -> 2NaHCO3"),
    "machine:chemical_reactor/107-sulfuric_acid": ("", 323, 101, "H2SO4 + Ca(OH)2 -> CaSO4·2H2O"),
    "machine:chemical_reactor/109-methanol": ("earth_on_minecraft:platinum_group_catalyst", 873, 101, "CH3OH -> HCHO + H2"),
    "machine:chemical_reactor/112-sodium_bicarbonate": ("", 433, 101, "2NaHCO3 -> Na2CO3 + CO2 + H2O"),
    "machine:chemical_reactor/267-metallurgical_silicon": ("", 573, 101, "Si + 2Cl2 + H2 -> SiHCl3 + HCl"),
    "machine:chemical_reactor/292-hydrogen_gas_cell": ("earth_on_minecraft:platinum_group_catalyst", 323, 200, "2H2 + 2O2 -> 2H2O2"),
    "machine:chemical_reactor/334-sulfuric_acid": ("", 353, 101, "H2SO4 + 2NH3 -> (NH4)2SO4"),
    "machine:chemical_reactor/347-yellowcake": ("", 623, 101, "2U3O8 + O2 -> 6UO3"),
    "machine:chemical_reactor/349-low_enriched_uranium": ("", 900, 101, "UF6 + 2H2O + H2 -> UO2 + 6HF"),
    "machine:electrolytic_cell/170-salt_dust": ("", 353, 101, "2NaCl + 2H2O -> 2NaOH + Cl2 + H2"),
    "machine:mixer/155-sodium_hydroxide": ("", 298, 101, "HCl + NaOH -> NaCl + H2O"),
    "machine:mixer/291-sodium_hypochlorite": ("", 303, 101, "2NaClO + Ca(OH)2 -> Ca(ClO)2 + 2NaOH"),
    "machine:synthesis_loop/129-natural_gas_cell": ("earth_on_minecraft:nickel_catalyst", 1073, 2000, "CH4 + 2H2O -> CO2 + 4H2"),
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
    path = spec / name
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


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

    pairs: set[tuple[str, str]] = {
        ("reduction_furnace", "earth_on_minecraft:iron_concentrate"),
    }
    for match in re.finditer(r'r(?:WithCoke)?\(Kind\.([A-Z_]+),\s*([^,]+),', body):
        kind = match.group(1)
        machine = next((m for m, k in KIND_BY_MACHINE.items() if k == kind), None)
        if machine not in PRIMARY_CURATED_MACHINES:
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


def extract_equation(note: str, rx: dict | None) -> str:
    if rx:
        rx_note = (rx.get("note") or "").strip()
        if rx_note:
            eq = rx_note.split("；")[0].split(";")[0].strip()
            if "：" in eq:
                eq = eq.split("：", 1)[1].strip()
            return eq.replace("→", " -> ")
    if note and ("->" in note or "→" in note):
        eq = note.split("；")[0].split(";")[0].strip()
        if "：" in eq:
            eq = eq.split("：", 1)[1].strip()
        return eq.replace("→", " -> ")
    return ""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--spec", default=str(DEFAULT_SPEC))
    args = parser.parse_args()
    spec = pathlib.Path(args.spec)
    known = registered_ids()
    known_fluids = fluid_ids(spec)
    curated = curated_primary_items()
    rows = load("recipes.jsonl", spec)
    reactions_by_recipe = {
        rx["recipe"]: rx
        for rx in load("reactions.jsonl", spec)
        if isinstance(rx.get("recipe"), str)
    }

    entries = []
    skipped = {"retired": 0, "foreign_machine": 0, "fluid_only": 0, "no_item_input": 0,
               "too_many_inputs": 0, "unknown_item": 0, "unknown_fluid": 0,
               "curated_route": 0, "no_item_output": 0, "legacy_unbalanced": 0}
    seen: set[tuple] = set()

    for record in rows:
        if record.get("state") == "retired":
            skipped["retired"] += 1
            continue
        rec_id = str(record.get("id", ""))
        if rec_id in REJECTED_LEGACY_IDS:
            skipped["legacy_unbalanced"] += 1
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
        if (
            machine in STRICT_STOICHIOMETRIC_MACHINES
            and record.get("state") == "transcribed"
            and rec_id not in ALLOWED_TRANSCRIBED_IDS
        ):
            skipped["legacy_unbalanced"] += 1
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

        rx = reactions_by_recipe.get(rec_id)
        raw_cat = str(record.get("catalyst") or (rx.get("catalyst") if rx else "") or "").strip()
        catalyst_item = CATALYST_MAP.get(raw_cat, "")
        if catalyst_item.startswith("earth_on_minecraft:") and catalyst_item.split(":", 1)[1] not in known:
            catalyst_item = ""
        input_item_ids = {item for item, _ in item_inputs}
        if catalyst_item in input_item_ids:
            catalyst_item = ""

        temp_k = int(record.get("temperature_k") or (rx.get("temperature_k") if rx else 0) or 0)
        press_kpa = int(record.get("pressure_kpa") or (rx.get("pressure_kpa") if rx else 101) or 101)
        note = record.get("note") or ""
        equation = extract_equation(note, rx)
        if rec_id in SPECIFIED_REACTION_OVERRIDES:
            ov_cat, ov_temp, ov_press, ov_eq = SPECIFIED_REACTION_OVERRIDES[rec_id]
            if not catalyst_item and ov_cat and ov_cat not in input_item_ids:
                catalyst_item = ov_cat
            if temp_k <= 0:
                temp_k = ov_temp
            if press_kpa <= 101 and ov_press > 101:
                press_kpa = ov_press
            if not equation:
                equation = ov_eq

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
            "catalyst": catalyst_item,
            "temperatureK": temp_k,
            "pressureKpa": press_kpa,
            "equation": equation,
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
                "catalyst": "",
                "temperatureK": 0,
                "pressureKpa": 101,
                "equation": "",
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
        "                 String[] fluidOutputs, int[] fluidOutputAmounts,",
        "                 String catalyst, int temperatureK, int pressureKpa, String equation) {",
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
                'new String[]{%s}, new int[]{%s}, new String[]{%s}, new int[]{%s}, new String[]{%s}, new int[]{%s}, '
                '"%s", %d, %d, "%s"));'
                % (entry["kind"], entry["machine"], java_string(entry["source"]),
                   java_string(entry["note"]), entry["ticks"], entry["energy"], inputs, input_counts,
                   outputs, output_counts, fluid_inputs, fluid_input_amounts, fluid_outputs,
                   fluid_output_amounts, java_string(entry["catalyst"]), entry["temperatureK"],
                   entry["pressureKpa"], java_string(entry["equation"]))
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
