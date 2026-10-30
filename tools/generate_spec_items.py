#!/usr/bin/env python3
"""Generate the spec item layer from spec/eom/items.jsonl.

Outputs:
  * neoforge-26.2/.../SpecItems.java (registry table)
  * assets/<ns>/items/<slug>.json (26.2 item definitions)
  * assets/<ns>/models/item/<slug>.json (generated item models)
  * assets/<ns>/textures/item/<slug>.png (deterministic template art)
  * lang zh_cn/en_us item names
  * data/<ns>/tags/item/** merged tag values
  * docs/quality/spec-item-layer.json manifest

The artwork is procedural template art, not final illustrations. Each generated
texture is recorded in the manifest so the art pipeline can replace it later.
"""

from __future__ import annotations

import argparse
import colorsys
import hashlib
import json
import math
import pathlib
import re

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "neoforge-26.2/src/main/java/com/xxsx/earthonminecraft"
RES = ROOT / "neoforge-26.2/src/main/resources"
ASSETS = RES / "assets/earth_on_minecraft"
TEXTURES = ASSETS / "textures/item"
MODELS = ASSETS / "models/item"
ITEM_DEFS = ASSETS / "items"
TAGS = RES / "data/earth_on_minecraft/tags/item"
LANG = ASSETS / "lang"
ENTRYPOINT = JAVA_ROOT / "EarthOnMinecraft.java"
SPEC_ITEMS_JAVA = JAVA_ROOT / "SpecItems.java"
MANIFEST = ROOT / "docs/quality/spec-item-layer.json"
DEFAULT_SPEC = pathlib.Path(r"D:\Codex\WORKSPACES\Projects\mc-agent\spec\eom")

FAMILY_HUE = {
    "chemical": (180, 265),
    "herb": (75, 140),
    "food": (5, 60),
    "drink": (180, 330),
    "medicine": (185, 230),
    "metal": (195, 225),
    "gem": (265, 330),
    "tool": (15, 45),
    "weapon": (0, 30),
    "armor": (200, 225),
    "container": (20, 45),
    "life": (90, 165),
    "raw_material": (25, 55),
    "intermediate": (170, 220),
    "misc": (0, 360),
    "tobacco": (20, 40),
    "ore": (20, 50),
    "power": (180, 220),
    "machine_part": (195, 225),
    "agriculture": (80, 130),
    "item": (0, 360),
}


def load_items(spec_root: pathlib.Path):
    path = spec_root / "items.jsonl"
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def existing_ids() -> set[str]:
    text = ENTRYPOINT.read_text(encoding="utf-8")
    ids = set(re.findall(r'\b(?:item|notebookItem|itemForBlock)\(\s*"([a-z0-9_]+)"', text))
    ids |= set(re.findall(r'ITEMS\.registerItem\(\s*"([a-z0-9_]+)"', text))
    ids |= set(re.findall(r'\b(?:machineBlock|oreBlock|supportBlock|controlPanelBlock|structureProjectionBlock|machineInterfaceBlock|energyGeneratorBlock|powerCableBlock|batteryBoxBlock|conveyorBeltBlock|settlementBoardBlock)\(\s*"([a-z0-9_]+)"', text))
    ids |= set(re.findall(r'BLOCKS\.registerBlock\(\s*"([a-z0-9_]+)"', text))
    return ids


def template_of(record: dict) -> str:
    texture = record.get("texture") or {}
    value = str(texture.get("item_texture", ""))
    return value.split(":", 1)[1] if value.startswith("template:") else ""


def numeric(value) -> float | None:
    try:
        return float(str(value).strip())
    except (TypeError, ValueError):
        return None


def food_values(record: dict) -> tuple[int, float]:
    columns = (record.get("catalog") or {}).get("columns") or {}
    nutrition = numeric(columns.get("饥饿"))
    saturation = numeric(columns.get("饱和"))
    if nutrition is None or saturation is None:
        return 0, 0.0
    return max(0, int(nutrition)), max(0.0, saturation)


