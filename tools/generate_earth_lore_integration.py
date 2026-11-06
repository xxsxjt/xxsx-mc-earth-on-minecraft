#!/usr/bin/env python3
"""Generate Earth on Minecraft's 107 earth_lore knowledge nodes, teaching data map, tags, and M-103/M-098 assets."""

from __future__ import annotations

import json
import math
import pathlib
import re
import sys
from collections import defaultdict

from PIL import Image, ImageDraw

EOM_ROOT = pathlib.Path(__file__).resolve().parents[1]
WORKSPACE_ROOT = EOM_ROOT.parents[1]
RES_ROOT = EOM_ROOT / "neoforge-26.2" / "src" / "main" / "resources"
ASSETS_ROOT = RES_ROOT / "assets" / "earth_on_minecraft"
DATA_ROOT = RES_ROOT / "data"

sys.path.insert(0, str(WORKSPACE_ROOT / "tools"))
sys.path.insert(0, str(EOM_ROOT / "tools"))

import knowledge_check as kc  # noqa: E402
import validate_resources as vr  # noqa: E402

CAP_PFX = "\u4f20\u4e16:"
READ_PFX = "\u8bfb\u6570:"
YIELD_PFX = "\u4ea7\u51fa:"
TEACHER_PFX = "\u5e08 "
COMMONSENSE_TOK = "\u5e38\u8bc6"
NOTES_TOK = "\u7b14\u8bb0"
SEC_CH = "\u00a7"

ALIAS_NS = {
    "eom": "earth_on_minecraft",
    "mc": "minecraft",
    "lore": "earth_lore",
    "life": "earth_life_sciences",
    "phys": "earth_physical_sciences",
    "xh": "xuanhuan_earth",
    "fe": "fantasy_earth",
    "theo": "earth_theology",
}

DISCIPLINE_EN = {
    "geo": "Geology",
    "che": "Chemistry",
    "mat": "Materials & Metallurgy",
    "eng": "Engineering",
    "agr": "Agronomy & Food",
    "med": "Basic Medicine",
}

DISCIPLINE_ZH = {
    "geo": "\u5730\u8d28\u5b66",
    "che": "\u5316\u5b66",
    "mat": "\u6750\u6599\u4e0e\u51b6\u91d1",
    "eng": "\u5de5\u7a0b\u5b66",
    "agr": "\u519c\u5b66\u4e0e\u98df\u54c1",
    "med": "\u57fa\u7840\u533b\u836f",
}

ENGLISH_NAMES = {
    "geology_basics": "Geological Commonsense",
    "mineral_identification": "Mineral Identification",
    "field_survey": "Field Survey & Triangulation",
    "stratigraphy": "Stratigraphy & Provinces",
    "ore_genesis": "Ore Genesis & Paragenesis",
    "economic_geology": "Economic Geology & Prospecting",
    "geophysics": "Geophysical Exploration",
    "gemology": "Gemology",
    "lapidary": "Lapidary & Faceting",
    "jade_carving": "Jade Carving & Seals",
    "hydrogeology": "Hydrogeology",
    "surveying": "Surveying & Navigation",
    "meteorology": "Meteorology",
    "geochemistry": "Geochemistry",
    "earth_system_science": "Earth System Science",
    "chemistry_basics": "Chemical Commonsense",
    "inorganic_chemistry": "Inorganic Chemistry",
    "reaction_engineering": "Reaction & Crystallization",
    "chlor_alkali": "Chlor-Alkali Industry",
    "sulfuric_acid_process": "Sulfuric Acid Process",
    "ammonia_and_fertilizer": "Synthetic Ammonia & Fertilizers",
    "separation_engineering": "Separation Engineering",
    "organic_chemistry": "Organic Chemistry",
    "petrochemistry": "Petrochemistry",
    "polymer_chemistry": "Polymer Chemistry",
    "water_chemistry": "Water Treatment Chemistry",
    "household_chemistry": "Household Chemistry & Dyeing",
    "analytical_chemistry": "Analytical Chemistry",
    "electrochemistry": "Electrochemistry",
    "radiochemistry": "Radiochemistry",
    "chemical_systems": "Chemical Systems Engineering",
    "materials_basics": "Materials Commonsense",
    "comminution": "Crushing & Grinding",
    "beneficiation": "Mineral Beneficiation",
    "pyrometallurgy": "Pyrometallurgy",
    "ironmaking_steelmaking": "Ironmaking & Steelmaking",
    "casting": "Foundry & Casting",
    "smithing": "Smithing & Toolmaking",
    "weaponsmithing": "Weaponsmithing",
    "armorsmithing": "Armorsmithing",
    "heat_treatment": "Quenching & Heat Treatment",
    "metal_forming": "Rolling & Wire Drawing",
    "nonferrous_metallurgy": "Nonferrous Metallurgy",
    "electrometallurgy": "Electrometallurgy & Light Metals",
    "alloy_steels": "Alloy Steels & Superalloys",
    "ceramics": "Ceramics & Brickmaking",
    "binders": "Lime & Cement Binders",
    "glassmaking": "Glassmaking & Glazing",
    "optical_glass": "Optical Glass Refining",
    "textiles": "Textiles & Leatherworking",
    "woodworking": "Woodworking & Joinery",
    "papermaking": "Papermaking & Printing",
    "hardware": "Architectural Hardware",
    "materials_science": "Materials Science Mastery",
    "engineering_basics": "Engineering Commonsense",
    "mechanics": "Applied Mechanics",
    "machine_elements": "Machine Elements & Vehicles",
    "measurement": "Metrology & Tolerances",
    "thermodynamics": "Engineering Thermodynamics",
    "electrical_basics": "Electrical Fundamentals",
    "steam_power": "Steam Power Engineering",
    "combustion_power": "Combustion Power Generation",
    "internal_combustion": "Internal Combustion & Transport",
    "power_grid": "Power Transmission Grid",
    "energy_storage": "Electrochemical Energy Storage",
    "nuclear_engineering": "Nuclear Thermal Engineering",
    "logistics": "Conveying & Material Handling",
    "control_basics": "Process Control & Automation",
    "robotics": "Industrial Robotics & Vision",
    "civil_engineering": "Civil & Structural Engineering",
    "timekeeping": "Horology & Calendars",
    "industrial_safety": "Industrial Safety & Protection",
    "systems_engineering": "Systems Engineering Mastery",
    "agriculture_basics": "Agricultural Commonsense",
    "tillage": "Tillage & Field Implements",
    "soil_science": "Soil Science & Amendments",
    "irrigation": "Hydraulic Irrigation",
    "husbandry": "Animal Husbandry",
    "apiculture_sericulture": "Apiculture & Sericulture",
    "aquaculture": "Aquaculture",
    "cooking": "Culinary Arts & Milling",
    "food_processing": "Food Preservation & Dairy",
    "fermentation": "Fermentation & Condiments",
    "sugar_and_oil": "Sugar Refining & Oil Pressing",
    "beverages": "Beverage Preparation",
    "tea_and_coffee": "Tea Curing & Coffee Roasting",
    "brewing": "Grain & Fruit Brewing",
    "distilling": "Spirit Distillation",
    "mixology": "Mixology & Bar Craft",
    "tobacco_curing": "Leaf Curing & Blending",
    "seed_selection": "Selective Seed Saving",
    "agronomy": "Agronomy Mastery",
    "medicine_basics": "Medical Commonsense",
    "first_aid": "Field First Aid",
    "materia_medica": "Materia Medica",
    "herbal_processing": "Herbal Preparation",
    "basic_pharmacy": "Basic Compounding Pharmacy",
    "sterile_technique": "Aseptic & Sterile Technique",
    "advanced_pharmacy": "Dosage Forms & Formulations",
    "wound_care": "Trauma & Wound Care",
    "sulfa_drugs": "Sulfa Drugs & Antibacterials",
    "serum_therapy": "Antitoxins & Serum Therapy",
    "anesthesia_basics": "Volatile Anesthetics",
    "nursing": "Ward Care & Nursing",
    "public_health": "Sanitation & Public Health",
    "veterinary": "Veterinary Care",
    "pharmacy_mastery": "Basic Pharmacy Mastery",
}

