#!/usr/bin/env python3
"""Emit the registry-side resources for the 30 spec recipe-station machines.

Per machine this writes the 26.2 item definition, the item model, the block loot table, the
block/item processing_machines tags, the mineable/needs-tier tags, both lang keys, and a
crafting recipe built from a thematically matched vanilla material set.
"""

from __future__ import annotations

import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
RES = ROOT / "neoforge-26.2/src/main/resources"
ASSETS = RES / "assets/earth_on_minecraft"
DATA = RES / "data"
ITEM_DEFS = ASSETS / "items"
ITEM_MODELS = ASSETS / "models/item"
LANG = ASSETS / "lang"
LOOT = DATA / "earth_on_minecraft/loot_table/blocks"
RECIPES = DATA / "earth_on_minecraft/recipe"
BLOCK_TAGS = DATA / "minecraft/tags/block"
ITEM_TAGS = DATA / "earth_on_minecraft/tags/item"
BLOCK_DATA_TAGS = DATA / "earth_on_minecraft/tags/block"

# id -> (en, zh, tool, needs_tier, recipe pattern, keys, shapeless)
MACHINES = {
    "bloomery_controller": ("Bloomery", "块炼炉", "pickaxe", "stone",
                      ["BBB", "BTB", "SSS"], {"B": "minecraft:clay", "T": "minecraft:campfire", "S": "minecraft:stone"}, False),
    "bread_oven_controller": ("Bread Oven", "面包窑", "pickaxe", "stone",
                      ["BBB", "B B", "SSS"], {"B": "minecraft:brick", "S": "minecraft:smooth_stone"}, False),
    "glass_kiln_controller": ("Glass Kiln", "玻璃窑", "pickaxe", "iron",
                      ["BBB", "BFB", "SSS"], {"B": "minecraft:brick", "F": "minecraft:blast_furnace", "S": "minecraft:smooth_stone"}, False),
    "lime_kiln_controller": ("Lime Kiln", "石灰窑", "pickaxe", "stone",
                      ["BBB", "B B", "BBB"], {"B": "minecraft:brick"}, False),
    "pit_kiln_controller": ("Charcoal Pit Kiln", "炭窑", "shovel", "none",
                      ["DDD", "D D", "DDD"], {"D": "minecraft:dirt"}, False),
    "shaft_furnace_controller": ("Shaft Furnace", "竖炉", "pickaxe", "iron",
                      ["BBB", "BFB", "BBB"], {"B": "minecraft:brick", "F": "minecraft:blast_furnace"}, False),
    "anvil": ("Anvil", "锻砧台", "pickaxe", "stone",
              ["III", " I ", "SSS"], {"I": "minecraft:iron_block", "S": "minecraft:stone"}, False),
    "bar_counter": ("Bar Counter", "吧台", "axe", "none",
                    ["PPP", "SSS", "S S"], {"P": "minecraft:stripped_oak_log", "S": "minecraft:oak_planks"}, False),
    "campfire": ("Campfire", "营火", "axe", "none",
                 [" S ", "SCS", "LLL"], {"S": "minecraft:cobblestone", "C": "minecraft:charcoal",
                                         "L": "minecraft:oak_log"}, False),
    "carpentry_bench": ("Carpentry Bench", "木工台", "axe", "none",
                        ["PPP", "P P", "S S"], {"P": "minecraft:oak_planks", "S": "minecraft:stick"}, False),
    "casting_table": ("Casting Table", "浇铸台", "pickaxe", "stone",
                      ["BBB", "B B", "SSS"], {"B": "minecraft:brick", "S": "minecraft:smooth_stone"}, False),
    "coffee_roaster": ("Coffee Roaster", "烘焙机", "pickaxe", "stone",
                       ["III", "IFI", "III"], {"I": "minecraft:iron_ingot", "F": "minecraft:furnace"}, False),
    "condenser": ("Condenser", "冷凝器", "pickaxe", "stone",
                  ["IGI", "GBG", "IGI"], {"I": "minecraft:iron_ingot", "G": "minecraft:glass",
                                          "B": "minecraft:bucket"}, False),
    "converter": ("Converter", "吹炼炉", "pickaxe", "iron",
                  ["BBB", "BFB", "III"], {"B": "minecraft:brick", "F": "minecraft:blast_furnace",
                                          "I": "minecraft:iron_ingot"}, False),
    "curing_barn": ("Curing Barn", "烘房", "axe", "none",
                    ["PPP", "P P", "PPP"], {"P": "minecraft:oak_planks"}, False),
    "fermenting_crock": ("Fermenting Crock", "发酵缸", "pickaxe", "none",
                         [" C ", "C C", "CCC"], {"C": "minecraft:terracotta"}, False),
    "filter_press": ("Filter Press", "压滤机", "pickaxe", "iron",
                     ["III", "ICI", "III"], {"I": "minecraft:iron_ingot", "C": "minecraft:cauldron"}, False),
    "gem_cutter": ("Gem Cutter", "宝石切割机", "pickaxe", "iron",
                   ["III", "IDI", "III"], {"I": "minecraft:iron_ingot", "D": "minecraft:diamond"}, False),
    "herb_processing_table": ("Herb Processing Table", "药材处理台", "axe", "none",
                              ["PPP", "P P", "S S"], {"P": "minecraft:oak_slab", "S": "minecraft:stick"}, False),
    "loom": ("Loom", "织布机", "axe", "none",
             ["SSS", "P P", "P P"], {"S": "minecraft:string", "P": "minecraft:oak_planks"}, False),
    "mill": ("Mill", "磨坊", "axe", "none",
             ["SSS", "S S", "PPP"], {"S": "minecraft:stone", "P": "minecraft:oak_planks"}, False),
    "noodle_press": ("Noodle Press", "压面机", "axe", "none",
                     ["PPP", "P P", "S S"], {"P": "minecraft:oak_planks", "S": "minecraft:stick"}, False),
    "oil_press": ("Oil Press", "榨油机", "axe", "none",
                  ["PPP", "P P", "LLL"], {"P": "minecraft:oak_planks", "L": "minecraft:oak_log"}, False),
    "rice_huller": ("Rice Huller", "砻谷机", "axe", "none",
                    ["PPP", "P P", "SSS"], {"P": "minecraft:oak_planks", "S": "minecraft:stone"}, False),
    "smelter": ("Smelter", "熔炼炉", "pickaxe", "iron",
                ["BBB", "BFB", "BBB"], {"B": "minecraft:brick", "F": "minecraft:blast_furnace"}, False),
    "smokehouse": ("Smokehouse", "熏房", "axe", "none",
                   ["PPP", "P P", "FFF"], {"P": "minecraft:oak_planks", "F": "minecraft:cobblestone"}, False),
    "steamer": ("Steamer", "蒸锅", "pickaxe", "stone",
                ["III", "ICI", "III"], {"I": "minecraft:iron_ingot", "C": "minecraft:cauldron"}, False),
    "steamer_basket": ("Steamer Basket", "蒸笼", "axe", "none",
                       ["B B", "BBB", "B B"], {"B": "minecraft:bamboo"}, False),
    "still": ("Still", "蒸馏器", "pickaxe", "stone",
              ["CCC", "C C", "BBB"], {"C": "minecraft:copper_ingot", "B": "minecraft:brick"}, False),
    "stone_mill": ("Stone Mill", "石磨", "axe", "none",
                   [" S ", "SIS", " S "], {"S": "minecraft:stone", "I": "minecraft:stick"}, False),
    "stove": ("Stove", "灶台", "pickaxe", "stone",
              ["III", "IFI", "III"], {"I": "minecraft:iron_ingot", "F": "minecraft:furnace"}, False),
    "tablet_press": ("Tablet Press", "压片机", "pickaxe", "iron",
                     ["III", "IPI", "III"], {"I": "minecraft:iron_ingot", "P": "minecraft:piston"}, False),
    "tannery": ("Tannery", "制革槽", "axe", "none",
                ["PPP", "P P", "WWW"], {"P": "minecraft:oak_planks", "W": "minecraft:oak_slab"}, False),
    "tea_kiln": ("Tea Kiln", "茶炒锅", "pickaxe", "stone",
                 ["BBB", "BIB", "BBB"], {"B": "minecraft:brick", "I": "minecraft:iron_ingot"}, False),
    "thickener": ("Thickener", "浓密机", "pickaxe", "iron",
                  ["III", "ICI", "III"], {"I": "minecraft:iron_ingot", "C": "minecraft:cauldron"}, False),
    "water_treatment_unit": ("Water Treatment Unit", "水处理单元", "pickaxe", "iron",
                             ["ICI", "CBC", "ICI"], {"I": "minecraft:iron_ingot", "C": "minecraft:copper_ingot",
                                                     "B": "minecraft:bucket"}, False),
}


