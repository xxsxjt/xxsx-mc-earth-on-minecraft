# The Earth 0.1.16 Test Beta

**Minecraft 26.1.2 / NeoForge 26.1.2.124 / Java 25 / Client and Server**

> This is an in-development test build. Use a new world or a backed-up test save. Recipes, world generation, machine balance, assets, and integration APIs may change before a stable release.

## Highlights

- Renames the project to **The Earth / 地球**. `modId`, namespace, package and artifact names are unchanged, so existing saves and packs load normally.
- Ports the whole mod to NeoForge 26.1.2.124. Five source files needed API fixes; `moddev-gradle` 2.0.148 is used for both 26.1.2 and 26.2.
- Adds a deposit depletion system. All 37 worldgen lodes now carry a finite reserve (`DepositReserve`), persist it in a `DepositBlockEntity`, report remaining capacity on right-click, and convert to one of 37 new depleted blocks when exhausted.
- Adds the **Industrial Bore Head** (`bore_head`): scans a 24-block radius, drains lode reserves, emits mining loot, and swaps spent lodes for their depleted form. 20 ticks and 24 EOU per unit.
- Adds the **Quarry** (`quarry`): works a bounded 5x5 footprint below the preserved support layer, yields normal block drops, and runs for 40 ticks and 12 EOU per unit. Live lodes consume finite reserve units and become depleted host rock rather than producing infinite ore.
- Implements all six spec multiblocks as real machines: bloomery, bread oven, glass kiln, lime kiln, charcoal pit kiln and shaft furnace, each with its own controller block, layered pattern and routes.
- Adds a machine fluid tank. Every processing machine accepts and produces the 30 spec fluids through an 8000 mB `ResourceHandler`; 16 fluid-only routes select from tank contents.
- Route count grows from 641 to **1084**. Five generators fill the families the spec left as placeholders: 83 drink routes, 70 medicine routes, 136 herb propagation routes, 129 crop routes, and 136 herbs obtainable from wild gathering.
- Gives herbs and crops a real source. Short grass, fern and large fern now drop a random herb at 12.5%; the herb processing table propagates one cutting into two.
- Adds JEI fluid rendering and a machine tank gauge with a `missing_fluid` status.
- Builds the route-tooltip index lazily so fluid-only routes and tooltip lookups during JEI registration cannot poison `RouteGuide` class initialization; JEI also lists mixed item/fluid inputs.
- Every registered item and block now has a curated art prompt. 42 block textures generated before the ledger existed are backfilled, and six soil/mineral items that were borrowing another icon got their own.

## Existing Saves

- No saved item stack is automatically converted or deleted.
- Lode blocks placed before this build gain a full reserve the first time they are read; nothing is lost.
- Machine inventories are unchanged: the fluid tank is new state stored alongside them.
- Depleted blocks are new IDs; worlds generated before this build simply have no depleted forms until a lode is worked.

## Compatibility

- Minecraft: `26.1.2`
- Loader: `NeoForge 26.1.2.124`
- Java: `25`
- Environment: client and dedicated server
- JEI: optional, tested against `29.53.0.112`

## Verification

- `compileJava`, `verifyStaticQuality` and `jar` pass. The final resource pass reports 4054 JSON files, 1698 PNGs, 119 blocks, 1312 items, 3268 models and 61 machine sound events; the material catalog validator accepts all 8 records.
- All 136 herb and 129 crop routes, plus the drink and medicine chains, were validated against the spec item list: zero unregistered items, zero unknown machine kinds.
- No client, server or GameTest process was launched while producing this build. Runtime behaviour of the bore head, quarry, depletion and the new chains is **unverified in game**.
- In-world survival timing and balance were not measured for this build.

Artifact: `earth-on-minecraft-neoforge-26.1.2-0.1.16.jar`

SHA256: `6F61DD1083F4A7614C3E6CCBA152EF6A7507D03600185E41335680F09ACE2B9F`
