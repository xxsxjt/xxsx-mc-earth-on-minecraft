# Earth on Minecraft 0.1.14 Test Beta

**Minecraft 26.2 / NeoForge 26.2.0.7-beta / Java 25 / Client and Server**

> This is an in-development test build. Use a new world or a backed-up test save. Recipes, world generation, machine balance, assets, and integration APIs may change before a stable release.

## Highlights

- Adds editable Blockbench models for 21 processing machines and 2 generators, with distinct silhouettes and non-cubic geometry based on real equipment.
- Adds separate idle, running, and fault visual states for industrial equipment.
- Adds custom machine sounds, particles, and automated audio-quality checks.
- Adds connected geology rendering so adjacent deposits visually merge without internal borders.
- Publishes integration API v1 for geology, material properties, processing, energy, logistics, and settlements.
- Expands automated quality infrastructure with asset inventories, bilingual UI state matrices, static resource validation, GameTests, screenshot fields, and migration checks.
- Adds a transparent project/mod icon and packages the complete AGPL-3.0-only license.

## Gameplay Scope

- Replaces vanilla natural ore generation with larger geology-informed deposits, host rocks, and associated minerals.
- Keeps Minecraft ingots, gems, and crafting compatibility while making natural sources, compositions, and processing chains more realistic.
- Includes mineral processing, chemical industry, electricity, logistics, nuclear-material preparation, automation components, settlements, a field guide, and optional JEI integration.

## Compatibility

- Minecraft: `26.2`
- Loader: `NeoForge 26.2.0.7-beta`
- Java: `25`
- Environment: client and dedicated server
- JEI: optional

## Verification

- Static resource, model, language, API, GUI, icon, sound, and world-generation validation passed.
- Automated GameTests and an existing-world migration test passed for the validated 0.1.14 worktree.
- A user-started client successfully loaded this mod's resources, atlases, sounds, particles, and JEI plugin with no Earth on Minecraft errors in the observed log.
- Full gameplay balance and long-term world compatibility are not yet considered stable.

Artifact: `earth-on-minecraft-neoforge-26.2-0.1.14.jar`

SHA256: `752D2AAFBB3A39CFCA5C64D7967C8B3B7F674B3A67228247FD02F7366AC16D39`