def write_json(path: pathlib.Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def merge_lang(lang: str, machine_id: str, en: str, zh: str) -> None:
    path = LANG / f"{lang}.json"
    data = json.loads(path.read_text(encoding="utf-8-sig"))
    display = en if lang == "en_us" else zh
    data[f"block.earth_on_minecraft.{machine_id}"] = display
    data[f"item.earth_on_minecraft.{machine_id}"] = display
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def merge_tag(path: pathlib.Path, machine_id: str) -> None:
    entry = f"earth_on_minecraft:{machine_id}"
    data = json.loads(path.read_text(encoding="utf-8-sig")) if path.exists() else {"replace": False, "values": []}
    values = data.setdefault("values", [])
    if entry not in values:
        values.append(entry)
        values.sort()
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def write_loot(machine_id: str) -> None:
    write_json(LOOT / f"{machine_id}.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1.0,
            "entries": [{"type": "minecraft:item", "name": f"earth_on_minecraft:{machine_id}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
        "random_sequence": f"earth_on_minecraft:blocks/{machine_id}",
    })


def write_recipe(machine_id: str, pattern: list[str], keys: dict[str, str], shapeless: bool) -> None:
    recipe: dict = {"type": "minecraft:crafting_shaped" if not shapeless else "minecraft:crafting_shapeless",
                    "category": "misc"}
    if shapeless:
        recipe["ingredients"] = list(keys.values())
    else:
        recipe["pattern"] = pattern
        recipe["key"] = dict(keys)
    recipe["result"] = {"id": f"earth_on_minecraft:{machine_id}", "count": 1}
    write_json(RECIPES / f"{machine_id}.json", recipe)


def main() -> None:
    for machine_id, (en, zh, tool, tier, pattern, keys, shapeless) in MACHINES.items():
        write_json(ITEM_DEFS / f"{machine_id}.json", {
            "model": {"type": "minecraft:model", "model": f"earth_on_minecraft:block/{machine_id}"}})
        write_json(ITEM_MODELS / f"{machine_id}.json", {"parent": f"earth_on_minecraft:block/{machine_id}"})
        write_loot(machine_id)
        for lang in ("en_us", "zh_cn"):
            merge_lang(lang, machine_id, en, zh)
        merge_tag(BLOCK_DATA_TAGS / "processing_machines.json", machine_id)
        merge_tag(ITEM_TAGS / "processing_machines.json", machine_id)
        if tool in ("pickaxe", "axe", "shovel"):
            merge_tag(BLOCK_TAGS / "mineable" / f"{tool}.json", machine_id)
        if tier == "iron":
            merge_tag(BLOCK_TAGS / "needs_iron_tool.json", machine_id)
        elif tier == "stone":
            merge_tag(BLOCK_TAGS / "needs_stone_tool.json", machine_id)
        write_recipe(machine_id, pattern, keys, shapeless)
    print(f"SPEC_MACHINE_RESOURCES_OK machines={len(MACHINES)}")


if __name__ == "__main__":
    main()
