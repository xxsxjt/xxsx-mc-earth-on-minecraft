"""Contact sheet of only the curated icons that the Gemini pipeline produced."""

from __future__ import annotations

import pathlib
import re

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[1]
TEX = ROOT / "neoforge-26.2/src/main/resources/assets/earth_on_minecraft/textures/item"
SCRIPT = ROOT / "tools/generate_gemini_art.py"
OUT = ROOT / "output/quality/gemini-art-only.png"

CELL = 128
PAD = 8
LABEL = 16
COLS = 8


def curated() -> list[str]:
    src = SCRIPT.read_text(encoding="utf-8")
    block = src[src.find("ITEM_SUBJECTS = {"): src.find("BLOCK_SUBJECTS")]
    return re.findall(r'^    "([a-z0-9_]+)":', block, re.M)


def main() -> None:
    names = []
    for name in curated():
        path = TEX / f"{name}.png"
        if path.exists() and path.stat().st_size > 1200:
            names.append(name)
    names.sort(key=lambda n: -(TEX / f"{n}.png").stat().st_mtime)

    rows = (len(names) + COLS - 1) // COLS
    sheet = Image.new("RGBA", (COLS * (CELL + PAD) + PAD, rows * (CELL + LABEL + PAD) + PAD),
                      (28, 32, 36, 255))
    draw = ImageDraw.Draw(sheet)
    for index, name in enumerate(names):
        column, row = index % COLS, index // COLS
        x = PAD + column * (CELL + PAD)
        y = PAD + row * (CELL + LABEL + PAD)
        draw.rectangle((x, y, x + CELL, y + CELL), fill=(48, 54, 60, 255))
        with Image.open(TEX / f"{name}.png") as icon:
            sheet.alpha_composite(icon.convert("RGBA").resize((CELL, CELL), Image.NEAREST), (x, y))
        draw.text((x + 2, y + CELL + 2), name[:20], fill=(226, 232, 238, 255))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print(f"{OUT} ({len(names)} icons)")


if __name__ == "__main__":
    main()