SPECIAL_SLUG_OVERRIDES = {
    "lime_kiln": "lime_kiln_controller",
    "glass_kiln": "glass_kiln_controller",
}

NUCLEAR_FUEL_SLUGS = [
    "yellowcake",
    "uranium_trioxide",
    "uranium_dioxide_powder",
    "uranium_tetrafluoride",
    "uranium_hexafluoride_cell",
    "low_enriched_uranium",
    "depleted_uranium",
    "nuclear_fuel_pellet",
    "nuclear_fuel_rod",
    "nuclear_fuel_assembly",
]


def expand_ref(raw: str) -> str:
    prefix = "#" if raw.startswith("#") else ""
    body = raw[1:] if prefix else raw
    alias, _, path = body.partition(":")
    ns = ALIAS_NS.get(alias, alias)
    return f"{prefix}{ns}:{path}"


def write_json(path: pathlib.Path, obj: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def build_unlocks(
    n,
    entries: dict,
    catalog_rows: dict,
    item_set: set[str],
    block_set: set[str],
    seen_unlock_keys: set[str],
) -> list[dict[str, str]]:
    out: list[dict[str, str]] = []
    local_keys: set[str] = set()

    def add_unlock(kind: str, val: str) -> None:
        key = f"capstone:{val}@earth_on_minecraft" if kind == "capstone" else f"{kind}:{val}"
        if key in local_keys:
            return
        if key in seen_unlock_keys:
            # Disambiguate cross-node overlap (such as magnetite_ore in K-AGR-00 herbs vs K-GEO-00)
            alt_val = f"earth_on_minecraft:{n.code.lower()}_{n.slug}/{val.split(':', 1)[-1]}"
            alt_key = f"ability:{alt_val}"
            if alt_key not in local_keys and alt_key not in seen_unlock_keys:
                local_keys.add(alt_key)
                seen_unlock_keys.add(alt_key)
                out.append({"ability": alt_val})
            return
        local_keys.add(key)
        seen_unlock_keys.add(key)
        out.append({kind: val})

    def emit_slug(slug: str) -> None:
        mapped = SPECIAL_SLUG_OVERRIDES.get(slug, slug)
        full = f"earth_on_minecraft:{mapped}"
        item_ok = mapped in item_set and f"item:{full}" not in seen_unlock_keys
        block_ok = mapped in block_set and f"block:{full}" not in seen_unlock_keys
        if item_ok:
            add_unlock("item", full)
            if block_ok:
                add_unlock("block", full)
        elif block_ok:
            add_unlock("block", full)
        else:
            add_unlock("ability", f"earth_on_minecraft:{n.code.lower()}_{n.slug}/{slug}")

    for tok in n.unlocks:
        if tok.startswith(CAP_PFX):
            code = tok[len(CAP_PFX):].strip().lower()
            add_unlock("capstone", f"earth_lore:{code}")
            continue
        if tok.startswith(READ_PFX):
            eid = tok[len(READ_PFX):].strip()
            if eid == "M-DATA-02":
                add_unlock("ability", "earth_on_minecraft:readout/material_batch")
            else:
                e = entries[eid]
                assert e.slug and e.slug in item_set, f"Readout item missing: {eid} -> {e.slug}"
                add_unlock("readout", f"earth_on_minecraft:{e.slug}")
            continue
        if tok.startswith(YIELD_PFX):
            eid = tok[len(YIELD_PFX):].strip()
            e = entries[eid]
            assert e.slug and e.slug in block_set, f"Yield block missing: {eid} -> {e.slug}"
            add_unlock("yield", f"earth_on_minecraft:{e.slug}")
            continue
        if tok.startswith("cat:"):
            mm = re.match(r"^cat:([a-z-]+)" + SEC_CH + r"([^:]+)(?::(.+))?$", tok)
            assert mm is not None, f"Bad cat ref: {tok}"
            stem, sec, subset = mm.group(1), mm.group(2), mm.group(3)
            rows = catalog_rows[(stem, sec)]
            picked = subset.split("+") if subset else list(rows)
            for s in picked:
                if rows.get(s) == "eom":
                    emit_slug(s)
            continue

        eid, _, subset = tok.partition(":")
        e = entries[eid]
        if subset:
            for s in subset.split("+"):
                emit_slug(s)
        elif e.slug:
            emit_slug(e.slug)
        else:
            if eid == "M-101":
                emit_slug("gem_cutter")
            elif eid == "M-044":
                emit_slug("bloomery_controller")
            elif eid == "M-045":
                emit_slug("shaft_furnace_controller")
            elif eid == "M-046":
                emit_slug("bread_oven_controller")
            elif eid == "M-068":
                for ns_slug in NUCLEAR_FUEL_SLUGS:
                    emit_slug(ns_slug)
                add_unlock("ability", "earth_on_minecraft:nuclear_fuel_cycle")
            elif eid == "M-100":
                add_unlock("ability", "earth_on_minecraft:gem_deposits")
            elif eid == "M-026":
                add_unlock("ability", "earth_on_minecraft:associated_minerals")
            else:
                add_unlock("ability", f"earth_on_minecraft:{eid.lower()}")

    return out


def make_descriptions(n) -> tuple[str, str]:
    code = n.code.lower()
    disc_en = DISCIPLINE_EN[code]
    disc_zh = DISCIPLINE_ZH[code]
    en_title = ENGLISH_NAMES.get(n.slug, n.slug.replace("_", " ").title())
    if n.gate == "root":
        return (
            f"Foundational {disc_en.lower()} commonsense known to all survivors from the start.",
            f"\u4e0e\u751f\u4ff1\u6765\u7684{disc_zh}\u57fa\u7840\u5e38\u8bc6\uff0c\u65e0\u9700\u7814\u8bfb\u5373\u53ef\u638c\u63e1\u3002",
        )
    if n.gate == "cap":
        return (
            f"Capstone mastery of {disc_en.lower()}, lifting soft quality caps across the discipline up to Heirloom grade.",
            f"{disc_zh}\u7684\u4f20\u4e16\u7cbe\u7814\u603b\u7eb2\uff0c\u89e3\u9664\u672c\u5b66\u79d1\u5185\u54c1\u8d28\u4e0a\u9650\u5e76\u5141\u8bb8\u4ea7\u51fa\u4f20\u4e16\u54c1\u7ea7\u6210\u679c\u3002",
        )
    if n.gate == "hard":
        return (
            f"Tier {n.tier} {disc_en.lower()} strict prerequisite required before operating or crafting {en_title.lower()} equipment.",
            f"\u7b2c {n.tier} \u5c42{disc_zh}\u786c\u95e8\u7981\u77e5\u8bc6\uff1a\u672a\u638c\u63e1\u300c{n.name}\u300d\u524d\uff0c\u5bf9\u5e94\u4eea\u5668\u4e0e\u5de5\u827a\u8def\u7ebf\u4fdd\u6301\u9501\u5b9a\u3002",
        )
    if n.gate == "theory":
        return (
            f"Tier {n.tier} {disc_en.lower()} theory unlocking quantitative instrument readouts and downstream {en_title.lower()} research.",
            f"\u7b2c {n.tier} \u5c42{disc_zh}\u7406\u8bba\u8282\u70b9\uff1a\u638c\u63e1\u300c{n.name}\u300d\u4ee5\u89e3\u9501\u5b9a\u91cf\u5206\u6790\u8bfb\u6570\u4e0e\u540e\u7eed\u5de5\u827a\u7814\u7a76\u3002",
        )
    return (
        f"Tier {n.tier} {disc_en.lower()} craft lifting quality caps from Common to Masterwork for {en_title.lower()} products.",
        f"\u7b2c {n.tier} \u5c42{disc_zh}\u5de5\u827a\u8282\u70b9\uff1a\u638c\u63e1\u300c{n.name}\u300d\u540e\uff0c\u76f8\u5173\u5236\u54c1\u54c1\u8d28\u4e0a\u9650\u7531\u51e1\u54c1\u63d0\u5347\u81f3\u7cbe\u5de5\u3002",
    )


def make_autoclave_textures() -> None:
    tex_dir = ASSETS_ROOT / "textures" / "block"
    tex_dir.mkdir(parents=True, exist_ok=True)

    # Base brushed stainless steel casing (128x128)
    def base_panel() -> Image.Image:
        img = Image.new("RGBA", (128, 128), (92, 100, 108, 255))
        px = img.load()
        for y in range(128):
            shade = int(6 * math.sin(y * 0.21))
            for x in range(128):
                grain = ((x * 17 + y * 31) % 9) - 4
                r = max(40, min(220, 106 + shade + grain))
                g = max(44, min(225, 114 + shade + grain))
                b = max(48, min(230, 122 + shade + grain))
                px[x, y] = (r, g, b, 255)
        draw = ImageDraw.Draw(img)
        draw.rectangle((0, 0, 127, 127), outline=(52, 58, 64, 255), width=4)
        draw.rectangle((4, 4, 123, 123), outline=(146, 156, 164, 255), width=2)
        for rx, ry in ((10, 10), (114, 10), (10, 114), (114, 114)):
            draw.ellipse((rx - 3, ry - 3, rx + 3, ry + 3), fill=(62, 68, 74, 255), outline=(156, 164, 172, 255))
        return img

    # Top: steam relief valve and copper manifold
    top = base_panel()
    dt = ImageDraw.Draw(top)
    dt.ellipse((34, 34, 94, 94), fill=(76, 84, 92, 255), outline=(44, 50, 56, 255), width=3)
    dt.ellipse((48, 48, 80, 80), fill=(168, 112, 62, 255), outline=(96, 58, 28, 255), width=3)
    dt.rectangle((61, 24, 67, 104), fill=(186, 128, 74, 255))
    dt.rectangle((24, 61, 104, 67), fill=(186, 128, 74, 255))
    top.save(tex_dir / "autoclave_top.png")

    # Side: insulated ribs and copper steam return pipe
    side = base_panel()
    ds = ImageDraw.Draw(side)
    for y in (28, 48, 68, 88):
        ds.rectangle((14, y, 114, y + 8), fill=(78, 86, 94, 255), outline=(54, 60, 66, 255))
    ds.rectangle((96, 16, 108, 112), fill=(176, 116, 64, 255), outline=(98, 60, 30, 255), width=2)
    side.save(tex_dir / "autoclave_side.png")

    # Front (idle & active): circular pressure door, locking handwheel, pressure gauge, indicator lamp
    for active in (False, True):
        front = base_panel()
        df = ImageDraw.Draw(front)
        # Top control bar with gauge and lamp
        df.rectangle((14, 12, 114, 34), fill=(58, 64, 72, 255), outline=(38, 42, 48, 255), width=2)
        # Pressure gauge
        df.ellipse((20, 14, 38, 32), fill=(236, 240, 232, 255), outline=(168, 112, 62, 255), width=2)
        if active:
            df.line((29, 23, 35, 18), fill=(214, 48, 38, 255), width=2)
        else:
            df.line((29, 23, 23, 27), fill=(48, 56, 64, 255), width=2)
        # Indicator lamp
        lamp_fill = (82, 236, 168, 255) if active else (64, 82, 78, 255)
        lamp_ring = (188, 255, 224, 255) if active else (42, 48, 54, 255)
        df.ellipse((94, 16, 108, 30), fill=lamp_fill, outline=lamp_ring, width=2)
        # Steam sight window glow in center door
        df.ellipse((24, 40, 104, 118), fill=(82, 90, 98, 255), outline=(44, 48, 54, 255), width=4)
        df.ellipse((32, 48, 96, 110), fill=(122, 132, 140, 255), outline=(66, 72, 80, 255), width=3)
        viewport_fill = (166, 228, 236, 255) if active else (72, 96, 108, 255)
        df.ellipse((48, 64, 80, 94), fill=viewport_fill, outline=(48, 54, 60, 255), width=3)
        # Radial locking dogs + handwheel spokes
        for angle_deg in (0, 60, 120, 180, 240, 300):
            rad = math.radians(angle_deg)
            x1 = int(64 + 28 * math.cos(rad))
            y1 = int(79 + 28 * math.sin(rad))
            x2 = int(64 + 41 * math.cos(rad))
            y2 = int(79 + 41 * math.sin(rad))
            df.line((x1, y1, x2, y2), fill=(52, 56, 62, 255), width=4)
        df.ellipse((57, 72, 71, 86), fill=(174, 116, 62, 255), outline=(92, 56, 26, 255), width=2)
        name = "autoclave_front_active.png" if active else "autoclave_front.png"
        front.save(tex_dir / name)


def make_item_icons() -> None:
    item_tex_dir = ASSETS_ROOT / "textures" / "item"
    item_tex_dir.mkdir(parents=True, exist_ok=True)

    # 1. optical_glass.png (128x128 RGBA, precision ground lens blank with cyan/white specular highlights)
    opt = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    do = ImageDraw.Draw(opt)
    # Beveled ground optical disc inside [18..110]
    do.ellipse((18, 26, 110, 108), fill=(88, 156, 184, 235), outline=(36, 78, 98, 255), width=4)
    do.ellipse((22, 20, 106, 100), fill=(156, 222, 242, 245), outline=(62, 124, 148, 255), width=3)
    do.ellipse((30, 28, 98, 92), fill=(202, 244, 252, 255), outline=(128, 198, 222, 255), width=2)
    # Specular streaks
    do.polygon([(38, 44), (62, 30), (72, 36), (44, 54)], fill=(248, 254, 255, 255))
    do.polygon([(56, 76), (82, 58), (88, 64), (62, 82)], fill=(236, 252, 255, 240))
    opt.save(item_tex_dir / "optical_glass.png")

    # 2. crushed_magnetite.png (128x128 RGBA, angular dark iron-oxide gravel cluster inside [18..110])
    cm = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    dc = ImageDraw.Draw(cm)
    shards = [
        ([(22, 78), (38, 52), (62, 58), (56, 88), (30, 94)], (54, 58, 66, 255), (96, 104, 116, 255)),
        ([(52, 46), (76, 26), (102, 44), (92, 72), (62, 68)], (46, 50, 58, 255), (112, 120, 134, 255)),
        ([(44, 74), (74, 64), (106, 80), (94, 104), (54, 104)], (62, 66, 74, 255), (124, 132, 146, 255)),
        ([(28, 50), (48, 32), (66, 42), (52, 58)], (70, 74, 84, 255), (138, 146, 160, 255)),
    ]
    for poly, fill, hi in shards:
        dc.polygon(poly, fill=fill, outline=(26, 28, 34, 255))
        dc.line([poly[0], poly[1], poly[2]], fill=hi, width=3)
    # Metallic magnetite glints
    for gx, gy in ((44, 62), (76, 44), (72, 82), (56, 90), (88, 56)):
        dc.rectangle((gx, gy, gx + 4, gy + 4), fill=(168, 178, 192, 255))
    cm.save(item_tex_dir / "crushed_magnetite.png")


def generate_assets_and_data(block_ids: list[str], stack_item_ids: list[str]) -> None:
    make_autoclave_textures()
    make_item_icons()

    # Blockstate & models for autoclave
    rotations = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for facing, y_rot in rotations.items():
        for active in ("false", "true"):
            model_ref = (
                "earth_on_minecraft:block/autoclave_active"
                if active == "true"
                else "earth_on_minecraft:block/autoclave"
            )
            entry: dict[str, object] = {"model": model_ref}
            if y_rot:
                entry["y"] = y_rot
            variants[f"facing={facing},active={active}"] = entry
    write_json(ASSETS_ROOT / "blockstates" / "autoclave.json", {"variants": variants})

    write_json(
        ASSETS_ROOT / "models" / "block" / "autoclave.json",
        {
            "parent": "minecraft:block/orientable",
            "textures": {
                "top": "earth_on_minecraft:block/autoclave_top",
                "front": "earth_on_minecraft:block/autoclave_front",
                "side": "earth_on_minecraft:block/autoclave_side",
            },
        },
    )
    write_json(
        ASSETS_ROOT / "models" / "block" / "autoclave_active.json",
        {
            "parent": "minecraft:block/orientable",
            "textures": {
                "top": "earth_on_minecraft:block/autoclave_top",
                "front": "earth_on_minecraft:block/autoclave_front_active",
                "side": "earth_on_minecraft:block/autoclave_side",
            },
        },
    )
    write_json(
        ASSETS_ROOT / "items" / "autoclave.json",
        {"model": {"type": "minecraft:model", "model": "earth_on_minecraft:block/autoclave"}},
    )

    for item_id in ("optical_glass", "crushed_magnetite"):
        write_json(
            ASSETS_ROOT / "items" / f"{item_id}.json",
            {"model": {"type": "minecraft:model", "model": f"earth_on_minecraft:item/{item_id}"}},
        )
        write_json(
            ASSETS_ROOT / "models" / "item" / f"{item_id}.json",
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"earth_on_minecraft:item/{item_id}"}},
        )

    # Loot table for autoclave
    write_json(
        DATA_ROOT / "earth_on_minecraft" / "loot_table" / "blocks" / "autoclave.json",
        {
            "type": "minecraft:block",
            "pools": [
                {
                    "rolls": 1,
                    "bonus_rolls": 0,
                    "entries": [{"type": "minecraft:item", "name": "earth_on_minecraft:autoclave"}],
                    "conditions": [{"condition": "minecraft:survives_explosion"}],
                }
            ],
        },
    )

    # Mineable with pickaxe tag
    pickaxe_tag_path = DATA_ROOT / "minecraft" / "tags" / "block" / "mineable" / "pickaxe.json"
    if pickaxe_tag_path.exists():
        pick_data = json.loads(pickaxe_tag_path.read_text(encoding="utf-8-sig"))
        vals = pick_data.get("values", [])
        if "earth_on_minecraft:autoclave" not in vals:
            vals.append("earth_on_minecraft:autoclave")
            pick_data["values"] = vals
            write_json(pickaxe_tag_path, pick_data)

    # Material catalog JSONs for optical_glass & crushed_magnetite
    mat_dir = DATA_ROOT / "earth_on_minecraft" / "materials"
    write_json(
        mat_dir / "optical_glass.json",
        {
            "schema_version": 1,
            "id": "earth_on_minecraft:optical_glass",
            "kind": "manufactured",
            "formula": "SiO2 + B2O3 + Na2O + K2O",
            "composition_basis": "mass_fraction",
            "phase": "solid",
            "constituents": [
                {"species": "SiO2", "fraction": 0.70},
                {"species": "B2O3", "fraction": 0.14},
                {"species": "Na2O", "fraction": 0.10},
                {"species": "K2O", "fraction": 0.06},
            ],
            "source": ["fining_melt"],
            "simplification": "game_batch",
            "certainty": "well_established",
            "display_key": "material.earth_on_minecraft.optical_glass",
            "notes": "Refined borosilicate crown optical glass blank fined for low bubble count.",
        },
    )
    write_json(
        mat_dir / "crushed_magnetite.json",
        {
            "schema_version": 1,
            "id": "earth_on_minecraft:crushed_magnetite",
            "kind": "mixture",
            "formula": "Fe3O4 + SiO2 + Fe2O3",
            "composition_basis": "mass_fraction",
            "phase": "solid",
            "constituents": [
                {"species": "Fe3O4", "fraction": 0.74},
                {"species": "SiO2", "fraction": 0.18},
                {"species": "Fe2O3", "fraction": 0.08},
            ],
            "source": ["crushing"],
            "simplification": "game_batch",
            "certainty": "well_established",
            "display_key": "material.earth_on_minecraft.crushed_magnetite",
            "notes": "Coarsely crushed magnetite feed ready for beneficiation and magnetic separation.",
        },
    )

    # Survival crafting recipes for early-game field tools, autoclave, and crushed_magnetite
    rec_dir = DATA_ROOT / "earth_on_minecraft" / "recipe"
    write_json(
        rec_dir / "hand_lens.json",
        {
            "type": "minecraft:crafting_shaped",
            "category": "equipment",
            "pattern": [" G ", " C ", " S "],
            "key": {
                "G": "minecraft:glass_pane",
                "C": "minecraft:copper_ingot",
                "S": "minecraft:stick",
            },
            "result": {"id": "earth_on_minecraft:hand_lens", "count": 1},
        },
    )
    write_json(
        rec_dir / "survey_stake.json",
        {
            "type": "minecraft:crafting_shaped",
            "category": "equipment",
            "pattern": ["P", "S", "S"],
            "key": {
                "P": "minecraft:oak_planks",
                "S": "minecraft:stick",
            },
            "result": {"id": "earth_on_minecraft:survey_stake", "count": 4},
        },
    )
    write_json(
        rec_dir / "sample_bag.json",
        {
            "type": "minecraft:crafting_shaped",
            "category": "equipment",
            "pattern": ["S S", "P P", " P "],
            "key": {
                "S": "minecraft:string",
                "P": "minecraft:paper",
            },
            "result": {"id": "earth_on_minecraft:sample_bag", "count": 2},
        },
    )
    write_json(
        rec_dir / "autoclave.json",
        {
            "type": "minecraft:crafting_shaped",
            "category": "misc",
            "pattern": ["SCS", "GFG", "SCS"],
            "key": {
                "S": "earth_on_minecraft:steel",
                "C": "minecraft:copper_ingot",
                "G": "minecraft:glass",
                "F": "minecraft:furnace",
            },
            "result": {"id": "earth_on_minecraft:autoclave", "count": 1},
        },
    )
    write_json(
        rec_dir / "crushed_magnetite_from_chunk.json",
        {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "ingredients": [
                "earth_on_minecraft:magnetite_chunk",
                "earth_on_minecraft:magnetite_chunk",
            ],
            "result": {"id": "earth_on_minecraft:crushed_magnetite", "count": 2},
        },
    )

    # earth_lore:records_owner block tag (all EOM processing machines, generators, battery, bore_head, quarry, autoclave)
    java_entry = (
        EOM_ROOT
        / "neoforge-26.2"
        / "src"
        / "main"
        / "java"
        / "com"
        / "xxsx"
        / "earthonminecraft"
        / "EarthOnMinecraft.java"
    ).read_text(encoding="utf-8")
    machine_blocks = re.findall(r'machineBlock\("([a-z0-9_]+)"', java_entry)
    extra_owned = [
        "combustion_generator",
        "steam_turbine_generator",
        "battery_box",
        "bore_head",
        "quarry",
        "autoclave",
    ]
    owner_blocks = sorted({f"earth_on_minecraft:{b}" for b in machine_blocks + extra_owned if b in block_ids})
    write_json(
        DATA_ROOT / "earth_lore" / "tags" / "block" / "records_owner.json",
        {"replace": False, "values": owner_blocks},
    )

    # earth_on_minecraft:sterilizable item tag
    candidate_sterilizable = [
        "syringe",
        "scalpel",
        "forceps",
        "suture_thread",
        "bandage",
        "tourniquet",
        "medical_kit",
        "vet_kit",
        "glass_bottle",
        "reagent_bottle",
        "graduated_cylinder",
        "beaker",
        "flask",
        "test_tube",
    ]
    sterilizable_items = [f"earth_on_minecraft:{i}" for i in candidate_sterilizable if i in stack_item_ids]
    sterilizable_items.append("minecraft:glass_bottle")
    write_json(
        DATA_ROOT / "earth_on_minecraft" / "tags" / "item" / "sterilizable.json",
        {"replace": False, "values": sterilizable_items},
    )


