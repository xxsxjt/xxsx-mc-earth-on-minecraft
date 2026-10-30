#!/usr/bin/env python3
"""Generate editable Blockbench sources and distinct runtime geometry for profiled devices."""

from __future__ import annotations

import json
import pathlib
import uuid


ROOT = pathlib.Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "neoforge-26.2" / "src" / "main" / "resources"
ASSETS = RESOURCES / "assets" / "earth_on_minecraft"
MODELS = ASSETS / "models" / "block"
BLOCKSTATES = ASSETS / "blockstates"
PROFILES = RESOURCES / "data" / "earth_on_minecraft" / "earth" / "quality" / "machine_profiles.json"
BLOCKBENCH = ROOT / "art" / "blockbench"
OUTPUT = ROOT / "output" / "quality"
FACES = ("north", "south", "west", "east", "up", "down")


def box(name: str, start: tuple[float, float, float], end: tuple[float, float, float],
        rotation: tuple[str, float, tuple[float, float, float]] | None = None) -> dict[str, object]:
    result: dict[str, object] = {"name": name, "from": list(start), "to": list(end)}
    if rotation is not None:
        result["rotation"] = {"axis": rotation[0], "angle": rotation[1], "origin": list(rotation[2])}
    return result


B = box
SHAPES: dict[str, list[dict[str, object]]] = {
    "bloomery_controller": [
        B("plinth", [1, 0, 1], [15, 2, 15]), B("shaft_low", [3, 2, 3], [13, 8, 13]), B("shaft_high", [4, 8, 4], [12, 13, 12]), B("tuyere", [0, 4, 6], [4, 7, 10]), B("tap_arch", [5, 2, 0], [11, 5, 4]), B("flue", [5, 13, 5], [11, 15, 11]),
    ],
    "bread_oven_controller": [
        B("plinth", [1, 0, 1], [15, 3, 15]), B("hearth", [2, 3, 2], [14, 5, 14]), B("dome_low", [3, 5, 3], [13, 10, 13]), B("dome_cap", [5, 10, 5], [11, 13, 11]), B("mouth_arch", [5, 3, 0], [11, 8, 4]), B("peel", [0, 4, 12], [4, 5, 15]),
    ],
    "glass_kiln_controller": [
        B("base", [1, 0, 1], [15, 4, 15]), B("barrel", [2, 4, 2], [14, 11, 14]), B("crown", [3, 11, 3], [13, 14, 13]), B("gather_port", [12, 4, 6], [15, 9, 10]), B("charging_chute", [6, 11, 0], [10, 14, 4]), B("firebox", [0, 1, 6], [3, 4, 10]),
    ],
    "lime_kiln_controller": [
        B("plinth", [2, 0, 2], [14, 3, 14]), B("shaft", [3, 3, 3], [13, 12, 13]), B("stack_cap", [4, 12, 4], [12, 14, 12]), B("draw_arch", [5, 1, 0], [11, 4, 3]), B("charging_opening", [6, 7, 12], [10, 11, 15]), B("ash_drawer", [5, 0, 3], [11, 2, 6]),
    ],
    "pit_kiln_controller": [
        B("earth_ring", [0, 0, 0], [16, 4, 16]), B("mound_low", [2, 4, 2], [14, 8, 14]), B("mound_cap", [5, 8, 5], [11, 12, 11]), B("vent_base", [6, 2, 0], [10, 5, 3]), B("vent_top", [7, 11, 7], [9, 14, 9]), B("earth_cap", [4, 12, 4], [12, 13, 12]),
    ],
    "shaft_furnace_controller": [
        B("plinth", [1, 0, 1], [15, 3, 15]), B("shaft_low", [2, 3, 2], [14, 9, 14]), B("shaft_high", [3, 9, 3], [13, 14, 13]), B("blast_main", [0, 4, 6], [4, 8, 10]), B("tap_hole", [5, 2, 0], [11, 5, 4]), B("charging_bell", [5, 14, 5], [11, 16, 11]),
    ],
    "jaw_crusher": [
        B("foundation", (1, 0, 1), (15, 2, 15)), B("jaw_chamber", (3, 2, 3), (13, 12, 14)),
        B("left_cheek", (1, 3, 2), (4, 13, 15)), B("right_cheek", (12, 3, 2), (15, 13, 15)),
        B("feed_hopper", (4, 12, 5), (12, 16, 14)), B("discharge", (5, 2, 0), (11, 6, 4)),
    ],
    "ball_mill": [
        B("foundation", (1, 0, 1), (15, 2, 15)), B("left_bearing", (1, 2, 4), (4, 8, 12)),
        B("drum", (3, 5, 2), (13, 13, 14)), B("right_bearing", (12, 2, 4), (15, 8, 12)),
        B("feed_trunnion", (0, 7, 6), (4, 11, 10)), B("discharge_trunnion", (12, 7, 6), (16, 11, 10)),
    ],
    "sieve": [
        B("foundation", (1, 0, 1), (15, 2, 15)), B("left_springs", (2, 2, 3), (4, 7, 13)),
        B("right_springs", (12, 2, 3), (14, 7, 13)),
        B("screen_deck", (2, 6, 3), (14, 9, 14), ("x", -22.5, (8, 8, 8))),
        B("feed_hood", (3, 9, 10), (13, 14, 15)), B("graded_chutes", (3, 2, 0), (13, 7, 5)),
    ],
    "magnetic_separator": [
        B("foundation", (1, 0, 1), (15, 2, 15)), B("drum", (3, 5, 3), (13, 13, 13)),
        B("left_yoke", (1, 3, 4), (4, 15, 12)), B("right_yoke", (12, 3, 4), (15, 15, 12)),
        B("feed_box", (4, 11, 11), (12, 16, 15)), B("split_discharge", (4, 2, 0), (12, 7, 5)),
    ],
    "flotation_cell": [
        B("foundation", (1, 0, 1), (15, 2, 15)), B("tank", (2, 2, 2), (14, 11, 14)),
        B("froth_launder", (1, 9, 1), (15, 12, 15)), B("agitator_shaft", (7, 10, 7), (9, 16, 9)),
        B("motor", (5, 13, 5), (11, 16, 11)), B("concentrate_outlet", (11, 5, 0), (15, 9, 4)),
    ],
    "ore_roaster": [
        B("windbox", (2, 0, 2), (14, 4, 14)), B("fluidized_vessel", (3, 3, 3), (13, 14, 13)),
        B("refractory_ring_low", (2, 4, 2), (14, 7, 14)), B("refractory_ring_high", (2, 10, 2), (14, 13, 14)),
        B("offgas_neck", (6, 13, 6), (10, 16, 10)), B("calcine_chute", (5, 1, 0), (11, 6, 4)),
    ],
    "reduction_furnace": [
        B("hearth", (1, 0, 1), (15, 5, 15)), B("shaft", (3, 4, 3), (13, 15, 13)),
        B("lower_bosh", (2, 3, 2), (14, 8, 14)), B("charge_throat", (5, 14, 5), (11, 16, 11)),
        B("tap_hole", (5, 1, 0), (11, 5, 3)), B("gas_manifold", (13, 6, 5), (16, 11, 11)),
    ],
    "leaching_tank": [
        B("foundation", (1, 0, 1), (15, 2, 15)), B("lined_tank", (2, 2, 2), (14, 12, 14)),
        B("tank_rim", (1, 10, 1), (15, 13, 15)), B("agitator", (7, 11, 7), (9, 16, 9)),
        B("drive", (5, 13, 5), (11, 16, 11)), B("sampling_box", (3, 5, 0), (8, 10, 3)),
    ],
    "electrolytic_cell": [
        B("foundation", (1, 0, 1), (15, 2, 15)), B("electrolyte_tank", (1, 2, 2), (15, 10, 14)),
        B("anode_bank", (3, 8, 3), (5, 15, 13)), B("cathode_bank", (7, 8, 3), (9, 15, 13)),
        B("return_bank", (11, 8, 3), (13, 15, 13)), B("busbar", (1, 13, 1), (15, 16, 4)),
    ],
    "powder_press": [
        B("press_bed", (1, 0, 1), (15, 3, 15)), B("left_columns", (2, 2, 2), (4, 15, 14)),
        B("right_columns", (12, 2, 2), (14, 15, 14)), B("top_crosshead", (1, 13, 1), (15, 16, 15)),
        B("hydraulic_ram", (6, 7, 5), (10, 14, 11)), B("die_table", (4, 3, 3), (12, 7, 13)),
    ],
    "chemical_reactor": [
        B("foundation", (1, 0, 1), (15, 2, 15)), B("pressure_vessel", (3, 2, 3), (13, 13, 13)),
        B("jacket_low", (2, 3, 2), (14, 7, 14)), B("jacket_high", (2, 9, 2), (14, 12, 14)),
        B("agitator_drive", (5, 12, 5), (11, 16, 11)), B("product_nozzle", (12, 5, 5), (16, 9, 11)),
    ],
    "distillation_column": [
        B("reboiler", (1, 0, 2), (15, 5, 14)), B("column", (5, 3, 5), (11, 16, 11)),
        B("lower_tray_ring", (3, 5, 3), (13, 7, 13)), B("middle_tray_ring", (3, 9, 3), (13, 11, 13)),
        B("upper_tray_ring", (3, 13, 3), (13, 15, 13)), B("side_draw", (10, 8, 6), (16, 11, 10)),
    ],
    "mixer": [
        B("foundation", (1, 0, 1), (15, 2, 15)), B("mixing_trough", (2, 4, 2), (14, 12, 14)),
        B("left_end", (1, 5, 4), (4, 11, 12)), B("right_end", (12, 5, 4), (15, 11, 12)),
        B("top_feed", (5, 11, 5), (11, 16, 11)), B("bottom_gate", (5, 2, 5), (11, 6, 11)),
    ],
    "crystallizer": [
        B("foundation", (1, 0, 1), (15, 2, 15)), B("crystal_vessel", (3, 2, 3), (13, 13, 13)),
        B("circulation_chamber", (1, 5, 5), (5, 12, 11)), B("vapor_head", (5, 11, 5), (11, 16, 11)),
        B("product_leg", (5, 1, 5), (11, 6, 11)), B("service_box", (12, 6, 4), (16, 11, 12)),
    ],
    "industrial_kiln": [
        B("foundation", (1, 0, 1), (15, 2, 15)), B("left_support", (2, 2, 3), (5, 7, 13)),
        B("rotary_shell", (2, 5, 2), (14, 13, 14), ("z", -22.5, (8, 9, 8))),
        B("right_support", (11, 2, 3), (14, 7, 13)), B("burner_hood", (0, 5, 5), (4, 12, 11)),
        B("feed_hood", (12, 7, 4), (16, 14, 12)),
    ],
    "gas_separator": [
        B("skid", (1, 0, 1), (15, 2, 15)), B("left_adsorber", (2, 2, 3), (7, 15, 13)),
        B("right_adsorber", (9, 2, 3), (14, 15, 13)), B("top_manifold", (1, 13, 2), (15, 16, 6)),
        B("feed_header", (0, 4, 5), (4, 8, 11)), B("product_header", (12, 4, 5), (16, 8, 11)),
    ],
    "fertilizer_granulator": [
        B("foundation", (1, 0, 1), (15, 2, 15)), B("left_bearing", (1, 2, 4), (4, 7, 12)),
        B("granulation_drum", (3, 5, 2), (13, 13, 14), ("z", -22.5, (8, 9, 8))),
        B("right_bearing", (12, 2, 4), (15, 8, 12)), B("powder_feed", (0, 8, 5), (5, 15, 12)),
        B("product_hood", (11, 4, 4), (16, 12, 13)),
    ],
    "polymerizer": [
        B("foundation", (1, 0, 1), (15, 2, 15)), B("autoclave", (3, 2, 3), (13, 13, 13)),
        B("cooling_jacket", (2, 4, 2), (14, 11, 14)), B("sealed_drive", (5, 12, 5), (11, 16, 11)),
        B("monomer_feed", (0, 7, 5), (5, 11, 11)), B("resin_outlet", (11, 4, 5), (16, 8, 11)),
    ],
    "steam_cracker": [
        B("radiant_furnace", (1, 0, 2), (10, 15, 14)), B("coil_chamber", (2, 3, 0), (9, 13, 4)),
        B("convection_bank", (9, 7, 3), (14, 16, 13)), B("quench_section", (12, 3, 4), (16, 13, 12)),
        B("burner_floor", (0, 0, 1), (11, 4, 15)), B("stack", (11, 12, 6), (15, 16, 10)),
    ],
    "synthesis_loop": [
        B("skid", (1, 0, 1), (15, 2, 15)), B("reactor", (5, 2, 5), (11, 16, 11)),
        B("compressor", (1, 3, 3), (6, 9, 13)), B("separator", (11, 4, 4), (15, 14, 12)),
        B("upper_recycle", (2, 12, 2), (14, 15, 5)), B("lower_recycle", (2, 2, 11), (14, 5, 14)),
    ],
    "absorption_tower": [
        B("sump", (1, 0, 1), (15, 5, 15)), B("packed_column", (5, 3, 5), (11, 16, 11)),
        B("lower_packing_ring", (3, 5, 3), (13, 7, 13)), B("upper_packing_ring", (3, 11, 3), (13, 13, 13)),
        B("recirculation_pump", (0, 3, 5), (5, 9, 11)), B("spray_header", (4, 14, 4), (12, 16, 12)),
    ],
    "combustion_generator": [
        B("skid", (1, 0, 1), (15, 2, 15)), B("prime_mover", (2, 2, 3), (9, 12, 13)),
        B("alternator", (8, 4, 4), (14, 11, 12)), B("coupling_guard", (7, 5, 5), (10, 10, 11)),
        B("control_cabinet", (1, 8, 1), (6, 15, 5)), B("exhaust_stack", (11, 10, 8), (14, 16, 11)),
    ],
    "steam_turbine_generator": [
        B("skid", (1, 0, 1), (15, 2, 15)), B("turbine_low", (1, 4, 3), (8, 12, 13)),
        B("turbine_high", (3, 7, 2), (10, 14, 14)), B("generator", (9, 5, 4), (15, 13, 12)),
        B("shaft_guard", (7, 6, 6), (11, 11, 10)), B("steam_chest", (1, 11, 5), (6, 16, 11)),
    ],
    "anvil": [
        B("stump", (4, 0, 4), (12, 4, 12)), B("anvil_base", (5, 4, 5), (11, 6, 11)),
        B("anvil_waist", (6, 6, 6), (10, 9, 10)), B("anvil_body", (4, 9, 4), (12, 12, 12)),
        B("anvil_horn", (12, 10, 6), (16, 12, 10)), B("hammer_rack", (1, 2, 5), (4, 9, 11)),
    ],
    "bar_counter": [
        B("counter_body", (1, 0, 4), (15, 8, 14)), B("counter_top", (0, 8, 3), (16, 10, 15)),
        B("back_bar", (1, 0, 0), (15, 14, 4)), B("bottle_rail", (2, 10, 3), (14, 13, 6)),
        B("ice_well", (3, 8, 9), (7, 10, 13)), B("foot_rail", (2, 0, 14), (14, 2, 16)),
    ],
    "campfire": [
        B("ash_bed", (2, 0, 2), (14, 2, 14)), B("stone_ring_west", (0, 0, 3), (3, 4, 13)),
        B("stone_ring_east", (13, 0, 3), (16, 4, 13)), B("stone_ring_north", (3, 0, 0), (13, 4, 3)),
        B("stone_ring_south", (3, 0, 13), (13, 4, 16)),
        B("log_pile", (4, 2, 4), (12, 6, 12), ("y", 45.0, (8, 4, 8))),
        B("cooking_grate", (3, 6, 3), (13, 8, 13)),
    ],
    "carpentry_bench": [
        B("left_trestle", (1, 0, 3), (4, 9, 13)), B("right_trestle", (12, 0, 3), (15, 9, 13)),
        B("worktop", (0, 9, 2), (16, 12, 14)), B("tail_vise", (11, 8, 14), (15, 12, 16)),
        B("tool_board", (2, 12, 1), (14, 16, 3)), B("dog_holes", (4, 12, 6), (10, 13, 11)),
    ],
    "casting_table": [
        B("table_bed", (1, 0, 2), (15, 4, 14)), B("mold_rack_left", (1, 4, 3), (4, 8, 13)),
        B("mold_rack_right", (12, 4, 3), (15, 8, 13)), B("launder", (5, 4, 0), (11, 7, 4)),
        B("cooling_fins", (4, 4, 4), (12, 6, 12)), B("quench_trough", (2, 0, 14), (14, 4, 16)),
    ],
    "coffee_roaster": [
        B("burner_base", (1, 0, 3), (15, 5, 13)), B("roast_drum", (3, 4, 4), (13, 12, 12)),
        B("drum_sight_glass", (6, 6, 2), (10, 10, 5)), B("bean_hopper", (4, 12, 4), (12, 16, 10)),
        B("exhaust_hood", (5, 12, 10), (11, 15, 14)), B("cooling_tray", (1, 0, 13), (9, 3, 16)),
    ],
    "condenser": [
        B("seal_sump", (2, 0, 3), (14, 4, 13)), B("shell", (4, 4, 4), (12, 14, 12)),
        B("vapor_dome", (5, 14, 5), (11, 16, 11)), B("coolant_feed", (0, 6, 5), (4, 10, 11)),
        B("coolant_return", (12, 6, 5), (16, 10, 11)), B("condensate_outlet", (5, 0, 0), (11, 3, 4)),
    ],
    "converter": [
        B("trunnion_base", (1, 0, 2), (15, 4, 14)), B("barrel", (2, 4, 3), (14, 12, 13)),
        B("offgas_hood", (4, 12, 4), (12, 16, 12)), B("tuyere_line", (2, 6, 0), (14, 9, 4)),
        B("tap_hole", (6, 2, 13), (10, 6, 16)), B("slag_notch", (12, 7, 6), (16, 10, 12)),
    ],
    "curing_barn": [
        B("barn_shell", (1, 0, 2), (15, 12, 14)), B("ridge_vent", (3, 12, 4), (13, 16, 12)),
        B("louver_west", (0, 3, 4), (2, 10, 12)), B("louver_east", (14, 3, 4), (16, 10, 12)),
        B("loading_door", (4, 0, 14), (12, 8, 16)), B("hanging_rack", (3, 8, 5), (13, 10, 11)),
    ],
    "fermenting_crock": [
        B("stone_plinth", (2, 0, 2), (14, 3, 14)), B("crock_belly", (3, 3, 3), (13, 11, 13)),
        B("crock_lid", (4, 11, 4), (12, 14, 12)), B("airlock", (7, 14, 7), (9, 16, 9)),
        B("sample_tap", (12, 5, 6), (16, 8, 10)), B("brine_gutter", (3, 3, 0), (13, 6, 3)),
    ],
    "filter_press": [
        B("press_frame", (1, 0, 2), (15, 4, 14)), B("left_plate_bank", (2, 4, 3), (6, 14, 13)),
        B("right_plate_bank", (8, 4, 3), (12, 14, 13)), B("hydraulic_ram", (6, 5, 5), (8, 13, 11)),
        B("feed_manifold", (12, 6, 4), (16, 12, 12)), B("drip_tray", (2, 0, 13), (14, 3, 16)),
    ],
    "gem_cutter": [
        B("lap_table", (2, 0, 2), (14, 5, 14)), B("dop_mast", (5, 5, 4), (8, 15, 7)),
        B("dop_arm", (5, 11, 6), (13, 14, 9)), B("index_gear", (10, 5, 9), (14, 10, 13)),
        B("coolant_can", (0, 8, 3), (4, 13, 8)), B("swarf_tray", (2, 0, 14), (14, 2, 16)),
    ],
    "herb_processing_table": [
        B("bench_body", (1, 0, 2), (15, 9, 14)), B("drying_rack", (2, 11, 3), (14, 13, 13)),
        B("rack_posts", (2, 9, 3), (4, 14, 5)), B("cutting_board", (3, 9, 5), (9, 11, 11)),
        B("wash_basin", (10, 9, 5), (14, 12, 11)), B("scrap_bin", (1, 0, 14), (6, 6, 16)),
    ],
    "loom": [
        B("loom_frame", (1, 0, 2), (15, 3, 14)), B("warp_beam", (1, 3, 3), (4, 13, 13)),
        B("cloth_beam", (12, 3, 3), (15, 13, 13)), B("heddle_frame", (5, 5, 4), (11, 12, 12)),
        B("shuttle_race", (4, 12, 5), (12, 14, 11)), B("warp_tension_bar", (3, 8, 2), (13, 10, 4)),
    ],
    "mill": [
        B("meal_bin", (2, 0, 2), (14, 5, 14)), B("stone_housing", (3, 5, 3), (13, 11, 13)),
        B("runner_stone", (4, 11, 4), (12, 14, 12)), B("grain_hopper", (5, 14, 5), (11, 16, 11)),
        B("flour_spout", (11, 4, 6), (16, 8, 10)), B("sack_hoist", (0, 7, 5), (4, 14, 11)),
    ],
    "noodle_press": [
        B("press_frame", (1, 0, 3), (15, 4, 13)), B("left_roller", (2, 4, 4), (6, 9, 12)),
        B("right_roller", (10, 4, 4), (14, 9, 12)), B("dough_hopper", (6, 9, 5), (10, 15, 11)),
        B("cutter_comb", (5, 3, 3), (11, 6, 13)), B("noodle_tray", (2, 0, 13), (14, 3, 16)),
    ],
    "oil_press": [
        B("cake_trough", (1, 0, 3), (15, 4, 13)), B("press_cage", (3, 4, 4), (13, 11, 12)),
        B("ram_screw", (6, 11, 6), (10, 16, 10)), B("seed_hopper", (4, 12, 4), (12, 16, 9)),
        B("oil_spout", (13, 5, 6), (16, 9, 10)), B("wedge_block", (4, 4, 12), (12, 9, 15)),
    ],
    "rice_huller": [
        B("huller_base", (1, 0, 2), (15, 5, 14)), B("left_roller", (3, 5, 4), (7, 11, 12)),
        B("right_roller", (9, 5, 4), (13, 11, 12)), B("paddy_hopper", (4, 11, 5), (12, 16, 11)),
        B("screen_shoe", (3, 2, 12), (13, 5, 16)), B("husk_chute", (0, 6, 5), (4, 10, 11)),
    ],
    "smelter": [
        B("hearth_base", (2, 0, 2), (14, 4, 14)), B("shaft_body", (3, 4, 3), (13, 12, 13)),
        B("charging_mouth", (5, 12, 5), (11, 16, 11)), B("tap_hole", (6, 2, 13), (10, 6, 16)),
        B("slag_notch", (13, 6, 5), (16, 10, 11)), B("metal_launder", (3, 0, 13), (13, 3, 16)),
    ],
    "smokehouse": [
        B("smoke_chamber", (2, 3, 3), (14, 14, 13)), B("gabled_roof", (1, 14, 2), (15, 16, 14)),
        B("firebox", (3, 0, 4), (13, 4, 12)), B("meat_rail", (3, 9, 5), (13, 11, 11)),
        B("draft_vent", (13, 5, 6), (16, 9, 10)), B("ash_pit", (4, 0, 12), (12, 2, 15)),
    ],
    "steamer": [
        B("boiler_base", (2, 0, 2), (14, 6, 14)), B("steam_pot", (3, 6, 3), (13, 12, 13)),
        B("domed_lid", (4, 12, 4), (12, 15, 12)), B("basket_ring", (4, 15, 4), (12, 16, 12)),
        B("fire_door", (5, 0, 13), (11, 4, 16)), B("water_feed", (13, 6, 5), (16, 10, 11)),
    ],
    "steamer_basket": [
        B("water_pot", (3, 0, 3), (13, 4, 13)), B("lower_tier", (3, 4, 3), (13, 8, 13)),
        B("middle_tier", (3, 8, 3), (13, 12, 13)), B("upper_tier", (3, 12, 3), (13, 15, 13)),
        B("bamboo_lid", (4, 15, 4), (12, 16, 12)), B("steam_inlet", (0, 4, 5), (3, 8, 11)),
    ],
    "still": [
        B("furnace_base", (2, 0, 2), (14, 5, 14)), B("copper_pot", (3, 5, 3), (13, 12, 13)),
        B("swan_neck", (6, 12, 6), (10, 16, 10)), B("worm_condenser", (12, 3, 4), (16, 11, 12)),
        B("spirit_receiver", (4, 0, 13), (10, 4, 16)), B("wash_inlet", (0, 6, 5), (3, 10, 11)),
    ],
    "stone_mill": [
        B("stone_base", (1, 0, 1), (15, 4, 15)), B("lower_stone", (3, 4, 3), (13, 9, 13)),
        B("runner_stone", (4, 9, 4), (12, 13, 12)), B("grain_eye", (6, 13, 6), (10, 15, 10)),
        B("side_handle", (12, 9, 6), (16, 12, 10)), B("meal_spout", (5, 1, 0), (11, 5, 4)),
    ],
    "stove": [
        B("firebox", (2, 0, 2), (14, 8, 14)), B("iron_plate", (1, 8, 1), (15, 11, 15)),
        B("burner_ring", (5, 11, 5), (11, 12, 11)), B("fuel_door", (5, 1, 13), (11, 6, 16)),
        B("oven_door", (13, 2, 5), (16, 7, 11)), B("ash_drawer", (3, 0, 13), (13, 3, 16)),
    ],
    "tablet_press": [
        B("press_base", (2, 0, 3), (14, 4, 13)), B("die_block", (5, 4, 5), (11, 8, 11)),
        B("upper_punch", (6, 8, 6), (10, 14, 10)), B("hopper", (4, 12, 3), (9, 16, 9)),
        B("tablet_chute", (11, 4, 5), (16, 8, 11)), B("dust_tray", (3, 0, 13), (13, 2, 16)),
    ],
    "tannery": [
        B("soak_vat", (1, 0, 2), (15, 5, 12)), B("tanning_pit", (3, 0, 12), (13, 3, 16)),
        B("fleshing_beam", (2, 5, 4), (14, 9, 9), ("z", -18.0, (8, 7, 7))),
        B("hide_rack", (2, 10, 4), (14, 15, 6)), B("liquor_pump", (0, 2, 4), (4, 8, 10)),
        B("drain_gutter", (4, 0, 9), (12, 2, 12)),
    ],
    "tea_kiln": [
        B("brick_base", (2, 0, 2), (14, 8, 14)), B("wok", (3, 8, 3), (13, 12, 13)),
        B("wok_rim", (2, 11, 2), (14, 13, 14)), B("stirrer", (7, 12, 7), (9, 16, 9)),
        B("fire_door", (5, 0, 13), (11, 5, 16)), B("cooling_tray", (12, 5, 4), (16, 8, 12)),
    ],
    "thickener": [
        B("cone_bottom", (3, 0, 3), (13, 4, 13)), B("thickener_tank", (1, 4, 1), (15, 11, 15)),
        B("rake_drive", (5, 11, 5), (11, 16, 11)), B("overflow_launder", (14, 5, 3), (16, 9, 13)),
        B("feed_well", (2, 8, 2), (7, 12, 7)), B("flocculant_dosing", (0, 9, 5), (3, 13, 11)),
    ],
    "water_treatment_unit": [
        B("skid_frame", (1, 0, 1), (15, 3, 15)), B("pressure_vessel", (3, 3, 4), (10, 15, 12)),
        B("gauge_panel", (10, 6, 3), (13, 13, 12)), B("raw_water_inlet", (0, 5, 5), (3, 9, 11)),
        B("treated_outlet", (12, 3, 4), (16, 7, 12)), B("backwash_drain", (4, 0, 13), (12, 3, 16)),
    ],
}


