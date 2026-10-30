#!/usr/bin/env python3
"""Render a crisp inventory-scale geology hammer from a small pixel grid."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "neoforge-26.2/src/main/resources/assets/earth_on_minecraft/textures/item/geology_hammer.png"


def main() -> None:
    icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pixels = ImageDraw.Draw(icon)
    outline = (35, 39, 41, 255)
    iron_shadow = (71, 78, 81, 255)
    iron_mid = (119, 128, 131, 255)
    iron_light = (174, 181, 181, 255)
    wood_shadow = (84, 54, 32, 255)
    wood_mid = (142, 96, 53, 255)
    wood_light = (190, 143, 82, 255)

    # Diagonal handle, from lower-left to upper-right.
    pixels.polygon([(2, 12), (4, 10), (10, 4), (12, 6), (6, 12), (4, 14)], fill=outline)
    pixels.polygon([(3, 12), (5, 10), (10, 5), (11, 6), (5, 12), (4, 13)], fill=wood_mid)
    pixels.line([(4, 12), (10, 6)], fill=wood_light, width=1)
    pixels.point((4, 13), fill=wood_shadow)

    # Compact steel head with a flat striking face and short pick nose.
    pixels.polygon([(5, 2), (11, 1), (14, 3), (13, 5), (10, 6), (7, 5), (4, 4)], fill=outline)
    pixels.polygon([(6, 2), (11, 2), (13, 3), (12, 4), (9, 5), (6, 4), (5, 3)], fill=iron_mid)
    pixels.polygon([(6, 2), (11, 2), (12, 3), (8, 3), (6, 4), (5, 3)], fill=iron_light)
    pixels.point((13, 3), fill=iron_shadow)
    pixels.line([(7, 5), (9, 6)], fill=iron_shadow, width=1)

    icon.resize((128, 128), Image.Resampling.NEAREST).save(OUTPUT, format="PNG", optimize=True)
    print(f"RENDERED {OUTPUT}")


if __name__ == "__main__":
    main()
