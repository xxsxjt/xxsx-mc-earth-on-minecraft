#!/usr/bin/env python3
"""Validate Earth on Minecraft resource files without requiring Minecraft to boot."""

from __future__ import annotations

import json
import hashlib
import pathlib
import re
import struct
import sys

from PIL import Image


ROOT = pathlib.Path(__file__).resolve().parents[1]
RES = ROOT / "neoforge-26.2" / "src" / "main" / "resources"
ASSETS = RES / "assets" / "earth_on_minecraft"
JAVA_ENTRYPOINT = ROOT / "neoforge-26.2" / "src" / "main" / "java" / "com" / "xxsx" / "earthonminecraft" / "EarthOnMinecraft.java"
JAVA_SPEC_ITEMS = ROOT / "neoforge-26.2" / "src" / "main" / "java" / "com" / "xxsx" / "earthonminecraft" / "SpecItems.java"
BUILD_GRADLE = ROOT / "neoforge-26.2" / "build.gradle"
JAVA_ROOT = ROOT / "neoforge-26.2" / "src" / "main" / "java" / "com" / "xxsx" / "earthonminecraft"
MACHINE_PROFILES = RES / "data" / "earth_on_minecraft" / "earth" / "quality" / "machine_profiles.json"
UI_STATE_MATRIX = ROOT / "docs" / "quality" / "ui-state-matrix.json"
VERTICAL_SLICE_TEMPLATE = ROOT / "docs" / "quality" / "vertical-slice-template.json"
MODS_TOML = RES / "META-INF" / "neoforge.mods.toml"
MOD_ICON = RES / "earth_on_minecraft.png"
VANILLA_ORE_PLACED_FEATURES = (
    "ore_coal_lower",
    "ore_coal_upper",
    "ore_copper",
    "ore_copper_large",
    "ore_diamond",
    "ore_diamond_buried",
    "ore_diamond_large",
    "ore_diamond_medium",
    "ore_emerald",
    "ore_gold",
    "ore_gold_extra",
    "ore_gold_lower",
    "ore_iron_middle",
    "ore_iron_small",
    "ore_iron_upper",
    "ore_lapis",
    "ore_lapis_buried",
    "ore_redstone",
    "ore_redstone_lower",
)

FORBIDDEN_PLAYER_TEXT = (
    ("placeholder", "use player-facing wording instead of development placeholders"),
    ("占位", "use player-facing wording instead of development placeholders"),
    ("push item drops", "conveyors use controlled cargo, not dropped-item pushing"),
    ("entities forward", "conveyors should not claim they push entities"),
)

INTEGRATION_TAG_REQUIREMENTS = {
    "data/earth_on_minecraft/tags/item/water/potable.json": {
        "earth_on_minecraft:softened_water",
    },
    "data/earth_on_minecraft/tags/item/water/treated.json": {
        "earth_on_minecraft:softened_water",
    },
    "data/earth_on_minecraft/tags/item/water/treatment_inputs.json": {
        "earth_on_minecraft:hard_water_sample",
        "earth_on_minecraft:activated_carbon_filter",
    },
    "data/earth_on_minecraft/tags/item/soil/amendments.json": {
        "earth_on_minecraft:humus_sample",
        "earth_on_minecraft:fertilizer_blend",
    },
    "data/earth_on_minecraft/tags/item/construction/materials.json": {
        "earth_on_minecraft:cement_powder",
        "earth_on_minecraft:asphalt",
    },
    "data/earth_on_minecraft/tags/item/electrical/conductors.json": {
        "earth_on_minecraft:copper_wire",
        "earth_on_minecraft:copper_busbar",
    },
    "data/earth_on_minecraft/tags/item/electrical/insulators.json": {
        "earth_on_minecraft:ceramic_insulator",
        "earth_on_minecraft:rubber_gasket",
    },
    "data/earth_on_minecraft/tags/item/filtration/media.json": {
        "earth_on_minecraft:activated_carbon",
        "earth_on_minecraft:activated_carbon_filter",
    },
    "data/earth_on_minecraft/tags/item/power/sources.json": {
        "earth_on_minecraft:nuclear_heat_module",
    },
    "data/earth_on_minecraft/tags/item/power/connectors.json": {
        "earth_on_minecraft:copper_wire",
    },
    "data/earth_on_minecraft/tags/block/power/generators.json": {
        "earth_on_minecraft:combustion_generator",
        "earth_on_minecraft:steam_turbine_generator",
    },
    "data/earth_on_minecraft/tags/block/power/connectors.json": {
        "earth_on_minecraft:copper_power_cable",
    },
    "data/earth_on_minecraft/tags/block/environment/heat_sources.json": {
        "earth_on_minecraft:industrial_kiln",
    },
    "data/earth_on_minecraft/tags/block/environment/air_pollution_sources.json": {
        "earth_on_minecraft:combustion_generator",
    },
    "data/earth_human/tags/item/care/hydration.json": {
        "earth_on_minecraft:softened_water",
    },
}


def fail(message: str) -> None:
    print(f"ERROR: {message}")
    sys.exit(1)


def validate_json() -> int:
    count = 0
    for path in RES.rglob("*.json"):
        try:
            json.loads(path.read_text(encoding="utf-8-sig"))
        except Exception as exc:  # noqa: BLE001 - this is a diagnostic script.
            fail(f"bad json {path.relative_to(ROOT)}: {exc}")
        count += 1
    return count


def validate_png_headers() -> int:
    count = 0
    for path in ASSETS.rglob("*.png"):
        data = path.read_bytes()
        if not data.startswith(b"\x89PNG\r\n\x1a\n"):
            fail(f"not a png {path.relative_to(ROOT)}")
        if len(data) < 24:
            fail(f"truncated png {path.relative_to(ROOT)}")
        width, height = struct.unpack(">II", data[16:24])
        if width <= 0 or height <= 0:
            fail(f"invalid png size {path.relative_to(ROOT)}: {width}x{height}")
        count += 1
    return count


