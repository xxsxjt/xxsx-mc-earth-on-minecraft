#!/usr/bin/env python3
"""Generate Earth on Minecraft item textures through the local Agnes provider config."""

from __future__ import annotations

import argparse
import base64
from collections import deque
import json
import pathlib
import re
import time
import urllib.parse

import requests
from PIL import Image


ROOT = pathlib.Path(__file__).resolve().parents[1]
RES = ROOT / "neoforge-26.2" / "src" / "main" / "resources"
ASSETS = RES / "assets" / "earth_on_minecraft"
TMP = ROOT / "tmp" / "imagegen" / "agnes-modern-chemistry"
PROVIDERS = pathlib.Path(r"C:\Users\du_ji\WorkBuddy\agnes\providers.json")

MODEL = "agnes-image-2.1-flash"
ENDPOINT = "https://apihub.agnes-ai.com/v1/images/generations"

ITEMS = {
    "field_geology_notebook": ("野外地质手册", "Field Geology Notebook", "a rugged field geology notebook with green cover, mineral sample corner, small compass clip, no readable text"),
    "magnetite_chunk": ("磁铁矿碎块 Fe3O4", "Magnetite Chunk Fe3O4", "a heavy black metallic magnetite ore chunk with angular Fe3O4 crystal faces"),
    "chalcopyrite_chunk": ("黄铜矿碎块 CuFeS2", "Chalcopyrite Chunk CuFeS2", "a brass-gold chalcopyrite ore chunk with iridescent tarnish and dark host rock"),
    "auriferous_quartz_chunk": ("含金石英碎块", "Auriferous Quartz Chunk", "a milky quartz chunk with tiny native gold specks"),
    "kimberlite_chunk": ("金伯利岩碎块", "Kimberlite Chunk", "a green gray kimberlite breccia chunk with dark mantle fragments"),
    "cinnabar_chunk": ("辰砂碎块 HgS", "Cinnabar Chunk HgS", "a vivid red cinnabar mineral chunk in pale rock"),
    "magnetite_dust": ("磁铁矿粉 Fe3O4", "Magnetite Dust Fe3O4", "a dark black magnetic iron oxide powder pile with subtle metallic grains"),
    "chalcopyrite_dust": ("黄铜矿粉 CuFeS2", "Chalcopyrite Dust CuFeS2", "a brass yellow copper sulfide powder pile with dark mineral speckles"),
    "iron_concentrate": ("铁精矿", "Iron Concentrate", "a dense dark iron concentrate pellet pile with metallic black-red highlights"),
    "sponge_iron": ("海绵铁", "Sponge Iron", "a porous dark gray mass of metallic iron nodules with visible sponge-like cavities"),
    "wrought_iron": ("熟铁", "Wrought Iron", "a compact hand-forged low-carbon iron billet with subtle hammer facets and warm gray metallic highlights"),
    "copper_concentrate": ("铜精矿", "Copper Concentrate", "a golden brown copper concentrate powder pile with sulfide sparkle"),
    "crude_oil_sample": ("原油样品", "Crude Oil Sample", "a small dark crude oil vial with black-brown liquid"),
    "natural_gas_cell": ("天然气单元 CH4 等", "Natural Gas Cell CH4 Mix", "a pale blue compressed gas cell with a metal cap"),
    "naphtha": ("石脑油馏分", "Naphtha Fraction", "a light amber petrochemical distillate vial"),
    "kerosene_fraction": ("煤油馏分", "Kerosene Fraction", "a golden kerosene distillate vial"),
    "diesel_fraction": ("柴油馏分", "Diesel Fraction", "a darker amber diesel distillate vial"),
    "lubricating_oil": ("润滑油", "Lubricating Oil", "a thick olive-black lubricating oil canister"),
    "asphalt": ("沥青", "Asphalt", "a matte black asphalt chunk with subtle aggregate speckles"),
    "petroleum_coke": ("石油焦", "Petroleum Coke", "a porous dark petroleum coke lump"),
    "wood_chips": ("木片", "Wood Chips", "several tan brown wood chips"),
    "cellulose_pulp": ("纤维素浆", "Cellulose Pulp", "a beige wet cellulose pulp clump"),
    "bleached_pulp": ("漂白浆", "Bleached Pulp", "a clean off-white bleached paper pulp clump"),
    "cellulose_fiber": ("纤维素纤维", "Cellulose Fiber", "cream colored cellulose fibers bundled like string"),
    "titanium_dioxide": ("二氧化钛 TiO2", "Titanium Dioxide TiO2", "bright white titanium dioxide pigment powder"),
    "iron_oxide_pigment": ("氧化铁颜料 Fe2O3", "Iron Oxide Pigment Fe2O3", "red iron oxide pigment powder"),
    "carbon_black": ("炭黑 C", "Carbon Black C", "deep black carbon black powder"),
    "paint_base": ("涂料基料", "Paint Base", "a small neutral paint base bucket"),
    "styrene": ("苯乙烯 C8H8", "Styrene C8H8", "a clear pale aromatic monomer vial"),
    "polystyrene_resin": ("聚苯乙烯树脂 PS", "Polystyrene Resin PS", "white polystyrene resin pellets"),
    "ethylene_glycol": ("乙二醇 C2H6O2", "Ethylene Glycol C2H6O2", "a clear blue-tinted ethylene glycol vial"),
    "terephthalic_acid": ("对苯二甲酸 C8H6O4", "Terephthalic Acid C8H6O4", "off-white terephthalic acid crystal powder"),
    "pet_resin": ("PET 聚酯树脂", "PET Resin", "translucent pale blue PET resin pellets"),
    "synthetic_rubber": ("合成橡胶", "Synthetic Rubber", "a black synthetic rubber sheet roll"),
    "caprolactam": ("己内酰胺 C6H11NO", "Caprolactam C6H11NO", "pale caprolactam crystals"),
    "nylon_fiber": ("尼龙纤维", "Nylon Fiber", "off-white nylon fibers bundled like thread"),
    "graphite_dust": ("石墨粉 C", "Graphite Dust C", "dark gray flaky graphite powder"),
    "activated_carbon": ("活性炭", "Activated Carbon", "porous black activated carbon granules"),
    "battery_carbon": ("电池级碳粉", "Battery Grade Carbon", "fine conductive black battery carbon powder"),
    "manganese_oxide_dust": ("锰氧化物粉 MnO2", "Manganese Oxide Dust MnO2", "dark brown manganese oxide powder"),
    "nickel_precursor": ("镍前驱体", "Nickel Precursor", "green nickel battery precursor crystals"),
    "lithium_salt": ("锂盐", "Lithium Salt", "pale white lithium salt crystals"),
    "electrolyte": ("电解液", "Electrolyte", "clear blue electrolyte vial for batteries"),
    "electrode_sheet": ("电极片", "Electrode Sheet", "thin layered black and copper battery electrode sheet"),
    "simple_battery_cell": ("简易电池单元", "Simple Battery Cell", "small cylindrical battery cell with metal caps"),
    "hard_water_sample": ("硬水样品", "Hard Water Sample", "cloudy mineral rich water sample vial"),
    "softened_water": ("软化水", "Softened Water", "clear softened water vial with pale blue tint"),
    "sludge_cake": ("污泥饼", "Sludge Cake", "dark brown pressed industrial sludge cake"),
    "neutral_salt": ("中和盐", "Neutral Salt", "mixed white gray neutralization salt crystals"),
    "activated_carbon_filter": ("活性炭滤料", "Activated Carbon Filter", "black activated carbon filter cartridge"),
    "lime_treatment_residue": ("石灰处理渣", "Lime Treatment Residue", "off-white chalky lime treatment residue clump"),
    "stabilized_tailings": ("稳定化尾矿", "Stabilized Tailings", "gray stabilized tailings aggregate blocky lump"),
    "natural_latex": ("天然胶乳", "Natural Latex", "milky white natural latex in a small sample cup"),
    "raw_rubber": ("粗橡胶", "Raw Rubber", "pale tan raw rubber lump"),
    "vulcanized_rubber": ("硫化橡胶", "Vulcanized Rubber", "dark vulcanized rubber slab"),
    "rubber_compound": ("橡胶复合料", "Rubber Compound", "black rubber compound sheet with carbon filler"),
    "rubber_gasket": ("橡胶密封圈", "Rubber Gasket", "small black rubber gasket ring"),
    "ethanol": ("乙醇 C2H5OH", "Ethanol C2H5OH", "clear ethanol vial with faint blue highlight"),
    "acetic_acid": ("乙酸 CH3COOH", "Acetic Acid CH3COOH", "clear acetic acid vial with sharp white shine"),
    "acetone": ("丙酮 C3H6O", "Acetone C3H6O", "clear solvent vial with pale purple tint"),
    "phenol": ("苯酚 C6H5OH", "Phenol C6H5OH", "pale amber phenol crystals in a small jar"),
    "phenolic_resin": ("酚醛树脂", "Phenolic Resin", "dark red brown phenolic resin pellets"),
    "epoxy_resin": ("环氧树脂", "Epoxy Resin", "golden translucent epoxy resin droplet"),
    "industrial_solvent": ("工业溶剂", "Industrial Solvent", "clear industrial solvent canister"),
    "chromite_dust": ("铬铁矿粉 FeCr2O4", "Chromite Dust FeCr2O4", "dark brown black chromite mineral powder"),
    "ferrochrome": ("铬铁 FeCr", "Ferrochrome FeCr", "dark metallic ferrochrome alloy lump"),
    "ferromanganese": ("锰铁 FeMn", "Ferromanganese FeMn", "gray metallic ferromanganese alloy lump"),
    "stainless_steel_bloom": ("不锈钢坯", "Stainless Steel Bloom", "bright silver stainless steel billet"),
    "aluminum_alloy_billet": ("铝合金坯", "Aluminum Alloy Billet", "pale silver aluminum alloy billet"),
    "magnesium_dust": ("镁粉 Mg", "Magnesium Dust Mg", "bright pale magnesium metal powder"),
    "titanium_slag": ("钛渣", "Titanium Slag", "dark blue gray titanium slag chunk"),
    "titanium_tetrachloride": ("四氯化钛 TiCl4", "Titanium Tetrachloride TiCl4", "smoky pale titanium tetrachloride vial"),
    "titanium_sponge": ("海绵钛", "Titanium Sponge", "porous silver gray titanium sponge lump"),
    "metallurgical_silicon": ("冶金级硅", "Metallurgical Silicon", "dark silver metallurgical silicon chunk"),
    "chlorosilane": ("氯硅烷", "Chlorosilane", "clear chlorosilane chemical vial"),
    "high_purity_silicon": ("高纯硅", "High Purity Silicon", "bright reflective high purity silicon shard"),
    "polysilicon": ("多晶硅", "Polysilicon", "blue gray polysilicon crystal chunks"),
    "silicon_wafer": ("硅晶圆", "Silicon Wafer", "thin shiny circular silicon wafer"),
    "dopant_dust": ("掺杂剂粉", "Dopant Dust", "tiny violet red semiconductor dopant powder"),
    "photoresist_precursor": ("光刻胶前驱体", "Photoresist Precursor", "amber photoresist precursor vial"),
    "monazite_sand": ("独居石砂", "Monazite Sand", "orange brown monazite heavy mineral sand"),
    "bastnasite_dust": ("氟碳铈矿粉", "Bastnasite Dust", "tan rare earth bastnasite powder"),
    "mixed_rare_earth_oxide": ("混合稀土氧化物", "Mixed Rare Earth Oxide", "pale cream mixed rare earth oxide powder"),
    "neodymium_salt": ("钕盐", "Neodymium Salt", "pink purple neodymium salt crystals"),
    "ndfeb_magnet": ("钕铁硼磁材", "NdFeB Magnet", "small dark metallic neodymium magnet"),
    "rare_earth_tailings": ("稀土尾渣", "Rare Earth Tailings", "gray tan rare earth tailings residue"),
    "iron_catalyst": ("铁基催化剂", "Iron Catalyst", "dark iron catalyst pellets"),
    "vanadium_catalyst": ("钒基催化剂", "Vanadium Catalyst", "orange vanadium catalyst pellets"),
    "nickel_catalyst": ("镍基催化剂", "Nickel Catalyst", "green nickel catalyst pellets"),
    "platinum_group_catalyst": ("铂族催化剂", "Platinum Group Catalyst", "silver platinum group catalyst grains"),
    "sodium_hypochlorite": ("次氯酸钠 NaClO", "Sodium Hypochlorite NaClO", "pale yellow sodium hypochlorite bleach vial"),
    "bleaching_powder": ("漂白粉", "Bleaching Powder", "off-white bleaching powder granules in a small pile"),
    "hydrogen_peroxide": ("过氧化氢 H2O2", "Hydrogen Peroxide H2O2", "clear hydrogen peroxide vial with bright highlights"),
    "soap_base": ("皂基", "Soap Base", "pale cream soap base flakes"),
    "glycerol": ("甘油 C3H8O3", "Glycerol C3H8O3", "clear viscous glycerol vial"),
    "surfactant": ("表面活性剂", "Surfactant", "blue white surfactant paste in a small sample jar"),
    "detergent_powder": ("洗涤剂粉", "Detergent Powder", "white detergent powder with blue speckles"),
    "butadiene": ("丁二烯 C4H6", "Butadiene C4H6", "pale compressed petrochemical gas cell"),
    "acrylonitrile": ("丙烯腈 C3H3N", "Acrylonitrile C3H3N", "clear acrylonitrile chemical vial"),
    "abs_resin": ("ABS 树脂", "ABS Resin", "ivory ABS plastic pellets"),
    "acrylic_monomer": ("亚克力单体", "Acrylic Monomer", "clear acrylic monomer vial"),
    "acrylic_resin": ("亚克力树脂", "Acrylic Resin", "transparent acrylic resin pellets"),
    "polyol": ("多元醇", "Polyol", "pale syrup polyol sample vial"),
    "isocyanate": ("异氰酸酯", "Isocyanate", "amber isocyanate reagent vial"),
    "polyurethane_foam": ("聚氨酯泡沫", "Polyurethane Foam", "small pale yellow polyurethane foam block"),
    "copper_wire": ("铜线", "Copper Wire", "coiled shiny copper wire"),
    "fiberglass_cloth": ("玻璃纤维布", "Fiberglass Cloth", "woven pale fiberglass cloth square"),
    "copper_clad_laminate": ("覆铜板", "Copper Clad Laminate", "green circuit laminate sheet with copper edge"),
    "printed_circuit_board": ("印刷电路板", "Printed Circuit Board", "small green printed circuit board with copper traces"),
    "solder_alloy": ("焊料合金", "Solder Alloy", "silver solder alloy wire coil"),
    "solder_flux": ("助焊剂", "Solder Flux", "amber solder flux paste jar"),
    "led_phosphor": ("LED 荧光粉", "LED Phosphor", "bright yellow rare earth LED phosphor powder"),
    "ceramic_substrate": ("陶瓷基板", "Ceramic Substrate", "white ceramic electronics substrate plate"),
    "sphalerite_dust": ("闪锌矿粉 ZnS", "Sphalerite Dust ZnS", "brown yellow sphalerite zinc sulfide powder"),
    "zinc_oxide": ("氧化锌 ZnO", "Zinc Oxide ZnO", "fine white zinc oxide powder"),
    "zinc_ingot": ("锌锭", "Zinc Ingot", "blue silver zinc metal ingot"),
    "galena_dust": ("方铅矿粉 PbS", "Galena Dust PbS", "dark lead gray galena powder with metallic grains"),
    "lead_ingot": ("铅锭", "Lead Ingot", "dull heavy blue gray lead ingot"),
    "cassiterite_dust": ("锡石粉 SnO2", "Cassiterite Dust SnO2", "dark brown cassiterite tin ore powder"),
    "tin_ingot": ("锡锭", "Tin Ingot", "bright pale tin metal ingot"),
    "kaolin_dust": ("高岭土粉", "Kaolin Dust", "soft white kaolin clay powder"),
    "refractory_clay": ("耐火黏土", "Refractory Clay", "tan refractory clay lump"),
    "firebrick": ("耐火砖", "Firebrick", "orange tan refractory firebrick"),
    "ceramic_body": ("陶瓷坯料", "Ceramic Body", "gray white ceramic body clay lump"),
    "porcelain_blank": ("瓷坯", "Porcelain Blank", "smooth white porcelain blank piece"),
    "ceramic_insulator": ("陶瓷绝缘件", "Ceramic Insulator", "white ribbed ceramic electrical insulator"),
    "mineral_wool": ("矿物棉", "Mineral Wool", "pale fibrous mineral wool tuft"),
    "ammonium_sulfate": ("硫酸铵", "Ammonium Sulfate", "white ammonium sulfate fertilizer crystals"),
    "single_superphosphate": ("过磷酸钙", "Single Superphosphate", "gray tan single superphosphate fertilizer granules"),
    "urea_formaldehyde_resin": ("脲醛树脂", "Urea Formaldehyde Resin", "cream urea formaldehyde resin granules"),
    "carbon_monoxide_cell": ("一氧化碳单元 CO", "Carbon Monoxide Cell CO", "gray compressed carbon monoxide gas cell"),
    "syngas_cell": ("合成气单元 CO/H2", "Syngas Cell CO/H2", "steel gas cell with blue gray synthesis gas glow"),
    "borate_mineral_dust": ("硼酸盐矿物粉", "Borate Mineral Dust", "white tan borate mineral powder with salt crystal flecks"),
    "boron_carbide_pellet": ("碳化硼芯块 B4C", "Boron Carbide Pellet B4C", "small matte black boron carbide ceramic pellet"),
    "uraninite_dust": ("沥青铀矿粉 UO2/U3O8", "Uraninite Dust UO2/U3O8", "dark heavy uranium oxide mineral powder with olive black grains"),
    "yellowcake": ("黄饼氧化铀 U3O8", "Yellowcake Uranium Oxide U3O8", "ochre yellow uranium oxide concentrate powder in a sealed sample cup"),
    "uranium_hexafluoride_cell": ("六氟化铀单元 UF6", "Uranium Hexafluoride Cell UF6", "sealed gray metal gas cell with pale yellow hazard-free uranium chemistry accent"),
    "low_enriched_uranium": ("低浓缩铀材料", "Low-Enriched Uranium Material", "small sealed olive gray low enriched uranium material canister"),
    "depleted_uranium": ("贫铀材料 U-238", "Depleted Uranium Material U-238", "dense dark gray depleted uranium metal billet in sealed industrial wrapping"),
    "uranium_dioxide_powder": ("二氧化铀粉 UO2", "Uranium Dioxide Powder UO2", "black uranium dioxide ceramic powder in a shielded tray"),
    "nuclear_fuel_pellet": ("核燃料芯块 UO2", "Nuclear Fuel Pellet UO2", "small dark cylindrical uranium dioxide ceramic fuel pellet"),
    "zirconium_alloy_tube": ("锆合金包壳管", "Zirconium Alloy Cladding Tube", "pale silver zirconium alloy tube segment"),
    "control_rod_assembly": ("控制棒组件", "Control Rod Assembly", "compact dark control rod bundle with black absorber rods and metal spacer"),
    "nuclear_fuel_rod": ("核燃料棒", "Nuclear Fuel Rod", "slender sealed nuclear fuel rod with silver cladding and dark end caps"),
    "nuclear_fuel_assembly": ("核燃料组件", "Nuclear Fuel Assembly", "small square nuclear fuel rod bundle assembly, clean industrial icon"),
    "spent_fuel_assembly": ("乏燃料组件", "Spent Fuel Assembly", "dark used fuel assembly sealed in blue gray cooling frame"),
    "dry_storage_cask": ("干式贮存罐", "Dry Storage Cask", "short cylindrical concrete and steel dry storage cask icon"),
    "nuclear_heat_module": ("核热模块", "Nuclear Heat Module", "shielded reactor heat module with steel casing and warm amber core window"),
    "copper_busbar": ("铜母排", "Copper Busbar", "flat polished copper busbar strips with bolt holes"),
    "transformer_core": ("变压器核心", "Transformer Core", "laminated iron transformer core with copper windings"),
    "grid_switchgear": ("电网开关柜组件", "Grid Switchgear Assembly", "compact electrical switchgear module with breakers and copper terminals"),
    "generator_stator": ("发电机定子", "Generator Stator", "round generator stator ring with copper windings"),
    "steam_turbine_assembly": ("蒸汽轮机组件", "Steam Turbine Assembly", "silver turbine rotor and casing segment, modern industrial icon"),
    "industrial_sensor": ("工业传感器", "Industrial Sensor", "small rugged industrial sensor module with lens and metal bracket"),
    "plc_controller": ("PLC 控制器", "PLC Controller", "compact programmable logic controller with terminals, no readable text"),
    "servo_motor": ("伺服电机", "Servo Motor", "small blue gray servo motor with copper coil hint and shaft"),
    "actuator_module": ("执行器模块", "Actuator Module", "linear actuator module with motor and metal rod"),
    "robotic_arm": ("工业机器人臂", "Industrial Robotic Arm", "compact orange and gray industrial robotic arm icon"),
    "machine_vision_camera": ("机器视觉相机", "Machine Vision Camera", "small machine vision camera with glass lens and LED ring"),
    "quality_inspection_module": ("质量检测模块", "Quality Inspection Module", "industrial inspection module with camera lens and sample tray"),
    "automation_bus": ("自动化总线", "Automation Bus", "shielded data and power bus cable with blue signal stripe"),
    "conveyor_drive": ("输送驱动机构", "Conveyor Drive", "motorized conveyor drive roller and rubber belt segment"),
    "redstone_io_gateway": ("红石 I/O 网关", "Redstone I/O Gateway", "redstone interface module with circuit board and red connector"),
}