def load_profiles() -> list[tuple[str, dict[str, object]]]:
    data = json.loads(PROFILES.read_text(encoding="utf-8"))
    return [
        *(("machines", profile) for profile in data["machines"]),
        *(("devices", profile) for profile in data.get("devices", [])),
    ]


def texture_roles(model_path: pathlib.Path) -> dict[str, str]:
    data = json.loads(model_path.read_text(encoding="utf-8-sig"))
    textures = data.get("textures", {})
    fallback = textures.get("all", textures.get("particle", "minecraft:block/iron_block"))
    return {
        "particle": textures.get("particle", fallback),
        "front": textures.get("front", textures.get("north", fallback)),
        "back": textures.get("back", textures.get("south", fallback)),
        "left": textures.get("left", textures.get("west", fallback)),
        "right": textures.get("right", textures.get("east", fallback)),
        "top": textures.get("top", textures.get("up", fallback)),
        "bottom": textures.get("bottom", textures.get("down", fallback)),
        "fault": "minecraft:block/redstone_lamp_on",
    }


def uv_for(face: str, start: list[float], end: list[float]) -> list[float]:
    x1, y1, z1 = start
    x2, y2, z2 = end
    return {
        "north": [x1, 16 - y2, x2, 16 - y1],
        "south": [16 - x2, 16 - y2, 16 - x1, 16 - y1],
        "west": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
        "east": [z1, 16 - y2, z2, 16 - y1],
        "up": [x1, z1, x2, z2],
        "down": [x1, 16 - z2, x2, 16 - z1],
    }[face]