def png_dimensions(path: pathlib.Path) -> tuple[int, int]:
    data = path.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n") or len(data) < 24:
        fail(f"invalid png {path.relative_to(ROOT)}")
    return struct.unpack(">II", data[16:24])


def validate_mod_icon() -> int:
    metadata = MODS_TOML.read_text(encoding="utf-8")
    if not re.search(r'(?m)^logoFile\s*=\s*"earth_on_minecraft\.png"\s*$', metadata):
        fail("neoforge.mods.toml must declare logoFile=\"earth_on_minecraft.png\"")
    if not re.search(r'(?m)^logoBlur\s*=\s*false\s*$', metadata):
        fail("pixel-art mod icon must declare logoBlur=false")
    if not MOD_ICON.exists():
        fail(f"missing mod icon {MOD_ICON.relative_to(ROOT)}")
    width, height = png_dimensions(MOD_ICON)
    if width != height or width < 256:
        fail(f"mod icon must be square and at least 256x256, got {width}x{height}")
    with Image.open(MOD_ICON).convert("RGBA") as image:
        alpha_min, alpha_max = image.getchannel("A").getextrema()
        if alpha_min != 0 or alpha_max != 255:
            fail(f"mod icon must contain true transparency and opaque pixels, got alpha {alpha_min}..{alpha_max}")
        if any(image.getpixel(point)[3] != 0 for point in (
                (0, 0), (width - 1, 0), (0, height - 1), (width - 1, height - 1))):
            fail("mod icon corners must be transparent")
    return 1


def connected_ore_ids() -> list[str]:
    java = JAVA_ENTRYPOINT.read_text(encoding="utf-8")
    ids = re.findall(r'oreBlock\("([a-z0-9_]+)"', java)
    if not ids:
        fail("no oreBlock registrations found for connected ore validation")
    if len(ids) != len(set(ids)):
        fail("duplicate oreBlock registration found")
    return ids


def validate_connected_ore_assets() -> int:
    ore_ids = connected_ore_ids()
    model_root = ASSETS / "models" / "block" / "connected"
    texture_root = ASSETS / "textures" / "block" / "connected"
    directions = ("down", "up", "north", "south", "west", "east")
    expected_models = {
        f"{block_id}_{suffix}.json"
        for block_id in ore_ids
        for suffix in ("center", *(f"edge_{direction}" for direction in directions))
    }
    expected_textures = {f"{block_id}_center.png" for block_id in ore_ids}
    actual_models = {path.name for path in model_root.glob("*.json")}
    actual_textures = {path.name for path in texture_root.glob("*.png")}
    if actual_models != expected_models:
        fail(
            "connected ore model set mismatch: "
            f"missing={sorted(expected_models - actual_models)} extra={sorted(actual_models - expected_models)}"
        )
    if actual_textures != expected_textures:
        fail(
            "connected ore texture set mismatch: "
            f"missing={sorted(expected_textures - actual_textures)} extra={sorted(actual_textures - expected_textures)}"
        )

    for block_id in ore_ids:
        source_texture = ASSETS / "textures" / "block" / f"{block_id}.png"
        center_texture = texture_root / f"{block_id}_center.png"
        if png_dimensions(source_texture) != png_dimensions(center_texture):
            fail(f"connected center texture size mismatch for {block_id}")

        center_model = json.loads((model_root / f"{block_id}_center.json").read_text(encoding="utf-8-sig"))
        expected_center_ref = f"earth_on_minecraft:block/connected/{block_id}_center"
        if center_model.get("parent") != "minecraft:block/cube_all":
            fail(f"connected center model has wrong parent for {block_id}")
        if center_model.get("textures", {}).get("all") != expected_center_ref:
            fail(f"connected center model has wrong texture for {block_id}")

        expected_ore_ref = f"earth_on_minecraft:block/{block_id}"
        for direction in directions:
            path = model_root / f"{block_id}_edge_{direction}.json"
            model = json.loads(path.read_text(encoding="utf-8-sig"))
            textures = model.get("textures", {})
            if textures.get("ore") != expected_ore_ref or textures.get("particle") != expected_ore_ref:
                fail(f"connected edge model has wrong texture for {block_id}/{direction}")
            elements = model.get("elements")
            if not isinstance(elements, list) or len(elements) != 4:
                fail(f"connected edge model must contain four face strips for {block_id}/{direction}")
            for element in elements:
                faces = element.get("faces") if isinstance(element, dict) else None
                if not isinstance(faces, dict) or len(faces) != 1:
                    fail(f"connected edge strip must contain one face for {block_id}/{direction}")
                face_name, face = next(iter(faces.items()))
                if not isinstance(face, dict) or face.get("cullface") != face_name:
                    fail(f"connected edge strip has wrong cullface for {block_id}/{direction}")
    return len(ore_ids)


def texture_refs(model_data: dict) -> list[str]:
    refs: list[str] = []
    textures = model_data.get("textures")
    if isinstance(textures, dict):
        refs.extend(value for value in textures.values() if isinstance(value, str))
    model = model_data.get("model")
    if isinstance(model, dict):
        nested = model.get("textures")
        if isinstance(nested, dict):
            refs.extend(value for value in nested.values() if isinstance(value, str))
    return refs


def model_path(ref: str) -> pathlib.Path | None:
    if ":" not in ref:
        return None
    namespace, model = ref.split(":", 1)
    if namespace != "earth_on_minecraft":
        return None
    return ASSETS / "models" / f"{model}.json"


