"""Inventory of textures that still need a Gemini art prompt.

Read-only by default: pass --write to refresh docs/quality/art-pending.json.

Two different gaps are reported:

  * template art  - the texture exists but is still the deterministic placeholder
  * no texture    - a registered item or block has no texture file at all

The second case used to be invisible: the old version only walked files that already existed, so a
missing texture silently counted as done.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools/generate_gemini_art.py"
SPEC = pathlib.Path(r"D:\Codex\WORKSPACES\Projects\mc-agent\spec\eom")
JAVA = ROOT / "neoforge-26.2/src/main/java/com/xxsx/earthonminecraft"
ITEM_TEX = ROOT / "neoforge-26.2/src/main/resources/assets/earth_on_minecraft/textures/item"
BLOCK_TEX = ROOT / "neoforge-26.2/src/main/resources/assets/earth_on_minecraft/textures/block"
OUT = ROOT / "docs/quality/art-pending.json"
TEMPLATE_MAX_BYTES = 1200

# Registered block ids: these own a block texture, not an item texture.
BLOCK_PATTERNS = (
    r'\bmachineBlock\("([a-z0-9_]+)"',
    r'\boreBlock\("([a-z0-9_]+)"',
    r'\bminerBlock\("([a-z0-9_]+)"',
    r'\bquarryBlock\("([a-z0-9_]+)"',
    r'\bpowerCableBlock\("([a-z0-9_]+)"',
    r'\bbatteryBoxBlock\("([a-z0-9_]+)"',
    r'\bconveyorBeltBlock\("([a-z0-9_]+)"',
    r'\bmachineInterfaceBlock\("([a-z0-9_]+)"',
    r'\benergyGeneratorBlock\("([a-z0-9_]+)"',
    r'\bsettlementBoardBlock\("([a-z0-9_]+)"',
    r'\bsupportPartBlock\("([a-z0-9_]+)"',
    r'\bregisterSimpleBlock\("([a-z0-9_]+)"',
)


def registered() -> tuple[set[str], set[str]]:
    items: set[str] = set()
    blocks: set[str] = set()
    for path in JAVA.rglob("*.java"):
        text = path.read_text(encoding="utf-8", errors="replace")
        items |= set(re.findall(r'new Entry\("([a-z0-9_]+)"', text))
        items |= set(re.findall(r'ITEMS\.registerItem\(\s*"([a-z0-9_]+)"', text))
        items |= set(re.findall(r'\bitem\("([a-z0-9_]+)"\)', text))
        for pattern in BLOCK_PATTERNS:
            blocks |= set(re.findall(pattern, text))
    return items - blocks, blocks


def curated_block() -> str:
    src = SCRIPT.read_text(encoding="utf-8")
    return src[src.find("ITEM_SUBJECTS = {"): src.find("BLOCK_SUBJECTS")]


ASSETS = ROOT / "neoforge-26.2/src/main/resources/assets/earth_on_minecraft"
NS = "earth_on_minecraft"


def _load(path: pathlib.Path) -> dict | None:
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"))
    except json.JSONDecodeError:
        return None


def _walk_textures(model_id: str, seen: set[str]) -> set[str] | None:
    """Follow `parent` links and collect every concrete texture id. None means a broken chain."""
    if model_id in seen:
        return set()
    seen.add(model_id)
    namespace, _, name = model_id.partition(":")
    if not name:
        namespace, name = "minecraft", model_id
    if namespace != NS:
        return set()
    data = _load(ASSETS / "models" / f"{name}.json")
    if data is None:
        return None
    found: set[str] = set()
    for value in (data.get("textures") or {}).values():
        if isinstance(value, str) and not value.startswith("#"):
            found.add(value)
    parent = data.get("parent")
    if isinstance(parent, str) and parent and not parent.startswith("minecraft:"):
        inherited = _walk_textures(parent, seen)
        if inherited is None:
            return None
        found |= inherited
    return found


def _texture_on_disk(texture_id: str) -> bool:
    namespace, _, name = texture_id.partition(":")
    if not name:
        namespace, name = NS, texture_id
    if namespace != NS:
        return True
    return (ASSETS / "textures" / f"{name}.png").exists()


def resolves_to_texture(name: str, kind: str) -> bool:
    """True when the model chain ends in textures that all exist on disk."""
    if kind == "item":
        definition = _load(ASSETS / "items" / f"{name}.json")
        model_ids: list[str] = []
        if definition is not None:
            model = definition.get("model")
            if isinstance(model, dict) and isinstance(model.get("model"), str):
                model_ids.append(model["model"])
        if not model_ids:
            model_ids.append(f"{NS}:item/{name}")
    else:
        state = _load(ASSETS / "blockstates" / f"{name}.json")
        if state is None:
            return False
        model_ids = []
        for variant in (state.get("variants") or {}).values():
            for item in (variant if isinstance(variant, list) else [variant]):
                if isinstance(item, dict) and isinstance(item.get("model"), str):
                    model_ids.append(item["model"])
        for part in (state.get("multipart") or []):
            apply = part.get("apply")
            for item in (apply if isinstance(apply, list) else [apply]):
                if isinstance(item, dict) and isinstance(item.get("model"), str):
                    model_ids.append(item["model"])
        if not model_ids:
            return False

    for model_id in model_ids:
        textures = _walk_textures(model_id, set())
        if textures is None:
            return False
        if any(not _texture_on_disk(t) for t in textures):
            return False
    return True


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true",
                        help="rewrite docs/quality/art-pending.json (destructive snapshot)")
    args = parser.parse_args()

    src = SCRIPT.read_text(encoding="utf-8")
    curated_items = set(re.findall(r'^    "([a-z0-9_]+)":', curated_block(), re.M))
    block_block = src[src.find("BLOCK_SUBJECTS = {"):]
    curated_blocks = set(re.findall(r'^    "([a-z0-9_]+)":', block_block, re.M))

    registered_items, registered_blocks = registered()

    spec_items = {}
    for line in (SPEC / "items.jsonl").read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        record = json.loads(line)
        key = record["id"].split(":", 1)[1]
        spec_items[key] = {
            "en": record.get("en"),
            "zh": record.get("zh"),
            "family": record.get("family"),
            "kind": record.get("kind"),
            "note": (record.get("note") or "")[:300],
        }

    spec_blocks = {}
    for line in (SPEC / "blocks.jsonl").read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        record = json.loads(line)
        key = record["id"].split(":", 1)[1]
        spec_blocks[key] = {
            "en": record.get("en"),
            "zh": record.get("zh"),
            "family": record.get("family"),
            "kind": record.get("kind"),
        }

    def entry(name: str, info: dict) -> dict:
        return {
            "id": name,
            "en": info.get("en"),
            "zh": info.get("zh"),
            "family": info.get("family"),
            "kind": info.get("kind"),
            "note": info.get("note"),
        }

    item_pending = []
    for path in sorted(ITEM_TEX.glob("*.png")):
        if path.stat().st_size > TEMPLATE_MAX_BYTES:
            continue
        name = path.stem
        if name in curated_items:
            continue
        item_pending.append(entry(name, spec_items.get(name, {})))

    block_pending = []
    for path in sorted(BLOCK_TEX.glob("*.png")):
        if path.stat().st_size > TEMPLATE_MAX_BYTES:
            continue
        name = path.stem
        if name in curated_blocks:
            continue
        block_pending.append(entry(name, spec_blocks.get(name, {})))

    # A registered id with no texture at all is the worst case: it renders as the missing-texture
    # checkerboard. The old script could not see this because it only iterated existing files.
    #
    # Both lists have to resolve the model chain first: a block or item may legitimately reference
    # a texture whose name differs from its id (machine fronts, multi-face blocks, shared icons).
    # Checking for `textures/<kind>/<id>.png` produced false positives for those.
    item_missing = [
        entry(name, spec_items.get(name, {}))
        for name in sorted(registered_items)
        if not resolves_to_texture(name, "item")
    ]
    block_missing = [
        entry(name, spec_blocks.get(name, {}))
        for name in sorted(registered_blocks)
        if not resolves_to_texture(name, "block")
    ]

    print(f"curated items {len(curated_items)} blocks {len(curated_blocks)}")
    print(f"item template art {len(item_pending)}   item missing texture {len(item_missing)}")
    print(f"block template art {len(block_pending)}  block missing texture {len(block_missing)}")
    for entry_ in item_missing[:12]:
        print("  MISSING", entry_["id"], "|", entry_.get("en"))
    for entry_ in block_missing[:12]:
        print("  MISSING", entry_["id"], "|", entry_.get("en"))

    if args.write:
        OUT.write_text(json.dumps({
            "schema_version": 2,
            "template_threshold_bytes": TEMPLATE_MAX_BYTES,
            "curated_items": len(curated_items),
            "curated_blocks": len(curated_blocks),
            "item_pending": item_pending,
            "block_pending": block_pending,
            "item_missing_texture": item_missing,
            "block_missing_texture": block_missing,
        }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("wrote", OUT)
    else:
        print("dry run; pass --write to refresh", OUT.name)
    for entry_ in item_pending[:12]:
        print(" ", entry_["id"], "|", entry_.get("en"), "|", entry_.get("family"))


if __name__ == "__main__":
    main()
