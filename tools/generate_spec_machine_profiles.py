#!/usr/bin/env python3
"""Append the 30 spec recipe-station machine profiles to machine_profiles.json.

Each profile carries the six face responsibilities, the three state cues, the sound event and
the particle profile that the quality gates require. Prototype and silhouette text is written
against the real-world machine the spec names, not against the block model.
"""

from __future__ import annotations

import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
PROFILES = ROOT / "neoforge-26.2/src/main/resources/data/earth_on_minecraft/earth/quality/machine_profiles.json"

FACES = {
    "bloomery_controller": ("tuyere and slag notch", "bellows linkage", "charging mouth", "tap arch", "flue opening", "ash pit"),
    "bread_oven_controller": ("arched mouth and peel rest", "ash rake door", "wood store", "brick cheek", "dome crown", "hearth floor"),
    "glass_kiln_controller": ("crucible mouth and gather hole", "flue and damper", "batch charging chute", "glass gather port", "crown and vent", "firebox and ash"),
    "lime_kiln_controller": ("draw arch and grate", "flue stack", "charging opening", "stoke hole", "stack crown", "ash drawer"),
    "pit_kiln_controller": ("vent hole and lit base", "sealed earth mound", "earth ring", "smoke vent", "capped crown", "ground"),
    "shaft_furnace_controller": ("tap hole and slag notch", "hot blast main", "double tuyere bank", "charging bell", "off-gas offtake", "hearth bottom"),
    "anvil": ("anvil face and horn", "hardie and pritchel holes", "hammer rack", "quench tank", "anvil waist", "stump base"),
    "bar_counter": ("bottle rail and speed pourers", "back bar mirror and shelves", "ice well", "glass washer", "counter top", "foot rail"),
    "campfire": ("open fire pit and grill", "log feed", "windward stone wall", "ember rake", "cooking grate", "ash bed"),
    "carpentry_bench": ("bench dog holes and vise", "tool board", "board jack", "saw till", "worktop", "trestle base"),
    "casting_table": ("mold cavity and pour lip", "cooling fin bank", "ingot mold rack", "quench trough", "molten metal launder", "sand bed"),
    "coffee_roaster": ("roast drum and sight glass", "chaff collector", "green bean hopper", "cooling tray", "exhaust hood", "burner base"),
    "condenser": ("vapor inlet and sight glass", "coolant return", "coolant feed", "condensate outlet", "vapor dome", "water seal sump"),
    "converter": ("tuyere line and slag notch", "off-gas hood", "blast inlet", "copper tap hole", "charging mouth", "hearth bottom"),
    "curing_barn": ("vent louver bank", "fan wall", "hanging rack", "loading door", "ridge vent", "dirt floor"),
    "fermenting_crock": ("sealed lid and airlock", "sample tap", "brine gutter", "drain valve", "crock mouth", "stone plinth"),
    "filter_press": ("plate stack and filter cloth", "hydraulic ram", "slurry feed manifold", "filtrate outlet", "feed screw", "drip tray"),
    "gem_cutter": ("dop arm and facet head", "coolant pump", "index gear", "rough gem chuck", "lubricant drip", "swarf tray"),
    "herb_processing_table": ("cutting board and sieve", "drying rack", "wash basin", "mortar and pestle", "drying surface", "scrap bin"),
    "loom": ("heddle frame and reed", "warp beam", "shuttle race", "cloth beam", "warp tension bar", "treadle pit"),
    "mill": ("millstones and feed shoe", "sack hoist", "grain hopper", "flour spout", "runner stone", "meal bin"),
    "noodle_press": ("roller gap and cutter", "crank handle", "dough hopper", "noodle tray", "press roller", "crumb tray"),
    "oil_press": ("press cage and wedge", "ram screw", "oilseed hopper", "oil spout", "pressing plate", "cake trough"),
    "rice_huller": ("husking roller pair", "drive pulley", "paddy hopper", "brown rice outlet", "screen shoe", "husk chute"),
    "smelter": ("tap hole and slag notch", "burner port", "charge door", "metal launder", "charging mouth", "crucible hearth"),
    "smokehouse": ("smoke chamber door", "firebox damper", "meat rail", "draft vent", "smoke flue", "ash pit"),
    "steamer": ("lid and steam vent", "water feed", "fire door", "steam outlet", "basket seat", "boiler base"),
    "steamer_basket": ("stacked basket tiers", "bamboo lid", "steam inlet", "condensate drip", "top tier rim", "water pot"),
    "still": ("pot still and lyne arm", "condenser coil", "wash inlet", "spirit receiver", "swan neck", "furnace base"),
    "stone_mill": ("quern stones and eye", "handle socket", "grain feed", "meal spout", "runner stone", "stone base"),
    "stove": ("burner ring and wok seat", "fuel door", "firebox draft", "oven door", "cast iron plate", "ash drawer"),
    "tablet_press": ("die set and upper punch", "hopper and feed shoe", "ejector cam", "tablet chute", "lower punch", "dust tray"),
    "tannery": ("tanning pit and beam", "liquor pump", "hide rack", "fleshing beam", "soak vat", "drain gutter"),
    "tea_kiln": ("wok and stirrer", "fire door", "leaf hopper", "cooling tray", "wok rim", "brick base"),
    "thickener": ("feed well and rake drive", "overflow launder", "flocculant dosing", "underflow outlet", "raking arms", "cone bottom"),
    "water_treatment_unit": ("filter bank and gauge panel", "backwash drain", "raw water inlet", "treated water outlet", "pressure vessel top", "brine sump"),
}

