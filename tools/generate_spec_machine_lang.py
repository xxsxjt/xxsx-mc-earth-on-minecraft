#!/usr/bin/env python3
"""Add the setpoint and description lang keys for the 30 spec recipe-station machines."""

from __future__ import annotations

import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
LANG = ROOT / "neoforge-26.2/src/main/resources/assets/earth_on_minecraft/lang"

KEYS = {
    "bloomery_controller": ("Operating focus: blast rate and charge descent",
                            "鼓风量与料柱下降速度决定还原是否完全，出炉后要趁热挤渣。"),
    "bread_oven_controller": ("Operating focus: stored heat and mouth closure",
                              "靠窑体积热烘烤，蓄热不足或窑口敞开都会烤出生面团。"),
    "glass_kiln_controller": ("Operating focus: crown temperature and batch mix",
                              "温度不足会留下未熔砂粒，助熔剂比例决定澄清速度。"),
    "lime_kiln_controller": ("Operating focus: draw rate and flue draft",
                             "抽风要稳定，出灰过快会让料柱塌陷。"),
    "pit_kiln_controller": ("Operating focus: seal tightness and vent control",
                            "封土漏气会让木材烧成灰，通气孔控制炭化速度。"),
    "shaft_furnace_controller": ("Operating focus: hot blast and tap timing",
                                 "热风温度决定炉况，出铁口要按炉缸液位及时开堵。"),
    "anvil": ("Operating focus: stock temperature and hammer order",
              "把钢坯烧到锻造温度，按顺序锻打成形并检查品质。"),
    "bar_counter": ("Operating focus: ingredient order and dilution",
                    "按配方顺序投料，控制冰量与稀释，摇匀后出杯。"),
    "campfire": ("Operating focus: grate height and doneness",
                 "用架高与翻面控制火候，过火会烤焦。"),
    "carpentry_bench": ("Operating focus: grain direction and joint fit",
                        "顺纹下料，榫卯贴合；湿料需要先干燥。"),
    "casting_table": ("Operating focus: pour temperature and mold preheat",
                      "铸型要预热，浇温过低会短浇，过高会缩孔。"),
    "coffee_roaster": ("Operating focus: drum temperature and roast level",
                       "控制滚筒温度与时间，浅烘保酸、深烘出苦甜。"),
    "condenser": ("Operating focus: coolant flow and vapor load",
                  "冷却水要稳定，蒸汽过载会让冷凝不完全。"),
    "converter": ("Operating focus: blast rate and bath temperature",
                  "鼓风量决定去硫速度，熔池温度过低会凝死。"),
    "curing_barn": ("Operating focus: air flow and humidity",
                    "通风与湿度决定脱水速度，湿度过高会霉变。"),
    "fermenting_crock": ("Operating focus: temperature and airlock seal",
                         "温度决定发酵速度，气密不好会酸败。"),
    "filter_press": ("Operating focus: ram pressure and cloth condition",
                     "压力过高会冲破滤布，滤布堵塞后要及时更换。"),
    "gem_cutter": ("Operating focus: facet angle and lap speed",
                   "按解理选角度，转速过高会崩边。"),
    "herb_processing_table": ("Operating focus: cut size and drying heat",
                              "切制粗细影响有效成分，干燥温度过高会损失药性。"),
    "loom": ("Operating focus: warp tension and weft density",
             "经线张力要均匀，纬密决定布的手感。"),
    "mill": ("Operating focus: feed rate and stone gap",
             "进料过快会堵磨，磨盘间隙决定粉的粗细。"),
    "noodle_press": ("Operating focus: dough moisture and roller gap",
                     "面团含水量与辊隙决定面条的筋道与厚薄。"),
    "oil_press": ("Operating focus: pressing temperature and cake thickness",
                  "压榨温度提高出油率，饼层过厚会出油不畅。"),
    "rice_huller": ("Operating focus: roller gap and paddy moisture",
                    "辊隙过小会碾碎米粒，稻谷过湿会堵塞。"),
    "smelter": ("Operating focus: bath temperature and melt limit",
                "炉温上限 1750 K，超过金属熔点才能出液。"),
    "smokehouse": ("Operating focus: smoke density and chamber heat",
                   "冷熏低温长时间，热熏高温短时间；烟量要稳定。"),
    "steamer": ("Operating focus: water level and steam tightness",
                "水位不足会干锅，锅盖漏汽会蒸不透。"),
    "steamer_basket": ("Operating focus: tier count and steam path",
                       "层数越多产量越大，但蒸汽要能穿透每一层。"),
    "still": ("Operating focus: pot temperature and cut points",
              "按沸点分段取酒头、酒心与酒尾。"),
    "stone_mill": ("Operating focus: rotation speed and feed size",
                   "低速慢磨粉更细，进料过快会卡住磨盘。"),
    "stove": ("Operating focus: burner setting and pan temperature",
              "按档位控制锅温，超过档位会焦糊。"),
    "tablet_press": ("Operating focus: die fill and compression force",
                     "装量决定片重，压力不足会松片。"),
    "tannery": ("Operating focus: liquor strength and soak time",
                "鞣剂浓度与时间决定成革的软硬。"),
    "tea_kiln": ("Operating focus: wok temperature and turning rhythm",
                 "锅温过高会焦边，翻拌要均匀。"),
    "thickener": ("Operating focus: flocculant dose and rake load",
                  "絮凝剂过量会压耙，底流浓度决定后续压滤负荷。"),
    "water_treatment_unit": ("Operating focus: filter load and backwash cycle",
                             "压差升高要反洗，出水硬度决定是否需软化。"),
}


def main() -> None:
    for lang, index in (("en_us", 0), ("zh_cn", 1)):
        path = LANG / f"{lang}.json"
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        added = 0
        for machine_id, texts in KEYS.items():
            setpoint = f"screen.earth_on_minecraft.machine.setpoint.{machine_id}"
            description = f"tooltip.earth_on_minecraft.machine.{machine_id}.description"
            if setpoint not in data:
                data[setpoint] = texts[index]
                added += 1
            if description not in data:
                data[description] = texts[index]
                added += 1
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"{lang} added={added}")


if __name__ == "__main__":
    main()
