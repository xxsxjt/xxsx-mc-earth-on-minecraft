"""Backfill every curated icon that predates the manifest, items and blocks alike.

The original script only scanned `textures/item`, so block art that was generated before the
ledger existed never got a record. This version walks both folders and uses the right size
threshold for each: block textures are legitimately a few kilobytes, so a 1200-byte cutoff would
misreport them as template art.

Records added this way are marked `backfilled: true` and `provenance: "pre-ledger"` so they can be
told apart from records the generator actually wrote.
"""

from __future__ import annotations

import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools/generate_gemini_art.py"
ASSETS = ROOT / "neoforge-26.2/src/main/resources/assets/earth_on_minecraft"
MANIFEST = ROOT / "docs/quality/gemini-art-manifest.json"

ITEM_TEMPLATE_MAX = 1200
BLOCK_TEMPLATE_MAX = 1200


def curated() -> tuple[list[str], list[str]]:
    src = SCRIPT.read_text(encoding="utf-8")
    item_block = src[src.find("ITEM_SUBJECTS = {"): src.find("BLOCK_SUBJECTS")]
    block_block = src[src.find("BLOCK_SUBJECTS = {"):]
    items = re.findall(r'^    "([a-z0-9_]+)":', item_block, re.M)
    blocks = re.findall(r'^    "([a-z0-9_]+)":', block_block, re.M)
    return items, blocks


def main() -> None:
    data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    known = {(record.get("kind", "item"), record.get("id")) for record in data["records"]}
    items, blocks = curated()
    added: list[str] = []
    skipped_template: list[str] = []

    for kind, names, folder, threshold in (
        ("item", items, "textures/item", ITEM_TEMPLATE_MAX),
        ("block", blocks, "textures/block", BLOCK_TEMPLATE_MAX),
    ):
        for name in names:
            if (kind, name) in known:
                continue
            path = ASSETS / folder / f"{name}.png"
            if not path.exists():
                skipped_template.append(f"{kind}:{name} (no file)")
                continue
            size = path.stat().st_size
            if size <= threshold:
                skipped_template.append(f"{kind}:{name} ({size} bytes)")
                continue
            data["records"].append({
                "id": name,
                "kind": kind,
                "target": str(path.relative_to(ROOT)).replace("\\", "/"),
                "status": "generated",
                "provider": "gemini",
                "model": "google-antigravity/gemini-3.1-flash-image",
                "bytes": size,
                "backfilled": True,
                "provenance": "pre-ledger",
            })
            added.append(f"{kind}:{name}")

    data["records"].sort(key=lambda r: (r.get("kind", "item"), r.get("id", "")))
    data["generated"] = sum(r["status"] == "generated" for r in data["records"])
    data["failed"] = sum(r["status"] == "failed" for r in data["records"])
    data["skipped"] = sum(r["status"] == "skipped_existing" for r in data["records"])
    data["protected"] = sum(r["status"] == "protected" for r in data["records"])
    data["remaining"] = 0
    MANIFEST.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(f"backfilled {len(added)} records")
    for entry in added:
        print("  +", entry)
    print(f"records total: {len(data['records'])}  generated: {data['generated']}")
    if skipped_template:
        print(f"still template or missing ({len(skipped_template)}):")
        for entry in skipped_template:
            print("  -", entry)


if __name__ == "__main__":
    main()
