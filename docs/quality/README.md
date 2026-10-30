# Earth on Minecraft Quality Foundation

This directory defines the evidence required before a feature is treated as complete. It complements runtime code; it is not a substitute for gameplay verification.

## Required Evidence

Every player-facing vertical slice must close all of these rows:

| Layer | Required evidence |
|---|---|
| Acquisition | Natural source, trade, loot, or recipe is reachable and documented. |
| Processing | Server-authoritative inputs, costs, outputs, blocked states, and recovery are implemented. |
| Purpose | At least one useful downstream recipe, construction use, automation role, or compatibility output exists. |
| Discovery | JEI and the field notebook identify the entry point and next step. |
| Feedback | Visual state, custom sound, particle/effect, status text, and failure reason agree. |
| Resilience | Full output, missing power, bad input, redstone pause, invalid structure, automation, and chunk reload are covered. |
| Verification | Resource audit, build, GameTest, screenshot matrix, runtime log, and deployment hash are recorded as applicable. |

## Quality Commands

```powershell
python tools\pipeline_geology_blocks.py --check
python tools\pipeline_item_icons.py --check
python tools\generate_blockbench_machine_models.py
python tools\pipeline_blockbench_models.py --check
python tools\audit_machine_audio.py --check
python tools\audit_assets.py --check
pwsh -NoProfile -File tools\render_gui_previews.ps1
python tools\validate_resources.py
cd neoforge-26.2
.\gradlew.bat verifyGuiPreviews
.\gradlew.bat clean build
cd ..
pwsh -NoProfile -File tools\run_client_smoke.ps1
```

`build` now reaches `check`, which runs all six static audit pipelines and the server-authoritative GameTest suite. `verifyGuiPreviews` remains a separate Windows visual gate because it depends on PowerShell and System.Drawing; release verification must run both commands.

`run_client_smoke.ps1` copies the disposable `runs/client/saves/ui-test` fixture into an isolated game directory, uses 26.2 Quick Play to enter it, verifies resource reload, sound, block atlas, integrated-server startup and player login from a fresh log, then closes only the scoped development client and checks graceful save/exit.

An authorized old-world smoke test uses `tools/run_world_migration_smoke.ps1` with a copied source world and copied instance mods. The script never starts the foreground client and verifies that the original `level.dat` hash is unchanged.

## Canonical Inputs

- Machine physical and presentation contracts: `data/earth_on_minecraft/earth/quality/machine_profiles.json`
- Authored route chains (outside the spec): `data/earth_on_minecraft/earth/quality/{drink,medicine,herb,crop}_routes.json`
- UI states: `docs/quality/ui-state-matrix.json`
- New feature closure contract: `docs/quality/vertical-slice-template.json`
- Runtime evidence: `docs/quality/runtime-checklist.md`
- Generated inventories and contact sheets: `output/quality/`

## Acquisition audit

The spec names several whole families with a pseudo-source and no producer (`family:drink`,
`family:medicine`, `gather:wild_herb`, `agriculture:crop`). `tools/_dead_content.py` prints every
registered item that has no producer at all, grouped by family, and is the metric to watch:

```powershell
python tools\_dead_content.py
```

Route generators that name an item or machine that does not exist are caught by
`tools/_validate_generated_routes.py`, which exits non-zero and lists the offenders.

## Asset Pipelines

The three pipelines are independent because their failure modes differ:

1. Geology blocks require periodic texture continuity, connected-block edge rules, geological identity, and cluster-scale previews.
2. Item icons require a transparent silhouette, inventory-scale readability, material-specific color, and no pseudo-text.
3. Machine/entity models require a real prototype, recognizable silhouette, coherent geometry, six-face responsibilities, state variants, collision expectations, and Blockbench source provenance.

Generated source art and final Minecraft resources must not be treated as the same artifact. Final-size previews and in-game screenshots remain mandatory.

Blockbench editable sources belong under `art/blockbench/`. The pipeline reports missing `.bbmodel` sources as pending geometry work instead of treating current cube texture references as completed models.

Every new player-facing feature gets a manifest under `docs/quality/vertical-slices/`. It may be marked complete only when acquisition, processing, purpose, JEI, handbook, feedback, failure recovery, and tests all contain concrete evidence.