def validate_model_texture_refs() -> int:
    count = 0
    roots = [ASSETS / "models", ASSETS / "items"]
    for root in roots:
        for path in root.rglob("*.json"):
            model = json.loads(path.read_text(encoding="utf-8-sig"))
            count += 1
            for ref in texture_refs(model):
                if ref.startswith("#") or ":" not in ref:
                    continue
                namespace, tex = ref.split(":", 1)
                if namespace != "earth_on_minecraft":
                    continue
                texture = ASSETS / "textures" / f"{tex}.png"
                if not texture.exists():
                    fail(f"missing texture {ref} referenced by {path.relative_to(ROOT)}")
    return count


def model_refs_from_blockstate(blockstate_data: dict) -> list[str]:
    refs: list[str] = []
    variants = blockstate_data.get("variants")
    if isinstance(variants, dict):
        for value in variants.values():
            entries = value if isinstance(value, list) else [value]
            for entry in entries:
                if isinstance(entry, dict) and isinstance(entry.get("model"), str):
                    refs.append(entry["model"])
    multipart = blockstate_data.get("multipart")
    if isinstance(multipart, list):
        for part in multipart:
            if not isinstance(part, dict):
                continue
            apply = part.get("apply")
            entries = apply if isinstance(apply, list) else [apply]
            for entry in entries:
                if isinstance(entry, dict) and isinstance(entry.get("model"), str):
                    refs.append(entry["model"])
    return refs


def validate_model_refs() -> int:
    count = 0
    for path in (ASSETS / "items").glob("*.json"):
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        model = data.get("model")
        if isinstance(model, dict) and isinstance(model.get("model"), str):
            target = model_path(model["model"])
            if target is not None and not target.exists():
                fail(f"missing item model {model['model']} referenced by {path.relative_to(ROOT)}")
        count += 1
    for path in (ASSETS / "blockstates").glob("*.json"):
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        for ref in model_refs_from_blockstate(data):
            target = model_path(ref)
            if target is not None and not target.exists():
                fail(f"missing blockstate model {ref} referenced by {path.relative_to(ROOT)}")
        count += 1
    return count


def registry_ids() -> tuple[list[str], list[str], list[str]]:
    java = JAVA_ENTRYPOINT.read_text(encoding="utf-8")
    block_ids = [
        match.group(1)
        for match in re.finditer(r'DeferredBlock<[^>]+>\s+\w+\s*=\s*\w+\("([a-z0-9_]+)"', java)
    ]
    block_ids.extend(
        match.group(1)
        for match in re.finditer(r'DeferredBlock<[^>]+>\s+\w+\s*=\s*BLOCKS\.registerBlock\(\s*"([a-z0-9_]+)"', java)
        if match.group(1) not in block_ids
    )
    spec_blocks_path = JAVA_ROOT / "SpecBlocks.java"
    if spec_blocks_path.exists():
        spec_blocks = spec_blocks_path.read_text(encoding="utf-8")
        block_ids.extend(
            match.group(1)
            for match in re.finditer(r'new BlockEntry\("([a-z0-9_]+)"', spec_blocks)
        )
    lode_blocks_path = JAVA_ROOT / "SpecLodeBlocks.java"
    if lode_blocks_path.exists():
        lode_blocks = lode_blocks_path.read_text(encoding="utf-8")
        block_ids.extend(
            match.group(1)
            for match in re.finditer(r'new Entry\("([a-z0-9_]+)"', lode_blocks)
        )
    depleted_blocks_path = JAVA_ROOT / "SpecDepletedBlocks.java"
    if depleted_blocks_path.exists():
        depleted_blocks = depleted_blocks_path.read_text(encoding="utf-8")
        block_ids.extend(
            match.group(1)
            for match in re.finditer(r'new Entry\("([a-z0-9_]+)"', depleted_blocks)
        )
    direct_item_ids = [
        match.group(1)
        for match in re.finditer(r'DeferredItem<[^>]+>\s+\w+\s*=\s*\w+\("([a-z0-9_]+)"', java)
    ]
    if JAVA_SPEC_ITEMS.exists():
        spec_items = JAVA_SPEC_ITEMS.read_text(encoding="utf-8")
        direct_item_ids.extend(
            match.group(1)
            for match in re.finditer(r'new Entry\("([a-z0-9_]+)"', spec_items)
        )
    # Projection blocks are internal ghost helpers and intentionally have no inventory item.
    block_item_ids = [block_id for block_id in block_ids if block_id != "structure_projection"]
    stack_item_ids = sorted(set(direct_item_ids + block_item_ids))
    return block_ids, direct_item_ids, stack_item_ids


def validate_registry_resource_coverage() -> tuple[int, int, int, set[str]]:
    block_ids, direct_item_ids, stack_item_ids = registry_ids()
    for lang in ("en_us", "zh_cn"):
        data = json.loads((ASSETS / "lang" / f"{lang}.json").read_text(encoding="utf-8-sig"))
        for block_id in block_ids:
            if block_id == "structure_projection":
                continue
            key = f"block.earth_on_minecraft.{block_id}"
            if key not in data:
                fail(f"missing {lang} block lang key {key}")
        for item_id in stack_item_ids:
            key = f"item.earth_on_minecraft.{item_id}"
            if key not in data:
                fail(f"missing {lang} item lang key {key}")
    for item_id in stack_item_ids:
        path = ASSETS / "items" / f"{item_id}.json"
        if not path.exists():
            fail(f"missing 26.2 item definition {path.relative_to(ROOT)}")
    for block_id in block_ids:
        path = ASSETS / "blockstates" / f"{block_id}.json"
        if not path.exists():
            fail(f"missing blockstate {path.relative_to(ROOT)}")
    return len(block_ids), len(direct_item_ids), len(stack_item_ids), set(block_ids) | set(stack_item_ids)


