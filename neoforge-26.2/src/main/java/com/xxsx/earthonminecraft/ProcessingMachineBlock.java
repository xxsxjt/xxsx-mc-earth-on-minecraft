package com.xxsx.earthonminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class ProcessingMachineBlock extends Block implements EntityBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty FAULT = BooleanProperty.create("fault");
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    private static List<Recipe> recipes;

    private final Kind kind;

    public ProcessingMachineBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any()
                .setValue(ACTIVE, false)
                .setValue(FAULT, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!RealitySupportRules.canPlace(context.getLevel(), context.getClickedPos())) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        MachineMultiblock.refreshProjection(level, pos, this.kind);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.isShiftKeyDown() && this.kind.powerMode().manualFallback()
                && level.getBlockEntity(pos) instanceof ProcessingMachineBlockEntity machine) {
            if (!level.isClientSide()) {
                machine.triggerManualWork(40);
            }
            return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }
        return openMachine(level, pos, player);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.isEmpty() && level.getBlockEntity(pos) instanceof ProcessingMachineBlockEntity machine) {
            if (machine.tryInteractWithFluidItem(player, hand, stack)) {
                return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
            }
            if ((stack.getItem() instanceof HandCrankItem || Ae2CrankCompat.isAe2CrankItem(stack))
                    && machine.canCrankTurn()) {
                if (!level.isClientSide()) {
                    machine.applyCrankTurn(HandCrankItem.CRANK_WORK_TICKS);
                    if (stack.isDamageableItem()) {
                        stack.hurtAndBreak(1, player, hand);
                    }
                }
                return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
            }
        }
        if (player.isShiftKeyDown() && this.kind.powerMode().manualFallback()
                && level.getBlockEntity(pos) instanceof ProcessingMachineBlockEntity machine) {
            if (!level.isClientSide()) {
                machine.triggerManualWork(40);
            }
            return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }
        return openMachine(level, pos, player);
    }

    /** True when any route of {@code kind} consumes {@code fluidId} as a fluid input. */
    public static boolean acceptsFluidInput(Kind kind, String fluidId) {
        if (fluidId == null || fluidId.isEmpty()) {
            return false;
        }
        return recipesFor(kind).stream()
                .anyMatch(recipe -> recipe.fluidInputs().stream()
                        .anyMatch(input -> input.fluidId().equals(fluidId)));
    }

    /** True when any route of {@code kind} produces {@code fluidId} as a fluid output. */
    public static boolean producesFluidOutput(Kind kind, String fluidId) {
        if (fluidId == null || fluidId.isEmpty()) {
            return false;
        }
        return recipesFor(kind).stream()
                .anyMatch(recipe -> recipe.fluidOutputs().stream()
                        .anyMatch(output -> output.fluidId().equals(fluidId)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ProcessingMachineBlockEntity(pos, state);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(FAULT) || !state.getValue(ACTIVE) || random.nextInt(5) != 0) {
            return;
        }
        MachineFeedback.emitRunning(level, pos, state, kind, random);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide()) {
            return null;
        }
        return blockEntityType == EarthOnMinecraft.PROCESSING_MACHINE_BLOCK_ENTITY.get()
                ? (tickerLevel, pos, tickerState, blockEntity) -> ProcessingMachineBlockEntity.serverTick(tickerLevel, pos, tickerState, (ProcessingMachineBlockEntity) blockEntity)
                : null;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof ProcessingMachineBlockEntity machine) {
            MachineMultiblock.clearProjectionForController(level, pos, machine.kind());
            Containers.dropContents(level, pos, (Container) machine);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, FAULT, FACING);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    public static List<Recipe> recipes() {
        return recipeTable();
    }

    public static List<Recipe> recipesFor(Kind kind) {
        return recipeTable().stream().filter(recipe -> recipe.kind == kind).toList();
    }

    public static List<Recipe> matchingRecipes(Kind kind, ItemStack stack) {
        if (stack.isEmpty()) {
            return List.of();
        }
        return recipeTable().stream()
                .filter(recipe -> recipe.kind == kind)
                .filter(recipe -> recipe.hasItemInput()
                        && stack.getItem() == recipe.inputStack().getItem())
                .toList();
    }

    /**
     * Routes whose only input is a fluid. These cannot be selected by an item in the input slot,
     * so the block entity resolves them from the machine tank instead.
     */
    public static List<Recipe> fluidOnlyRecipes(Kind kind, String fluidId) {
        if (fluidId == null || fluidId.isEmpty()) {
            return List.of();
        }
        return recipeTable().stream()
                .filter(recipe -> recipe.kind == kind)
                .filter(recipe -> !recipe.hasItemInput())
                .filter(recipe -> !recipe.fluidInputs().isEmpty())
                .filter(recipe -> recipe.fluidInputs().stream()
                        .anyMatch(input -> input.fluidId().equals(fluidId)))
                .toList();
    }

    /**
     * Routes that could run if the given item were the primary input, ignoring secondary stock.
     * Used by automation and slot filters, which cannot see the whole inventory.
     */
    public static boolean acceptsInput(Kind kind, ItemStack stack) {
        return !matchingRecipes(kind, stack).isEmpty();
    }

    /** True when any route of {@code kind} uses {@code stack} as a non-consumed catalyst in {@code SLOT_REAGENT}. */
    public static boolean acceptsCatalyst(Kind kind, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return recipesFor(kind).stream()
                .filter(Recipe::hasCatalyst)
                .anyMatch(recipe -> stack.getItem() == recipe.catalyst().get().asItem());
    }

    public static Optional<Recipe> findRecipe(Kind kind, ItemStack stack) {
        return findRecipe(kind, stack, 0);
    }

    public static Optional<Recipe> findRecipe(Kind kind, ItemStack stack, int routeIndex) {
        List<Recipe> matches = matchingRecipes(kind, stack);
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        int safeIndex = Math.floorMod(routeIndex, matches.size());
        return Optional.of(matches.get(safeIndex));
    }

    public Kind kind() {
        return this.kind;
    }

    private InteractionResult openMachine(Level level, BlockPos pos, Player player) {
        return openMachineAt(level, pos, player);
    }

    public static InteractionResult openMachineAt(Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof ProcessingMachineBlockEntity machine) {
            serverPlayer.openMenu(machine, buf -> buf.writeBlockPos(pos));
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    public static String describeOutputs(List<Output> outputs) {
        List<String> names = new ArrayList<>();
        for (Output output : outputs) {
            ItemStack stack = new ItemStack(output.item.get().asItem(), output.count);
            names.add(output.count + "x " + stack.getItemName().getString());
        }
        return String.join(" + ", names);
    }

    /**
     * Builds the route table lazily. Spec routes resolve registry entries by id, so the table
     * cannot be built while item registration is still running.
     */
    private static synchronized List<Recipe> recipeTable() {
        if (recipes == null) {
            List<Recipe> table = new ArrayList<>(createRecipes());
            table.addAll(createSpecRecipes());
            recipes = table.stream()
                    .filter(PlayableContentFilter::isPlayableRecipe)
                    .toList();
        }
        return recipes;
    }

    private static List<Recipe> createSpecRecipes() {
        List<Recipe> table = new ArrayList<>();
        for (SpecMachineRecipes.Route route : SpecMachineRecipes.ROUTES) {
            Kind kind;
            try {
                kind = Kind.valueOf(route.kind());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            List<Input> inputs = new ArrayList<>();
            for (int i = 0; i < route.inputs().length; i++) {
                Supplier<? extends ItemLike> supplier = vanillaOrModItem(route.inputs()[i]);
                if (supplier == null) {
                    inputs = null;
                    break;
                }
                inputs.add(new Input(supplier, route.inputCounts()[i]));
            }
            if (inputs == null) {
                continue;
            }
            List<Output> outputs = new ArrayList<>();
            for (int i = 0; i < route.outputs().length; i++) {
                Supplier<? extends ItemLike> supplier = vanillaOrModItem(route.outputs()[i]);
                if (supplier == null) {
                    outputs = null;
                    break;
                }
                outputs.add(new Output(supplier, route.outputCounts()[i]));
            }
            if (outputs == null) {
                continue;
            }
            // The generator stores fully-qualified fluid ids, so they must not be prefixed again.
            // Double-prefixing turned every fluid route into "earth_on_minecraft:earth_on_minecraft:
            // molten_iron", which never matched the tank contents and silently disabled them all.
            List<FluidInput> fluidInputs = new ArrayList<>();
            for (int i = 0; i < route.fluidInputs().length; i++) {
                fluidInputs.add(new FluidInput(qualifyFluid(route.fluidInputs()[i]),
                        route.fluidInputAmounts()[i]));
            }
            List<FluidOutput> fluidOutputs = new ArrayList<>();
            for (int i = 0; i < route.fluidOutputs().length; i++) {
                fluidOutputs.add(new FluidOutput(qualifyFluid(route.fluidOutputs()[i]),
                        route.fluidOutputAmounts()[i]));
            }
            if (outputs.isEmpty() && fluidOutputs.isEmpty()) {
                continue;
            }
            Supplier<? extends ItemLike> catalyst = route.catalyst() == null || route.catalyst().isEmpty()
                    ? null
                    : vanillaOrModItem(route.catalyst());
            table.add(new Recipe(kind, inputs, route.note(), outputs, false,
                    route.ticks(), route.energy(), route.sourceId(), fluidInputs, fluidOutputs,
                    catalyst, route.temperatureK(), route.pressureKpa(), route.equation()));
        }
        return table;
    }

    /** Fluid ids are stored fully qualified; add the namespace only when it is missing. */
    private static String qualifyFluid(String id) {
        return id.indexOf(':') >= 0 ? id : "earth_on_minecraft:" + id;
    }

    @Nullable
    private static Supplier<? extends ItemLike> vanillaOrModItem(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            return null;
        }
        if (BuiltInRegistries.ITEM.containsKey(identifier)) {
            Item item = BuiltInRegistries.ITEM.getValue(identifier);
            return item == null || item == Items.AIR ? null : () -> item;
        }
        if (BuiltInRegistries.BLOCK.containsKey(identifier)) {
            Block block = BuiltInRegistries.BLOCK.getValue(identifier);
            return block == null || block == Blocks.AIR ? null : () -> block;
        }
        return null;
    }

    private static List<Recipe> createRecipes() {
        List<Recipe> recipes = new ArrayList<>();

        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.POOR_MAGNETITE_ORE::get, "贫磁铁矿粗碎", out(EarthOnMinecraft.MAGNETITE_CHUNK::get, 2), out(EarthOnMinecraft.TAILINGS_DUST::get, 2)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.MAGNETITE_ORE::get, "磁铁矿粗碎", out(EarthOnMinecraft.MAGNETITE_CHUNK::get, 4), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.RICH_MAGNETITE_ORE::get, "富磁铁矿粗碎", out(EarthOnMinecraft.MAGNETITE_CHUNK::get, 6), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.CHALCOPYRITE_ORE::get, "黄铜矿粗碎", out(EarthOnMinecraft.CHALCOPYRITE_CHUNK::get, 4), out(EarthOnMinecraft.PYRITE_DUST::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.AURIFEROUS_QUARTZ_VEIN::get, "含金石英脉粗碎", out(EarthOnMinecraft.AURIFEROUS_QUARTZ_CHUNK::get, 3), out(EarthOnMinecraft.QUARTZ_DUST::get, 2)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.KIMBERLITE::get, "金伯利岩粗碎", out(EarthOnMinecraft.KIMBERLITE_CHUNK::get, 4), out(EarthOnMinecraft.TAILINGS_DUST::get, 2)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.DIAMONDIFEROUS_KIMBERLITE::get, "含钻金伯利岩粗碎", out(EarthOnMinecraft.KIMBERLITE_CHUNK::get, 4), out(EarthOnMinecraft.DIAMOND_GRIT::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.CINNABAR_VEIN::get, "辰砂矿脉粗碎与伴生导电粉分离", out(EarthOnMinecraft.CINNABAR_CHUNK::get, 4), out(() -> Items.REDSTONE, 2), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.REDSTONE_MINERAL_ORE::get, "赤铁软锰伴生矿粗碎", out(EarthOnMinecraft.REDSTONE_CONCENTRATE::get, 2), out(() -> Items.REDSTONE, 3), out(EarthOnMinecraft.MANGANESE_OXIDE_DUST::get, 1), out(EarthOnMinecraft.HEMATITE_DUST::get, 1)));
        recipes.add(r(Kind.CRUSHER, SpecBlocks.BY_ID.get("manganite_ore")::get, "水锰矿粗碎与伴生导电粉分离", out(EarthOnMinecraft.MANGANESE_OXIDE_DUST::get, 3), out(() -> Items.REDSTONE, 2), out(EarthOnMinecraft.HEMATITE_DUST::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.BITUMINOUS_COAL_SEAM::get, "烟煤含煤岩破碎与挥发分回收", out(EarthOnMinecraft.COAL_DUST::get, 4), out(EarthOnMinecraft.COAL_TAR::get, 1), out(EarthOnMinecraft.COAL_GAS_CELL::get, 1), out(EarthOnMinecraft.CRUDE_OIL_SAMPLE::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.ANTHRACITE_COAL_SEAM::get, "无烟煤含煤岩破碎与高碳富集", out(EarthOnMinecraft.COAL_DUST::get, 7), out(EarthOnMinecraft.GRAPHITE_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.BAUXITE_LATERITE_DEPOSIT::get, "铝土矿风化壳破碎", out(EarthOnMinecraft.BAUXITE_DUST::get, 5), out(EarthOnMinecraft.HEMATITE_DUST::get, 1), out(EarthOnMinecraft.KAOLIN_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.PHOSPHORITE_BED::get, "磷块岩沉积层破碎", out(EarthOnMinecraft.PHOSPHATE_ROCK_DUST::get, 5), out(EarthOnMinecraft.CALCITE_DUST::get, 1), out(EarthOnMinecraft.GYPSUM_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.EVAPORITE_SALT_BED::get, "蒸发岩盐层破碎", out(EarthOnMinecraft.SALT_DUST::get, 5), out(EarthOnMinecraft.GYPSUM_DUST::get, 1), out(EarthOnMinecraft.BRINE_CRYSTAL::get, 1), out(EarthOnMinecraft.POTASSIUM_CHLORIDE::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.CASSITERITE_PLACER_DEPOSIT::get, "锡石砂矿预碎洗选", out(EarthOnMinecraft.CASSITERITE_DUST::get, 3), out(EarthOnMinecraft.SILICA_DUST::get, 2), out(EarthOnMinecraft.TITANIUM_DIOXIDE::get, 1), out(EarthOnMinecraft.MONAZITE_SAND::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.RARE_EARTH_CARBONATITE::get, "稀土碳酸岩破碎", out(EarthOnMinecraft.BASTNASITE_DUST::get, 3), out(EarthOnMinecraft.MONAZITE_SAND::get, 1), out(EarthOnMinecraft.CALCITE_DUST::get, 2), out(EarthOnMinecraft.URANINITE_DUST::get, 1), out(EarthOnMinecraft.RARE_EARTH_TAILINGS::get, 1)));

        recipes.add(r(Kind.BALL_MILL, EarthOnMinecraft.MAGNETITE_CHUNK::get, "磁铁矿球磨", out(EarthOnMinecraft.MAGNETITE_DUST::get, 3), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, EarthOnMinecraft.CRUSHED_MAGNETITE::get, "磁铁矿碎矿球磨", out(EarthOnMinecraft.MAGNETITE_DUST::get, 3), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, EarthOnMinecraft.CHALCOPYRITE_CHUNK::get, "黄铜矿球磨", out(EarthOnMinecraft.CHALCOPYRITE_DUST::get, 3), out(EarthOnMinecraft.PYRITE_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, EarthOnMinecraft.CINNABAR_CHUNK::get, "辰砂球磨", out(EarthOnMinecraft.CINNABAR_DUST::get, 3), out(() -> Items.REDSTONE, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, EarthOnMinecraft.REDSTONE_CONCENTRATE::get, "铁锰导电精矿细磨", out(() -> Items.REDSTONE, 4), out(EarthOnMinecraft.MANGANESE_OXIDE_DUST::get, 1), out(EarthOnMinecraft.HEMATITE_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.GRANITE, "花岗岩矿物分离", out(EarthOnMinecraft.QUARTZ_DUST::get, 2), out(EarthOnMinecraft.FELDSPAR_DUST::get, 3), out(EarthOnMinecraft.MICA_DUST::get, 1), out(EarthOnMinecraft.MONAZITE_SAND::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.DIORITE, "闪长岩矿物分离", out(EarthOnMinecraft.FELDSPAR_DUST::get, 3), out(EarthOnMinecraft.MAFIC_SILICATE_DUST::get, 2), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.ANDESITE, "安山岩矿物分离", out(EarthOnMinecraft.FELDSPAR_DUST::get, 2), out(EarthOnMinecraft.MAFIC_SILICATE_DUST::get, 3), out(EarthOnMinecraft.MAGNETITE_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.BASALT, "玄武岩矿物分离", out(EarthOnMinecraft.MAFIC_SILICATE_DUST::get, 4), out(EarthOnMinecraft.MAGNETITE_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.STONE, "普通石料粉磨（未分类硅酸盐-碳酸盐岩）", out(EarthOnMinecraft.SILICA_DUST::get, 2), out(EarthOnMinecraft.FELDSPAR_DUST::get, 1), out(EarthOnMinecraft.MAFIC_SILICATE_DUST::get, 1), out(EarthOnMinecraft.CALCITE_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.COBBLESTONE, "圆石碎屑粉磨（混合采石骨料）", out(EarthOnMinecraft.SILICA_DUST::get, 2), out(EarthOnMinecraft.FELDSPAR_DUST::get, 1), out(EarthOnMinecraft.MAFIC_SILICATE_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.DEEPSLATE, "深板岩矿物分离", out(EarthOnMinecraft.ALUMINOSILICATE_DUST::get, 4), out(EarthOnMinecraft.MICA_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.COBBLED_DEEPSLATE, "深层圆石矿物分离", out(EarthOnMinecraft.ALUMINOSILICATE_DUST::get, 3), out(EarthOnMinecraft.MICA_DUST::get, 1), out(EarthOnMinecraft.MAFIC_SILICATE_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.TUFF, "凝灰岩矿物分离", out(EarthOnMinecraft.SILICA_DUST::get, 2), out(EarthOnMinecraft.MAFIC_SILICATE_DUST::get, 2), out(EarthOnMinecraft.TAILINGS_DUST::get, 2)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.CALCITE, "方解石粉磨", out(EarthOnMinecraft.CALCITE_DUST::get, 5)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.DRIPSTONE_BLOCK, "钙质滴石粉磨", out(EarthOnMinecraft.CALCITE_DUST::get, 4), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.SANDSTONE, "砂岩粉磨", out(EarthOnMinecraft.SILICA_DUST::get, 5), out(EarthOnMinecraft.CALCITE_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.BLACKSTONE, "富铁镁质黑石粉磨", out(EarthOnMinecraft.MAFIC_SILICATE_DUST::get, 4), out(EarthOnMinecraft.HEMATITE_DUST::get, 1), out(EarthOnMinecraft.CHROMITE_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Items.OAK_LOG, "橡木机械削片", out(EarthOnMinecraft.WOOD_CHIPS::get, 4)));
        recipes.add(r(Kind.BALL_MILL, () -> Items.SPRUCE_LOG, "云杉机械削片", out(EarthOnMinecraft.WOOD_CHIPS::get, 4)));
        recipes.add(r(Kind.BALL_MILL, () -> Items.BIRCH_LOG, "白桦机械削片", out(EarthOnMinecraft.WOOD_CHIPS::get, 4)));
        recipes.add(r(Kind.BALL_MILL, () -> Items.JUNGLE_LOG, "丛林木机械削片", out(EarthOnMinecraft.WOOD_CHIPS::get, 4)));
        recipes.add(r(Kind.BALL_MILL, () -> Items.ACACIA_LOG, "金合欢机械削片", out(EarthOnMinecraft.WOOD_CHIPS::get, 4)));
        recipes.add(r(Kind.BALL_MILL, () -> Items.DARK_OAK_LOG, "深色橡木机械削片", out(EarthOnMinecraft.WOOD_CHIPS::get, 4)));
        recipes.add(r(Kind.BALL_MILL, () -> Items.MANGROVE_LOG, "红树木机械削片", out(EarthOnMinecraft.WOOD_CHIPS::get, 4)));
        recipes.add(r(Kind.BALL_MILL, () -> Items.CHERRY_LOG, "樱花木机械削片", out(EarthOnMinecraft.WOOD_CHIPS::get, 4)));

        recipes.add(r(Kind.SIEVE, EarthOnMinecraft.TAILINGS_DUST::get, "尾粉筛分", out(EarthOnMinecraft.SILICA_DUST::get, 1), out(EarthOnMinecraft.ALUMINOSILICATE_DUST::get, 1)));
        recipes.add(r(Kind.SIEVE, EarthOnMinecraft.KIMBERLITE_CHUNK::get, "金伯利岩重矿物筛分", out(EarthOnMinecraft.DIAMOND_GRIT::get, 1), out(EarthOnMinecraft.MAFIC_SILICATE_DUST::get, 2), out(EarthOnMinecraft.TAILINGS_DUST::get, 2)));
        recipes.add(r(Kind.SIEVE, EarthOnMinecraft.AURIFEROUS_QUARTZ_CHUNK::get, "含金石英碎块筛分", out(EarthOnMinecraft.GOLD_DUST::get, 1), out(EarthOnMinecraft.QUARTZ_DUST::get, 2), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.SIEVE, EarthOnMinecraft.MICA_DUST::get, "云母片状矿物筛分", out(EarthOnMinecraft.ALUMINOSILICATE_DUST::get, 1), out(EarthOnMinecraft.SILICA_DUST::get, 1)));
        recipes.add(r(Kind.SIEVE, EarthOnMinecraft.FELDSPAR_DUST::get, "长石粉筛分除杂", out(EarthOnMinecraft.ALUMINOSILICATE_DUST::get, 1), out(EarthOnMinecraft.SILICA_DUST::get, 1), out(EarthOnMinecraft.BASTNASITE_DUST::get, 1)));
        recipes.add(r(Kind.SIEVE, EarthOnMinecraft.CASSITERITE_PLACER_DEPOSIT::get, "锡石砂矿重矿物筛分", out(EarthOnMinecraft.CASSITERITE_DUST::get, 2), out(EarthOnMinecraft.MONAZITE_SAND::get, 1), out(EarthOnMinecraft.SILICA_DUST::get, 2), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.SIEVE, EarthOnMinecraft.EVAPORITE_SALT_BED::get, "蒸发岩硼钾伴生筛分", out(EarthOnMinecraft.BORATE_MINERAL_DUST::get, 1), out(EarthOnMinecraft.POTASSIUM_CHLORIDE::get, 1), out(EarthOnMinecraft.SALT_DUST::get, 2)));
        recipes.add(r(Kind.SIEVE, EarthOnMinecraft.BASTNASITE_DUST::get, "稀土矿物伴生独居石与铀筛分", out(EarthOnMinecraft.BASTNASITE_DUST::get, 1), out(EarthOnMinecraft.MONAZITE_SAND::get, 1), out(EarthOnMinecraft.URANINITE_DUST::get, 1), out(EarthOnMinecraft.CALCITE_DUST::get, 1)));
        recipes.add(r(Kind.SIEVE, () -> Items.COAL, "原煤重介质筛分与挥发油样品回收", out(EarthOnMinecraft.COAL_DUST::get, 2), out(EarthOnMinecraft.CRUDE_OIL_SAMPLE::get, 1), out(EarthOnMinecraft.GRAPHITE_DUST::get, 1)));

        recipes.add(r(Kind.MAGNETIC_SEPARATOR, EarthOnMinecraft.MAGNETITE_DUST::get, "磁选铁精矿", out(EarthOnMinecraft.IRON_CONCENTRATE::get, 2), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.MAGNETIC_SEPARATOR, EarthOnMinecraft.HEMATITE_DUST::get, "赤铁矿弱磁分选", out(EarthOnMinecraft.IRON_CONCENTRATE::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.MAGNETIC_SEPARATOR, EarthOnMinecraft.MAFIC_SILICATE_DUST::get, "镁铁质硅酸盐除铁", out(EarthOnMinecraft.MAGNETITE_DUST::get, 1), out(EarthOnMinecraft.MAGNESIUM_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 2)));

        recipes.add(r(Kind.FLOTATION_CELL, EarthOnMinecraft.CHALCOPYRITE_DUST::get, "黄铜矿浮选", out(EarthOnMinecraft.COPPER_CONCENTRATE::get, 2), out(EarthOnMinecraft.PYRITE_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.FLOTATION_CELL, EarthOnMinecraft.PYRITE_DUST::get, "黄铁矿浮选", out(EarthOnMinecraft.SULFUR_DUST::get, 1), out(EarthOnMinecraft.IRON_CONCENTRATE::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.FLOTATION_CELL, EarthOnMinecraft.GOLD_DUST::get, "金粉富集", out(EarthOnMinecraft.GOLD_CONCENTRATE::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.FLOTATION_CELL, EarthOnMinecraft.LAPIS_LAZULI_ORE::get, "青金石浮选", out(EarthOnMinecraft.LAPIS_CONCENTRATE::get, 2), out(EarthOnMinecraft.CALCITE_DUST::get, 1), out(EarthOnMinecraft.PYRITE_DUST::get, 1)));
        recipes.add(r(Kind.FLOTATION_CELL, EarthOnMinecraft.REDSTONE_MINERAL_ORE::get, "赤铁软锰伴生矿浮选富集", out(EarthOnMinecraft.REDSTONE_CONCENTRATE::get, 2), out(EarthOnMinecraft.SILICA_DUST::get, 1)));

        recipes.add(r(Kind.ROASTER, EarthOnMinecraft.COPPER_CONCENTRATE::get, "铜精矿焙烧", out(EarthOnMinecraft.ROASTED_COPPER_CONCENTRATE::get, 1), out(EarthOnMinecraft.SULFUR_DUST::get, 2)));
        recipes.add(r(Kind.ROASTER, EarthOnMinecraft.PYRITE_DUST::get, "黄铁矿焙烧", out(EarthOnMinecraft.HEMATITE_DUST::get, 1), out(EarthOnMinecraft.SULFUR_DUST::get, 2)));
        recipes.add(r(Kind.ROASTER, EarthOnMinecraft.CINNABAR_DUST::get, "辰砂焙烧", out(EarthOnMinecraft.MERCURY_DROPLET::get, 1), out(EarthOnMinecraft.SULFUR_DUST::get, 1)));
        recipes.add(r(Kind.ROASTER, EarthOnMinecraft.CALCITE_DUST::get, "碳酸盐煅烧", out(EarthOnMinecraft.LIME_DUST::get, 1)));
        recipes.add(r(Kind.ROASTER, EarthOnMinecraft.PETROLEUM_COKE::get, "石油焦高温石墨化近似路线", out(EarthOnMinecraft.GRAPHITE_DUST::get, 1), out(EarthOnMinecraft.ACTIVATED_CARBON::get, 1)));

        recipes.add(rWithCoke(Kind.REDUCTION_FURNACE, EarthOnMinecraft.IRON_CONCENTRATE::get,
                "铁精矿焦炭还原；需要焦炭装料", out(EarthOnMinecraft.SPONGE_IRON::get, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(new Recipe(Kind.REDUCTION_FURNACE,
                List.of(in(EarthOnMinecraft.IRON_CONCENTRATE::get, 1), in(() -> Items.CHARCOAL, 1)),
                "铁精矿木炭快速还原（前期免焦炭高产路线）",
                List.of(out(EarthOnMinecraft.SPONGE_IRON::get, 2), out(EarthOnMinecraft.SLAG::get, 1)),
                false, 120, 48, "fast_charcoal_iron_concentrate",
                List.of(), List.of(), null, 1150, 101, "Fe3O4 + 2C(charcoal) -> 2 sponge_iron + slag"));
        recipes.add(new Recipe(Kind.REDUCTION_FURNACE,
                List.of(in(EarthOnMinecraft.MAGNETITE_DUST::get, 1), in(() -> Items.CHARCOAL, 1)),
                "磁铁矿粉木炭快速直接还原（免焦炭）",
                List.of(out(EarthOnMinecraft.SPONGE_IRON::get, 2), out(EarthOnMinecraft.SLAG::get, 1)),
                false, 120, 48, "fast_charcoal_magnetite_dust",
                List.of(), List.of(), null, 1150, 101, "Fe3O4 + C -> 2 sponge_iron"));
        recipes.add(new Recipe(Kind.REDUCTION_FURNACE,
                List.of(in(EarthOnMinecraft.HEMATITE_DUST::get, 1), in(() -> Items.CHARCOAL, 1)),
                "赤铁矿粉木炭快速直接还原（免焦炭）",
                List.of(out(EarthOnMinecraft.SPONGE_IRON::get, 2), out(EarthOnMinecraft.SLAG::get, 1)),
                false, 120, 48, "fast_charcoal_hematite_dust",
                List.of(), List.of(), null, 1150, 101, "Fe2O3 + C -> 2 sponge_iron"));
        recipes.add(r(Kind.SMELTER, EarthOnMinecraft.SPONGE_IRON::get,
                "海绵铁熔炼炉快速精炼（高产熟铁坯）",
                out(EarthOnMinecraft.WROUGHT_IRON::get, 2), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.ANVIL, EarthOnMinecraft.WROUGHT_IRON::get,
                "熟铁坯锻砧台锻造标准熟铁剑",
                out(SpecItems.BY_ID.get("iron_sword")::get, 1)));
        recipes.add(r(Kind.ANVIL, () -> Items.IRON_INGOT,
                "铁锭锻砧台锻造重型战锤",
                out(SpecItems.BY_ID.get("war_hammer")::get, 1)));
        recipes.add(r(Kind.REDUCTION_FURNACE, EarthOnMinecraft.ROASTED_COPPER_CONCENTRATE::get, "焙烧铜精矿还原", out(() -> Items.COPPER_INGOT, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.REDUCTION_FURNACE, EarthOnMinecraft.GOLD_CONCENTRATE::get, "金精矿熔炼", out(() -> Items.GOLD_INGOT, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.REDUCTION_FURNACE, EarthOnMinecraft.COAL_DUST::get, "煤粉压焦近似处理", out(() -> Items.COAL, 1)));
        recipes.add(r(Kind.REDUCTION_FURNACE, () -> Items.IRON_INGOT, "生铁到钢坯的简化精炼", out(EarthOnMinecraft.STEEL_BLOOM::get, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.REDUCTION_FURNACE, EarthOnMinecraft.SILICA_DUST::get, "硅石碳热还原制硅铁和冶金硅", out(EarthOnMinecraft.FERROSILICON::get, 1), out(EarthOnMinecraft.METALLURGICAL_SILICON::get, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.REDUCTION_FURNACE, EarthOnMinecraft.COKE::get, "焦炭高温还原气氛", out(EarthOnMinecraft.COAL_GAS_CELL::get, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.REDUCTION_FURNACE, EarthOnMinecraft.FERROSILICON::get, "硅铁脱氧辅助炼钢", out(EarthOnMinecraft.STEEL_BLOOM::get, 1), out(EarthOnMinecraft.SLAG::get, 1)));

        recipes.add(r(Kind.LEACHING_TANK, EarthOnMinecraft.AURIFEROUS_QUARTZ_CHUNK::get, "含金石英浸出", out(EarthOnMinecraft.GOLD_CONCENTRATE::get, 1), out(EarthOnMinecraft.SILICA_DUST::get, 2), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.LEACHING_TANK, EarthOnMinecraft.EMERALD_BERYL_VEIN::get, "绿柱石浸出富集", out(EarthOnMinecraft.BERYL_CONCENTRATE::get, 2), out(EarthOnMinecraft.QUARTZ_DUST::get, 1), out(EarthOnMinecraft.MICA_DUST::get, 1)));
        recipes.add(r(Kind.LEACHING_TANK, EarthOnMinecraft.REDSTONE_CONCENTRATE::get, "铁锰导电精矿浸出提纯", out(() -> Items.REDSTONE, 4), out(EarthOnMinecraft.SILICA_DUST::get, 1)));
        recipes.add(r(Kind.LEACHING_TANK, EarthOnMinecraft.MERCURY_DROPLET::get, "汞齐法金回收的安全化近似", out(EarthOnMinecraft.GOLD_CONCENTRATE::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.LEACHING_TANK, EarthOnMinecraft.BRINE_CRYSTAL::get, "盐卤锂钾富集", out(EarthOnMinecraft.LITHIUM_SALT::get, 1), out(EarthOnMinecraft.POTASSIUM_CHLORIDE::get, 1), out(EarthOnMinecraft.SALT_DUST::get, 1)));
        recipes.add(r(Kind.LEACHING_TANK, EarthOnMinecraft.MAFIC_SILICATE_DUST::get, "镁铁质硅酸盐镍伴生浸出", out(EarthOnMinecraft.NICKEL_PRECURSOR::get, 1), out(EarthOnMinecraft.IRON_CONCENTRATE::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.LEACHING_TANK, EarthOnMinecraft.RARE_EARTH_CARBONATITE::get, "稀土碳酸岩酸浸", out(EarthOnMinecraft.MIXED_RARE_EARTH_OXIDE::get, 2), out(EarthOnMinecraft.CALCIUM_CHLORIDE::get, 1), out(EarthOnMinecraft.RARE_EARTH_TAILINGS::get, 2)));
        recipes.add(r(Kind.LEACHING_TANK, EarthOnMinecraft.BAUXITE_LATERITE_DEPOSIT::get, "铝土矿风化壳碱浸", out(EarthOnMinecraft.ALUMINUM_HYDROXIDE::get, 2), out(EarthOnMinecraft.HEMATITE_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 2)));

        recipes.add(r(Kind.ELECTROLYTIC_CELL, EarthOnMinecraft.COPPER_CONCENTRATE::get, "铜精矿电积", out(() -> Items.COPPER_INGOT, 1), out(EarthOnMinecraft.SULFUR_DUST::get, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.ELECTROLYTIC_CELL, EarthOnMinecraft.GOLD_CONCENTRATE::get, "金精矿电解精炼", out(() -> Items.GOLD_INGOT, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.ELECTROLYTIC_CELL, EarthOnMinecraft.LAPIS_CONCENTRATE::get, "青金石湿法提纯", out(() -> Items.LAPIS_LAZULI, 3), out(EarthOnMinecraft.CALCITE_DUST::get, 1)));
        recipes.add(r(Kind.ELECTROLYTIC_CELL, EarthOnMinecraft.BERYL_CONCENTRATE::get, "绿柱石宝石分选", out(() -> Items.EMERALD, 1), out(EarthOnMinecraft.ALUMINOSILICATE_DUST::get, 2)));
        recipes.add(r(Kind.ELECTROLYTIC_CELL, () -> Items.WATER_BUCKET, "水电解气体回收", out(EarthOnMinecraft.HYDROGEN_GAS_CELL::get, 2), out(EarthOnMinecraft.OXYGEN_GAS_CELL::get, 1), out(() -> Items.BUCKET, 1)));
        recipes.add(r(Kind.ELECTROLYTIC_CELL, EarthOnMinecraft.REDSTONE_CONCENTRATE::get, "铁锰导电精矿锰氧化物电积", out(EarthOnMinecraft.MANGANESE_OXIDE_DUST::get, 1), out(() -> Items.REDSTONE, 2)));

        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.COAL_DUST::get, "煤粉压块", out(() -> Items.COAL, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.DIAMOND_GRIT::get, "金刚石砂粒压制", out(() -> Items.DIAMOND, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.QUARTZ_DUST::get, "石英粉压制", out(() -> Items.QUARTZ, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.REDSTONE_CONCENTRATE::get, "铁锰导电精矿压制", out(() -> Items.REDSTONE, 3)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.STEEL_BLOOM::get, "钢坯轧制为标准钢材与兼容铁锭", out(SpecItems.BY_ID.get("steel")::get, 1), out(() -> Items.IRON_INGOT, 1)));
        recipes.add(r(Kind.ANVIL, EarthOnMinecraft.STEEL_BLOOM::get, "钢坯锻打成精炼钢材", out(SpecItems.BY_ID.get("steel")::get, 1)));

        recipes.add(r(Kind.GAS_SEPARATOR, EarthOnMinecraft.COAL_DUST::get, "煤粉干馏分离", out(EarthOnMinecraft.COKE::get, 1), out(EarthOnMinecraft.COAL_TAR::get, 1), out(EarthOnMinecraft.COAL_GAS_CELL::get, 1)));
        recipes.add(r(Kind.GAS_SEPARATOR, EarthOnMinecraft.COAL_GAS_CELL::get, "煤气净化", out(EarthOnMinecraft.HYDROGEN_GAS_CELL::get, 1), out(EarthOnMinecraft.AMMONIA::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.GAS_SEPARATOR, EarthOnMinecraft.CHLORINE_GAS_CELL::get, "氯气吸收制酸", out(EarthOnMinecraft.HYDROCHLORIC_ACID::get, 1), out(EarthOnMinecraft.CALCIUM_CHLORIDE::get, 1)));
        recipes.add(r(Kind.GAS_SEPARATOR, () -> Items.GLASS_BOTTLE, "空气压缩分离", out(EarthOnMinecraft.NITROGEN_GAS_CELL::get, 3), out(EarthOnMinecraft.OXYGEN_GAS_CELL::get, 1)));
        recipes.add(r(Kind.GAS_SEPARATOR, EarthOnMinecraft.OXYGEN_GAS_CELL::get, "富氧尾气回收", out(EarthOnMinecraft.OXYGEN_GAS_CELL::get, 1), out(EarthOnMinecraft.NITROGEN_GAS_CELL::get, 1)));

        recipes.add(r(Kind.DISTILLATION_COLUMN, EarthOnMinecraft.COAL_TAR::get, "煤焦油分馏", out(EarthOnMinecraft.ETHYLENE::get, 1), out(EarthOnMinecraft.POLYMER_RESIN::get, 1), out(EarthOnMinecraft.CRUDE_OIL_SAMPLE::get, 1), out(EarthOnMinecraft.COKE::get, 1)));
        recipes.add(r(Kind.DISTILLATION_COLUMN, EarthOnMinecraft.CRUDE_OIL_SAMPLE::get, "原油常压分馏", out(EarthOnMinecraft.NATURAL_GAS_CELL::get, 1), out(EarthOnMinecraft.NAPHTHA::get, 2), out(EarthOnMinecraft.KEROSENE_FRACTION::get, 1), out(EarthOnMinecraft.DIESEL_FRACTION::get, 1), out(EarthOnMinecraft.LUBRICATING_OIL::get, 1), out(EarthOnMinecraft.ASPHALT::get, 1), out(EarthOnMinecraft.PETROLEUM_COKE::get, 1)));
        recipes.add(r(Kind.DISTILLATION_COLUMN, EarthOnMinecraft.BRINE_CRYSTAL::get, "盐卤浓缩", out(EarthOnMinecraft.SALT_DUST::get, 2), out(EarthOnMinecraft.CALCIUM_CHLORIDE::get, 1)));
        recipes.add(r(Kind.DISTILLATION_COLUMN, EarthOnMinecraft.HYDROCHLORIC_ACID::get, "盐酸精馏", out(EarthOnMinecraft.HYDROCHLORIC_ACID::get, 1), out(EarthOnMinecraft.SALT_DUST::get, 1)));
        recipes.add(r(Kind.STEAM_CRACKER, EarthOnMinecraft.COAL_TAR::get, "煤焦油裂解芳烃和烯烃", out(EarthOnMinecraft.BENZENE::get, 1), out(EarthOnMinecraft.ETHYLENE::get, 1), out(EarthOnMinecraft.PROPYLENE::get, 1), out(EarthOnMinecraft.COKE::get, 1)));
        recipes.add(r(Kind.STEAM_CRACKER, EarthOnMinecraft.NAPHTHA::get, "石脑油蒸汽裂解", out(EarthOnMinecraft.ETHYLENE::get, 2), out(EarthOnMinecraft.PROPYLENE::get, 1), out(EarthOnMinecraft.BENZENE::get, 1), out(EarthOnMinecraft.CARBON_BLACK::get, 1)));
        recipes.add(r(Kind.STEAM_CRACKER, EarthOnMinecraft.DIESEL_FRACTION::get, "柴油馏分深度裂化", out(EarthOnMinecraft.PROPYLENE::get, 2), out(EarthOnMinecraft.CARBON_BLACK::get, 1), out(EarthOnMinecraft.PETROLEUM_COKE::get, 1)));
        recipes.add(r(Kind.STEAM_CRACKER, EarthOnMinecraft.POLYMER_RESIN::get, "混合树脂热裂解回收单体", out(EarthOnMinecraft.ETHYLENE::get, 1), out(EarthOnMinecraft.PROPYLENE::get, 1)));
        recipes.add(r(Kind.STEAM_CRACKER, EarthOnMinecraft.COAL_GAS_CELL::get, "合成气整备用裂解入口", out(EarthOnMinecraft.HYDROGEN_GAS_CELL::get, 1), out(EarthOnMinecraft.CARBON_DIOXIDE_CELL::get, 1), out(EarthOnMinecraft.METHANOL::get, 1)));

        recipes.add(rChem(Kind.CHEMICAL_REACTOR, List.of(
                in(EarthOnMinecraft.SULFUR_DUST::get, 2),
                in(EarthOnMinecraft.OXYGEN_GAS_CELL::get, 3),
                in(SpecItems.BY_ID.get("distilled_water")::get, 2)),
                "接触法制硫酸：2S + 3O2 + 2H2O -> 2H2SO4",
                List.of(out(EarthOnMinecraft.SULFURIC_ACID::get, 2)),
                EarthOnMinecraft.VANADIUM_CATALYST::get, 720, 101, "2S + 3O2 + 2H2O -> 2H2SO4"));
        recipes.add(rChem(Kind.CHEMICAL_REACTOR, List.of(
                in(EarthOnMinecraft.SULFUR_DIOXIDE_CELL::get, 2),
                in(EarthOnMinecraft.OXYGEN_GAS_CELL::get, 1),
                in(SpecItems.BY_ID.get("distilled_water")::get, 2)),
                "接触法二氧化硫催化氧化吸收制硫酸：2SO2 + O2 + 2H2O -> 2H2SO4",
                List.of(out(EarthOnMinecraft.SULFURIC_ACID::get, 2)),
                EarthOnMinecraft.VANADIUM_CATALYST::get, 720, 101, "2SO2 + O2 + 2H2O -> 2H2SO4"));
        recipes.add(rChem(Kind.CHEMICAL_REACTOR, List.of(
                in(EarthOnMinecraft.AMMONIA::get, 1),
                in(EarthOnMinecraft.OXYGEN_GAS_CELL::get, 2)),
                "奥斯特瓦尔德法氨催化氧化制硝酸：NH3 + 2O2 -> HNO3 + H2O",
                List.of(out(EarthOnMinecraft.NITRIC_ACID::get, 1), out(SpecItems.BY_ID.get("distilled_water")::get, 1)),
                EarthOnMinecraft.PLATINUM_GROUP_CATALYST::get, 1120, 500, "NH3 + 2O2 -> HNO3 + H2O"));
        recipes.add(rChem(Kind.CHEMICAL_REACTOR, List.of(
                in(EarthOnMinecraft.NITRIC_ACID::get, 1),
                in(EarthOnMinecraft.AMMONIA::get, 1)),
                "硝酸与氨中和制硝酸铵：HNO3 + NH3 -> NH4NO3",
                List.of(out(EarthOnMinecraft.AMMONIUM_NITRATE::get, 1)),
                null, 353, 101, "HNO3 + NH3 -> NH4NO3"));
        recipes.add(rChem(Kind.CHEMICAL_REACTOR, List.of(
                in(EarthOnMinecraft.HYDROCHLORIC_ACID::get, 1),
                in(EarthOnMinecraft.SODIUM_HYDROXIDE::get, 1)),
                "盐酸与烧碱酸碱中和：HCl + NaOH -> NaCl + H2O",
                List.of(out(EarthOnMinecraft.SALT_DUST::get, 1), out(SpecItems.BY_ID.get("distilled_water")::get, 1)),
                null, 298, 101, "HCl + NaOH -> NaCl + H2O"));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.PHOSPHATE_ROCK_DUST::get, "磷矿酸解", out(EarthOnMinecraft.PHOSPHORIC_ACID::get, 1), out(EarthOnMinecraft.GYPSUM_DUST::get, 2)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.LIME_DUST::get, "石灰消化", out(EarthOnMinecraft.SLAKED_LIME::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.SODA_ASH::get, "碳酸氢钠转化", out(EarthOnMinecraft.SODIUM_BICARBONATE::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.SULFURIC_ACID::get, "硫酸盐副产物回收", out(EarthOnMinecraft.SODIUM_SULFATE::get, 1), out(EarthOnMinecraft.GYPSUM_DUST::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.CARBON_DIOXIDE_CELL::get, "二氧化碳制纯碱近似路线", out(EarthOnMinecraft.SODA_ASH::get, 1), out(EarthOnMinecraft.SODIUM_BICARBONATE::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.METHANOL::get, "甲醇氧化制甲醛", out(EarthOnMinecraft.FORMALDEHYDE::get, 1), out(EarthOnMinecraft.HYDROGEN_GAS_CELL::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.ETHYLENE::get, "乙烯氧化/氯化制塑料前驱体", out(EarthOnMinecraft.VINYL_CHLORIDE::get, 1), out(EarthOnMinecraft.ETHYLENE_GLYCOL::get, 1), out(EarthOnMinecraft.HYDROCHLORIC_ACID::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.POTASSIUM_CHLORIDE::get, "钾盐复分解制硝酸钾", out(EarthOnMinecraft.POTASSIUM_NITRATE::get, 1), out(EarthOnMinecraft.SALT_DUST::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.SODIUM_BICARBONATE::get, "小苏打热分解回收纯碱", out(EarthOnMinecraft.SODA_ASH::get, 1), out(EarthOnMinecraft.CARBON_DIOXIDE_CELL::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.BENZENE::get, "苯系树脂/纤维/溶剂前驱体近似路线", out(EarthOnMinecraft.STYRENE::get, 1), out(EarthOnMinecraft.CAPROLACTAM::get, 1), out(EarthOnMinecraft.PHENOL::get, 1), out(EarthOnMinecraft.INDUSTRIAL_SOLVENT::get, 1), out(EarthOnMinecraft.POLYMER_RESIN::get, 1), out(EarthOnMinecraft.HYDROGEN_GAS_CELL::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.SODIUM_SULFATE::get, "硫酸钠副产物制玻璃助熔剂", out(EarthOnMinecraft.GLASS_BATCH::get, 1), out(EarthOnMinecraft.SODA_ASH::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.PROPYLENE::get, "丙烯合成橡胶和丙酮近似路线", out(EarthOnMinecraft.SYNTHETIC_RUBBER::get, 1), out(EarthOnMinecraft.ACETONE::get, 1), out(EarthOnMinecraft.CARBON_BLACK::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.LUBRICATING_OIL::get, "润滑油氧化与炭黑回收", out(EarthOnMinecraft.CARBON_BLACK::get, 1), out(EarthOnMinecraft.PETROLEUM_COKE::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.WOOD_CHIPS::get, "木片碱法制浆", out(EarthOnMinecraft.CELLULOSE_PULP::get, 2), out(EarthOnMinecraft.CARBON_BLACK::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.CELLULOSE_PULP::get, "纤维素浆纤维化", out(EarthOnMinecraft.CELLULOSE_FIBER::get, 2), out(EarthOnMinecraft.BLEACHED_PULP::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.HEMATITE_DUST::get, "赤铁矿制氧化铁颜料", out(EarthOnMinecraft.IRON_OXIDE_PIGMENT::get, 2)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.CARBON_BLACK::get, "炭黑活化制电池碳", out(EarthOnMinecraft.ACTIVATED_CARBON::get, 1), out(EarthOnMinecraft.BATTERY_CARBON::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.LITHIUM_SALT::get, "锂盐配制电解液", out(EarthOnMinecraft.ELECTROLYTE::get, 1), out(EarthOnMinecraft.NEUTRAL_SALT::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, () -> Items.WATER_BUCKET, "硬水采样与桶回收", out(EarthOnMinecraft.HARD_WATER_SAMPLE::get, 1), out(() -> Items.BUCKET, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.HARD_WATER_SAMPLE::get, "石灰软化硬水", out(EarthOnMinecraft.SOFTENED_WATER::get, 1), out(EarthOnMinecraft.LIME_TREATMENT_RESIDUE::get, 1)));

        recipes.add(rChem(Kind.SYNTHESIS_LOOP, List.of(
                in(EarthOnMinecraft.NITROGEN_GAS_CELL::get, 1),
                in(EarthOnMinecraft.HYDROGEN_GAS_CELL::get, 3)),
                "哈柏-博施法高压催化合成氨：N2 + 3H2 -> 2NH3",
                List.of(out(EarthOnMinecraft.AMMONIA::get, 2)),
                EarthOnMinecraft.IRON_CATALYST::get, 723, 20000, "N2 + 3H2 -> 2NH3"));
        recipes.add(rChem(Kind.CHEMICAL_REACTOR, List.of(
                in(EarthOnMinecraft.NITROGEN_GAS_CELL::get, 1),
                in(EarthOnMinecraft.HYDROGEN_GAS_CELL::get, 3)),
                "哈柏-博施法催化合成氨：N2 + 3H2 -> 2NH3",
                List.of(out(EarthOnMinecraft.AMMONIA::get, 2)),
                EarthOnMinecraft.IRON_CATALYST::get, 723, 20000, "N2 + 3H2 -> 2NH3"));
        recipes.add(r(Kind.SYNTHESIS_LOOP, EarthOnMinecraft.COAL_GAS_CELL::get, "煤气合成甲醇", out(EarthOnMinecraft.METHANOL::get, 1), out(EarthOnMinecraft.CARBON_DIOXIDE_CELL::get, 1)));
        recipes.add(r(Kind.SYNTHESIS_LOOP, EarthOnMinecraft.AMMONIA::get, "氨和二氧化碳合成尿素", out(EarthOnMinecraft.UREA::get, 1), out(EarthOnMinecraft.CARBON_DIOXIDE_CELL::get, 1)));
        recipes.add(r(Kind.SYNTHESIS_LOOP, EarthOnMinecraft.FORMALDEHYDE::get, "甲醛树脂前驱体", out(EarthOnMinecraft.POLYMER_RESIN::get, 1), out(() -> Items.WATER_BUCKET, 1)));
        recipes.add(r(Kind.SYNTHESIS_LOOP, EarthOnMinecraft.NATURAL_GAS_CELL::get, "天然气重整制合成气", out(EarthOnMinecraft.HYDROGEN_GAS_CELL::get, 2), out(EarthOnMinecraft.METHANOL::get, 1), out(EarthOnMinecraft.CARBON_DIOXIDE_CELL::get, 1)));
        recipes.add(r(Kind.SYNTHESIS_LOOP, EarthOnMinecraft.ETHYLENE_GLYCOL::get, "聚酯酸料近似合成", out(EarthOnMinecraft.TEREPHTHALIC_ACID::get, 1), out(EarthOnMinecraft.METHANOL::get, 1)));

        recipes.add(rChem(Kind.ABSORPTION_TOWER, List.of(
                in(EarthOnMinecraft.SULFUR_DUST::get, 2),
                in(EarthOnMinecraft.OXYGEN_GAS_CELL::get, 3),
                in(SpecItems.BY_ID.get("distilled_water")::get, 2)),
                "接触法硫氧化吸收制硫酸：2S + 3O2 + 2H2O -> 2H2SO4",
                List.of(out(EarthOnMinecraft.SULFURIC_ACID::get, 2)),
                EarthOnMinecraft.VANADIUM_CATALYST::get, 720, 101, "2S + 3O2 + 2H2O -> 2H2SO4"));
        recipes.add(rChem(Kind.ABSORPTION_TOWER, List.of(
                in(EarthOnMinecraft.SULFUR_DIOXIDE_CELL::get, 2),
                in(EarthOnMinecraft.OXYGEN_GAS_CELL::get, 1),
                in(SpecItems.BY_ID.get("distilled_water")::get, 2)),
                "接触法二氧化硫催化氧化吸收：2SO2 + O2 + 2H2O -> 2H2SO4",
                List.of(out(EarthOnMinecraft.SULFURIC_ACID::get, 2)),
                EarthOnMinecraft.VANADIUM_CATALYST::get, 720, 101, "2SO2 + O2 + 2H2O -> 2H2SO4"));
        recipes.add(r(Kind.ABSORPTION_TOWER, EarthOnMinecraft.CHLORINE_GAS_CELL::get, "氯气吸收制盐酸", out(EarthOnMinecraft.HYDROCHLORIC_ACID::get, 1), out(EarthOnMinecraft.CALCIUM_CHLORIDE::get, 1)));
        recipes.add(r(Kind.ABSORPTION_TOWER, EarthOnMinecraft.CARBON_DIOXIDE_CELL::get, "碳酸化吸收", out(EarthOnMinecraft.SODA_ASH::get, 1), out(EarthOnMinecraft.SODIUM_BICARBONATE::get, 1)));
        recipes.add(r(Kind.ABSORPTION_TOWER, EarthOnMinecraft.CELLULOSE_PULP::get, "纸浆漂白洗涤", out(EarthOnMinecraft.BLEACHED_PULP::get, 2), out(EarthOnMinecraft.CALCIUM_CHLORIDE::get, 1)));
        recipes.add(r(Kind.ABSORPTION_TOWER, EarthOnMinecraft.ACTIVATED_CARBON::get, "活性炭吸附滤料制备", out(EarthOnMinecraft.ACTIVATED_CARBON_FILTER::get, 1), out(EarthOnMinecraft.SOFTENED_WATER::get, 1)));

        recipes.add(r(Kind.MIXER, EarthOnMinecraft.SILICA_DUST::get, "玻璃配合料混合", out(EarthOnMinecraft.GLASS_BATCH::get, 1), out(EarthOnMinecraft.SODA_ASH::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.CALCITE_DUST::get, "水泥生料配比", out(EarthOnMinecraft.CEMENT_RAW_MEAL::get, 2), out(EarthOnMinecraft.CLAY_DUST::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.PHOSPHORIC_ACID::get, "磷肥母料混合", out(EarthOnMinecraft.FERTILIZER_BLEND::get, 1), out(EarthOnMinecraft.GYPSUM_DUST::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.SLAKED_LIME::get, "石灰乳吸收", out(EarthOnMinecraft.CALCIUM_CHLORIDE::get, 1), out(EarthOnMinecraft.CEMENT_POWDER::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.POTASSIUM_CHLORIDE::get, "钾肥粗混", out(EarthOnMinecraft.POTASH::get, 1), out(EarthOnMinecraft.FERTILIZER_BLEND::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.UREA::get, "尿素复合肥混合", out(EarthOnMinecraft.FERTILIZER_BLEND::get, 2), out(EarthOnMinecraft.SODIUM_BICARBONATE::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.SLAG::get, "矿渣水泥掺合料", out(EarthOnMinecraft.CEMENT_RAW_MEAL::get, 1), out(EarthOnMinecraft.CEMENT_POWDER::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.POTASH::get, "钾肥复合肥混合", out(EarthOnMinecraft.FERTILIZER_BLEND::get, 2)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.ALUMINOSILICATE_DUST::get, "铝硅酸盐陶瓷坯料混合", out(EarthOnMinecraft.CEMENT_RAW_MEAL::get, 1), out(EarthOnMinecraft.CLAY_DUST::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.TITANIUM_DIOXIDE::get, "钛白粉白色涂料基料", out(EarthOnMinecraft.PAINT_BASE::get, 1), out(() -> Items.WHITE_DYE, 2)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.IRON_OXIDE_PIGMENT::get, "氧化铁红涂料基料", out(EarthOnMinecraft.PAINT_BASE::get, 1), out(() -> Items.RED_DYE, 2)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.CARBON_BLACK::get, "炭黑补强与黑色涂料基料", out(EarthOnMinecraft.PAINT_BASE::get, 1), out(() -> Items.BLACK_DYE, 2)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.PAINT_BASE::get, "通用涂料调和", out(() -> Items.WHITE_DYE, 1), out(() -> Items.RED_DYE, 1), out(() -> Items.BLACK_DYE, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.GRAPHITE_DUST::get, "石墨负极浆料混合", out(EarthOnMinecraft.ELECTRODE_SHEET::get, 1), out(EarthOnMinecraft.BATTERY_CARBON::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.MANGANESE_OXIDE_DUST::get, "锰系正极与铁锰导电粉混合", out(EarthOnMinecraft.ELECTRODE_SHEET::get, 1), out(EarthOnMinecraft.NICKEL_PRECURSOR::get, 1), out(() -> Items.REDSTONE, 2)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.CINNABAR_DUST::get, "辰砂-铁锰复合导电粉调配", out(() -> Items.REDSTONE, 4), out(EarthOnMinecraft.REDSTONE_CONCENTRATE::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.NICKEL_PRECURSOR::get, "镍系正极前驱体混合", out(EarthOnMinecraft.ELECTRODE_SHEET::get, 1), out(EarthOnMinecraft.MANGANESE_OXIDE_DUST::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.TAILINGS_DUST::get, "尾粉稳定化处理", out(EarthOnMinecraft.STABILIZED_TAILINGS::get, 1), out(EarthOnMinecraft.SLUDGE_CAKE::get, 1)));
        recipes.add(rChem(Kind.MIXER, List.of(
                in(EarthOnMinecraft.HYDROCHLORIC_ACID::get, 1),
                in(EarthOnMinecraft.SODIUM_HYDROXIDE::get, 1)),
                "盐酸与烧碱酸碱中和：HCl + NaOH -> NaCl + H2O",
                List.of(out(EarthOnMinecraft.SALT_DUST::get, 1), out(SpecItems.BY_ID.get("distilled_water")::get, 1)),
                null, 298, 101, "HCl + NaOH -> NaCl + H2O"));
        recipes.add(rChem(Kind.MIXER, List.of(
                in(EarthOnMinecraft.SODIUM_HYDROXIDE::get, 1),
                in(EarthOnMinecraft.HYDROCHLORIC_ACID::get, 1)),
                "烧碱与盐酸酸碱中和：NaOH + HCl -> NaCl + H2O",
                List.of(out(EarthOnMinecraft.NEUTRAL_SALT::get, 1), out(EarthOnMinecraft.SOFTENED_WATER::get, 1)),
                null, 298, 101, "HCl + NaOH -> NaCl + H2O"));

        recipes.add(r(Kind.CRYSTALLIZER, EarthOnMinecraft.SALT_DUST::get, "盐结晶复溶", out(EarthOnMinecraft.BRINE_CRYSTAL::get, 1)));
        recipes.add(r(Kind.CRYSTALLIZER, EarthOnMinecraft.SODIUM_HYDROXIDE::get, "烧碱结晶", out(EarthOnMinecraft.SODIUM_HYDROXIDE::get, 1)));
        recipes.add(r(Kind.CRYSTALLIZER, EarthOnMinecraft.AMMONIUM_NITRATE::get, "硝酸铵造粒前结晶", out(EarthOnMinecraft.AMMONIUM_NITRATE::get, 1)));
        recipes.add(r(Kind.CRYSTALLIZER, EarthOnMinecraft.GYPSUM_DUST::get, "石膏结晶", out(EarthOnMinecraft.GYPSUM_DUST::get, 2)));
        recipes.add(r(Kind.CRYSTALLIZER, EarthOnMinecraft.BRINE_CRYSTAL::get, "盐卤分级结晶", out(EarthOnMinecraft.SALT_DUST::get, 2), out(EarthOnMinecraft.POTASSIUM_CHLORIDE::get, 1), out(EarthOnMinecraft.CALCIUM_CHLORIDE::get, 1)));
        recipes.add(r(Kind.CRYSTALLIZER, EarthOnMinecraft.LIME_TREATMENT_RESIDUE::get, "水处理石灰渣结晶回收", out(EarthOnMinecraft.CALCITE_DUST::get, 1), out(EarthOnMinecraft.NEUTRAL_SALT::get, 1)));

        recipes.add(r(Kind.INDUSTRIAL_KILN, EarthOnMinecraft.GLASS_BATCH::get, "玻璃熔制", out(() -> Items.GLASS, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.INDUSTRIAL_KILN, EarthOnMinecraft.CEMENT_RAW_MEAL::get, "水泥熟料煅烧", out(EarthOnMinecraft.CEMENT_CLINKER::get, 1), out(EarthOnMinecraft.LIME_DUST::get, 1)));
        recipes.add(r(Kind.INDUSTRIAL_KILN, EarthOnMinecraft.CEMENT_CLINKER::get, "水泥粉磨", out(EarthOnMinecraft.CEMENT_POWDER::get, 2), out(EarthOnMinecraft.GYPSUM_DUST::get, 1)));
        recipes.add(r(Kind.INDUSTRIAL_KILN, EarthOnMinecraft.BAUXITE_DUST::get, "铝土矿煅烧", out(EarthOnMinecraft.ALUMINA::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.INDUSTRIAL_KILN, EarthOnMinecraft.CLAY_DUST::get, "黏土烧结制砖", out(() -> Items.BRICK, 2), out(EarthOnMinecraft.ALUMINOSILICATE_DUST::get, 1)));
        recipes.add(r(Kind.INDUSTRIAL_KILN, EarthOnMinecraft.FELDSPAR_DUST::get, "长石助熔制玻璃", out(() -> Items.GLASS, 1), out(EarthOnMinecraft.ALUMINOSILICATE_DUST::get, 1)));
        recipes.add(r(Kind.INDUSTRIAL_KILN, EarthOnMinecraft.ALUMINOSILICATE_DUST::get, "铝硅酸盐烧结成砖", out(() -> Items.BRICK, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.INDUSTRIAL_KILN, EarthOnMinecraft.SLUDGE_CAKE::get, "污泥饼烧结无害化", out(() -> Items.BRICK, 1), out(EarthOnMinecraft.STABILIZED_TAILINGS::get, 1)));

        recipes.add(rChem(Kind.ELECTROLYTIC_CELL, List.of(
                in(EarthOnMinecraft.SALT_DUST::get, 2),
                in(SpecItems.BY_ID.get("distilled_water")::get, 2)),
                "氯碱工业盐水电解：2NaCl + 2H2O -> 2NaOH + Cl2 + H2",
                List.of(
                        out(EarthOnMinecraft.SODIUM_HYDROXIDE::get, 2),
                        out(EarthOnMinecraft.CHLORINE_GAS_CELL::get, 1),
                        out(EarthOnMinecraft.HYDROGEN_GAS_CELL::get, 1)),
                null, 353, 101, "2NaCl + 2H2O -> 2NaOH + Cl2 + H2"));
        recipes.add(r(Kind.ELECTROLYTIC_CELL, EarthOnMinecraft.ALUMINUM_HYDROXIDE::get, "氢氧化铝电解前处理", out(EarthOnMinecraft.ALUMINA::get, 1), out(EarthOnMinecraft.HYDROGEN_GAS_CELL::get, 1)));
        recipes.add(r(Kind.ELECTROLYTIC_CELL, EarthOnMinecraft.ALUMINA::get, "氧化铝熔盐电解", out(EarthOnMinecraft.ALUMINUM_INGOT::get, 1), out(EarthOnMinecraft.OXYGEN_GAS_CELL::get, 1)));
        recipes.add(r(Kind.LEACHING_TANK, EarthOnMinecraft.BAUXITE_DUST::get, "拜耳法铝土矿浸出", out(EarthOnMinecraft.ALUMINUM_HYDROXIDE::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 2)));
        recipes.add(r(Kind.LEACHING_TANK, EarthOnMinecraft.CALCIUM_CHLORIDE::get, "氯化钙母液回收", out(EarthOnMinecraft.BRINE_CRYSTAL::get, 1), out(EarthOnMinecraft.SALT_DUST::get, 1)));

        recipes.add(r(Kind.FERTILIZER_GRANULATOR, EarthOnMinecraft.FERTILIZER_BLEND::get, "复合肥造粒", out(EarthOnMinecraft.FERTILIZER_BLEND::get, 2)));
        recipes.add(r(Kind.FERTILIZER_GRANULATOR, EarthOnMinecraft.AMMONIUM_NITRATE::get, "氮肥造粒", out(EarthOnMinecraft.FERTILIZER_BLEND::get, 1), out(EarthOnMinecraft.SODIUM_BICARBONATE::get, 1)));
        recipes.add(r(Kind.FERTILIZER_GRANULATOR, EarthOnMinecraft.PHOSPHATE_ROCK_DUST::get, "磷肥粗混", out(EarthOnMinecraft.FERTILIZER_BLEND::get, 1), out(EarthOnMinecraft.GYPSUM_DUST::get, 1)));
        recipes.add(r(Kind.FERTILIZER_GRANULATOR, EarthOnMinecraft.UREA::get, "尿素造粒", out(EarthOnMinecraft.FERTILIZER_BLEND::get, 2)));
        recipes.add(r(Kind.FERTILIZER_GRANULATOR, EarthOnMinecraft.POTASSIUM_NITRATE::get, "硝酸钾复合肥造粒", out(EarthOnMinecraft.FERTILIZER_BLEND::get, 2), out(EarthOnMinecraft.POTASH::get, 1)));

        recipes.add(r(Kind.POLYMERIZER, EarthOnMinecraft.ETHYLENE::get, "聚乙烯树脂聚合", out(EarthOnMinecraft.POLYETHYLENE_RESIN::get, 2)));
        recipes.add(r(Kind.POLYMERIZER, EarthOnMinecraft.COAL_TAR::get, "煤化工树脂前驱体", out(EarthOnMinecraft.POLYMER_RESIN::get, 1), out(EarthOnMinecraft.COKE::get, 1)));
        recipes.add(r(Kind.POLYMERIZER, EarthOnMinecraft.PROPYLENE::get, "聚丙烯树脂聚合", out(EarthOnMinecraft.POLYPROPYLENE_RESIN::get, 2)));
        recipes.add(r(Kind.POLYMERIZER, EarthOnMinecraft.VINYL_CHLORIDE::get, "PVC 树脂聚合", out(EarthOnMinecraft.PVC_RESIN::get, 2)));
        recipes.add(r(Kind.POLYMERIZER, EarthOnMinecraft.STYRENE::get, "聚苯乙烯树脂聚合", out(EarthOnMinecraft.POLYSTYRENE_RESIN::get, 2)));
        recipes.add(r(Kind.POLYMERIZER, EarthOnMinecraft.TEREPHTHALIC_ACID::get, "PET 聚酯树脂缩聚", out(EarthOnMinecraft.PET_RESIN::get, 2), out(EarthOnMinecraft.ETHYLENE_GLYCOL::get, 1)));
        recipes.add(r(Kind.POLYMERIZER, EarthOnMinecraft.CAPROLACTAM::get, "尼龙 6 开环聚合", out(EarthOnMinecraft.NYLON_FIBER::get, 2)));

        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.CEMENT_POWDER::get, "水泥粉压制为建筑块", out(() -> Items.STONE_BRICKS, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.POLYETHYLENE_RESIN::get, "聚乙烯拉丝成纤维", out(() -> Items.STRING, 2)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.POLYPROPYLENE_RESIN::get, "聚丙烯拉丝成纤维", out(() -> Items.STRING, 2)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.PVC_RESIN::get, "PVC 树脂压制为弹性兼容材料", out(() -> Items.SLIME_BALL, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.POLYSTYRENE_RESIN::get, "聚苯乙烯泡沫化兼容材料", out(() -> Items.SLIME_BALL, 1), out(() -> Items.WHITE_DYE, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.PET_RESIN::get, "PET 拉丝成兼容纤维", out(() -> Items.STRING, 2)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.SYNTHETIC_RUBBER::get, "合成橡胶压制为黏性材料", out(() -> Items.SLIME_BALL, 2), out(EarthOnMinecraft.CARBON_BLACK::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.NYLON_FIBER::get, "尼龙纤维整理成线", out(() -> Items.STRING, 3)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.CELLULOSE_FIBER::get, "纤维素纤维整理成线", out(() -> Items.STRING, 2)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.BLEACHED_PULP::get, "漂白浆抄纸", out(() -> Items.PAPER, 4)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.PETROLEUM_COKE::get, "石油焦压块为燃料兼容物", out(() -> Items.COAL, 1), out(EarthOnMinecraft.CARBON_BLACK::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.ASPHALT::get, "沥青压制为黑石路面材料", out(() -> Blocks.BLACKSTONE, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.LUBRICATING_OIL::get, "润滑油制黏性兼容材料", out(() -> Items.SLIME_BALL, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.SIMPLE_BATTERY_CELL::get, "简易电池拆解为红石兼容物", out(() -> Items.REDSTONE, 4), out(() -> Items.COPPER_INGOT, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.STABILIZED_TAILINGS::get, "稳定化尾矿压制为建筑石料", out(() -> Blocks.STONE, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.ACTIVATED_CARBON_FILTER::get, "废活性炭滤料压滤", out(EarthOnMinecraft.SLUDGE_CAKE::get, 1), out(EarthOnMinecraft.ACTIVATED_CARBON::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.NEUTRAL_SALT::get, "中和盐压制为盐粉", out(EarthOnMinecraft.SALT_DUST::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.SOFTENED_WATER::get, "软化水桶装回收", out(() -> Items.WATER_BUCKET, 1)));

        recipes.add(r(Kind.ELECTROLYTIC_CELL, EarthOnMinecraft.ELECTROLYTE::get, "电解液活化为电池单元", out(EarthOnMinecraft.SIMPLE_BATTERY_CELL::get, 1), out(EarthOnMinecraft.NEUTRAL_SALT::get, 1)));
        recipes.add(r(Kind.ELECTROLYTIC_CELL, EarthOnMinecraft.ELECTRODE_SHEET::get, "电极片装配电池单元", out(EarthOnMinecraft.SIMPLE_BATTERY_CELL::get, 1), out(EarthOnMinecraft.ELECTROLYTE::get, 1)));

        recipes.add(r(Kind.BALL_MILL, () -> Blocks.CLAY, "黏土粉磨", out(EarthOnMinecraft.CLAY_DUST::get, 4), out(EarthOnMinecraft.KAOLIN_DUST::get, 1), out(EarthOnMinecraft.ALUMINOSILICATE_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.DIRT, "普通土壤矿物和腐殖质分离", out(EarthOnMinecraft.CLAY_DUST::get, 1), out(EarthOnMinecraft.HUMUS_SAMPLE::get, 1), out(EarthOnMinecraft.SOIL_MINERAL_MIX::get, 1), out(EarthOnMinecraft.SALT_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.GRASS_BLOCK, "表土层采样和矿质分离", out(EarthOnMinecraft.HUMUS_SAMPLE::get, 2), out(EarthOnMinecraft.CLAY_DUST::get, 1), out(EarthOnMinecraft.SOIL_MINERAL_MIX::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.COARSE_DIRT, "粗颗粒土壤骨架分离", out(EarthOnMinecraft.SANDY_LOAM_SAMPLE::get, 2), out(EarthOnMinecraft.SOIL_MINERAL_MIX::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.ROOTED_DIRT, "根系土壤有机质分离", out(EarthOnMinecraft.HUMUS_SAMPLE::get, 2), out(EarthOnMinecraft.WOOD_CHIPS::get, 1), out(EarthOnMinecraft.CLAY_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.PODZOL, "灰化土有机质和铝硅酸盐分离", out(EarthOnMinecraft.HUMUS_SAMPLE::get, 2), out(EarthOnMinecraft.ALUMINOSILICATE_DUST::get, 1), out(EarthOnMinecraft.SOIL_MINERAL_MIX::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.MUD, "冲积泥质土分层", out(EarthOnMinecraft.ALLUVIAL_LOAM_SAMPLE::get, 2), out(EarthOnMinecraft.CLAY_DUST::get, 2), out(EarthOnMinecraft.HUMUS_SAMPLE::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Items.LEAF_LITTER, "枯叶腐殖化和纤维回收", out(EarthOnMinecraft.HUMUS_SAMPLE::get, 2), out(EarthOnMinecraft.CELLULOSE_FIBER::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Items.DEAD_BUSH, "枯灌木干质粉碎", out(EarthOnMinecraft.WOOD_CHIPS::get, 1), out(EarthOnMinecraft.POTASH::get, 1), out(EarthOnMinecraft.HUMUS_SAMPLE::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Items.BUSH, "灌木生物质粉碎", out(EarthOnMinecraft.CELLULOSE_FIBER::get, 1), out(EarthOnMinecraft.HUMUS_SAMPLE::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Items.SHORT_GRASS, "短草生物质粉碎", out(EarthOnMinecraft.HUMUS_SAMPLE::get, 1), out(EarthOnMinecraft.CELLULOSE_FIBER::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Items.FERN, "蕨类生物质粉碎", out(EarthOnMinecraft.HUMUS_SAMPLE::get, 1), out(EarthOnMinecraft.CELLULOSE_FIBER::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Items.DRY_SHORT_GRASS, "干草灰分和纤维回收", out(EarthOnMinecraft.CELLULOSE_FIBER::get, 1), out(EarthOnMinecraft.POTASH::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Items.DRY_TALL_GRASS, "高干草灰分和纤维回收", out(EarthOnMinecraft.CELLULOSE_FIBER::get, 2), out(EarthOnMinecraft.POTASH::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Items.BONE_MEAL, "骨粉磷酸盐富集", out(EarthOnMinecraft.PHOSPHATE_ROCK_DUST::get, 2), out(EarthOnMinecraft.CALCITE_DUST::get, 1)));
        recipes.add(r(Kind.SIEVE, () -> Blocks.SAND, "砂中重矿物筛分", out(EarthOnMinecraft.SILICA_DUST::get, 3), out(EarthOnMinecraft.BAUXITE_DUST::get, 1), out(EarthOnMinecraft.TITANIUM_DIOXIDE::get, 1), out(EarthOnMinecraft.MONAZITE_SAND::get, 1), out(EarthOnMinecraft.CASSITERITE_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.SIEVE, () -> Blocks.RED_SAND, "红砂铁氧化物和重矿物筛分", out(EarthOnMinecraft.SILICA_DUST::get, 3), out(EarthOnMinecraft.HEMATITE_DUST::get, 1), out(EarthOnMinecraft.TITANIUM_DIOXIDE::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.SIEVE, () -> Blocks.GRAVEL, "沙砾河床沉积物筛分", out(EarthOnMinecraft.SILICA_DUST::get, 2), out(EarthOnMinecraft.CALCITE_DUST::get, 1), out(EarthOnMinecraft.MAFIC_SILICATE_DUST::get, 1), out(EarthOnMinecraft.HEMATITE_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.SIEVE, EarthOnMinecraft.SANDY_LOAM_SAMPLE::get, "砂质壤土颗粒级配", out(EarthOnMinecraft.SILICA_DUST::get, 2), out(EarthOnMinecraft.CLAY_DUST::get, 1), out(EarthOnMinecraft.HUMUS_SAMPLE::get, 1), out(EarthOnMinecraft.SOIL_MINERAL_MIX::get, 1)));
        recipes.add(r(Kind.SIEVE, EarthOnMinecraft.ALLUVIAL_LOAM_SAMPLE::get, "冲积壤土矿物级配", out(EarthOnMinecraft.CLAY_DUST::get, 2), out(EarthOnMinecraft.CALCITE_DUST::get, 1), out(EarthOnMinecraft.HUMUS_SAMPLE::get, 1), out(EarthOnMinecraft.SALINE_SOIL_SAMPLE::get, 1)));
        recipes.add(r(Kind.SIEVE, EarthOnMinecraft.SOIL_MINERAL_MIX::get, "土壤矿物混合物精筛", out(EarthOnMinecraft.ALUMINOSILICATE_DUST::get, 1), out(EarthOnMinecraft.CALCITE_DUST::get, 1), out(EarthOnMinecraft.SILICA_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.CRYSTALLIZER, () -> Items.WATER_BUCKET, "盐水蒸发结晶", out(EarthOnMinecraft.BRINE_CRYSTAL::get, 1), out(() -> Items.BUCKET, 1)));
        recipes.add(r(Kind.CRYSTALLIZER, EarthOnMinecraft.HARD_WATER_SAMPLE::get, "硬水蒸发结垢", out(EarthOnMinecraft.IRRIGATION_MINERAL_DEPOSIT::get, 1), out(EarthOnMinecraft.CALCITE_DUST::get, 1)));
        recipes.add(r(Kind.LEACHING_TANK, EarthOnMinecraft.SALINE_SOIL_SAMPLE::get, "盐碱化土壤淋洗", out(EarthOnMinecraft.BRINE_CRYSTAL::get, 1), out(EarthOnMinecraft.CLAY_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.IRRIGATION_MINERAL_DEPOSIT::get, "灌溉结垢沉积物酸解", out(EarthOnMinecraft.CALCITE_DUST::get, 1), out(EarthOnMinecraft.GYPSUM_DUST::get, 1), out(EarthOnMinecraft.SALT_DUST::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, () -> Items.WILDFLOWERS, "野花植物色素浸提", out(() -> Items.YELLOW_DYE, 2), out(EarthOnMinecraft.CELLULOSE_FIBER::get, 1), out(EarthOnMinecraft.HUMUS_SAMPLE::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, () -> Items.PINK_PETALS, "粉色花瓣色素浸提", out(() -> Items.PINK_DYE, 2), out(EarthOnMinecraft.CELLULOSE_FIBER::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, () -> Items.CACTUS_FLOWER, "仙人掌花色素和蜡质回收", out(() -> Items.PINK_DYE, 1), out(EarthOnMinecraft.NATURAL_LATEX::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, () -> Items.KELP, "海藻灰碱盐提取", out(EarthOnMinecraft.SODA_ASH::get, 1), out(EarthOnMinecraft.POTASH::get, 1), out(EarthOnMinecraft.CELLULOSE_FIBER::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, () -> Items.DRIED_KELP, "干海藻碱盐浓缩", out(EarthOnMinecraft.SODA_ASH::get, 1), out(EarthOnMinecraft.POTASH::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.HUMUS_SAMPLE::get, "腐殖质和矿物质复配", out(EarthOnMinecraft.FERTILIZER_BLEND::get, 1), out(EarthOnMinecraft.SOIL_MINERAL_MIX::get, 1)));
        recipes.add(r(Kind.FERTILIZER_GRANULATOR, EarthOnMinecraft.SOIL_MINERAL_MIX::get, "土壤矿物改良剂造粒", out(EarthOnMinecraft.FERTILIZER_BLEND::get, 1), out(EarthOnMinecraft.CALCITE_DUST::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.HUMUS_SAMPLE::get, "腐殖质压回可用表土", out(() -> Blocks.DIRT, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.SANDY_LOAM_SAMPLE::get, "砂质壤土整理为泥土", out(() -> Blocks.DIRT, 1), out(EarthOnMinecraft.SILICA_DUST::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.ALLUVIAL_LOAM_SAMPLE::get, "冲积壤土整理为泥巴", out(() -> Blocks.MUD, 1), out(EarthOnMinecraft.CLAY_DUST::get, 1)));

        recipes.add(r(Kind.LEACHING_TANK, EarthOnMinecraft.WOOD_CHIPS::get, "木质原料胶乳/纤维并行浸提", out(EarthOnMinecraft.NATURAL_LATEX::get, 1), out(EarthOnMinecraft.CELLULOSE_PULP::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.NATURAL_LATEX::get, "天然胶乳凝聚", out(EarthOnMinecraft.RAW_RUBBER::get, 2), out(EarthOnMinecraft.SOFTENED_WATER::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.RAW_RUBBER::get, "粗橡胶硫化", out(EarthOnMinecraft.VULCANIZED_RUBBER::get, 1), out(EarthOnMinecraft.NEUTRAL_SALT::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.VULCANIZED_RUBBER::get, "硫化橡胶炭黑补强", out(EarthOnMinecraft.RUBBER_COMPOUND::get, 1), out(EarthOnMinecraft.CARBON_BLACK::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.SYNTHETIC_RUBBER::get, "合成橡胶配混", out(EarthOnMinecraft.RUBBER_COMPOUND::get, 1), out(EarthOnMinecraft.CARBON_BLACK::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.RUBBER_COMPOUND::get, "橡胶复合料压制密封件", out(EarthOnMinecraft.RUBBER_GASKET::get, 2), out(() -> Items.SLIME_BALL, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.RUBBER_GASKET::get, "密封圈兼容黏性材料", out(() -> Items.SLIME_BALL, 2)));

        recipes.add(r(Kind.CHEMICAL_REACTOR, () -> Items.WHEAT, "谷物发酵制乙醇", out(EarthOnMinecraft.ETHANOL::get, 1), out(EarthOnMinecraft.CARBON_DIOXIDE_CELL::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, () -> Items.SUGAR, "糖发酵制乙醇", out(EarthOnMinecraft.ETHANOL::get, 2), out(EarthOnMinecraft.CARBON_DIOXIDE_CELL::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.ETHANOL::get, "乙醇氧化制乙酸和溶剂", out(EarthOnMinecraft.ACETIC_ACID::get, 1), out(EarthOnMinecraft.INDUSTRIAL_SOLVENT::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.ACETIC_ACID::get, "乙酸盐化回收溶剂", out(EarthOnMinecraft.INDUSTRIAL_SOLVENT::get, 1), out(EarthOnMinecraft.NEUTRAL_SALT::get, 1)));
        recipes.add(r(Kind.SYNTHESIS_LOOP, EarthOnMinecraft.PHENOL::get, "苯酚甲醛树脂近似合成", out(EarthOnMinecraft.PHENOLIC_RESIN::get, 1), out(EarthOnMinecraft.EPOXY_RESIN::get, 1)));
        recipes.add(r(Kind.SYNTHESIS_LOOP, EarthOnMinecraft.ACETONE::get, "丙酮路线环氧树脂前驱", out(EarthOnMinecraft.EPOXY_RESIN::get, 1), out(EarthOnMinecraft.INDUSTRIAL_SOLVENT::get, 1)));
        recipes.add(r(Kind.POLYMERIZER, EarthOnMinecraft.PHENOLIC_RESIN::get, "酚醛树脂固化", out(EarthOnMinecraft.POLYMER_RESIN::get, 2), out(EarthOnMinecraft.CARBON_BLACK::get, 1)));
        recipes.add(r(Kind.POLYMERIZER, EarthOnMinecraft.EPOXY_RESIN::get, "环氧树脂固化", out(EarthOnMinecraft.POLYMER_RESIN::get, 2), out(EarthOnMinecraft.INDUSTRIAL_SOLVENT::get, 1)));
        recipes.add(r(Kind.ABSORPTION_TOWER, EarthOnMinecraft.INDUSTRIAL_SOLVENT::get, "工业溶剂回收净化", out(EarthOnMinecraft.ETHANOL::get, 1), out(EarthOnMinecraft.ACTIVATED_CARBON_FILTER::get, 1)));

        recipes.add(r(Kind.REDUCTION_FURNACE, EarthOnMinecraft.CHROMITE_DUST::get, "铬铁矿碳热还原", out(EarthOnMinecraft.FERROCHROME::get, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.REDUCTION_FURNACE, EarthOnMinecraft.MANGANESE_OXIDE_DUST::get, "锰氧化物还原制锰铁", out(EarthOnMinecraft.FERROMANGANESE::get, 1), out(EarthOnMinecraft.OXYGEN_GAS_CELL::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.STEEL_BLOOM::get, "钢坯合金化制不锈钢", out(EarthOnMinecraft.STAINLESS_STEEL_BLOOM::get, 1), out(EarthOnMinecraft.FERROCHROME::get, 1), out(EarthOnMinecraft.FERROMANGANESE::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.ALUMINUM_INGOT::get, "铝锭合金化", out(EarthOnMinecraft.ALUMINUM_ALLOY_BILLET::get, 1), out(EarthOnMinecraft.MAGNESIUM_DUST::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.STAINLESS_STEEL_BLOOM::get, "不锈钢坯轧制为兼容铁材", out(() -> Items.IRON_INGOT, 3)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.ALUMINUM_ALLOY_BILLET::get, "铝合金坯压制为轻质兼容金属", out(EarthOnMinecraft.ALUMINUM_INGOT::get, 2), out(() -> Items.IRON_INGOT, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.TITANIUM_DIOXIDE::get, "钛白粉氯化制钛中间物", out(EarthOnMinecraft.TITANIUM_SLAG::get, 1), out(EarthOnMinecraft.TITANIUM_TETRACHLORIDE::get, 1)));
        recipes.add(r(Kind.REDUCTION_FURNACE, EarthOnMinecraft.TITANIUM_TETRACHLORIDE::get, "四氯化钛镁还原近似路线", out(EarthOnMinecraft.TITANIUM_SPONGE::get, 1), out(EarthOnMinecraft.CALCIUM_CHLORIDE::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.TITANIUM_SPONGE::get, "海绵钛压制为轻质金属兼容物", out(EarthOnMinecraft.ALUMINUM_INGOT::get, 1), out(() -> Items.IRON_INGOT, 1)));

        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.METALLURGICAL_SILICON::get, "冶金硅氯化", out(EarthOnMinecraft.CHLOROSILANE::get, 1), out(EarthOnMinecraft.HYDROCHLORIC_ACID::get, 1)));
        recipes.add(r(Kind.DISTILLATION_COLUMN, EarthOnMinecraft.CHLOROSILANE::get, "氯硅烷精馏提纯", out(EarthOnMinecraft.HIGH_PURITY_SILICON::get, 1), out(EarthOnMinecraft.HYDROCHLORIC_ACID::get, 1)));
        recipes.add(r(Kind.CRYSTALLIZER, EarthOnMinecraft.HIGH_PURITY_SILICON::get, "高纯硅沉积为多晶硅", out(EarthOnMinecraft.POLYSILICON::get, 1), out(EarthOnMinecraft.CHLOROSILANE::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.POLYSILICON::get, "多晶硅切片", out(EarthOnMinecraft.SILICON_WAFER::get, 2)));
        recipes.add(r(Kind.CRYSTALLIZER, EarthOnMinecraft.REDSTONE_CONCENTRATE::get, "红石半导体掺杂粉结晶", out(EarthOnMinecraft.DOPANT_DUST::get, 1), out(() -> Items.REDSTONE, 2)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.EPOXY_RESIN::get, "树脂路线制光刻胶前驱体", out(EarthOnMinecraft.PHOTORESIST_PRECURSOR::get, 1), out(EarthOnMinecraft.INDUSTRIAL_SOLVENT::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.SILICON_WAFER::get, "晶圆掺杂与红石兼容化", out(() -> Items.REDSTONE, 3), out(() -> Items.QUARTZ, 2), out(EarthOnMinecraft.DOPANT_DUST::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.PHOTORESIST_PRECURSOR::get, "光刻胶前驱体涂布回收", out(EarthOnMinecraft.INDUSTRIAL_SOLVENT::get, 1), out(() -> Items.REDSTONE, 1)));

        recipes.add(r(Kind.LEACHING_TANK, EarthOnMinecraft.MONAZITE_SAND::get, "独居石酸浸稀土富集", out(EarthOnMinecraft.MIXED_RARE_EARTH_OXIDE::get, 1), out(EarthOnMinecraft.PHOSPHATE_ROCK_DUST::get, 1), out(EarthOnMinecraft.RARE_EARTH_TAILINGS::get, 1)));
        recipes.add(r(Kind.LEACHING_TANK, EarthOnMinecraft.BASTNASITE_DUST::get, "氟碳铈矿浸出", out(EarthOnMinecraft.MIXED_RARE_EARTH_OXIDE::get, 1), out(EarthOnMinecraft.CALCIUM_CHLORIDE::get, 1), out(EarthOnMinecraft.RARE_EARTH_TAILINGS::get, 1)));
        recipes.add(r(Kind.CRYSTALLIZER, EarthOnMinecraft.MIXED_RARE_EARTH_OXIDE::get, "混合稀土分级结晶", out(EarthOnMinecraft.NEODYMIUM_SALT::get, 1), out(EarthOnMinecraft.RARE_EARTH_TAILINGS::get, 1)));
        recipes.add(r(Kind.SYNTHESIS_LOOP, EarthOnMinecraft.NEODYMIUM_SALT::get, "钕铁硼磁材合成", out(EarthOnMinecraft.NDFEB_MAGNET::get, 1), out(EarthOnMinecraft.FERROSILICON::get, 1)));
        recipes.add(r(Kind.MAGNETIC_SEPARATOR, EarthOnMinecraft.NDFEB_MAGNET::get, "钕铁硼磁材回收为红石磁性组件", out(() -> Items.REDSTONE, 2), out(() -> Items.IRON_INGOT, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.RARE_EARTH_TAILINGS::get, "稀土尾渣稳定化", out(EarthOnMinecraft.STABILIZED_TAILINGS::get, 1), out(EarthOnMinecraft.NEUTRAL_SALT::get, 1)));

        recipes.add(r(Kind.MIXER, EarthOnMinecraft.HEMATITE_DUST::get, "铁基催化剂载体制备", out(EarthOnMinecraft.IRON_CATALYST::get, 1), out(EarthOnMinecraft.ALUMINOSILICATE_DUST::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.TITANIUM_SLAG::get, "钒钛系催化剂载体制备", out(EarthOnMinecraft.VANADIUM_CATALYST::get, 1), out(EarthOnMinecraft.SILICA_DUST::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.NICKEL_PRECURSOR::get, "镍基催化剂制备", out(EarthOnMinecraft.NICKEL_CATALYST::get, 1), out(EarthOnMinecraft.ALUMINA::get, 1)));
        recipes.add(r(Kind.LEACHING_TANK, EarthOnMinecraft.GOLD_CONCENTRATE::get, "金精矿铂族伴生回收", out(EarthOnMinecraft.PLATINUM_GROUP_CATALYST::get, 1), out(EarthOnMinecraft.GOLD_CONCENTRATE::get, 1)));

        recipes.add(r(Kind.ABSORPTION_TOWER, EarthOnMinecraft.CHLORINE_GAS_CELL::get, "氯气吸收制次氯酸盐和盐酸", out(EarthOnMinecraft.SODIUM_HYPOCHLORITE::get, 1), out(EarthOnMinecraft.HYDROCHLORIC_ACID::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.SODIUM_HYDROXIDE::get, "烧碱氯化制漂白液近似路线", out(EarthOnMinecraft.SODIUM_HYPOCHLORITE::get, 1), out(EarthOnMinecraft.NEUTRAL_SALT::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.SODIUM_HYPOCHLORITE::get, "次氯酸盐吸附成漂白粉", out(EarthOnMinecraft.BLEACHING_POWDER::get, 1), out(EarthOnMinecraft.SLAKED_LIME::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.HYDROGEN_GAS_CELL::get, "氢氧合成过氧化氢近似路线", out(EarthOnMinecraft.HYDROGEN_PEROXIDE::get, 1), out(EarthOnMinecraft.OXYGEN_GAS_CELL::get, 1)));
        recipes.add(r(Kind.ABSORPTION_TOWER, EarthOnMinecraft.HYDROGEN_PEROXIDE::get, "过氧化氢漂白纸浆", out(EarthOnMinecraft.BLEACHED_PULP::get, 1), out(EarthOnMinecraft.SOFTENED_WATER::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, () -> Items.WHEAT_SEEDS, "植物油皂化入口", out(EarthOnMinecraft.SOAP_BASE::get, 1), out(EarthOnMinecraft.GLYCEROL::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.SOAP_BASE::get, "皂基复配表面活性剂", out(EarthOnMinecraft.SURFACTANT::get, 1), out(EarthOnMinecraft.GLYCEROL::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.SURFACTANT::get, "洗涤剂粉体配混", out(EarthOnMinecraft.DETERGENT_POWDER::get, 2), out(EarthOnMinecraft.SODA_ASH::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.DETERGENT_POWDER::get, "洗涤剂整理为白色兼容染料", out(() -> Items.WHITE_DYE, 2), out(EarthOnMinecraft.SODIUM_SULFATE::get, 1)));

        recipes.add(r(Kind.STEAM_CRACKER, EarthOnMinecraft.ETHYLENE::get, "乙烯裂解副产丁二烯和丙烯腈入口", out(EarthOnMinecraft.BUTADIENE::get, 1), out(EarthOnMinecraft.ACRYLONITRILE::get, 1), out(EarthOnMinecraft.HYDROGEN_GAS_CELL::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.PROPYLENE::get, "丙烯氧化制亚克力单体", out(EarthOnMinecraft.ACRYLIC_MONOMER::get, 1), out(EarthOnMinecraft.ACETONE::get, 1)));
        recipes.add(r(Kind.POLYMERIZER, EarthOnMinecraft.ACRYLONITRILE::get, "丙烯腈丁二烯苯乙烯共聚近似", out(EarthOnMinecraft.ABS_RESIN::get, 1), out(EarthOnMinecraft.BUTADIENE::get, 1)));
        recipes.add(r(Kind.POLYMERIZER, EarthOnMinecraft.ACRYLIC_MONOMER::get, "亚克力树脂聚合", out(EarthOnMinecraft.ACRYLIC_RESIN::get, 2)));
        recipes.add(r(Kind.SYNTHESIS_LOOP, EarthOnMinecraft.FORMALDEHYDE::get, "甲醛路线制多元醇和脲醛树脂", out(EarthOnMinecraft.POLYOL::get, 1), out(EarthOnMinecraft.UREA_FORMALDEHYDE_RESIN::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.AMMONIA::get, "氨基路线制异氰酸酯中间物", out(EarthOnMinecraft.ISOCYANATE::get, 1), out(EarthOnMinecraft.UREA::get, 1)));
        recipes.add(r(Kind.POLYMERIZER, EarthOnMinecraft.POLYOL::get, "聚氨酯发泡", out(EarthOnMinecraft.POLYURETHANE_FOAM::get, 2), out(EarthOnMinecraft.ISOCYANATE::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.ABS_RESIN::get, "ABS 树脂压制为耐冲击兼容材料", out(() -> Items.SLIME_BALL, 1), out(EarthOnMinecraft.CARBON_BLACK::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.POLYURETHANE_FOAM::get, "聚氨酯泡沫整理为轻质纤维", out(() -> Items.STRING, 2), out(() -> Blocks.WHITE_WOOL, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.ACRYLIC_RESIN::get, "亚克力树脂压制为透明兼容板材", out(() -> Items.GLASS, 1), out(EarthOnMinecraft.INDUSTRIAL_SOLVENT::get, 1)));

        recipes.add(r(Kind.POWDER_PRESS, () -> Items.COPPER_INGOT, "铜锭拉丝", out(EarthOnMinecraft.COPPER_WIRE::get, 4)));
        recipes.add(r(Kind.INDUSTRIAL_KILN, EarthOnMinecraft.SILICA_DUST::get, "二氧化硅拉制玻璃纤维", out(EarthOnMinecraft.FIBERGLASS_CLOTH::get, 1), out(() -> Items.GLASS, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.FIBERGLASS_CLOTH::get, "玻纤环氧覆铜板基材", out(EarthOnMinecraft.COPPER_CLAD_LAMINATE::get, 1), out(EarthOnMinecraft.EPOXY_RESIN::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.COPPER_WIRE::get, "铜线蚀刻制印刷电路板", out(EarthOnMinecraft.PRINTED_CIRCUIT_BOARD::get, 1), out(EarthOnMinecraft.COPPER_CLAD_LAMINATE::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.PHOTORESIST_PRECURSOR::get, "光刻胶显影回收 PCB", out(EarthOnMinecraft.PRINTED_CIRCUIT_BOARD::get, 1), out(EarthOnMinecraft.INDUSTRIAL_SOLVENT::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.SOLDER_ALLOY::get, "焊料连接电子板", out(EarthOnMinecraft.PRINTED_CIRCUIT_BOARD::get, 1), out(EarthOnMinecraft.SOLDER_FLUX::get, 1)));
        recipes.add(r(Kind.CRYSTALLIZER, EarthOnMinecraft.MIXED_RARE_EARTH_OXIDE::get, "稀土荧光粉分级", out(EarthOnMinecraft.LED_PHOSPHOR::get, 1), out(EarthOnMinecraft.NEODYMIUM_SALT::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.PRINTED_CIRCUIT_BOARD::get, "电路板拆解为红石兼容件", out(() -> Items.REDSTONE, 4), out(EarthOnMinecraft.COPPER_WIRE::get, 2)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.CERAMIC_INSULATOR::get, "陶瓷绝缘件装配电子基板", out(EarthOnMinecraft.CERAMIC_SUBSTRATE::get, 1), out(EarthOnMinecraft.LED_PHOSPHOR::get, 1)));

        recipes.add(r(Kind.SIEVE, EarthOnMinecraft.TAILINGS_DUST::get, "尾粉伴生铅锌矿物回收", out(EarthOnMinecraft.SPHALERITE_DUST::get, 1), out(EarthOnMinecraft.GALENA_DUST::get, 1), out(EarthOnMinecraft.SILICA_DUST::get, 1)));
        recipes.add(r(Kind.FLOTATION_CELL, EarthOnMinecraft.SPHALERITE_DUST::get, "闪锌矿浮选焙烧入口", out(EarthOnMinecraft.ZINC_OXIDE::get, 1), out(EarthOnMinecraft.SULFUR_DUST::get, 1)));
        recipes.add(r(Kind.REDUCTION_FURNACE, EarthOnMinecraft.ZINC_OXIDE::get, "氧化锌还原制锌", out(EarthOnMinecraft.ZINC_INGOT::get, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.FLOTATION_CELL, EarthOnMinecraft.GALENA_DUST::get, "方铅矿浮选富集", out(EarthOnMinecraft.LEAD_INGOT::get, 1), out(EarthOnMinecraft.SULFUR_DUST::get, 1)));
        recipes.add(r(Kind.REDUCTION_FURNACE, EarthOnMinecraft.CASSITERITE_DUST::get, "锡石还原制锡", out(EarthOnMinecraft.TIN_INGOT::get, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.TIN_INGOT::get, "锡基低温焊料调配", out(EarthOnMinecraft.SOLDER_ALLOY::get, 1), out(EarthOnMinecraft.SOLDER_FLUX::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.ZINC_INGOT::get, "锌铝镀层兼容化", out(EarthOnMinecraft.ALUMINUM_ALLOY_BILLET::get, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.LEAD_INGOT::get, "铅锭安全封装为重金属兼容坯", out(EarthOnMinecraft.SOLDER_ALLOY::get, 1), out(EarthOnMinecraft.STABILIZED_TAILINGS::get, 1)));

        recipes.add(r(Kind.SIEVE, EarthOnMinecraft.CLAY_DUST::get, "黏土分级提纯高岭土", out(EarthOnMinecraft.KAOLIN_DUST::get, 2), out(EarthOnMinecraft.ALUMINOSILICATE_DUST::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.KAOLIN_DUST::get, "高岭土配制陶瓷坯料", out(EarthOnMinecraft.CERAMIC_BODY::get, 1), out(EarthOnMinecraft.FELDSPAR_DUST::get, 1)));
        recipes.add(r(Kind.INDUSTRIAL_KILN, EarthOnMinecraft.KAOLIN_DUST::get, "高岭土煅烧制耐火黏土", out(EarthOnMinecraft.REFRACTORY_CLAY::get, 1), out(EarthOnMinecraft.ALUMINA::get, 1)));
        recipes.add(r(Kind.INDUSTRIAL_KILN, EarthOnMinecraft.REFRACTORY_CLAY::get, "耐火黏土烧成耐火砖", out(EarthOnMinecraft.FIREBRICK::get, 2), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.INDUSTRIAL_KILN, EarthOnMinecraft.CERAMIC_BODY::get, "陶瓷坯体烧成瓷坯", out(EarthOnMinecraft.PORCELAIN_BLANK::get, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.PORCELAIN_BLANK::get, "瓷坯压制绝缘件", out(EarthOnMinecraft.CERAMIC_INSULATOR::get, 2)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.FIREBRICK::get, "耐火砖整理为兼容砖块", out(() -> Items.BRICK, 4)));
        recipes.add(r(Kind.INDUSTRIAL_KILN, EarthOnMinecraft.MAFIC_SILICATE_DUST::get, "镁铁质硅酸盐熔融纤维化", out(EarthOnMinecraft.MINERAL_WOOL::get, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.MINERAL_WOOL::get, "矿物棉整理为隔热纤维", out(() -> Items.STRING, 3), out(() -> Blocks.WHITE_WOOL, 1)));

        recipes.add(rChem(Kind.CHEMICAL_REACTOR, List.of(
                in(EarthOnMinecraft.SULFURIC_ACID::get, 1),
                in(EarthOnMinecraft.AMMONIA::get, 2)),
                "硫酸与氨中和制硫酸铵：H2SO4 + 2NH3 -> (NH4)2SO4",
                List.of(out(EarthOnMinecraft.AMMONIUM_SULFATE::get, 1)),
                null, 353, 101, "H2SO4 + 2NH3 -> (NH4)2SO4"));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.PHOSPHATE_ROCK_DUST::get, "普通过磷酸钙酸解", out(EarthOnMinecraft.SINGLE_SUPERPHOSPHATE::get, 1), out(EarthOnMinecraft.GYPSUM_DUST::get, 1)));
        recipes.add(r(Kind.FERTILIZER_GRANULATOR, EarthOnMinecraft.AMMONIUM_SULFATE::get, "硫酸铵造粒", out(EarthOnMinecraft.FERTILIZER_BLEND::get, 2)));
        recipes.add(r(Kind.FERTILIZER_GRANULATOR, EarthOnMinecraft.SINGLE_SUPERPHOSPHATE::get, "过磷酸钙造粒", out(EarthOnMinecraft.FERTILIZER_BLEND::get, 2), out(EarthOnMinecraft.GYPSUM_DUST::get, 1)));
        recipes.add(r(Kind.POLYMERIZER, EarthOnMinecraft.UREA_FORMALDEHYDE_RESIN::get, "脲醛树脂固化为木材胶黏剂", out(EarthOnMinecraft.POLYMER_RESIN::get, 2), out(EarthOnMinecraft.WOOD_CHIPS::get, 1)));
        recipes.add(r(Kind.STEAM_CRACKER, EarthOnMinecraft.COAL_GAS_CELL::get, "煤气重整为合成气", out(EarthOnMinecraft.SYNGAS_CELL::get, 1), out(EarthOnMinecraft.CARBON_MONOXIDE_CELL::get, 1), out(EarthOnMinecraft.HYDROGEN_GAS_CELL::get, 1)));
        recipes.add(r(Kind.SYNTHESIS_LOOP, EarthOnMinecraft.SYNGAS_CELL::get, "合成气路线制甲醇和氨", out(EarthOnMinecraft.METHANOL::get, 1), out(EarthOnMinecraft.AMMONIA::get, 1)));
        recipes.add(r(Kind.ABSORPTION_TOWER, EarthOnMinecraft.CARBON_MONOXIDE_CELL::get, "一氧化碳变换吸收", out(EarthOnMinecraft.CARBON_DIOXIDE_CELL::get, 1), out(EarthOnMinecraft.HYDROGEN_GAS_CELL::get, 1)));

        recipes.add(r(Kind.SIEVE, EarthOnMinecraft.BRINE_CRYSTAL::get, "盐卤硼酸盐伴生筛分", out(EarthOnMinecraft.BORATE_MINERAL_DUST::get, 1), out(EarthOnMinecraft.SALT_DUST::get, 1), out(EarthOnMinecraft.NEUTRAL_SALT::get, 1)));
        recipes.add(r(Kind.REDUCTION_FURNACE, EarthOnMinecraft.BORATE_MINERAL_DUST::get, "硼酸盐碳热制碳化硼近似路线", out(EarthOnMinecraft.BORON_CARBIDE_PELLET::get, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.SIEVE, EarthOnMinecraft.MONAZITE_SAND::get, "独居石伴生铀矿物筛分", out(EarthOnMinecraft.URANINITE_DUST::get, 1), out(EarthOnMinecraft.PHOSPHATE_ROCK_DUST::get, 1), out(EarthOnMinecraft.RARE_EARTH_TAILINGS::get, 1)));
        recipes.add(r(Kind.LEACHING_TANK, EarthOnMinecraft.RARE_EARTH_TAILINGS::get, "稀土尾渣铀矿物再浸出", out(EarthOnMinecraft.URANINITE_DUST::get, 1), out(EarthOnMinecraft.STABILIZED_TAILINGS::get, 1)));
        recipes.add(r(Kind.LEACHING_TANK, EarthOnMinecraft.URANINITE_DUST::get, "铀矿浸出制黄饼", out(EarthOnMinecraft.YELLOWCAKE::get, 1), out(EarthOnMinecraft.STABILIZED_TAILINGS::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.YELLOWCAKE::get, "黄饼转化为六氟化铀便携路线", out(EarthOnMinecraft.URANIUM_HEXAFLUORIDE_CELL::get, 1), out(EarthOnMinecraft.CALCIUM_CHLORIDE::get, 1)));
        recipes.add(r(Kind.GAS_SEPARATOR, EarthOnMinecraft.URANIUM_HEXAFLUORIDE_CELL::get, "同位素富集的游戏化分离", out(EarthOnMinecraft.LOW_ENRICHED_URANIUM::get, 1), out(EarthOnMinecraft.DEPLETED_URANIUM::get, 2)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.LOW_ENRICHED_URANIUM::get, "低浓缩铀转化为二氧化铀粉", out(EarthOnMinecraft.URANIUM_DIOXIDE_POWDER::get, 1), out(EarthOnMinecraft.NEUTRAL_SALT::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.URANIUM_DIOXIDE_POWDER::get, "二氧化铀陶瓷芯块压制", out(EarthOnMinecraft.NUCLEAR_FUEL_PELLET::get, 2)));
        recipes.add(r(Kind.INDUSTRIAL_KILN, EarthOnMinecraft.TITANIUM_SPONGE::get, "锆合金包壳管近似制造", out(EarthOnMinecraft.ZIRCONIUM_ALLOY_TUBE::get, 1), out(EarthOnMinecraft.TITANIUM_SPONGE::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.BORON_CARBIDE_PELLET::get, "碳化硼控制棒装配", out(EarthOnMinecraft.CONTROL_ROD_ASSEMBLY::get, 1), out(EarthOnMinecraft.CERAMIC_INSULATOR::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.NUCLEAR_FUEL_PELLET::get, "燃料芯块装入锆合金包壳", out(EarthOnMinecraft.NUCLEAR_FUEL_ROD::get, 1), out(EarthOnMinecraft.ZIRCONIUM_ALLOY_TUBE::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.NUCLEAR_FUEL_ROD::get, "燃料棒束装配燃料组件", out(EarthOnMinecraft.NUCLEAR_FUEL_ASSEMBLY::get, 1), out(EarthOnMinecraft.CONTROL_ROD_ASSEMBLY::get, 1)));
        recipes.add(r(Kind.SYNTHESIS_LOOP, EarthOnMinecraft.NUCLEAR_FUEL_ASSEMBLY::get, "核燃料组件产生稳定核热模块", out(EarthOnMinecraft.NUCLEAR_HEAT_MODULE::get, 1), out(EarthOnMinecraft.SPENT_FUEL_ASSEMBLY::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.SPENT_FUEL_ASSEMBLY::get, "乏燃料安全干式封装", out(EarthOnMinecraft.DRY_STORAGE_CASK::get, 1), out(EarthOnMinecraft.DEPLETED_URANIUM::get, 1)));

        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.COPPER_WIRE::get, "铜线压制为电力母排", out(EarthOnMinecraft.COPPER_BUSBAR::get, 2)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.COPPER_BUSBAR::get, "母排和铁芯组装变压器核心", out(EarthOnMinecraft.TRANSFORMER_CORE::get, 1), out(EarthOnMinecraft.CERAMIC_INSULATOR::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.TRANSFORMER_CORE::get, "变压器核心装配开关柜", out(EarthOnMinecraft.GRID_SWITCHGEAR::get, 1), out(EarthOnMinecraft.PRINTED_CIRCUIT_BOARD::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.COPPER_WIRE::get, "铜线绕制发电机定子", out(EarthOnMinecraft.GENERATOR_STATOR::get, 1), out(EarthOnMinecraft.NDFEB_MAGNET::get, 1)));
        recipes.add(r(Kind.INDUSTRIAL_KILN, EarthOnMinecraft.STEEL_BLOOM::get, "钢坯制造蒸汽轮机转子/壳体", out(EarthOnMinecraft.STEAM_TURBINE_ASSEMBLY::get, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.NUCLEAR_HEAT_MODULE::get, "核热模块接入蒸汽轮机和发电机前置", out(EarthOnMinecraft.STEAM_TURBINE_ASSEMBLY::get, 1), out(EarthOnMinecraft.GENERATOR_STATOR::get, 1), out(EarthOnMinecraft.GRID_SWITCHGEAR::get, 1)));

        recipes.add(r(Kind.MIXER, EarthOnMinecraft.PRINTED_CIRCUIT_BOARD::get, "工业传感和 PLC 控制器装配", out(EarthOnMinecraft.INDUSTRIAL_SENSOR::get, 1), out(EarthOnMinecraft.PLC_CONTROLLER::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.NDFEB_MAGNET::get, "钕铁硼磁体制伺服电机", out(EarthOnMinecraft.SERVO_MOTOR::get, 1), out(EarthOnMinecraft.COPPER_WIRE::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.SERVO_MOTOR::get, "伺服电机装配执行器和机械臂", out(EarthOnMinecraft.ACTUATOR_MODULE::get, 1), out(EarthOnMinecraft.ROBOTIC_ARM::get, 1)));
        recipes.add(r(Kind.CRYSTALLIZER, EarthOnMinecraft.SILICON_WAFER::get, "硅晶圆制视觉传感器芯片", out(EarthOnMinecraft.MACHINE_VISION_CAMERA::get, 1), out(EarthOnMinecraft.DOPANT_DUST::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.MACHINE_VISION_CAMERA::get, "视觉检测模块装配", out(EarthOnMinecraft.QUALITY_INSPECTION_MODULE::get, 1), out(EarthOnMinecraft.LED_PHOSPHOR::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.PLC_CONTROLLER::get, "PLC 接入自动化总线和红石网关", out(EarthOnMinecraft.AUTOMATION_BUS::get, 1), out(EarthOnMinecraft.REDSTONE_IO_GATEWAY::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.ROBOTIC_ARM::get, "机械臂改装为输送驱动机构", out(EarthOnMinecraft.CONVEYOR_DRIVE::get, 1), out(EarthOnMinecraft.ACTUATOR_MODULE::get, 1)));
        recipes.add(new Recipe(Kind.GLASS_KILN,
                List.of(in(() -> Items.GLASS, 4), in(EarthOnMinecraft.BORATE_MINERAL_DUST::get, 1)),
                "光学硼硅玻璃精熔退火",
                List.of(out(EarthOnMinecraft.OPTICAL_GLASS::get, 2)),
                false, 600, 0, "optical_glass_kiln",
                List.of(), List.of(), null, 1500, 101, "SiO2 + B2O3 -> optical_glass"));

        // ---- Early-to-mid playable progression loop closures ----
        // 1. Borate deposit comminution for optical glass
        recipes.add(r(Kind.CRUSHER, SpecBlocks.BY_ID.get("borate_deposit")::get,
                "硼砂矿床破碎富集", out(EarthOnMinecraft.BORATE_MINERAL_DUST::get, 3), out(EarthOnMinecraft.SALT_DUST::get, 1)));
        recipes.add(r(Kind.CRUSHER, SpecBlocks.BY_ID.get("chromite_ore")::get,
                "铬铁矿破碎", out(EarthOnMinecraft.CHROMITE_DUST::get, 3), out(EarthOnMinecraft.MAFIC_SILICATE_DUST::get, 1)));
        recipes.add(r(Kind.CRUSHER, SpecBlocks.BY_ID.get("galena_ore")::get,
                "方铅矿破碎", out(EarthOnMinecraft.GALENA_DUST::get, 3), out(EarthOnMinecraft.PYRITE_DUST::get, 1)));
        recipes.add(r(Kind.CRUSHER, SpecBlocks.BY_ID.get("sphalerite_ore")::get,
                "闪锌矿破碎", out(EarthOnMinecraft.SPHALERITE_DUST::get, 3), out(EarthOnMinecraft.PYRITE_DUST::get, 1)));
        recipes.add(r(Kind.ROASTER, EarthOnMinecraft.SPHALERITE_DUST::get,
                "闪锌矿氧化焙烧", out(EarthOnMinecraft.ZINC_OXIDE::get, 2), out(EarthOnMinecraft.SULFUR_DUST::get, 1)));
        recipes.add(r(Kind.REDUCTION_FURNACE, EarthOnMinecraft.GALENA_DUST::get,
                "方铅矿直接还原炼铅", out(EarthOnMinecraft.LEAD_INGOT::get, 1), out(EarthOnMinecraft.SLAG::get, 1)));

        // 2. Jade & gemstone rough extraction + grinding wheels & carving tool on Anvil
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.EMERALD_BERYL_VEIN::get,
                "绿柱石玉脉粗碎提取玉石原石", out(SpecItems.BY_ID.get("gem_rough")::get, 2), out(EarthOnMinecraft.QUARTZ_DUST::get, 2)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.LAPIS_LAZULI_ORE::get,
                "青金石矿脉粗碎", out(() -> Items.LAPIS_LAZULI, 2), out(SpecItems.BY_ID.get("gem_rough")::get, 1), out(EarthOnMinecraft.CALCITE_DUST::get, 1)));
        recipes.add(r(Kind.ANVIL, () -> Items.EMERALD,
                "绿宝石解理整形为玉石毛坯", out(SpecItems.BY_ID.get("gem_rough")::get, 2)));
        recipes.add(r(Kind.ANVIL, () -> Items.AMETHYST_SHARD,
                "紫水晶整形为宝石毛坯", out(SpecItems.BY_ID.get("gem_rough")::get, 1)));
        recipes.add(new Recipe(Kind.ANVIL,
                List.of(in(() -> Items.IRON_INGOT, 1), in(EarthOnMinecraft.SILICA_DUST::get, 2)),
                "锻打粗磨砂轮（硅砂烧结包镶）",
                List.of(out(SpecItems.BY_ID.get("grinding_wheel_coarse")::get, 1)),
                false, 80, 0, "early_grinding_wheel_coarse"));
        recipes.add(new Recipe(Kind.ANVIL,
                List.of(in(() -> Items.IRON_INGOT, 1), in(EarthOnMinecraft.QUARTZ_DUST::get, 2)),
                "锻打细磨砂轮（细石英砂）",
                List.of(out(SpecItems.BY_ID.get("grinding_wheel_fine")::get, 1)),
                false, 80, 0, "early_grinding_wheel_fine"));
        recipes.add(new Recipe(Kind.ANVIL,
                List.of(in(() -> Items.IRON_INGOT, 1), in(EarthOnMinecraft.DIAMOND_GRIT::get, 1)),
                "锻打金刚石抛光轮",
                List.of(out(SpecItems.BY_ID.get("grinding_wheel_polish")::get, 1)),
                false, 100, 0, "early_grinding_wheel_polish"));
        recipes.add(new Recipe(Kind.ANVIL,
                List.of(in(EarthOnMinecraft.WROUGHT_IRON::get, 1), in(EarthOnMinecraft.QUARTZ_DUST::get, 1)),
                "锻打玉雕砣刀",
                List.of(out(SpecItems.BY_ID.get("gem_carving_tool")::get, 1)),
                false, 80, 0, "early_gem_carving_tool"));
        recipes.add(r(Kind.ANVIL, () -> Items.COPPER_INGOT,
                "铜锭手工延展拉制铜线", out(EarthOnMinecraft.COPPER_WIRE::get, 3)));

        // 3. Masonry, textile & leather early loop closures
        recipes.add(r(Kind.CARPENTRY_BENCH, () -> Blocks.DIRT,
                "夯筑夯土块（窑炉基础砌体）", out(SpecBlocks.BY_ID.get("packed_earth")::get, 2)));
        recipes.add(r(Kind.MILL, () -> Items.WHEAT,
                "原版小麦研磨成面粉与植物纤维", out(SpecItems.BY_ID.get("flour")::get, 2), out(EarthOnMinecraft.CELLULOSE_FIBER::get, 1)));
        recipes.add(r(Kind.STONE_MILL, () -> Items.WHEAT,
                "石磨细磨原版小麦成面粉", out(SpecItems.BY_ID.get("flour")::get, 2)));
        recipes.add(r(Kind.NOODLE_PRESS, SpecItems.BY_ID.get("flour")::get,
                "面粉揉制面团", out(SpecItems.BY_ID.get("dough")::get, 2)));
        recipes.add(r(Kind.LOOM, EarthOnMinecraft.CELLULOSE_FIBER::get,
                "植物纤维纺制亚麻线", out(SpecItems.BY_ID.get("flax_thread")::get, 2)));
        recipes.add(r(Kind.LOOM, () -> Items.STRING,
                "原版线纺制紧实亚麻线", out(SpecItems.BY_ID.get("flax_thread")::get, 2)));
        recipes.add(r(Kind.LOOM, () -> Blocks.WHITE_WOOL,
                "羊毛织造急救绷带", out(SpecItems.BY_ID.get("bandage")::get, 3)));
        recipes.add(r(Kind.TANNERY, () -> Items.LEATHER,
                "原版皮革鞣制整理", out(SpecItems.BY_ID.get("leather")::get, 2)));

        // 4. Functional herbal medicines & tablet pressing (all 10 real medicines craftable early)
        recipes.add(new Recipe(Kind.HERB_PROCESSING_TABLE,
                List.of(in(SpecItems.BY_ID.get("honeysuckle")::get, 1), in(SpecItems.BY_ID.get("beeswax")::get, 1)),
                "金银花蜂蜡熬制抗菌药膏",
                List.of(out(SpecItems.BY_ID.get("antibiotic_ointment")::get, 2)),
                false, 80, 0, "early_antibiotic_ointment_honeysuckle"));
        recipes.add(new Recipe(Kind.HERB_PROCESSING_TABLE,
                List.of(in(SpecItems.BY_ID.get("coptis")::get, 1), in(() -> Items.HONEYCOMB, 1)),
                "黄连蜜蜡熬制抗菌药膏",
                List.of(out(SpecItems.BY_ID.get("antibiotic_ointment")::get, 2)),
                false, 80, 0, "early_antibiotic_ointment_coptis"));
        recipes.add(new Recipe(Kind.HERB_PROCESSING_TABLE,
                List.of(in(SpecItems.BY_ID.get("artemisia_annua")::get, 1), in(SpecItems.BY_ID.get("beeswax")::get, 1)),
                "青蒿蜂蜡熬制清凉烫伤膏",
                List.of(out(SpecItems.BY_ID.get("burn_ointment")::get, 2)),
                false, 80, 0, "early_burn_ointment_artemisia"));
        recipes.add(new Recipe(Kind.HERB_PROCESSING_TABLE,
                List.of(in(SpecItems.BY_ID.get("licorice")::get, 1), in(() -> Items.HONEYCOMB, 1)),
                "甘草蜜蜡熬制烫伤膏",
                List.of(out(SpecItems.BY_ID.get("burn_ointment")::get, 2)),
                false, 80, 0, "early_burn_ointment_licorice"));
        recipes.add(new Recipe(Kind.HERB_PROCESSING_TABLE,
                List.of(in(EarthOnMinecraft.SALT_DUST::get, 1), in(() -> Items.GLASS_BOTTLE, 1)),
                "精盐煮沸配制无菌生理盐水",
                List.of(out(SpecItems.BY_ID.get("saline_solution")::get, 2)),
                false, 60, 0, "early_saline_solution"));
        recipes.add(new Recipe(Kind.HERB_PROCESSING_TABLE,
                List.of(in(EarthOnMinecraft.GYPSUM_DUST::get, 2), in(SpecItems.BY_ID.get("bandage")::get, 1)),
                "熟石膏绷带调配正骨石膏夹",
                List.of(out(SpecItems.BY_ID.get("plaster")::get, 2)),
                false, 80, 0, "early_plaster_herb_table"));
        recipes.add(new Recipe(Kind.HERB_PROCESSING_TABLE,
                List.of(in(() -> Items.STICK, 2), in(SpecItems.BY_ID.get("bandage")::get, 1)),
                "木条绷带绑扎急救夹板",
                List.of(out(SpecItems.BY_ID.get("splint")::get, 2)),
                false, 60, 0, "early_splint_herb_table"));

        recipes.add(new Recipe(Kind.TABLET_PRESS,
                List.of(in(() -> Items.CHARCOAL, 1), in(() -> Items.SUGAR, 1)),
                "木炭粉加糖辅料压制活性炭解毒片",
                List.of(out(SpecItems.BY_ID.get("activated_charcoal_tablet")::get, 3)),
                false, 40, 3, "early_press_charcoal_tablet"));
        recipes.add(new Recipe(Kind.TABLET_PRESS,
                List.of(in(SpecItems.BY_ID.get("licorice")::get, 1), in(() -> Items.SUGAR, 1)),
                "草本水杨苷提取压制阿司匹林镇痛片",
                List.of(out(SpecItems.BY_ID.get("aspirin")::get, 2)),
                false, 40, 3, "early_press_aspirin"));
        recipes.add(new Recipe(Kind.TABLET_PRESS,
                List.of(in(SpecItems.BY_ID.get("hawthorn_fruit")::get, 1), in(() -> Items.SUGAR, 1)),
                "山楂果酸压制复合维生素片",
                List.of(out(SpecItems.BY_ID.get("vitamin_tablet")::get, 3)),
                false, 40, 3, "early_press_vitamin_hawthorn"));
        recipes.add(new Recipe(Kind.TABLET_PRESS,
                List.of(in(() -> Items.APPLE, 1), in(() -> Items.SUGAR, 1)),
                "鲜果提取物压制维生素片",
                List.of(out(SpecItems.BY_ID.get("vitamin_tablet")::get, 2)),
                false, 40, 3, "early_press_vitamin_apple"));
        recipes.add(new Recipe(Kind.TABLET_PRESS,
                List.of(in(EarthOnMinecraft.SALT_DUST::get, 1), in(() -> Items.SUGAR, 1)),
                "氯化钠与糖定量压制口服补液盐",
                List.of(out(SpecItems.BY_ID.get("oral_rehydration_salts")::get, 3)),
                false, 40, 3, "early_press_ors"));

        return List.copyOf(recipes);
    }

    private static Recipe r(Kind kind, Supplier<? extends ItemLike> input, String note, Output... outputs) {
        return new Recipe(kind, List.of(new Input(input, 1)), note, List.of(outputs), false, 0, 0, "");
    }

    private static Recipe rWithCoke(Kind kind, Supplier<? extends ItemLike> input, String note, Output... outputs) {
        return new Recipe(kind, List.of(new Input(input, 1)), note, List.of(outputs), true, 0, 0, "");
    }

    private static Recipe rChem(Kind kind, List<Input> inputs, String note, List<Output> outputs,
                                @Nullable Supplier<? extends ItemLike> catalyst,
                                int temperatureK, int pressureKpa, String equation) {
        return new Recipe(kind, inputs, note, outputs, false, 0, 0, "",
                List.of(), List.of(), catalyst, temperatureK, pressureKpa, equation);
    }

    private static Input in(Supplier<? extends ItemLike> item, int count) {
        return new Input(item, count);
    }

    private static Output out(Supplier<? extends ItemLike> item, int count) {
        return new Output(item, count);
    }

    public record Input(Supplier<? extends ItemLike> item, int count) {
        public ItemStack stack() {
            return new ItemStack(item.get().asItem(), Math.max(1, count));
        }
    }

    /**
     * A machine route. {@code inputs} is ordered: the first item drives route selection, the
     * remaining entries are consumed from the secondary input slots.
     * {@code fluidInputs} and {@code fluidOutputs} are matched against the machine's own fluid tank
     * in {@code mB}; a route with neither is an ordinary item-only route.
     * {@code ticks} and {@code energyPerTick} override the machine defaults when greater than zero,
     * because a single machine runs processes of very different durations in the spec.
     */
    public record Recipe(Kind kind, List<Input> inputs, String note, List<Output> outputs,
                         boolean requiresCoke, int ticks, int energyPerTick, String sourceId,
                         List<FluidInput> fluidInputs, List<FluidOutput> fluidOutputs,
                         @Nullable Supplier<? extends ItemLike> catalyst,
                         int recipeTemperatureK, int pressureKpa, String equation) {
        public Recipe {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
            fluidInputs = List.copyOf(fluidInputs);
            fluidOutputs = List.copyOf(fluidOutputs);
            equation = equation == null ? "" : equation;
        }

        public Recipe(Kind kind, List<Input> inputs, String note, List<Output> outputs,
                      boolean requiresCoke, int ticks, int energyPerTick, String sourceId,
                      List<FluidInput> fluidInputs, List<FluidOutput> fluidOutputs) {
            this(kind, inputs, note, outputs, requiresCoke, ticks, energyPerTick, sourceId,
                    fluidInputs, fluidOutputs, null, 0, 101, "");
        }

        /** Item-only route. */
        public Recipe(Kind kind, List<Input> inputs, String note, List<Output> outputs,
                      boolean requiresCoke, int ticks, int energyPerTick, String sourceId) {
            this(kind, inputs, note, outputs, requiresCoke, ticks, energyPerTick, sourceId,
                    List.of(), List.of(), null, 0, 101, "");
        }

        public boolean usesFluid() {
            return !fluidInputs.isEmpty() || !fluidOutputs.isEmpty();
        }

        public boolean hasCatalyst() {
            return catalyst != null;
        }

        public ItemStack catalystStack() {
            return catalyst == null ? ItemStack.EMPTY : new ItemStack(catalyst.get().asItem(), 1);
        }

        public int requiredPressureKpa() {
            return pressureKpa > 0 ? pressureKpa : 101;
        }

        public boolean hasEquation() {
            return equation != null && !equation.isEmpty();
        }

        public Supplier<? extends ItemLike> input() {
            return inputs.isEmpty() ? null : inputs.getFirst().item();
        }

        public ItemStack inputStack() {
            return inputs.isEmpty() ? ItemStack.EMPTY : inputs.getFirst().stack();
        }

        public int primaryCount() {
            return inputs.isEmpty() ? 0 : inputs.getFirst().count();
        }

        public boolean hasItemInput() {
            return !inputs.isEmpty();
        }

        public boolean hasSecondaryInput() {
            return inputs.size() > 1;
        }

        public List<Input> secondaryInputs() {
            return inputs.size() > 1 ? inputs.subList(1, inputs.size()) : List.of();
        }

        public int processTicks() {
            return ticks > 0 ? ticks : kind.processTicks();
        }

        public int energyPerTickEffective() {
            return energyPerTick > 0 ? energyPerTick : kind.energyPerTick();
        }

        public int requiredTemperatureK() {
            if (recipeTemperatureK > 0) {
                return recipeTemperatureK;
            }
            if (kind == Kind.SMELTER || kind == Kind.SHAFT_FURNACE) {
                for (FluidOutput fluid : fluidOutputs) {
                    String id = fluid.fluidId();
                    if (id.endsWith("molten_tin")) return 510;
                    if (id.endsWith("molten_lead")) return 610;
                    if (id.endsWith("molten_zinc")) return 700;
                    if (id.endsWith("molten_aluminum") || id.endsWith("molten_aluminium")) return 940;
                    if (id.endsWith("molten_bronze") || id.endsWith("molten_brass")) return 1250;
                    if (id.endsWith("molten_silver") || id.endsWith("molten_gold") || id.endsWith("molten_copper") || id.endsWith("molten_blister_copper")) return 1360;
                    if (id.endsWith("molten_iron") || id.endsWith("molten_steel") || id.endsWith("molten_pig_iron")) return 1820;
                }
            }
            return kind.requiredTemperatureK();
        }

        public List<ItemStack> inputStacks() {
            return inputs.stream().map(Input::stack).toList();
        }

        public List<ItemStack> outputStacks() {
            return outputs.stream().map(Output::stack).toList();
        }
    }

    public record Output(Supplier<? extends ItemLike> item, int count) {
        public ItemStack stack() {
            return new ItemStack(item.get().asItem(), count);
        }
    }

    /** A fluid the route consumes, expressed in millibuckets. */
    public record FluidInput(String fluidId, int amount) {
        /** Resolves the spec id to a registered fluid; returns the empty fluid when unknown. */
        public net.minecraft.world.level.material.Fluid fluid() {
            Identifier id = Identifier.tryParse(fluidId);
            if (id == null) {
                return net.minecraft.world.level.material.Fluids.EMPTY;
            }
            return BuiltInRegistries.FLUID.getValue(id);
        }
    }

    /** A fluid the route produces, expressed in millibuckets. */
    public record FluidOutput(String fluidId, int amount) {
        /** Resolves the spec id to a registered fluid; returns the empty fluid when unknown. */
        public net.minecraft.world.level.material.Fluid fluid() {
            Identifier id = Identifier.tryParse(fluidId);
            if (id == null) {
                return net.minecraft.world.level.material.Fluids.EMPTY;
            }
            return BuiltInRegistries.FLUID.getValue(id);
        }
    }

    public enum Kind {
        CRUSHER("颚式破碎机", "把矿石和矿床方块粗碎成碎块，通常会产生尾粉或伴生矿物。", 80, 20, PowerMode.LOCAL_OR_GRID, ProcessFamily.COMMINUTION),
        BALL_MILL("球磨机", "把碎块和岩石磨成粉末，是分离矿物组分的基础步骤。", 140, 32, PowerMode.LOCAL_OR_GRID, ProcessFamily.COMMINUTION),
        SIEVE("筛分机", "按颗粒大小和密度做简化筛分，适合尾粉、金伯利岩和含金石英碎块。", 80, 16, PowerMode.LOCAL_OR_GRID, ProcessFamily.CLASSIFICATION),
        MAGNETIC_SEPARATOR("磁选机", "利用磁性分离磁铁矿、赤铁矿和镁铁质硅酸盐里的含铁组分。", 100, 48, PowerMode.LOCAL_OR_GRID, ProcessFamily.CLASSIFICATION),
        FLOTATION_CELL("浮选槽", "富集硫化矿和宝石矿物，让铜、金、青金石、红石等进入精矿。", 180, 56, PowerMode.GRID_ONLY, ProcessFamily.WET_PROCESS),
        ROASTER("焙烧炉", "把硫化矿、辰砂和碳酸盐做热处理，产出焙烧矿、硫粉或石灰粉。", 220, 56, PowerMode.HEAT_OR_GRID, ProcessFamily.THERMAL),
        REDUCTION_FURNACE("还原炉", "把铁精矿还原成海绵铁；其他精矿按各自路线处理并产生矿渣。", 260, 72, PowerMode.HEAT_OR_GRID, ProcessFamily.THERMAL),
        LEACHING_TANK("浸出槽", "模拟湿法冶金，从石英脉、绿柱石和红石精矿里选择性提取目标物。", 200, 40, PowerMode.GRID_ONLY, ProcessFamily.WET_PROCESS),
        ELECTROLYTIC_CELL("电解槽", "用现代电化学路线精炼铜、金、青金石和绿柱石精矿。", 280, 96, PowerMode.GRID_ONLY, ProcessFamily.ELECTROCHEMICAL),
        POWDER_PRESS("压粉机", "把粉末或精矿压回 MC 兼容物品，保留合成魔法的便利性。", 100, 48, PowerMode.GRID_ONLY, ProcessFamily.FORMING),
        CHEMICAL_REACTOR("化学反应釜", "承载酸碱中和、酸解、氧化和常见无机化工反应的简化路线。", 200, 64, PowerMode.GRID_ONLY, ProcessFamily.REACTION),
        DISTILLATION_COLUMN("精馏塔", "分离盐卤、盐酸和煤焦油等混合物，得到更集中的化工原料。", 260, 64, PowerMode.HEAT_OR_GRID, ProcessFamily.COLUMN),
        MIXER("工业混合机", "把粉体和母料混合成玻璃配合料、水泥生料、肥料母料等中间物。", 100, 32, PowerMode.GRID_ONLY, ProcessFamily.MIXING),
        CRYSTALLIZER("结晶器", "把盐、烧碱、硝酸铵、石膏等溶液或粉体整理成可堆叠产品。", 200, 40, PowerMode.GRID_ONLY, ProcessFamily.CRYSTALLIZATION),
        INDUSTRIAL_KILN("工业窑炉", "处理玻璃、水泥、铝土矿和其他需要高温煅烧的化工路线。", 300, 80, PowerMode.HEAT_OR_GRID, ProcessFamily.THERMAL),
        GAS_SEPARATOR("气体分离器", "从煤气、氯气等气体混合物中回收氢、氨、盐酸等产品。", 220, 96, PowerMode.GRID_ONLY, ProcessFamily.CLASSIFICATION),
        FERTILIZER_GRANULATOR("肥料造粒机", "把氮肥、磷肥和复合肥母料整理成可运输的颗粒产品。", 140, 40, PowerMode.GRID_ONLY, ProcessFamily.FORMING),
        POLYMERIZER("聚合釜", "把乙烯、丙烯和氯乙烯聚合为常见塑料树脂。", 260, 72, PowerMode.GRID_ONLY, ProcessFamily.REACTION),
        STEAM_CRACKER("蒸汽裂解炉", "把煤焦油、煤气和混合树脂裂解成芳烃、烯烃与合成气入口。", 320, 120, PowerMode.HEAT_OR_GRID, ProcessFamily.THERMAL),
        SYNTHESIS_LOOP("合成塔", "承载合成氨、甲醇、尿素和树脂前驱体这类高压循环反应。", 360, 128, PowerMode.GRID_ONLY, ProcessFamily.REACTION),
        ABSORPTION_TOWER("吸收塔", "把酸性或含氯尾气吸收为硫酸、盐酸、纯碱等可用产品。", 240, 56, PowerMode.GRID_ONLY, ProcessFamily.COLUMN),
        BLOOMERY("块炼炉", "土石竖穴炉：鼓风把氧化铁还原成海绵铁块，出炉后必须趁热锻打挤渣。", 400, 0, PowerMode.HEAT_ONLY, ProcessFamily.THERMAL),
        BREAD_OVEN("面包窑", "砖拱蓄热窑：先烧透砖体再撤火，靠蓄热烘烤面团。", 300, 0, PowerMode.HEAT_ONLY, ProcessFamily.THERMAL),
        GLASS_KILN("玻璃窑", "高温窑：石英砂与助熔剂熔成玻璃液，温度不足会留下未熔砂粒。", 240, 0, PowerMode.HEAT_ONLY, ProcessFamily.THERMAL),
        LIME_KILN("石灰窑", "竖窑烧石灰石，得到砂浆、玻璃助熔与中和反应的起点。", 300, 0, PowerMode.HEAT_ONLY, ProcessFamily.THERMAL),
        PIT_KILN("炭窑", "土堆炭窑：木材覆土后从底部点火并封堵进气，让木材炭化而不是烧尽。", 1200, 0, PowerMode.HEAT_ONLY, ProcessFamily.THERMAL),
        SHAFT_FURNACE("竖炉", "高型砖砌竖炉，配热风与出铁口，炉温超过铁熔点，可连续出铁水并浇铸。", 200, 0, PowerMode.HEAT_ONLY, ProcessFamily.THERMAL),
        ANVIL("锻砧台", "锻造：把锭与钢锻成工具、零件与建材，并按工艺记录品质。", 120, 0, PowerMode.MANUAL_OR_HEAT, ProcessFamily.FORMING),
        BAR_COUNTER("吧台", "调饮：把酒、果汁与香料按配方顺序配成鸡尾酒。", 40, 0, PowerMode.MANUAL, ProcessFamily.MIXING),
        CAMPFIRE("营火", "明火直烤：把生食烤熟，火候过头会烤焦。", 100, 0, PowerMode.HEAT_ONLY, ProcessFamily.THERMAL),
        CARPENTRY_BENCH("木工台", "木工：把原木加工成板材、榫卯构件、家具与农具木件。", 100, 0, PowerMode.KINETIC, ProcessFamily.FORMING),
        CASTING_TABLE("浇铸台", "浇铸：把熔融金属倒进铸型，冷却后得到锭与坯料。", 60, 0, PowerMode.MANUAL_OR_HEAT, ProcessFamily.FORMING),
        COFFEE_ROASTER("烘焙机", "烘焙：把生豆烘到目标程度，浅烘与深烘风味不同。", 120, 0, PowerMode.HEAT_ONLY, ProcessFamily.THERMAL),
        CONDENSER("冷凝器", "冷凝：把高温蒸汽收回成液体或固体，水封避免氧化。", 80, 12, PowerMode.HEAT_OR_GRID, ProcessFamily.COLUMN),
        CONVERTER("吹炼炉", "吹炼：向粗铜熔体鼓风去硫，把粗铜转成精铜。", 90, 72, PowerMode.HEAT_OR_GRID, ProcessFamily.THERMAL),
        CURING_BARN("烘房", "烘房：用温和热风长期脱水，保留风味并延长保存。", 600, 0, PowerMode.HEAT_ONLY, ProcessFamily.THERMAL),
        FERMENTING_CROCK("发酵缸", "发酵：酒、醋、酱与腌菜，按游戏日计时，不需要电。", 2400, 0, PowerMode.PASSIVE, ProcessFamily.REACTION),
        FILTER_PRESS("压滤机", "压滤：把浆料压成滤饼，滤布磨损后需要更换。", 120, 32, PowerMode.MANUAL_OR_GRID, ProcessFamily.WET_PROCESS),
        GEM_CUTTER("宝石切割机", "切磨：按解理与硬度切出刻面，切割角度决定成品净度。", 60, 6, PowerMode.LOCAL_OR_GRID, ProcessFamily.FORMING),
        HERB_PROCESSING_TABLE("药材处理台", "炮制：把草药洗净、切制、干燥或炒制，改变药性。", 120, 0, PowerMode.HEAT_ONLY, ProcessFamily.MIXING),
        LOOM("织布机", "纺织：把纤维纺成线，把线织成布，手摇或水轮驱动。", 100, 0, PowerMode.KINETIC, ProcessFamily.FORMING),
        MILL("磨坊", "磨粉：手摇、水轮或畜力驱动，把谷物与矿物磨成粉。", 60, 0, PowerMode.KINETIC, ProcessFamily.COMMINUTION),
        NOODLE_PRESS("压面机", "压面：把面团压成面条或面皮，厚度决定口感。", 60, 0, PowerMode.MANUAL, ProcessFamily.FORMING),
        OIL_PRESS("榨油机", "榨油：油料作物出油，饼粕做饲料。", 100, 0, PowerMode.KINETIC, ProcessFamily.FORMING),
        RICE_HULLER("砻谷机", "脱壳：稻谷出糙米，稻壳做燃料。", 80, 0, PowerMode.KINETIC, ProcessFamily.COMMINUTION),
        SMELTER("熔炼炉", "熔炼：上限 1750 K，只能熔化熔点低于此值的金属；铁需要更高的炉子。", 100, 80, PowerMode.HEAT_OR_GRID, ProcessFamily.THERMAL),
        SMOKEHOUSE("熏房", "冷熏与热熏：长期保存肉类，并带来烟熏风味。", 600, 0, PowerMode.HEAT_ONLY, ProcessFamily.THERMAL),
        STEAMER("蒸锅", "蒸制：需要水，水量不足会干锅。", 120, 0, PowerMode.HEAT_ONLY, ProcessFamily.THERMAL),
        STEAMER_BASKET("蒸笼", "叠放蒸制：一次可蒸多层，层数决定产量。", 120, 0, PowerMode.HEAT_ONLY, ProcessFamily.THERMAL),
        STILL("蒸馏器", "蒸馏：按沸点差分离酒精与芳香组分，得到烈酒与精油。", 200, 0, PowerMode.HEAT_ONLY, ProcessFamily.COLUMN),
        STONE_MILL("石磨", "低速研磨：比磨坊细，但更慢。", 120, 0, PowerMode.KINETIC, ProcessFamily.COMMINUTION),
        STOVE("灶台", "煎、炒、煮、蒸：火候分档，超过档位会焦。", 80, 0, PowerMode.HEAT_ONLY, ProcessFamily.THERMAL),
        TABLET_PRESS("压片机", "压片：把药粉与辅料压成定量片剂，剂量更稳定。", 40, 3, PowerMode.MANUAL_OR_GRID, ProcessFamily.FORMING),
        TANNERY("制革槽", "制革：鞣剂浸泡生皮成革，需要鞣剂与时间，不耗电。", 200, 0, PowerMode.MANUAL, ProcessFamily.WET_PROCESS),
        TEA_KILN("茶炒锅", "杀青与提香：控制锅温炒制茶叶，温度过高会焦边。", 100, 0, PowerMode.HEAT_ONLY, ProcessFamily.THERMAL),
        THICKENER("浓密机", "精矿浓密脱水，为压滤做准备。", 140, 40, PowerMode.GRID_ONLY, ProcessFamily.WET_PROCESS),
        WATER_TREATMENT_UNIT("水处理单元", "水处理：软化与过滤，供锅炉与电解用水。", 160, 36, PowerMode.GRID_ONLY, ProcessFamily.WET_PROCESS);

        private final String displayName;
        private final String description;
        private final int processTicks;
        private final int energyPerTick;
        private final PowerMode powerMode;
        private final ProcessFamily processFamily;

        Kind(String displayName, String description, int processTicks, int energyPerTick,
             PowerMode powerMode, ProcessFamily processFamily) {
            this.displayName = displayName;
            this.description = description;
            this.processTicks = processTicks;
            this.energyPerTick = energyPerTick;
            this.powerMode = powerMode;
            this.processFamily = processFamily;
        }

        public String displayName() {
            return displayName;
        }

        public String description() {
            return description;
        }

        public String displayNameKey() {
            return "block.earth_on_minecraft." + blockId();
        }

        public String descriptionKey() {
            return "tooltip.earth_on_minecraft.machine." + blockId() + ".description";
        }

        public String setpointKey() {
            return "screen.earth_on_minecraft.machine.setpoint." + blockId();
        }

        public int processTicks() {
            return processTicks;
        }

        public int energyPerTick() {
            return energyPerTick;
        }

        public PowerMode powerMode() {
            return powerMode;
        }

        public ProcessFamily processFamily() {
            return processFamily;
        }

        public boolean acceptsLocalFuel() {
            return powerMode.allowsLocalFuel();
        }

        public int requiredTemperatureK() {
            return switch (this) {
                case CURING_BARN -> 340;
                case SMOKEHOUSE -> 350;
                case HERB_PROCESSING_TABLE -> 360;
                case STILL -> 365;
                case STEAMER, STEAMER_BASKET -> 373;
                case DISTILLATION_COLUMN, STOVE -> 450;
                case COFFEE_ROASTER -> 480;
                case TEA_KILN, BREAD_OVEN -> 500;
                case CAMPFIRE -> 600;
                case PIT_KILN -> 750;
                case ROASTER -> 1050;
                case STEAM_CRACKER -> 1100;
                case LIME_KILN -> 1200;
                case REDUCTION_FURNACE -> 1350;
                case SMELTER -> 1400;
                case BLOOMERY -> 1450;
                case GLASS_KILN -> 1500;
                case CONVERTER -> 1550;
                case INDUSTRIAL_KILN -> 1600;
                case SHAFT_FURNACE -> 1820;
                default -> 293;
            };
        }

        public int maxTemperatureK() {
            return switch (this) {
                case ELECTROLYTIC_CELL -> 2300;
                case SHAFT_FURNACE -> 2100;
                case INDUSTRIAL_KILN, CONVERTER, GLASS_KILN, BLOOMERY, REDUCTION_FURNACE -> 1950;
                case SMELTER -> 1750;
                case LIME_KILN, STEAM_CRACKER, ROASTER -> 1650;
                case CHEMICAL_REACTOR, SYNTHESIS_LOOP, ABSORPTION_TOWER, POLYMERIZER, LEACHING_TANK -> 1500;
                case PIT_KILN, CAMPFIRE -> 1000;
                case TEA_KILN, BREAD_OVEN, COFFEE_ROASTER, STOVE, DISTILLATION_COLUMN -> 700;
                case STEAMER, STEAMER_BASKET, STILL, HERB_PROCESSING_TABLE, SMOKEHOUSE, CURING_BARN, MIXER, CRYSTALLIZER, CONDENSER -> 450;
                case ANVIL, CASTING_TABLE -> 1350;
                default -> 293;
            };
        }

        public boolean usesThermalTemperature() {
            return requiredTemperatureK() > 293 || powerMode.requiresFuel() || processFamily == ProcessFamily.THERMAL;
        }

        public int energyBufferCapacity() {
            if (!powerMode.grid()) {
                return 0;
            }
            return Math.min(16000, Math.max(1600, Math.max(16, energyPerTick) * 40));
        }

        public String localizedDisplayName() {
            return Language.getInstance().getOrDefault(displayNameKey(), displayName);
        }

        public String blockId() {
            return switch (this) {
                case CRUSHER -> "jaw_crusher";
                case BALL_MILL -> "ball_mill";
                case SIEVE -> "sieve";
                case MAGNETIC_SEPARATOR -> "magnetic_separator";
                case FLOTATION_CELL -> "flotation_cell";
                case ROASTER -> "ore_roaster";
                case REDUCTION_FURNACE -> "reduction_furnace";
                case LEACHING_TANK -> "leaching_tank";
                case ELECTROLYTIC_CELL -> "electrolytic_cell";
                case POWDER_PRESS -> "powder_press";
                case CHEMICAL_REACTOR -> "chemical_reactor";
                case DISTILLATION_COLUMN -> "distillation_column";
                case MIXER -> "mixer";
                case CRYSTALLIZER -> "crystallizer";
                case INDUSTRIAL_KILN -> "industrial_kiln";
                case GAS_SEPARATOR -> "gas_separator";
                case FERTILIZER_GRANULATOR -> "fertilizer_granulator";
                case POLYMERIZER -> "polymerizer";
                case STEAM_CRACKER -> "steam_cracker";
                case SYNTHESIS_LOOP -> "synthesis_loop";
                case ABSORPTION_TOWER -> "absorption_tower";
                case ANVIL -> "anvil";
                case BAR_COUNTER -> "bar_counter";
                case CAMPFIRE -> "campfire";
                case CARPENTRY_BENCH -> "carpentry_bench";
                case CASTING_TABLE -> "casting_table";
                case COFFEE_ROASTER -> "coffee_roaster";
                case CONDENSER -> "condenser";
                case CONVERTER -> "converter";
                case CURING_BARN -> "curing_barn";
                case FERMENTING_CROCK -> "fermenting_crock";
                case FILTER_PRESS -> "filter_press";
                case GEM_CUTTER -> "gem_cutter";
                case HERB_PROCESSING_TABLE -> "herb_processing_table";
                case LOOM -> "loom";
                case MILL -> "mill";
                case NOODLE_PRESS -> "noodle_press";
                case OIL_PRESS -> "oil_press";
                case RICE_HULLER -> "rice_huller";
                case SMELTER -> "smelter";
                case SMOKEHOUSE -> "smokehouse";
                case STEAMER -> "steamer";
                case STEAMER_BASKET -> "steamer_basket";
                case STILL -> "still";
                case STONE_MILL -> "stone_mill";
                case STOVE -> "stove";
                case TABLET_PRESS -> "tablet_press";
                case TANNERY -> "tannery";
                case TEA_KILN -> "tea_kiln";
                case THICKENER -> "thickener";
                case WATER_TREATMENT_UNIT -> "water_treatment_unit";
                case BLOOMERY -> "bloomery_controller";
                case BREAD_OVEN -> "bread_oven_controller";
                case GLASS_KILN -> "glass_kiln_controller";
                case LIME_KILN -> "lime_kiln_controller";
                case PIT_KILN -> "pit_kiln_controller";
                case SHAFT_FURNACE -> "shaft_furnace_controller";
            };
        }
    }

    /**
     * How a machine gets its working energy, mirroring the spec {@code power} arrays.
     *
     * <ul>
     *   <li>{@code grid}: the machine can draw EOU power.</li>
     *   <li>{@code fuel}: the fuel slot accepts burnable items.</li>
     *   <li>{@code heat}: the process needs a burning fire when no grid power is available.</li>
     *   <li>{@code manualFallback}: hand or kinetic drive can replace missing grid power.</li>
     *   <li>{@code passive}: ambient process that runs with no supply at all.</li>
     * </ul>
     */
    public enum PowerMode {
        LOCAL_OR_GRID(true, true, false, true, false, "screen.earth_on_minecraft.machine.power.local_or_grid"),
        HEAT_OR_GRID(true, true, true, false, false, "screen.earth_on_minecraft.machine.power.heat_or_grid"),
        GRID_ONLY(true, false, false, false, false, "screen.earth_on_minecraft.machine.power.grid_only"),
        MANUAL_OR_HEAT(false, true, false, true, false, "screen.earth_on_minecraft.machine.power.manual_or_heat"),
        MANUAL_OR_GRID(true, false, false, true, false, "screen.earth_on_minecraft.machine.power.manual_or_grid"),
        KINETIC(false, false, false, true, false, "screen.earth_on_minecraft.machine.power.kinetic"),
        MANUAL(false, false, false, true, false, "screen.earth_on_minecraft.machine.power.manual"),
        HEAT_ONLY(false, true, true, false, false, "screen.earth_on_minecraft.machine.power.heat_only"),
        PASSIVE(false, false, false, false, true, "screen.earth_on_minecraft.machine.power.passive");

        private final boolean grid;
        private final boolean fuel;
        private final boolean heat;
        private final boolean manualFallback;
        private final boolean passive;
        private final String labelKey;

        PowerMode(boolean grid, boolean fuel, boolean heat, boolean manualFallback, boolean passive,
                  String labelKey) {
            this.grid = grid;
            this.fuel = fuel;
            this.heat = heat;
            this.manualFallback = manualFallback;
            this.passive = passive;
            this.labelKey = labelKey;
        }

        public boolean grid() {
            return grid;
        }

        public boolean allowsLocalFuel() {
            return fuel;
        }

        /** True when the process needs a burning fire even though it may draw no electricity. */
        public boolean requiresFuel() {
            return heat;
        }

        /** True when hand or kinetic drive can replace missing grid power. */
        public boolean manualFallback() {
            return manualFallback;
        }

        /** True when the machine needs no fuel and no grid connection to make progress. */
        public boolean passive() {
            return passive;
        }

        public String labelKey() {
            return labelKey;
        }
    }

    public enum ProcessFamily {
        COMMINUTION("screen.earth_on_minecraft.machine.family.comminution", 0xFF7A6247),
        CLASSIFICATION("screen.earth_on_minecraft.machine.family.classification", 0xFF4F7081),
        WET_PROCESS("screen.earth_on_minecraft.machine.family.wet_process", 0xFF347B75),
        THERMAL("screen.earth_on_minecraft.machine.family.thermal", 0xFFA44C2B),
        ELECTROCHEMICAL("screen.earth_on_minecraft.machine.family.electrochemical", 0xFF356AA0),
        FORMING("screen.earth_on_minecraft.machine.family.forming", 0xFF76678D),
        REACTION("screen.earth_on_minecraft.machine.family.reaction", 0xFF8A5A32),
        COLUMN("screen.earth_on_minecraft.machine.family.column", 0xFF4B6F76),
        MIXING("screen.earth_on_minecraft.machine.family.mixing", 0xFF65733D),
        CRYSTALLIZATION("screen.earth_on_minecraft.machine.family.crystallization", 0xFF6B7692);

        private final String labelKey;
        private final int accentColor;

        ProcessFamily(String labelKey, int accentColor) {
            this.labelKey = labelKey;
            this.accentColor = accentColor;
        }

        public String labelKey() {
            return labelKey;
        }

        public int accentColor() {
            return accentColor;
        }
    }
}
