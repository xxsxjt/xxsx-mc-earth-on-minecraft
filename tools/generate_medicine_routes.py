#!/usr/bin/env python3
"""Emit medicine production routes for the `family:medicine` placeholders.

The spec's own catalog splits medicine into five tiers:

  1. 原料药中间体 (API intermediates) - phenol, toluene, glucose, salicylic acid.
  2. 治疗药物 (therapeutic drugs) - aspirin, paracetamol, antacid, cough syrup.
  3. 感染与伤口 (infection and wound care) - antiseptics, antibiotics, dressings.
  4. 麻醉与镇痛 (anaesthesia and analgesia) - morphine, lidocaine, chloroform.
  5. 慢性病与长期 (chronic conditions) - statins, antihypertensives, thyroxine.

Only items the spec actually ships are referenced; every material below was checked against
`spec/eom/items.jsonl`. Biologicals the spec scopes out stay as planned placeholders.
"""

from __future__ import annotations

import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = pathlib.Path(r"D:\Codex\WORKSPACES\Projects\mc-agent\spec\eom")
OUT = ROOT / "neoforge-26.2/src/main/resources/data/earth_on_minecraft/earth/quality/medicine_routes.json"

# Vanilla names the chain may reference.
VANILLA = {"sugar", "honey", "salt", "starch", "kelp", "glass_bottle"}

# Tier 1: API intermediates. (machine, [(material, count)], ticks, energy, note)
INTERMEDIATES = {
    "phenol": ("chemical_reactor", [("benzene", 1), ("sulfuric_acid", 1)], 120, 20,
               "cumene-style oxidation of benzene to phenol"),
    "acetic_anhydride": ("chemical_reactor", [("acetone", 2)], 100, 16,
                         "ketene route from acetone"),
    "acetylsalicylic_acid": ("chemical_reactor",
                             [("salicylic_acid", 1), ("acetic_anhydride", 1)], 120, 18,
                             "acetylate salicylic acid"),
    "glucose": ("fermenting_crock", [("starch", 2)], 300, 0,
                "enzymatic hydrolysis of starch"),
    "sucrose": ("filter_press", [("sugar", 2)], 60, 6, "refine and press cane sugar"),
    "potassium_iodide": ("chemical_reactor",
                         [("iodine", 1), ("potassium_chloride", 1)], 100, 14,
                         "halide exchange over iodine"),
    "toluene": ("distillation_column", [("coal_tar", 2)], 140, 24,
                "fractionate toluene out of coal tar"),
    "iodine": ("leaching_tank", [("kelp", 4)], 180, 12, "leach iodine from kelp"),
}

# Tier 2: therapeutic formulations.
THERAPEUTIC = {
    "aspirin": ("tablet_press", [("acetylsalicylic_acid", 2), ("starch", 1)], 60, 8),
    "paracetamol": ("chemical_reactor", [("p_aminophenol", 1), ("acetic_anhydride", 1)], 120, 16),
    "paracetamol_tablet": ("tablet_press", [("paracetamol", 2), ("starch", 1)], 60, 8),
    "ibuprofen": ("chemical_reactor", [("benzene", 1), ("acetic_anhydride", 1)], 160, 22),
    "antacid_tablet": ("tablet_press", [("calcium_phosphate", 2), ("starch", 1)], 60, 8),
    "calcium_tablet": ("tablet_press", [("calcium_phosphate", 2), ("starch", 1)], 60, 8),
    "vitamin_tablet": ("tablet_press", [("citric_acid", 1), ("starch", 1)], 60, 8),
    "caffeine_tablet": ("tablet_press", [("caffeine", 1), ("starch", 1)], 60, 8),
    "motion_sickness_pill": ("tablet_press", [("antihistamine", 1), ("starch", 1)], 60, 8),
    "antihistamine": ("chemical_reactor", [("phenol", 1), ("ammonia", 1)], 140, 18),
    "cough_syrup": ("mixer", [("honey", 2), ("citric_acid", 1), ("glycerol", 1)], 80, 8),
    "throat_lozenge": ("tablet_press", [("honey", 1), ("citric_acid", 1)], 50, 6),
    "oral_rehydration_salts": ("mixer",
                               [("salt", 1), ("sucrose", 1), ("potassium_chloride", 1)], 60, 6),
    "laxative": ("mixer", [("magnesium_hydroxide", 2), ("sucrose", 1)], 60, 6),
    "iron_supplement": ("tablet_press", [("calcium_phosphate", 1), ("starch", 1)], 60, 8),
    "melatonin": ("chemical_reactor", [("toluene", 1), ("ammonia", 1)], 160, 20),
    "saline_solution": ("mixer", [("distilled_water", 4), ("salt", 1)], 60, 6),
    "glucose_injection": ("mixer", [("distilled_water", 4), ("glucose", 2)], 60, 6),
    "hydrogen_peroxide_solution": ("mixer",
                                   [("distilled_water", 3), ("hydrogen_peroxide", 1)], 60, 6),
}

