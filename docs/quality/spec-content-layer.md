# Spec content layer (items, blocks, components)

Date: 2026-10-29

## What is implemented

- 7 data component types registered with persistent codecs and validation:
  `food_quality`, `gem`, `herb`, `pharmaceutical`, `tool_quality`, `material_batch`, `metal_batch`.
- 1068 spec items registered from `spec/eom/items.jsonl` (all non-superseded, non-duplicate records):
  - item ids, stack sizes, zh/en names, item definitions, generated item models and textures
  - food/drink nutrition and saturation from the catalog where numeric values exist
  - default herb, gem, tool-quality, material-batch and metal-batch components attached from spec records
  - item tags from the spec merged into existing tag files
- 33 spec blocks registered from `spec/eom/blocks.jsonl`:
  - block and block-item names, blockstates, cube-all models, 128x128 textures
  - loot tables generated from the spec `drops` arrays with silk-touch alternatives
  - mineable/needs-tier tags merged
  - 6 missing worldgen lodes generated as configured + placed features and added to the overworld deposit biome modifier
- 85 vanilla crafting recipes have item coverage and all 85 recipe files exist. Three of them
  (`glass_kiln_controller`, `shaft_furnace_controller`, `steam_cracker`) list more than nine input
  items, but the generator compresses repeats into a shaped pattern, so they fit the 3x3 grid.
- A reproducible coverage report is written to `docs/quality/spec-coverage.json`.

## Verification evidence

Last full run, 2026-10-29:

- `gradlew.bat compileJava` passes.
- `gradlew.bat verifyStaticQuality` passes:
  - `ASSET_AUDIT_OK assets=5245 pipelines=6`
  - `BLOCKBENCH_PIPELINE_OK profiles=59 sources=59 pending=0`
  - `GEOLOGY_PIPELINE_OK blocks=18 models=126`
  - `ITEM_ICON_PIPELINE_OK items=1306`
  - `MACHINE_AUDIO_OK files=61 events=61`
  - `validate_resources.py`: `OK json=4045 png=1692 blocks=119 items=1312 stacks=1430
    worldgenRefs=41 machineProfiles=57 deviceProfiles=2 feedback=61`
- No client, server, GameTest or migration launch was performed for this layer.

## Art status

Every registered item and block now has a curated prompt: `ITEM_SUBJECTS` covers 1123 items and
`BLOCK_SUBJECTS` covers 131 blocks, including 41 extra depleted/quarry block placeholders that were
not in the original list. `python tools/list_pending_art.py` reports `item pending 0 / block pending 0`
against the 1200-byte template threshold.

The images are produced by `tools/generate_gemini_art.py` through the local relay. Provider order:

| Order | Model | Reply shape | Post-process |
|---|---|---|---|
| 1 | `google-antigravity/gemini-3.1-flash-image` | markdown link to a relay artifact (JPEG) | magenta chroma key |
| 2 | `AIGCPro-image/gpt-image-2.5-sunburst` | inline `data:image/png;base64` | alpha kept when present, else chroma key |
| 3 | `AIGCPro-image/gpt-image-2.5-flare` | same as sunburst | same |

Gemini is preferred for style consistency. Its 429 body carries the reset countdown
(`Your quota will reset after 4h25m28s`); the tool parses it and fails over immediately when the
reset is more than three minutes away instead of burning retries. When all three providers are
exhausted the batch stops with exit code 3 and reports the remaining time.

Approved iron-pilot icons (`sponge_iron`, `wrought_iron`) are never overwritten; their size and
timestamp were verified unchanged after the batch.

- Coverage manifest: `docs/quality/gemini-art-manifest.json` (schema 2, accumulating ledger, one
  record per icon with the provider and model that produced it).
- Contact sheet of generated icons: `python tools/make_gemini_only_sheet.py` writes
  `output/quality/gemini-art-only.png`.
- `python tools/list_pending_art.py` is read-only by default; pass `--write` to refresh
  `docs/quality/art-pending.json`. It used to rewrite that file unconditionally, which silently
  replaced a completed ledger with a stale snapshot.

## Machine routes

- 1084 routes are generated into `SpecMachineRecipes.java` on top of the curated first-iron table,
  covering all 51 registered processing machines plus the 6 multiblock controllers.
- 5 generators add the families the spec left as placeholders (drink, medicine, herb, crop, wild
  herb loot). See `docs/quality/machine-layer.md` for the table and the reasoning.
- See `docs/quality/machine-layer.md` for the legacy machine fixes, the additive slot layout,
  save migration, and the fluid tank.

## Deposit and depletion layer

Every one of the 37 spec lodes now has a finite reserve that depletes.