def runtime_element(machine_id: str, entry: dict[str, object], fault: bool = False) -> dict[str, object]:
    start = entry["from"]
    end = entry["to"]
    roles = {"north": "front", "south": "back", "west": "left", "east": "right", "up": "top", "down": "bottom"}
    element: dict[str, object] = {
        "name": entry["name"],
        "from": start,
        "to": end,
        "faces": {face: {"uv": uv_for(face, start, end), "texture": f"#{'fault' if fault else roles[face]}"}
                  for face in FACES},
    }
    if "rotation" in entry:
        element["rotation"] = {**entry["rotation"], "rescale": True}
    return element


def runtime_model(machine_id: str, textures: dict[str, str], state: str) -> dict[str, object]:
    entries = list(SHAPES[machine_id])
    elements = [runtime_element(machine_id, entry) for entry in entries]
    if state == "fault":
        alarm = B("fault_beacon", (6.5, 5, 0), (9.5, 8, 1))
        elements.append(runtime_element(machine_id, alarm, fault=True))
    return {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "gui_light": "side",
        "textures": textures,
        "elements": elements,
    }


def blockstate(machine_id: str, processing_machine: bool) -> dict[str, object]:
    variants: dict[str, object] = {}
    rotations = {"north": 0, "east": 90, "south": 180, "west": 270}
    if processing_machine:
        for active in (False, True):
            for fault in (False, True):
                model = f"earth_on_minecraft:block/{machine_id}{'_fault' if fault else '_active' if active else ''}"
                for facing, y in rotations.items():
                    value: dict[str, object] = {"model": model}
                    if y:
                        value["y"] = y
                    variants[f"active={str(active).lower()},facing={facing},fault={str(fault).lower()}"] = value
    else:
        for active in (False, True):
            model = f"earth_on_minecraft:block/{machine_id}{'_active' if active else ''}"
            for facing, y in rotations.items():
                value = {"model": model}
                if y:
                    value["y"] = y
                variants[f"active={str(active).lower()},facing={facing}"] = value
    return {"variants": variants}


