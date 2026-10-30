#!/usr/bin/env python3
"""Emit drink production routes for the 68 drinks the spec left as `family:drink`.

The catalog columns already name the raw material for 25 of them (baijiu = sorghum/wheat,
beer = barley, wine = grapes, rum = cane molasses, ...). The rest are derived from their section:
water and juice drinks come from a press, teas from the tea kiln, coffee drinks from the roaster,
fermented drinks from the fermenting crock, distilled spirits from the still, and cocktails from
the bar counter.

Output is a JSON route table consumed by `SpecDrinkRecipes`, mirroring how machine routes are
generated, so the data stays reviewable instead of being buried in Java.
"""

from __future__ import annotations

import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = pathlib.Path(r"D:\Codex\WORKSPACES\Projects\mc-agent\spec\eom")
OUT = ROOT / "neoforge-26.2/src/main/resources/data/earth_on_minecraft/earth/quality/drink_routes.json"

# Fermentable base: what each fermented drink is made from, and what it becomes when distilled.
# `material` is the spec's own catalog wording where it exists.
FERMENTED = {
    "baijiu": ("sorghum", 4),
    "beer": ("barley", 4),
    "lager": ("barley", 4),
    "stout": ("barley", 4),
    "wheat_beer": ("wheat", 4),
    "wine": ("grape", 5),
    "cherry_wine": ("coffee_cherry", 5),
    "plum_wine": ("smoked_plum", 5),
    "cider": ("apple", 5),
    "perry": ("pear", 5),
    "mead": ("honey", 3),
    "kumis": ("milk", 4),
    "kvass": ("rye_bread", 3),
    "huangjiu": ("glutinous_rice", 4),
    "sake": ("rice", 4),
    "rice_wine": ("rice", 4),
    "palm_wine": ("coconut_water", 4),
}

# Distilled spirits: (fermented source or raw material, units per bottle).
DISTILLED = {
    "brandy": ("wine", 2),
    "gin": ("wheat", 3),
    "grappa": ("grape", 4),
    "plum_brandy": ("plum_wine", 2),
    "rum": ("sugar", 3),
    "tequila": ("sorghum", 4),
    "vodka": ("potato", 4),
    "whisky": ("barley", 3),
}

# Teas share one kiln path from fresh leaf.
TEAS = {
    "black_tea": 2,
    "dark_tea": 2,
    "green_tea": 1,
    "oolong_tea": 2,
    "pu_er_tea": 3,
    "white_tea": 1,
    "yellow_tea": 2,
    "scented_tea": 2,
    "matcha": 3,
}

# Coffee drinks: roast -> grind -> brew. `bean` is how many roasted beans a cup needs.
COFFEE = {
    "coffee": 2,
    "americano": 2,
    "espresso": 3,
    "latte": 2,
    "cappuccino": 2,
    "cold_brew": 3,
}

# Pressed juices and plant milks.
PRESSED = {
    "juice": ("apple", 2),
    "tomato_juice": ("tomato", 3),
    "vegetable_juice": ("carrot", 3),
    "lemonade": ("lemon", 2),
    "coconut_water": ("coconut", 1),
    "almond_milk": ("soybean", 3),
    "oat_milk": ("oat", 3),
    "rice_milk": ("rice", 3),
    "soy_milk": ("soybean", 3),
}

# Water and soda.
WATERS = {
    "water_bottle": 0,
    "mineral_water": 0,
    "sparkling_water": 0,
    "soda_water": 0,
    "tonic_water": 0,
    "deionized_water": 0,
    "distilled_water": 0,
    "ginger_ale": 2,
    "hot_chocolate": 2,
    "drinking_yogurt": 2,
    "butter_tea": 2,
    "milk_tea": 2,
}

# Cocoa chain.
COCOA = {
    "cocoa_bean": ("cocoa", 1),
    "cocoa_powder": ("cocoa_bean", 2),
    "cocoa": ("cocoa_powder", 2),
}

# Coffee intermediates.
COFFEE_CHAIN = {
    "roasted_coffee_bean": ("coffee_bean", 1),
    "coffee_grounds": ("roasted_coffee_bean", 1),
}

