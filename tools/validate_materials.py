#!/usr/bin/env python3
"""Validate the versioned Earth on Minecraft material catalog without starting Minecraft."""

from __future__ import annotations

import json
import pathlib
import re
import sys


ROOT = pathlib.Path(__file__).resolve().parents[1]
CATALOG = ROOT / "neoforge-26.2" / "src" / "main" / "resources" / "data" / "earth_on_minecraft" / "materials"
SCHEMA_ID = "earth_on_minecraft:materials.schema.json"
ALLOWED_KINDS = {"element", "compound", "mineral", "rock", "deposit", "mixture", "alloy", "polymer", "manufactured", "soil", "gas"}
ALLOWED_BASES = {"ideal_formula", "end_member_range", "mass_fraction", "game_simplified", "mixed"}
ALLOWED_PHASES = {"solid", "liquid", "gas", "solution", "plasma", "mixed"}
ALLOWED_SIMPLIFICATIONS = {"none", "game_batch", "mixture", "composite", "concentrate"}
ALLOWED_CERTAINTY = {"well_established", "range_estimate", "game_simplified", "unknown"}
REQUIRED = {"schema_version", "id", "kind", "formula", "composition_basis", "phase", "constituents", "simplification", "certainty"}
FORMULA = re.compile(r"^[A-Za-z0-9().,+\-/·\s]+$")
ELEMENT = re.compile(r"[A-Z][a-z]?")

# Rounded standard atomic weights. The check only needs to reject a formula that
# cannot match its declared molar mass; it is not a thermodynamics database.
ATOMIC_WEIGHTS = {
    "H": 1.008, "B": 10.81, "C": 12.011, "N": 14.007, "O": 15.999, "F": 18.998,
    "Na": 22.990, "Mg": 24.305, "Al": 26.982, "Si": 28.085, "P": 30.974,
    "S": 32.06, "Cl": 35.45, "K": 39.098, "Ca": 40.078, "Ti": 47.867,
    "Cr": 51.996, "Mn": 54.938, "Fe": 55.845, "Co": 58.933, "Ni": 58.693,
    "Cu": 63.546, "Zn": 65.38, "As": 74.922, "Se": 78.971, "Br": 79.904,
    "Sr": 87.62, "Zr": 91.224, "Nb": 92.906, "Mo": 95.95, "Ag": 107.868,
    "Cd": 112.414, "Sn": 118.710, "Sb": 121.760, "Ba": 137.327, "La": 138.905,
    "Ce": 140.116, "Nd": 144.242, "W": 183.84, "Pt": 195.084, "Au": 196.967,
    "Hg": 200.592, "Pb": 207.2, "Th": 232.038, "U": 238.029,
}


def formula_weight(formula: str) -> float | None:
    """Molar mass of a plain formula, or None when it is a mixed description."""
    if any(marker in formula for marker in (" ", "+", ",", "/", "-", "·", ".")):
        return None
    total = 0.0
    index = 0
    while index < len(formula):
        match = ELEMENT.match(formula, index)
        if not match or match.group(0) not in ATOMIC_WEIGHTS:
            return None
        symbol = match.group(0)
        index = match.end()
        digits = ""
        while index < len(formula) and formula[index].isdigit():
            digits += formula[index]
            index += 1
        total += ATOMIC_WEIGHTS[symbol] * (int(digits) if digits else 1)
    return total if total > 0 else None


def fail(message: str) -> None:
    print(f"ERROR: {message}")
    sys.exit(1)