NON_CHEMICAL_PRODUCT_ITEMS = {
    "field_geology_notebook",
    "magnetite_chunk",
    "chalcopyrite_chunk",
    "auriferous_quartz_chunk",
    "kimberlite_chunk",
    "cinnabar_chunk",
    "magnetite_dust",
    "chalcopyrite_dust",
    "iron_concentrate",
    "sponge_iron",
    "wrought_iron",
    "copper_concentrate",
}


def load_agnes_key() -> str:
    raw = PROVIDERS.read_text(encoding="utf-8", errors="ignore")
    try:
        data = json.loads(raw)
        keys = data["agnes"]["keys"]
        if isinstance(keys, list) and keys:
            return str(keys[0])
    except Exception:
        pass

    agnes_pos = raw.find('"agnes"')
    if agnes_pos < 0:
        raise RuntimeError("Agnes provider not found in providers.json.")
    next_provider = raw.find('\n  "', agnes_pos + 8)
    agnes_raw = raw[agnes_pos: next_provider if next_provider > agnes_pos else len(raw)]
    keys_match = re.search(r'"keys"\s*:\s*\[(.*?)\]', agnes_raw, re.S)
    if not keys_match:
        raise RuntimeError("Agnes key list not found in providers.json.")
    keys = re.findall(r'"([^"]+)"', keys_match.group(1))
    if not keys:
        raise RuntimeError("Agnes provider has no usable keys.")
    return keys[0]


