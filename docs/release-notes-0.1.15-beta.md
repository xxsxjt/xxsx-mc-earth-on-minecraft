# Earth on Minecraft 0.1.15 Test Beta

**Minecraft 26.2 / NeoForge 26.2.0.7-beta / Java 25 / Client and Server**

> This is an in-development test build. Use a new world or a backed-up test save. Recipes, world generation, machine balance, assets, and integration APIs may change before a stable release.

## Highlights

- Reworks the first-iron route: magnetite deposit -> Jaw Crusher -> Ball Mill -> Magnetic Separator -> iron concentrate -> coke-charged Reduction Furnace -> sponge iron -> stone-anvil forging -> wrought iron -> `minecraft:iron_ingot`.
- Retires the four magnetite chunk/dust furnace and blast-furnace recipes that produced `minecraft:iron_ingot` directly.
- Adds `sponge_iron` and `wrought_iron` item IDs with approved iron-pilot icons.
- Adds a craftable stone ironworking anvil and a 96-durability geology hammer; the anvil forges one stage per strike and consumes hammer durability.
- Adds a dedicated coke reagent slot to processing machines. Iron-concentrate reduction consumes one coke charge when the recipe completes.
- Makes coke obtainable before the first iron ingot by smelting coal in a furnace.
- Removes the first-iron progression deadlock: the first Ball Mill is stone-and-furnace crafted, the Magnetic Separator uses copper and magnetite chunks and accepts local coal fuel, and the Reduction Furnace uses bricks and a furnace with no heated multiblock requirement on this route.
- Updates bilingual handbook, tooltips, JEI hints, and route-index text to match the new iron route.
- Links settlement boards with optional Fantasy Earth and Xuanhuan Earth settlement anchors, so addon settlements share the board's technologies, industries, residents, and facility registry.
- Adds arcane and cultivation settlement profiles plus resident roles; addon settlers persist their settlement anchor and concrete role identity and appear on the matched board.
- Fantasy Earth witch hamlets, goblin exchanges, and academy outposts, and Xuanhuan Earth markets, spirit-spring villages, and sect outposts now place mod-specific workstations, stocked storage barrels, and a linked settlement board.
- Addon workstations count as arcane or cultivation facilities on the board, and their residents keep distinct roles: hedge witch, goblin appraiser, academy researcher, wandering cultivator merchant, spirit spring keeper, and sect steward.
- Extends static validation with a first-iron bootstrap check and extends the GameTest suite to cover route entries, local fuel, coke, reduction outputs, both anvil stages, and invalid workpiece rejection.

## Existing Saves

- No saved item stack is automatically converted or deleted.
- Machine inventories append the coke reagent slot after the existing fuel and seven output slots; existing input, fuel, and output slot indices do not move.
- Retired recipes disappear after a data-pack reload. Old furnace inputs that depended on them stay in place but cannot finish through the retired route.
- Settlement boards and resident records remain compatible with existing saves; addon settlement profiles are additive and require the optional addon mods to appear.

## Compatibility

- Minecraft: `26.2`
- Loader: `NeoForge 26.2.0.7-beta`
- Java: `25`
- Environment: client and dedicated server
- JEI: optional
- Optional settlement integration: Fantasy Earth `0.7.0-alpha.10`, Xuanhuan Earth `0.7.0-alpha.8`

## Verification

- Static resource, model, language, API, GUI, icon, sound, world-generation, and first-iron bootstrap validation passed; the final resource pass reports 1276 JSON files.
- Automated GameTest run passed: all 9 required tests succeeded before the final settlement-content additions; no GameTest was rerun afterward.
- Fantasy Earth and Xuanhuan Earth compile and their arcana regression checks pass.
- In-world survival timing and balance were not measured for this build.

Artifact: `earth-on-minecraft-neoforge-26.2-0.1.15.jar`

SHA256: `CCA7E4E41F7A69F3014ADB56B6FB1CFE4BE8CB523DCA246FFED5673C8CFBD840`
