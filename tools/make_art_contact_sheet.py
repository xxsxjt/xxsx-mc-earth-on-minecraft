"""Build a contact sheet of every icon produced by the Gemini art pipeline."""

from __future__ import annotations

import json
import pathlib

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[1]
TEX = ROOT / "neoforge-26.2/src/main/resources/assets/earth_on_minecraft/textures/item"
MANIFEST = ROOT / "docs/quality/gemini-art-manifest.json"
OUT = ROOT / "output/quality/gemini-art-contact-sheet.png"

CELL = 128
PAD = 8
LABEL = 16
COLS = 8


def main() -> None:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    recorded = {record["id"] for record in manifest["records"]}
    # Every icon above template size was produced by the art pipeline, whether or not the run
    # that made it survived in the manifest (interrupted batches used to skip the write).
    ids = [
        path.stem for path in TEX.glob("*.png")
        if path.stat().st_size > 1200
    ]
    ids.sort(key=lambda name: (name not in recorded, -(TEX / f"{name}.png").stat().st_mtime))
    ids = [name for name in ids if (TEX / f"{name}.png").exists()]

    rows = (len(ids) + COLS - 1) // COLS
    width = COLS * (CELL + PAD) + PAD
    height = rows * (CELL + LABEL + PAD) + PAD
    sheet = Image.new("RGBA", (width, height), (28, 32, 36, 255))
    draw = ImageDraw.Draw(sheet)

    for index, name in enumerate(ids):
        column = index % COLS
        row = index // COLS
        x = PAD + column * (CELL + PAD)
        y = PAD + row * (CELL + LABEL + PAD)
        draw.rectangle((x, y, x + CELL, y + CELL), fill=(48, 54, 60, 255))
        with Image.open(TEX / f"{name}.png") as icon:
            icon = icon.convert("RGBA").resize((CELL, CELL), Image.NEAREST)
            sheet.alpha_composite(icon, (x, y))
        draw.text((x + 2, y + CELL + 2), name[:20], fill=(226, 232, 238, 255))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print(f"contact sheet: {OUT} ({len(ids)} icons)")


if __name__ == "__main__":
    main()