def blockbench_source(machine_id: str, profile: dict[str, object], entries: list[dict[str, object]],
                      idle: dict[str, str], active: dict[str, str]) -> dict[str, object]:
    texture_ids = list(dict.fromkeys(idle[role] for role in ("front", "back", "left", "right", "top", "bottom")))
    texture_index = {texture: index for index, texture in enumerate(texture_ids)}
    elements: list[dict[str, object]] = []
    outliner: list[str] = []
    roles = {"north": "front", "south": "back", "west": "left", "east": "right", "up": "top", "down": "bottom"}
    for index, entry in enumerate(entries):
        element_uuid = str(uuid.uuid5(uuid.NAMESPACE_URL, f"earth-on-minecraft:{machine_id}:{index}:{entry['name']}"))
        start = entry["from"]
        end = entry["to"]
        element: dict[str, object] = {
            "name": entry["name"], "box_uv": False, "rescale": bool(entry.get("rotation")),
            "locked": False, "from": start, "to": end, "autouv": 0, "color": index % 8,
            "origin": entry.get("rotation", {}).get("origin", [8, 8, 8]), "uuid": element_uuid,
            "faces": {face: {"uv": uv_for(face, start, end), "texture": texture_index[idle[roles[face]]]}
                      for face in FACES},
            "type": "cube",
        }
        if "rotation" in entry:
            rotation = [0.0, 0.0, 0.0]
            rotation[{"x": 0, "y": 1, "z": 2}[entry["rotation"]["axis"]]] = entry["rotation"]["angle"]
            element["rotation"] = rotation
        elements.append(element)
        outliner.append(element_uuid)

    textures = []
    source_dir = BLOCKBENCH / ("machines" if profile.get("kind") not in {"COMBUSTION_GENERATOR", "STEAM_TURBINE_GENERATOR"} else "devices")
    for index, texture_id in enumerate(texture_ids):
        namespace, separator, texture_path = texture_id.partition(":")
        if not separator:
            namespace, texture_path = "minecraft", namespace
        path = "" if namespace != "earth_on_minecraft" else str(pathlib.Path("../../..") / "neoforge-26.2" / "src" / "main" / "resources" / "assets" / namespace / "textures" / f"{texture_path}.png").replace("\\", "/")
        texture = {
            "name": pathlib.Path(texture_path).name + ".png", "folder": "",
            "namespace": namespace, "id": str(index), "particle": texture_id == idle["particle"],
            "render_mode": "default", "render_sides": "auto", "frame_time": 1,
            "frame_order_type": "loop", "frame_order": "", "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"earth-texture:{texture_id}")),
        }
        if path:
            texture["relative_path"] = path
        textures.append(texture)
    return {
        "meta": {"format_version": "5.0", "model_format": "java_block", "box_uv": False},
        "name": machine_id, "model_identifier": f"earth_on_minecraft:{machine_id}",
        "visible_box": [1, 1, 0], "variable_placeholders": "", "resolution": {"width": 128, "height": 128},
        "elements": elements, "outliner": outliner, "textures": textures,
        "earth_on_minecraft": {
            "schema_version": 1, "prototype": profile["prototype"], "silhouette": profile["silhouette"],
            "faces": profile["faces"], "states": {"idle": idle, "running": active, "fault": {**idle, "fault": "minecraft:block/redstone_lamp_on"}},
        },
    }