def validate_first_iron_bootstrap() -> int:
    recipe_root = RES / "data" / "earth_on_minecraft" / "recipe"

    def recipe(name: str) -> dict:
        path = recipe_root / f"{name}.json"
        if not path.exists():
            fail(f"missing first-iron bootstrap recipe {path.relative_to(ROOT)}")
        return json.loads(path.read_text(encoding="utf-8-sig"))

    ball_mill = recipe("ball_mill")
    ball_mill_inputs = set(walk_values(ball_mill.get("key", {})))
    if "minecraft:stone" not in ball_mill_inputs or "minecraft:furnace" not in ball_mill_inputs:
        fail("first Ball Mill recipe must use stone and a furnace")
    if "minecraft:iron_ingot" in ball_mill_inputs:
        fail("first Ball Mill recipe must not require an iron ingot")

    separator = recipe("magnetic_separator")
    separator_inputs = set(walk_values(separator.get("key", {})))
    if not {"minecraft:copper_ingot", "earth_on_minecraft:magnetite_chunk"}.issubset(separator_inputs):
        fail("first Magnetic Separator recipe must use copper and a magnetite chunk")
    if "minecraft:iron_ingot" in separator_inputs:
        fail("first Magnetic Separator recipe must not require an iron ingot")

    reduction_furnace = recipe("reduction_furnace")
    reduction_inputs = set(walk_values(reduction_furnace.get("key", {})))
    if not {"minecraft:brick", "minecraft:furnace"}.issubset(reduction_inputs):
        fail("first Reduction Furnace recipe must use bricks and a furnace")
    if "minecraft:iron_ingot" in reduction_inputs or "minecraft:blast_furnace" in reduction_inputs:
        fail("first Reduction Furnace recipe must not require iron or a blast furnace")

    anvil = recipe("ironworking_anvil")
    anvil_inputs = set(walk_values(anvil.get("key", {})))
    if not {"minecraft:stone", "minecraft:cobblestone"}.issubset(anvil_inputs):
        fail("stone ironworking anvil must be craftable from early stone materials")

    hammer = recipe("geology_hammer")
    hammer_inputs = set(walk_values(hammer.get("key", {})))
    if not {"minecraft:cobblestone", "minecraft:stick"}.issubset(hammer_inputs):
        fail("geology hammer must be craftable from cobblestone and sticks")

    coke = recipe("coke_from_coal")
    if coke.get("type") != "minecraft:smelting" or coke.get("ingredient") != "minecraft:coal":
        fail("coke must be obtainable by smelting coal")
    if coke.get("result", {}).get("id") != "earth_on_minecraft:coke":
        fail("coal smelting must produce coke for the first-iron reduction route")

    retired = (
        "iron_ingot_from_smelting_magnetite_chunk.json",
        "iron_ingot_from_smelting_magnetite_dust.json",
        "iron_ingot_from_blasting_magnetite_chunk.json",
        "iron_ingot_from_blasting_magnetite_dust.json",
    )
    if any((recipe_root / name).exists() for name in retired):
        fail("magnetite chunk/dust direct iron-ingot recipes must remain retired")
    return 7


def validate_player_facing_text() -> int:
    roots = [
        ASSETS / "lang",
        ROOT / "neoforge-26.2" / "src" / "main" / "java" / "com" / "xxsx" / "earthonminecraft" / "client",
    ]
    count = 0
    for root in roots:
        for path in root.rglob("*"):
            if path.suffix not in {".json", ".java"}:
                continue
            text = path.read_text(encoding="utf-8-sig")
            for needle, reason in FORBIDDEN_PLAYER_TEXT:
                if needle in text:
                    fail(f"player-facing text contains {needle!r} in {path.relative_to(ROOT)}: {reason}")
            count += 1
    return count


def validate_release_note_sha() -> int:
    build_text = BUILD_GRADLE.read_text(encoding="utf-8-sig")
    version_match = re.search(r"^version\s*=\s*['\"]([^'\"]+)['\"]", build_text, re.MULTILINE)
    if version_match is None:
        fail(f"cannot determine project version from {BUILD_GRADLE.relative_to(ROOT)}")
    version = version_match.group(1)
    current_jar = ROOT / "neoforge-26.2" / "build" / "libs" / f"earth-on-minecraft-neoforge-26.1.2-{version}.jar"
    current_release_notes = ROOT / "docs" / f"release-notes-{version}-beta.md"
    if not current_release_notes.exists():
        return 0
    text = current_release_notes.read_text(encoding="utf-8-sig")
    match = re.search(r"SHA256: `([A-Fa-f0-9]{64})`", text)
    if match is None:
        fail(f"missing SHA256 in {current_release_notes.relative_to(ROOT)}")
    if current_jar.exists():
        digest = hashlib.sha256(current_jar.read_bytes()).hexdigest().upper()
        if match.group(1).upper() != digest:
            # The release note records an artifact that was actually published. A
            # local rebuild after further source work is expected to differ; the
            # gate must not force the published record to be rewritten.
            print(f"WARN: local rebuild differs from the published release note for "
                  f"{current_jar.relative_to(ROOT)}; published SHA256 stays authoritative")
    return 1


def walk_values(value):
    if isinstance(value, dict):
        for nested in value.values():
            yield from walk_values(nested)
    elif isinstance(value, list):
        for nested in value:
            yield from walk_values(nested)
    else:
        yield value


def tag_values(data: dict) -> list[str]:
    result: list[str] = []
    values = data.get("values", [])
    if not isinstance(values, list):
        return result
    for entry in values:
        if isinstance(entry, str):
            result.append(entry)
        elif isinstance(entry, dict) and isinstance(entry.get("id"), str):
            result.append(entry["id"])
    return result