# Tier 3: infection and wound care.
WOUND = {
    "antiseptic_ethanol": ("mixer", [("ethanol", 3), ("distilled_water", 1)], 60, 6),
    "povidone_iodine": ("mixer", [("iodine", 1), ("soap_base", 2)], 80, 8),
    "antibiotic_ointment": ("mixer", [("penicillin", 1), ("wax", 2)], 80, 8),
    "burn_ointment": ("mixer", [("sulfanilamide", 1), ("wax", 2)], 80, 8),
    "styptic_powder": ("powder_press", [("alum", 2), ("calcium_sulfate", 1)], 60, 8),
    "sulfanilamide": ("chemical_reactor", [("aniline", 1), ("acetic_anhydride", 1)], 160, 20),
    "penicillin": ("fermenting_crock", [("glucose", 2), ("yeast", 1)], 600, 0),
    "tetracycline": ("fermenting_crock", [("glucose", 2), ("bone_broth", 1)], 600, 0),
    "streptomycin": ("fermenting_crock", [("glucose", 3), ("soybean", 2)], 600, 0),
    "chloramphenicol": ("chemical_reactor", [("benzene", 1), ("nitric_acid", 1)], 180, 24),
    "vancomycin": ("fermenting_crock", [("glucose", 3), ("soybean", 3)], 800, 0),
    "antifungal_agent": ("chemical_reactor",
                         [("phenol", 1), ("chlorine_gas_cell", 1)], 140, 18),
    "antiviral_agent": ("chemical_reactor", [("benzene", 1), ("ammonia", 1)], 180, 24),
    "antivenom": ("mixer", [("saline_solution", 2), ("bone_broth", 1)], 120, 10),
    "tetanus_antitoxin": ("mixer", [("saline_solution", 2), ("bone_broth", 1)], 120, 10),
    "suture_thread": ("loom", [("silk_thread", 2), ("antiseptic_ethanol", 1)], 100, 4),
    "bandage": ("loom", [("cotton_cloth", 2), ("antiseptic_ethanol", 1)], 80, 4),
    "syringe": ("anvil", [("glass_bottle", 1), ("steel", 1)], 100, 0),
    "scalpel": ("anvil", [("steel", 2)], 120, 0),
    "forceps": ("anvil", [("steel", 2)], 120, 0),
    "tourniquet": ("loom", [("cotton_cloth", 2), ("rope", 1)], 60, 4),
}

# Tier 4: anaesthesia and analgesia. Corydalis is the spec's real analgesic herb.
ANALGESIA = {
    "morphine": ("chemical_reactor", [("corydalis", 2), ("lime", 1)], 240, 26),
    "codeine": ("chemical_reactor", [("morphine", 1), ("methanol", 1)], 160, 20),
    "fentanyl": ("chemical_reactor", [("phenol", 1), ("aniline", 1)], 260, 30),
    "naloxone": ("chemical_reactor", [("morphine", 1), ("acetic_anhydride", 1)], 220, 26),
    "lidocaine": ("chemical_reactor", [("toluene", 1), ("ammonia", 1)], 200, 24),
    "chloroform": ("chemical_reactor", [("acetone", 1), ("chlorine_gas_cell", 1)], 140, 18),
    "diethyl_ether": ("distillation_column",
                      [("ethanol", 2), ("sulfuric_acid", 1)], 140, 18),
    "nitrous_oxide": ("industrial_kiln", [("ammonium_nitrate", 2)], 160, 20),
    "muscle_relaxant": ("chemical_reactor", [("glycerol", 1), ("ammonia", 1)], 160, 20),
}