def validate(profiles: list[tuple[str, dict[str, object]]]) -> list[str]:
    failures: list[str] = []
    profile_ids = {str(profile["id"]) for _, profile in profiles}
    if profile_ids != set(SHAPES):
        failures.append(f"shape coverage mismatch missing={sorted(profile_ids - set(SHAPES))} extra={sorted(set(SHAPES) - profile_ids)}")
    signatures: set[tuple[tuple[float, ...], ...]] = set()
    for machine_id in sorted(profile_ids & set(SHAPES)):
        entries = SHAPES[machine_id]
        if len(entries) < 5:
            failures.append(f"{machine_id} has fewer than five geometry elements")
        names: set[str] = set()
        signature: list[tuple[float, ...]] = []
        for entry in entries:
            names.add(str(entry["name"]))
            start = [float(value) for value in entry["from"]]
            end = [float(value) for value in entry["to"]]
            if any(value < 0 or value > 16 for value in (*start, *end)):
                failures.append(f"{machine_id}/{entry['name']} exceeds block bounds")
            if any(end[index] <= start[index] for index in range(3)):
                failures.append(f"{machine_id}/{entry['name']} has non-positive dimensions")
            signature.append(tuple((*start, *end)))
        if len(names) != len(entries):
            failures.append(f"{machine_id} has duplicate element names")
        frozen = tuple(sorted(signature))
        if frozen in signatures:
            failures.append(f"{machine_id} duplicates another machine geometry signature")
        signatures.add(frozen)
    return failures