PROTOTYPES = {
    "bloomery_controller": ("clay-and-stone shaft furnace with a tuyere and tap arch", "土石竖穴炉，带风口与出渣口"),
    "bread_oven_controller": ("brick dome oven with an arched mouth and a peel", "砖砌穹顶窑，带拱形窑口与面包铲"),
    "glass_kiln_controller": ("domed kiln with a central crucible and a gather port", "穹顶窑，中央坩埚与取料口"),
    "lime_kiln_controller": ("square masonry shaft kiln with a draw arch", "方形砖砌竖窑，底部有出灰拱口"),
    "pit_kiln_controller": ("stacked wood mound sealed under earth with vent holes", "木材堆覆土封窑，留通气孔"),
    "shaft_furnace_controller": ("tall brick shaft furnace with hot blast and a taphole", "高型砖砌竖炉，配热风管与出铁口"),
    "anvil": ("single-horn blacksmith anvil on a stump", "单角铁砧与木墩"),
    "bar_counter": ("back-bar cocktail station with speed rail", "带酒架的调酒吧台"),
    "campfire": ("stone-ringed open cooking fire", "石圈明火烤位"),
    "carpentry_bench": ("joiners bench with tail vise and dogs", "带尾虎钳与台面榫的木工台"),
    "casting_table": ("open ingot casting table with cooling fins", "带散热片的敞口浇铸台"),
    "coffee_roaster": ("drum coffee roaster with cooling tray", "带冷却盘的滚筒烘豆机"),
    "condenser": ("water-jacketed shell-and-tube condenser", "水套列管冷凝器"),
    "converter": ("Peirce-Smith style copper converter", "卧式铜吹炼转炉"),
    "curing_barn": ("ventilated tobacco curing barn", "通风烤烟房"),
    "fermenting_crock": ("stoneware fermentation crock with airlock", "带气密盖的陶制发酵缸"),
    "filter_press": ("plate-and-frame filter press", "板框压滤机"),
    "gem_cutter": ("faceting head on a mast with drip coolant", "带滴水冷却的刻面机"),
    "herb_processing_table": ("herbal preparation bench with drying rack", "带晾架的药材处理台"),
    "loom": ("four-shaft counterbalance floor loom", "四综平衡式织布机"),
    "mill": ("water-powered stone flour mill", "水轮驱动的石磨磨坊"),
    "noodle_press": ("roller noodle press with cutter comb", "带切刀梳的辊式压面机"),
    "oil_press": ("wedge press oil mill with cage", "楔式榨油机"),
    "rice_huller": ("rubber roller rice huller", "胶辊砻谷机"),
    "smelter": ("low-temperature shaft melting furnace", "低温竖式熔炼炉"),
    "smokehouse": ("offset firebox smokehouse", "外置火箱熏房"),
    "steamer": ("direct-fired steamer pot with basket seat", "直火蒸锅"),
    "steamer_basket": ("stacked bamboo steamer baskets", "叠层竹蒸笼"),
    "still": ("copper pot still with worm condenser", "铜制壶式蒸馏器与盘管冷凝器"),
    "stone_mill": ("hand quern with runner stone", "手推石磨"),
    "stove": ("cast-iron range with wok ring", "带炒锅圈的铸铁灶台"),
    "tablet_press": ("single-punch tablet press", "单冲压片机"),
    "tannery": ("tanning pit with fleshing beam", "带刮皮梁的制革槽"),
    "tea_kiln": ("tea firing wok over a brick firebox", "砖砌火箱上的炒茶锅"),
    "thickener": ("center-drive rake thickener", "中心传动耙式浓密机"),
    "water_treatment_unit": ("pressurized media filter skid", "承压滤料水处理撬装单元"),
}