| Piece | Class | Behaviour |
|---|---|---|
| Reserve | `DepositReserve` | codec-backed unit count; `extract` clamps, `isEmpty`, `rollLoot` |
| Per-block state | `DepositBlockEntity` | lazily fills a full reserve on first access, saves/loads |
| Lode block | `DepositBlock` | right-click reports remaining / capacity |
| Lookup | `DepositSpec` | block to lode map, bound on `FMLCommonSetupEvent` |
| Sizing | `SpecLodeReserves` | 37 entries from `reserve_chunks x 12 / lodes_per_region`, clamped 16-512 |
| Spent form | `SpecDepletedBlocks` | 37 depleted blocks with blockstates, models, items, loot, 32x32 textures, lang |

`tools/generate_depleted_blocks.py`, `generate_lode_reserves.py`, `generate_depleted_lang.py` produce
the data; nothing about depletion is hand-written.

### Bore head (`bore_head`)

`MinerBlock` + `MinerBlockEntity`. Scans a 24-block radius for `DepositBlockEntity`, drains the
reserve, emits mining loot through `Block.getDrops`, and swaps a spent lode for its depleted form.
20 ticks and 24 EOU per unit, 64 tracked lodes maximum, rescan every 40 ticks. Requires
`RealitySupportRules.isOperational`, draws `EnergyNetwork.consume`, exposes a 9-slot container so
conveyors and hoppers can pull output.

### Quarry (`quarry`)

`QuarryBlock` + `QuarryBlockEntity`. Yields the block's own item from the rock below (sampled up to
3 blocks down), 40 ticks and 12 EOU per unit. Lodes are deliberately excluded and return cobblestone,
so a quarry is never a cheaper bore head. Every site therefore has a floor value.

## Fluid layer

- All 30 spec fluids are registered from `spec/eom/fluids.jsonl` by `tools/generate_spec_fluids.py`:
  one `FluidType` plus a still/flowing `BaseFlowingFluid` pair each, in `SpecFluids.java`.
- 60 generated 32x32 textures (`textures/block/fluid/<id>_still.png`, `_flow.png`); molten metals
  are drawn luminous (`lightLevel 12`).
- Client binding lives in `client/SpecFluidModels.java`, registered on `RegisterFluidModelsEvent`
  (26.2 replaced the old `IClientFluidTypeExtensions` texture hooks).
- Worlds cannot be flooded with these fluids yet: no `LiquidBlock` and no bucket item is
  registered, so they exist for machine transport only. `BaseFlowingFluid.getBucket()` falls back
  to air.
- Machines consume and produce them through `MachineFluidTank`, a single-slot
  `ResourceHandler<FluidResource>` (8000 mB) exposed on `Capabilities.Fluid.BLOCK` for every
  processing machine. All 18 fluid routes are emitted; the 16 fluid-only ones are selected from
  the tank contents.

## Remaining gaps

- 5 spec machines still have no block/block-entity registration: `autoclave`, `herb_dryer`,
  `pharmacy_table` and `sorter` have no spec recipes at all, and `electrolysis_brine` has a
  single retired route. All five are still `planned` in `spec/eom/machines.jsonl`, so there is
  nothing to wire yet.
- `automation_bus`, `robotic_arm` and `sorter` are logistics devices: the spec gives them
  `recipes: []` because they move items rather than transform them. They need their own block
  entity behaviour, not recipes.
- 129 registered items have no producer of any kind. `python tools/_dead_content.py` prints the
  live list and the per-family breakdown. The metric was 256 before the herb and crop chains landed.
- Wild herbs drop at 12.5% from short grass, fern and large fern. That is a first-source only;
  propagation is the intended supply.
- The 136 herb and 129 crop propagation routes are authored, not spec-sourced, so their balance
  (200-600 ticks, zero energy) is a design decision rather than a spec value.

## Generator commands

```text
python tools/generate_spec_items.py
python tools/generate_spec_blocks.py
python tools/generate_spec_vanilla_recipes.py
python tools/generate_spec_machine_recipes.py
python tools/generate_spec_machine_profiles.py
python tools/generate_spec_machine_textures.py
python tools/generate_spec_machine_resources.py
python tools/generate_spec_machine_lang.py
python tools/generate_spec_machine_power_lang.py
python tools/generate_blockbench_machine_models.py
python tools/generate_machine_feedback_assets.py
python tools/generate_spec_fluids.py
python tools/generate_drink_routes.py
python tools/generate_medicine_routes.py
python tools/generate_herb_routes.py
python tools/generate_crop_routes.py
python tools/generate_wild_herbs.py
python tools/generate_depleted_blocks.py
python tools/generate_lode_reserves.py
python tools/generate_bore_head_resources.py
python tools/generate_quarry_resources.py
python tools/generate_depleted_lang.py
python tools/spec_coverage.py
```
