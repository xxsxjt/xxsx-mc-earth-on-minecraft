package com.xxsx.earthonminecraft;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.xxsx.earthonminecraft.api.v1.EarthOnMinecraftApi;
import com.xxsx.earthonminecraft.component.EarthDataComponents;
import com.xxsx.earthonminecraft.component.Grade;
import com.xxsx.earthonminecraft.component.MaterialBatch;
import com.xxsx.earthonminecraft.component.MetalBatch;
import com.xxsx.earthonminecraft.component.QualityComponents;
import com.xxsx.earthonminecraft.component.ToolQualityComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

final class EarthGameTests {
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final Identifier ROUTE_CATALOG = EarthOnMinecraft.id("route_catalog");
    private static final Identifier IRON_SPONGE_REDUCTION = EarthOnMinecraft.id("iron_sponge_reduction");
    private static final Identifier ENERGY_ROUND_TRIP = EarthOnMinecraft.id("energy_round_trip");
    private static final Identifier LOGISTICS_INSERT = EarthOnMinecraft.id("logistics_insert");
    private static final Identifier GEOLOGY_QUERY = EarthOnMinecraft.id("geology_query");
    private static final Identifier STRUCTURE_FAULT_STATE = EarthOnMinecraft.id("structure_fault_state");
    private static final Identifier REALITY_SUPPORT = EarthOnMinecraft.id("reality_support");
    private static final Identifier REALITY_UTILITIES = EarthOnMinecraft.id("reality_utilities");
    private static final Identifier SCREENSHOT_FIELD = EarthOnMinecraft.id("quality_screenshot_field");
    private static final Identifier MACHINE_MULTI_INPUT = EarthOnMinecraft.id("machine_multi_input");
    private static final Identifier MACHINE_POWER_MODES = EarthOnMinecraft.id("machine_power_modes");
    private static final Identifier FLUID_LAYER = EarthOnMinecraft.id("fluid_layer");
    private static final Identifier MULTIBLOCK_ASSEMBLY = EarthOnMinecraft.id("multiblock_assembly");
    private static final Identifier DEPOSIT_RESERVE = EarthOnMinecraft.id("deposit_reserve");
    private static final Identifier ASSAY_METALLURGY_FORGING = EarthOnMinecraft.id("assay_metallurgy_forging");
    private static final Identifier CHEMICAL_CATALYST_DUAL_TANKS = EarthOnMinecraft.id("chemical_catalyst_dual_tanks");
    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_INSTANCE_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, EarthOnMinecraft.MODID);
    private static final Map<Identifier, Consumer<GameTestHelper>> TESTS = Map.ofEntries(
            Map.entry(ROUTE_CATALOG, EarthGameTests::routeCatalog),
            Map.entry(IRON_SPONGE_REDUCTION, EarthGameTests::ironSpongeReduction),
            Map.entry(ENERGY_ROUND_TRIP, EarthGameTests::energyRoundTrip),
            Map.entry(LOGISTICS_INSERT, EarthGameTests::logisticsInsert),
            Map.entry(GEOLOGY_QUERY, EarthGameTests::geologyQuery),
            Map.entry(STRUCTURE_FAULT_STATE, EarthGameTests::structureFaultState),
            Map.entry(REALITY_SUPPORT, EarthGameTests::realitySupport),
            Map.entry(REALITY_UTILITIES, EarthGameTests::realityUtilities),
            Map.entry(SCREENSHOT_FIELD, EarthGameTests::screenshotField),
            Map.entry(MACHINE_MULTI_INPUT, EarthGameTests::machineMultiInput),
            Map.entry(MACHINE_POWER_MODES, EarthGameTests::machinePowerModes),
            Map.entry(FLUID_LAYER, EarthGameTests::fluidLayer),
            Map.entry(MULTIBLOCK_ASSEMBLY, EarthGameTests::multiblockAssembly),
            Map.entry(DEPOSIT_RESERVE, EarthGameTests::depositReserve),
            Map.entry(ASSAY_METALLURGY_FORGING, EarthGameTests::assayMetallurgyForging),
            Map.entry(CHEMICAL_CATALYST_DUAL_TANKS, EarthGameTests::chemicalCatalystDualTanks));

    static {
        TEST_INSTANCE_TYPES.register("direct", () -> DirectGameTestInstance.CODEC);
    }

    private EarthGameTests() {
    }

    static void register(IEventBus modBus) {
        TEST_INSTANCE_TYPES.register(modBus);
        modBus.addListener(EarthGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                EarthOnMinecraft.id("quality"), new TestEnvironmentDefinition.AllOf());
        event.registerTest(ROUTE_CATALOG, test(ROUTE_CATALOG, environment, 20, true));
        event.registerTest(IRON_SPONGE_REDUCTION, test(IRON_SPONGE_REDUCTION, environment, 20, true, 8));
        event.registerTest(ENERGY_ROUND_TRIP, test(ENERGY_ROUND_TRIP, environment, 40, true, 8));
        event.registerTest(LOGISTICS_INSERT, test(LOGISTICS_INSERT, environment, 40, true, 8));
        event.registerTest(GEOLOGY_QUERY, test(GEOLOGY_QUERY, environment, 40, true, 8));
        event.registerTest(STRUCTURE_FAULT_STATE, test(STRUCTURE_FAULT_STATE, environment, 60, true, 8));
        event.registerTest(REALITY_SUPPORT, test(REALITY_SUPPORT, environment, 40, true, 8));
        event.registerTest(REALITY_UTILITIES, test(REALITY_UTILITIES, environment, 40, true, 8));
        TestData<Holder<TestEnvironmentDefinition<?>>> screenshotData = new TestData<>(
                environment, EMPTY_STRUCTURE, 200, 1, false, Rotation.NONE, true, 1, 1, true, 24);
        event.registerTest(SCREENSHOT_FIELD, new DirectGameTestInstance(SCREENSHOT_FIELD, screenshotData));
        event.registerTest(MACHINE_MULTI_INPUT, test(MACHINE_MULTI_INPUT, environment, 40, true, 8));
        event.registerTest(MACHINE_POWER_MODES, test(MACHINE_POWER_MODES, environment, 40, true, 8));
        event.registerTest(FLUID_LAYER, test(FLUID_LAYER, environment, 40, true, 8));
        event.registerTest(MULTIBLOCK_ASSEMBLY, test(MULTIBLOCK_ASSEMBLY, environment, 100, true, 8));
        event.registerTest(DEPOSIT_RESERVE, test(DEPOSIT_RESERVE, environment, 100, true, 8));
        event.registerTest(ASSAY_METALLURGY_FORGING, test(ASSAY_METALLURGY_FORGING, environment, 60, true, 8));
        event.registerTest(CHEMICAL_CATALYST_DUAL_TANKS, test(CHEMICAL_CATALYST_DUAL_TANKS, environment, 100, true, 12));
    }

    private static GameTestInstance test(Identifier testId, Holder<TestEnvironmentDefinition<?>> environment,
                                         int maxTicks, boolean required) {
        return test(testId, environment, maxTicks, required, 1);
    }

    /**
     * @param padding extra blocks of buildable space around the structure. The empty structure is
     *                1x1x1, so any test that places a machine or builds a multiblock needs room;
     *                writes outside the padded area are silently discarded.
     */
    private static GameTestInstance test(Identifier testId, Holder<TestEnvironmentDefinition<?>> environment,
                                         int maxTicks, boolean required, int padding) {
        return new DirectGameTestInstance(testId,
                new TestData<>(environment, EMPTY_STRUCTURE, maxTicks, 1, required,
                        Rotation.NONE, false, 1, 1, true, padding));
    }

    private static void routeCatalog(GameTestHelper helper) {
        var api = EarthOnMinecraftApi.processing();
        helper.assertValueEqual(api.machines().size(), ProcessingMachineBlock.Kind.values().length, "machine spec count");
        helper.assertTrue(!api.routes().isEmpty(), "processing route catalog must not be empty");
        for (var machine : api.machines()) {
            helper.assertTrue(machine.routeCount() > 0, "machine has no processing route: " + machine.id());
            helper.assertTrue(!api.routesFor(machine.id()).isEmpty(), "route query failed for " + machine.id());
        }
        for (var route : api.routes()) {
            // A route must produce something, but "something" can be a fluid: the shaft furnace
            // melts metal into molten metal with no item result at all.
            helper.assertTrue(route.hasOutputs(), "route has no outputs: " + route.note());
            helper.assertTrue(route.outputs().stream().allMatch(output -> output.count() > 0),
                    "route has a non-positive output: " + route.note());
            helper.assertTrue(route.fluidOutputs().stream().allMatch(fluid -> fluid.amount() > 0),
                    "route has a non-positive fluid output: " + route.note());
        }
        helper.succeed();
    }

    private static void ironSpongeReduction(GameTestHelper helper) {
        helper.assertTrue(ProcessingMachineBlock.findRecipe(ProcessingMachineBlock.Kind.CRUSHER,
                        new ItemStack(EarthOnMinecraft.MAGNETITE_ORE.get())).isPresent(),
                "magnetite ore must enter the early jaw crusher route");
        helper.assertTrue(ProcessingMachineBlock.findRecipe(ProcessingMachineBlock.Kind.BALL_MILL,
                        new ItemStack(EarthOnMinecraft.MAGNETITE_CHUNK.get())).isPresent(),
                "magnetite chunks must enter the iron-free ball mill route");
        helper.assertTrue(ProcessingMachineBlock.findRecipe(ProcessingMachineBlock.Kind.MAGNETIC_SEPARATOR,
                        new ItemStack(EarthOnMinecraft.MAGNETITE_DUST.get())).isPresent(),
                "magnetite dust must enter the coal-powered magnetic separator route");
        helper.assertTrue(ProcessingMachineBlock.Kind.MAGNETIC_SEPARATOR.acceptsLocalFuel(),
                "the first iron route must not require an EOU grid");
        helper.assertTrue(ProcessingMachineBlockEntity.getFuelTicks(new ItemStack(Items.COAL)) > 0,
                "the first-iron magnetic separator must accept ordinary coal as local fuel");
        helper.assertValueEqual(MachineMultiblock.patternFor(ProcessingMachineBlock.Kind.REDUCTION_FURNACE),
                MachineMultiblock.Pattern.NONE,
                "the first-iron reduction furnace must not require a heated multiblock");
        helper.assertValueEqual(new ItemStack(EarthOnMinecraft.GEOLOGY_HAMMER.get()).getMaxDamage(), 96,
                "geology hammer durability");
        BlockPos machinePos = new BlockPos(1, 1, 1);
        helper.setBlock(machinePos, EarthOnMinecraft.REDUCTION_FURNACE.get());
        ProcessingMachineBlockEntity machine = helper.getBlockEntity(machinePos, ProcessingMachineBlockEntity.class);
        helper.assertTrue(machine.canPlaceItem(ProcessingMachineBlockEntity.SLOT_REAGENT,
                new ItemStack(EarthOnMinecraft.COKE.get())), "reduction furnace must accept coke in its reagent slot");
        helper.assertFalse(machine.canPlaceItem(ProcessingMachineBlockEntity.SLOT_REAGENT,
                new ItemStack(Items.COAL)), "reduction reagent slot must require coke");
        var recipe = ProcessingMachineBlock.findRecipe(
                ProcessingMachineBlock.Kind.REDUCTION_FURNACE,
                new ItemStack(EarthOnMinecraft.IRON_CONCENTRATE.get())).orElseThrow(
                () -> helper.assertionException("iron concentrate has no reduction-furnace route"));
        List<ItemStack> outputs = recipe.outputStacks();
        helper.assertTrue(outputs.stream().anyMatch(stack -> stack.getItem() == EarthOnMinecraft.SPONGE_IRON.get()),
                "iron concentrate reduction must produce sponge iron");
        helper.assertTrue(outputs.stream().anyMatch(stack -> stack.getItem() == EarthOnMinecraft.SLAG.get()),
                "iron concentrate reduction must preserve the slag byproduct");
        helper.assertFalse(outputs.stream().anyMatch(stack -> stack.getItem() == Items.IRON_INGOT),
                "iron concentrate reduction must not bypass the smithing route");
        helper.assertTrue(recipe.requiresCoke(), "iron reduction route must declare a coke charge");
        ItemStack spongeIron = outputs.stream()
                .filter(stack -> stack.getItem() == EarthOnMinecraft.SPONGE_IRON.get())
                .findFirst().orElseThrow();
        ItemStack wroughtIron = IronworkingAnvilBlock.forgeResult(spongeIron).orElseThrow(
                () -> helper.assertionException("stone anvil must refine sponge iron into wrought iron"));
        helper.assertTrue(wroughtIron.getItem() == EarthOnMinecraft.WROUGHT_IRON.get(),
                "first anvil strike must produce wrought iron");
        ItemStack ironIngot = IronworkingAnvilBlock.forgeResult(wroughtIron).orElseThrow(
                () -> helper.assertionException("stone anvil must forge wrought iron into an iron ingot"));
        helper.assertTrue(ironIngot.getItem() == Items.IRON_INGOT,
                "second anvil strike must produce the vanilla iron ingot");
        helper.assertTrue(IronworkingAnvilBlock.forgeResult(new ItemStack(Items.COPPER_INGOT)).isEmpty(),
                "anvil must reject unrelated workpieces");
        helper.succeed();
    }

    private static void energyRoundTrip(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 1, 1);
        BlockPos targetPos = new BlockPos(2, 1, 1);
        helper.setBlock(sourcePos, EarthOnMinecraft.BATTERY_BOX.get());
        helper.setBlock(targetPos, EarthOnMinecraft.BATTERY_BOX.get());
        var source = EarthOnMinecraftApi.energy().findPort(helper.getLevel(), helper.absolutePos(sourcePos), Direction.UP)
                .orElseThrow(() -> helper.assertionException("missing source battery energy port"));
        var target = EarthOnMinecraftApi.energy().findPort(helper.getLevel(), helper.absolutePos(targetPos), Direction.UP)
                .orElseThrow(() -> helper.assertionException("missing target battery energy port"));
        helper.assertValueEqual(source.receiveEnergy(2_000, false), BatteryBoxBlockEntity.TRANSFER_LIMIT,
                "bounded accepted energy");
        helper.assertValueEqual(source.receiveEnergy(2_000, false), BatteryBoxBlockEntity.TRANSFER_LIMIT,
                "second bounded accepted energy");
        int totalBefore = source.getEnergyStored() + target.getEnergyStored();
        helper.assertValueEqual(EarthOnMinecraftApi.energy().transfer(source, target, 750, false),
                BatteryBoxBlockEntity.TRANSFER_LIMIT, "bounded transferred energy");
        helper.assertValueEqual(source.getEnergyStored() + target.getEnergyStored(), totalBefore,
                "energy conservation");
        helper.assertValueEqual(source.getEnergyStored(), BatteryBoxBlockEntity.TRANSFER_LIMIT,
                "source remaining energy");
        helper.assertValueEqual(target.getEnergyStored(), BatteryBoxBlockEntity.TRANSFER_LIMIT,
                "target stored energy");
        helper.succeed();
    }

    private static void logisticsInsert(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, EarthOnMinecraft.JAW_CRUSHER.get());
        var port = EarthOnMinecraftApi.logistics().findPort(helper.getLevel(), helper.absolutePos(pos), Direction.UP)
                .orElseThrow(() -> helper.assertionException("missing machine logistics port"));
        ItemStack remainder = port.insert(new ItemStack(EarthOnMinecraft.MAGNETITE_ORE.get(), 2), false);
        helper.assertTrue(remainder.isEmpty(), "machine logistics port rejected a valid input");
        ProcessingMachineBlockEntity machine = helper.getBlockEntity(pos, ProcessingMachineBlockEntity.class);
        helper.assertValueEqual(machine.getItem(ProcessingMachineBlockEntity.SLOT_INPUT).getCount(), 2,
                "inserted machine input count");
        helper.succeed();
    }

    private static void geologyQuery(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, EarthOnMinecraft.MAGNETITE_ORE.get());
        var sample = EarthOnMinecraftApi.geology().inspect(helper.getLevel(), helper.absolutePos(pos))
                .orElseThrow(() -> helper.assertionException("magnetite geology query returned empty"));
        helper.assertValueEqual(sample.formula(), "Fe3O4", "magnetite formula");
        helper.assertValueEqual(sample.role(), com.xxsx.earthonminecraft.api.v1.GeologyQueryApi.DepositRole.ORE,
                "magnetite deposit role");
        var material = EarthOnMinecraftApi.materials()
                .find(Identifier.fromNamespaceAndPath(EarthOnMinecraft.MODID, "magnetite"))
                .orElseThrow(() -> helper.assertionException("material query returned empty"));
        helper.assertValueEqual(material.compositionBasis(), "ideal_formula", "magnetite composition basis");
        helper.assertValueEqual(material.energyPolicy(), "chemical", "chemical material must not expose arcane energy");
        var matrix = com.xxsx.earthonminecraft.MaterialCatalog.find(
                        Identifier.fromNamespaceAndPath(EarthOnMinecraft.MODID, "spirit_stone_matrix"))
                .orElseThrow(() -> helper.assertionException("spirit stone matrix record missing"));
        helper.assertTrue(matrix.isMixture(), "spirit stone matrix must stay a mixture record");
        helper.assertValueEqual(matrix.constituents().size(), 4, "spirit stone matrix phase count");
        helper.assertValueEqual(matrix.constituents().get(0).fraction(), 0.80D,
                "spirit stone quartz mass fraction");
        helper.assertValueEqual(matrix.energyPolicy(), "chemical",
                "the real matrix must not claim arcane storage");
        helper.succeed();
    }

    private static void structureFaultState(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, EarthOnMinecraft.DISTILLATION_COLUMN.get());
        helper.runAfterDelay(5, () -> {
            helper.assertBlockProperty(pos, ProcessingMachineBlock.FAULT, true);
            helper.assertBlockProperty(pos, ProcessingMachineBlock.ACTIVE, false);
            helper.succeed();
        });
    }

    private static void realitySupport(GameTestHelper helper) {
        BlockPos floating = new BlockPos(1, 2, 1);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.AIR);
        helper.assertFalse(RealitySupportRules.canPlace(helper.getLevel(), helper.absolutePos(floating)),
                "floating infrastructure must not be placeable");

        helper.setBlock(new BlockPos(1, 1, 1), Blocks.SMOOTH_STONE);
        helper.assertTrue(RealitySupportRules.canPlace(helper.getLevel(), helper.absolutePos(floating)),
                "a solid foundation must support infrastructure");

        BlockPos conveyor = new BlockPos(3, 2, 1);
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.SMOOTH_STONE);
        helper.setBlock(conveyor, EarthOnMinecraft.CONVEYOR_BELT.get());
        helper.assertTrue(RealitySupportRules.isOperational(helper.getLevel(), helper.absolutePos(conveyor)),
                "grounded conveyor must be operational");
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.AIR);
        helper.assertFalse(RealitySupportRules.isOperational(helper.getLevel(), helper.absolutePos(conveyor)),
                "conveyor without foundation must stop being operational");
        helper.succeed();
    }

    private static void realityUtilities(GameTestHelper helper) {
        BlockPos cable = new BlockPos(1, 1, 1);
        BlockPos battery = new BlockPos(2, 1, 1);
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.SMOOTH_STONE);
        helper.setBlock(new BlockPos(2, 0, 1), Blocks.SMOOTH_STONE);
        helper.setBlock(cable, EarthOnMinecraft.COPPER_POWER_CABLE.get());
        helper.setBlock(battery, EarthOnMinecraft.BATTERY_BOX.get());
        helper.assertTrue(RealitySupportRules.isCableOperational(helper.getLevel(), helper.absolutePos(cable)),
                "supported cable connected to a battery must be operational");
        helper.setBlock(battery, Blocks.AIR);
        helper.assertFalse(RealitySupportRules.isCableOperational(helper.getLevel(), helper.absolutePos(cable)),
                "isolated cable must be treated as disconnected");
        helper.succeed();
    }

    private static void machineMultiInput(GameTestHelper helper) {
        // The spec route table must contain at least one multi-input route for the registered machines.
        ProcessingMachineBlock.Recipe multi = ProcessingMachineBlock.recipes().stream()
                .filter(ProcessingMachineBlock.Recipe::hasSecondaryInput)
                .findFirst()
                .orElseThrow(() -> helper.assertionException("no multi-input machine route is registered"));
        helper.assertTrue(multi.inputs().size() >= 2, "multi-input route must declare at least two inputs");
        helper.assertTrue(multi.secondaryInputs().size() == multi.inputs().size() - 1,
                "secondary inputs must exclude the primary input");
        helper.assertTrue(multi.processTicks() > 0, "route must declare a positive process time");

        var recipe = ProcessingMachineBlock.findRecipe(ProcessingMachineBlock.Kind.REDUCTION_FURNACE,
                        new ItemStack(EarthOnMinecraft.IRON_CONCENTRATE.get()))
                .orElseThrow(() -> helper.assertionException("iron concentrate route missing"));
        helper.assertTrue(recipe.requiresCoke(), "iron reduction must keep its coke charge");
        // This is the curated first-iron route, which intentionally overrides the spec route and
        // therefore runs at the machine's own duration rather than the spec's 150 ticks.
        helper.assertValueEqual(recipe.processTicks(),
                ProcessingMachineBlock.Kind.REDUCTION_FURNACE.processTicks(),
                "the curated iron reduction must use the machine duration");
        helper.assertTrue(recipe.energyPerTickEffective() > 0, "iron reduction must declare an energy cost");

        helper.assertTrue(ProcessingMachineBlockEntity.isSecondaryInputSlot(
                        ProcessingMachineBlockEntity.SLOT_SECONDARY_START),
                "first secondary slot must be recognised as an additive slot");
        helper.assertFalse(ProcessingMachineBlockEntity.isSecondaryInputSlot(
                        ProcessingMachineBlockEntity.SLOT_REAGENT),
                "reagent slot must stay distinct from additive slots");

        // The curated single-input routes must keep working after the multi-input rewrite.
        var crusher = ProcessingMachineBlock.findRecipe(ProcessingMachineBlock.Kind.CRUSHER,
                        new ItemStack(EarthOnMinecraft.MAGNETITE_ORE.get()))
                .orElseThrow(() -> helper.assertionException("curated crusher route disappeared"));
        helper.assertFalse(crusher.hasSecondaryInput(), "curated crusher route must stay single-input");
        helper.assertValueEqual(crusher.primaryCount(), 1, "curated crusher route must consume one ore");

        // A spec route that declares two inputs must report both and consume them separately.
        var twoInput = ProcessingMachineBlock.recipes().stream()
                .filter(candidate -> candidate.inputs().size() == 2)
                .findFirst()
                .orElseThrow(() -> helper.assertionException("no two-input spec route is registered"));
        helper.assertTrue(twoInput.inputs().get(1).count() >= 1,
                "second input must declare a positive count");
        helper.assertTrue(twoInput.inputStacks().size() == 2,
                "two-input route must expose two input stacks");

        BlockPos furnacePos = new BlockPos(3, 1, 1);
        helper.setBlock(furnacePos, EarthOnMinecraft.REDUCTION_FURNACE.get());
        ProcessingMachineBlockEntity furnace = helper.getBlockEntity(furnacePos, ProcessingMachineBlockEntity.class);
        helper.assertTrue(furnace.acceptsSecondaryInput(new ItemStack(EarthOnMinecraft.COKE.get())) == false,
                "coke must stay reserved for the reagent slot, not the additive slots");
        helper.succeed();
    }

    private static void machinePowerModes(GameTestHelper helper) {
        // Every registered machine must resolve to a block and a power mode.
        List<Block> machines = EarthOnMinecraft.processingMachineBlocks().stream()
                .map(holder -> (Block) holder.get()).toList();
        helper.assertValueEqual(machines.size(), ProcessingMachineBlock.Kind.values().length,
                "every machine kind must have exactly one registered block");
        helper.assertTrue(machines.size() >= 51,
                "the 21 processing machines and 30 spec stations must all be registered");

        // The six spec kilns are full multiblocks, not single-block stations.
        for (ProcessingMachineBlock.Kind kiln : List.of(
                ProcessingMachineBlock.Kind.BLOOMERY,
                ProcessingMachineBlock.Kind.BREAD_OVEN,
                ProcessingMachineBlock.Kind.GLASS_KILN,
                ProcessingMachineBlock.Kind.LIME_KILN,
                ProcessingMachineBlock.Kind.PIT_KILN,
                ProcessingMachineBlock.Kind.SHAFT_FURNACE)) {
            helper.assertTrue(MachineMultiblock.patternFor(kiln) != MachineMultiblock.Pattern.NONE,
                    kiln + " must require a built structure");
            helper.assertTrue(kiln.powerMode().requiresFuel(),
                    kiln + " must keep a fire going");
        }
        helper.assertValueEqual(MachineMultiblock.patternFor(ProcessingMachineBlock.Kind.BLOOMERY),
                MachineMultiblock.Pattern.BLOOMERY, "the bloomery must use its own layer pattern");
        helper.assertValueEqual(MachineMultiblock.patternFor(ProcessingMachineBlock.Kind.SHAFT_FURNACE),
                MachineMultiblock.Pattern.SHAFT_FURNACE, "the shaft furnace must use its own layer pattern");
        helper.assertTrue(ProcessingMachineBlock.recipesFor(ProcessingMachineBlock.Kind.SHAFT_FURNACE).size() >= 13,
                "the shaft furnace must expose its alloy and melting routes");
        helper.assertTrue(ProcessingMachineBlock.recipesFor(ProcessingMachineBlock.Kind.BLOOMERY).size() >= 3,
                "the bloomery must expose its three bloom routes");
        helper.assertTrue(ProcessingMachineBlock.recipesFor(ProcessingMachineBlock.Kind.PIT_KILN).size() >= 1,
                "the charcoal pit kiln must expose its charring route");

        // Manual and kinetic stations must not demand fuel or grid power.
        for (ProcessingMachineBlock.Kind kind : ProcessingMachineBlock.Kind.values()) {
            ProcessingMachineBlock.PowerMode mode = kind.powerMode();
            if (mode == ProcessingMachineBlock.PowerMode.MANUAL || mode == ProcessingMachineBlock.PowerMode.KINETIC) {
                helper.assertFalse(mode.grid(), kind + " must not require grid power");
                helper.assertFalse(mode.requiresFuel(), kind + " must not require a fire");
            }
        }

        // Heat-only stations keep their fire even at zero electric draw.
        helper.assertTrue(ProcessingMachineBlock.Kind.STOVE.powerMode().requiresFuel(),
                "the stove must keep a fire going");
        helper.assertTrue(ProcessingMachineBlock.Kind.SMOKEHOUSE.powerMode().requiresFuel(),
                "the smokehouse must keep a fire going");
        helper.assertFalse(ProcessingMachineBlock.Kind.STOVE.powerMode().grid(),
                "the stove must not be a grid machine");

        // The fermenting crock is the ambient process: no fuel, no grid, no manual step.
        ProcessingMachineBlock.PowerMode passive = ProcessingMachineBlock.Kind.FERMENTING_CROCK.powerMode();
        helper.assertTrue(passive.passive(), "the fermenting crock must run without a supply");
        helper.assertFalse(passive.grid(), "the fermenting crock must not need grid power");
        helper.assertFalse(passive.allowsLocalFuel(), "the fermenting crock must not take fuel");

        // Grid machines still refuse to run without power, and the first-iron separator keeps its fuel route.
        helper.assertTrue(ProcessingMachineBlock.Kind.ELECTROLYTIC_CELL.powerMode().grid(),
                "the electrolytic cell must be a grid machine");
        helper.assertFalse(ProcessingMachineBlock.Kind.ELECTROLYTIC_CELL.powerMode().manualFallback(),
                "the electrolytic cell must not fall back to hand drive");
        helper.assertTrue(ProcessingMachineBlock.Kind.MAGNETIC_SEPARATOR.acceptsLocalFuel(),
                "the first-iron separator must still take local fuel");

        // The new stations must carry their generated spec routes.
        helper.assertTrue(!ProcessingMachineBlock.recipesFor(ProcessingMachineBlock.Kind.CARPENTRY_BENCH).isEmpty(),
                "the carpentry bench must expose spec routes");
        helper.assertTrue(!ProcessingMachineBlock.recipesFor(ProcessingMachineBlock.Kind.ANVIL).isEmpty(),
                "the anvil must expose spec routes");
        helper.assertTrue(!ProcessingMachineBlock.recipesFor(ProcessingMachineBlock.Kind.LOOM).isEmpty(),
                "the loom must expose spec routes");

        // 1. Manual station must NOT advance unmanned, and MUST advance when manually operated.
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos manualPos = origin.offset(1, 1, 1);
        setAbsent(helper, manualPos.below(), Blocks.SMOOTH_STONE.defaultBlockState());
        setAbsent(helper, manualPos, EarthOnMinecraft.NOODLE_PRESS.get().defaultBlockState());
        ProcessingMachineBlockEntity manualMachine = (ProcessingMachineBlockEntity) helper.getLevel().getBlockEntity(manualPos);
        var manualRecipe = ProcessingMachineBlock.recipesFor(ProcessingMachineBlock.Kind.NOODLE_PRESS).getFirst();
        manualMachine.setItem(ProcessingMachineBlockEntity.SLOT_INPUT, manualRecipe.inputStack().copyWithCount(4));
        for (int i = 0; i < manualRecipe.secondaryInputs().size(); i++) {
            manualMachine.setItem(ProcessingMachineBlockEntity.SLOT_SECONDARY_START + i,
                    manualRecipe.secondaryInputs().get(i).stack().copyWithCount(4));
        }
        ProcessingMachineBlockEntity.serverTick(helper.getLevel(), manualPos, helper.getLevel().getBlockState(manualPos), manualMachine);
        helper.assertValueEqual(manualMachine.data().get(0), 0, "unmanned manual machine must not advance progress");
        helper.assertValueEqual(manualMachine.driveState(), ProcessingMachineBlockEntity.DriveState.MANUAL_WAIT,
                "unmanned manual machine must report MANUAL_WAIT");
        manualMachine.triggerManualWork(10);
        ProcessingMachineBlockEntity.serverTick(helper.getLevel(), manualPos, helper.getLevel().getBlockState(manualPos), manualMachine);
        helper.assertValueEqual(manualMachine.data().get(0), 1, "manually cranked machine must advance progress");

        // 2. Kinetic station must NOT advance without kinetic drive, and MUST advance with waterwheel drive.
        BlockPos kineticPos = origin.offset(3, 1, 1);
        setAbsent(helper, kineticPos.below(), Blocks.SMOOTH_STONE.defaultBlockState());
        setAbsent(helper, kineticPos, EarthOnMinecraft.CARPENTRY_BENCH.get().defaultBlockState());
        ProcessingMachineBlockEntity kineticMachine = (ProcessingMachineBlockEntity) helper.getLevel().getBlockEntity(kineticPos);
        kineticMachine.setItem(ProcessingMachineBlockEntity.SLOT_INPUT, new ItemStack(Items.OAK_LOG, 4));
        ProcessingMachineBlockEntity.serverTick(helper.getLevel(), kineticPos, helper.getLevel().getBlockState(kineticPos), kineticMachine);
        helper.assertValueEqual(kineticMachine.data().get(0), 0, "unpowered kinetic machine must not advance progress");
        helper.assertValueEqual(kineticMachine.driveState(), ProcessingMachineBlockEntity.DriveState.KINETIC_WAIT,
                "unpowered kinetic machine must report KINETIC_WAIT");
        ItemStack waterwheel = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath(EarthOnMinecraft.MODID, "waterwheel")));
        kineticMachine.setItem(ProcessingMachineBlockEntity.SLOT_FUEL, waterwheel);
        ProcessingMachineBlockEntity.serverTick(helper.getLevel(), kineticPos, helper.getLevel().getBlockState(kineticPos), kineticMachine);
        helper.assertValueEqual(kineticMachine.data().get(0), 1, "kinetic machine with waterwheel drive must advance progress");

        // 3. Thermal furnace temperature (K) gate: low-temp wood chips (900 K) cannot reach 1350 K reduction temperature;
        // high-temp coke (2100 K) preheats from 293 K and advances once temperatureK >= 1350 K.
        BlockPos thermalPos = origin.offset(5, 1, 1);
        setAbsent(helper, thermalPos.below(), Blocks.SMOOTH_STONE.defaultBlockState());
        setAbsent(helper, thermalPos, EarthOnMinecraft.REDUCTION_FURNACE.get().defaultBlockState());
        ProcessingMachineBlockEntity thermalMachine = (ProcessingMachineBlockEntity) helper.getLevel().getBlockEntity(thermalPos);
        thermalMachine.setItem(ProcessingMachineBlockEntity.SLOT_INPUT, new ItemStack(EarthOnMinecraft.IRON_CONCENTRATE.get(), 4));
        thermalMachine.setItem(ProcessingMachineBlockEntity.SLOT_REAGENT, new ItemStack(EarthOnMinecraft.COKE.get(), 4));
        thermalMachine.setItem(ProcessingMachineBlockEntity.SLOT_FUEL, new ItemStack(EarthOnMinecraft.WOOD_CHIPS.get(), 1));
        ProcessingMachineBlockEntity.serverTick(helper.getLevel(), thermalPos, helper.getLevel().getBlockState(thermalPos), thermalMachine);
        helper.assertValueEqual(thermalMachine.data().get(0), 0, "preheating furnace below required K must not advance progress");
        helper.assertValueEqual(thermalMachine.driveState(), ProcessingMachineBlockEntity.DriveState.FUEL_TEMP_LOW,
                "wood chips (900 K) in a 1350 K reduction furnace must report FUEL_TEMP_LOW");
        thermalMachine.setItem(ProcessingMachineBlockEntity.SLOT_FUEL, new ItemStack(EarthOnMinecraft.COKE.get(), 2));
        // Force burnTime reset to consume the high-grade coke fuel and heat past 1350 K.
        thermalMachine.data().set(5, 0);
        for (int t = 0; t < 60; t++) {
            ProcessingMachineBlockEntity.serverTick(helper.getLevel(), thermalPos, helper.getLevel().getBlockState(thermalPos), thermalMachine);
        }
        helper.assertTrue(thermalMachine.temperatureK() >= 1350,
                "coke-heated reduction furnace must reach >= 1350 K, got " + thermalMachine.temperatureK());
        helper.assertTrue(thermalMachine.data().get(0) > 0,
                "reduction furnace at >= 1350 K must advance recipe progress");
        helper.succeed();
    }

    private static void depositReserve(GameTestHelper helper) {
        // Every spec lode must have a reserve size and a depleted form to fall back to.
        helper.assertValueEqual(SpecLodeReserves.all().size(), 37,
                "the spec declares thirty-seven lodes");
        helper.assertValueEqual(SpecDepletedBlocks.entries().size(), 37,
                "every lode must have a depleted block");

        for (var entry : SpecDepletedBlocks.entries()) {
            Integer capacity = SpecLodeReserves.capacity(entry.lode());
            helper.assertTrue(capacity != null && capacity > 0,
                    "lode " + entry.lode() + " must have a positive reserve");
        }

        // A fresh reserve starts full and drains one unit at a time.
        DepositReserve reserve = DepositReserve.fresh(
                Identifier.fromNamespaceAndPath("earth_on_minecraft", "magnetite_lens"), 64);
        helper.assertValueEqual(reserve.remaining(), 64, "a fresh reserve starts full");
        helper.assertValueEqual(reserve.extract(1), 1, "one unit comes out");
        helper.assertValueEqual(reserve.remaining(), 63, "the reserve decrements");
        helper.assertFalse(reserve.isEmpty(), "a partly worked lode is not empty");
        helper.assertValueEqual(reserve.extract(100), 63, "extraction is clamped to what is left");
        helper.assertTrue(reserve.isEmpty(), "a fully worked lode reports empty");

        // A lode block must expose its reserve through the block entity.
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, EarthOnMinecraft.MAGNETITE_ORE.get());
        var deposit = helper.getBlockEntity(pos, DepositBlockEntity.class);
        helper.assertTrue(deposit != null, "a lode block must carry a deposit entity");
        helper.assertTrue(deposit.reserve().capacity() > 0, "the lode must have a capacity");
        helper.assertFalse(deposit.reserve().isEmpty(), "an untouched lode is not empty");

        // The depleted form named by the spec must resolve to a real block.
        var depleted = SpecDepletedBlocks.byLode(
                Identifier.fromNamespaceAndPath("earth_on_minecraft", "magnetite_lens"));
        helper.assertTrue(depleted != null, "the magnetite lens must have a depleted block");

        // The bore head itself must exist and require support like any other machine.
        helper.setBlock(new BlockPos(5, 1, 5), EarthOnMinecraft.MINER.get());
        var miner = helper.getBlockEntity(new BlockPos(5, 1, 5), MinerBlockEntity.class);
        helper.assertTrue(miner != null, "the bore head must carry its block entity");
        helper.assertValueEqual(miner.getContainerSize(), MinerBlockEntity.OUTPUT_SLOTS,
                "the bore head exposes its output buffer as a container");
        helper.succeed();
    }

    /** Writes a block state at an absolute position, bypassing the GameTest structure bounds. */
    private static void setAbsent(GameTestHelper helper, BlockPos absolute, BlockState state) {
        helper.getLevel().setBlock(absolute, state, 3);
    }

    /**
     * Diagnostic dump for a failed kiln assembly: what each requirement resolved to, and what block
     * was actually there. Without it the failure only says "not complete", which is impossible to
     * act on.
     */
    private static String describeKiln(GameTestHelper helper, BlockPos controller) {
        StringBuilder text = new StringBuilder("controller=").append(controller)
                .append(" block=").append(helper.getLevel().getBlockState(controller).getBlock())
                .append(" placements=[");
        for (MachineMultiblock.PlacedRequirement requirement
                : MachineMultiblock.requirementsFor(ProcessingMachineBlock.Kind.LIME_KILN,
                net.minecraft.core.Direction.NORTH)) {
            BlockPos pos = controller.offset(requirement.x(), requirement.y(), requirement.z());
            text.append(requirement.role())
                    .append('@').append(pos.getX()).append(',').append(pos.getY())
                    .append(',').append(pos.getZ())
                    .append('=').append(helper.getLevel().getBlockState(pos).getBlock())
                    .append("; ");
        }
        return text.append(']').toString();
    }

    private static void multiblockAssembly(GameTestHelper helper) {
        // Build the 2x2x4 lime kiln from the same layer pattern the matcher uses.
        //
        // `helper.setBlock` refuses anything outside `getRelativeBounds()`, and the shared
        // `minecraft:empty` structure is 1x1x1, so every placement was silently dropped and the
        // whole kiln read back as air. Writing through the level directly is the only way to build
        // a multi-block structure without shipping a structure template.
        BlockPos controller = new BlockPos(1, 1, 1);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);

        setAbsent(helper, origin.offset(controller), EarthOnMinecraft.LIME_KILN.get().defaultBlockState());

        Block shell = SpecBlocks.BY_ID.get("firebrick_block").get();
        Block chimney = SpecBlocks.BY_ID.get("chimney").get();

        for (MachineMultiblock.PlacedRequirement requirement
                : MachineMultiblock.requirementsFor(ProcessingMachineBlock.Kind.LIME_KILN,
                net.minecraft.core.Direction.NORTH)) {
            BlockPos pos = controller.offset(requirement.x(), requirement.y(), requirement.z());
            Block block = "PIPE".equals(requirement.role()) ? chimney : shell;
            setAbsent(helper, origin.offset(pos), block.defaultBlockState());
        }

        helper.assertTrue(MachineMultiblock.isComplete(helper.getLevel(), origin.offset(controller),
                        ProcessingMachineBlock.Kind.LIME_KILN),
                "the assembled 2x2x4 lime kiln must register as complete; "
                        + describeKiln(helper, origin.offset(controller)));

        // Breaking one shell block must invalidate the structure again.
        BlockPos brokenShell = MachineMultiblock.requirementsFor(
                        ProcessingMachineBlock.Kind.LIME_KILN, net.minecraft.core.Direction.NORTH)
                .stream()
                .filter(requirement -> "CASING".equals(requirement.role()))
                .map(requirement -> controller.offset(requirement.x(), requirement.y(), requirement.z()))
                .filter(pos -> !pos.equals(controller))
                .findFirst().orElseThrow();
        setAbsent(helper, origin.offset(brokenShell), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        helper.assertFalse(MachineMultiblock.isComplete(helper.getLevel(), origin.offset(controller),
                        ProcessingMachineBlock.Kind.LIME_KILN),
                "removing a shell block must break the structure");
        var kilnDiag = MachineMultiblock.diagnose(helper.getLevel(), origin.offset(controller),
                ProcessingMachineBlock.Kind.LIME_KILN).orElseThrow(
                () -> helper.assertionException("broken lime kiln must produce a coordinate diagnostic"));
        helper.assertValueEqual(kilnDiag.worldPos(), origin.offset(brokenShell),
                "diagnostic must pinpoint the exact missing shell coordinate");
        helper.assertValueEqual(kilnDiag.expectedRole(), MachineMultiblock.PartRole.CASING,
                "diagnostic must report the missing casing role");

        // Build a modern 3D layered industrial multiblock (Distillation Column / TALL_COLUMN).
        BlockPos colController = new BlockPos(5, 1, 5);
        setAbsent(helper, origin.offset(colController), EarthOnMinecraft.DISTILLATION_COLUMN.get().defaultBlockState());
        for (MachineMultiblock.PlacedRequirement req
                : MachineMultiblock.requirementsFor(ProcessingMachineBlock.Kind.DISTILLATION_COLUMN,
                net.minecraft.core.Direction.NORTH)) {
            BlockPos pos = colController.offset(req.x(), req.y(), req.z());
            Block partBlock = switch (req.role()) {
                case "CONTROL_PANEL" -> EarthOnMinecraft.CONTROL_PANEL.get();
                case "PIPE" -> EarthOnMinecraft.STEEL_PROCESS_PIPE.get();
                default -> EarthOnMinecraft.INDUSTRIAL_MACHINE_CASING.get();
            };
            setAbsent(helper, origin.offset(pos), partBlock.defaultBlockState());
        }
        helper.assertTrue(MachineMultiblock.isComplete(helper.getLevel(), origin.offset(colController),
                        ProcessingMachineBlock.Kind.DISTILLATION_COLUMN),
                "the 3D layered distillation column must register as complete");
        BlockPos topPipe = MachineMultiblock.requirementsFor(
                        ProcessingMachineBlock.Kind.DISTILLATION_COLUMN, net.minecraft.core.Direction.NORTH)
                .stream()
                .filter(req -> "PIPE".equals(req.role()) && req.y() == 3)
                .map(req -> colController.offset(req.x(), req.y(), req.z()))
                .findFirst().orElseThrow();
        setAbsent(helper, origin.offset(topPipe), Blocks.COBBLESTONE.defaultBlockState());
        helper.assertFalse(MachineMultiblock.isComplete(helper.getLevel(), origin.offset(colController),
                        ProcessingMachineBlock.Kind.DISTILLATION_COLUMN),
                "replacing a top reflux pipe with cobblestone must invalidate the distillation column");
        var colDiag = MachineMultiblock.diagnose(helper.getLevel(), origin.offset(colController),
                ProcessingMachineBlock.Kind.DISTILLATION_COLUMN).orElseThrow();
        helper.assertValueEqual(colDiag.worldPos(), origin.offset(topPipe),
                "distillation column diagnostic must report the exact wrong pipe coordinate");
        helper.assertTrue(colDiag.wrongBlock(), "cobblestone in a pipe position must be flagged as wrongBlock");
        helper.assertValueEqual(colDiag.actualBlock(), Blocks.COBBLESTONE,
                "diagnostic must report the actual wrong block found");

        // A single-block station must never claim a multiblock pattern.
        helper.assertValueEqual(MachineMultiblock.patternFor(ProcessingMachineBlock.Kind.ANVIL),
                MachineMultiblock.Pattern.NONE,
                "the anvil is a single-block station");
        helper.succeed();
    }

    private static void fluidLayer(GameTestHelper helper) {
        // Every spec fluid must be registered with a still and a flowing variant.
        helper.assertValueEqual(SpecFluids.entries().size(), 30,
                "the spec lists thirty fluids");
        helper.assertValueEqual(SpecFluids.registered().size(), 30,
                "every spec fluid must be registered");
        for (SpecFluids.Registered fluid : SpecFluids.registered().values()) {
            helper.assertTrue(fluid.source() != null && fluid.source().isBound(),
                    "missing still fluid for " + fluid.type().getId());
            helper.assertTrue(fluid.flowing() != null && fluid.flowing().isBound(),
                    "missing flowing fluid for " + fluid.type().getId());
            helper.assertTrue(fluid.source().get() != fluid.flowing().get(),
                    "still and flowing must be distinct fluids for " + fluid.type().getId());
        }

        // The tank must accept the spec's cast amount (144 mB per ingot) and reject a foreign fluid.
        MachineFluidTank tank = new MachineFluidTank(8000);
        helper.assertTrue(tank.isEmpty(), "a new tank must start empty");
        helper.assertValueEqual(tank.fill("earth_on_minecraft:molten_iron", 144), 144,
                "the tank must accept a spec ingot cast");
        helper.assertTrue(tank.holds("earth_on_minecraft:molten_iron", 144),
                "the tank must hold the fluid it just accepted");
        helper.assertValueEqual(tank.fill("earth_on_minecraft:molten_copper", 144), 0,
                "the tank must reject mixing two fluids");
        helper.assertValueEqual(tank.drain("earth_on_minecraft:molten_iron", 144), 144,
                "the tank must drain the stored fluid");
        helper.assertTrue(tank.isEmpty(), "draining the last of a fluid must empty the tank");

        // Fluid routes must be attached to their machines and keep their spec amounts.
        var casting = ProcessingMachineBlock.recipesFor(ProcessingMachineBlock.Kind.CASTING_TABLE)
                .stream().filter(ProcessingMachineBlock.Recipe::usesFluid).toList();
        helper.assertTrue(casting.size() >= 14,
                "the casting table must expose the molten-metal casting routes");
        ProcessingMachineBlock.Recipe cast = casting.getFirst();
        helper.assertTrue(!cast.fluidInputs().isEmpty(), "a fluid route must declare a fluid input");
        helper.assertValueEqual(cast.fluidInputs().getFirst().amount(), 144,
                "a molten-metal cast must consume one spec ingot worth of metal");

        // A machine with a fluid route must carry a tank and expose it through the item-free path.
        BlockPos pos = new BlockPos(5, 1, 1);
        helper.setBlock(pos, EarthOnMinecraft.CASTING_TABLE.get());
        ProcessingMachineBlockEntity machine = helper.getBlockEntity(pos, ProcessingMachineBlockEntity.class);
        helper.assertValueEqual(machine.tank().capacity(), ProcessingMachineBlockEntity.TANK_CAPACITY,
                "the machine tank must use the shared capacity");
        helper.assertTrue(machine.tank().isEmpty(), "a fresh machine tank must be empty");

        // Fluid-only routes must be selectable from tank contents alone.
        int moltenIronRoutes = ProcessingMachineBlock.fluidOnlyRecipes(
                ProcessingMachineBlock.Kind.CASTING_TABLE, "earth_on_minecraft:molten_iron").size();
        helper.assertTrue(moltenIronRoutes == 1,
                "molten iron must select exactly one casting route, got " + moltenIronRoutes);
        helper.assertTrue(
                ProcessingMachineBlock.matchingRecipes(ProcessingMachineBlock.Kind.CASTING_TABLE,
                        new ItemStack(Items.IRON_INGOT)).isEmpty(),
                "an ingot must not select the fluid-only casting route");

        // Adjacent fluid-producing machines must auto-transfer into fluid-consuming machines.
        BlockPos furnacePos = new BlockPos(4, 1, 1);
        helper.setBlock(furnacePos, EarthOnMinecraft.SHAFT_FURNACE.get());
        ProcessingMachineBlockEntity furnace = helper.getBlockEntity(furnacePos, ProcessingMachineBlockEntity.class);
        furnace.tank().fill("earth_on_minecraft:molten_iron", 288);
        helper.assertTrue(furnace.tryExportFluidToNeighbors(helper.getLevel(), helper.absolutePos(furnacePos)),
                "shaft furnace must export molten iron into an adjacent casting table");
        helper.assertValueEqual(machine.tank().amount(), 288,
                "casting table must receive the exported molten iron");
        helper.assertTrue(furnace.tank().isEmpty(),
                "shaft furnace tank must empty after exporting to the casting table");

        // All machine routes must fit within standard 64-item stacks.
        for (ProcessingMachineBlock.Recipe route : ProcessingMachineBlock.recipes()) {
            for (ProcessingMachineBlock.Input input : route.inputs()) {
                helper.assertTrue(input.count() <= 64,
                        "route input exceeds stack limit 64: " + route.sourceId() + " (" + input.count() + ")");
            }
            for (ProcessingMachineBlock.Output output : route.outputs()) {
                helper.assertTrue(output.count() <= 64,
                        "route output exceeds stack limit 64: " + route.sourceId() + " (" + output.count() + ")");
            }
        }
        helper.succeed();
    }

    private static void screenshotField(GameTestHelper helper) {
        List<Block> machines = EarthOnMinecraft.processingMachineBlocks().stream().map(holder -> (Block) holder.get()).toList();
        for (int index = 0; index < machines.size(); index++) {
            int x = 1 + index % 7 * 3;
            int z = 1 + index / 7 * 4;
            int stateRow = index / 7;
            boolean active = stateRow == 1;
            boolean fault = stateRow == 2;
            Block block = machines.get(index);
            var state = block.defaultBlockState();
            if (state.hasProperty(ProcessingMachineBlock.ACTIVE)) {
                state = state.setValue(ProcessingMachineBlock.ACTIVE, active);
            }
            if (state.hasProperty(ProcessingMachineBlock.FAULT)) {
                state = state.setValue(ProcessingMachineBlock.FAULT, fault);
            }
            helper.setBlock(new BlockPos(x, 1, z), state);
            helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
        }

        List<Block> ores = EarthOnMinecraft.connectedOreBlocks().stream().map(holder -> (Block) holder.get()).toList();
        for (int index = 0; index < ores.size(); index++) {
            int x = 1 + index % 6 * 3;
            int z = 15 + index / 6 * 3;
            Block ore = ores.get(index);
            helper.setBlock(new BlockPos(x, 1, z), ore);
            helper.setBlock(new BlockPos(x + 1, 1, z), ore);
            helper.setBlock(new BlockPos(x, 1, z + 1), ore);
            helper.setBlock(new BlockPos(x + 1, 1, z + 1), ore);
        }

        helper.setBlock(new BlockPos(23, 1, 1), EarthOnMinecraft.COMBUSTION_GENERATOR.get());
        helper.setBlock(new BlockPos(23, 1, 3), EarthOnMinecraft.BATTERY_BOX.get());
        helper.setBlock(new BlockPos(23, 1, 5), EarthOnMinecraft.COPPER_POWER_CABLE.get());
        helper.setBlock(new BlockPos(23, 1, 7), EarthOnMinecraft.CONVEYOR_BELT.get());
        helper.succeed();
    }

    private static void assayMetallurgyForging(GameTestHelper helper) {
        // 1. Expanded MaterialCatalog coverage for ores, concentrates, metals & chemicals
        helper.assertTrue(MaterialCatalog.find(EarthOnMinecraft.id("magnetite_ore")).isPresent(),
                "magnetite_ore must be in MaterialCatalog");
        helper.assertTrue(MaterialCatalog.find(EarthOnMinecraft.id("iron_concentrate")).isPresent(),
                "iron_concentrate must be in MaterialCatalog");
        helper.assertTrue(MaterialCatalog.find(EarthOnMinecraft.id("sponge_iron")).isPresent(),
                "sponge_iron must be in MaterialCatalog");
        helper.assertTrue(MaterialCatalog.find(EarthOnMinecraft.id("high_purity_silicon")).isPresent(),
                "high_purity_silicon must be in MaterialCatalog");
        helper.assertTrue(MaterialCatalog.find(EarthOnMinecraft.id("methanol")).isPresent(),
                "methanol must be in MaterialCatalog");

        // 2. Sponge Iron -> Wrought Iron slag expulsion & C < 0.02% decarburization
        ItemStack sponge = new ItemStack(EarthOnMinecraft.SPONGE_IRON.get());
        MetalBatch spongeMetal = sponge.get(EarthDataComponents.METAL_BATCH.get());
        helper.assertTrue(spongeMetal != null, "sponge iron must carry default MetalBatch");
        helper.assertTrue(spongeMetal.slagContent() >= 0.10,
                "sponge iron must start with high slag (>=10%), got " + spongeMetal.slagContent());
        helper.assertValueEqual(spongeMetal.ironCarbonClass(), "sponge_iron", "sponge iron metallurgical class");

        ItemStack masterHammer = new ItemStack(EarthOnMinecraft.GEOLOGY_HAMMER.get());
        masterHammer.set(EarthDataComponents.TOOL_QUALITY.get(),
                new ToolQualityComponent(1, Grade.MASTERWORK, "test_smith", 0.9));
        ItemStack wrought = IronworkingAnvilBlock.forgeResult(sponge, masterHammer, 1280)
                .orElseThrow(() -> helper.assertionException("sponge iron forging failed"));
        MetalBatch wroughtMetal = wrought.get(EarthDataComponents.METAL_BATCH.get());
        MaterialBatch wroughtBatch = wrought.get(EarthDataComponents.MATERIAL_BATCH.get());
        helper.assertTrue(wroughtMetal != null && wroughtBatch != null,
                "forged wrought iron must carry updated MetalBatch and MaterialBatch");
        helper.assertTrue(wroughtMetal.slagContent() < 0.01,
                "anvil forging must expel slag below 1%, got " + wroughtMetal.slagContent());
        helper.assertTrue(wroughtMetal.carbonContent() < 0.0002,
                "wrought iron must have C < 0.02% (0.0002), got " + wroughtMetal.carbonContent());
        helper.assertTrue(wroughtMetal.fractionOf("Fe") > 0.99,
                "forged wrought iron must exceed 99% Fe purity, got " + wroughtMetal.fractionOf("Fe"));
        helper.assertValueEqual(wroughtMetal.ironCarbonClass(), "wrought_iron",
                "forged wrought iron must classify as wrought_iron");
        helper.assertTrue(wroughtBatch.grade().tier() >= Grade.FINE.tier(),
                "hot-forged wrought iron with master hammer must upgrade to >= FINE, got " + wroughtBatch.grade());

        // 3. Iron-Carbon windows & S/P impurity cap
        MetalBatch cleanSteel = MetallurgyAssay.createIronCarbonBatch(0.010, 0.0002, 0.0002, 0.0008);
        helper.assertValueEqual(cleanSteel.ironCarbonClass(), "steel", "1.0% C batch must classify as steel");
        helper.assertTrue(cleanSteel.maxGradeCap().tier() >= Grade.MASTERWORK.tier(),
                "low S/P clean steel must allow MASTERWORK/HEIRLOOM grade");

        MetalBatch highSulfurSteel = MetallurgyAssay.createIronCarbonBatch(0.010, 0.0040, 0.0025, 0.0050);
        helper.assertValueEqual(highSulfurSteel.ironCarbonClass(), "steel", "high-S steel still in steel C window");
        helper.assertValueEqual(highSulfurSteel.maxGradeCap(), Grade.CRUDE,
                "high S+P (>0.5%) must cap metallurgical grade at CRUDE");

        MetalBatch pigIronBatch = MetallurgyAssay.createIronCarbonBatch(0.038, 0.0010, 0.0015, 0.0020);
        helper.assertValueEqual(pigIronBatch.ironCarbonClass(), "pig_iron", "3.8% C (>2.1%) must classify as pig_iron");

        // 4. Steel Bloom -> Steel -> Folding -> Tool Quality propagation
        ItemStack bloom = new ItemStack(EarthOnMinecraft.STEEL_BLOOM.get());
        bloom.set(EarthDataComponents.METAL_BATCH.get(),
                MetallurgyAssay.createIronCarbonBatch(0.011, 0.0002, 0.0002, 0.0040));
        bloom.set(EarthDataComponents.MATERIAL_BATCH.get(),
                MaterialBatch.defaultFor("steel_bloom").withPurityAndGrade(0.975, Grade.FINE).withTemperatureK(1300));
        ItemStack forgedSteel = IronworkingAnvilBlock.forgeResult(bloom, masterHammer, 1300)
                .orElseThrow(() -> helper.assertionException("steel bloom forging failed"));
        ItemStack foldedSteel = IronworkingAnvilBlock.forgeResult(forgedSteel, masterHammer, 1320)
                .orElseThrow(() -> helper.assertionException("steel folding failed"));
        MaterialBatch foldedBatch = foldedSteel.get(EarthDataComponents.MATERIAL_BATCH.get());
        helper.assertTrue(foldedBatch.grade().tier() >= Grade.MASTERWORK.tier(),
                "folded clean steel at 1320 K must reach MASTERWORK or HEIRLOOM, got " + foldedBatch.grade());

        var pickaxeRoute = ProcessingMachineBlock.recipesFor(ProcessingMachineBlock.Kind.ANVIL).stream()
                .filter(r -> r.outputs().stream().anyMatch(o ->
                        BuiltInRegistries.ITEM.getKey(o.item().get().asItem()).getPath().equals("steel_pickaxe")))
                .findFirst()
                .orElseThrow(() -> helper.assertionException("missing anvil steel_pickaxe route"));
        List<ItemStack> forgedTools = MetallurgyAssay.evaluateMachineOutputs(
                ProcessingMachineBlock.Kind.ANVIL, pickaxeRoute, foldedSteel, List.of(), ItemStack.EMPTY, 1300);
        ToolQualityComponent toolQuality = forgedTools.getFirst().get(EarthDataComponents.TOOL_QUALITY.get());
        helper.assertTrue(toolQuality != null && toolQuality.grade().tier() >= Grade.MASTERWORK.tier(),
                "forging steel_pickaxe from masterwork/heirloom steel at 1300 K must yield >= MASTERWORK tool_quality");

        // 5. Tooltip formatting check (wt%, grade, purity)
        List<Component> tooltipLines = new ArrayList<>();
        QualityComponents.appendTooltip(foldedSteel, tooltipLines::add);
        String joinedTooltip = tooltipLines.stream().map(Component::getString).reduce("", (a, b) -> a + "\n" + b);
        helper.assertTrue(joinedTooltip.contains("Fe ") && joinedTooltip.contains("%"),
                "tooltip must display element mass fraction percentages: " + joinedTooltip);

        // 6. Cross-addon API (Xuanhuan / Magic / Sci-Fi)
        var cleanReport = EarthOnMinecraftApi.materials().inspectStack(foldedSteel);
        ItemStack dirtySponge = new ItemStack(EarthOnMinecraft.SPONGE_IRON.get());
        var dirtyReport = EarthOnMinecraftApi.materials().inspectStack(dirtySponge);
        helper.assertTrue(cleanReport.xuanhuanArtifactEmbryoFactor() > dirtyReport.xuanhuanArtifactEmbryoFactor() * 2.0,
                "folded clean steel must have a much higher Xuanhuan flying-sword embryo factor than dirty sponge iron");
        helper.assertTrue(dirtyReport.impurityPenalty() > cleanReport.impurityPenalty(),
                "dirty sponge iron must report higher impurity penalty than refined steel");

        var cathodeReport = EarthOnMinecraftApi.materials()
                .inspectMaterial(EarthOnMinecraft.id("cathode_copper"))
                .orElseThrow(() -> helper.assertionException("missing cathode_copper assay report"));
        helper.assertTrue(cathodeReport.magicArcaneConductivityFactor() > cleanReport.magicArcaneConductivityFactor(),
                "4N cathode copper must exceed ferrous steel in Magic arcane conductivity");

        var waferReport = EarthOnMinecraftApi.materials()
                .inspectMaterial(EarthOnMinecraft.id("silicon_wafer"))
                .orElseThrow(() -> helper.assertionException("missing silicon_wafer assay report"));
        var mgSiReport = EarthOnMinecraftApi.materials()
                .inspectMaterial(EarthOnMinecraft.id("metallurgical_silicon"))
                .orElseThrow(() -> helper.assertionException("missing metallurgical_silicon assay report"));
        helper.assertTrue(waferReport.scifiHighPurityFactor() > mgSiReport.scifiHighPurityFactor(),
                "9N silicon wafer must exceed metallurgical silicon in Sci-Fi high-purity factor");

        helper.succeed();
    }

    private static void chemicalCatalystDualTanks(GameTestHelper helper) {
        // 1. Verify balanced multi-reactant chemical routes & catalyst metadata (Haber-Bosch, Contact, Ostwald, Chlor-Alkali, Neutralization)
        var haberRoutes = ProcessingMachineBlock.matchingRecipes(
                ProcessingMachineBlock.Kind.SYNTHESIS_LOOP,
                new ItemStack(EarthOnMinecraft.NITROGEN_GAS_CELL.get()));
        helper.assertTrue(!haberRoutes.isEmpty(), "Synthesis Loop must expose Haber-Bosch nitrogen + hydrogen route");
        ProcessingMachineBlock.Recipe haber = haberRoutes.getFirst();
        helper.assertTrue(haber.hasSecondaryInput(), "Haber-Bosch must require secondary H2 reactant");
        helper.assertValueEqual(haber.primaryCount(), 1, "Haber-Bosch primary N2 count must be 1");
        helper.assertValueEqual(haber.secondaryInputs().getFirst().count(), 3, "Haber-Bosch secondary H2 count must be 3");
        helper.assertTrue(haber.hasCatalyst(), "Haber-Bosch must require a catalyst");
        helper.assertValueEqual(haber.catalystStack().getItem(), EarthOnMinecraft.IRON_CATALYST.get().asItem(),
                "Haber-Bosch catalyst must be iron_catalyst");
        helper.assertValueEqual(haber.requiredTemperatureK(), 723, "Haber-Bosch temperature must be 723 K");
        helper.assertValueEqual(haber.requiredPressureKpa(), 20000, "Haber-Bosch pressure must be 20000 kPa");
        helper.assertTrue(haber.equation().contains("N2") && haber.equation().contains("3H2"),
                "Haber-Bosch must carry balanced equation metadata");

        // Ensure 1-input catalyst consumption shortcuts are removed
        helper.assertTrue(ProcessingMachineBlock.matchingRecipes(
                        ProcessingMachineBlock.Kind.SYNTHESIS_LOOP,
                        new ItemStack(EarthOnMinecraft.IRON_CATALYST.get())).isEmpty(),
                "iron_catalyst must not be a consumed primary input in Synthesis Loop");

        var contactRoutes = ProcessingMachineBlock.matchingRecipes(
                ProcessingMachineBlock.Kind.CHEMICAL_REACTOR,
                new ItemStack(EarthOnMinecraft.SULFUR_DUST.get()));
        helper.assertTrue(!contactRoutes.isEmpty(), "Chemical Reactor must expose Contact Process sulfur route");
        ProcessingMachineBlock.Recipe contact = contactRoutes.getFirst();
        helper.assertValueEqual(contact.inputs().size(), 3, "Contact Process must require S + O2 + H2O (3 reactants)");
        helper.assertTrue(contact.hasCatalyst()
                        && contact.catalystStack().getItem() == EarthOnMinecraft.VANADIUM_CATALYST.get().asItem(),
                "Contact Process must require vanadium_catalyst");

        var ostwaldRoutes = ProcessingMachineBlock.matchingRecipes(
                ProcessingMachineBlock.Kind.CHEMICAL_REACTOR,
                new ItemStack(EarthOnMinecraft.AMMONIA.get()));
        ProcessingMachineBlock.Recipe ostwald = ostwaldRoutes.stream()
                .filter(ProcessingMachineBlock.Recipe::hasCatalyst)
                .findFirst()
                .orElseThrow(() -> helper.assertionException("Ostwald catalytic nitric acid route missing"));
        helper.assertValueEqual(ostwald.catalystStack().getItem(),
                EarthOnMinecraft.PLATINUM_GROUP_CATALYST.get().asItem(),
                "Ostwald process must require platinum_group_catalyst");
        helper.assertValueEqual(ostwald.requiredTemperatureK(), 1120, "Ostwald temperature must be 1120 K");

        var chlorAlkali = ProcessingMachineBlock.matchingRecipes(
                ProcessingMachineBlock.Kind.ELECTROLYTIC_CELL,
                new ItemStack(EarthOnMinecraft.SALT_DUST.get())).stream()
                .filter(ProcessingMachineBlock.Recipe::hasSecondaryInput)
                .findFirst()
                .orElseThrow(() -> helper.assertionException("Chlor-alkali multi-reactant route missing"));
        helper.assertValueEqual(chlorAlkali.primaryCount(), 2, "Chlor-alkali must consume 2 NaCl");
        helper.assertValueEqual(chlorAlkali.secondaryInputs().getFirst().count(), 2, "Chlor-alkali must consume 2 H2O");

        // 2. Verify non-consumed catalyst gating in SLOT_REAGENT on an assembled Chemical Reactor
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos reactorRel = new BlockPos(3, 1, 3);
        BlockPos reactorAbs = origin.offset(reactorRel);
        setAbsent(helper, reactorAbs.below(), Blocks.SMOOTH_STONE.defaultBlockState());
        setAbsent(helper, reactorAbs, EarthOnMinecraft.CHEMICAL_REACTOR.get().defaultBlockState());
        for (MachineMultiblock.PlacedRequirement req
                : MachineMultiblock.requirementsFor(ProcessingMachineBlock.Kind.CHEMICAL_REACTOR, Direction.NORTH)) {
            BlockPos partAbs = reactorAbs.offset(req.x(), req.y(), req.z());
            if (req.y() == 0) {
                setAbsent(helper, partAbs.below(), Blocks.SMOOTH_STONE.defaultBlockState());
            }
            Block partBlock = switch (req.role()) {
                case "CONTROL_PANEL" -> EarthOnMinecraft.CONTROL_PANEL.get();
                case "PIPE" -> EarthOnMinecraft.STEEL_PROCESS_PIPE.get();
                default -> EarthOnMinecraft.INDUSTRIAL_MACHINE_CASING.get();
            };
            setAbsent(helper, partAbs, partBlock.defaultBlockState());
        }
        helper.assertTrue(MachineMultiblock.isComplete(helper.getLevel(), reactorAbs,
                        ProcessingMachineBlock.Kind.CHEMICAL_REACTOR),
                "assembled chemical reactor must be structurally complete");

        ProcessingMachineBlockEntity reactor = (ProcessingMachineBlockEntity) helper.getLevel().getBlockEntity(reactorAbs);
        helper.assertTrue(reactor.canPlaceItem(ProcessingMachineBlockEntity.SLOT_REAGENT,
                        new ItemStack(EarthOnMinecraft.IRON_CATALYST.get())),
                "chemical reactor must accept iron_catalyst in SLOT_REAGENT");
        var reactorHaber = ProcessingMachineBlock.matchingRecipes(
                ProcessingMachineBlock.Kind.CHEMICAL_REACTOR,
                new ItemStack(EarthOnMinecraft.NITROGEN_GAS_CELL.get())).stream()
                .filter(r -> r.hasCatalyst()
                        && r.catalystStack().getItem() == EarthOnMinecraft.IRON_CATALYST.get().asItem()
                        && r.outputs().stream().anyMatch(o -> o.item().get().asItem() == EarthOnMinecraft.AMMONIA.get().asItem()))
                .findFirst()
                .orElseThrow(() -> helper.assertionException("Chemical Reactor Haber-Bosch route missing"));
        int haberIdx = ProcessingMachineBlock.matchingRecipes(
                ProcessingMachineBlock.Kind.CHEMICAL_REACTOR,
                new ItemStack(EarthOnMinecraft.NITROGEN_GAS_CELL.get())).indexOf(reactorHaber);

        reactor.setItem(ProcessingMachineBlockEntity.SLOT_INPUT,
                new ItemStack(EarthOnMinecraft.NITROGEN_GAS_CELL.get(), 2));
        reactor.setItem(ProcessingMachineBlockEntity.SLOT_SECONDARY_START,
                new ItemStack(EarthOnMinecraft.HYDROGEN_GAS_CELL.get(), 6));
        reactor.data().set(14, haberIdx);
        reactor.data().set(ProcessingMachineBlockEntity.DATA_ENERGY_STORED, 16000);

        // Tick without catalyst: must NOT advance and must report DATA_CATALYST_MISSING = 1
        ProcessingMachineBlockEntity.serverTick(helper.getLevel(), reactorAbs,
                helper.getLevel().getBlockState(reactorAbs), reactor);
        reactor.data().set(14, haberIdx);
        ProcessingMachineBlockEntity.serverTick(helper.getLevel(), reactorAbs,
                helper.getLevel().getBlockState(reactorAbs), reactor);
        helper.assertValueEqual(reactor.data().get(0), 0,
                "catalytic reaction without catalyst in SLOT_REAGENT must not advance progress");
        helper.assertValueEqual(reactor.data().get(ProcessingMachineBlockEntity.DATA_CATALYST_MISSING), 1,
                "missing catalyst must flag DATA_CATALYST_MISSING=1");
        helper.assertValueEqual(reactor.data().get(ProcessingMachineBlockEntity.DATA_REQUIRED_PRESSURE_KPA), 20000,
                "Haber-Bosch must sync DATA_REQUIRED_PRESSURE_KPA=20000");

        // Insert 1 iron catalyst and run reaction to completion: catalyst must NOT be consumed!
        reactor.setItem(ProcessingMachineBlockEntity.SLOT_REAGENT,
                new ItemStack(EarthOnMinecraft.IRON_CATALYST.get(), 1));
        int ticksNeeded = reactorHaber.processTicks();
        for (int t = 0; t < ticksNeeded; t++) {
            reactor.data().set(ProcessingMachineBlockEntity.DATA_ENERGY_STORED, 16000);
            ProcessingMachineBlockEntity.serverTick(helper.getLevel(), reactorAbs,
                    helper.getLevel().getBlockState(reactorAbs), reactor);
        }
        helper.assertValueEqual(reactor.getItem(ProcessingMachineBlockEntity.SLOT_REAGENT).getCount(), 1,
                "catalyst in SLOT_REAGENT must not be consumed after completing reaction");
        helper.assertValueEqual(reactor.getItem(ProcessingMachineBlockEntity.SLOT_INPUT).getCount(), 1,
                "1 N2 must be consumed from primary input");
        helper.assertValueEqual(reactor.getItem(ProcessingMachineBlockEntity.SLOT_SECONDARY_START).getCount(), 3,
                "3 H2 must be consumed from secondary input");
        helper.assertValueEqual(reactor.getItem(ProcessingMachineBlockEntity.SLOT_OUTPUT_START).getCount(), 2,
                "2 NH3 must be produced in output slot");

        // 3. Verify dual tanks (inputTank + outputTank) in a simultaneous fluid-in + fluid-out machine (Shaft Furnace melt_iron)
        BlockPos furnaceAbs = origin.offset(1, 1, 8);
        setAbsent(helper, furnaceAbs.below(), Blocks.SMOOTH_STONE.defaultBlockState());
        setAbsent(helper, furnaceAbs, EarthOnMinecraft.SHAFT_FURNACE.get().defaultBlockState());
        ProcessingMachineBlockEntity furnace = (ProcessingMachineBlockEntity) helper.getLevel().getBlockEntity(furnaceAbs);
        furnace.inputTank().fill("earth_on_minecraft:steam", 500);
        furnace.outputTank().fill("earth_on_minecraft:molten_iron", 288);
        helper.assertValueEqual(furnace.tankAmount(), 500, "inputTank must hold 500 mB steam");
        helper.assertValueEqual(furnace.outputTankAmount(), 288, "outputTank must simultaneously hold 288 mB molten_iron");
        helper.assertValueEqual(furnace.data().get(ProcessingMachineBlockEntity.DATA_OUTPUT_TANK_AMOUNT), 288,
                "DATA_OUTPUT_TANK_AMOUNT must sync outputTank amount");
        helper.assertTrue(furnace.data().get(ProcessingMachineBlockEntity.DATA_OUTPUT_TANK_FLUID) >= 0,
                "DATA_OUTPUT_TANK_FLUID must sync valid fluid registry ID");

        BlockPos castAbs = origin.offset(2, 1, 8);
        setAbsent(helper, castAbs.below(), Blocks.SMOOTH_STONE.defaultBlockState());
        setAbsent(helper, castAbs, EarthOnMinecraft.CASTING_TABLE.get().defaultBlockState());
        ProcessingMachineBlockEntity caster = (ProcessingMachineBlockEntity) helper.getLevel().getBlockEntity(castAbs);
        helper.assertTrue(furnace.tryExportFluidToNeighbors(helper.getLevel(), furnaceAbs),
                "dual-tank machine must export from outputTank into neighbor inputTank while keeping its own inputTank intact");
        helper.assertValueEqual(furnace.tankAmount(), 500, "inputTank steam must remain untouched after exporting outputTank");
        helper.assertValueEqual(furnace.outputTankAmount(), 0, "outputTank must empty after export");
        helper.assertValueEqual(caster.tankAmount(), 288, "neighbor inputTank must receive 288 mB molten_iron");

        helper.succeed();
    }

    private static final class DirectGameTestInstance extends GameTestInstance {
        private static final MapCodec<DirectGameTestInstance> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("test").forGetter(test -> test.testId),
                TestData.CODEC.forGetter(DirectGameTestInstance::info)
        ).apply(instance, DirectGameTestInstance::new));

        private final Identifier testId;

        private DirectGameTestInstance(Identifier testId, TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
            this.testId = testId;
        }

        @Override
        public void run(GameTestHelper helper) {
            Consumer<GameTestHelper> test = TESTS.get(testId);
            if (test == null) {
                throw new IllegalStateException("Unknown Earth on Minecraft test: " + testId);
            }
            test.accept(helper);
        }

        @Override
        public MapCodec<DirectGameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Earth on Minecraft direct GameTest");
        }
    }
}