def update_lang_files(eom_nodes: list) -> None:
    lang_dir = ASSETS_ROOT / "lang"
    en_path = lang_dir / "en_us.json"
    zh_path = lang_dir / "zh_cn.json"
    en = json.loads(en_path.read_text(encoding="utf-8-sig"))
    zh = json.loads(zh_path.read_text(encoding="utf-8-sig"))

    en_updates = {
        "block.earth_on_minecraft.autoclave": "Steam Autoclave",
        "item.earth_on_minecraft.autoclave": "Steam Autoclave",
        "item.earth_on_minecraft.optical_glass": "Optical Glass Blank",
        "item.earth_on_minecraft.crushed_magnetite": "Crushed Magnetite",
        "material.earth_on_minecraft.optical_glass": "Optical Glass",
        "material.earth_on_minecraft.crushed_magnetite": "Crushed Magnetite",
        "tooltip.earth_on_minecraft.automation.autoclave": "Pressurized steam sterilizer (8 EOU/t, 120 ticks) for surgical and laboratory ware.",
        "tooltip.earth_on_minecraft.sterile": "Sterile (Autoclaved)",
        "tooltip.earth_on_minecraft.optical_glass": "Optical Index n=%s | Bubbles: %s",
        "tooltip.earth_on_minecraft.optical_glass.bubbles_high": "High bubble inclusion: unsuitable for precision lenses",
        "tooltip.earth_on_minecraft.chem.detail.mineral": "Mineral Specimen",
        "message.earth_on_minecraft.hand_lens.empty": "Hold a mineral or material sample in your other hand to inspect it.",
        "message.earth_on_minecraft.hand_lens.category_only": "Hand lens observation: %s (study Mineral Identification for quantitative assay).",
        "message.earth_on_minecraft.hand_lens.detail_plain": "Hand lens: %s (%s).",
        "message.earth_on_minecraft.hand_lens.detail": "Hand lens: %s (%s) | Purity %s | Moisture %s | Grain %s um | %s",
        "message.earth_on_minecraft.survey_stake.category_none": "Survey stake: no main ore deposit projection detected nearby.",
        "message.earth_on_minecraft.survey_stake.category_present": "Survey stake: standing within a mineral deposit zone (study Field Survey for bearing and depth).",
        "message.earth_on_minecraft.survey_stake.none": "Survey stake: no main ore body within 4 chunks.",
        "message.earth_on_minecraft.survey_stake.detail": "Survey stake: %s to the %s (~%sm, %s, reserve %s).",
        "message.earth_on_minecraft.geology_hammer.category_present": "Rock strike echoes a mineralized body nearby (study Economic Geology for bearing, depth, and reserve).",
        "message.earth_on_minecraft.autoclave.not_sterilizable": "Only sterilizable instruments and glassware can be loaded into the autoclave.",
        "message.earth_on_minecraft.autoclave.status": "Autoclave cycle: %s/%s ticks | Buffer: %s EOU",
        "message.earth_on_minecraft.autoclave.fault.pressure_low": "Autoclave fault: steam pressure dropped (insufficient power, 8 EOU/t required).",
        "message.earth_on_minecraft.autoclave.fault.cycle_incomplete": "Autoclave fault: sterilization cycle interrupted before completion.",
        "message.earth_on_minecraft.autoclave.fault.lore_locked": "Autoclave fault: operator lacks required knowledge.",
        "screen.earth_on_minecraft.notebook.stratigraphy_locked": "Study Stratigraphy & Provinces (K-GEO-03) to interpret host provinces, companion minerals, and depth bands.",
        "screen.earth_on_minecraft.notebook.stratigraphy_redacted": "[Stratigraphic details require K-GEO-03]",
    }

    zh_updates = {
        "block.earth_on_minecraft.autoclave": "\u9ad8\u538b\u84b8\u6c7d\u706d\u83cc\u91dc",
        "item.earth_on_minecraft.autoclave": "\u9ad8\u538b\u84b8\u6c7d\u706d\u83cc\u91dc",
        "item.earth_on_minecraft.optical_glass": "\u5149\u5b66\u73bb\u7483\u6bdb\u576f",
        "item.earth_on_minecraft.crushed_magnetite": "\u7c97\u788e\u78c1\u94c1\u77ff",
        "material.earth_on_minecraft.optical_glass": "\u5149\u5b66\u73bb\u7483",
        "material.earth_on_minecraft.crushed_magnetite": "\u7c97\u788e\u78c1\u94c1\u77ff",
        "tooltip.earth_on_minecraft.automation.autoclave": "\u9ad8\u538b\u9971\u548c\u84b8\u6c7d\u706d\u83cc\u8bbe\u5907\uff088 EOU/t\uff0c120 \u523b\uff09\uff0c\u7528\u4e8e\u533b\u7597\u5668\u68b0\u4e0e\u5b9e\u9a8c\u5668\u76bf\u65e0\u83cc\u5904\u7406\u3002",
        "tooltip.earth_on_minecraft.sterile": "\u5df2\u706d\u83cc\uff08\u65e0\u83cc\u72b6\u6001\uff09",
        "tooltip.earth_on_minecraft.optical_glass": "\u6298\u5c04\u7387 n=%s | \u6c14\u6ce1\u6570\uff1a%s",
        "tooltip.earth_on_minecraft.optical_glass.bubbles_high": "\u6c14\u6ce1\u504f\u591a\uff1a\u4e0d\u5b9c\u7528\u4e8e\u7cbe\u5bc6\u900f\u955c\u7814\u78e8",
        "tooltip.earth_on_minecraft.chem.detail.mineral": "\u77ff\u7269\u6807\u672c",
        "message.earth_on_minecraft.hand_lens.empty": "\u8bf7\u5728\u53e6\u4e00\u53ea\u624b\u6301\u63e1\u77ff\u7269\u6216\u6750\u6599\u6837\u54c1\u8fdb\u884c\u89c2\u5bdf\u3002",
        "message.earth_on_minecraft.hand_lens.category_only": "\u653e\u5927\u955c\u521d\u68c0\uff1a%s\uff08\u7814\u8bfb\u300c\u77ff\u7269\u9274\u5b9a\u300d\u540e\u53ef\u8bfb\u53d6\u7eaf\u5ea6\u3001\u7c92\u5f84\u4e0e\u54c1\u7ea7\uff09\u3002",
        "message.earth_on_minecraft.hand_lens.detail_plain": "\u653e\u5927\u955c\u9274\u5b9a\uff1a%s\uff08%s\uff09\u3002",
        "message.earth_on_minecraft.hand_lens.detail": "\u653e\u5927\u955c\u9274\u5b9a\uff1a%s\uff08%s\uff09| \u7eaf\u5ea6 %s | \u6c34\u5206 %s | \u7c92\u5f84 %s um | %s",
        "message.earth_on_minecraft.survey_stake.category_none": "\u6d4b\u6869\u8e0f\u52d8\uff1a\u9644\u8fd1\u672a\u53d1\u73b0\u4e3b\u77ff\u4f53\u6295\u5f71\u3002",
        "message.earth_on_minecraft.survey_stake.category_present": "\u6d4b\u6869\u8e0f\u52d8\uff1a\u4f4d\u4e8e\u77ff\u5e8a\u6295\u5f71\u8303\u56f4\u5185\uff08\u7814\u8bfb\u300c\u91ce\u5916\u8e0f\u52d8\u300d\u540e\u53ef\u6d4b\u5b9a\u65b9\u4f4d\u3001\u8ddd\u79bb\u4e0e\u57cb\u6df1\uff09\u3002",
        "message.earth_on_minecraft.survey_stake.none": "\u6d4b\u6869\u6d4b\u91cf\uff1a4 \u533a\u5757\u534a\u5f84\u5185\u65e0\u4e3b\u77ff\u4f53\u3002",
        "message.earth_on_minecraft.survey_stake.detail": "\u6d4b\u6869\u6d4b\u91cf\uff1a%s \u4f4d\u4e8e%s\u65b9\u5411\uff08\u6c34\u5e73\u7ea6 %sm\uff0c%s\uff0c\u5269\u4f59\u50a8\u91cf %s\uff09\u3002",
        "message.earth_on_minecraft.geology_hammer.category_present": "\u5ca9\u5c42\u56de\u54cd\u663e\u793a\u9644\u8fd1\u5b58\u5728\u77ff\u5316\u4f53\uff08\u7814\u8bfb\u300c\u77ff\u5e8a\u4e0e\u52d8\u67e5\u300d\u540e\u53ef\u5224\u8bfb\u65b9\u4f4d\u3001\u6df1\u5ea6\u4e0e\u50a8\u91cf\uff09\u3002",
        "message.earth_on_minecraft.autoclave.not_sterilizable": "\u706d\u83cc\u91dc\u4ec5\u63a5\u6536\u53ef\u706d\u83cc\u7684\u533b\u7597\u5668\u68b0\u4e0e\u5b9e\u9a8c\u73bb\u7483\u5668\u76bf\u3002",
        "message.earth_on_minecraft.autoclave.status": "\u706d\u83cc\u8fdb\u5ea6\uff1a%s/%s \u523b | \u7535\u80fd\u7f13\u5b58\uff1a%s EOU",
        "message.earth_on_minecraft.autoclave.fault.pressure_low": "\u706d\u83cc\u91dc\u6545\u969c\uff1a\u84b8\u6c7d\u538b\u529b\u4e0d\u8db3\uff08\u4f9b\u7535\u4e2d\u65ad\uff0c\u9700\u6301\u7eed 8 EOU/t\uff09\u3002",
        "message.earth_on_minecraft.autoclave.fault.cycle_incomplete": "\u706d\u83cc\u91dc\u6545\u969c\uff1a\u706d\u83cc\u5468\u671f\u672a\u5b8c\u6210\u5373\u88ab\u4e2d\u65ad\u3002",
        "message.earth_on_minecraft.autoclave.fault.lore_locked": "\u706d\u83cc\u91dc\u6545\u969c\uff1a\u653e\u7f6e\u8005\u5c1a\u672a\u638c\u63e1\u6240\u9700\u5b66\u8bc6\u3002",
        "screen.earth_on_minecraft.notebook.stratigraphy_locked": "\u7814\u8bfb\u300c\u5730\u5c42\u4e0e\u5ca9\u7701\u300d\uff08K-GEO-03\uff09\u540e\u53ef\u89e3\u8bfb\u6bcd\u5ca9\u7701\u3001\u4f34\u751f\u7ec4\u5408\u4e0e\u57cb\u6df1\u5c42\u4f4d\u3002",
        "screen.earth_on_minecraft.notebook.stratigraphy_redacted": "[\u5ca9\u7701\u4e0e\u5c42\u4f4d\u8be6\u60c5\u9700\u7814\u8bfb K-GEO-03]",
    }

    for n in eom_nodes:
        code = n.code.lower()
        name_key = f"lore.earth_on_minecraft.{code}.{n.slug}"
        desc_key = f"{name_key}.desc"
        en_name = ENGLISH_NAMES.get(n.slug, n.slug.replace("_", " ").title())
        en_desc, zh_desc = make_descriptions(n)
        en_updates[name_key] = en_name
        en_updates[desc_key] = en_desc
        zh_updates[name_key] = n.name
        zh_updates[desc_key] = zh_desc

    en.update(en_updates)
    zh.update(zh_updates)
    write_json(en_path, en)
    write_json(zh_path, zh)


