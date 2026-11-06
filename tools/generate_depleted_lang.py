"""Add lang keys for the depleted lode blocks and their tooltip."""

from __future__ import annotations

import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
LANG = ROOT / "neoforge-26.2/src/main/resources/assets/earth_on_minecraft/lang"
JAVA = ROOT / "neoforge-26.2/src/main/java/com/xxsx/earthonminecraft/SpecDepletedBlocks.java"

import re

TEXT = {
    "tooltip.earth_on_minecraft.depleted_lode": (
        "Worked-out ore body: the valuable fraction is gone, but residual mineral can still be "
        "recovered at low yield.",
        "已采空的矿体：有价部分已被采尽，残余矿物仍可以低回收率提取。",
    ),
}

# Human-readable part names for the block label.
PART_EN = {
    "andesite_volcanic_belt": "Depleted Andesite Volcanic Belt",
    "anthracite_coal_seam": "Depleted Anthracite Coal Seam",
    "anthracite_deepslate_halo": "Depleted Anthracite Deepslate Halo",
    "auriferous_quartz_calcite_halo": "Depleted Auriferous Quartz Calcite Halo",
    "auriferous_quartz_vein": "Depleted Auriferous Quartz Vein",
    "basalt_mafic_dike": "Depleted Basalt Mafic Dike",
    "bauxite_laterite_deposit": "Depleted Bauxite Laterite Deposit",
    "bituminous_coal_seam": "Depleted Bituminous Coal Seam",
    "bituminous_sandstone_halo": "Depleted Bituminous Sandstone Halo",
    "borate_deposit": "Depleted Borate Deposit",
    "cassiterite_placer_deposit": "Depleted Cassiterite Placer Deposit",
    "chalcopyrite_granite_halo": "Depleted Chalcopyrite Granite Halo",
    "chalcopyrite_vein": "Depleted Chalcopyrite Vein",
    "chromite_lens": "Depleted Chromite Lens",
    "cinnabar_vein": "Depleted Cinnabar Vein",
    "diamondiferous_kimberlite_pipe": "Depleted Diamondiferous Kimberlite Pipe",
    "diorite_intrusion": "Depleted Diorite Intrusion",
    "emerald_beryl_vein": "Depleted Emerald Beryl Vein",
    "evaporite_salt_bed": "Depleted Evaporite Salt Bed",
    "galena_lens": "Depleted Galena Lens",
    "granitic_intrusion": "Depleted Granitic Intrusion",
    "kimberlite_pipe": "Depleted Kimberlite Pipe",
    "kimberlite_tuff_halo": "Depleted Kimberlite Tuff Halo",
    "lapis_lazuli_body": "Depleted Lapis Lazuli Body",
    "limestone_calcite_bed": "Depleted Limestone Calcite Bed",
    "magnetite_lens": "Depleted Magnetite Lens",
    "magnetite_tuff_halo": "Depleted Magnetite Tuff Halo",
    "manganite_lens": "Depleted Manganite Lens",
    "phosphorite_bed": "Depleted Phosphorite Bed",
    "poor_magnetite_lens": "Depleted Lean Magnetite Lens",
    "rare_earth_carbonatite": "Depleted Rare Earth Carbonatite",
    "redstone_mineral_body": "Depleted Ferromanganese-Cinnabar Body",
    "rich_magnetite_lens": "Depleted Rich Magnetite Lens",
    "sedimentary_sandstone_bed": "Depleted Sedimentary Sandstone Bed",
    "sphalerite_lens": "Depleted Sphalerite Lens",
    "tuff_volcanic_belt": "Depleted Tuff Volcanic Belt",
    "uraninite_lens": "Depleted Uraninite Lens",
}

PART_ZH = {
    "andesite_volcanic_belt": "采空安山岩火山带",
    "anthracite_coal_seam": "采空无烟煤层",
    "anthracite_deepslate_halo": "采空无烟煤深板岩晕",
    "auriferous_quartz_calcite_halo": "采空含金石英方解石晕",
    "auriferous_quartz_vein": "采空含金石英脉",
    "basalt_mafic_dike": "采空玄武岩基性岩墙",
    "bauxite_laterite_deposit": "采空铝土红土矿床",
    "bituminous_coal_seam": "采空烟煤层",
    "bituminous_sandstone_halo": "采空沥青砂岩晕",
    "borate_deposit": "采空硼酸盐矿床",
    "cassiterite_placer_deposit": "采空锡石砂矿",
    "chalcopyrite_granite_halo": "采空黄铜矿花岗岩晕",
    "chalcopyrite_vein": "采空黄铜矿脉",
    "chromite_lens": "采空铬铁矿透镜体",
    "cinnabar_vein": "采空辰砂脉",
    "diamondiferous_kimberlite_pipe": "采空含金刚石金伯利岩筒",
    "diorite_intrusion": "采空闪长岩侵入体",
    "emerald_beryl_vein": "采空祖母绿绿柱石脉",
    "evaporite_salt_bed": "采空蒸发岩盐层",
    "galena_lens": "采空方铅矿透镜体",
    "granitic_intrusion": "采空花岗岩侵入体",
    "kimberlite_pipe": "采空金伯利岩筒",
    "kimberlite_tuff_halo": "采空金伯利岩凝灰岩晕",
    "lapis_lazuli_body": "采空青金石矿体",
    "limestone_calcite_bed": "采空石灰岩方解石层",
    "magnetite_lens": "采空磁铁矿透镜体",
    "magnetite_tuff_halo": "采空磁铁矿凝灰岩晕",
    "manganite_lens": "采空水锰矿透镜体",
    "phosphorite_bed": "采空磷块岩层",
    "poor_magnetite_lens": "采空贫磁铁矿透镜体",
    "rare_earth_carbonatite": "采空稀土碳酸岩",
    "redstone_mineral_body": "采空赤铁软锰伴生矿体",
    "rich_magnetite_lens": "采空富磁铁矿透镜体",
    "sedimentary_sandstone_bed": "采空沉积砂岩层",
    "sphalerite_lens": "采空闪锌矿透镜体",
    "tuff_volcanic_belt": "采空凝灰岩火山带",
    "uraninite_lens": "采空晶质铀矿透镜体",
}


def main() -> None:
    text = JAVA.read_text(encoding="utf-8")
    ids = re.findall(r'new Entry\("(depleted_[a-z0-9_]+)", "([a-z0-9_]+)"\)', text)
    print(f"depleted blocks found: {len(ids)}")

    for lang, index, table in (("en_us", 0, PART_EN), ("zh_cn", 1, PART_ZH)):
        path = LANG / f"{lang}.json"
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        added = 0
        for key, texts in TEXT.items():
            if key not in data:
                data[key] = texts[index]
                added += 1
        for block_id, lode in ids:
            block_key = f"block.earth_on_minecraft.{block_id}"
            if block_key not in data:
                data[block_key] = table.get(lode, block_id.replace("_", " ").title())
                added += 1
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"{lang}: added {added}")


if __name__ == "__main__":
    main()