def write_json(path: pathlib.Path, data: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def update_resource_metadata() -> None:
    items_dir = ASSETS / "items"
    models_dir = ASSETS / "models" / "item"
    for item_id in ITEMS:
        write_json(items_dir / f"{item_id}.json", {
            "model": {
                "type": "minecraft:model",
                "model": f"earth_on_minecraft:item/{item_id}",
            }
        })
        write_json(models_dir / f"{item_id}.json", {
            "parent": "minecraft:item/generated",
            "textures": {
                "layer0": f"earth_on_minecraft:item/{item_id}",
            },
        })

    for lang_name, name_index in [("zh_cn.json", 0), ("en_us.json", 1)]:
        path = ASSETS / "lang" / lang_name
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        for item_id, names in ITEMS.items():
            data[f"item.earth_on_minecraft.{item_id}"] = names[name_index]
        write_json(path, data)

    tag_path = RES / "data" / "earth_on_minecraft" / "tags" / "item" / "chemical_products.json"
    tag = json.loads(tag_path.read_text(encoding="utf-8-sig"))
    values = tag.setdefault("values", [])
    for item_id in ITEMS:
        if item_id in NON_CHEMICAL_PRODUCT_ITEMS:
            continue
        value = f"earth_on_minecraft:{item_id}"
        if value not in values:
            values.append(value)
    write_json(tag_path, tag)

    manifest_path = RES / "data" / "earth_on_minecraft" / "earth" / "industry" / "chemical_industry_manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
    manifest["scope"] = "modern first playable chemistry coverage"
    industries = manifest.setdefault("industries", [])
    for entry in [
        "petroleum refining",
        "natural gas reforming",
        "naphtha steam cracking",
        "kerosene/diesel fraction upgrading",
        "lubricants/asphalt/petroleum coke",
        "wood pulping and paper",
        "cellulose fiber",
        "titanium dioxide pigment",
        "iron oxide pigment",
        "carbon black",
        "paint base",
        "polystyrene",
        "PET polyester",
        "synthetic rubber",
        "nylon fiber",
        "battery materials",
        "graphite anode material",
        "lithium salt electrolyte",
        "nickel manganese battery precursors",
        "simple battery cells",
        "water softening",
        "activated carbon filtration",
        "wastewater neutralization",
        "tailings stabilization",
        "sludge cake sintering",
        "natural latex and rubber vulcanization",
        "rubber compounding and gaskets",
        "ethanol fermentation",
        "acetic acid and acetone solvents",
        "phenolic resin",
        "epoxy resin",
        "stainless steel alloying",
        "aluminum alloying",
        "ferrochrome and ferromanganese",
        "titanium chloride and sponge titanium",
        "metallurgical silicon",
        "chlorosilane purification",
        "polysilicon and silicon wafers",
        "semiconductor dopants",
        "photoresist precursor",
        "monazite and bastnasite rare earth processing",
        "mixed rare earth oxide",
        "neodymium magnet material",
        "industrial catalysts",
        "chlor-alkali bleach and peroxide",
        "soap, surfactants and detergents",
        "ABS and acrylic polymer chemistry",
        "polyurethane foam",
        "copper wire and printed circuit boards",
        "solder alloys and flux",
        "LED phosphor and ceramic electronics substrates",
        "zinc, lead and tin metallurgy",
        "kaolin ceramics and firebrick",
        "mineral wool insulation",
        "ammonium sulfate and superphosphate fertilizer",
        "urea formaldehyde resin",
        "syngas and carbon monoxide processing",
    ]:
        if entry not in industries:
            industries.append(entry)
    manifest["note"] = (
        "Java ProcessingMachineBlock owns executable recipes; this manifest documents "
        "implemented industry families for handbook, JEI and future data-driven migration."
    )
    write_json(manifest_path, manifest)


def prompt_for(item_id: str, descriptor: str) -> str:
    return (
        "Create one polished Minecraft-style item icon for a mod called Earth on Minecraft.\n"
        f"Item id: {item_id}\n"
        f"Subject: {descriptor}.\n"
        "Style: clean high-resolution Minecraft mod item texture, readable at inventory scale, hand-painted pixel-art source detail, "
        "modern industrial chemistry material icon, not retro, not steampunk, centered, no text, no letters, no pseudo-letters, no UI, no watermark.\n"
        "Background: transparent if supported; otherwise pure flat #00ff00 chroma key with no shadows or gradients.\n"
        "Constraints: isolated icon only, generous padding, crisp edges, suitable for downscaling to 32x32 or 64x64."
    )


def request_image(key: str, item_id: str, descriptor: str, attempts: int = 5) -> bytes:
    body = {
        "model": MODEL,
        "prompt": prompt_for(item_id, descriptor),
        "size": "1024x1024",
        "n": 1,
    }
    last_error: Exception | None = None
    for attempt in range(1, attempts + 1):
        try:
            response = requests.post(
                ENDPOINT,
                headers={
                    "Authorization": f"Bearer {key}",
                    "Content-Type": "application/json",
                },
                json=body,
                timeout=300,
            )
            response.raise_for_status()
            break
        except requests.RequestException as exc:
            last_error = exc
            if attempt >= attempts:
                raise
            wait = min(90, 8 * attempt)
            print(f"  request failed for {item_id}, retry {attempt}/{attempts} after {wait}s: {exc}", flush=True)
            time.sleep(wait)
    else:
        raise RuntimeError(f"Agnes request failed for {item_id}: {last_error}")
    payload = response.json()
    data = payload.get("data") or []
    if not data:
        raise RuntimeError(f"Agnes returned no image data for {item_id}: {payload}")
    image = data[0]
    if isinstance(image, dict) and image.get("b64_json"):
        return base64.b64decode(image["b64_json"])
    url = image.get("url") or image.get("image_url") if isinstance(image, dict) else None
    if not url:
        raise RuntimeError(f"Unsupported Agnes image payload for {item_id}: {payload}")
    if url.startswith("data:image"):
        _, encoded = url.split(",", 1)
        return base64.b64decode(encoded)
    parsed = urllib.parse.urlparse(url)
    if not parsed.scheme:
        raise RuntimeError(f"Invalid image url for {item_id}: {url}")
    last_error: Exception | None = None
    for attempt in range(1, attempts + 1):
        try:
            download = requests.get(url, timeout=300)
            download.raise_for_status()
            return download.content
        except requests.RequestException as exc:
            last_error = exc
            if attempt >= attempts:
                raise
            wait = min(90, 8 * attempt)
            print(f"  download failed for {item_id}, retry {attempt}/{attempts} after {wait}s: {exc}", flush=True)
            time.sleep(wait)
    raise RuntimeError(f"Agnes image download failed for {item_id}: {last_error}")


def remove_flat_background(image: Image.Image) -> Image.Image:
    img = image.convert("RGBA")
    width, height = img.size
    pixels = img.load()
    corner_samples = [
        pixels[0, 0],
        pixels[width - 1, 0],
        pixels[0, height - 1],
        pixels[width - 1, height - 1],
    ]
    # Prefer green chroma key if present; otherwise use the average corner color.
    has_green_key = any(g > 150 and g > r * 1.45 and g > b * 1.45 for r, g, b, _a in corner_samples)
    has_magenta_key = any(r > 150 and b > 150 and g < min(r, b) * 0.55 for r, g, b, _a in corner_samples)
    if has_green_key:
        key = (0, 255, 0)
        threshold = 82
    elif has_magenta_key:
        key = (255, 0, 255)
        threshold = 120
    else:
        key = tuple(sum(sample[i] for sample in corner_samples) // 4 for i in range(3))
        threshold = 42
    def color_distance(r: int, g: int, b: int) -> int:
        return abs(r - key[0]) + abs(g - key[1]) + abs(b - key[2])

    def is_background(x: int, y: int) -> bool:
        r, g, b, _a = pixels[x, y]
        if has_green_key:
            return g > 130 and g > r * 1.35 and g > b * 1.35
        if has_magenta_key:
            return r > 135 and b > 135 and g < min(r, b) * 0.72
        return color_distance(r, g, b) < threshold

    # Only remove key-colored pixels connected to the image border. This keeps
    # legitimate green liquids, catalysts, circuit boards, and mineral grains.
    queue: deque[tuple[int, int]] = deque()
    visited: set[tuple[int, int]] = set()
    for x in range(width):
        queue.append((x, 0))
        queue.append((x, height - 1))
    for y in range(height):
        queue.append((0, y))
        queue.append((width - 1, y))

    while queue:
        x, y = queue.popleft()
        if (x, y) in visited or not is_background(x, y):
            continue
        visited.add((x, y))
        if x > 0:
            queue.append((x - 1, y))
        if x + 1 < width:
            queue.append((x + 1, y))
        if y > 0:
            queue.append((x, y - 1))
        if y + 1 < height:
            queue.append((x, y + 1))

    for x, y in visited:
        r, g, b, _a = pixels[x, y]
        pixels[x, y] = (r, g, b, 0)

    for y in range(height):
        for x in range(width):
            if (x, y) in visited:
                continue
            if not any((nx, ny) in visited for nx, ny in (
                (x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)
            ) if 0 <= nx < width and 0 <= ny < height):
                continue
            r, g, b, a = pixels[x, y]
            dist = color_distance(r, g, b)
            if a == 255 and dist < threshold * 2:
                pixels[x, y] = (r, g, b, max(40, int(255 * (dist - threshold) / threshold)))
    return img


def crop_and_downscale(source: pathlib.Path, target: pathlib.Path, size: int) -> None:
    img = Image.open(source)
    img = remove_flat_background(img)
    bbox = img.getbbox()
    if bbox:
        img = img.crop(bbox)
    side = max(img.size)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.alpha_composite(img, ((side - img.width) // 2, (side - img.height) // 2))
    padded = Image.new("RGBA", (side + side // 4, side + side // 4), (0, 0, 0, 0))
    padded.alpha_composite(canvas, (side // 8, side // 8))
    icon = padded.resize((size, size), Image.Resampling.LANCZOS)
    target.parent.mkdir(parents=True, exist_ok=True)
    icon.save(target)


def make_montage(item_ids: list[str], size: int) -> pathlib.Path:
    thumbs = []
    for item_id in item_ids:
        path = ASSETS / "textures" / "item" / f"{item_id}.png"
        if path.exists():
            thumbs.append((item_id, Image.open(path).convert("RGBA").resize((32, 32), Image.Resampling.NEAREST)))
    if not thumbs:
        raise RuntimeError("No generated item textures found for montage.")
    cols = 8
    rows = (len(thumbs) + cols - 1) // cols
    montage = Image.new("RGBA", (cols * 40, rows * 40), (24, 24, 24, 255))
    for i, (_item_id, thumb) in enumerate(thumbs):
        x = (i % cols) * 40 + 4
        y = (i // cols) * 40 + 4
        montage.alpha_composite(thumb, (x, y))
    out = TMP / f"item_texture_montage_{size}px.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    montage.save(out)
    return out


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true", help="regenerate textures even if final PNG exists")
    parser.add_argument("--only", nargs="*", default=[], help="specific item ids to generate")
    parser.add_argument("--limit", type=int, default=0, help="only generate first N missing textures")
    parser.add_argument("--size", type=int, default=32, choices=[16, 32, 64, 128], help="final texture size")
    args = parser.parse_args()

    update_resource_metadata()
    selected = args.only or list(ITEMS)
    unknown = [item_id for item_id in selected if item_id not in ITEMS]
    if unknown:
        raise SystemExit(f"Unknown item ids: {', '.join(unknown)}")
    key = load_agnes_key()
    TMP.mkdir(parents=True, exist_ok=True)
    generated = 0
    for item_id in selected:
        _zh, _en, descriptor = ITEMS[item_id]
        final = ASSETS / "textures" / "item" / f"{item_id}.png"
        raw = TMP / f"{item_id}.png"
        if final.exists() and not args.force:
            print(f"skip existing {item_id}")
            continue
        if args.limit and generated >= args.limit:
            break
        print(f"generating {item_id} ...", flush=True)
        content = request_image(key, item_id, descriptor)
        raw.write_bytes(content)
        crop_and_downscale(raw, final, args.size)
        generated += 1
        time.sleep(1.0)
    montage = make_montage(selected, args.size)
    print(f"Agnes texture generation complete: generated={generated}, montage={montage}")


if __name__ == "__main__":
    main()