SILHOUETTES = {
    "bloomery_controller": "tapered clay shaft on a squat stone plinth",
    "bread_oven_controller": "low brick dome on a square plinth",
    "glass_kiln_controller": "tall domed kiln with a side gather port",
    "lime_kiln_controller": "narrow square masonry shaft",
    "pit_kiln_controller": "low rounded earthen mound",
    "shaft_furnace_controller": "tall brick shaft on a broad plinth",
    "anvil": "waisted steel block on a tapered stump with a horn on one end",
    "bar_counter": "long low counter with a raised back bar and bottle rail",
    "campfire": "low ring of stones around a burning log pile with a grate",
    "carpentry_bench": "long heavy worktop on trestles with a vise at one end",
    "casting_table": "broad shallow table with mold recesses and finned edges",
    "coffee_roaster": "horizontal drum over a burner with a round cooling tray beside it",
    "condenser": "vertical shell with tube bundle and two coolant stubs",
    "converter": "horizontal barrel on trunnions with a short hood and tuyere line",
    "curing_barn": "tall narrow shed with louvered walls and a ridge vent",
    "fermenting_crock": "round-bellied glazed crock with a clamped lid and airlock",
    "filter_press": "long frame holding a stack of plates behind a hydraulic ram",
    "gem_cutter": "small mast and dop arm over a rotating lap with a drip can",
    "herb_processing_table": "waist-high bench with a rack above and drawers below",
    "loom": "upright timber frame with a warp beam, heddles, and cloth beam",
    "mill": "round stone housing with a hopper above and a flour spout below",
    "noodle_press": "tabletop frame with two rollers and a cutter comb",
    "oil_press": "stout cage of wooden slats under a screw and wedge",
    "rice_huller": "compact twin-roller housing with a hopper and two outlets",
    "smelter": "short squat shaft with a tap hole and a launder at the base",
    "smokehouse": "small gabled shed with a firebox attached at the base",
    "steamer": "wide pot with a domed lid and a basket ring on top",
    "steamer_basket": "cylindrical stack of woven trays with a domed lid",
    "still": "bulbous copper pot with a swan neck into a coiled condenser",
    "stone_mill": "two stacked round stones with a side handle and a feed eye",
    "stove": "flat iron plate with burner rings over a firebox and ash drawer",
    "tablet_press": "narrow vertical press with a hopper and a tablet chute",
    "tannery": "sunken vat beside a slanted beam and a hide rack",
    "tea_kiln": "round wok set into a brick base with a fire door",
    "thickener": "wide shallow tank with a center drive and a conical bottom",
    "water_treatment_unit": "upright pressure vessel with a gauge panel and pipe manifold",
}

