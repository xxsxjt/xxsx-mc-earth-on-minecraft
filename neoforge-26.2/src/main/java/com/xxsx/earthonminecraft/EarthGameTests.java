package com.xxsx.earthonminecraft;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.xxsx.earthonminecraft.api.v1.EarthOnMinecraftApi;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

final class EarthGameTests {
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final Identifier ROUTE_CATALOG = EarthOnMinecraft.id("route_catalog");
    private static final Identifier ENERGY_ROUND_TRIP = EarthOnMinecraft.id("energy_round_trip");
    private static final Identifier LOGISTICS_INSERT = EarthOnMinecraft.id("logistics_insert");
    private static final Identifier GEOLOGY_QUERY = EarthOnMinecraft.id("geology_query");
    private static final Identifier STRUCTURE_FAULT_STATE = EarthOnMinecraft.id("structure_fault_state");
    private static final Identifier REALITY_SUPPORT = EarthOnMinecraft.id("reality_support");
    private static final Identifier REALITY_UTILITIES = EarthOnMinecraft.id("reality_utilities");
    private static final Identifier SCREENSHOT_FIELD = EarthOnMinecraft.id("quality_screenshot_field");
    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_INSTANCE_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, EarthOnMinecraft.MODID);
    private static final Map<Identifier, Consumer<GameTestHelper>> TESTS = Map.of(
            ROUTE_CATALOG, EarthGameTests::routeCatalog,
            ENERGY_ROUND_TRIP, EarthGameTests::energyRoundTrip,
            LOGISTICS_INSERT, EarthGameTests::logisticsInsert,
            GEOLOGY_QUERY, EarthGameTests::geologyQuery,
            STRUCTURE_FAULT_STATE, EarthGameTests::structureFaultState,
            REALITY_SUPPORT, EarthGameTests::realitySupport,
            REALITY_UTILITIES, EarthGameTests::realityUtilities,
            SCREENSHOT_FIELD, EarthGameTests::screenshotField);

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
        event.registerTest(ENERGY_ROUND_TRIP, test(ENERGY_ROUND_TRIP, environment, 40, true));
        event.registerTest(LOGISTICS_INSERT, test(LOGISTICS_INSERT, environment, 40, true));
        event.registerTest(GEOLOGY_QUERY, test(GEOLOGY_QUERY, environment, 40, true));
        event.registerTest(STRUCTURE_FAULT_STATE, test(STRUCTURE_FAULT_STATE, environment, 60, true));
        event.registerTest(REALITY_SUPPORT, test(REALITY_SUPPORT, environment, 40, true));
        event.registerTest(REALITY_UTILITIES, test(REALITY_UTILITIES, environment, 40, true));
        TestData<Holder<TestEnvironmentDefinition<?>>> screenshotData = new TestData<>(
                environment, EMPTY_STRUCTURE, 200, 1, false, Rotation.NONE, true, 1, 1, true, 24);
        event.registerTest(SCREENSHOT_FIELD, new DirectGameTestInstance(SCREENSHOT_FIELD, screenshotData));
    }

    private static GameTestInstance test(Identifier testId, Holder<TestEnvironmentDefinition<?>> environment,
                                         int maxTicks, boolean required) {
        return new DirectGameTestInstance(testId,
                new TestData<>(environment, EMPTY_STRUCTURE, maxTicks, 1, required));
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
            helper.assertTrue(!route.outputs().isEmpty(), "route has no outputs: " + route.note());
            helper.assertTrue(route.outputs().stream().allMatch(output -> output.count() > 0),
                    "route has a non-positive output: " + route.note());
        }
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
