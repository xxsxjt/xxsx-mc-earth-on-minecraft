# Iron route migration record

## Target changes in this branch

- Retire the four magnetite chunk/dust furnace and blast-furnace recipes that directly produced `minecraft:iron_ingot`.
- Change the reduction-furnace iron-concentrate output to `earth_on_minecraft:sponge_iron` plus the existing slag byproduct.
- Register `sponge_iron` and `wrought_iron` as new item IDs. Their inventory icons are imported from the approved iron-pilot drafts by `tools/import_iron_pilot_icons.py`.
- Close the survival bootstrap before the first iron ingot: the first Ball Mill is stone-and-furnace crafted; the Magnetic Separator is copper-and-magnetite crafted and accepts local coal fuel; and the Reduction Furnace is brick-and-furnace crafted without a multiblock requirement on this route.
- Smelt coal into coke, consume coke as a distinct reduction charge, then forge sponge iron to wrought iron and iron ingots at a craftable stone anvil using a durable geology hammer.
- Preserve unrelated iron-ingot uses and other metal compatibility routes; they are not blanket-rewritten by this iron-scope migration.

## Existing-save behavior

- No saved item stack is automatically converted or deleted. Existing iron ingots, magnetite chunks/dust, iron concentrate, machine inventories, furnace input/output stacks, and experience remain under their existing IDs and save data.
- Processing-machine inventories append a coke-reagent slot after the existing fuel and seven output slots; existing input, fuel, and output slot indices do not move. Legacy pre-fuel layouts continue through the existing migration path, bounded to those seven output slots.
- The retired recipes are no longer available after the data pack reload. An old furnace input that depended on one of them stays in its slot but cannot complete using that retired route; players can remove it manually or use a future supported processing path.
- No registry ID is renamed or removed in this change. The two new iron intermediate IDs are additive; existing namespace aliases are left intact.
- No bulk save rewrite is needed for this resource and additive-registry change. Any future conversion of legacy stacks or machine state requires its own versioned, repeatable migration and tests.

## Iron route and remaining validation

The first-ingot route is now wired as magnetite deposit -> stone-pick mining -> Jaw Crusher -> Ball Mill -> Magnetic Separator -> iron concentrate -> coke-charged Reduction Furnace -> sponge iron -> geology-hammer/stone-anvil wrought iron -> vanilla iron ingot. The first three route machines no longer require a prior iron ingot, grid power, or a heated multiblock. The custom anvil consumes one workpiece and one hammer durability per stage, returning the next stage in the other hand or inventory.

The automated GameTest run recorded in `neoforge-26.2/runs/gametest/logs/latest.log` on 2026-10-27 passed all 9 required tests. This includes the server-side iron-route checks for machine inputs, local fuel, coke acceptance, reduction outputs, both anvil transformations, and rejection of unrelated workpieces. It does not verify client interaction or a survival progression; no in-world interaction evidence has been collected. Magnetite deposit access and first-ingot timing should be measured in a fresh survival world before claiming a particular minute target; temperature targets still need reconciliation against the design source.

The resource validator, geology/item-icon/Blockbench/audio/asset audit pipelines, and 26-state GUI preview render passed during the 2026-10-27 continuation review. A clean Gradle build and `check` gate passed, including all 9 GameTests, before the final settlement-content additions; the final jar was rebuilt with `earth-on-minecraft-neoforge-26.2-0.1.15.jar` SHA256 `CCA7E4E41F7A69F3014ADB56B6FB1CFE4BE8CB523DCA246FFED5673C8CFBD840`. A client smoke launch reached resource reload, sound engine and block atlas, but Quick Play did not enter the `ui-test` world (`integrated_server_observed=false`); no integrated server or player join was observed, so client interaction remains unverified.

## Addon settlement integration

- Settlement boards recognize optional `fantasy_earth:settlement_anchor` and `xuanhuan_earth:settlement_anchor` blocks, bind facilities and residents to the matching addon settlement record, and display the `arcane_settlement` / `cultivation_settlement` profiles.
- Fantasy Earth and Xuanhuan Earth spawn addon residents with persisted settlement profile, anchor coordinates, and concrete role identity; their settlement features place mod-specific workstations, stocked storage barrels, and a linked settlement board.
- Addon workstations count as arcane or cultivation facilities, and addon residents keep distinct role names: hedge witch, goblin appraiser, academy researcher, wandering cultivator merchant, spirit spring keeper, and sect steward.
- The main mod and both addon mods compile; both addon `verifyArcanaBalance` regression tasks pass. The resource validator passes with 1276 JSON files. No GameTest or client run was performed after the final settlement additions.
- Cross-mod runtime behavior (board UI with both addons installed, addon resident display, and stocked structures) has not been verified in a combined client instance.

## Test deployment

- Candidate jars deployed on 2026-10-27 to `E:\_dx\_Games\MC\xxxxxx\.minecraft\versions\26.2-NeoForge_26.2.0.7-beta\mods`.
- Main `earth-on-minecraft-neoforge-26.2-0.1.15.jar`: `CCA7E4E41F7A69F3014ADB56B6FB1CFE4BE8CB523DCA246FFED5673C8CFBD840`
- Fantasy Earth `fantasy-earth-neoforge-26.2-0.7.0-alpha.10.jar`: `F7BB1582211C3B0495FD8966943700976E5A9AC03182C0AE0C42341C5F091B38`
- Xuanhuan Earth `xuanhuan-earth-neoforge-26.2-0.7.0-alpha.8.jar`: `F93B074E570E8FA491DAF7FEAD196B72B087138B527507DDBF3032F891217DF2`
- Previous jars backed up under `E:\_dx\_Games\MC\xxxxxx\.minecraft\versions\26.2-NeoForge_26.2.0.7-beta\mod-backups\20261027-205947`.
