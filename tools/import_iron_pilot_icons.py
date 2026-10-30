#!/usr/bin/env python3
"""Import the approved 16x16 iron pilot drafts as inventory-scale item textures."""

from __future__ import annotations

import argparse
import pathlib

from PIL import Image


ROOT = pathlib.Path(__file__).resolve().parents[1]
TEXTURES = ROOT / "neoforge-26.2" / "src" / "main" / "resources" / "assets" / "earth_on_minecraft" / "textures" / "item"
TARGET_SIZE = 128


def import_icon(source: pathlib.Path, item_id: str) -> None:
    with Image.open(source) as draft:
        if draft.size != (16, 16):
            raise ValueError(f"{source} must be a 16x16 pilot draft, got {draft.width}x{draft.height}")
        if draft.mode != "RGBA":
            raise ValueError(f"{source} must preserve RGBA transparency, got {draft.mode}")
        texture = draft.resize((TARGET_SIZE, TARGET_SIZE), Image.Resampling.NEAREST)
        destination = TEXTURES / f"{item_id}.png"
        texture.save(destination, format="PNG", optimize=True)
        print(f"IMPORTED {item_id}: {source} -> {destination}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sponge", required=True, type=pathlib.Path, help="approved sponge_iron 16x16 draft PNG")
    parser.add_argument("--wrought", required=True, type=pathlib.Path, help="approved wrought_iron 16x16 draft PNG")
    args = parser.parse_args()
    import_icon(args.sponge, "sponge_iron")
    import_icon(args.wrought, "wrought_iron")


if __name__ == "__main__":
    main()
