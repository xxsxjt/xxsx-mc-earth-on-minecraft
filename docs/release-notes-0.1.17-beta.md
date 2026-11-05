# The Earth 0.1.17 Test Beta

**Minecraft 26.1.2 / NeoForge 26.1.2.124 / Java 25 / Client and Server**

> This is an in-development test build. Use a new world or a backed-up test save. Recipes, world generation, machine balance, assets, and integration APIs may change before a stable release.

## Highlights

Prospecting now has clues. Deposits keep their realistic structure (core, halo, host rock, finite reserves, lean near the surface and rich at depth), and the player can follow surface signs, the hammer and the scanner instead of digging blind.

- **Surface signals.** A new post-ore pass, `earth_on_minecraft:lode_surface_signal`, runs once per chunk in `underground_decoration`. When a main lode has at least 6 blocks in the chunk, the surface above its XZ centroid (`OCEAN_FLOOR_WG`, so river and sea beds count) gets a small halo-rock patch or 1-3 outcrop blocks of the lode itself, up to 3 signals per chunk. Halos and host-rock strata never trigger a signal, and signals only replace natural ground. Every signal sits over ore that exists.
- **Geology hammer prospecting.** Right-click any block to survey the 3x3 chunks around it over the full height: up to three lode types, nearest first, each with an eight-point bearing (-Z is north), horizontal distance and depth rounded to 5 blocks, block count and remaining reserve. Striking a lode block first assays that block. Each survey costs 1 durability with a 20-tick cooldown. Anvil forging is unchanged.
- **Ore scanner rework.** The scanner now covers the 5x5 chunks around the player over the full height, gives the exact east/west, up/down, north/south offset to the nearest target, and lists up to five lode types by reserve. Sneak-right-click cycles the target: all, iron, copper, gold, coal, tin, lead-zinc, gems, rare, industrial minerals. The target is stored in `minecraft:custom_data` (`EomScanFilter`). 40-tick cooldown.
- Both tools read only chunks that are already loaded and walk block-entity positions, so a survey never loads or generates terrain and never scans a cube block by block.
- **More common starter ores.** Poor magnetite chance 3 -> 2 at 0..112; magnetite 5 -> 3 at -48..48; rich magnetite 16 -> 8 (still -64..0); chalcopyrite vein 8 -> 4 at -16..112; chalcopyrite granite halo 8 -> 4; cassiterite placer 20 -> 8.
- **Six more deposits generate.** Galena, sphalerite, chromite, manganite, borate and uraninite were registered with placed features but not attached to any biome; they now generate in the Overworld with their spec values.
- **Double reserves.** `DepositSpec.RESERVE_MULTIPLIER = 2` doubles the units each lode block holds (minimum 32), so the bore head and quarry get twice as much from each block. Hand mining still drops once per block.
- A lode block no longer swallows the right-click of a hammer or scanner; empty-hand right-click still reports its reserve.

## Existing Saves

- Lode blocks whose reserve was already read keep their saved capacity; blocks read for the first time use the doubled value.
- Surface signals and the six newly attached deposits only appear in chunks generated with this build.
- Scanner stacks without a stored target default to "all".

## Compatibility

- Minecraft: `26.1.2`
- Loader: `NeoForge 26.1.2.124`
- Java: `25`
- Environment: client and dedicated server
- JEI: optional, tested against `29.53.0.112`

## Verification

- `check build` passes: `verifyStaticQuality` and the headless GameTest server.
- New GameTests cover the hammer survey (lode, bearing, rounded distance and depth, durability and cooldown), scanner targets (copper returns only chalcopyrite, "all" skips host strata, the target persists on the stack), the reserve multiplier, and the surface-signal mapping and trigger rules.
- No game client was launched. Worldgen density, signal frequency and survival balance are **unverified in game**.

Artifact: `earth-on-minecraft-neoforge-26.1.2-0.1.17.jar`

SHA256: `103F8546F60530025C309BAA54D11E25668671DA98239F1740EC462AF6B44D88`