def validate_record(path: pathlib.Path, seen: set[str]) -> None:
    try:
        data = json.loads(path.read_text(encoding="utf-8-sig"))
    except Exception as exc:  # noqa: BLE001 - diagnostic script.
        fail(f"bad material json {path.name}: {exc}")
    if not isinstance(data, dict):
        fail(f"material record must be an object: {path.name}")
    missing = REQUIRED - data.keys()
    if missing:
        fail(f"material record missing fields {sorted(missing)}: {path.name}")
    uid = data["id"]
    if not isinstance(uid, str) or not re.fullmatch(r"earth_on_minecraft:[a-z0-9_/]+", uid):
        fail(f"bad material id in {path.name}: {uid!r}")
    if uid in seen:
        fail(f"duplicate material id {uid} in {path.name}")
    seen.add(uid)
    if data["kind"] not in ALLOWED_KINDS:
        fail(f"bad kind in {path.name}: {data['kind']!r}")
    if data["composition_basis"] not in ALLOWED_BASES:
        fail(f"bad composition_basis in {path.name}: {data['composition_basis']!r}")
    if data["phase"] not in ALLOWED_PHASES:
        fail(f"bad phase in {path.name}: {data['phase']!r}")
    if data["simplification"] not in ALLOWED_SIMPLIFICATIONS:
        fail(f"bad simplification in {path.name}: {data['simplification']!r}")
    if data["certainty"] not in ALLOWED_CERTAINTY:
        fail(f"bad certainty in {path.name}: {data['certainty']!r}")
    formula = data["formula"]
    if not isinstance(formula, str) or not formula.strip():
        fail(f"empty formula in {path.name}")
    if not FORMULA.fullmatch(formula):
        fail(f"formula contains unsupported characters in {path.name}: {formula!r}")
    if data["schema_version"] != 1:
        fail(f"unsupported material schema_version in {path.name}: {data['schema_version']!r}")
    constituents = data["constituents"]
    if not isinstance(constituents, list) or not constituents:
        fail(f"constituents must be a non-empty list: {path.name}")
    for entry in constituents:
        if not isinstance(entry, dict) or not isinstance(entry.get("species"), str) or not entry["species"].strip():
            fail(f"bad constituent in {path.name}: {entry!r}")
        if "fraction" in entry:
            fraction = entry["fraction"]
            if not isinstance(fraction, (int, float)) or isinstance(fraction, bool) or not 0 <= fraction <= 1:
                fail(f"constituent fraction outside 0..1 in {path.name}: {fraction!r}")
        if "fraction_min" in entry or "fraction_max" in entry:
            low = entry.get("fraction_min", 0)
            high = entry.get("fraction_max", 1)
            if not 0 <= low <= high <= 1:
                fail(f"constituent range invalid in {path.name}: {low!r}..{high!r}")
    molar_mass = data.get("molar_mass_g_mol")
    if molar_mass is not None and (not isinstance(molar_mass, (int, float)) or molar_mass <= 0):
        fail(f"molar mass must be positive in {path.name}: {molar_mass!r}")
    if molar_mass is not None and data["composition_basis"] == "ideal_formula":
        computed = formula_weight(formula)
        if computed is not None and abs(computed - float(molar_mass)) > max(0.5, computed * 0.01):
            fail(f"molar mass {molar_mass} does not match formula {formula} "
                 f"(computed {computed:.3f}) in {path.name}")
    if data["composition_basis"] == "ideal_formula":
        species = [entry["species"] for entry in constituents]
        if len(species) != 1 or species[0].replace(" ", "") != formula.replace(" ", ""):
            fail(f"ideal_formula record must have exactly one constituent equal to its formula "
                 f"in {path.name}: {species!r} vs {formula!r}")
    if data["composition_basis"] == "mass_fraction":
        total = sum(float(entry.get("fraction", 0)) for entry in constituents)
        if abs(total - 1.0) > 0.0001:
            fail(f"mass_fraction constituents must total 1.0 in {path.name}: {total:.6f}")


def main() -> None:
    if not CATALOG.is_dir():
        fail(f"material catalog directory does not exist: {CATALOG.relative_to(ROOT)}")
    schema_path = CATALOG / "materials.schema.json"
    try:
        schema = json.loads(schema_path.read_text(encoding="utf-8-sig"))
    except Exception as exc:  # noqa: BLE001 - diagnostic script.
        fail(f"cannot read material schema: {exc}")
    if schema.get("$id") != SCHEMA_ID:
        fail(f"material schema id mismatch: {schema.get('$id')!r}")
    records = sorted(path for path in CATALOG.glob("*.json") if path.name != "materials.schema.json")
    if not records:
        fail("material catalog has no records")
    seen: set[str] = set()
    for path in records:
        validate_record(path, seen)
    required_fixtures = {
        "earth_on_minecraft:magnetite": "Fe3O4",
        "earth_on_minecraft:chalcopyrite": "CuFeS2",
        "earth_on_minecraft:calcite": "CaCO3",
        "earth_on_minecraft:quartz": "SiO2",
        "earth_on_minecraft:orthoclase": "KAlSi3O8",
        "earth_on_minecraft:muscovite": "KAl2(AlSi3O10)(OH)2",
        "earth_on_minecraft:hematite": "Fe2O3",
        "earth_on_minecraft:spirit_stone_matrix":
            "SiO2 + KAlSi3O8 + KAl2(AlSi3O10)(OH)2 + Fe2O3",
    }
    found = {json.loads(path.read_text(encoding="utf-8-sig"))["id"]: json.loads(path.read_text(encoding="utf-8-sig"))["formula"] for path in records}
    for uid, formula in required_fixtures.items():
        if found.get(uid) != formula:
            fail(f"missing required material fixture {uid} -> {formula}; got {found.get(uid)!r}")
    spirit = next(json.loads(path.read_text(encoding="utf-8-sig")) for path in records
                  if json.loads(path.read_text(encoding="utf-8-sig"))["id"]
                  == "earth_on_minecraft:spirit_stone_matrix")
    fractions = {entry["species"]: entry["fraction"] for entry in spirit["constituents"]}
    expected = {"SiO2": 0.80, "KAlSi3O8": 0.15, "KAl2(AlSi3O10)(OH)2": 0.04, "Fe2O3": 0.01}
    if fractions != expected:
        fail(f"spirit stone matrix drifted from the Xuanhuan contract: {fractions!r}")
    if spirit["composition_basis"] != "mass_fraction":
        fail("spirit stone matrix must stay a mass_fraction mixture, not a formula")
    total_records = len(records)
    print(f"OK materialCatalog records={total_records} schema={SCHEMA_ID}")


if __name__ == "__main__":
    main()