def component_value(record: dict, component: str, template: str) -> str:
    slug = record["id"].split(":", 1)[1]
    if component == "earth_on_minecraft:herb":
        match = re.search(r"/herb/([a-z_]+)$", template)
        part = match.group(1) if match else "root"
        if part == "crystal":
            part = "mineral"
        valid = {"root", "rhizome", "stem", "leaf", "flower", "fruit", "seed", "bark", "resin", "mineral", "animal"}
        return part if part in valid else "root"
    if component == "earth_on_minecraft:metal_batch":
        return "steel" if "steel" in slug else ("aluminium" if "alumin" in slug else "iron")
    return ""


def pick_color(seed: str, family: str) -> tuple[int, int, int]:
    lo, hi = FAMILY_HUE.get(family, (0, 360))
    digest = int(hashlib.sha256(seed.encode("utf-8")).hexdigest(), 16)
    hue = (lo + digest % max(1, hi - lo)) / 360.0
    saturation = 0.45 + ((digest >> 8) % 30) / 100.0
    lightness = 0.42 + ((digest >> 16) % 20) / 100.0
    return tuple(int(channel * 255) for channel in colorsys.hls_to_rgb(hue, lightness, saturation))


def shade(color: tuple[int, int, int], factor: float) -> tuple[int, int, int]:
    return tuple(max(0, min(255, int(channel * factor))) for channel in color)