# Cocktails: built at the bar counter from a base plus modifiers.
COCKTAILS = {
    "bloody_mary": ("vodka", "tomato_juice"),
    "daiquiri": ("rum", "lemonade"),
    "eggnog": ("rum", "milk"),
    "gin_tonic": ("gin", "tonic_water"),
    "highball": ("whisky", "soda_water"),
    "margarita": ("tequila", "lemonade"),
    "martini": ("gin", "vermouth"),
    "mint_julep": ("whisky", "mint"),
    "mojito": ("rum", "mint"),
    "mulled_wine": ("wine", "cinnamon"),
    "old_fashioned": ("whisky", "sugar"),
    "paloma": ("tequila", "bitter_orange"),
    "sidecar": ("brandy", "lemonade"),
    "white_russian": ("vodka", "milk"),
    "mocktail": ("juice", "soda_water"),
}

# Liqueurs and aromatised drinks.
LIQUEURS = {
    "coffee_liqueur": ("vodka", "coffee"),
    "orange_liqueur": ("brandy", "bitter_orange"),
    "bitters": ("vodka", "bitter_orange"),
    "vermouth": ("wine", "mint"),
}

# A few drinks whose raw material is itself a mod item with a different id.
ALIASES = {
    "soy_milk": "soybean",
    "rye_bread": "rye_bread",
}


def item_ref(name: str) -> str:
    """Resolve a short material name to a registry id."""
    if name in ALIASES:
        name = ALIASES[name]
    # Only names that genuinely exist in minecraft: must be listed here.
    vanilla = {"apple", "barley", "wheat", "sugar", "potato", "carrot", "lemon",
               "honey", "milk", "cocoa", "oat", "rice", "pear", "grape"}
    if name in vanilla:
        return f"minecraft:{name}"
    return f"earth_on_minecraft:{name}"


def main() -> None:
    routes: list[dict] = []

    def add(recipe_id: str, machine: str, inputs: list[tuple[str, int]],
            output: str, count: int, ticks: int, energy: int, note: str) -> None:
        routes.append({
            "id": recipe_id,
            "machine": machine,
            "inputs": [{"item": item_ref(name), "count": amount} for name, amount in inputs],
            "outputs": [{"item": item_ref(output), "count": count}],
            "ticks": ticks,
            "energy": energy,
            "note": note,
        })

    # Intermediates first so the chain reads bottom-up.
    for drink, (source, amount) in COFFEE_CHAIN.items():
        add(f"drink/{drink}", "coffee_roaster", [(source, amount)], drink, 1, 120, 8,
            "roast and grind the bean")

    for drink, (source, amount) in COCOA.items():
        add(f"drink/{drink}", "mixer", [(source, amount)], drink, 1, 80, 6,
            "press and refine the cocoa")

    for drink, amount in TEAS.items():
        add(f"drink/{drink}", "tea_kiln", [("tea_fresh_leaf", amount)], drink, 1, 100, 4,
            "fix the leaf in the tea kiln")

    for drink, (source, amount) in PRESSED.items():
        add(f"drink/{drink}", "filter_press", [(source, amount)], drink, 1, 60, 6,
            "press the juice out")

    for drink in WATERS:
        if drink in ("distilled_water", "deionized_water"):
            continue  # already produced by the chemical chain
        add(f"drink/{drink}", "water_treatment_unit", [("softened_water", 1)], drink, 1, 40, 4,
            "treat and carbonate the water")

    for drink, (source, amount) in FERMENTED.items():
        add(f"drink/{drink}", "fermenting_crock", [(source, amount)], drink, 1, 600, 0,
            "ferment in the crock")

    for drink, (source, amount) in DISTILLED.items():
        add(f"drink/{drink}", "still", [(source, amount)], drink, 1, 240, 0,
            "distill in the still")

    for drink, (base, modifier) in COCKTAILS.items():
        add(f"drink/{drink}", "bar_counter", [(base, 1), (modifier, 1)], drink, 1, 40, 0,
            "build at the bar")

    for drink, (base, modifier) in LIQUEURS.items():
        add(f"drink/{drink}", "bar_counter", [(base, 2), (modifier, 1)], drink, 1, 80, 0,
            "infuse at the bar")

    for drink, amount in COFFEE.items():
        add(f"drink/{drink}", "coffee_roaster",
            [("coffee_grounds", max(1, amount))], drink, 1, 100, 6,
            "brew the ground coffee")

    routes.sort(key=lambda r: (r["machine"], r["id"]))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({
        "schema_version": 1,
        "source": str(SPEC),
        "routes": routes,
        "counts": {
            "total": len(routes),
            "machines": sorted({r["machine"] for r in routes}),
        },
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"DRINK_ROUTES_OK routes={len(routes)}")
    from collections import Counter
    for machine, count in Counter(r["machine"] for r in routes).most_common():
        print(f"  {machine:24} {count}")


if __name__ == "__main__":
    main()