def main() -> None:
    mods, disciplines = kc.load_config()
    entries, declared, catalog_rows = kc.scan_docs(mods)
    nodes = kc.parse_nodes(mods)
    kc.check_nodes(nodes, mods, disciplines, entries, declared, catalog_rows)
    eom_nodes = sorted(
        [n for n in nodes.values() if n.owner == "eom"],
        key=lambda x: (x.code, x.sid),
    )
    assert len(eom_nodes) == 107, f"Expected 107 EOM nodes, got {len(eom_nodes)}"

    block_ids, _, stack_item_ids = vr.registry_ids()
    block_set = set(block_ids)
    item_set = set(stack_item_ids)

    # Clean old generated nodes directory first
    node_base = DATA_ROOT / "earth_on_minecraft" / "earth_lore" / "knowledge_node"
    if node_base.exists():
        for old in node_base.rglob("*.json"):
            old.unlink()

    seen_unlock_keys: set[str] = set()
    vanilla_teaching: dict[str, list[dict[str, object]]] = defaultdict(list)
    rid_of = {
        s: (n.rid if n.rid else f"{ALIAS_NS[n.owner]}:{n.code.lower()}/{n.slug}")
        for s, n in nodes.items()
    }

    for n in eom_nodes:
        code = n.code.lower()
        node_rid = rid_of[n.sid]
        assert node_rid and ":" in node_rid, f"Empty rid for {n.sid}"
        requires = [
            {"all": rid_of[g[0]]} if len(g) == 1 else {"any": [rid_of[x] for x in g]}
            for g in n.groups
        ]
        item_requires = (
            [{"hold": expand_ref(h)} for h in n.holds]
            + [{"made": expand_ref(m)} for m in n.mades]
            + [{"cond": c} for c in n.conds]
        )
        unlocks = build_unlocks(n, entries, catalog_rows, item_set, block_set, seen_unlock_keys)
        literature = []
        teachers = []
        for tok in n.lit:
            if tok == COMMONSENSE_TOK:
                literature.append("commonsense")
            elif tok == NOTES_TOK:
                literature.append("notes")
            elif tok.startswith(TEACHER_PFX):
                t_ref = expand_ref(tok[len(TEACHER_PFX):].strip())
                teachers.append(t_ref)
                if t_ref.startswith("minecraft:") and 1 <= n.tier <= 2:
                    vanilla_teaching[t_ref].append({"node": node_rid})
        if not literature:
            literature = ["commonsense"] if n.gate == "root" else ["notes"]

        name_key = f"lore.earth_on_minecraft.{code}.{n.slug}"
        node_json: dict[str, object] = {
            "discipline": f"earth_lore:{code}",
            "tier": n.tier,
            "gate": n.gate,
            "requires": requires,
            "item_requires": item_requires,
            "unlocks": unlocks,
            "literature": literature,
            "teachers": teachers,
            "research": {
                "items": [{"ingredient": expand_ref(r), "count": cnt} for r, cnt in n.cost],
                "ticks": n.ticks,
            },
            "fail_codes": n.fails,
            "name_key": name_key,
            "desc_key": f"{name_key}.desc",
        }
        write_json(node_base / code / f"{n.slug}.json", node_json)

    # Write NeoForge VillagerProfession data map for earth_lore:teaching
    teaching_values = {prof: entries_list for prof, entries_list in sorted(vanilla_teaching.items())}
    write_json(
        DATA_ROOT / "earth_lore" / "data_maps" / "villager_profession" / "teaching.json",
        {"replace": False, "values": teaching_values},
    )

    generate_assets_and_data(block_ids, stack_item_ids)
    update_lang_files(eom_nodes)

    print(
        f"OK generated nodes={len(eom_nodes)} uniqueUnlockKeys={len(seen_unlock_keys)} "
        f"vanillaTeacherProfessions={len(teaching_values)}"
    )


if __name__ == "__main__":
    main()