def draw_icon(slug: str, family: str, template: str) -> Image.Image:
    image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    base = pick_color(slug, family)
    light = shade(base, 1.25)
    dark = shade(base, 0.55)
    outline = shade(base, 0.32)
    token = template.lower()
    anchor = template.rsplit("/", 1)[-1].lower() if template else ""

    def finish():
        return image.resize((128, 128), Image.Resampling.NEAREST)

    if any(word in token for word in ("powder", "dust", "grain", "flour", "spice")):
        draw.polygon([(5, 26), (9, 18), (14, 15), (18, 17), (23, 20), (27, 26)], fill=base, outline=outline)
        for index in range(6):
            x = 8 + (int(hashlib.sha256(f"{slug}{index}".encode()).hexdigest(), 16) % 16)
            y = 20 + (index % 4)
            draw.rectangle((x, y, x + 1, y + 1), fill=light)
        return finish()

    if any(word in token for word in ("ingot", "billet", "bloom", "anode", "cathode")):
        draw.polygon([(6, 22), (9, 14), (25, 12), (28, 20), (25, 26), (8, 27)], fill=base, outline=outline)
        draw.line((10, 15, 23, 14), fill=light)
        return finish()

    if any(word in token for word in ("cell", "bottle", "vial", "flask", "jar", "canister", "bucket")):
        draw.rounded_rectangle((10, 12, 22, 28), radius=3, fill=base, outline=outline)
        draw.rectangle((13, 6, 19, 12), fill=shade(base, 0.85), outline=outline)
        draw.rectangle((12, 3, 20, 6), fill=dark, outline=outline)
        draw.rectangle((12, 16, 14, 24), fill=light)
        return finish()

    if "tablet" in token or anchor in ("pill",):
        draw.ellipse((8, 10, 24, 26), fill=base, outline=outline)
        draw.line((11, 13, 21, 23), fill=light)
        return finish()

    if "capsule" in token:
        draw.rounded_rectangle((8, 11, 24, 23), radius=6, fill=base, outline=outline)
        draw.rectangle((16, 11, 24, 23), fill=light)
        return finish()

    if "ointment" in token or "salve" in token:
        draw.rounded_rectangle((8, 15, 24, 28), radius=2, fill=base, outline=outline)
        draw.rectangle((10, 10, 22, 15), fill=dark, outline=outline)
        draw.rectangle((12, 19, 14, 24), fill=light)
        return finish()

    if "root" in token or "rhizome" in token:
        draw.line((16, 6, 16, 26), fill=base, width=3)
        draw.line((16, 12, 8, 20), fill=base, width=2)
        draw.line((16, 16, 24, 24), fill=base, width=2)
        draw.line((16, 20, 11, 27), fill=dark, width=2)
        return finish()

    if "leaf" in token or "stem" in token or "herb" in token:
        draw.polygon([(16, 26), (10, 18), (8, 10), (14, 5), (22, 8), (24, 17), (19, 22)], fill=base, outline=outline)
        draw.line((16, 26, 16, 9), fill=light, width=1)
        return finish()

    if "flower" in token:
        for dx, dy in ((0, -8), (7, -3), (5, 6), (-5, 6), (-7, -3)):
            draw.ellipse((14 + dx, 14 + dy, 20 + dx, 20 + dy), fill=base, outline=outline)
        draw.ellipse((14, 14, 20, 20), fill=light, outline=outline)
        draw.line((16, 20, 16, 28), fill=shade(base, 0.7), width=2)
        return finish()

    if "fruit" in token or "berry" in token:
        draw.ellipse((8, 10, 24, 26), fill=base, outline=outline)
        draw.arc((8, 10, 24, 26), 200, 330, fill=light)
        draw.line((17, 10, 20, 5), fill=shade(base, 0.7), width=2)
        draw.polygon([(20, 6), (26, 4), (24, 9)], fill=(70, 130, 60, 255), outline=(30, 70, 30, 255))
        return finish()

    if "seed" in token:
        for index, (cx, cy) in enumerate(((11, 16), (17, 12), (21, 19), (14, 23))):
            draw.ellipse((cx - 3, cy - 2, cx + 3, cy + 2), fill=shade(base, 1.0 - index * 0.05), outline=outline)
        return finish()

    if "bark" in token or "resin" in token:
        draw.rounded_rectangle((10, 6, 22, 27), radius=2, fill=base, outline=outline)
        draw.line((13, 8, 13, 25), fill=dark)
        draw.line((18, 8, 18, 25), fill=dark)
        return finish()

    if "meat" in token:
        draw.ellipse((8, 10, 25, 24), fill=base, outline=outline)
        draw.arc((8, 10, 25, 24), 200, 330, fill=light)
        draw.rectangle((6, 14, 10, 17), fill=(235, 225, 200, 255), outline=outline)
        return finish()

    if "dish" in token or "soup" in token or "stew" in token:
        draw.ellipse((6, 14, 26, 27), fill=(180, 170, 150, 255), outline=(70, 65, 55, 255))
        draw.ellipse((8, 13, 24, 23), fill=base, outline=outline)
        return finish()

    if "gem" in token:
        draw.polygon([(16, 4), (27, 14), (16, 29), (5, 14)], fill=base, outline=outline)
        draw.polygon([(16, 4), (21, 14), (16, 29), (11, 14)], fill=light, outline=None)
        return finish()

    if any(word in token for word in ("pickaxe", "axe", "shovel", "hoe", "sword", "dagger", "knife", "hammer", "saw", "chisel", "drill", "spear", "sickle", "wrench", "file", "needle", "tongs")):
        draw.line((9, 27, 22, 9), fill=(110, 75, 45, 255), width=4)
        head = (200, 205, 210, 255)
        if "sword" in token or "dagger" in token or "knife" in token:
            draw.polygon([(20, 5), (28, 9), (15, 24), (12, 20)], fill=head, outline=(70, 75, 80, 255))
        elif "axe" in token:
            draw.polygon([(18, 5), (28, 9), (22, 16), (15, 12)], fill=head, outline=(70, 75, 80, 255))
            draw.line((20, 10, 24, 8), fill=light)
        else:
            draw.rectangle((18, 6, 26, 11), fill=head, outline=(70, 75, 80, 255))
        return finish()

    if any(word in token for word in ("helmet", "chestplate", "leggings", "boots", "armor", "chainmail")):
        draw.polygon([(8, 8), (24, 8), (26, 14), (22, 16), (22, 27), (10, 27), (10, 16), (6, 14)], fill=base, outline=outline)
        draw.line((12, 12, 20, 12), fill=light)
        return finish()

    if any(word in token for word in ("container", "box", "basket", "bag", "barrel", "amphora", "jar")):
        draw.rounded_rectangle((7, 10, 25, 27), radius=2, fill=base, outline=outline)
        draw.line((7, 16, 25, 16), fill=dark)
        draw.rectangle((13, 6, 19, 10), fill=dark, outline=outline)
        return finish()

    draw.rounded_rectangle((7, 8, 25, 27), radius=4, fill=base, outline=outline)
    draw.ellipse((10, 11, 16, 17), fill=light)
    return finish()


def java_string(value: str) -> str:
    return value.replace("\\", "\\\\").replace('"', '\\"')