def main() -> None:
    profiles = load_profiles()
    failures = validate(profiles)
    if failures:
        for failure in failures:
            print(f"ERROR: {failure}")
        raise SystemExit(1)

    records: list[dict[str, object]] = []
    for group, profile in profiles:
        machine_id = str(profile["id"])
        idle_path = MODELS / f"{machine_id}.json"
        active_path = MODELS / f"{machine_id}_active.json"
        idle = texture_roles(idle_path)
        active = texture_roles(active_path)
        for state, textures in (("idle", idle), ("active", active), ("fault", idle)):
            suffix = "" if state == "idle" else f"_{state}"
            (MODELS / f"{machine_id}{suffix}.json").write_text(
                json.dumps(runtime_model(machine_id, textures, state), indent=2) + "\n", encoding="utf-8")
        (BLOCKSTATES / f"{machine_id}.json").write_text(
            json.dumps(blockstate(machine_id, group == "machines"), indent=2) + "\n", encoding="utf-8")
        source_dir = BLOCKBENCH / group
        source_dir.mkdir(parents=True, exist_ok=True)
        source_path = source_dir / f"{machine_id}.bbmodel"
        source_path.write_text(
            json.dumps(blockbench_source(machine_id, profile, SHAPES[machine_id], idle, active), indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8")
        records.append({
            "id": machine_id, "group": group, "elements": len(SHAPES[machine_id]),
            "source": source_path.relative_to(ROOT).as_posix(),
            "models": [f"models/block/{machine_id}.json", f"models/block/{machine_id}_active.json", f"models/block/{machine_id}_fault.json"],
        })

    OUTPUT.mkdir(parents=True, exist_ok=True)
    report = {"schema_version": 1, "profiles": len(records), "geometry_sources": len(records), "records": records, "failures": []}
    report_path = OUTPUT / "blockbench-generation-report.json"
    report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"BLOCKBENCH_GENERATION_OK profiles={len(records)} sources={len(records)} report={report_path}")


if __name__ == "__main__":
    main()
