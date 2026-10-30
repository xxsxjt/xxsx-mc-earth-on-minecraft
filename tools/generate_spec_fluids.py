#!/usr/bin/env python3
"""Emit the spec fluid layer: Java registration, textures, lang, and a coverage manifest.

The spec lists 30 fluids. This generator owns the fluid surface: a `SpecFluids` registration
table (fluid type plus a still/flowing pair per fluid), one still/flowing texture pair per fluid,
both lang keys, and a manifest the resource validator can audit.

Placeable liquid blocks and buckets are deliberately out of scope: the machine routes only need
`FluidStack` transport, and adding world-placeable blocks would change world behaviour.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "neoforge-26.2/src/main/java/com/xxsx/earthonminecraft"
RES = ROOT / "neoforge-26.2/src/main/resources"
ASSETS = RES / "assets/earth_on_minecraft"
LANG = ASSETS / "lang"
FLUID_TEXTURES = ASSETS / "textures/block/fluid"
SPEC_FLUIDS_JAVA = JAVA_ROOT / "SpecFluids.java"
MANIFEST = ROOT / "docs/quality/spec-fluid-layer.json"
DEFAULT_SPEC = pathlib.Path(r"D:\Codex\WORKSPACES\Projects\mc-agent\spec\eom")

SIZE = 32

MATTER_COLORS = {
    "gas": (150, 168, 176),
    "liquid": (78, 128, 150),
    "slurry": (108, 100, 82),
}

FLUID_COLORS = {
    "carbon_dioxide": (126, 140, 148),
    "carbon_monoxide": (118, 130, 136),
    "chlorine": (186, 196, 96),
    "crude_oil": (46, 40, 36),
    "fluorine": (198, 206, 112),
    "hydrogen": (170, 186, 196),
    "molten_aluminium": (198, 202, 208),
    "molten_blister_copper": (150, 84, 46),
    "molten_calcium": (176, 172, 160),
    "molten_calcium_carbide": (98, 94, 88),
    "molten_chromium": (140, 148, 156),
    "molten_copper": (196, 106, 54),
    "molten_ferrosilicon": (118, 116, 112),
    "molten_iron": (214, 132, 62),
    "molten_magnesium": (186, 190, 196),
    "molten_manganese": (128, 120, 112),
    "molten_silicon": (86, 90, 96),
    "molten_sodium": (198, 200, 204),
    "molten_sodium_cyanide": (160, 164, 170),
    "molten_tin": (176, 180, 188),
    "molten_titanium_sponge": (132, 136, 142),
    "nitrogen": (146, 160, 172),
    "oxygen": (138, 176, 196),
    "phosphorus_white_vapour": (206, 198, 150),
    "slurry": (110, 100, 84),
    "spent_electrolyte": (96, 116, 92),
    "steam": (188, 194, 198),
    "sulfur_dioxide": (188, 178, 108),
    "uranium_hexafluoride": (128, 168, 132),
    "uranium_leachate": (150, 156, 96),
}

LUMINOUS = {
    "molten_aluminium", "molten_blister_copper", "molten_calcium", "molten_calcium_carbide",
    "molten_chromium", "molten_copper", "molten_ferrosilicon", "molten_iron", "molten_magnesium",
    "molten_manganese", "molten_silicon", "molten_sodium", "molten_sodium_cyanide", "molten_tin",
    "molten_titanium_sponge",
}


def load(name: str, spec: pathlib.Path):
    return [json.loads(line) for line in (spec / name).read_text(encoding="utf-8").splitlines() if line.strip()]


def java_string(value: str) -> str:
    return value.replace("\\", "\\\\").replace('"', '\\"')


def shade(color: tuple[int, int, int], delta: int) -> tuple[int, int, int]:
    return tuple(max(0, min(255, channel + delta)) for channel in color)


def draw_still(slug: str, base: tuple[int, int, int], glowing: bool) -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    for y in range(SIZE):
        for x in range(SIZE):
            value = int(hashlib.sha256(f"{slug}:still:{x}:{y}".encode()).hexdigest(), 16) % 15 - 7
            draw.point((x, y), fill=(*shade(base, value), 232))
    for index in range(6):
        digest = int(hashlib.sha256(f"{slug}:ripple:{index}".encode()).hexdigest(), 16)
        y = digest % SIZE
        offset = (digest >> 8) % 8
        draw.line((offset, y, min(SIZE - 1, offset + 18), y), fill=(*shade(base, 26), 255))
    if glowing:
        for index in range(5):
            digest = int(hashlib.sha256(f"{slug}:glow:{index}".encode()).hexdigest(), 16)
            draw.point((digest % SIZE, (digest >> 8) % SIZE), fill=shade(base, 70))
    return image


def draw_flow(slug: str, base: tuple[int, int, int], glowing: bool) -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    for y in range(SIZE):
        for x in range(SIZE):
            value = int(hashlib.sha256(f"{slug}:flow:{x}:{y}".encode()).hexdigest(), 16) % 15 - 7
            draw.point((x, y), fill=(*shade(base, value), 232))
    for index in range(4):
        digest = int(hashlib.sha256(f"{slug}:streak:{index}".encode()).hexdigest(), 16)
        x = digest % SIZE
        offset = (digest >> 8) % 8
        draw.line((x, offset, x, min(SIZE - 1, offset + 20)), fill=(*shade(base, 34), 255))
    if glowing:
        for index in range(4):
            digest = int(hashlib.sha256(f"{slug}:flowglow:{index}".encode()).hexdigest(), 16)
            draw.point((digest % SIZE, (digest >> 8) % SIZE), fill=shade(base, 78))
    return image


def generate_java(entries: list[dict]) -> str:
    lines = [
        "package com.xxsx.earthonminecraft;",
        "",
        "import java.util.LinkedHashMap;",
        "import java.util.List;",
        "import java.util.Map;",
        "import net.minecraft.world.level.material.Fluid;",
        "import net.neoforged.neoforge.fluids.BaseFlowingFluid;",
        "import net.neoforged.neoforge.fluids.FluidType;",
        "import net.neoforged.neoforge.registries.DeferredHolder;",
        "import net.neoforged.neoforge.registries.DeferredRegister;",
        "",
        "/**",
        " * Registration for every spec fluid: one fluid type plus a still/flowing pair.",
        " *",
        " * <p>The still variant is registered first and the flowing variant points back at it. Both are",
        " * held in {@link #REGISTERED} so callers can resolve a fluid id to its source holder.</p>",
        " */",
        "public final class SpecFluids {",
        "    public record FluidEntry(String id, String stateOfMatter, int density, int viscosity,",
        "                             int temperatureK, boolean luminous) {",
        "    }",
        "",
        "    public record Registered(DeferredHolder<FluidType, FluidType> type,",
        "                             DeferredHolder<Fluid, Fluid> source,",
        "                             DeferredHolder<Fluid, Fluid> flowing) {",
        "    }",
        "",
        "    private static final List<FluidEntry> ENTRIES = List.of(",
    ]
    entry_lines = [
        '            new FluidEntry("%s", "%s", %d, %d, %d, %s)' % (
            java_string(entry["id"]), java_string(entry["state_of_matter"]), entry["density"],
            entry["viscosity"], entry["temperature_k"], "true" if entry["luminous"] else "false")
        for entry in entries
    ]
    lines.append(",\n".join(entry_lines))
    lines.extend([
        "    );",
        "",
        "    private static final Map<String, FluidEntry> BY_ID = new LinkedHashMap<>();",
        "    private static final Map<String, Registered> REGISTERED = new LinkedHashMap<>();",
        "",
        "    private SpecFluids() {",
        "    }",
        "",
        "    public static void register(DeferredRegister<FluidType> fluidTypes, DeferredRegister<Fluid> fluids) {",
        "        for (FluidEntry entry : ENTRIES) {",
        "            BY_ID.put(entry.id(), entry);",
        "            DeferredHolder<FluidType, FluidType> type = fluidTypes.register(entry.id(),",
        "                    () -> new FluidType(FluidType.Properties.create()",
        "                            .descriptionId(\"fluid.earth_on_minecraft.\" + entry.id())",
        "                            .density(entry.density())",
        "                            .viscosity(entry.viscosity())",
        "                            .temperature(entry.temperatureK())",
        "                            .lightLevel(entry.luminous() ? 12 : 0)",
        "                            .canDrown(false)",
        "                            .canSwim(false)",
        "                            .supportsBoating(false)));",
        "            // The still fluid is created first; the flowing variant resolves it from the map.",
        "            DeferredHolder<Fluid, Fluid> source = fluids.register(entry.id(),",
        "                    () -> new BaseFlowingFluid.Source(properties(entry.id(), type, false)));",
        "            DeferredHolder<Fluid, Fluid> flowing = fluids.register(\"flowing_\" + entry.id(),",
        "                    () -> new BaseFlowingFluid.Flowing(properties(entry.id(), type, true)));",
        "            REGISTERED.put(entry.id(), new Registered(type, source, flowing));",
        "        }",
        "    }",
        "",
        "    private static BaseFlowingFluid.Properties properties(String id, DeferredHolder<FluidType, FluidType> type,",
        "                                                          boolean flowingVariant) {",
        "        return new BaseFlowingFluid.Properties(",
        "                type,",
        "                () -> resolve(id, false),",
        "                () -> resolve(id, true))",
        "                .slopeFindDistance(entry(id).stateOfMatter().equals(\"gas\") ? 1 : 3)",
        "                .levelDecreasePerBlock(entry(id).stateOfMatter().equals(\"gas\") ? 2 : 1)",
        "                .tickRate(entry(id).stateOfMatter().equals(\"gas\") ? 2 : 5)",
        "                .explosionResistance(100.0F);",
        "    }",
        "",
        "    private static Fluid resolve(String id, boolean flowingVariant) {",
        "        Registered registered = REGISTERED.get(id);",
        "        if (registered == null) {",
        "            return null;",
        "        }",
        "        return (flowingVariant ? registered.flowing() : registered.source()).get();",
        "    }",
        "",
        "    public static List<FluidEntry> entries() {",
        "        return ENTRIES;",
        "    }",
        "",
        "    public static Map<String, Registered> registered() {",
        "        return Map.copyOf(REGISTERED);",
        "    }",
        "",
        "    public static FluidEntry entry(String id) {",
        "        return BY_ID.get(id);",
        "    }",
        "",
        "    public static DeferredHolder<Fluid, Fluid> source(String id) {",
        "        Registered registered = REGISTERED.get(id);",
        "        return registered == null ? null : registered.source();",
        "    }",
        "}",
        "",
    ])
    return "\n".join(lines)


def merge_lang(lang: str, slug: str, en: str, zh: str) -> None:
    path = LANG / f"{lang}.json"
    data = json.loads(path.read_text(encoding="utf-8-sig"))
    data[f"fluid.earth_on_minecraft.{slug}"] = en if lang == "en_us" else zh
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--spec", default=str(DEFAULT_SPEC))
    parser.add_argument("--skip-art", action="store_true")
    args = parser.parse_args()
    spec = pathlib.Path(args.spec)
    records = load("fluids.jsonl", spec)

    entries = []
    textures = 0
    for record in records:
        slug = record["id"].split(":", 1)[1]
        matter = record.get("state_of_matter") or "liquid"
        base = FLUID_COLORS.get(slug, MATTER_COLORS.get(matter, (100, 120, 130)))
        luminous = slug in LUMINOUS
        entries.append({
            "id": slug,
            "state_of_matter": matter,
            "density": int(record.get("density") or 1000),
            "viscosity": int(record.get("viscosity") or 1000),
            "temperature_k": int(record.get("temperature_k") or 293),
            "luminous": luminous,
        })
        if not args.skip_art:
            FLUID_TEXTURES.mkdir(parents=True, exist_ok=True)
            draw_still(slug, base, luminous).save(FLUID_TEXTURES / f"{slug}_still.png", optimize=True)
            draw_flow(slug, base, luminous).save(FLUID_TEXTURES / f"{slug}_flow.png", optimize=True)
            textures += 2
        for lang in ("en_us", "zh_cn"):
            merge_lang(lang, slug, record.get("en") or slug, record.get("zh") or slug)

    SPEC_FLUIDS_JAVA.write_text(generate_java(entries), encoding="utf-8")
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST.write_text(json.dumps({
        "schema_version": 1,
        "source": str(spec),
        "fluids": len(entries),
        "textures": textures,
        "template_art": True,
        "note": "Fluid textures are deterministic template art pending the approved art pipeline.",
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"SPEC_FLUIDS fluids={len(entries)} textures={textures}")


if __name__ == "__main__":
    main()