CUES = {
    "bloomery_controller": ("cold stack and empty tuyere", "blast roars, charge sinks, slag runs from the arch", "blast stopped or the stack choked with ash"),
    "bread_oven_controller": ("cold dome and swept hearth", "embers glow under the dome and loaves rise", "dome cooled and the batch raw"),
    "glass_kiln_controller": ("cold crown and empty crucible", "batch melts to a bright pool under the crown", "crown too cold and the batch frozen"),
    "lime_kiln_controller": ("cold stack and empty grate", "charge settles as lime drops through the grate", "flue blocked and the draw arch cold"),
    "pit_kiln_controller": ("capped mound and cold vent", "smoke seeps from the vents while the wood chars", "air leaking and the wood burning to ash"),
    "shaft_furnace_controller": ("cold shell and plugged tap", "tuyeres blast and iron runs from the tap hole", "bath frozen and the tap blocked"),
    "anvil": ("cold stock on the face and a still hammer", "hammer falls and orange scale flakes off", "stock below forging heat with a dull ring"),
    "bar_counter": ("dry shaker and empty glass rail", "shaker rhythm, ice clink, and pour stream", "missing ingredient at the station"),
    "campfire": ("cold ash and unlit logs", "flame flicker, ember rise, and grease smoke", "fire out or food burnt black"),
    "carpentry_bench": ("still vise and stacked boards", "plane shavings curl off and the saw runs", "blade binding in the kerf"),
    "casting_table": ("cold molds and empty launder", "metal fills the mold with a bright meniscus", "mold too cold and metal frozen short"),
    "coffee_roaster": ("cold drum and pale green beans", "drum turns, beans darken, chaff blows off", "roast stalled or scorched"),
    "condenser": ("dry shell and still sight glass", "condensate beads and drips steadily", "coolant lost and vapor venting"),
    "converter": ("cold barrel and closed tuyeres", "air blast roars and the bath brightens", "bath frozen or off-gas backed up"),
    "curing_barn": ("closed louvers and still air", "louvers open with slow warm airflow", "humidity too high and leaf molding"),
    "fermenting_crock": ("still airlock and cloudy must", "slow bubble through the airlock", "airlock dry or the batch soured"),
    "filter_press": ("open plates and dry cloth", "ram closes, filtrate runs clear", "cloth blinded and pressure climbing"),
    "gem_cutter": ("still dop and dry lap", "lap spins with a fine coolant mist", "facet misaligned and the stone chipped"),
    "herb_processing_table": ("dry board and empty rack", "knife work, sieve shake, and warm drying air", "herb scorched or the sieve clogged"),
    "loom": ("slack warp and parked shuttle", "heddles lift, shuttle flies, cloth advances", "warp broken and the shed collapsed"),
    "mill": ("still stones and empty hopper", "stones turn with flour dust at the spout", "hopper empty or the stone glazed"),
    "noodle_press": ("dry rollers and empty tray", "rollers turn and noodles fall onto the tray", "dough too wet and the cutter clogged"),
    "oil_press": ("slack screw and dry cage", "screw tightens and oil runs from the spout", "cake jammed and the ram stalled"),
    "rice_huller": ("still rollers and full hopper", "rollers spin with husk dust and grain rattle", "roller gap wrong and grain broken"),
    "smelter": ("cold crucible and plugged tap", "bath glows and metal runs from the tap", "charge above the melt limit"),
    "smokehouse": ("cold chamber and closed damper", "smoke drifts and the rail glistens", "fire smoldering out or the chamber too hot"),
    "steamer": ("cold pot and dry basket seat", "steam rises and the lid rattles gently", "boiler dry and the pot scorching"),
    "steamer_basket": ("cold tiers and dry pot", "steam pushes through every tier", "water boiled off and the tiers dry"),
    "still": ("cold pot and still coil", "spirit drips from the worm in a steady thread", "mash scorched and the run stalled"),
    "stone_mill": ("still runner and empty eye", "runner turns with meal falling at the rim", "grain feed stopped and the stones rubbing"),
    "stove": ("cold plate and closed dampers", "burner glow with a steady simmer", "pan scorched and the plate smoking"),
    "tablet_press": ("raised punch and empty die", "punch cycles and tablets drop into the chute", "die overloaded and the punch jammed"),
    "tannery": ("dry beam and empty pit", "liquor circulates and hides soak", "liquor spent and the hide unfinished"),
    "tea_kiln": ("cold wok and dry leaves", "leaves turn in the wok with a warm roast aroma", "wok too hot and the leaf edges scorched"),
    "thickener": ("still rake and clear overflow", "rake turns and a clear overflow runs", "underflow blocked and the bed rising"),
    "water_treatment_unit": ("dry vessels and a steady gauge", "pumps run and the gauge holds", "filter blinded and the pressure dropping"),
}

