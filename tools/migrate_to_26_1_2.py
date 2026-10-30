"""Rewrite 26.2-only APIs to their 26.1.2 equivalents.

26.1.2 predates the ColorCollection refactor, so `Items.DYE.white()` becomes `Items.WHITE_DYE`
and `Blocks.WOOL.white()` becomes `Blocks.WHITE_WOOL`. The screen-layer API is also newer than
26.1.2, which only exposes `Minecraft.setScreen`.
"""

from __future__ import annotations

import pathlib
import re

ROOT = pathlib.Path("neoforge-26.2/src/main/java/com/xxsx/earthonminecraft")

DYE = {
    "white": "WHITE_DYE",
    "orange": "ORANGE_DYE",
    "magenta": "MAGENTA_DYE",
    "light_blue": "LIGHT_BLUE_DYE",
    "yellow": "YELLOW_DYE",
    "lime": "LIME_DYE",
    "pink": "PINK_DYE",
    "gray": "GRAY_DYE",
    "light_gray": "LIGHT_GRAY_DYE",
    "cyan": "CYAN_DYE",
    "purple": "PURPLE_DYE",
    "blue": "BLUE_DYE",
    "brown": "BROWN_DYE",
    "green": "GREEN_DYE",
    "red": "RED_DYE",
    "black": "BLACK_DYE",
}

WOOL = {
    "white": "WHITE_WOOL",
    "orange": "ORANGE_WOOL",
    "magenta": "MAGENTA_WOOL",
    "light_blue": "LIGHT_BLUE_WOOL",
    "yellow": "YELLOW_WOOL",
    "lime": "LIME_WOOL",
    "pink": "PINK_WOOL",
    "gray": "GRAY_WOOL",
    "light_gray": "LIGHT_GRAY_WOOL",
    "cyan": "CYAN_WOOL",
    "purple": "PURPLE_WOOL",
    "blue": "BLUE_WOOL",
    "brown": "BROWN_WOOL",
    "green": "GREEN_WOOL",
    "red": "RED_WOOL",
    "black": "BLACK_WOOL",
}


def rewrite(text: str) -> tuple[str, list[str]]:
    notes: list[str] = []

    for colour, field in DYE.items():
        pattern = rf"Items\.DYE\.{colour}\(\)"
        if re.search(pattern, text):
            text = re.sub(pattern, f"Items.{field}", text)
            notes.append(f"Items.DYE.{colour}() -> Items.{field}")

    for colour, field in WOOL.items():
        pattern = rf"Blocks\.WOOL\.{colour}\(\)"
        if re.search(pattern, text):
            text = re.sub(pattern, f"Blocks.{field}", text)
            notes.append(f"Blocks.WOOL.{colour}() -> Blocks.{field}")

    if "gui.setScreen(" in text:
        text = text.replace("Minecraft.getInstance().gui.setScreen(", "Minecraft.getInstance().setScreen(")
        text = text.replace("this.minecraft.gui.setScreen(", "this.minecraft.setScreen(")
        notes.append("gui.setScreen -> setScreen")

    if "gui.pushScreenLayer(" in text:
        text = re.sub(r"Minecraft\.getInstance\(\)\.gui\.pushScreenLayer\(", "Minecraft.getInstance().setScreen(", text)
        notes.append("gui.pushScreenLayer -> setScreen")

    if "gui.popScreenLayer()" in text:
        text = re.sub(r"Minecraft\.getInstance\(\)\.gui\.popScreenLayer\(\)", "Minecraft.getInstance().setScreen(null)", text)
        notes.append("gui.popScreenLayer -> setScreen(null)")

    return text, notes


def main() -> None:
    changed_files = 0
    for path in sorted(ROOT.rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        updated, notes = rewrite(text)
        if updated != text:
            path.write_text(updated, encoding="utf-8")
            changed_files += 1
            print(f"{path.relative_to(ROOT)}")
            for note in dict.fromkeys(notes):
                print(f"  {note}")
    print(f"rewrote {changed_files} files")


if __name__ == "__main__":
    main()