# Tier 5: chronic condition management.
CHRONIC = {
    "anticoagulant": ("chemical_reactor",
                      [("salicylic_acid", 1), ("acetic_anhydride", 1)], 180, 22),
    "anticonvulsant": ("chemical_reactor", [("benzene", 1), ("urea", 1)], 180, 22),
    "antidepressant": ("chemical_reactor", [("toluene", 1), ("ammonia", 1)], 180, 22),
    "antidiabetic": ("chemical_reactor", [("sulfanilamide", 1), ("urea", 1)], 200, 24),
    "antihypertensive": ("chemical_reactor", [("phenol", 1), ("nitric_acid", 1)], 200, 24),
    "antipsychotic": ("chemical_reactor",
                      [("phenol", 1), ("chlorine_gas_cell", 1)], 200, 24),
    "bronchodilator": ("chemical_reactor", [("phenol", 1), ("ammonia", 1)], 180, 22),
    "corticosteroid": ("chemical_reactor",
                       [("glycerol", 1), ("acetic_anhydride", 1)], 220, 26),
    "immunosuppressant": ("fermenting_crock", [("glucose", 2), ("soybean", 2)], 700, 0),
    "statin": ("fermenting_crock", [("glucose", 2), ("oat", 2)], 700, 0),
    "thyroxine": ("chemical_reactor", [("iodine", 1), ("insect_protein", 1)], 200, 24),
    "chemotherapy_agent": ("chemical_reactor",
                           [("nitrotoluene", 1), ("benzene", 1)], 260, 30),
    "insulin": ("mixer", [("bone_broth", 1), ("saline_solution", 2)], 180, 16),
}


def item_ref(name: str) -> str:
    if name in VANILLA:
        return f"minecraft:{name}"
    return f"earth_on_minecraft:{name}"


def main() -> None:
    routes: list[dict] = []

    def add(route_id: str, machine: str, materials: list[tuple[str, int]], ticks: int,
            energy: int, note: str) -> None:
        routes.append({
            "id": route_id,
            "machine": machine,
            "inputs": [{"item": item_ref(name), "count": amount} for name, amount in materials],
            "outputs": [{"item": item_ref(route_id), "count": 1}],
            "ticks": ticks,
            "energy": energy,
            "note": note,
        })

    for name, (machine, materials, ticks, energy, note) in INTERMEDIATES.items():
        add(name, machine, materials, ticks, energy, note)
    for name, (machine, materials, ticks, energy) in THERAPEUTIC.items():
        add(name, machine, materials, ticks, energy, "therapeutic formulation")
    for name, (machine, materials, ticks, energy) in WOUND.items():
        add(name, machine, materials, ticks, energy, "wound care")
    for name, (machine, materials, ticks, energy) in ANALGESIA.items():
        add(name, machine, materials, ticks, energy, "anaesthesia and analgesia")
    for name, (machine, materials, ticks, energy) in CHRONIC.items():
        add(name, machine, materials, ticks, energy, "chronic condition management")

    routes.sort(key=lambda r: (r["machine"], r["id"]))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({
        "schema_version": 1,
        "source": str(SPEC),
        "routes": routes,
        "counts": {
            "total": len(routes),
            "machines": sorted({r["machine"] for r in routes}),
        },
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(f"MEDICINE_ROUTES_OK routes={len(routes)}")
    from collections import Counter
    for machine, count in Counter(r["machine"] for r in routes).most_common():
        print(f"  {machine:24} {count}")


if __name__ == "__main__":
    main()