def generate_java(entries: list[dict]) -> str:
    lines = [
        "package com.xxsx.earthonminecraft;",
        "",
        "import com.xxsx.earthonminecraft.component.EarthDataComponents;",
        "import com.xxsx.earthonminecraft.component.GemComponent;",
        "import com.xxsx.earthonminecraft.component.Grade;",
        "import com.xxsx.earthonminecraft.component.HerbComponent;",
        "import com.xxsx.earthonminecraft.component.MaterialBatch;",
        "import com.xxsx.earthonminecraft.component.MetalBatch;",
        "import com.xxsx.earthonminecraft.component.ToolQualityComponent;",
        "import net.minecraft.world.food.FoodProperties;",
        "import net.minecraft.world.item.Item;",
        "import net.minecraft.world.item.component.Consumables;",
        "import net.neoforged.neoforge.registries.DeferredItem;",
        "",
        "import java.util.LinkedHashMap;",
        "import java.util.List;",
        "import java.util.Locale;",
        "import java.util.Map;",
        "",
        "public final class SpecItems {",
        "    public record Entry(String id, int stack, int nutrition, float saturation, boolean drink,",
        "                        String component, String componentValue) {",
        "    }",
        "",
        "    private static final List<Entry> ENTRIES = List.of(",
    ]
    entry_lines = [
        '            new Entry("%s", %d, %d, %.1fF, %s, "%s", "%s")' % (
            java_string(entry["id"]), entry["stack"], entry["nutrition"], entry["saturation"],
            "true" if entry["drink"] else "false",
            java_string(entry["component"]), java_string(entry["componentValue"]))
        for entry in entries
    ]
    lines.append(",\n".join(entry_lines))
    lines.extend([
        "    );",
        "",
        "    public static final Map<String, DeferredItem<Item>> BY_ID = new LinkedHashMap<>();",
        "    private static boolean registered;",
        "",
        "    private SpecItems() {",
        "    }",
        "",
        "    public static void register() {",
        "        if (registered) {",
        "            return;",
        "        }",
        "        registered = true;",
        "        for (Entry entry : ENTRIES) {",
        "            DeferredItem<Item> item = EarthOnMinecraft.ITEMS.registerItem(entry.id(), SpecItem::new,",
        "                    props -> apply(entry, props));",
        "            BY_ID.put(entry.id(), item);",
        "            EarthOnMinecraft.addTabItem(item);",
        "        }",
        "    }",
        "",
        "    private static Item.Properties apply(Entry entry, Item.Properties props) {",
        "        props = props.stacksTo(entry.stack());",
        "        if (entry.nutrition() > 0 || entry.drink()) {",
        "            FoodProperties food = new FoodProperties(entry.nutrition(), entry.saturation(), entry.drink());",
        "            props = entry.drink() ? props.food(food, Consumables.DEFAULT_DRINK) : props.food(food);",
        "        }",
        "        return switch (entry.component()) {",
        '            case "earth_on_minecraft:herb" -> props.component(EarthDataComponents.HERB,',
        "                    new HerbComponent(1, entry.id(), herbPart(entry.componentValue()),",
        "                            HerbComponent.Nature.NEUTRAL, List.of(), 0, HerbComponent.Prepared.RAW,",
        "                            1.0, Grade.COMMON, \"\"));",
        '            case "earth_on_minecraft:gem" -> props.component(EarthDataComponents.GEM,',
        "                    new GemComponent(1, entry.id(), \"\", Grade.COMMON, GemComponent.Clarity.TRANSLUCENT,",
        "                            GemComponent.ColorGrade.MEDIUM, 1.0, GemComponent.Treatment.NONE,",
        "                            GemComponent.Cut.ROUGH, \"\", 0));",
        '            case "earth_on_minecraft:tool_quality" -> props.component(EarthDataComponents.TOOL_QUALITY,',
        "                    ToolQualityComponent.DEFAULT);",
        '            case "earth_on_minecraft:material_batch" -> props.component(EarthDataComponents.MATERIAL_BATCH,',
        "                    MaterialBatch.defaultFor(entry.id()));",
        '            case "earth_on_minecraft:metal_batch" -> props.component(EarthDataComponents.METAL_BATCH,',
        "                    MetalBatch.defaultFor(entry.componentValue()));",
        "            default -> props;",
        "        };",
        "    }",
        "",
        "    private static HerbComponent.Part herbPart(String part) {",
        "        return switch (part) {",
        "            case \"rhizome\" -> HerbComponent.Part.RHIZOME;",
        "            case \"stem\" -> HerbComponent.Part.STEM;",
        "            case \"leaf\" -> HerbComponent.Part.LEAF;",
        "            case \"flower\" -> HerbComponent.Part.FLOWER;",
        "            case \"fruit\" -> HerbComponent.Part.FRUIT;",
        "            case \"seed\" -> HerbComponent.Part.SEED;",
        "            case \"bark\" -> HerbComponent.Part.BARK;",
        "            case \"resin\" -> HerbComponent.Part.RESIN;",
        "            case \"mineral\" -> HerbComponent.Part.MINERAL;",
        "            case \"animal\" -> HerbComponent.Part.ANIMAL;",
        "            default -> HerbComponent.Part.ROOT;",
        "        };",
        "    }",
        "}",
        "",
    ])
    return "\n".join(lines)