def validate_local_reference(value: str, path: pathlib.Path, known_ids: set[str]) -> None:
    is_tag = value.startswith("#")
    reference = value[1:] if is_tag else value
    if not reference.startswith("earth_on_minecraft:"):
        return
    local_id = reference.split(":", 1)[1]
    parts = path.relative_to(RES / "data").parts
    registry_type = parts[2] if len(parts) >= 4 and parts[1] == "tags" else None
    if not is_tag:
        if registry_type in (None, "item", "block") and local_id not in known_ids:
            fail(f"unknown item/block id {reference} referenced by {path.relative_to(ROOT)}")
        if registry_type not in (None, "item", "block"):
            target = RES / "data" / "earth_on_minecraft" / registry_type / f"{local_id}.json"
            if not target.exists():
                fail(f"unknown {registry_type} id {reference} referenced by {path.relative_to(ROOT)}")
        return

    if registry_type is None:
        return
    target = RES / "data" / "earth_on_minecraft" / "tags" / registry_type / f"{local_id}.json"
    if not target.exists():
        fail(f"unknown local tag {reference} referenced by {path.relative_to(ROOT)}")


def validate_recipe_and_tag_refs(known_ids: set[str]) -> int:
    count = 0
    roots = [RES / "data" / "earth_on_minecraft" / "recipe"]
    roots.extend(path / "tags" for path in (RES / "data").iterdir() if (path / "tags").is_dir())
    for root in roots:
        for path in root.rglob("*.json"):
            data = json.loads(path.read_text(encoding="utf-8-sig"))
            for value in walk_values(data):
                if not isinstance(value, str):
                    continue
                validate_local_reference(value, path, known_ids)
            count += 1
    return count


