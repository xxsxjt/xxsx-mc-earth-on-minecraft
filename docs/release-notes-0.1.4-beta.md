# Earth on Minecraft 0.1.4 Beta

**开发中测试版 / In-development beta.**  
This build is only intended for `Minecraft 26.2` with `NeoForge 26.2.0.7-beta`.

## Artifact

- MC: `26.2`
- Loader: `NeoForge 26.2.0.7-beta`
- Mod version: `0.1.4`
- Jar: `earth-on-minecraft-neoforge-26.2-0.1.4.jar`
- SHA256: `A1D06FE235779C3A3416AC3F150C7B9E3C52F8905A4048556070563E782C4906`

## Included In This Beta

- Keeps the 0.1.3 vanilla-ore cleanup: vanilla overworld ore placed features are removed, and vanilla large ore-vein noise is overridden so new chunks should not naturally create vanilla copper ore, iron ore, or raw ore blocks.
- Adds sedimentary/metamorphic host-rock halos for coal:
  - bituminous coal can now be accompanied by sandstone-like sedimentary host rock;
  - anthracite can now be accompanied by deepslate-like metamorphic host rock.
- Updates geology notes to explain the current spawn-rarity model, companion/host-rock status, and why real mineral abundance needs gameplay conversion instead of direct one-to-one copying.

## Known Status

- This is not a stable release.
- Existing worlds may keep already-generated vanilla ore blocks in old chunks; generate new chunks or a new test world to validate natural generation changes.
- Coal host-rock halos are still separate placed features, not a single coherent custom deposit generator. A later generator should place main ore, companion minerals, and host rock as one spatial body.
- World height is intentionally not changed in this beta. It should become a separate world preset or explicit configuration path because it affects saves and other mods.