def merge_lang(slug: str, zh: str, en: str) -> None:
    for locale, value in (("zh_cn", zh), ("en_us", en)):
        path = LANG / f"{locale}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        data[f"item.earth_on_minecraft.{slug}"] = value
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def merge_tags(slug: str, tags: list[str]) -> None:
    for tag in tags:
        if not tag.startswith("earth_on_minecraft:"):
            continue
        tag_path = tag.split(":", 1)[1]
        if tag_path.startswith("item/"):
            tag_path = tag_path[len("item/"):]
        path = TAGS / (tag_path + ".json")
        data = {"replace": False, "values": []}
        if path.exists():
            data = json.loads(path.read_text(encoding="utf-8"))
        values = data.setdefault("values", [])
        entry = f"earth_on_minecraft:{slug}"
        if entry not in values:
            values.append(entry)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--spec", default=str(DEFAULT_SPEC))
    parser.add_argument("--skip-art", action="store_true")
    args = parser.parse_args()

    spec_root = pathlib.Path(args.spec)
    records = load_items(spec_root)
    taken = existing_ids()
    entries = []
    skipped = []
    art = []
    tags_written = 0

    for record in records:
        slug = record["id"].split(":", 1)[1]
        if record.get("superseded_by"):
            skipped.append({"id": slug, "reason": "superseded"})
            continue
        if slug in taken:
            skipped.append({"id": slug, "reason": "already_registered"})
            continue
        template = template_of(record)
        nutrition, saturation = food_values(record)
        drink = record.get("family") == "drink"
        components = record.get("components") or []
        component = components[0] if components else ""
        entries.append({
            "id": slug,
            "stack": int(record.get("stack") or 64),
            "nutrition": nutrition,
            "saturation": saturation,
            "drink": drink,
            "component": component,
            "componentValue": component_value(record, component, template),
        })
        if not args.skip_art:
            texture_path = TEXTURES / f"{slug}.png"
            if not texture_path.exists():
                draw_icon(slug, record.get("family") or "misc", template).save(texture_path)
                art.append(slug)
            item_def = ITEM_DEFS / f"{slug}.json"
            item_def.write_text(json.dumps({
                "model": {"type": "minecraft:model", "model": f"earth_on_minecraft:item/{slug}"}
            }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            model = MODELS / f"{slug}.json"
            model.write_text(json.dumps({
                "parent": "minecraft:item/generated",
                "textures": {"layer0": f"earth_on_minecraft:item/{slug}"}
            }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        merge_lang(slug, record.get("zh") or slug, record.get("en") or slug)
        before = len((record.get("tags") or []))
        merge_tags(slug, record.get("tags") or [])
        tags_written += before

    SPEC_ITEMS_JAVA.write_text(generate_java(entries), encoding="utf-8")
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST.write_text(json.dumps({
        "schema_version": 1,
        "source": str(spec_root),
        "generated_items": len(entries),
        "generated_textures": len(art),
        "skipped": skipped,
        "template_art": True,
        "note": "Item textures are deterministic template art pending the approved art pipeline."
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"SPEC_ITEMS generated={len(entries)} textures={len(art)} skipped={len(skipped)} tagRefs={tags_written}")


if __name__ == "__main__":
    main()
