#!/usr/bin/env python3
"""Generate item and block art through the local relay's image models.

Provider order:

1. `google-antigravity/gemini-3.1-flash-image` - preferred; returns a markdown link to a
   relay-hosted JPEG, so it needs the magenta chroma-key pass.
2. `AIGCPro-image/gpt-image-2.5-sunburst` - fallback when Gemini's quota is exhausted; returns
   an inline `data:image/png;base64,...` with a real alpha channel.
3. `AIGCPro-image/gpt-image-2.5-flare` - second fallback, same shape as sunburst.

The relay only exposes these through the chat completions surface, not
`/v1/images/generations`.

Every prompt still asks for a flat #FF00FF background so the Gemini JPEG path can be keyed;
the gpt-image providers ignore that and return transparency directly.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import pathlib
import re
import subprocess
import tempfile
import sys
import time
import urllib.error
import urllib.request

from contextlib import contextmanager

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / "neoforge-26.2/src/main/resources/assets/earth_on_minecraft"
ITEM_TEXTURES = ASSETS / "textures/item"
BLOCK_TEXTURES = ASSETS / "textures/block"
SPEC = pathlib.Path(r"D:\Codex\WORKSPACES\Projects\mc-agent\spec\eom")
TMP = ROOT / "tmp/imagegen/gemini-art"
MANIFEST = ROOT / "docs/quality/gemini-art-manifest.json"
AUTH = pathlib.Path(r"C:\Users\xxsx\.codex\auth.json")
REMOVER = pathlib.Path(r"C:\Users\xxsx\.codex\skills\.system\imagegen\scripts\remove_chroma_key.py")

BASE_URL = "http://127.0.0.1:10100/v1"
MODEL = "google-antigravity/gemini-3.1-flash-image"

# Provider chain, tried in order. Gemini is preferred for style consistency; the AIGCPro
# gpt-image models are the fallback when Gemini's Antigravity quota is exhausted.
# `format` describes how the reply carries the image:
#   "artifact" - markdown link to a relay-hosted JPEG, needs chroma-key removal
#   "base64"   - inline `data:image/png;base64,...`, already has an alpha channel
PROVIDERS = (
    {"id": "gemini", "model": "google-antigravity/gemini-3.1-flash-image", "format": "artifact"},
    {"id": "gpt-image-sunburst", "model": "AIGCPro-image/gpt-image-2.5-sunburst", "format": "base64"},
    {"id": "gpt-image-flare", "model": "AIGCPro-image/gpt-image-2.5-flare", "format": "base64"},
)
ACTIVE_PROVIDER = 0
FINAL_SIZE = 128
CHROMA = "#FF00FF"

# Approved iron-pilot icons imported from the 16x16 drafts; never overwrite them.
PROTECTED = frozenset({"sponge_iron", "wrought_iron"})
# Textures at or above this size were produced by the earlier art pipeline, not by the template
# generator, so the default batch leaves them alone unless --force is passed.
TEMPLATE_MAX_BYTES = 1200

STYLE = (
    "Style: crisp pixel art for a Minecraft mod icon, 16-bit game style, thick dark outline, "
    "flat cel shading with a small amount of dithering, no photorealism, no 3D render, no text, "
    "no watermark, no border frame."
)
BACKGROUND = (
    "Background: a perfectly flat uniform solid magenta " + CHROMA + " chroma-key fill covering the "
    "whole canvas, with no gradient, no shadow, no glow, no vignette and no ground plane."
)

ITEM_SUBJECTS = {
    "magnetite_chunk": "a chunk of magnetite ore, dark metallic black octahedral crystals in a gray rock matrix",
    "chalcopyrite_chunk": "a chunk of chalcopyrite ore, brassy golden sulfide crystals in gray host rock",
    "auriferous_quartz_chunk": "a chunk of gold-bearing quartz, milky white quartz with tiny bright gold specks",
    "kimberlite_chunk": "a chunk of kimberlite, green-gray volcanic breccia with dark olive nodules",
    "cinnabar_chunk": "a chunk of cinnabar ore, vivid red mercury sulfide crystals in pale rock",
    "tailings_dust": "a small heap of gray-green mine tailings dust in a shallow paper packet",
    "magnetite_dust": "a small heap of black magnetic iron oxide powder in a shallow paper packet",
    "hematite_dust": "a small heap of brick-red iron oxide powder in a shallow paper packet",
    "pyrite_dust": "a small heap of brassy yellow sulfide powder in a shallow paper packet",
    "quartz_dust": "a small heap of white quartz powder in a shallow paper packet",
    "calcite_dust": "a small heap of chalky white calcite powder in a shallow paper packet",
    "gypsum_dust": "a small heap of pale beige gypsum powder in a shallow paper packet",
    "salt_dust": "a small heap of coarse white salt crystals in a shallow paper packet",
    "coal_dust": "a small heap of glossy black coal powder in a shallow paper packet",
    "graphite_dust": "a small heap of dark gray graphite powder with a metallic sheen in a shallow paper packet",
    "sulfur_dust": "a small heap of bright yellow sulfur powder in a shallow paper packet",
    "silica_dust": "a small heap of fine pale silica sand in a shallow paper packet",
    "kaolin_dust": "a small heap of soft white kaolin clay powder in a shallow paper packet",
    "bauxite_dust": "a small heap of reddish brown bauxite powder in a shallow paper packet",
    "phosphate_rock_dust": "a small heap of gray-beige phosphate rock powder in a shallow paper packet",
    "bastnasite_dust": "a small heap of pale tan rare-earth carbonate powder in a shallow paper packet",
    "monazite_sand": "a small heap of amber-brown heavy mineral sand in a shallow paper packet",
    "uraninite_dust": "a small heap of near-black uranium oxide powder with a faint green sheen in a shallow paper packet",
    "titanium_dioxide": "a small heap of bright white titanium dioxide pigment powder in a shallow paper packet",
    "brine_crystal": "a cluster of translucent salt crystals with a faint blue tint",
    "potassium_chloride": "a cluster of white cubic potash salt crystals with a pink tint",
    "diamond_grit": "a small heap of fine pale blue diamond abrasive grit",
    "rare_earth_tailings": "a small heap of dull gray-brown rare earth processing residue",
    "slag": "a lump of dark porous metallurgical slag with glassy green edges",
    "sponge_iron": "a porous gray lump of sponge iron with hollow voids",
    "wrought_iron": "a small gray wrought iron bar with a fibrous hammered surface",
    "steel_bloom": "a stubby bloom of steel with bright hammered facets",
    "stainless_steel_bloom": "a bloom of stainless steel with a bright silvery sheen",
    "steel": "a clean steel ingot with crisp beveled edges",
    "iron_concentrate": "a small heap of dark iron ore concentrate pellets",
    "copper_busbar": "a short flat copper busbar with drilled terminal holes",
    "servo_motor": "a compact industrial servo motor with a copper winding window and a mounting flange",
    "transformer_core": "a laminated iron transformer core with copper windings",
    "grid_switchgear": "a compact industrial switchgear module with breakers and a busbar",
    "plc_controller": "a compact PLC controller with a dark display and terminal strips",
    "industrial_sensor": "a compact cylindrical industrial sensor with a threaded body and a status LED",
    "machine_vision_camera": "a compact machine vision camera with a lens barrel and a mounting bracket",
    "robotic_arm": "a short articulated robotic arm segment with servo joints and a gripper",
    "automation_bus": "a shielded industrial data and power bus module with connectors on both ends",
    "nuclear_heat_module": "a sealed cylindrical heat module with cooling fins and a radiation trefoil plate",
    "actuator_module": "a compact linear actuator module with a servo body and an extending rod",
    "coke": "a lump of porous black metallurgical coke",
    "charcoal": "a lump of black charcoal with a matte fractured surface",
    "slaked_lime": "a lump of soft white slaked lime powder",
    "cement": "a small heap of gray cement powder in a shallow paper packet",
    "concrete": "a small cast gray concrete block with sharp edges",
    "limestone": "a lump of pale limestone with fossil flecks",
    "millstone": "a round millstone disc with radial furrows and a central eye",
    "rope": "a coil of twisted natural fiber rope",
    "cotton_thread": "a spool of fine white cotton thread",
    "cotton_cloth": "a folded square of plain white cotton cloth",
    "linen": "a folded square of pale linen fabric with a visible weave",
    "silk_thread": "a spool of glossy pale silk thread",
    "leather": "a folded piece of tanned brown leather",
    "tannin": "a small heap of dark brown tannin powder in a shallow paper packet",
    "flour": "a small heap of fine white flour in a shallow paper packet",
    "dough": "a rounded ball of pale wheat dough",
    "rice": "a small heap of white rice grains in a shallow paper packet",
    "cooked_rice": "a small mound of steamed white rice",
    "coffee_bean": "a small heap of green unroasted coffee beans",
    "roasted_coffee_bean": "a small heap of dark roasted coffee beans with a glossy surface",
    "tea_leaf": "a small heap of dried green tea leaves",
    "salt": "a small heap of coarse white salt crystals in a shallow paper packet",
    "sugar": "a small heap of white sugar crystals in a shallow paper packet",
    "vinegar": "a glass bottle of pale amber vinegar with a cork stopper",
    "soy_sauce": "a glass bottle of dark soy sauce with a paper label",
    "wine": "a glass bottle of deep red wine with a cork stopper",
    "beer": "a glass bottle of amber beer with a crown cap",
    "ethanol": "a glass bottle of clear ethanol with a hazard label",
    "copper_wire": "a coil of bright copper wire",
    "activated_carbon": "a small heap of black activated carbon granules",
    "activated_carbon_filter": "a cylindrical activated carbon filter cartridge with mesh end caps",
    "hard_water_sample": "a glass vial of cloudy hard water with mineral sediment",
    "softened_water": "a glass vial of clear softened water",
    "paper": "a folded sheet of plain paper",
    "geology_hammer": "a geologist's rock hammer with a steel head and a wooden handle",
    "field_geology_notebook": "a weathered field notebook with a rock sample strapped to the cover",
    "abalone_shell": "an iridescent abalone shell with a pearly blue green inner surface and a ridged oval edge",
    "acetanilide": "a small heap of fine acetanilide chemical crystals in a shallow glass dish",
    "acetic_anhydride": "a small amber glass reagent bottle of clear acetic anhydride with a ground glass stopper",
    "acetic_anhydride_species": "a small clear glass reagent bottle containing colorless acetic anhydride species liquid with a narrow glass stopper",
    "acetylene_cell": "a small sealed steel gas cylinder containing acetylene, with a narrow colored glass sight window and a metal valve",
    "acetylsalicylic_acid": "a small amber medicine bottle containing acetylsalicylic acid liquid with a cork stopper",
    "aconite": "a bundle of dried aconite roots with twisted tan tubers and a few fibrous rootlets",
    "aconitum": "a bundle of dried aconitum roots with twisted tan tubers and a few fibrous rootlets",
    "activated_charcoal_tablet": "one small compressed pale activated charcoal medicine tablet with a shallow center score",
    "adzuki_bean": "a small heap of deep red adzuki bean grains and seeds in a shallow wooden bowl",
    "agarwood": "a short dark fragrant agarwood chip with visible resin streaks",
    "agrimony": "a small bundle of dried agrimony herb leaves and slender stems in muted olive and tan hues",
    "almond_milk": "a small ceramic cup of almond milk, with a smooth pale cream surface and a thick rounded rim",
    "altar": "a low stone ritual altar with a small bronze incense bowl and carved square base",
    "alum": "a cluster of clear angular alum crystals with a faint violet tint",
    "aluminium": "a compact beveled ingot of aluminium, with a pale silver sheen and crisp cast edges",
    "aluminium_alloy": "a compact beveled ingot of aluminium alloy, with a bright silver sheen and crisp cast edges",
    "aluminium_hydroxide": "a small amber pharmacy bottle containing a few pale aluminium hydroxide tablets, with an unmarked paper label",
    "aluminium_sulfate": "a small heap of white aluminium sulfate crystals with angular faces in a shallow glass dish",
    "amaranth": "a small heap of natural tan and cream amaranth grains and seeds in a shallow wooden bowl",
    "americano": "a small ceramic cup of americano, with a warm dark brown surface and a delicate rising curl of steam",
    "ammonia_cell": "a small sealed steel gas cylinder containing ammonia, with a narrow colored glass sight window and a metal valve",
    "ammonium_chloride": "a small heap of white ammonium chloride crystals with angular faces in a shallow glass dish",
    "amomum": "a small cluster of dried aromatic amomum seed pods with ribbed brown shells",
    "amphora": "a tall terracotta amphora with two loop handles, a narrow neck and a pointed base",
    "angelica": "a bundle of dried angelica roots with twisted tan tubers and a few fibrous rootlets",
    "angelica_pubescens": "a small bundle of dried angelica pubescens herb leaves and slender stems in muted olive and tan hues",
    "aniline": "a small sealed glass chemical bottle containing aniline liquid with a dark amber stopper",
    "animal_power": "a small wooden yoke and harness with leather straps for draft animal power",
    "anode_copper": "a short dark copper anode plate with a rough oxidized surface and a hanging tab",
    "antacid_tablet": "one small compressed pale antacid medicine tablet with a shallow center score",
    "antibiotic_ointment": "a small folded metal tube of antibiotic ointment with a screw cap and a narrow nozzle",
    "anticoagulant": "a small amber pharmacy bottle containing a few pale anticoagulant tablets, with an unmarked paper label",
    "anticonvulsant": "a small amber pharmacy bottle containing a few pale anticonvulsant tablets, with an unmarked paper label",
    "antidepressant": "a small amber pharmacy bottle containing a few pale antidepressant tablets, with an unmarked paper label",
    "antidiabetic": "a small amber pharmacy bottle containing a few pale antidiabetic tablets, with an unmarked paper label",
    "antifungal_agent": "a small amber pharmacy bottle containing a few pale antifungal agent tablets, with an unmarked paper label",
    "antihistamine": "a small amber pharmacy bottle containing a few pale antihistamine tablets, with an unmarked paper label",
    "antihypertensive": "a small amber pharmacy bottle containing a few pale antihypertensive tablets, with an unmarked paper label",
    "antipsychotic": "a small amber pharmacy bottle containing a few pale antipsychotic tablets, with an unmarked paper label",
    "antiseptic": "a small amber glass antiseptic bottle with clear liquid and a plain cork stopper",
    "antiseptic_ethanol": "a small amber medicine bottle containing antiseptic ethanol liquid with a cork stopper",
    "antivenom": "a small amber pharmacy bottle containing a few pale antivenom tablets, with an unmarked paper label",
    "antiviral_agent": "a small amber pharmacy bottle containing a few pale antiviral agent tablets, with an unmarked paper label",
    "apple": "a single fresh apple with red with a small green leaf and a clearly recognizable natural shape",
    "apricot_seed": "a small heap of dried apricot seeds with smooth firm shells in natural tan and brown tones",
    "apron": "a neatly folded apron made from sturdy woven fabric with visible seams and a clear wearable shape",
    "aquarium": "a small clear glass aquarium with blue water, a green aquatic plant and pale gravel",
    "argentite_dust": "a small heap of argentite powder with fine angular grains in a shallow paper packet",
    "arisaema": "a bundle of dried arisaema roots with twisted tan tubers and a few fibrous rootlets",
    "arrowhead": "a fresh arrowhead with a firm edible shape, visible natural skin texture and green leafy accents",
    "artemisia_annua": "a small bundle of dried artemisia annua herb leaves and slender stems in muted olive and tan hues",
    "artemisia_capillaris": "a small bundle of dried artemisia capillaris herb leaves and slender stems in muted olive and tan hues",
    "asparagus": "a fresh asparagus with a firm edible shape, visible natural skin texture and green leafy accents",
    "aspirin": "one small compressed pale aspirin medicine tablet with a shallow center score",
    "astragalus": "a bundle of dried astragalus roots with twisted tan tubers and a few fibrous rootlets",
    "atractylodes": "a bundle of dried atractylodes roots with twisted tan tubers and a few fibrous rootlets",
    "atractylodes_lancea": "a small bundle of dried atractylodes lancea herb leaves and slender stems in muted olive and tan hues",
    "automobile": "a compact vintage automobile with a rounded red body, black tires and brass headlamps",
    "avocado": "a single fresh avocado with deep green with a pale cut half and a clearly recognizable natural shape",
    "axe": "a sturdy axe with a dark steel working head and a smooth wooden handle",
    "backpack": "a small backpack made from woven brown fiber or leather with visible straps, handles or stitching",
    "bacon": "a compact serving of bacon with a clearly visible natural texture on a shallow ceramic dish",
    "baguette": "a small serving of baguette on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "baijiu": "a small clear drinking glass filled with baijiu, showing clear liquid and a clean rounded rim",
    "baked_potato": "a single recognizable serving of baked potato with natural ingredient colors and a clear appetizing silhouette on a small plate",
    "balance": "a small brass balance with a central beam, two shallow weighing pans and a sturdy base",
    "bamboo": "three fresh green bamboo stalks with segmented joints and narrow pointed leaves",
    "bamboo_hat": "a neatly folded bamboo hat made from sturdy woven fabric with visible seams and a clear wearable shape",
    "bamboo_leaf": "a fresh bamboo leaf with a firm edible shape, visible natural skin texture and green leafy accents",
    "bamboo_raft": "a small raft of lashed golden bamboo poles with a simple rope tie",
    "bamboo_shoot": "a single recognizable serving of bamboo shoot with natural ingredient colors and a clear appetizing silhouette on a small plate",
    "banana": "a single fresh banana with bright yellow with a short brown stem and a clearly recognizable natural shape",
    "bandage": "a neatly folded bandage made from sturdy woven fabric with visible seams and a clear wearable shape",
    "banknote": "a folded pale green paper banknote with fine ornamental lines and no readable text",
    "banner": "a folded crimson cloth banner hanging from a short dark wooden crossbar",
    "baozi": "a small serving of baozi on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "barley": "a small heap of natural tan and cream barley grains and seeds in a shallow wooden bowl",
    "barometer": "a compact brass and glass barometer with a marked measuring tube and a visible indicator",
    "barrel": "a compact wooden barrel with visible compartments, a sturdy rim and dark iron or brass fittings",
    "basket": "a small basket made from woven brown fiber or leather with visible straps, handles or stitching",
    "bathhouse": "a compact bathhouse built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "bathtub": "a compact bathtub built from aged copper and gray stone, with a clear functional opening and distinct structural details",
    "battle_axe": "a compact battle axe with a dark metal blade or striking head, a sturdy handle and a distinct guard",
    "bean_paste": "a small glass jar of bean paste with a clear view of its natural rich color and smooth texture",
    "bed": "a compact bed built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "beehive": "a compact golden straw beehive with a rounded woven body and a small entrance hole",
    "beer_glass": "a clear or pale glass beer glass with a thick rim, visible empty interior and a sturdy base",
    "beeswax": "a small block of warm yellow beeswax with a clean cut surface",
    "beet": "a fresh beet with a firm edible shape, visible natural skin texture and green leafy accents",
    "bell": "a small polished bronze hand bell with a curved handle and dark clapper",
    "bell_pepper": "a fresh bell pepper with a firm edible shape, visible natural skin texture and green leafy accents",
    "belt": "a neatly folded belt made from sturdy woven fabric with visible seams and a clear wearable shape",
    "bench": "a small wooden bench with clean joinery, warm brown grain and a compact recognizable silhouette",
    "benzoin": "a few pale amber benzoin resin tears in a shallow dish",
    "berry": "a single fresh berry with its recognizable natural ripe colors and a clearly recognizable natural shape",
    "betel_leaf": "a few dried heart shaped betel leaves with visible veins and muted olive green surfaces",
    "betel_nut": "a few smooth oval betel nuts with reddish brown skins in a shallow wooden bowl",
    "bicycle": "a sturdy black iron bicycle with two thin wheels, handlebars and a leather saddle",
    "billhook": "a sturdy billhook with a dark steel working head and a smooth wooden handle",
    "biota_leaf": "several dried biota leaves with visible veins and muted olive green edges",
    "biota_seed": "a small heap of dried biota seeds with smooth firm shells in natural tan and brown tones",
    "birdcage": "a compact birdcage built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "bitter_melon": "a fresh bitter melon with a firm edible shape, visible natural skin texture and green leafy accents",
    "bitter_orange": "a few dried bitter orange fruits with wrinkled skins and deep red brown natural colors",
    "bittern": "a small glass vial of concentrated bitter brine with pale salt crystals settling at the bottom",
    "bitters": "a small clear drinking glass filled with bitters, showing deep amber liquid and a clean rounded rim",
    "bitumen": "a lump of matte black bitumen with subtle tan aggregate specks",
    "black_bean": "a small heap of glossy black beans with smooth oval skins in a shallow wooden bowl",
    "black_concrete": "a solid charcoal black concrete cube with a smooth gritty face",
    "black_dye": "a small heap of fine black dye powder in a shallow dish",
    "black_tea": "a small ceramic cup of black tea, with a warm dark brown surface and a delicate rising curl of steam",
    "blackcurrant": "a single fresh blackcurrant with deep purple and a clearly recognizable natural shape",
    "blister_copper": "a rough porous lump of reddish blister copper with many tiny gas pits",
    "bloody_mary": "a clear cocktail glass filled with bloody mary, showing a distinct natural drink color and a small citrus garnish",
    "blowgun": "a compact dark metal blowgun with a taut string or narrow barrel and a clear hand grip",
    "blueberry": "a single fresh blueberry with dusty blue with a pale bloom and a clearly recognizable natural shape",
    "board_game": "a compact board game with its distinctive paper, carved or metal details and a clearly readable silhouette",
    "bolt": "a single piece of bolt showing its practical shape, natural material color and rough or worked surface",
    "bone": "a compact serving of bone with a clearly visible natural texture on a shallow ceramic dish",
    "bone_broth": "a single recognizable serving of bone broth with natural ingredient colors and a clear appetizing silhouette on a small plate",
    "bone_meal": "a small heap of pale ivory bone meal powder in a paper packet",
    "book": "a closed brown leather bound book with a thick cream page block and a plain cover",
    "bookshelf": "a compact wooden bookshelf with visible compartments, a sturdy rim and dark iron or brass fittings",
    "boots": "a neatly folded boots made from sturdy woven fabric with visible seams and a clear wearable shape",
    "borax_ore": "a rough cream borax ore fragment with chalky white crystals",
    "borax_species": "a small heap of white borax species crystals with angular faces in a shallow glass dish",
    "boric_acid": "a small amber medicine bottle containing boric acid liquid with a cork stopper",
    "borneol": "a small heap of pale translucent borneol crystals",
    "bow": "a compact dark metal bow with a taut string or narrow barrel and a clear hand grip",
    "bran": "a small heap of fine bran powder in a shallow paper packet with a few visible grains",
    "branding_iron": "a sturdy branding iron with a dark steel working head and a smooth wooden handle",
    "brandy": "a small clear drinking glass filled with brandy, showing warm amber liquid and a clean rounded rim",
    "brass": "a compact beveled ingot of brass, with a warm golden yellow sheen and crisp cast edges",
    "bread": "a small serving of bread on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "bread_basket": "a small bread basket made from woven brown fiber or leather with visible straps, handles or stitching",
    "brick": "a single red fired clay brick with rough edges and a porous orange surface",
    "bricks": "a short stack of red fired clay bricks with rough edges",
    "bridge": "a compact bridge built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "brine": "a small glass jar of brine with a clear view of its natural rich color and smooth texture",
    "bristle": "a compact bundle or folded swatch of bristle with visible fibers, grain or woven texture",
    "broad_bean": "a small heap of natural tan and cream broad bean grains and seeds in a shallow wooden bowl",
    "broadcloth": "a compact bundle or folded swatch of broadcloth with visible fibers, grain or woven texture",
    "broccoli": "a fresh broccoli with a firm edible shape, visible natural skin texture and green leafy accents",
    "bromine": "a small sealed steel gas cylinder containing bromine, with a narrow colored glass sight window and a metal valve",
    "bronchodilator": "a small amber pharmacy bottle containing a few pale bronchodilator tablets, with an unmarked paper label",
    "bronze": "a compact beveled ingot of bronze, with a dark copper brown sheen and crisp cast edges",
    "broom": "a short straw broom with tightly bound golden bristles and a dark wooden handle",
    "brown_sugar": "a small serving of brown sugar on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "brush": "a compact recognizable brush with a clear silhouette and visible wood, metal or fiber details",
    "bucket": "a small weathered wooden bucket with iron hoops and a curved handle",
    "buckwheat": "a small heap of natural tan and cream buckwheat grains and seeds in a shallow wooden bowl",
    "bunk_bed": "a small wooden bunk bed with clean joinery, warm brown grain and a compact recognizable silhouette",
    "bupleurum": "a bundle of dried bupleurum roots with twisted tan tubers and a few fibrous rootlets",
    "burger": "a small serving of burger on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "burn_ointment": "a small folded metal tube of burn ointment with a screw cap and a narrow nozzle",
    "butane": "a small sealed steel gas cylinder containing butane, with a narrow colored glass sight window and a metal valve",
    "butter": "a small serving of butter on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "butter_tea": "a small ceramic cup of butter tea, with a pale cream surface and a delicate rising curl of steam",
    "cabbage": "a fresh cabbage with a firm edible shape, visible natural skin texture and green leafy accents",
    "cabinet": "a small wooden cabinet with clean joinery, warm brown grain and a compact recognizable silhouette",
    "cable_car": "a small red cable car cabin suspended from a taut overhead cable",
    "caffeine": "a small heap of fine caffeine chemical crystals in a shallow glass dish",
    "caffeine_tablet": "one small compressed pale caffeine medicine tablet with a shallow center score",
    "calcined_oyster_shell": "a small pile of chalky white calcined oyster shell fragments",
    "calcium": "a few dull silver metallic calcium pieces with small fractured faces",
    "calcium_acetate": "a small heap of white calcium acetate crystals with angular faces in a shallow glass dish",
    "calcium_carbide": "a small heap of fine calcium carbide chemical crystals in a shallow glass dish",
    "calcium_dihydrogen_phosphate": "a small heap of white calcium dihydrogen phosphate crystals with angular faces in a shallow glass dish",
    "calcium_fluoride": "a small heap of white calcium fluoride crystals with angular faces in a shallow glass dish",
    "calcium_phosphate": "a small heap of white calcium phosphate crystals with angular faces in a shallow glass dish",
    "calcium_silicate": "a small heap of white calcium silicate crystals with angular faces in a shallow glass dish",
    "calcium_sulfate": "a small heap of white calcium sulfate crystals with angular faces in a shallow glass dish",
    "calcium_tablet": "one small compressed pale calcium medicine tablet with a shallow center score",
    "calendar": "a small folded paper calendar with blank pale pages and a dark wooden hanger",
    "camphor": "a few translucent white camphor crystals in a shallow glass dish",
    "candle": "a short ivory beeswax candle with a small glowing wick and a shallow wax drip",
    "canning_rack": "a compact wooden canning rack with visible compartments, a sturdy rim and dark iron or brass fittings",
    "canoe": "a narrow varnished wooden canoe with a pointed bow and a single paddle",
    "canvas": "a compact bundle or folded swatch of canvas with visible fibers, grain or woven texture",
    "cappuccino": "a small ceramic cup of cappuccino, with a pale cream surface and a delicate rising curl of steam",
    "carbon_disulfide": "a small sealed glass chemical bottle containing carbon disulfide liquid with a dark amber stopper",
    "carbonic_acid": "a small clear glass reagent bottle containing colorless carbonic acid liquid with a narrow glass stopper",
    "cardamom": "a small bundle of dried cardamom herb leaves and slender stems in muted olive and tan hues",
    "carrot": "a fresh carrot with a firm edible shape, visible natural skin texture and green leafy accents",
    "cart": "a small wooden handcart with two spoked wheels and a long handle",
    "cassava": "a fresh cassava with a firm edible shape, visible natural skin texture and green leafy accents",
    "cassia_seed": "a small heap of dried cassia seeds with smooth firm shells in natural tan and brown tones",
    "cast_iron": "a compact beveled ingot of cast iron, with a charcoal gray sheen and crisp cast edges",
    "catalyst": "a small heap of dark gray porous catalyst pellets in a shallow dish",
    "cathode_copper": "a clean bright copper cathode plate with a smooth reddish metallic face",
    "cauldron": "a squat dark iron cauldron with three feet and a wide open rim",
    "cauliflower": "a fresh cauliflower with a firm edible shape, visible natural skin texture and green leafy accents",
    "celery": "a fresh celery with a firm edible shape, visible natural skin texture and green leafy accents",
    "cellulose": "a compact pale fibrous wad of cellulose with loose strands along the edges",
    "cellulose_species": "a compact pale fibrous wad of cellulose species with loose strands along the edges",
    "celtuce": "a fresh celtuce with a firm edible shape, visible natural skin texture and green leafy accents",
    "centipede_dried": "a curled dried centipede with many tiny dark legs",
    "chaff": "a small serving of chaff on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "chainmail": "a compact piece of chainmail made from interlocked dark steel rings, with visible straps, rivets and layered protective plates",
    "chair": "a small wooden chair with clean joinery, warm brown grain and a compact recognizable silhouette",
    "charred_hawthorn": "a few glossy black charred hawthorn fruit pieces with wrinkled skins",
    "cheese": "a small serving of cheese on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "chemotherapy_agent": "a small amber pharmacy bottle containing a few pale chemotherapy agent tablets, with an unmarked paper label",
    "cherry_wine": "a small clear drinking glass filled with cherry wine, showing ruby red liquid and a clean rounded rim",
    "chest": "a compact wooden chest with visible compartments, a sturdy rim and dark iron or brass fittings",
    "chest_of_drawers": "a small wooden chest of drawers with clean joinery, warm brown grain and a compact recognizable silhouette",
    "chewing_tobacco": "a small heap of finely cut dark brown chewing tobacco leaves in a shallow tin dish",
    "chicken_coop": "a compact chicken coop built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "chicken_gizzard": "a small cleaned chicken gizzard with a dark reddish muscular surface",
    "chickpea": "a small heap of natural tan and cream chickpea grains and seeds in a shallow wooden bowl",
    "chili": "a single recognizable serving of chili with natural ingredient colors and a clear appetizing silhouette on a small plate",
    "chimney_pipe": "a compact chimney pipe built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "chisel": "a iron chisel with a sturdy wooden handle and a clearly shaped working end",
    "chloramphenicol": "a small amber pharmacy bottle containing a few pale chloramphenicol tablets, with an unmarked paper label",
    "chloroauric_acid": "a small amber glass reagent bottle containing deep golden chloroauric acid solution",
    "chloroform": "a small amber medicine bottle containing chloroform liquid with a cork stopper",
    "chromium": "a compact beveled ingot of chromium, with a bright mirror silver sheen and crisp cast edges",
    "chromium_oxide": "a small heap of white chromium oxide crystals with angular faces in a shallow glass dish",
    "chrysanthemum": "a small bunch of dried chrysanthemum blossoms with curled petals in muted natural colors",
    "chrysanthemum_greens": "a fresh chrysanthemum greens with a firm edible shape, visible natural skin texture and green leafy accents",
    "cicada_shell": "a thin amber cicada shell with a hollow segmented body and delicate legs",
    "cider": "a small clear drinking glass filled with cider, showing pale golden liquid and a clean rounded rim",
    "cigar": "one neatly rolled cigar with a tan paper or leaf wrapper and a dark tip",
    "cigarette": "one neatly rolled cigarette with a tan paper or leaf wrapper and a dark tip",
    "cigarette_case": "a sturdy cigarette case with a clear opening, useful capacity and distinct wood, glass or woven material details",
    "cinnabar_ore": "a rough pale stone fragment veined with vivid vermilion cinnabar crystals",
    "cinnamon": "a small heap of fine cinnamon powder in a shallow paper packet with a few visible grains",
    "cinnamon_bark": "a bundle of curled dried cinnamon bark strips with rough brown outer surfaces",
    "cinnamon_twig": "a short dried cinnamon twig with slender woody stems and a few attached leaves",
    "cistern": "a compact cistern built from aged copper and gray stone, with a clear functional opening and distinct structural details",
    "citric_acid": "a small amber medicine bottle containing citric acid liquid with a cork stopper",
    "citrus": "a single fresh citrus with its recognizable natural ripe colors and a clearly recognizable natural shape",
    "clay_brick": "a single piece of clay brick showing its practical shape, natural material color and rough or worked surface",
    "clay_jar": "a compact wooden clay jar with visible compartments, a sturdy rim and dark iron or brass fittings",
    "clay_tile": "a single piece of clay tile showing its practical shape, natural material color and rough or worked surface",
    "cloak": "a neatly folded cloak made from sturdy woven fabric with visible seams and a clear wearable shape",
    "clock": "a compact functional clock made from dark brass and glass with a clear working feature",
    "clogs": "a neatly folded clogs made from sturdy woven fabric with visible seams and a clear wearable shape",
    "clothesline": "a compact recognizable clothesline made from practical natural materials, with a clear functional shape and distinct surface details",
    "clove": "a small heap of fine clove powder in a shallow paper packet with a few visible grains",
    "coagulant": "a single recognizable serving of coagulant with natural ingredient colors and a clear appetizing silhouette on a small plate",
    "coal": "a jagged glossy black coal lump with angular fractured faces",
    "coal_chunk": "a jagged glossy black coal chunk with layered fractured faces",
    "coarse_tailings": "a small heap of coarse gray mine tailings with mixed angular rock grains",
    "cobalt": "a compact beveled ingot of cobalt, with a cool steel blue sheen and crisp cast edges",
    "cobalt_oxide": "a small heap of deep navy cobalt oxide crystals with angular faces in a shallow glass dish",
    "coca_leaf": "a small bundle of dried coca leaf leaves or nuts with visible veins and muted green brown tones",
    "cocktail_shaker": "a clear or pale glass cocktail shaker with a thick rim, visible empty interior and a sturdy base",
    "cocoa": "a small clear drinking glass filled with cocoa, showing natural pale golden liquid and a clean rounded rim",
    "cocoa_bean": "a small heap of dark brown cocoa beans in a shallow wooden bowl",
    "cocoa_powder": "a small heap of fine dark cocoa powder in a shallow paper packet",
    "coconut": "a single fresh coconut with its recognizable natural ripe colors and a clearly recognizable natural shape",
    "coconut_water": "a clear glass bottle of coconut water with pale cream liquid, a few bright bubbles and a simple metal cap",
    "codeine": "a small amber pharmacy bottle containing a few pale codeine tablets, with an unmarked paper label",
    "codonopsis": "a bundle of dried codonopsis roots with twisted tan tubers and a few fibrous rootlets",
    "coffee": "a small ceramic cup of coffee, with a warm dark brown surface and a delicate rising curl of steam",
    "coffee_cherry": "a single fresh coffee cherry with its recognizable natural ripe colors and a clearly recognizable natural shape",
    "coffee_grounds": "a small heap of coarse dark roasted coffee grounds in a shallow wooden bowl",
    "coffee_liqueur": "a small clear drinking glass filled with coffee liqueur, showing dark brown liquid and a clean rounded rim",
    "coffee_pot": "a sturdy coffee pot with a clear opening, useful capacity and distinct wood, glass or woven material details",
    "coin": "a single round bronze coin with a raised geometric rim and a plain unmarked face",
    "coix_seed": "a small heap of dried coix seeds with smooth firm shells in natural tan and brown tones",
    "cold_brew": "a small ceramic cup of cold brew, with a warm dark brown surface and a delicate rising curl of steam",
    "comparator": "a compact redstone comparator with three red stone torches on a gray base",
    "compass": "a round brass compass with a glass cover, dark needle and engraved rim",
    "cooked_beef": "a small browned serving of beef with a golden seared edge",
    "cooked_chicken": "a small browned serving of chicken with a golden seared edge",
    "cooked_fish": "a small browned serving of fish with a golden seared edge",
    "cooked_mutton": "a small browned serving of mutton with a golden seared edge",
    "cooked_pork": "a small browned serving of pork with a golden seared edge",
    "cooked_rabbit": "a small browned serving of rabbit with a golden seared edge",
    "cooking_oil": "a small glass jar of cooking oil with a clear view of its natural rich color and smooth texture",
    "copper_armor": "a compact piece of copper armor made from warm reddish copper plates, with visible straps, rivets and layered protective plates",
    "copper_oxide": "a small heap of black copper oxide crystals with angular faces in a shallow glass dish",
    "copper_pickaxe": "a iron copper pickaxe with a sturdy wooden handle and a clearly shaped working end",
    "copper_sulfate": "a small heap of fine copper sulfate chemical crystals in a shallow glass dish",
    "copper_sulfide_mat": "a small heap of white copper sulfide mat crystals with angular faces in a shallow glass dish",
    "copper_sword": "a compact copper sword with a dark metal blade or striking head, a sturdy handle and a distinct guard",
    "coptis": "a bundle of dried coptis roots with twisted tan tubers and a few fibrous rootlets",
    "corn_flour": "a small heap of fine corn flour powder in a shallow paper packet with a few visible grains",
    "corn_tortilla": "a small serving of corn tortilla on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "cornus": "a few dried cornus fruits with wrinkled skins and deep red brown natural colors",
    "corticosteroid": "a small amber pharmacy bottle containing a few pale corticosteroid tablets, with an unmarked paper label",
    "corydalis": "a bundle of dried corydalis roots with twisted tan tubers and a few fibrous rootlets",
    "cotton_raw": "a compact bundle or folded swatch of cotton raw with visible fibers, grain or woven texture",
    "cough_syrup": "a small amber medicine bottle containing cough syrup liquid with a cork stopper",
    "cowshed": "a compact cowshed built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "crab": "a compact serving of crab with a clearly visible natural texture on a shallow ceramic dish",
    "crate": "a compact wooden crate with visible compartments, a sturdy rim and dark iron or brass fittings",
    "cream": "a small serving of cream on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "crossbow": "a compact dark metal crossbow with a taut string or narrow barrel and a clear hand grip",
    "crutch": "a neatly folded crutch made from sturdy woven fabric with visible seams and a clear wearable shape",
    "cucumber": "a fresh cucumber with a firm edible shape, visible natural skin texture and green leafy accents",
    "cupronickel": "a compact beveled ingot of cupronickel, with a pale silver sheen and crisp cast edges",
    "cured_tobacco": "a small bundle of dried cured tobacco leaves or nuts with visible veins and muted green brown tones",
    "curry": "a small serving of curry on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "curtain": "a neatly folded curtain made from sturdy woven fabric with visible seams and a clear wearable shape",
    "cut_stone": "a single piece of cut stone showing its practical shape, natural material color and rough or worked surface",
    "cuttlebone": "a light porous ivory cuttlebone with a narrow oval shape",
    "dagger": "a compact dagger with a dark metal blade or striking head, a sturdy handle and a distinct guard",
    "daiquiri": "a clear cocktail glass filled with daiquiri, showing a distinct natural drink color and a small citrus garnish",
    "dandelion": "a small bundle of dried dandelion herb leaves and slender stems in muted olive and tan hues",
    "dark_tea": "a small ceramic cup of dark tea, with a warm dark brown surface and a delicate rising curl of steam",
    "date": "a single fresh date with its recognizable natural ripe colors and a clearly recognizable natural shape",
    "deionized_water": "a clear glass bottle of deionized water with transparent liquid, a few bright bubbles and a simple metal cap",
    "diamond_armor": "a compact piece of diamond armor made from pale blue mineral plates, with visible straps, rivets and layered protective plates",
    "diamond_pickaxe": "a iron diamond pickaxe with a sturdy wooden handle and a clearly shaped working end",
    "diamond_sword": "a compact diamond sword with a dark metal blade or striking head, a sturdy handle and a distinct guard",
    "dice": "a compact dice with its distinctive paper, carved or metal details and a clearly readable silhouette",
    "diethyl_ether": "a small amber medicine bottle containing diethyl ether liquid with a cork stopper",
    "dioscorea": "a bundle of dried dioscorea roots with twisted tan tubers and a few fibrous rootlets",
    "display_case": "a compact wooden display case with visible compartments, a sturdy rim and dark iron or brass fittings",
    "distilled_water": "a clear glass bottle of distilled water with transparent liquid, a few bright bubbles and a simple metal cap",
    "door": "a compact door built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "door_curtain": "a neatly folded door curtain made from sturdy woven fabric with visible seams and a clear wearable shape",
    "double": "a small charcoal glass token with two pale inset dots representing a double precision value",
    "dough_wrapper": "a small serving of dough wrapper on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "down": "a compact bundle or folded swatch of down with visible fibers, grain or woven texture",
    "dragons_blood": "a small dark crimson resin lump with a brittle fractured surface",
    "drain_pipe": "a single piece of drain pipe showing its practical shape, natural material color and rough or worked surface",
    "dried_fish": "a compact serving of dried fish with a clearly visible natural texture on a shallow ceramic dish",
    "dried_tangerine_peel": "a curled strip of dried orange tangerine peel with a pale pith and dimpled skin",
    "dried_tofu": "a compact serving of dried tofu with a clearly visible natural texture on a shallow ceramic dish",
    "drill": "a compact hand drill with a dark iron body, wooden grip and a sharp spiral steel bit",
    "drinking_yogurt": "a small ceramic cup of drinking yogurt, with a smooth pale cream surface and a thick rounded rim",
    "drum": "a compact recognizable drum with a clear silhouette and visible wood, metal or fiber details",
    "drywall": "a single piece of drywall showing its practical shape, natural material color and rough or worked surface",
    "duck_egg": "a single fresh duck egg with a smooth pale shell and a softly shaded rounded form",
    "dumpling": "a small serving of dumpling on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "dustpan": "a compact recognizable dustpan with a clear silhouette and visible wood, metal or fiber details",
    "dye": "a small glass jar of dye with richly colored liquid and a simple cork stopper",
    "earplugs": "a neatly folded earplugs made from sturdy woven fabric with visible seams and a clear wearable shape",
    "earthworm_dried": "a slender curled dried earthworm with segmented brown rings",
    "egg": "a single fresh egg with a smooth pale shell and a softly shaded rounded form",
    "eggnog": "a small clear drinking glass filled with eggnog, showing pale cream liquid and a clean rounded rim",
    "eggplant": "a fresh eggplant with a firm edible shape, visible natural skin texture and green leafy accents",
    "emodin": "a small heap of orange brown emodin crystals in a shallow dish",
    "enum": "a small selector medallion divided into three distinct colored segments",
    "ephedra": "a bundle of dried ephedra roots with twisted tan tubers and a few fibrous rootlets",
    "espresso": "a small ceramic cup of espresso, with a warm dark brown surface and a delicate rising curl of steam",
    "ethylbenzene": "a small sealed glass chemical bottle containing ethylbenzene liquid with a dark amber stopper",
    "ethylene_cell": "a small sealed steel gas cylinder containing ethylene, with a narrow colored glass sight window and a metal valve",
    "eucommia_bark": "a bundle of curled dried eucommia bark strips with rough brown outer surfaces",
    "eucommia_leaf": "several dried eucommia leaves with visible veins and muted olive green edges",
    "euryale": "a small heap of dried euryale seeds with smooth firm shells in natural tan and brown tones",
    "evodia": "a few dried evodia fruits with wrinkled skins and deep red brown natural colors",
    "face_mask": "a neatly folded face mask made from sturdy woven fabric with visible seams and a clear wearable shape",
    "feed_trough": "a compact feed trough built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "felt": "a compact bundle or folded swatch of felt with visible fibers, grain or woven texture",
    "fence": "a compact fence built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "fentanyl": "a small unmarked amber pharmacy bottle with a child resistant cap and several pale round scored tablets visible through the glass",
    "ferry": "a compact wooden river ferry with a low deck and short railings",
    "fig": "a single fresh fig with its recognizable natural ripe colors and a clearly recognizable natural shape",
    "file": "a iron file with a sturdy wooden handle and a clearly shaped working end",
    "filling": "a small serving of filling on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "filter_cake": "a compact damp gray filter cake slab with a crumbly mineral surface",
    "finger_citron": "a small bundle of dried finger citron herb leaves and slender stems in muted olive and tan hues",
    "fire_striker": "a sturdy fire striker with a dark steel working head and a smooth wooden handle",
    "fireplace": "a compact red brick fireplace with a dark arched hearth and a small orange flame",
    "firework": "a short colorful firework rocket with a paper body, wooden stick and bright fuse",
    "fish_pond": "a compact fish pond built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "fishing_net": "a small bundled brown fishing net with diamond mesh and a short wooden float",
    "flail": "a sturdy flail with a dark steel working head and a smooth wooden handle",
    "flask": "a clear or pale glass flask with a thick rim, visible empty interior and a sturdy base",
    "flatbread": "a small serving of flatbread on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "flax_thread": "a compact bundle or folded swatch of flax thread with visible fibers, grain or woven texture",
    "flint_and_steel": "a compact recognizable flint and steel made from practical natural materials, with a clear functional shape and distinct surface details",
    "floor_tile": "a single piece of floor tile showing its practical shape, natural material color and rough or worked surface",
    "flotation_reagent": "a small amber reagent bottle of flotation chemical with a narrow stopper",
    "fluorine_gas_cell": "a small sealed steel gas cylinder containing fluorine, with a narrow colored glass sight window and a metal valve",
    "fluorosilicic_acid": "a small clear glass reagent bottle containing colorless fluorosilicic acid liquid with a narrow glass stopper",
    "flute": "a compact recognizable flute with a clear silhouette and visible wood, metal or fiber details",
    "folding_screen": "a compact folding screen built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "forceps": "a pair of slender stainless steel medical forceps with narrow gripping tips",
    "forsythia": "a small bundle of dried forsythia herb leaves and slender stems in muted olive and tan hues",
    "fossil_bone": "a weathered tan fossil bone fragment with porous broken ends",
    "foxnut": "a small heap of dried foxnut seeds with smooth firm shells in natural tan and brown tones",
    "frankincense": "a small heap of pale golden frankincense resin tears",
    "fried_egg": "a sunny side up fried egg with a bright golden yolk and crisp white edge on a small plate",
    "fries": "a small serving of fries on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "fructose": "a small heap of fine fructose chemical crystals in a shallow glass dish",
    "fur": "a compact bundle or folded swatch of fur with visible fibers, grain or woven texture",
    "garlic": "a fresh garlic with a firm edible shape, visible natural skin texture and green leafy accents",
    "gastrodia": "a bundle of dried gastrodia roots with twisted tan tubers and a few fibrous rootlets",
    "gem_carving_tool": "a iron gem carving tool with a sturdy wooden handle and a clearly shaped working end",
    "gem_cut": "a small polished faceted gemstone with bright blue green planes and crisp reflective edges",
    "gem_dust": "a small heap of fine translucent gemstone grit with pale blue green sparkles",
    "gem_rough": "a rough uncut gemstone with a muted blue green crystal body and irregular natural facets",
    "gin": "a small clear drinking glass filled with gin, showing clear liquid and a clean rounded rim",
    "gin_tonic": "a clear cocktail glass filled with gin tonic, showing a distinct natural drink color and a small citrus garnish",
    "ginger": "a fresh ginger with a firm edible shape, visible natural skin texture and green leafy accents",
    "ginger_ale": "a small clear drinking glass filled with ginger ale, showing golden with fine bubbles liquid and a clean rounded rim",
    "ginseng": "a bundle of dried ginseng roots with twisted tan tubers and a few fibrous rootlets",
    "ginseng_decoction": "a small glass bottle of dark amber ginseng decoction with a cork stopper",
    "glass_bottle": "a clear or pale glass glass bottle with a thick rim, visible empty interior and a sturdy base",
    "glass_pane": "a thin clear glass pane with a faint blue edge and four sharp corners",
    "gloves": "a neatly folded gloves made from sturdy woven fabric with visible seams and a clear wearable shape",
    "glucose": "a small amber pharmacy bottle containing a few pale glucose tablets, with an unmarked paper label",
    "glucose_injection": "a small amber medicine bottle containing glucose injection liquid with a cork stopper",
    "glucose_syrup": "a small glass bottle of thick translucent glucose syrup syrup with a warm golden tint",
    "glutinous_rice": "a small heap of natural tan and cream glutinous rice grains and seeds in a shallow wooden bowl",
    "glutinous_rice_flour": "a small heap of fine glutinous rice flour powder in a shallow paper packet with a few visible grains",
    "goat_milk": "a small glass jar of goat milk with a clear view of its natural rich color and smooth texture",
    "goggles": "a neatly folded goggles made from sturdy woven fabric with visible seams and a clear wearable shape",
    "goji": "a few dried goji fruits with wrinkled skins and deep red brown natural colors",
    "gold": "a compact beveled ingot of gold, with a rich yellow gold sheen and crisp cast edges",
    "gold_sponge": "a porous bright yellow gold sponge mass with many small cavities",
    "goose_egg": "a single fresh goose egg with a smooth pale shell and a softly shaded rounded form",
    "granary": "a compact wooden granary with visible compartments, a sturdy rim and dark iron or brass fittings",
    "grape": "a single fresh grape with its recognizable natural ripe colors and a clearly recognizable natural shape",
    "grapefruit": "a ripe grapefruit with a dimpled yellow rind and one pink cut half",
    "grappa": "a small clear drinking glass filled with grappa, showing clear liquid and a clean rounded rim",
    "grass_fiber": "a compact bundle or folded swatch of grass fiber with visible fibers, grain or woven texture",
    "gravestone": "a weathered gray stone gravestone with a plain carved top and moss along the base",
    "gray_concrete_powder": "a small heap of cool gray concrete powder with coarse grains",
    "green_tangerine_peel": "a small bundle of dried green tangerine peel herb leaves and slender stems in muted olive and tan hues",
    "green_tea": "a small ceramic cup of green tea, with a bright green surface and a delicate rising curl of steam",
    "grinding_wheel_coarse": "a thick circular grinding wheel of coarse dark gray abrasive grains, with a dark central mounting hole and a solid stone rim",
    "grinding_wheel_fine": "a thick circular grinding wheel of fine pale gray abrasive grains, with a dark central mounting hole and a solid stone rim",
    "grinding_wheel_polish": "a thick circular grinding wheel of smooth light gray polishing grit, with a dark central mounting hole and a solid stone rim",
    "gypsum": "a cluster of translucent milky gypsum crystals with flat cleavage faces",
    "halite": "a cluster of translucent white halite crystals with cubic corners",
    "ham": "a compact serving of ham with a clearly visible natural texture on a shallow ceramic dish",
    "hammock": "a neatly folded hammock made from sturdy woven fabric with visible seams and a clear wearable shape",
    "hamper": "a small hamper made from woven brown fiber or leather with visible straps, handles or stitching",
    "hand_lens": "a compact hand lens with a dark metal casing, a clear glass or measuring feature and a sturdy grip",
    "hand_pump": "a compact cast iron hand pump with a curved lever, wooden grip and short water spout",
    "hard_hat": "a compact recognizable hard hat made from practical natural materials, with a clear functional shape and distinct surface details",
    "hard_water": "a clear glass vial of cloudy hard water with pale mineral sediment at the bottom",
    "harrow": "a sturdy harrow with a dark steel working head and a smooth wooden handle",
    "hawthorn": "a single fresh hawthorn with its recognizable natural ripe colors and a clearly recognizable natural shape",
    "hawthorn_fruit": "a few dried hawthorn fruit fruits with wrinkled skins and deep red brown natural colors",
    "hay": "a compact golden bundle of dry hay tied with a short fiber cord",
    "haystack": "a compact haystack built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "headscarf": "a neatly folded headscarf made from sturdy woven fabric with visible seams and a clear wearable shape",
    "hearth": "a compact hearth built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "hematite_ore": "a dark stone fragment with rusty red metallic hematite grains",
    "high_carbon_steel": "a compact beveled ingot of high carbon steel, with a dark blue gray sheen and crisp cast edges",
    "highball": "a clear cocktail glass filled with highball, showing a distinct natural drink color and a small citrus garnish",
    "hinge": "a sturdy hinge with a dark steel working head and a smooth wooden handle",
    "hoe": "a sturdy hoe with a dark steel working head and a smooth wooden handle",
    "honey": "a small glass jar of honey with a clear view of its natural rich color and smooth texture",
    "honey_pot": "a compact wooden honey pot with visible compartments, a sturdy rim and dark iron or brass fittings",
    "honeycomb": "a small golden honeycomb piece with visible hexagonal wax cells",
    "honeysuckle": "a small bundle of dried honeysuckle herb leaves and slender stems in muted olive and tan hues",
    "hookah": "a compact wooden hookah with a curved stem, dark bowl and a small brass fitting",
    "hopper": "a dark iron hopper with a wide square mouth and a tapered lower spout",
    "horn": "a compact recognizable horn with a clear silhouette and visible wood, metal or fiber details",
    "hot_chocolate": "a small ceramic cup of hot chocolate, with a warm dark brown surface and a delicate rising curl of steam",
    "hourglass": "a small brass framed hourglass with pale sand in a clear double bulb",
    "houttuynia": "a small bundle of dried houttuynia herb leaves and slender stems in muted olive and tan hues",
    "huangjiu": "a small clear drinking glass filled with huangjiu, showing amber gold liquid and a clean rounded rim",
    "human_power": "a compact wooden hand crank with a dark iron axle and a rotating handle",
    "humidor": "a compact wooden humidor with visible compartments, a sturdy rim and dark iron or brass fittings",
    "hydrated_silica": "a small heap of fine hydrated silica chemical crystals in a shallow glass dish",
    "hydrofluoric_acid": "a small clear glass reagent bottle containing colorless hydrofluoric acid liquid with a narrow glass stopper",
    "hydrogen_fluoride": "a small sealed steel gas cylinder containing hydrogen fluoride, with a narrow colored glass sight window and a metal valve",
    "hydrogen_peroxide_solution": "a small amber medicine bottle containing hydrogen peroxide solution liquid with a cork stopper",
    "hydrogen_sulfide_cell": "a small sealed steel gas cylinder containing hydrogen sulfide, with a narrow colored glass sight window and a metal valve",
    "ibuprofen": "one small compressed pale ibuprofen medicine tablet with a shallow center score",
    "icebox": "a compact wooden icebox with visible compartments, a sturdy rim and dark iron or brass fittings",
    "immunosuppressant": "a small amber pharmacy bottle containing a few pale immunosuppressant tablets, with an unmarked paper label",
    "imperata": "a bundle of dried imperata roots with twisted tan tubers and a few fibrous rootlets",
    "inconel": "a compact beveled ingot of inconel, with a dull silver gray sheen and crisp cast edges",
    "indigo": "a small heap of deep blue indigo dye crystals in a shallow wooden bowl",
    "indigo_dye": "a small glass jar of indigo dye with richly colored liquid and a simple cork stopper",
    "ink": "a small glass jar of ink with richly colored liquid and a simple cork stopper",
    "ink_line": "a compact recognizable ink line made from practical natural materials, with a clear functional shape and distinct surface details",
    "insect_protein": "a single recognizable serving of insect protein with natural ingredient colors and a clear appetizing silhouette on a small plate",
    "instrument": "a compact recognizable instrument with a clear silhouette and visible wood, metal or fiber details",
    "insulation": "a compact bundle or folded swatch of insulation with visible fibers, grain or woven texture",
    "insulin": "a small amber pharmacy bottle containing a few pale insulin tablets, with an unmarked paper label",
    "int": "a compact brass counting token with a single raised notch representing an integer value",
    "iodine": "a small amber medicine bottle containing iodine liquid with a cork stopper",
    "iodine_species": "a small heap of white iodine species crystals with angular faces in a shallow glass dish",
    "iron": "a single piece of iron showing its practical shape, natural material color and rough or worked surface",
    "iron_armor": "a compact piece of iron armor made from dull gray iron plates, with visible straps, rivets and layered protective plates",
    "iron_axe": "a iron axe with a sturdy wooden handle and a clearly shaped working end",
    "iron_bars": "a short section of dark iron bars with evenly spaced vertical rods",
    "iron_hoe": "a iron hoe with a sturdy wooden handle and a clearly shaped working end",
    "iron_oxide_mat": "a small heap of white iron oxide mat crystals with angular faces in a shallow glass dish",
    "iron_pickaxe": "a iron pickaxe with a sturdy wooden handle and a clearly shaped working end",
    "iron_shovel": "a iron shovel with a sturdy wooden handle and a clearly shaped working end",
    "iron_silicate": "a small heap of white iron silicate crystals with angular faces in a shallow glass dish",
    "iron_sulfide_mat": "a small heap of white iron sulfide mat crystals with angular faces in a shallow glass dish",
    "iron_supplement": "a small amber pharmacy bottle containing a few pale iron supplement tablets, with an unmarked paper label",
    "iron_sword": "a compact iron sword with a dark metal blade or striking head, a sturdy handle and a distinct guard",
    "irrigation_channel": "a compact irrigation channel built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "isatis_root": "a bundle of dried isatis roots with twisted tan tubers and a few fibrous rootlets",
    "jewelry_box": "a compact wooden jewelry box with visible compartments, a sturdy rim and dark iron or brass fittings",
    "jigger": "a clear or pale glass jigger with a thick rim, visible empty interior and a sturdy base",
    "job_tears": "a small heap of natural tan and cream job tears grains and seeds in a shallow wooden bowl",
    "juice": "a small clear drinking glass filled with juice, showing orange gold liquid and a clean rounded rim",
    "jujube_seed": "a small heap of dried jujube seeds with smooth firm shells in natural tan and brown tones",
    "kaolinite": "a pale cream kaolinite clay lump with a soft powdery surface",
    "kebab": "a small serving of kebab on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "kelp": "a single recognizable serving of kelp with natural ingredient colors and a clear appetizing silhouette on a small plate",
    "kelp_species": "a compact pale fibrous wad of kelp species with loose strands along the edges",
    "key": "an old iron key with a broad round bow and a notched shaft",
    "kiwi": "a single fresh kiwi with its recognizable natural ripe colors and a clearly recognizable natural shape",
    "knife": "a sturdy knife with a dark steel working head and a smooth wooden handle",
    "konjac": "a fresh konjac with a firm edible shape, visible natural skin texture and green leafy accents",
    "kudzu_root": "a bundle of dried kudzu roots with twisted tan tubers and a few fibrous rootlets",
    "kumis": "a small ceramic cup of kumis, with a smooth pale cream surface and a thick rounded rim",
    "kvass": "a small clear drinking glass filled with kvass, showing dark amber liquid and a clean rounded rim",
    "lacquer": "a small glass jar of lacquer with richly colored liquid and a simple cork stopper",
    "lactic_acid": "a small amber medicine bottle containing lactic acid liquid with a cork stopper",
    "ladder": "a sturdy ladder with a dark steel working head and a smooth wooden handle",
    "lager": "a small clear drinking glass filled with lager, showing pale gold with a light foam liquid and a clean rounded rim",
    "lamp": "a compact brass oil lamp with a pale glass chimney and a small yellow flame",
    "lantern": "a square iron lantern with clear glass panes and a warm golden flame",
    "lard": "a single recognizable serving of lard with natural ingredient colors and a clear appetizing silhouette on a small plate",
    "large_chest": "a compact wooden large chest with visible compartments, a sturdy rim and dark iron or brass fittings",
    "latrine": "a compact latrine built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "latte": "a small ceramic cup of latte, with a pale cream surface and a delicate rising curl of steam",
    "laxative": "a small amber pharmacy bottle containing a few pale laxative tablets, with an unmarked paper label",
    "leach_residue": "a small heap of damp brown leach residue with rusty mineral grains",
    "lead": "a compact beveled ingot of lead, with a heavy blue gray sheen and crisp cast edges",
    "lead_oxide": "a small heap of white lead oxide crystals with angular faces in a shallow glass dish",
    "leather_armor": "a compact piece of leather armor made from soft dark brown leather, with visible straps, rivets and layered protective plates",
    "leather_boots": "a compact piece of leather boots made from stitched dark brown leather, with visible straps, rivets and layered protective plates",
    "leek": "a fresh leek with a firm edible shape, visible natural skin texture and green leafy accents",
    "lemon": "a single fresh lemon with bright yellow and a clearly recognizable natural shape",
    "lemonade": "a small clear drinking glass filled with lemonade, showing pale lemon yellow liquid and a clean rounded rim",
    "lentil": "a small heap of natural tan and cream lentil grains and seeds in a shallow wooden bowl",
    "leonurus": "a bundle of dried leonurus roots with twisted tan tubers and a few fibrous rootlets",
    "lettuce": "a fresh lettuce with a firm edible shape, visible natural skin texture and green leafy accents",
    "licorice": "a bundle of dried licorice roots with twisted tan tubers and a few fibrous rootlets",
    "lidocaine": "a small amber pharmacy bottle containing a few pale lidocaine tablets, with an unmarked paper label",
    "lighthouse": "a compact lighthouse built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "lightning_rod": "a slender copper lightning rod with a pointed tip and a small mounting base",
    "ligusticum": "a bundle of dried ligusticum roots with twisted tan tubers and a few fibrous rootlets",
    "lime": "a single piece of lime showing its practical shape, natural material color and rough or worked surface",
    "lime_mortar": "a single piece of lime mortar showing its practical shape, natural material color and rough or worked surface",
    "lime_residue": "a chalky off white heap of lime residue with a few gray specks",
    "lock": "a compact recognizable lock with a clear silhouette and visible wood, metal or fiber details",
    "log": "a compact bundle or folded swatch of log with visible fibers, grain or woven texture",
    "long": "a compact beveled ingot of long, with a dark gray sheen and crisp cast edges",
    "longan": "a single fresh longan with its recognizable natural ripe colors and a clearly recognizable natural shape",
    "lophatherum": "a small bundle of dried lophatherum herb leaves and slender stems in muted olive and tan hues",
    "loquat_leaf": "several dried loquat leaves with visible veins and muted olive green edges",
    "lotus_leaf": "several dried lotus leaves with visible veins and muted olive green edges",
    "lotus_root": "a fresh lotus root with a firm edible shape, visible natural skin texture and green leafy accents",
    "lotus_seed": "a small heap of dried lotus seeds with smooth firm shells in natural tan and brown tones",
    "luffa": "a fresh luffa with a firm edible shape, visible natural skin texture and green leafy accents",
    "lunch_box": "a sturdy lunch box with a clear opening, useful capacity and distinct wood, glass or woven material details",
    "lupin": "a single recognizable serving of lupin with natural ingredient colors and a clear appetizing silhouette on a small plate",
    "lychee": "a single fresh lychee with its recognizable natural ripe colors and a clearly recognizable natural shape",
    "lycium_bark": "a bundle of curled dried lycium bark strips with rough brown outer surfaces",
    "lysimachia": "a small bundle of dried lysimachia herb leaves and slender stems in muted olive and tan hues",
    "madder": "a small heap of dried red madder roots with a few vivid crimson cut surfaces",
    "madder_dye": "a small glass jar of madder dye with richly colored liquid and a simple cork stopper",
    "magnesium": "a few bright silver metallic magnesium pieces with small fractured faces",
    "magnesium_bromide": "a small heap of white magnesium bromide crystals with angular faces in a shallow glass dish",
    "magnesium_chloride": "a small heap of white magnesium chloride crystals with angular faces in a shallow glass dish",
    "magnesium_fluoride": "a small heap of white magnesium fluoride crystals with angular faces in a shallow glass dish",
    "magnesium_hydroxide": "a small heap of white magnesium hydroxide crystals with angular faces in a shallow glass dish",
    "magnesium_oxide": "a small heap of white magnesium oxide crystals with angular faces in a shallow glass dish",
    "magnifying_glass": "a compact functional magnifying glass made from dark brass and glass with a clear working feature",
    "magnolia_bark": "a bundle of curled dried magnolia bark strips with rough brown outer surfaces",
    "magnolia_flower": "a small bunch of dried magnolia blossoms with curled petals in muted natural colors",
    "mailbox": "a compact wooden mailbox with visible compartments, a sturdy rim and dark iron or brass fittings",
    "maize": "a small heap of natural tan and cream maize grains and seeds in a shallow wooden bowl",
    "manganese": "a few white metallic manganese pieces with small fractured faces",
    "manganese_sulfate": "a small heap of pale pink manganese sulfate crystals with angular faces in a shallow glass dish",
    "mango": "a single fresh mango with golden orange and a clearly recognizable natural shape",
    "mantis_egg": "a tan ridged mantis egg case attached to a short twig",
    "map": "a folded parchment map with pale green coastlines and a dark leather tie, without text",
    "margarita": "a clear cocktail glass filled with margarita, showing a distinct natural drink color and a small citrus garnish",
    "martini": "a clear cocktail glass filled with martini, showing a distinct natural drink color and a small citrus garnish",
    "mask": "a compact recognizable mask made from practical natural materials, with a clear functional shape and distinct surface details",
    "match": "a compact recognizable match with a clear silhouette and visible wood, metal or fiber details",
    "matcha": "a small ceramic cup of matcha, with a bright green surface and a delicate rising curl of steam",
    "mead": "a small clear drinking glass filled with mead, showing clear golden liquid and a clean rounded rim",
    "medical_kit": "a small amber medical bottle or kit of medical kit with clean glass, pale contents and a secure cap",
    "medicine_box": "a compact wooden medicine box with visible compartments, a sturdy rim and dark iron or brass fittings",
    "medicine_cabinet": "a compact wooden medicine cabinet with visible compartments, a sturdy rim and dark iron or brass fittings",
    "melatonin": "a small amber pharmacy bottle containing a few pale melatonin tablets, with an unmarked paper label",
    "melia_bark": "a bundle of curled dried melia bark strips with rough brown outer surfaces",
    "milestone": "a sturdy milestone with a dark steel working head and a smooth wooden handle",
    "milk": "a small glass jar of milk with a clear view of its natural rich color and smooth texture",
    "milk_can": "a compact wooden milk can with visible compartments, a sturdy rim and dark iron or brass fittings",
    "milk_tea": "a small ceramic cup of milk tea, with a pale cream surface and a delicate rising curl of steam",
    "milking_bucket": "a compact recognizable milking bucket made from practical natural materials, with a clear functional shape and distinct surface details",
    "millet": "a small heap of natural tan and cream millet grains and seeds in a shallow wooden bowl",
    "minecart": "a squat steel minecart with a deep open tub and four small iron wheels",
    "mineral_water": "a clear glass bottle of mineral water with transparent liquid, a few bright bubbles and a simple metal cap",
    "mining_lamp": "a compact mining lamp with a dark metal casing, a clear glass or measuring feature and a sturdy grip",
    "mint": "a small bundle of dried mint herb leaves and slender stems in muted olive and tan hues",
    "mint_julep": "a clear cocktail glass filled with mint julep, showing a distinct natural drink color and a small citrus garnish",
    "mirabilite": "a cluster of translucent white sodium sulfate crystals",
    "mirror": "a small oval mirror in a carved dark wood frame with a pale silver reflective center",
    "miso": "a small glass jar of miso with a clear view of its natural rich color and smooth texture",
    "mocktail": "a clear cocktail glass filled with mocktail, showing a distinct natural drink color and a small citrus garnish",
    "mojito": "a clear cocktail glass filled with mojito, showing a distinct natural drink color and a small citrus garnish",
    "molten_metal": "a small dark crucible filled with glowing orange molten metal",
    "molybdenite_dust": "a small heap of molybdenite powder with fine angular grains in a shallow paper packet",
    "molybdenum": "a compact beveled ingot of molybdenum, with a dark silver gray sheen and crisp cast edges",
    "molybdenum_oxide": "a small heap of white molybdenum oxide crystals with angular faces in a shallow glass dish",
    "monel": "a compact beveled ingot of monel, with a soft nickel silver sheen and crisp cast edges",
    "monument": "a compact monument built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "mooncake": "a small serving of mooncake on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "morphine": "a small amber pharmacy bottle containing a few pale morphine tablets, with an unmarked paper label",
    "mortar_pestle": "a sturdy mortar pestle with a dark steel working head and a smooth wooden handle",
    "mosla": "a small bundle of dried mosla herb leaves and slender stems in muted olive and tan hues",
    "motion_sickness_pill": "one small compressed pale motion sickness pill medicine tablet with a shallow center score",
    "motorcycle": "a compact black motorcycle with a metal frame, fuel tank and two thick tires",
    "moutan_bark": "a bundle of curled dried moutan bark strips with rough brown outer surfaces",
    "msg": "a small heap of fine msg powder in a shallow paper packet with a few visible grains",
    "mug": "a clear or pale glass mug with a thick rim, visible empty interior and a sturdy base",
    "mugwort": "a small bundle of dried mugwort herb leaves and slender stems in muted olive and tan hues",
    "mulberry": "a single fresh mulberry with its recognizable natural ripe colors and a clearly recognizable natural shape",
    "mulberry_bark": "a bundle of curled dried mulberry bark strips with rough brown outer surfaces",
    "mulberry_leaf": "several dried mulberry leaves with visible veins and muted olive green edges",
    "mulled_wine": "a small clear drinking glass filled with mulled wine, showing deep red liquid and a clean rounded rim",
    "mung_bean": "a small heap of bright green mung bean grains and seeds in a shallow wooden bowl",
    "murex": "a small ridged sea snail shell with muted purple and ivory bands",
    "murex_dye": "a small glass jar of murex dye with richly colored liquid and a simple cork stopper",
    "muscle_relaxant": "a small amber pharmacy bottle containing a few pale muscle relaxant tablets, with an unmarked paper label",
    "mushroom": "a small serving of mushroom on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "mustard": "a single recognizable serving of mustard with natural ingredient colors and a clear appetizing silhouette on a small plate",
    "mustard_seed": "a small heap of dried mustard seeds with smooth firm shells in natural tan and brown tones",
    "myrrh": "a small heap of dark reddish brown myrrh resin tears",
    "nail": "a sturdy nail with a dark steel working head and a smooth wooden handle",
    "naloxone": "a small amber pharmacy bottle containing a few pale naloxone tablets, with an unmarked paper label",
    "needle": "a compact recognizable needle with a clear silhouette and visible wood, metal or fiber details",
    "nickel": "a compact beveled ingot of nickel, with a pale metallic silver sheen and crisp cast edges",
    "nickel_oxide": "a small heap of dark green nickel oxide crystals with angular faces in a shallow glass dish",
    "nickel_sulfide": "a small heap of white nickel sulfide crystals with angular faces in a shallow glass dish",
    "nitric_oxide_cell": "a small sealed steel gas cylinder containing nitric oxide, with a narrow colored glass sight window and a metal valve",
    "nitrobenzene": "a small sealed glass chemical bottle containing nitrobenzene liquid with a dark amber stopper",
    "nitrogen_dioxide_cell": "a small sealed steel gas cylinder containing nitrogen dioxide, with a narrow colored glass sight window and a metal valve",
    "nitroglycerin": "a small heap of fine nitroglycerin chemical crystals in a shallow glass dish",
    "nitrotoluene": "a small sealed glass chemical bottle containing nitrotoluene liquid with a dark amber stopper",
    "nitrous_oxide": "a small amber pharmacy bottle containing a few pale nitrous oxide tablets, with an unmarked paper label",
    "nitrous_oxide_cell": "a small sealed steel gas cylinder containing nitrous oxide, with a narrow colored glass sight window and a metal valve",
    "noodles": "a small serving of noodles on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "nori": "a single recognizable serving of nori with natural ingredient colors and a clear appetizing silhouette on a small plate",
    "notopterygium": "a bundle of dried notopterygium roots with twisted tan tubers and a few fibrous rootlets",
    "nutmeg": "a small heap of fine nutmeg powder in a shallow paper packet with a few visible grains",
    "oak_bark": "a short rugged strip of oak bark with deep fissures and gray brown ridges",
    "oak_cask": "a compact wooden oak cask with visible compartments, a sturdy rim and dark iron or brass fittings",
    "oat": "a small heap of natural tan and cream oat grains and seeds in a shallow wooden bowl",
    "oat_milk": "a small ceramic cup of oat milk, with a smooth pale cream surface and a thick rounded rim",
    "observer": "a compact gray observer block face with a dark circular sensor and redstone output",
    "oil_lamp": "a compact brass oil lamp with a pale glass chimney and a small yellow flame",
    "okra": "a fresh okra with a firm edible shape, visible natural skin texture and green leafy accents",
    "old_fashioned": "a clear cocktail glass filled with old fashioned, showing a distinct natural drink color and a small citrus garnish",
    "olive": "a single fresh olive with its recognizable natural ripe colors and a clearly recognizable natural shape",
    "onion": "a fresh onion with a firm edible shape, visible natural skin texture and green leafy accents",
    "oolong_tea": "a small ceramic cup of oolong tea, with a warm dark brown surface and a delicate rising curl of steam",
    "operating_table": "a compact operating table built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "oral_rehydration_salts": "a small packet of white oral rehydration salt crystals with a few pale blue granules",
    "orange_liqueur": "a small clear drinking glass filled with orange liqueur, showing bright orange amber liquid and a clean rounded rim",
    "ore_scanner": "a compact ore scanner with a dark metal casing, a clear glass or measuring feature and a sturdy grip",
    "ox_cart": "a wooden two wheeled ox cart with a yoke and a plank cargo bed",
    "oyster_shell": "a rough fan shaped oyster shell with a pale ridged exterior",
    "p_aminophenol": "a small heap of fine p aminophenol chemical crystals in a shallow glass dish",
    "p_nitrophenol": "a small heap of fine p nitrophenol chemical crystals in a shallow glass dish",
    "paddock": "a compact paddock built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "padlock": "a compact recognizable padlock made from practical natural materials, with a clear functional shape and distinct surface details",
    "paint": "a small glass jar of paint with richly colored liquid and a simple cork stopper",
    "palm_fiber": "a compact bundle or folded swatch of palm fiber with visible fibers, grain or woven texture",
    "palm_wine": "a small clear drinking glass filled with palm wine, showing pale golden liquid and a clean rounded rim",
    "paloma": "a clear cocktail glass filled with paloma, showing a distinct natural drink color and a small citrus garnish",
    "panax_notoginseng": "a bundle of dried panax notoginseng roots with twisted tan tubers and a few fibrous rootlets",
    "papaya": "a single fresh papaya with its recognizable natural ripe colors and a clearly recognizable natural shape",
    "papaya_fruit": "a few dried papaya fruit fruits with wrinkled skins and deep red brown natural colors",
    "paracetamol": "one small compressed pale paracetamol medicine tablet with a shallow center score",
    "paracetamol_tablet": "one small compressed pale paracetamol medicine tablet with a shallow center score",
    "patty": "a single recognizable serving of patty with natural ingredient colors and a clear appetizing silhouette on a small plate",
    "pea": "a small heap of natural tan and cream pea grains and seeds in a shallow wooden bowl",
    "peach": "a single fresh peach with soft pink and golden and a clearly recognizable natural shape",
    "peach_seed": "a small heap of dried peach seeds with smooth firm shells in natural tan and brown tones",
    "peanut": "a small heap of natural tan and cream peanut grains and seeds in a shallow wooden bowl",
    "pear": "a single fresh pear with pale green and a clearly recognizable natural shape",
    "pearl_shell": "a curved pale shell with a small lustrous pearl embedded inside",
    "penicillin": "a small amber pharmacy bottle containing a few pale penicillin tablets, with an unmarked paper label",
    "penicillin_species": "a small heap of white penicillin species crystals with angular faces in a shallow glass dish",
    "pepper": "a single recognizable serving of pepper with natural ingredient colors and a clear appetizing silhouette on a small plate",
    "pepperweed_seed": "a small heap of dried pepperweed seeds with smooth firm shells in natural tan and brown tones",
    "perilla": "a small bundle of dried perilla herb leaves and slender stems in muted olive and tan hues",
    "perry": "a small clear drinking glass filled with perry, showing pale straw gold liquid and a clean rounded rim",
    "persimmon": "a single fresh persimmon with warm orange and a clearly recognizable natural shape",
    "phellodendron": "a small bundle of dried phellodendron herb leaves and slender stems in muted olive and tan hues",
    "phosphorus_white": "a small heap of fine phosphorus white chemical crystals in a shallow glass dish",
    "pickaxe": "a compact recognizable pickaxe made from practical natural materials, with a clear functional shape and distinct surface details",
    "pickling_crock": "a compact wooden pickling crock with visible compartments, a sturdy rim and dark iron or brass fittings",
    "pie": "a small serving of pie on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "pigpen": "a compact pigpen built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "pine_resin": "a few translucent amber pine resin tears with glossy rounded surfaces",
    "pineapple": "a single fresh pineapple with golden yellow with a small green crown and a clearly recognizable natural shape",
    "pinellia": "a bundle of dried pinellia roots with twisted tan tubers and a few fibrous rootlets",
    "pipe": "a compact wooden pipe with a curved stem, dark bowl and a small brass fitting",
    "pipe_stand": "a compact wooden pipe stand with visible compartments, a sturdy rim and dark iron or brass fittings",
    "pipe_tobacco": "a small heap of finely cut dark brown pipe tobacco leaves in a shallow tin dish",
    "pizza": "a small serving of pizza on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "plane": "a compact recognizable plane made from practical natural materials, with a clear functional shape and distinct surface details",
    "plank": "a compact bundle or folded swatch of plank with visible fibers, grain or woven texture",
    "plantago": "a small bundle of dried plantago herb leaves and slender stems in muted olive and tan hues",
    "plaster": "a single piece of plaster showing its practical shape, natural material color and rough or worked surface",
    "plate_armor": "a compact piece of plate armor made from hammered blue gray steel plates, with visible straps, rivets and layered protective plates",
    "platinum": "a compact beveled ingot of platinum, with a bright cool silver sheen and crisp cast edges",
    "platycodon": "a bundle of dried platycodon roots with twisted tan tubers and a few fibrous rootlets",
    "playing_cards": "a small fanned stack of cream playing cards with simple red and black suit shapes",
    "pliers": "a compact recognizable pliers made from practical natural materials, with a clear functional shape and distinct surface details",
    "plow": "a compact recognizable plow made from practical natural materials, with a clear functional shape and distinct surface details",
    "plum_brandy": "a small clear drinking glass filled with plum brandy, showing deep amber liquid and a clean rounded rim",
    "plum_wine": "a small clear drinking glass filled with plum wine, showing dark plum red liquid and a clean rounded rim",
    "polygonum_multiflorum": "a bundle of dried polygonum multiflorum roots with twisted tan tubers and a few fibrous rootlets",
    "pomegranate": "a single fresh pomegranate with deep red with a few visible ruby seeds and a clearly recognizable natural shape",
    "pomelo": "a single fresh pomelo with its recognizable natural ripe colors and a clearly recognizable natural shape",
    "porridge": "a small serving of porridge on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "post_station": "a small timber post station with a red door, cream parcel slot and sturdy roof",
    "potassium_hydroxide": "a small heap of white potassium hydroxide crystals with angular faces in a shallow glass dish",
    "potassium_iodate": "a small heap of white potassium iodate crystals with angular faces in a shallow glass dish",
    "potassium_iodide": "a small amber pharmacy bottle containing a few pale potassium iodide tablets, with an unmarked paper label",
    "potassium_iodide_species": "a small heap of white potassium iodide species crystals with angular faces in a shallow glass dish",
    "potassium_permanganate": "a small amber pharmacy bottle containing a few pale potassium permanganate tablets, with an unmarked paper label",
    "potato": "a fresh potato with a firm edible shape, visible natural skin texture and green leafy accents",
    "potato_chips": "a small serving of potato chips on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "potted_plant": "a compact recognizable potted plant made from practical natural materials, with a clear functional shape and distinct surface details",
    "povidone_iodine": "a small amber medicine bottle containing povidone iodine liquid with a cork stopper",
    "powered_rail": "a short golden powered rail segment with redstone contacts and dark ties",
    "prepared_angelica": "a bundle of dried prepared angelica roots with twisted tan tubers and a few fibrous rootlets",
    "prepared_corydalis": "a bundle of dried prepared corydalis roots with twisted tan tubers and a few fibrous rootlets",
    "prepared_eucommia": "a bundle of dried prepared eucommia roots with twisted tan tubers and a few fibrous rootlets",
    "prepared_licorice": "a bundle of dried prepared licorice roots with twisted tan tubers and a few fibrous rootlets",
    "prepared_rehmannia": "a bundle of dried prepared rehmannia roots with twisted tan tubers and a few fibrous rootlets",
    "propylene_cell": "a small sealed steel gas cylinder containing propylene, with a narrow colored glass sight window and a metal valve",
    "proso_millet": "a small heap of natural tan and cream proso millet grains and seeds in a shallow wooden bowl",
    "pu_er_tea": "a small ceramic cup of pu er tea, with a warm dark brown surface and a delicate rising curl of steam",
    "pumpkin": "a single fresh pumpkin with warm orange with deep ridges and a clearly recognizable natural shape",
    "purslane": "a small bundle of dried purslane herb leaves and slender stems in muted olive and tan hues",
    "pyrite_tailings": "a small heap of gray brown pyrite tailings with a few brassy sulfide flecks",
    "pyroligneous_acid": "a small glass reagent bottle of pale amber pyroligneous acid with a cork stopper",
    "quill": "a long cream feather quill with a dark brown shaft and pointed nib",
    "quinoa": "a small heap of ivory and pale red quinoa grains and seeds in a shallow wooden bowl",
    "radish": "a fresh radish with a firm edible shape, visible natural skin texture and green leafy accents",
    "radish_seed": "a small heap of dried radish seeds with smooth firm shells in natural tan and brown tones",
    "rail": "a compact recognizable rail made from practical natural materials, with a clear functional shape and distinct surface details",
    "rain_cape": "a neatly folded rain cape made from sturdy woven fabric with visible seams and a clear wearable shape",
    "rain_gauge": "a compact brass and glass rain gauge with a marked measuring tube and a visible indicator",
    "rake": "a sturdy rake with a dark steel working head and a smooth wooden handle",
    "ramen": "a small serving of ramen on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "raspberry": "a single fresh raspberry with rich red with a bumpy surface and a clearly recognizable natural shape",
    "rattan": "a compact bundle or folded swatch of rattan with visible fibers, grain or woven texture",
    "raw_beef": "a neat cut of raw beef with a fresh natural red and cream surface",
    "raw_chicken": "a neat cut of raw chicken with a fresh natural red and cream surface",
    "raw_fish": "a neat cut of raw fish with a fresh natural red and cream surface",
    "raw_mutton": "a neat cut of raw mutton with a fresh natural red and cream surface",
    "raw_pork": "a neat cut of raw pork with a fresh natural red and cream surface",
    "raw_rabbit": "a neat cut of raw rabbit with a fresh natural red and cream surface",
    "rawhide": "a compact bundle or folded swatch of rawhide with visible fibers, grain or woven texture",
    "realgar_ore": "a rough pale stone fragment studded with orange red realgar crystals",
    "red_dye": "a small heap of vivid crimson dye powder in a shallow dish",
    "red_peony": "a small bunch of dried red peony blossoms with curled petals in muted natural colors",
    "redstone": "a small heap of bright red redstone dust crystals with angular grains",
    "reed_root": "a bundle of dried reed roots with twisted tan tubers and a few fibrous rootlets",
    "refined_copper": "a clean compact ingot of bright reddish copper with crisp beveled edges",
    "rehmannia": "a bundle of dried rehmannia roots with twisted tan tubers and a few fibrous rootlets",
    "reinforced_concrete": "a single piece of reinforced concrete showing its practical shape, natural material color and rough or worked surface",
    "repeater": "a compact redstone repeater with two red torches on a gray stone base",
    "residue": "a small heap of mixed gray chemical residue with fine irregular grains",
    "resin": "a few amber resin droplets hardened into glossy irregular beads",
    "rhubarb": "a small bundle of dried rhubarb herb leaves and slender stems in muted olive and tan hues",
    "rice_cake": "a small serving of rice cake on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "rice_flour": "a small heap of fine rice flour powder in a shallow paper packet with a few visible grains",
    "rice_milk": "a small ceramic cup of rice milk, with a smooth pale cream surface and a thick rounded rim",
    "rice_noodles": "a single recognizable serving of rice noodles with natural ingredient colors and a clear appetizing silhouette on a small plate",
    "rice_paddy": "a small heap of golden tan rice paddy grains and seeds in a shallow wooden bowl",
    "rice_vinegar": "a small glass jar of rice vinegar with a clear view of its natural rich color and smooth texture",
    "rice_wine": "a small clear drinking glass filled with rice wine, showing pale amber liquid and a clean rounded rim",
    "road": "a single piece of road showing its practical shape, natural material color and rough or worked surface",
    "roasted_copper": "a rough dark reddish copper matte lump with a dry oxidized surface",
    "rock_salt": "a small serving of rock salt on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "rock_sugar": "a small serving of rock sugar on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "roof_tile": "a single piece of roof tile showing its practical shape, natural material color and rough or worked surface",
    "rope_ladder": "a compact recognizable rope ladder made from practical natural materials, with a clear functional shape and distinct surface details",
    "rosa_laevigata": "a small bundle of dried rosa laevigata herb leaves and slender stems in muted olive and tan hues",
    "rose": "a small bunch of dried rose blossoms with curled petals in muted natural colors",
    "rosewood": "a polished reddish brown rosewood chip with dark grain lines",
    "rowboat": "a small varnished wooden rowboat with two oars and a pointed bow",
    "rug": "a neatly folded rug made from sturdy woven fabric with visible seams and a clear wearable shape",
    "ruler": "a sturdy ruler with a dark steel working head and a smooth wooden handle",
    "rum": "a small clear drinking glass filled with rum, showing warm golden brown liquid and a clean rounded rim",
    "rye": "a small heap of natural tan and cream rye grains and seeds in a shallow wooden bowl",
    "rye_bread": "a small serving of rye bread on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "rye_flour": "a small heap of fine rye flour powder in a shallow paper packet with a few visible grains",
    "saddlebag": "a small saddlebag made from woven brown fiber or leather with visible straps, handles or stitching",
    "safe": "a compact wooden safe with visible compartments, a sturdy rim and dark iron or brass fittings",
    "safflower": "a small bunch of dried safflower blossoms with curled petals in muted natural colors",
    "sailboat": "a small wooden sailboat with a cream triangular sail and a dark hull",
    "sake": "a small clear drinking glass filled with sake, showing pale straw liquid and a clean rounded rim",
    "salicylic_acid": "a small amber medicine bottle containing salicylic acid liquid with a cork stopper",
    "saline_solution": "a small amber medicine bottle containing saline solution liquid with a cork stopper",
    "salvia_miltiorrhiza": "a small bundle of dried salvia miltiorrhiza herb leaves and slender stems in muted olive and tan hues",
    "sample_bag": "a small sample bag made from woven brown fiber or leather with visible straps, handles or stitching",
    "sandalwood": "a short pale tan sandalwood chip with fine straight grain",
    "sandwich": "a small serving of sandwich on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "saposhnikovia": "a bundle of dried saposhnikovia roots with twisted tan tubers and a few fibrous rootlets",
    "sappanwood": "a reddish brown sappanwood shaving with a vivid crimson cut edge",
    "sausage": "a compact serving of sausage with a clearly visible natural texture on a shallow ceramic dish",
    "saw": "a iron saw with a sturdy wooden handle and a clearly shaped working end",
    "scale": "a small brass scale with a central beam, two shallow weighing pans and a sturdy base",
    "scale_armor": "a compact piece of scale armor made from overlapping bronze scales, with visible straps, rivets and layered protective plates",
    "scalpel": "a compact surgical scalpel with a bright steel blade and a dark handle",
    "scented_soap": "a compact recognizable scented soap made from practical natural materials, with a clear functional shape and distinct surface details",
    "scented_tea": "a small ceramic cup of scented tea, with a warm dark brown surface and a delicate rising curl of steam",
    "scheelite_dust": "a small heap of scheelite powder with fine angular grains in a shallow paper packet",
    "schisandra": "a few dried schisandra fruits with wrinkled skins and deep red brown natural colors",
    "schizonepeta": "a small bundle of dried schizonepeta herb leaves and slender stems in muted olive and tan hues",
    "scorpion_dried": "a small dried scorpion with curled tail and visible pincers",
    "screwdriver": "a sturdy screwdriver with a dark steel working head and a smooth wooden handle",
    "scroll": "a rolled cream parchment scroll tied with a red cord and plain wooden end caps",
    "scutellaria": "a bundle of dried scutellaria roots with twisted tan tubers and a few fibrous rootlets",
    "sea_buckthorn": "a single fresh sea buckthorn with its recognizable natural ripe colors and a clearly recognizable natural shape",
    "sea_salt": "a small serving of sea salt on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "seal": "a small brass sealing stamp with a carved handle and a round blank face",
    "sedum": "a small bundle of dried sedum herb leaves and slender stems in muted olive and tan hues",
    "septic_tank": "a compact septic tank built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "sesame_oil": "a small glass jar of sesame oil with a clear view of its natural rich color and smooth texture",
    "sewing_kit": "a small sewing kit made from woven brown fiber or leather with visible straps, handles or stitching",
    "sextant": "a compact functional sextant made from dark brass and glass with a clear working feature",
    "shears": "a compact recognizable shears made from practical natural materials, with a clear functional shape and distinct surface details",
    "sheepfold": "a compact sheepfold built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "shellfish": "a compact serving of shellfish with a clearly visible natural texture on a shallow ceramic dish",
    "shot_glass": "a clear or pale glass shot glass with a thick rim, visible empty interior and a sturdy base",
    "shovel": "a sturdy shovel with a dark steel working head and a smooth wooden handle",
    "shower": "a compact shower built from aged copper and gray stone, with a clear functional opening and distinct structural details",
    "shrimp": "a compact serving of shrimp with a clearly visible natural texture on a shallow ceramic dish",
    "shutters": "a compact recognizable shutters made from practical natural materials, with a clear functional shape and distinct surface details",
    "sichuan_pepper": "a single recognizable serving of sichuan pepper with natural ingredient colors and a clear appetizing silhouette on a small plate",
    "sickbed": "a neatly folded sickbed made from sturdy woven fabric with visible seams and a clear wearable shape",
    "sickle": "a sturdy sickle with a dark steel working head and a smooth wooden handle",
    "sidecar": "a clear cocktail glass filled with sidecar, showing a distinct natural drink color and a small citrus garnish",
    "signpost": "a compact recognizable signpost made from practical natural materials, with a clear functional shape and distinct surface details",
    "silage_pit": "a compact silage pit built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "silicate_tailings": "a small heap of pale gray silicate tailings with gritty angular grains",
    "silk": "a compact bundle or folded swatch of silk with visible fibers, grain or woven texture",
    "silkworm_dried": "a curled dried silkworm larva with a pale segmented body",
    "silkworm_room": "a compact recognizable silkworm room made from practical natural materials, with a clear functional shape and distinct surface details",
    "silver": "a compact beveled ingot of silver, with a bright silver sheen and crisp cast edges",
    "silver_nitrate": "a small heap of white silver nitrate crystals with angular faces in a shallow glass dish",
    "sky_lantern": "a small pale orange paper sky lantern with a dark wire frame and a glowing center",
    "slaked_lime_paste": "a compact portion of slaked lime paste with a distinct earthy brown texture in a small dish",
    "slate": "a flat blue gray slate fragment with thin parallel cleavage layers",
    "slate_tile": "a single piece of slate tile showing its practical shape, natural material color and rough or worked surface",
    "sled": "a low wooden sled with curved runners and a short rope pull",
    "sleeping_bag": "a neatly folded sleeping bag made from sturdy woven fabric with visible seams and a clear wearable shape",
    "slime_ball": "a glossy translucent lime green slime ball with a soft rounded surface",
    "sling": "a compact dark metal sling with a taut string or narrow barrel and a clear hand grip",
    "smoked_plum": "a wrinkled dark smoked plum with a dusty plum skin",
    "smooth_stone": "a square slab of pale gray smooth stone with a clean even face",
    "snake_slough": "a thin translucent shed snake skin curled into a narrow strip",
    "snuff": "a small heap of finely cut dark brown snuff leaves in a shallow tin dish",
    "soap": "a compact recognizable soap made from practical natural materials, with a clear functional shape and distinct surface details",
    "soda_water": "a clear glass bottle of soda water with transparent liquid, a few bright bubbles and a simple metal cap",
    "sodium": "a few silvery metallic sodium pieces with small fractured faces",
    "sodium_acetate": "a small heap of white sodium acetate crystals with angular faces in a shallow glass dish",
    "sodium_aluminate": "a small heap of white sodium aluminate crystals with angular faces in a shallow glass dish",
    "sodium_chlorate": "a small heap of white sodium chlorate crystals with angular faces in a shallow glass dish",
    "sodium_cyanide": "a small heap of white sodium cyanide crystals with angular faces in a shallow glass dish",
    "sodium_fluorosilicate": "a small heap of white sodium fluorosilicate crystals with angular faces in a shallow glass dish",
    "sodium_silicate": "a small heap of white sodium silicate crystals with angular faces in a shallow glass dish",
    "sodium_sulfide": "a small heap of white sodium sulfide crystals with angular faces in a shallow glass dish",
    "sodium_tungstate": "a small heap of white sodium tungstate crystals with angular faces in a shallow glass dish",
    "solder": "a compact beveled ingot of solder, with a pale silver sheen and crisp cast edges",
    "sophora_flower": "a small bunch of dried sophora blossoms with curled petals in muted natural colors",
    "sorghum": "a small heap of natural tan and cream sorghum grains and seeds in a shallow wooden bowl",
    "soup": "a small serving of soup on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "soy_milk": "a small glass jar of soy milk with a clear view of its natural rich color and smooth texture",
    "soybean": "a small heap of pale yellow soybean grains and seeds in a shallow wooden bowl",
    "sparkling_water": "a clear glass bottle of sparkling water with transparent liquid, a few bright bubbles and a simple metal cap",
    "spear": "a compact spear with a dark metal blade or striking head, a sturdy handle and a distinct guard",
    "spice": "a small heap of fine spice powder in a shallow paper packet with a few visible grains",
    "spice_cabinet": "a compact wooden spice cabinet with visible compartments, a sturdy rim and dark iron or brass fittings",
    "spinach": "a fresh spinach with a firm edible shape, visible natural skin texture and green leafy accents",
    "spirit_level": "a compact spirit level with a dark metal casing, a clear glass or measuring feature and a sturdy grip",
    "splint": "a neatly folded splint made from sturdy woven fabric with visible seams and a clear wearable shape",
    "stable": "a compact stable built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "stage": "a compact stage built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "stainless_steel": "a compact beveled ingot of stainless steel, with a clean polished silver sheen and crisp cast edges",
    "stairs": "a compact stairs built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "starch": "a small heap of fine starch powder in a shallow paper packet with a few visible grains",
    "starch_species": "a small heap of white starch species crystals with angular faces in a shallow glass dish",
    "statin": "a small amber pharmacy bottle containing a few pale statin tablets, with an unmarked paper label",
    "steam_cell": "a small sealed steel gas cylinder containing steam, with a narrow colored glass sight window and a metal valve",
    "steam_locomotive": "a compact black steam locomotive with a short smokestack, red wheels and brass fittings",
    "steamed_bun": "a small serving of steamed bun on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "steel_armor": "a compact piece of steel armor made from polished dark steel plates, with visible straps, rivets and layered protective plates",
    "steel_axe": "a steel axe with a sturdy wooden handle and a clearly shaped working end",
    "steel_beam": "a compact bundle or folded swatch of steel beam with visible fibers, grain or woven texture",
    "steel_boots": "a compact piece of steel boots made from heavy dark steel, with visible straps, rivets and layered protective plates",
    "steel_pickaxe": "a steel pickaxe with a sturdy wooden handle and a clearly shaped working end",
    "steel_sword": "a compact steel sword with a bright steel blade or striking head, a sturdy handle and a distinct guard",
    "stew": "a small serving of stew on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "sticky_piston": "a compact wooden and iron sticky piston with a green slime-coated face",
    "stir_fry": "a small serving of stir fry on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "stone_pickaxe": "a stone pickaxe with a sturdy wooden handle and a clearly shaped working end",
    "stone_step": "a single piece of stone step showing its practical shape, natural material color and rough or worked surface",
    "stone_sword": "a compact sword with a stone blade or striking head, a sturdy handle and a distinct guard",
    "stool": "a small wooden stool with clean joinery, warm brown grain and a compact recognizable silhouette",
    "stout": "a small clear drinking glass filled with stout, showing nearly black with a tan foam liquid and a clean rounded rim",
    "straw_hat": "a neatly folded straw hat made from sturdy woven fabric with visible seams and a clear wearable shape",
    "strawberry": "a single fresh strawberry with bright red with tiny pale seeds and a clearly recognizable natural shape",
    "streptomycin": "a small amber pharmacy bottle containing a few pale streptomycin tablets, with an unmarked paper label",
    "stretcher": "a compact recognizable stretcher made from practical natural materials, with a clear functional shape and distinct surface details",
    "string": "a small coil of plain cream colored cotton string",
    "styptic_powder": "a small heap of pale beige styptic powder in a folded paper sachet",
    "sucrose": "a small amber pharmacy bottle containing a few pale sucrose tablets, with an unmarked paper label",
    "sucrose_species": "a small heap of white sucrose species crystals with angular faces in a shallow glass dish",
    "sucrose_syrup": "a small glass bottle of thick translucent sucrose syrup syrup with a warm golden tint",
    "sulfanilamide": "a small amber pharmacy bottle containing a few pale sulfanilamide tablets, with an unmarked paper label",
    "sulfur_trioxide_cell": "a small sealed steel gas cylinder containing sulfur trioxide, with a narrow colored glass sight window and a metal valve",
    "sundial": "a compact functional sundial made from dark brass and glass with a clear working feature",
    "sushi": "a small serving of sushi on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "suture_thread": "a small spool of fine pale surgical suture thread with a curved needle",
    "sweet_corn": "a fresh sweet corn with a firm edible shape, visible natural skin texture and green leafy accents",
    "sweet_potato": "a fresh sweet potato with a firm edible shape, visible natural skin texture and green leafy accents",
    "syringe": "a clear glass medical syringe with a fine steel needle and dark plunger",
    "syrup": "a small glass jar of syrup with a clear view of its natural rich color and smooth texture",
    "table": "a small wooden table with clean joinery, warm brown grain and a compact recognizable silhouette",
    "talc": "a small heap of soft pale green talc powder in a shallow paper packet",
    "tangyuan": "a small serving of tangyuan on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "tape_measure": "a compact tape measure with a dark metal casing, a clear glass or measuring feature and a sturdy grip",
    "taro": "a fresh taro with a firm edible shape, visible natural skin texture and green leafy accents",
    "tea_fresh_leaf": "a single recognizable serving of fresh tea leaf with natural ingredient colors and a clear appetizing silhouette on a small plate",
    "teapot": "a sturdy teapot with a clear opening, useful capacity and distinct wood, glass or woven material details",
    "teff": "a small heap of natural tan and cream teff grains and seeds in a shallow wooden bowl",
    "telescope": "a compact functional telescope made from dark brass and glass with a clear working feature",
    "tent": "a compact tent built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "tequila": "a small clear drinking glass filled with tequila, showing pale agave gold liquid and a clean rounded rim",
    "tetanus_antitoxin": "a small amber medicine bottle containing tetanus antitoxin liquid with a cork stopper",
    "tetracycline": "a small amber pharmacy bottle containing a few pale tetracycline tablets, with an unmarked paper label",
    "thatch": "a compact bundle or folded swatch of thatch with visible fibers, grain or woven texture",
    "thermometer": "a narrow glass thermometer in a brass frame with a red fluid column and no readable markings",
    "thread": "a compact bundle or folded swatch of thread with visible fibers, grain or woven texture",
    "throat_lozenge": "a small amber pharmacy bottle containing a few pale throat lozenge tablets, with an unmarked paper label",
    "thyroxine": "a small amber pharmacy bottle containing a few pale thyroxine tablets, with an unmarked paper label",
    "timber_beam": "a compact bundle or folded swatch of timber beam with visible fibers, grain or woven texture",
    "tin": "a compact beveled ingot of tin, with a soft pale silver sheen and crisp cast edges",
    "tin_oxide": "a small heap of white tin oxide crystals with angular faces in a shallow glass dish",
    "titanium": "a compact beveled ingot of titanium, with a cool dark silver sheen and crisp cast edges",
    "titanium_alloy": "a compact beveled ingot of titanium alloy, with a blue gray silver sheen and crisp cast edges",
    "tobacco_leaf": "a small bundle of dried tobacco leaf leaves or nuts with visible veins and muted green brown tones",
    "tobacco_shred": "a small heap of finely cut dark brown tobacco shred leaves in a shallow tin dish",
    "tofu": "a small serving of tofu on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "tofu_pudding": "a small serving of tofu pudding on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "toluene": "a small amber pharmacy bottle containing a few pale toluene tablets, with an unmarked paper label",
    "tomato": "a single fresh tomato with glossy red with a green calyx and a clearly recognizable natural shape",
    "tomato_juice": "a small clear drinking glass filled with tomato juice, showing rich tomato red liquid and a clean rounded rim",
    "tonic_water": "a clear glass bottle of tonic water with transparent liquid, a few bright bubbles and a simple metal cap",
    "toolbox": "a compact wooden toolbox with visible compartments, a sturdy rim and dark iron or brass fittings",
    "toothbrush": "a compact recognizable toothbrush made from practical natural materials, with a clear functional shape and distinct surface details",
    "toothpaste": "a compact recognizable toothpaste made from practical natural materials, with a clear functional shape and distinct surface details",
    "torch": "a short wooden torch with a wrapped cloth head and a bright orange flame",
    "tortoise_shell": "a curved amber brown tortoise shell plate with dark segmented markings",
    "tourniquet": "a short rolled medical tourniquet band with a small locking buckle",
    "trash_bin": "a compact recognizable trash bin made from practical natural materials, with a clear functional shape and distinct surface details",
    "tricycle": "a small red three wheeled pedal tricycle with a curved handlebar",
    "truck": "a compact blue cargo truck with a square cab, open bed and four dark tires",
    "tsaoko": "a few dried tsaoko fruits with wrinkled skins and deep red brown natural colors",
    "tungsten": "a compact beveled ingot of tungsten, with a dense dark gray sheen and crisp cast edges",
    "tungsten_oxide": "a small heap of white tungsten oxide crystals with angular faces in a shallow glass dish",
    "tungstic_acid": "a small heap of lemon yellow tungstic acid crystals in a shallow glass dish",
    "tunnel": "a compact tunnel built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "turmeric": "a small heap of fine turmeric powder in a shallow paper packet with a few visible grains",
    "turnip": "a fresh turnip with a firm edible shape, visible natural skin texture and green leafy accents",
    "turtle_shell": "a domed olive brown turtle shell with distinct polygonal scutes",
    "umbrella": "a closed dark green umbrella with a curved wooden handle and pointed metal tip",
    "uraninite_chunk": "a rough near black uraninite ore chunk with subtle olive metallic flecks",
    "uranium": "a few olive green metallic uranium pieces with small fractured faces",
    "uranium_tetrafluoride": "a small heap of fine uranium tetrafluoride chemical crystals in a shallow glass dish",
    "uranium_trioxide": "a small heap of fine uranium trioxide chemical crystals in a shallow glass dish",
    "vaccine": "a small amber medical bottle or kit of vaccine with clean glass, pale contents and a secure cap",
    "vancomycin": "a small amber pharmacy bottle containing a few pale vancomycin tablets, with an unmarked paper label",
    "vegetable_juice": "a small clear drinking glass filled with vegetable juice, showing deep green and red liquid and a clean rounded rim",
    "vermouth": "a small clear drinking glass filled with vermouth, showing herbal amber liquid and a clean rounded rim",
    "vet_kit": "a small amber medical bottle or kit of vet kit with clean glass, pale contents and a secure cap",
    "vitamin_tablet": "one small compressed pale vitamin medicine tablet with a shallow center score",
    "vodka": "a small clear drinking glass filled with vodka, showing clear liquid and a clean rounded rim",
    "walking_stick": "a compact recognizable walking stick made from practical natural materials, with a clear functional shape and distinct surface details",
    "war_hammer": "a compact war hammer with a dark metal blade or striking head, a sturdy handle and a distinct guard",
    "wardrobe": "a compact wooden wardrobe with visible compartments, a sturdy rim and dark iron or brass fittings",
    "wash_tub": "a compact wash tub built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "washbasin": "a compact washbasin built from aged copper and gray stone, with a clear functional opening and distinct structural details",
    "water_bottle": "a small clear glass bottle filled with transparent water and sealed with a simple metal cap",
    "water_chestnut": "a single fresh water chestnut with its recognizable natural ripe colors and a clearly recognizable natural shape",
    "water_heater": "a compact water heater built from aged copper and gray stone, with a clear functional opening and distinct structural details",
    "water_jug": "a clear or pale glass water jug with a thick rim, visible empty interior and a sturdy base",
    "water_power": "a small wooden waterwheel with broad paddles and a blue water channel",
    "water_spinach": "a fresh water spinach with a firm edible shape, visible natural skin texture and green leafy accents",
    "water_vat": "a compact water vat built from aged copper and gray stone, with a clear functional opening and distinct structural details",
    "waterproof_membrane": "a compact recognizable waterproof membrane made from practical natural materials, with a clear functional shape and distinct surface details",
    "waterskin": "a small waterskin made from woven brown fiber or leather with visible straps, handles or stitching",
    "waterwheel": "a compact waterwheel built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "wattle_fence": "a compact wattle fence built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "wax": "a small block of pale golden beeswax with a clean cut surface and a soft matte finish",
    "waxberry": "a single fresh waxberry with its recognizable natural ripe colors and a clearly recognizable natural shape",
    "waxed_canvas": "a compact recognizable waxed canvas made from practical natural materials, with a clear functional shape and distinct surface details",
    "well": "a compact well built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "wheat": "a small heap of warm golden wheat grains and seeds in a shallow wooden bowl",
    "wheat_beer": "a small clear drinking glass filled with wheat beer, showing hazy golden with a cream head liquid and a clean rounded rim",
    "wheel": "a sturdy wooden wagon wheel with eight dark spokes and a thick iron rim",
    "wheelbarrow": "a wooden wheelbarrow with a single iron wheel and a shallow cargo tray",
    "wheelchair": "a compact steel wheelchair with two large spoked wheels and a dark seat",
    "whisky": "a small clear drinking glass filled with whisky, showing deep copper amber liquid and a clean rounded rim",
    "white_dye": "a small heap of fine chalk white dye powder in a shallow dish",
    "white_peony": "a small bunch of dried white peony blossoms with curled petals in muted natural colors",
    "white_russian": "a clear cocktail glass filled with white russian, showing a distinct natural drink color and a small citrus garnish",
    "white_tea": "a small ceramic cup of white tea, with a warm dark brown surface and a delicate rising curl of steam",
    "white_wool": "a fluffy compact tuft of clean white wool fibers",
    "wild_chrysanthemum": "a small bunch of dried wild chrysanthemum blossoms with curled petals in muted natural colors",
    "wild_rice": "a small heap of dark brown wild rice grains and seeds in a shallow wooden bowl",
    "wind": "a compact wooden windmill with four pale canvas sails on a dark central shaft",
    "window": "a compact window built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "window_frame": "a compact window frame built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "wine_bottle": "a clear or pale glass wine bottle with a thick rim, visible empty interior and a sturdy base",
    "wine_glass": "a clear or pale glass wine glass with a thick rim, visible empty interior and a sturdy base",
    "wine_jar": "a compact wooden wine jar with visible compartments, a sturdy rim and dark iron or brass fittings",
    "wine_rack": "a compact wooden wine rack with visible compartments, a sturdy rim and dark iron or brass fittings",
    "winnowing_machine": "a compact recognizable winnowing machine made from practical natural materials, with a clear functional shape and distinct surface details",
    "winter_melon": "a single fresh winter melon with its recognizable natural ripe colors and a clearly recognizable natural shape",
    "wood_floor": "a compact wood floor built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "wood_shingle": "a compact wood shingle built from weathered timber and muted stone, with a clear functional opening and distinct structural details",
    "wooden_pickaxe": "a wooden pickaxe with a sturdy wooden handle and a clearly shaped working end",
    "wooden_sword": "a compact sword with a wooden blade or striking head, a sturdy handle and a distinct guard",
    "wool": "a compact bundle or folded swatch of wool with visible fibers, grain or woven texture",
    "wool_cloth": "a compact bundle or folded swatch of wool cloth with visible fibers, grain or woven texture",
    "wrench": "a iron wrench with a sturdy wooden handle and a clearly shaped working end",
    "yam": "a fresh yam with a firm edible shape, visible natural skin texture and green leafy accents",
    "yeast": "a small heap of natural tan and cream yeast grains and seeds in a shallow wooden bowl",
    "yellow_tea": "a small ceramic cup of yellow tea, with a warm dark brown surface and a delicate rising curl of steam",
    "yogurt": "a small serving of yogurt on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "zinc": "a compact beveled ingot of zinc, with a blue tinted silver sheen and crisp cast edges",
    "zinc_sulfate": "a small heap of white zinc sulfate crystals with angular faces in a shallow glass dish",
    "zither": "a small polished wooden zither with taut strings, a narrow bridge and brass tuning pegs",
    "zongzi": "a small serving of zongzi on a shallow ceramic dish, showing its distinct ingredients and appetizing natural colors",
    "zucchini": "a single fresh zucchini with dark green with pale flecks and a clearly recognizable natural shape",
    "alluvial_loam_sample": "a small labelled soil sample bag of alluvial loam, silty brown river-deposit soil with fine grit and a paper tag",
    "sandy_loam_sample": "a small labelled soil sample bag of sandy loam, pale golden coarse soil with visible sand grains and a paper tag",
    "saline_soil_sample": "a small labelled soil sample bag of saline soil, grey crusted earth with white salt efflorescence and a paper tag",
    "humus_sample": "a small labelled soil sample bag of dark humus, rich black crumbly organic soil with leaf fragments and a paper tag",
    "soil_mineral_mix": "a shallow ceramic dish of mixed mineral soil, layered grey and ochre grains with small pale mineral flecks",
    "irrigation_mineral_deposit": "a rough lump of irrigation mineral crust, chalky white and buff salt deposit with a cracked layered surface",
}

BLOCK_SUBJECTS = {
    "poor_magnetite_ore": "a stone block face with sparse small black magnetite crystals and faint rust stains",
    "magnetite_ore": "a stone block face with clear black metallic magnetite crystal clusters",
    "rich_magnetite_ore": "a stone block face densely packed with black magnetite bands and red iron oxide highlights",
    "chalcopyrite_ore": "a stone block face with brassy golden chalcopyrite crystals and slight iridescent tarnish",
    "auriferous_quartz_vein": "a stone block face cut by a milky quartz vein with tiny native gold specks",
    "bituminous_coal_seam": "a dark shale block face with glossy black bituminous coal bands",
    "anthracite_coal_seam": "a dense dark rock block face with hard shiny anthracite coal lenses",
    "kimberlite": "a green-gray volcanic breccia block face with dark mantle fragments",
    "diamondiferous_kimberlite": "a green-gray kimberlite block face with rare pale diamond glints",
    "lapis_lazuli_ore": "a stone block face with deep blue lazurite patches, white calcite and gold pyrite specks",
    "emerald_beryl_vein": "a pale pegmatite block face with green beryl crystals",
    "redstone_mineral_ore": "a dark stone block face with red mineral grains",
    "cinnabar_vein": "a pale carbonate block face cut by a vivid red cinnabar vein",
    "bauxite_laterite_deposit": "a reddish brown laterite block face with pisolitic nodules",
    "phosphorite_bed": "a sandy block face with gray-beige phosphate nodules",
    "evaporite_salt_bed": "a layered sedimentary block face with white and pink evaporite salt bands",
    "cassiterite_placer_deposit": "a sandy gravel block face with dark brown cassiterite pebbles",
    "rare_earth_carbonatite": "a pale tan carbonatite block face with white and brown carbonate veins",
    "air_inlet": "a square industrial air intake block with louvered slats and a mesh screen",
    "chimney": "a square industrial chimney block with soot-stained brick and a metal cap ring",
    "tuyere": "a square industrial tuyere block with a copper nozzle and a refractory surround",
    "oven_mouth": "a square brick oven mouth block with a dark arched opening and an iron lintel",
    "quench_tank": "a square industrial quench tank block with a dark water surface and steel rim",
    "mold": "a square casting mold block with recessed ingot cavities",
    "wire_die": "a square wire drawing die block with a hardened steel plate and a tapered hole",
    "tongs": "a square block of blacksmith tongs racked against a brick wall",
    "hammer": "a square block of a blacksmith hammer racked against a brick wall",
    "grindstone": "a square grindstone block with a round abrasive wheel and a treadle frame",
    "survey_stake": "a square survey stake block with a wooden post and a painted marker flag",
    "packed_earth": "a square block of packed earth with a firm compacted surface",
    "firebrick_block": "a square block of pale refractory firebrick with tight mortar joints",
    "clay_brick_block": "a square block of red clay brick with tight mortar joints",
    "bloomery_controller": "a square stone bloomery controller block with a tuyere port and an arched opening",
    "shaft_furnace_controller": "a square refractory shaft furnace controller block with a tap hole and a charging mouth",
    "glass_kiln_controller": "a square glass kiln controller block with a glowing firing chamber and a metal frame",
    "lime_kiln_controller": "a square lime kiln controller block with a stone arch and a draw hole",
    "pit_kiln_controller": "a square pit kiln controller block with a charred earth rim and a smoke vent",
    "bread_oven_controller": "a square bread oven controller block with a domed brick face and a peel slot",
    "carbonator": "a square carbonator block with a pressure gauge and a gas inlet",
    "pasteurizer": "a square pasteurizer block with a stainless jacket and a temperature dial",
    "coffee_grinder": "a square coffee grinder block with a hopper and a burr housing",
    "juice_press": "a square juice press block with a pressing basket and a juice spout",
    "rolling_mill": "a square rolling mill block with two steel rolls and a guide",
    "oven": "a square brick oven block with a soot-dark mouth and an iron door",
    "forge": "a square blacksmith forge block with a coal bed and a hood",
    "industrial_machine_casing": "a square modular industrial machine casing block with satin steel panels and beveled seams",
    "steel_process_pipe": "a square steel process pipe block with a flanged connector and a blue-gray finish",
    "control_panel": "a square industrial control panel block with a dark glass screen and status lights",
    "anvil": "a square blacksmith anvil block face with a heavy dark iron anvil on a stout timber stand and a broad flat working horn",
    "anvil_active": "a square blacksmith anvil block face with a glowing orange hot iron billet on its dark iron surface and a few tiny sparks",
    "bar_counter": "a square tavern counter block face built from warm oak planks with a polished serving ledge and dark iron corner straps",
    "bloomery_active": "a square stone bloomery block face with a bright orange firing mouth, soot darkened stones and a small copper tuyere",
    "bloomery_controller_active": "a square bloomery controller block face with a bright orange tuyere opening, dark refractory bricks and a small iron control plate",
    "bread_oven_active": "a square domed brick bread oven block face with a glowing orange bake chamber and a pale stone arch",
    "bread_oven_controller_active": "a square bread oven controller block face with a glowing bake opening, domed red brickwork and a short iron peel slot",
    "carpentry_bench": "a square carpentry bench block face with a honey brown worktop, dark iron vise and neatly arranged wood shavings",
    "carpentry_bench_active": "a square carpentry bench block face with a fresh plank clamped in a dark iron vise and pale curled wood shavings",
    "casting_table": "a square casting table block face with a flat dark iron top, a sand casting mold and small copper tools",
    "casting_table_active": "a square casting table block face with a newly poured orange hot metal ingot cooling inside a dark sand mold",
    "curing_barn": "a square timber curing barn block face with dark wooden slats, a small vent and a hanging bundle of cured leaves",
    "curing_barn_active": "a square timber curing barn block face with a warm drying vent, hanging cured leaf bundles and a thin curl of smoke",
    "filter_press": "a square filter press block face with stacked gray filter plates, a steel crossbeam and a small filtrate outlet",
    "filter_press_active": "a square filter press block face with compressed wet filter cakes between dark steel plates and a drip outlet",
    "glass_kiln_active": "a square glass kiln block face with a brilliant amber furnace mouth, pale refractory bricks and dark iron bands",
    "glass_kiln_controller_active": "a square glass kiln controller block face with a glowing orange firing window, refractory brick arch and a dark metal control rim",
    "herb_processing_table": "a square herb processing table block face with a clean timber worktop, dried leaves, a stone mortar and a small wooden tray",
    "herb_processing_table_active": "a square herb processing table block face with freshly cut green leaves under a wooden hand press and a stone mortar",
    "lime_kiln_active": "a square stone lime kiln block face with a pale orange firing opening, chalky limestone pieces and a soot darkened arch",
    "lime_kiln_controller_active": "a square lime kiln controller block face with a glowing firing vent, pale stonework and a dark iron draw plate",
    "loom": "a square wooden loom block face with a taut cream warp, a dark shuttle and a compact woven cloth panel",
    "loom_active": "a square wooden loom block face with a shuttle crossing taut threads and a growing strip of cream woven cloth",
    "noodle_press": "a square noodle press block face with a dark iron screw, a wooden feed tray and neat pale noodle strands",
    "noodle_press_active": "a square noodle press block face with fresh pale noodle strands emerging through a row of small steel holes",
    "oil_press": "a square timber oil press block face with a dark wooden screw, a shallow catch bowl and a few amber oil drops",
    "oil_press_active": "a square timber oil press block face with crushed seeds under a wooden screw and a thin stream of golden oil",
    "pit_kiln_active": "a square earthen pit kiln block face with a charred earth rim, glowing ember bed and a narrow smoke vent",
    "pit_kiln_controller_active": "a square pit kiln controller block face with an ember bright firing vent, charred earth rim and a short iron air pipe",
    "rice_huller": "a square rice huller block face with a wooden grain hopper, paired stone rollers and a small rice outlet",
    "rice_huller_active": "a square rice huller block face with pale rice grains flowing between paired stone rollers into a wooden catch tray",
    "shaft_furnace_active": "a square refractory shaft furnace block face with a glowing orange tap hole, dark steel bands and pale firebrick lining",
    "shaft_furnace_controller_active": "a square shaft furnace controller block face with a bright orange tap opening, an iron charging plate and refractory brickwork",
    "smelter": "a square compact smelter block face with a dark iron casing, a small refractory-lined fire mouth and a metal tap spout",
    "smelter_active": "a square compact smelter block face with a bright molten orange fire mouth, a dark iron casing and a short metal tap spout",
    "tablet_press": "a square tablet press block face with a dark steel platen, round die opening and a short lever handle",
    "tablet_press_active": "a square tablet press block face with a pale round tablet compressed in a dark steel die beneath a short lever",
    "tannery": "a square tannery block face with a wooden stretching frame, a brown hide and a small iron scraping blade",
    "tannery_active": "a square tannery block face with a wet brown hide stretched over a timber frame and a dark iron scraping tool",
    "thickener": "a square mineral thickener block face with a dark steel tank rim, a central rake shaft and a cloudy gray slurry surface",
    "thickener_active": "a square mineral thickener block face with a rotating central rake above settled gray mineral slurry and a small overflow lip",
    "water_treatment_unit": "a square water treatment block face with a blue glass inspection window, a copper pipe, a pale mineral filter and a dark valve",
    "bore_head": "a square drill head block face with a central dark steel rotary bit, six tungsten carbide teeth and a heavy bolted collar",
    "bore_head_active": "a square active drill head block face with a rotating steel bit cutting into gray stone and a few pale rock chips",
    "depleted_andesite_volcanic_belt": "a blue gray andesite block face with fine volcanic layers and only a few pale plagioclase flecks",
    "depleted_anthracite_coal_seam": "a dark shale block face with only a few thin glossy black anthracite bands",
    "depleted_anthracite_deepslate_halo": "a dark blue gray deepslate block face with faint rusty stains and sparse angular anthracite flecks",
    "depleted_auriferous_quartz_calcite_halo": "a gray carbonate rock block face with thin cream calcite patches, faint milky quartz threads and only a few tiny gold specks",
    "depleted_auriferous_quartz_vein": "a gray host rock block face cut by a narrow fractured milky quartz vein with very sparse native gold specks",
    "depleted_basalt_mafic_dike": "a dark basalt block face with a muted blue gray mafic dike and only a few scattered mineral flecks",
    "depleted_bauxite_laterite_deposit": "a rusty red laterite block face with only a few scattered pale red bauxite nodules",
    "depleted_bituminous_coal_seam": "a dark shale block face with a few thin glossy black bituminous coal bands",
    "depleted_bituminous_sandstone_halo": "a tan sandstone block face with faint brown black organic stains and sparse dark coal flecks",
    "depleted_borate_deposit": "a pale evaporite rock block face with a few chalky white borate crystals and faded salt bands",
    "depleted_cassiterite_placer_deposit": "a sandy gravel block face with only a few dark brown cassiterite pebbles among tan quartz grains",
    "depleted_chalcopyrite_granite_halo": "a pale gray granite block face with quartz and feldspar grains and only a few tiny brassy sulfide flecks",
    "depleted_chalcopyrite_vein": "a gray host rock block face cut by a narrow quartz vein with sparse brassy chalcopyrite specks",
    "depleted_chromite_lens": "a dark green ultramafic rock block face with only a few scattered black chromite grains",
    "depleted_cinnabar_vein": "a pale carbonate block face with a thin faded vermilion cinnabar seam and sparse red crystal flecks",
    "depleted_diamondiferous_kimberlite_pipe": "a green gray kimberlite breccia block face with dark mantle fragments and extremely sparse pale diamond glints",
    "depleted_diorite_intrusion": "a salt and pepper diorite block face with white plagioclase crystals and scattered dark hornblende grains",
    "depleted_emerald_beryl_vein": "a pale pegmatite block face with only a few tiny green beryl crystals and faint mica flecks",
    "depleted_evaporite_salt_bed": "a layered cream sedimentary block face with thin faded white and pale pink evaporite bands",
    "depleted_galena_lens": "a gray carbonate rock block face with sparse dull lead gray galena crystals",
    "depleted_granitic_intrusion": "a pale gray pink granite block face with coarse quartz and feldspar grains and faint mineral seams",
    "depleted_kimberlite_pipe": "a green gray kimberlite breccia block face with dark mantle clasts and sparse olive nodules",
    "depleted_kimberlite_tuff_halo": "a pale green gray volcanic tuff block face with dark kimberlite fragments and scattered mineral flecks",
    "depleted_lapis_lazuli_body": "a dark slate block face with a few muted deep blue lazurite patches and small white calcite grains",
    "depleted_limestone_calcite_bed": "a pale beige limestone block face with thin cream calcite layers and a few fossil shell fragments",
    "depleted_magnetite_lens": "a dark mafic rock block face with scattered black metallic magnetite crystals and subtle blue gray highlights",
    "depleted_magnetite_tuff_halo": "a pale gray green tuff block face with sparse black magnetite grains and faint rust specks",
    "depleted_manganite_lens": "a gray brown rock block face with sparse dark brown manganese oxide grains",
    "depleted_phosphorite_bed": "a sandy sedimentary block face with sparse gray beige phosphate nodules and faint bedding lines",
    "depleted_poor_magnetite_lens": "a dark host rock block face with extremely sparse tiny black magnetite grains",
    "depleted_rare_earth_carbonatite": "a pale tan carbonatite block face with faded carbonate veins and a few brown bastnasite grains",
    "depleted_redstone_mineral_body": "a dark stone block face with sparse crimson mineral grains and faint iron oxide seams",
    "depleted_rich_magnetite_lens": "a dark mafic rock block face with scattered black magnetite clusters and rusty red highlights, less dense than fresh rich ore",
    "depleted_sedimentary_sandstone_bed": "a layered tan sandstone block face with pale quartz grains and faint horizontal bedding",
    "depleted_sphalerite_lens": "a gray carbonate rock block face with sparse dark brown sphalerite crystals and muted honey colored flecks",
    "depleted_tuff_volcanic_belt": "a pale gray green tuff block face with soft ash layers and sparse dark angular lithic clasts",
    "depleted_uraninite_lens": "a dark gray rock block face with sparse near black uraninite grains and subtle olive green mineral flecks",
    "quarry": "a square quarry machine block face with a dark steel cutter frame, a guarded toothed wheel and pale stone rubble along the lower edge",
    "quarry_active": "a square active quarry machine block face with a rotating toothed cutter biting into gray stone and a few falling chips",
}


def load_key() -> str:
    """The local relay accepts an empty bearer token, so a missing key is not fatal."""
    if not AUTH.exists():
        return ""
    try:
        auth = json.loads(AUTH.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return ""
    return auth.get("OPENAI_API_KEY") or ""


def parse_quota_reset(body: str) -> float | None:
    """Return the seconds until the provider quota resets, parsed from the 429 body.

    The relay forwards Antigravity's message verbatim, which reads
    "Your quota will reset after 4h25m28s." No header carries this value.
    """
    match = re.search(r"reset after\s+(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?", body)
    if not match:
        return None
    hours, minutes, seconds = (int(group or 0) for group in match.groups())
    total = hours * 3600 + minutes * 60 + seconds
    return float(total) if total > 0 else None


class QuotaExhausted(RuntimeError):
    """Raised when the provider quota reset is far enough away that retrying is pointless."""

    def __init__(self, message: str, reset_seconds: float) -> None:
        super().__init__(message)
        self.reset_seconds = reset_seconds


def request_image(prompt: str, key: str, timeout: int = 300, attempts: int = 5,
                  quota_grace: float = 180.0) -> tuple[str, dict]:
    """Ask the active provider for an image, falling back down the chain on exhaustion.

    Returns a tuple of (payload, provider): the payload is either an artifact path or a data
    URL, and provider describes how to decode it.
    """
    global ACTIVE_PROVIDER

    last_error: Exception | None = None
    skipped: list[str] = []
    while ACTIVE_PROVIDER < len(PROVIDERS):
        provider = PROVIDERS[ACTIVE_PROVIDER]
        payload = {
            "model": provider["model"],
            "messages": [{"role": "user", "content": prompt}],
            "stream": False,
        }
        for attempt in range(1, attempts + 1):
            request = urllib.request.Request(
                BASE_URL + "/chat/completions",
                data=json.dumps(payload).encode("utf-8"),
                headers={"Content-Type": "application/json", "Authorization": "Bearer " + key},
                method="POST",
            )
            try:
                with urllib.request.urlopen(request, timeout=timeout) as response:
                    data = json.loads(response.read().decode("utf-8", "replace"))
                content = data["choices"][0]["message"]["content"]
                return extract_payload(content, provider), provider
            except urllib.error.HTTPError as error:
                body = ""
                try:
                    body = error.read().decode("utf-8", "replace")
                except OSError:
                    pass
                reset = parse_quota_reset(body) if error.code == 429 else None
                if reset is not None and reset > quota_grace:
                    # Hours-long exhaustion: switch providers instead of burning retries.
                    print(f"    {provider['id']}: quota exhausted, resets in "
                          f"{format_duration(reset)}; failing over")
                    skipped.append(f"{provider['id']} ({format_duration(reset)})")
                    last_error = QuotaExhausted(
                        f"{provider['id']} quota exhausted, resets in {format_duration(reset)}", reset
                    )
                    break
                last_error = RuntimeError(f"HTTP {error.code}: {body[:200] or error.reason}")
                if reset is not None:
                    print(f"    quota resets in {format_duration(reset)}, waiting")
                    time.sleep(min(reset + 5, quota_grace))
                    continue
            except (urllib.error.URLError, RuntimeError) as error:
                last_error = error
            if attempt < attempts:
                backoff = min(120.0, 20.0 * attempt)
                print(f"    retry {attempt}/{attempts} after {backoff:.0f}s ({last_error})")
                time.sleep(backoff)
        if isinstance(last_error, QuotaExhausted):
            ACTIVE_PROVIDER += 1
            continue
        break

    if ACTIVE_PROVIDER >= len(PROVIDERS):
        summary = ", ".join(skipped) or "no providers left"
        raise QuotaExhausted(f"every image provider is exhausted: {summary}", 0.0)
    raise RuntimeError(f"request failed after {attempts} attempts: {last_error}")


def extract_payload(content: str, provider: dict) -> str:
    """Pull the image out of the model reply in whatever shape the provider uses."""
    if provider["format"] == "artifact":
        match = re.search(r"\]\((/v1/opencodex/artifacts/[^)]+)\)", content)
        if not match:
            raise RuntimeError(f"no artifact link in model reply: {content[:200]}")
        return match.group(1)

    match = re.search(r"\]\((data:image/[a-z]+;base64,[A-Za-z0-9+/=]+)\)", content)
    if not match:
        raise RuntimeError(f"no inline image in model reply: {content[:200]}")
    return match.group(1)


def materialise(payload: str, provider: dict, raw: pathlib.Path,
                cut: pathlib.Path) -> pathlib.Path:
    """Write the provider payload to disk and return the RGBA source to downscale.

    The artifact path needs a chroma-key pass because the relay returns JPEG. The gpt-image
    models may or may not honour the transparency request, so an opaque reply still goes
    through the same chroma-key pass.
    """
    if provider["format"] == "artifact":
        download(payload, raw)
        cut_out(raw, cut)
        return cut

    _, _, encoded = payload.partition(",")
    data = base64.b64decode(encoded)
    raw.parent.mkdir(parents=True, exist_ok=True)
    raw.write_bytes(data)
    cut.parent.mkdir(parents=True, exist_ok=True)
    with Image.open(raw) as image:
        has_alpha = image.mode in ("RGBA", "LA")
        if has_alpha:
            alpha = image.convert("RGBA").getchannel("A")
            has_alpha = alpha.getextrema()[0] < 255
    if has_alpha:
        cut.write_bytes(data)
        return cut
    cut_out(raw, cut)
    return cut


def format_duration(seconds: float) -> str:
    total = int(seconds)
    hours, remainder = divmod(total, 3600)
    minutes, secs = divmod(remainder, 60)
    if hours:
        return f"{hours}h{minutes:02d}m{secs:02d}s"
    if minutes:
        return f"{minutes}m{secs:02d}s"
    return f"{secs}s"


ORIGIN = BASE_URL.removesuffix("/v1")


def download(artifact: str, target: pathlib.Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(ORIGIN + artifact, timeout=120) as response:
        target.write_bytes(response.read())


def cut_out(source: pathlib.Path, target: pathlib.Path) -> None:
    subprocess.run([
        sys.executable, str(REMOVER),
        "--input", str(source), "--out", str(target),
        "--auto-key", "border", "--tolerance", "60",
        "--soft-matte", "--spill-cleanup", "--force",
    ], check=True, capture_output=True,
        creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))


def downscale(source: pathlib.Path, target: pathlib.Path) -> None:
    with Image.open(source).convert("RGBA") as image:
        bbox = image.getchannel("A").getbbox()
        if bbox is None:
            raise RuntimeError(f"{source.name} has no visible pixels after chroma removal")
        cropped = image.crop(bbox)
        side = max(cropped.width, cropped.height)
        square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
        square.alpha_composite(cropped, ((side - cropped.width) // 2, (side - cropped.height) // 2))
        resized = square.resize((FINAL_SIZE, FINAL_SIZE), Image.Resampling.NEAREST)
        target.parent.mkdir(parents=True, exist_ok=True)
        resized.save(target, optimize=True)


def build_prompt(subject: str, kind: str) -> str:
    noun = "inventory item icon" if kind == "item" else "seamless square block texture"
    framing = ("The subject is centered and fills about 78% of the frame."
               if kind == "item"
               else "The block face fills the entire frame edge to edge with no perspective, "
                    "no drop shadow and no visible border.")
    return f"Create a single {noun} for a Minecraft-style mod. Subject: {subject}. {STYLE} {framing} {BACKGROUND}"


def run_batch(entries: dict[str, str], kind: str, key: str, out_dir: pathlib.Path,
              limit: int, pause: float) -> list[dict[str, object]]:
    records: list[dict[str, object]] = []
    targets = ITEM_TEXTURES if kind == "item" else BLOCK_TEXTURES
    names = list(entries)
    if ARGS.only:
        wanted = {name.strip() for name in ARGS.only.split(",") if name.strip()}
        names = [name for name in names if name in wanted]
    if ARGS.template_only:
        names = [name for name in names
                 if not (targets / f"{name}.png").exists()
                 or (targets / f"{name}.png").stat().st_size <= TEMPLATE_MAX_BYTES]
    if limit > 0:
        names = names[:limit]
    for index, name in enumerate(names, start=1):
        final = targets / f"{name}.png"
        work = out_dir / name
        raw = work.with_suffix(".raw.jpg")
        cut = work.with_suffix(".cut.png")
        record: dict[str, object] = {"id": name, "kind": kind, "target": str(final.relative_to(ROOT))}
        try:
            if name in PROTECTED:
                record["status"] = "protected"
                records.append(record)
                print(f"[{index}/{len(names)}] {name}: approved pilot icon, protected")
                continue
            if final.exists() and not ARGS.force and final.stat().st_size > TEMPLATE_MAX_BYTES:
                record["status"] = "skipped_existing"
                records.append(record)
                print(f"[{index}/{len(names)}] {name}: existing art kept, skipped")
                continue
            payload, provider = request_image(build_prompt(entries[name], kind), key)
            cut = materialise(payload, provider, raw, cut)
            downscale(cut, final)
            record["status"] = "generated"
            record["provider"] = provider["id"]
            record["model"] = provider["model"]
            if provider["format"] == "artifact":
                record["artifact"] = str(payload)
            record["bytes"] = final.stat().st_size
            print(f"[{index}/{len(names)}] {name}: generated via {provider['id']} -> {final.name}")
        except QuotaExhausted as error:
            # The provider is out for hours; stop the batch and keep what we already produced.
            record["status"] = "blocked"
            record["error"] = str(error)
            records.append(record)
            write_manifest(records, entries, kind)
            print(f"[{index}/{len(names)}] {name}: BLOCKED {error}")
            print(f"GEMINI_ART_QUOTA_BLOCKED reset_in={format_duration(error.reset_seconds)}")
            raise
        except (urllib.error.URLError, RuntimeError, subprocess.CalledProcessError, OSError) as error:
            record["status"] = "failed"
            record["error"] = str(error)[:400]
            print(f"[{index}/{len(names)}] {name}: FAILED {error}")
        records.append(record)
        write_manifest(records, entries, kind)
        if pause > 0:
            time.sleep(pause)
    return records


def load_previous_records() -> dict[tuple[str, str], dict[str, object]]:
    """Read the manifest so a new run appends to history instead of replacing it."""
    if not MANIFEST.exists():
        return {}
    try:
        data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return {}
    return {
        (record.get("kind", "item"), record.get("id")): record
        for record in data.get("records", [])
        if record.get("id")
    }


@contextmanager
def _manifest_lock():
    """Serialize manifest read/merge/write across generator processes."""
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    lock_path = MANIFEST.with_name(MANIFEST.name + ".lock")
    with lock_path.open("a+b") as lock_file:
        lock_file.seek(0, os.SEEK_END)
        if lock_file.tell() == 0:
            lock_file.write(b"\\0")
            lock_file.flush()
        if os.name == "nt":
            import msvcrt
            while True:
                try:
                    lock_file.seek(0)
                    msvcrt.locking(lock_file.fileno(), msvcrt.LK_NBLCK, 1)
                    break
                except OSError:
                    time.sleep(0.05)
            try:
                yield
            finally:
                lock_file.seek(0)
                msvcrt.locking(lock_file.fileno(), msvcrt.LK_UNLCK, 1)
        else:
            import fcntl
            fcntl.flock(lock_file.fileno(), fcntl.LOCK_EX)
            try:
                yield
            finally:
                fcntl.flock(lock_file.fileno(), fcntl.LOCK_UN)


def write_manifest(records: list[dict[str, object]], entries: dict[str, str], kind: str) -> None:
    """Merge and atomically persist progress while holding a cross-process lock."""
    targets = ITEM_TEXTURES if kind == "item" else BLOCK_TEXTURES
    with _manifest_lock():
        pending = [
            name for name in entries
            if name not in PROTECTED
            and (not (targets / f"{name}.png").exists()
                 or (targets / f"{name}.png").stat().st_size <= TEMPLATE_MAX_BYTES)
        ]
        merged = load_previous_records()
        for record in records:
            merged[(record.get("kind", kind), record.get("id"))] = record
        all_records = list(merged.values())

        generated = sum(record["status"] == "generated" for record in all_records)
        failed = sum(record["status"] == "failed" for record in all_records)
        skipped = sum(record["status"] == "skipped_existing" for record in all_records)
        protected = sum(record["status"] == "protected" for record in all_records)
        data = {
            "schema_version": 2,
            "preferred_model": PROVIDERS[0]["model"],
            "providers": [provider["model"] for provider in PROVIDERS],
            "active_provider": PROVIDERS[min(ACTIVE_PROVIDER, len(PROVIDERS) - 1)]["model"],
            "endpoint": BASE_URL + "/chat/completions",
            "final_size": FINAL_SIZE,
            "chroma_key": CHROMA,
            "generated": generated,
            "skipped": skipped,
            "failed": failed,
            "protected": protected,
            "records": all_records,
            "remaining": pending,
        }
        fd, temp_name = tempfile.mkstemp(
            prefix=MANIFEST.name + ".", suffix=".tmp", dir=MANIFEST.parent,
        )
        try:
            with os.fdopen(fd, "w", encoding="utf-8", newline="") as temp_file:
                temp_file.write(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
                temp_file.flush()
                os.fsync(temp_file.fileno())
            os.replace(temp_name, MANIFEST)
        finally:
            if os.path.exists(temp_name):
                os.unlink(temp_name)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--kind", choices=("item", "block", "both"), default="item")
    parser.add_argument("--limit", type=int, default=0, help="0 means every entry")
    parser.add_argument("--pause", type=float, default=0.5, help="seconds between requests")
    parser.add_argument("--only", default="", help="comma separated ids to process")
    parser.add_argument("--template-only", action="store_true",
                        help="only replace textures still at template size")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    global ARGS
    ARGS = parser.parse_args()

    TMP.mkdir(parents=True, exist_ok=True)
    key = load_key()
    records: list[dict[str, object]] = []
    if ARGS.dry_run:
        for kind, entries in (("item", ITEM_SUBJECTS), ("block", BLOCK_SUBJECTS)):
            if ARGS.kind not in (kind, "both"):
                continue
            for name in entries:
                print(f"{kind}: {name}")
                print("  " + build_prompt(entries[name], kind))
        return
    if ARGS.kind in ("item", "both"):
        try:
            records.extend(run_batch(ITEM_SUBJECTS, "item", key, TMP / "item", ARGS.limit, ARGS.pause))
        except QuotaExhausted as error:
            print(f"quota exhausted, reset in {format_duration(error.reset_seconds)}; stopping")
            raise SystemExit(3)
    if ARGS.kind in ("block", "both"):
        try:
            records.extend(run_batch(BLOCK_SUBJECTS, "block", key, TMP / "block", ARGS.limit, ARGS.pause))
        except QuotaExhausted as error:
            print(f"quota exhausted, reset in {format_duration(error.reset_seconds)}; stopping")
            raise SystemExit(3)

    generated = sum(record["status"] == "generated" for record in records)
    failed = sum(record["status"] == "failed" for record in records)
    skipped = sum(record["status"] == "skipped_existing" for record in records)
    print(f"GEMINI_ART generated={generated} skipped={skipped} failed={failed} manifest={MANIFEST}")


if __name__ == "__main__":
    main()