PARTICLES = {
    "bloomery_controller": "bloomery_sparks",
    "bread_oven_controller": "oven_embers",
    "glass_kiln_controller": "kiln_heat_shimmer",
    "lime_kiln_controller": "lime_dust",
    "pit_kiln_controller": "pit_kiln_smoke",
    "shaft_furnace_controller": "furnace_iron_sparks",
    "anvil": "forge_scale",
    "bar_counter": "chilled_mist",
    "campfire": "ember_smoke",
    "carpentry_bench": "wood_shavings",
    "casting_table": "molten_glow",
    "coffee_roaster": "roast_chaff",
    "condenser": "condensate_drip",
    "converter": "sulfurous_offgas",
    "curing_barn": "warm_draft",
    "fermenting_crock": "ferment_bubbles",
    "filter_press": "filtrate_trickle",
    "gem_cutter": "coolant_spray",
    "herb_processing_table": "herb_dust",
    "loom": "lint_fibers",
    "mill": "flour_dust",
    "noodle_press": "flour_puff",
    "oil_press": "oil_beads",
    "rice_huller": "husk_dust",
    "smelter": "metal_fumes",
    "smokehouse": "smoke_wisps",
    "steamer": "steam_plume",
    "steamer_basket": "basket_steam",
    "still": "spirit_vapor",
    "stone_mill": "stone_meal",
    "stove": "cooking_steam",
    "tablet_press": "powder_puff",
    "tannery": "tanning_liquor",
    "tea_kiln": "tea_aroma",
    "thickener": "floc_mist",
    "water_treatment_unit": "filter_spray",
}

FAMILIES = {
    "bloomery_controller": "thermal",
    "bread_oven_controller": "thermal",
    "glass_kiln_controller": "thermal",
    "lime_kiln_controller": "thermal",
    "pit_kiln_controller": "thermal",
    "shaft_furnace_controller": "thermal",
    "anvil": "forming", "bar_counter": "mixing", "campfire": "thermal", "carpentry_bench": "forming",
    "casting_table": "forming", "coffee_roaster": "thermal", "condenser": "column", "converter": "thermal",
    "curing_barn": "thermal", "fermenting_crock": "reaction", "filter_press": "wet_process",
    "gem_cutter": "forming", "herb_processing_table": "mixing", "loom": "forming", "mill": "comminution",
    "noodle_press": "forming", "oil_press": "forming", "rice_huller": "comminution", "smelter": "thermal",
    "smokehouse": "thermal", "steamer": "thermal", "steamer_basket": "thermal", "still": "column",
    "stone_mill": "comminution", "stove": "thermal", "tablet_press": "forming", "tannery": "wet_process",
    "tea_kiln": "thermal", "thickener": "wet_process", "water_treatment_unit": "wet_process",
}

ORDER = [
    "bloomery_controller", "bread_oven_controller", "glass_kiln_controller", "lime_kiln_controller", "pit_kiln_controller", "shaft_furnace_controller",
    "anvil", "bar_counter", "campfire", "carpentry_bench", "casting_table", "coffee_roaster",
    "condenser", "converter", "curing_barn", "fermenting_crock", "filter_press", "gem_cutter",
    "herb_processing_table", "loom", "mill", "noodle_press", "oil_press", "rice_huller",
    "smelter", "smokehouse", "steamer", "steamer_basket", "still", "stone_mill", "stove",
    "tablet_press", "tannery", "tea_kiln", "thickener", "water_treatment_unit",
]


def build(machine_id: str) -> dict[str, object]:
    front, back, left, right, top, bottom = FACES[machine_id]
    idle, running, fault = CUES[machine_id]
    prototype, prototype_zh = PROTOTYPES[machine_id]
    return {
        "id": machine_id,
        "kind": machine_id.removesuffix("_controller").upper(),
        "family": FAMILIES[machine_id],
        "prototype": prototype,
        "prototype_zh": prototype_zh,
        "silhouette": SILHOUETTES[machine_id],
        "faces": {"front": front, "back": back, "left": left, "right": right, "top": top, "bottom": bottom},
        "idle_cue": idle,
        "running_cue": running,
        "fault_cue": fault,
        "sound_event": f"earth_on_minecraft:machine.{machine_id}.run",
        "particle_profile": PARTICLES[machine_id],
    }


def main() -> None:
    data = json.loads(PROFILES.read_text(encoding="utf-8"))
    existing = {profile["id"] for profile in data["machines"]}
    added = []
    for machine_id in ORDER:
        if machine_id in existing:
            continue
        data["machines"].append(build(machine_id))
        added.append(machine_id)
    PROFILES.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"MACHINE_PROFILES_ADDED count={len(added)} total={len(data['machines'])}")


if __name__ == "__main__":
    main()