def validate_living_world(known_ids: set[str]) -> tuple[int, int, int]:
    langs = {
        lang: json.loads((ASSETS / "lang" / f"{lang}.json").read_text(encoding="utf-8-sig"))
        for lang in ("en_us", "zh_cn")
    }

    def require_lang(key: str, source: pathlib.Path) -> None:
        for lang, values in langs.items():
            if key not in values:
                fail(f"missing {lang} living-world lang key {key} referenced by {source.relative_to(ROOT)}")

    role_dir = RES / "data" / "earth_on_minecraft" / "settlements" / "resident_roles"
    trade_root = RES / "data" / "earth_on_minecraft" / "villager_trade" / "resident"
    tag_root = RES / "data" / "minecraft" / "tags" / "villager_trade"
    expected_trades: set[str] = set()
    expected_tags: dict[tuple[str, int], set[str]] = {}

    for role_path in sorted(role_dir.glob("*.json")):
        role = json.loads(role_path.read_text(encoding="utf-8-sig"))
        role_name = role_path.stem
        role_id = f"earth_on_minecraft:{role_name}"
        require_lang(role["title_key"], role_path)
        require_lang(role["identity_key"], role_path)
        professions = [
            value.split(":", 1)[1]
            for value in role.get("vanilla_professions", [])
            if value.startswith("minecraft:") and value != "minecraft:none"
        ]
        for index, definition in enumerate(role.get("trades", []), start=1):
            level = max(1, min(5, int(definition["level"])))
            local_path = f"resident/{role_name}/level_{level}_{index}"
            trade_id = f"earth_on_minecraft:{local_path}"
            expected_trades.add(local_path)
            trade_path = RES / "data" / "earth_on_minecraft" / "villager_trade" / f"{local_path}.json"
            if not trade_path.exists():
                fail(f"missing generated living-world trade {trade_path.relative_to(ROOT)}")
            trade = json.loads(trade_path.read_text(encoding="utf-8-sig"))
            predicate = trade.get("merchant_predicate", {}).get("predicate", {}).get("minecraft:nbt", "")
            if role_id not in predicate:
                fail(f"trade predicate does not select {role_id}: {trade_path.relative_to(ROOT)}")
            for field in ("input", "output"):
                item_id = definition[field]
                if item_id.startswith("earth_on_minecraft:") and item_id.split(":", 1)[1] not in known_ids:
                    fail(f"unknown living-world trade item {item_id} in {role_path.relative_to(ROOT)}")
            for profession in professions:
                expected_tags.setdefault((profession, level), set()).add(trade_id)

    actual_trades = {
        path.relative_to(trade_root.parent).with_suffix("").as_posix()
        for path in trade_root.rglob("*.json")
    }
    if actual_trades != expected_trades:
        missing = sorted(expected_trades - actual_trades)
        stale = sorted(actual_trades - expected_trades)
        fail(f"living-world generated trade drift; missing={missing} stale={stale}")

    for (profession, level), expected_values in sorted(expected_tags.items()):
        tag_path = tag_root / profession / f"level_{level}.json"
        if not tag_path.exists():
            fail(f"missing generated villager trade tag {tag_path.relative_to(ROOT)}")
        values = set(tag_values(json.loads(tag_path.read_text(encoding="utf-8-sig"))))
        if not expected_values.issubset(values):
            fail(f"villager trade tag is missing role trades: {tag_path.relative_to(ROOT)}")

    settlement_root = RES / "data" / "earth_on_minecraft" / "settlements"
    for path in sorted((settlement_root / "name_pools").glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        for key in data.get("name_keys", []):
            require_lang(key, path)
    for path in sorted((settlement_root / "profiles").glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        tier = data.get("tier")
        if not isinstance(tier, int) or tier not in (1, 2, 3, 4):
            fail(f"settlement profile must declare tier 1..4 in {path.relative_to(ROOT)}")
        for field in ("display_name_key", "scale_key", "technology_key"):
            require_lang(data[field], path)
        for field in ("name_keys", "industry_keys", "demand_keys", "supply_keys"):
            for key in data.get(field, []):
                require_lang(key, path)

    required_profiles = {
        "primitive_hamlet": 1,
        "mining_settlement": 2,
        "industrial_town": 3,
        "mechanized_city": 4,
        "agricultural_village": 1,
        "resource_village": 2,
        "mechanized_town": 4,
    }
    for profile_name, expected_tier in required_profiles.items():
        profile_path = settlement_root / "profiles" / f"{profile_name}.json"
        if not profile_path.exists():
            fail(f"missing settlement profile {profile_path.relative_to(ROOT)}")
        profile_data = json.loads(profile_path.read_text(encoding="utf-8-sig"))
        if profile_data.get("tier") != expected_tier:
            fail(f"settlement profile {profile_name} must have tier {expected_tier}")

    required_chest_loot = (
        "settlement_primitive_hamlet",
        "settlement_mining_settlement",
        "settlement_industrial_town",
        "settlement_mechanized_city",
    )
    for chest_table in required_chest_loot:
        chest_path = RES / "data" / "earth_on_minecraft" / "loot_table" / "chests" / f"{chest_table}.json"
        if not chest_path.exists():
            fail(f"missing settlement chest loot table {chest_path.relative_to(ROOT)}")
        chest_data = json.loads(chest_path.read_text(encoding="utf-8-sig"))
        for pool in chest_data.get("pools", []):
            for entry in pool.get("entries", []):
                item_name = entry.get("name", "")
                if item_name.startswith("earth_on_minecraft:") and item_name.split(":", 1)[1] not in known_ids:
                    fail(f"unknown item {item_name} in {chest_path.relative_to(ROOT)}")

    required_loot = {
        "villager_work_items": "living/villager_work_items",
        "pillager_stolen_supplies": "living/pillager_stolen_supplies",
    }
    for modifier_name, table_id in required_loot.items():
        modifier_path = RES / "data" / "earth_on_minecraft" / "loot_modifiers" / f"{modifier_name}.json"
        table_path = RES / "data" / "earth_on_minecraft" / "loot_table" / f"{table_id}.json"
        if not modifier_path.exists() or not table_path.exists():
            fail(f"missing Living World loot modifier/table pair {modifier_name}")
        modifier = json.loads(modifier_path.read_text(encoding="utf-8-sig"))
        if modifier.get("table") != f"earth_on_minecraft:{table_id}":
            fail(f"wrong Living World loot table reference in {modifier_path.relative_to(ROOT)}")

    return len(expected_trades), len(expected_tags), len(required_loot)


def validate_integration_contract(known_ids: set[str]) -> int:
    count = 0
    for relative, required_values in INTEGRATION_TAG_REQUIREMENTS.items():
        path = RES / relative
        if not path.exists():
            fail(f"missing integration tag {path.relative_to(ROOT)}")
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        if data.get("replace") is not False:
            fail(f"integration tag must use replace=false: {path.relative_to(ROOT)}")
        values = set(tag_values(data))
        missing = sorted(required_values - values)
        if missing:
            fail(f"integration tag {path.relative_to(ROOT)} is missing {', '.join(missing)}")
        for value in values:
            validate_local_reference(value, path, known_ids)
        count += 1
    return count


def validate_worldgen_refs() -> int:
    configured = {path.stem for path in (RES / "data" / "earth_on_minecraft" / "worldgen" / "configured_feature").glob("*.json")}
    placed = {path.stem for path in (RES / "data" / "earth_on_minecraft" / "worldgen" / "placed_feature").glob("*.json")}
    count = 0
    for path in (RES / "data" / "earth_on_minecraft" / "worldgen" / "placed_feature").glob("*.json"):
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        feature = data.get("feature")
        if isinstance(feature, str) and feature.startswith("earth_on_minecraft:"):
            local_id = feature.split(":", 1)[1]
            if local_id not in configured:
                fail(f"unknown configured feature {feature} referenced by {path.relative_to(ROOT)}")
        count += 1
    for path in (RES / "data" / "earth_on_minecraft" / "neoforge" / "biome_modifier").glob("*.json"):
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        features = data.get("features")
        refs = features if isinstance(features, list) else [features]
        for ref in refs:
            if isinstance(ref, str) and ref.startswith("earth_on_minecraft:"):
                local_id = ref.split(":", 1)[1]
                if local_id not in placed:
                    fail(f"unknown placed feature {ref} referenced by {path.relative_to(ROOT)}")
        count += 1
    return count


def absolute_height(value: object) -> int | None:
    if isinstance(value, dict) and isinstance(value.get("absolute"), int):
        return value["absolute"]
    return None


def validate_worldgen_height_ranges() -> int:
    count = 0
    root = RES / "data" / "earth_on_minecraft" / "worldgen" / "placed_feature"
    for path in root.glob("*.json"):
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        for placement in data.get("placement", []):
            if not isinstance(placement, dict) or placement.get("type") != "minecraft:height_range":
                continue
            height = placement.get("height")
            if not isinstance(height, dict):
                fail(f"missing height object in {path.relative_to(ROOT)}")
            min_y = absolute_height(height.get("min_inclusive"))
            max_y = absolute_height(height.get("max_inclusive"))
            if min_y is None or max_y is None:
                fail(f"non-absolute height range in {path.relative_to(ROOT)}")
            if min_y < -64 or max_y > 320 or min_y > max_y:
                fail(f"height out of vanilla 26.2 bounds in {path.relative_to(ROOT)}: {min_y}..{max_y}")
        count += 1
    return count


def validate_earth_strata_refs() -> int:
    configured = {path.stem for path in (RES / "data" / "earth_on_minecraft" / "worldgen" / "configured_feature").glob("*.json")}
    placed = {path.stem for path in (RES / "data" / "earth_on_minecraft" / "worldgen" / "placed_feature").glob("*.json")}
    count = 0
    root = RES / "data" / "earth_on_minecraft" / "earth" / "strata"
    for path in root.glob("*.json"):
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        for layer in data.get("layers", []) + data.get("planned_layers", []):
            if not isinstance(layer, dict):
                continue
            for field in ("background_features", "deposit_features"):
                for ref in layer.get(field, []):
                    if not isinstance(ref, str) or not ref.startswith("earth_on_minecraft:"):
                        continue
                    local_id = ref.split(":", 1)[1]
                    if local_id not in placed and local_id not in configured:
                        fail(f"unknown strata feature {ref} referenced by {path.relative_to(ROOT)}")
            y_range = layer.get("y_range")
            if isinstance(y_range, list) and len(y_range) == 2:
                min_y, max_y = y_range
                if not isinstance(min_y, int) or not isinstance(max_y, int) or min_y > max_y:
                    fail(f"bad strata y_range in {path.relative_to(ROOT)}: {y_range}")
        count += 1
    return count


def has_count_zero_placement(data: dict) -> bool:
    placement = data.get("placement")
    if not isinstance(placement, list):
        return False
    return any(
        isinstance(entry, dict) and entry.get("type") == "minecraft:count" and entry.get("count") == 0
        for entry in placement
    )


def validate_vanilla_ore_suppression() -> int:
    noise_path = RES / "data" / "minecraft" / "worldgen" / "noise" / "ore_veininess.json"
    if not noise_path.exists():
        fail(f"missing vanilla ore veininess override {noise_path.relative_to(ROOT)}")
    noise = json.loads(noise_path.read_text(encoding="utf-8-sig"))
    if noise.get("amplitudes") != [0.0]:
        fail(f"vanilla ore veininess is not disabled in {noise_path.relative_to(ROOT)}")

    count = 1
    root = RES / "data" / "minecraft" / "worldgen" / "placed_feature"
    for feature in VANILLA_ORE_PLACED_FEATURES:
        path = root / f"{feature}.json"
        if not path.exists():
            fail(f"missing vanilla ore placed_feature override {path.relative_to(ROOT)}")
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        if not has_count_zero_placement(data):
            fail(f"vanilla ore placed_feature is not count=0: {path.relative_to(ROOT)}")
        count += 1
    return count


def validate_quality_foundation() -> tuple[int, int, int, int, int]:
    source = JAVA_ENTRYPOINT.read_text(encoding="utf-8")
    registered_machines = set(re.findall(r'machineBlock\("([a-z0-9_]+)"', source))
    registered_devices = set(re.findall(r'energyGeneratorBlock\("([a-z0-9_]+)"', source))
    profile_data = json.loads(MACHINE_PROFILES.read_text(encoding="utf-8-sig"))
    if profile_data.get("schema_version") != 1:
        fail("machine profile schema_version must be 1")
    state_contract = profile_data.get("state_contract")
    if not isinstance(state_contract, dict) or set(state_contract) != {"idle", "running", "fault"}:
        fail("machine profiles must define idle, running, and fault state contracts")
    profiles = profile_data.get("machines")
    if not isinstance(profiles, list):
        fail("machine profiles must contain a machines list")
    devices = profile_data.get("devices")
    if not isinstance(devices, list):
        fail("machine profiles must contain a devices list")
    profile_ids = {profile.get("id") for profile in profiles if isinstance(profile, dict)}
    if profile_ids != registered_machines:
        fail(f"machine profile coverage mismatch: missing={sorted(registered_machines - profile_ids)} extra={sorted(profile_ids - registered_machines)}")
    device_ids = {profile.get("id") for profile in devices if isinstance(profile, dict)}
    if device_ids != registered_devices:
        fail(f"device profile coverage mismatch: missing={sorted(registered_devices - device_ids)} extra={sorted(device_ids - registered_devices)}")

    sounds_path = ASSETS / "sounds.json"
    sounds = json.loads(sounds_path.read_text(encoding="utf-8-sig"))
    face_keys = {"front", "back", "left", "right", "top", "bottom"}
    for group, group_profiles in (("machine", profiles), ("device", devices)):
        for profile in group_profiles:
            machine_id = profile["id"]
            if set(profile.get("faces", {})) != face_keys:
                fail(f"{group} profile must define six face responsibilities: {machine_id}")
            for cue in ("idle_cue", "running_cue", "fault_cue", "prototype", "silhouette", "particle_profile"):
                if not isinstance(profile.get(cue), str) or not profile[cue].strip():
                    fail(f"{group} profile missing {cue}: {machine_id}")
            event = profile.get("sound_event", "").removeprefix("earth_on_minecraft:")
            if event not in sounds:
                fail(f"missing custom {group} sound event {event}")
            ogg = ASSETS / "sounds" / group / machine_id / "run.ogg"
            if not ogg.exists() or not ogg.read_bytes().startswith(b"OggS"):
                fail(f"missing or invalid custom {group} sound {ogg.relative_to(ROOT)}")
            particle_id = f"{group}_{machine_id}_process"
            particle_json = ASSETS / "particles" / f"{particle_id}.json"
            particle_png = ASSETS / "textures" / "particle" / group / f"{machine_id}.png"
            if not particle_json.exists() or not particle_png.exists():
                fail(f"missing custom process particle for {machine_id}")
            source_group = "machines" if group == "machine" else "devices"
            blockbench_source = ROOT / "art" / "blockbench" / source_group / f"{machine_id}.bbmodel"
            if not blockbench_source.exists():
                fail(f"missing editable Blockbench source {blockbench_source.relative_to(ROOT)}")
            source_data = json.loads(blockbench_source.read_text(encoding="utf-8-sig"))
            if source_data.get("meta", {}).get("model_format") != "java_block":
                fail(f"invalid Blockbench model format for {machine_id}")
            if len(source_data.get("elements", [])) < 5:
                fail(f"Blockbench source has too little geometry for {machine_id}")
            for suffix in ("", "_active", "_fault"):
                model_path = ASSETS / "models" / "block" / f"{machine_id}{suffix}.json"
                model_data = json.loads(model_path.read_text(encoding="utf-8-sig"))
                if len(model_data.get("elements", [])) < 5:
                    fail(f"runtime model has too little geometry: {model_path.relative_to(ROOT)}")
            blockstate_path = ASSETS / "blockstates" / f"{machine_id}.json"
            variants = json.loads(blockstate_path.read_text(encoding="utf-8-sig")).get("variants", {})
            expected_variants = 16 if group == "machine" else 8
            if len(variants) != expected_variants:
                fail(f"unexpected state coverage for {machine_id}: {len(variants)} != {expected_variants}")
            if group == "machine" and not any("fault=true" in key for key in variants):
                fail(f"processing machine has no fault model state: {machine_id}")

    for event, file_name in (("machine.fault", "fault.ogg"), ("machine.complete", "complete.ogg")):
        path = ASSETS / "sounds" / "machine" / file_name
        if event not in sounds or not path.exists() or not path.read_bytes().startswith(b"OggS"):
            fail(f"missing machine feedback event {event}")

    matrix = json.loads(UI_STATE_MATRIX.read_text(encoding="utf-8-sig"))
    if matrix.get("schema_version") != 1 or set(matrix.get("languages", [])) != {"zh_cn", "en_us"}:
        fail("UI state matrix schema or languages are invalid")
    screens = matrix.get("screens")
    if not isinstance(screens, list):
        fail("UI state matrix must contain screens")
    required_screens = {
        "processing_machine", "energy_generator", "battery_box", "machine_side_config",
        "field_geology_notebook", "settlement_board",
    }
    screen_ids = {screen.get("id") for screen in screens if isinstance(screen, dict)}
    if screen_ids != required_screens:
        fail(f"UI state matrix screen mismatch: missing={sorted(required_screens - screen_ids)} extra={sorted(screen_ids - required_screens)}")
    ui_state_count = sum(len(screen.get("states", [])) for screen in screens)
    if ui_state_count < 20:
        fail(f"UI state matrix is too narrow: {ui_state_count}")

    api_files = {
        "EarthOnMinecraftApi.java", "GeologyQueryApi.java", "MaterialPropertyApi.java",
        "MachineProcessingApi.java", "EnergyApi.java", "LogisticsApi.java", "SettlementQueryApi.java",
    }
    api_root = JAVA_ROOT / "api" / "v1"
    actual_api_files = {path.name for path in api_root.glob("*.java")}
    if not api_files.issubset(actual_api_files):
        fail(f"missing api.v1 contracts: {sorted(api_files - actual_api_files)}")
    if not (ROOT / "docs" / "quality" / "runtime-checklist.md").exists():
        fail("missing runtime quality checklist")
    if not (ROOT / "art" / "blockbench" / "README.md").exists():
        fail("missing Blockbench source pipeline contract")

    required_layers = {
        "acquisition", "processing", "purpose", "jei", "handbook", "feedback", "failure_recovery", "tests",
    }
    template = json.loads(VERTICAL_SLICE_TEMPLATE.read_text(encoding="utf-8-sig"))
    if template.get("schema_version") != 1 or set(template.get("layers", {})) != required_layers:
        fail("vertical slice template must define all eight closure layers")
    slice_root = ROOT / "docs" / "quality" / "vertical-slices"
    for path in sorted(slice_root.glob("*.json")) if slice_root.exists() else ():
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        layers = data.get("layers", {})
        if data.get("schema_version") != 1 or set(layers) != required_layers:
            fail(f"invalid vertical slice manifest {path.relative_to(ROOT)}")
        if data.get("status") == "complete":
            unfinished = [name for name, layer in layers.items()
                          if layer.get("status") != "complete" or not layer.get("evidence")]
            if unfinished:
                fail(f"complete vertical slice has unfinished evidence in {path.relative_to(ROOT)}: {unfinished}")

    for path in (JAVA_ROOT / "ProcessingMachineBlock.java", JAVA_ROOT / "EnergyGeneratorBlock.java"):
        feedback_source = path.read_text(encoding="utf-8")
        if "SoundEvents." in feedback_source or "ParticleTypes." in feedback_source:
            fail(f"device feedback must use registered custom assets, not vanilla events: {path.relative_to(ROOT)}")
    return len(profiles), len(devices), ui_state_count, len(profiles) + len(devices) + 2, len(api_files)


def main() -> None:
    json_count = validate_json()
    png_count = validate_png_headers()
    mod_icon_count = validate_mod_icon()
    connected_ore_count = validate_connected_ore_assets()
    model_count = validate_model_texture_refs()
    ref_count = validate_model_refs()
    block_count, item_count, stack_count, known_ids = validate_registry_resource_coverage()
    data_ref_count = validate_recipe_and_tag_refs(known_ids)
    living_trade_count, living_tag_count, living_loot_count = validate_living_world(known_ids)
    integration_tag_count = validate_integration_contract(known_ids)
    worldgen_ref_count = validate_worldgen_refs()
    worldgen_height_count = validate_worldgen_height_ranges()
    strata_count = validate_earth_strata_refs()
    vanilla_ore_suppression_count = validate_vanilla_ore_suppression()
    machine_profile_count, device_profile_count, ui_state_count, feedback_count, api_count = validate_quality_foundation()
    player_text_count = validate_player_facing_text()
    iron_bootstrap_count = validate_first_iron_bootstrap()
    release_note_count = validate_release_note_sha()
    print(
        f"OK json={json_count} png={png_count} modIcons={mod_icon_count} connectedOres={connected_ore_count} models={model_count} refs={ref_count} "
        f"blocks={block_count} items={item_count} stacks={stack_count} "
        f"dataRefs={data_ref_count} integrationTags={integration_tag_count} worldgenRefs={worldgen_ref_count} "
        f"livingTrades={living_trade_count} livingTradeTags={living_tag_count} livingLoot={living_loot_count} "
        f"worldgenHeights={worldgen_height_count} strata={strata_count} "
        f"vanillaOreSuppression={vanilla_ore_suppression_count} machineProfiles={machine_profile_count} "
        f"deviceProfiles={device_profile_count} "
        f"uiStates={ui_state_count} feedback={feedback_count} apiV1={api_count} playerText={player_text_count} "
        f"ironBootstrap={iron_bootstrap_count} releaseNotes={release_note_count}"
    )


if __name__ == "__main__":
    main()
