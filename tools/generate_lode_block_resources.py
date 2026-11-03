#!/usr/bin/env python3
"""Generate resources for tracked host-rock lodes and remove reserve-reset silk drops."""

from __future__ import annotations

import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
RES = ROOT / "neoforge-26.2/src/main/resources"
ASSETS = RES / "assets/earth_on_minecraft"
LOOT = RES / "data/earth_on_minecraft/loot_table/blocks"
JAVA = ROOT / "neoforge-26.2/src/main/java/com/xxsx/earthonminecraft"

HOST_LODES = {
    "andesite_volcanic_belt": ("andesite", "minecraft:andesite"),
    "anthracite_deepslate_halo": ("deepslate", "earth_on_minecraft:coal_dust"),
    "auriferous_quartz_calcite_halo": ("calcite", "earth_on_minecraft:auriferous_quartz_chunk"),
    "basalt_mafic_dike": ("basalt", "minecraft:basalt"),
    "bituminous_sandstone_halo": ("sandstone", "earth_on_minecraft:coal_dust"),
    "chalcopyrite_granite_halo": ("granite", "earth_on_minecraft:chalcopyrite_chunk"),
    "diorite_intrusion": ("diorite", "minecraft:diorite"),
    "granitic_intrusion": ("granite", "minecraft:granite"),
    "kimberlite_tuff_halo": ("tuff", "earth_on_minecraft:kimberlite_chunk"),
    "limestone_calcite_bed": ("calcite", "minecraft:calcite"),
    "magnetite_tuff_halo": ("tuff", "earth_on_minecraft:magnetite_chunk"),
    "sedimentary_sandstone_bed": ("sandstone", "minecraft:sandstone"),
    "tuff_volcanic_belt": ("tuff", "minecraft:tuff"),
}

ORE_BLOCKS = {
    "poor_magnetite_ore", "magnetite_ore", "rich_magnetite_ore", "chalcopyrite_ore",
    "auriferous_quartz_vein", "bituminous_coal_seam", "anthracite_coal_seam", "kimberlite",
    "diamondiferous_kimberlite", "lapis_lazuli_ore", "emerald_beryl_vein", "redstone_mineral_ore",
    "cinnabar_vein", "bauxite_laterite_deposit", "phosphorite_bed", "evaporite_salt_bed",
    "cassiterite_placer_deposit", "rare_earth_carbonatite", "chromite_ore", "galena_ore",
    "manganite_ore", "sphalerite_ore", "uraninite_ore", "borate_deposit",
}

SELF_DROP_REPLACEMENTS = {
    "bauxite_laterite_deposit": "earth_on_minecraft:bauxite_dust",
    "phosphorite_bed": "earth_on_minecraft:phosphate_rock_dust",
    "evaporite_salt_bed": "earth_on_minecraft:brine_crystal",
    "cassiterite_placer_deposit": "earth_on_minecraft:cassiterite_dust",
    "rare_earth_carbonatite": "earth_on_minecraft:bastnasite_dust",
    "chromite_ore": "earth_on_minecraft:chromite_dust",
    "galena_ore": "earth_on_minecraft:galena_dust",
    "manganite_ore": "earth_on_minecraft:manganese_oxide_dust",
    "sphalerite_ore": "earth_on_minecraft:sphalerite_dust",
    "uraninite_ore": "earth_on_minecraft:uraninite_dust",
    "borate_deposit": "earth_on_minecraft:borate_mineral_dust",
}


def write_host_resources(block_id: str, host: str, drop: str) -> None:
    (ASSETS / "blockstates").mkdir(parents=True, exist_ok=True)
    (ASSETS / "models/block").mkdir(parents=True, exist_ok=True)
    (ASSETS / "items").mkdir(parents=True, exist_ok=True)
    (LOOT).mkdir(parents=True, exist_ok=True)
    (ASSETS / "blockstates" / f"{block_id}.json").write_text(json.dumps({
        "variants": {"": {"model": f"earth_on_minecraft:block/{block_id}"}}
    }, indent=2) + "\n", encoding="utf-8")
    (ASSETS / "models/block" / f"{block_id}.json").write_text(json.dumps({
        "parent": "minecraft:block/cube_all",
        "textures": {"all": f"minecraft:block/{host}"},
    }, indent=2) + "\n", encoding="utf-8")
    (ASSETS / "items" / f"{block_id}.json").write_text(json.dumps({
        "model": {"type": "minecraft:model", "model": f"earth_on_minecraft:block/{block_id}"}
    }, indent=2) + "\n", encoding="utf-8")
    (LOOT / f"{block_id}.json").write_text(json.dumps({
        "type": "minecraft:block",
        "pools": [{"rolls": 1.0, "entries": [{"type": "minecraft:item", "name": drop}]}],
        "random_sequence": f"earth_on_minecraft:blocks/{block_id}",
    }, indent=2) + "\n", encoding="utf-8")


def remove_reserve_reset_drops(block_id: str) -> None:
    path = LOOT / f"{block_id}.json"
    data = json.loads(path.read_text(encoding="utf-8"))

    def replace_self_drop(value: object) -> None:
        if isinstance(value, dict):
            if value.get("type") == "minecraft:item" and value.get("name") in (
                    f"earth_on_minecraft:{block_id}", "minecraft:cobblestone"):
                value["name"] = SELF_DROP_REPLACEMENTS.get(block_id, "minecraft:cobblestone")
            for child in list(value.values()):
                replace_self_drop(child)
        elif isinstance(value, list):
            for child in value:
                replace_self_drop(child)

    replace_self_drop(data)
    for pool in data.get("pools", []):
        entries = pool.get("entries", [])
        for index, entry in enumerate(entries):
            if entry.get("type") != "minecraft:alternatives":
                continue
            children = entry.get("children", [])
            safe_drops = [child for child in children
                          if not any(condition.get("condition") == "minecraft:match_tool"
                                     for condition in child.get("conditions", []))]
            if safe_drops:
                entries[index:index + 1] = safe_drops
                break
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def merge_tag(path: pathlib.Path, block_id: str) -> None:
    data = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"replace": False, "values": []}
    identifier = f"earth_on_minecraft:{block_id}"
    if identifier not in data.setdefault("values", []):
        data["values"].append(identifier)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    for block_id, (host, drop) in HOST_LODES.items():
        write_host_resources(block_id, host, drop)
        merge_tag(RES / "data/minecraft/tags/block/mineable/pickaxe.json", block_id)
        merge_tag(RES / "data/minecraft/tags/block/needs_stone_tool.json", block_id)
    for block_id in sorted(ORE_BLOCKS):
        remove_reserve_reset_drops(block_id)
    print(f"LODE_BLOCK_RESOURCES_OK host_lodes={len(HOST_LODES)} finite_loot_tables={len(ORE_BLOCKS)}")


if __name__ == "__main__":
    main()
