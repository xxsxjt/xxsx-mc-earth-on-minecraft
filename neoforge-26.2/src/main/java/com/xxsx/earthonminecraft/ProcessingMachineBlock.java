package com.xxsx.earthonminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class ProcessingMachineBlock extends Block implements EntityBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final List<Recipe> RECIPES = createRecipes();

    private final Kind kind;

    public ProcessingMachineBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        MachineMultiblock.refreshProjection(level, pos, this.kind);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return openMachine(level, pos, player);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        return openMachine(level, pos, player);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ProcessingMachineBlockEntity(pos, state);
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
        builder.add(ACTIVE);
    }

    public static List<Recipe> recipes() {
        return RECIPES;
    }

    public static List<Recipe> recipesFor(Kind kind) {
        return RECIPES.stream().filter(recipe -> recipe.kind == kind).toList();
    }

    public static Optional<Recipe> findRecipe(Kind kind, ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return RECIPES.stream()
                .filter(recipe -> recipe.kind == kind && stack.getItem() == recipe.input.get().asItem())
                .findFirst();
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

    private static List<Recipe> createRecipes() {
        List<Recipe> recipes = new ArrayList<>();

        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.POOR_MAGNETITE_ORE::get, "贫磁铁矿粗碎", out(EarthOnMinecraft.MAGNETITE_CHUNK::get, 2), out(EarthOnMinecraft.TAILINGS_DUST::get, 2)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.MAGNETITE_ORE::get, "磁铁矿粗碎", out(EarthOnMinecraft.MAGNETITE_CHUNK::get, 4), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.RICH_MAGNETITE_ORE::get, "富磁铁矿粗碎", out(EarthOnMinecraft.MAGNETITE_CHUNK::get, 6), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.CHALCOPYRITE_ORE::get, "黄铜矿粗碎", out(EarthOnMinecraft.CHALCOPYRITE_CHUNK::get, 4), out(EarthOnMinecraft.PYRITE_DUST::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.AURIFEROUS_QUARTZ_VEIN::get, "含金石英脉粗碎", out(EarthOnMinecraft.AURIFEROUS_QUARTZ_CHUNK::get, 3), out(EarthOnMinecraft.QUARTZ_DUST::get, 2)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.KIMBERLITE::get, "金伯利岩粗碎", out(EarthOnMinecraft.KIMBERLITE_CHUNK::get, 4), out(EarthOnMinecraft.TAILINGS_DUST::get, 2)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.DIAMONDIFEROUS_KIMBERLITE::get, "含钻金伯利岩粗碎", out(EarthOnMinecraft.KIMBERLITE_CHUNK::get, 4), out(EarthOnMinecraft.DIAMOND_GRIT::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.CINNABAR_VEIN::get, "辰砂矿脉粗碎", out(EarthOnMinecraft.CINNABAR_CHUNK::get, 4), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.BITUMINOUS_COAL_SEAM::get, "烟煤含煤岩破碎与挥发分回收", out(EarthOnMinecraft.COAL_DUST::get, 4), out(EarthOnMinecraft.COAL_TAR::get, 1), out(EarthOnMinecraft.COAL_GAS_CELL::get, 1), out(EarthOnMinecraft.CRUDE_OIL_SAMPLE::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.ANTHRACITE_COAL_SEAM::get, "无烟煤含煤岩破碎与高碳富集", out(EarthOnMinecraft.COAL_DUST::get, 7), out(EarthOnMinecraft.GRAPHITE_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.BAUXITE_LATERITE_DEPOSIT::get, "铝土矿风化壳破碎", out(EarthOnMinecraft.BAUXITE_DUST::get, 5), out(EarthOnMinecraft.HEMATITE_DUST::get, 1), out(EarthOnMinecraft.KAOLIN_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.PHOSPHORITE_BED::get, "磷块岩沉积层破碎", out(EarthOnMinecraft.PHOSPHATE_ROCK_DUST::get, 5), out(EarthOnMinecraft.CALCITE_DUST::get, 1), out(EarthOnMinecraft.GYPSUM_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.EVAPORITE_SALT_BED::get, "蒸发岩盐层破碎", out(EarthOnMinecraft.SALT_DUST::get, 5), out(EarthOnMinecraft.GYPSUM_DUST::get, 1), out(EarthOnMinecraft.BRINE_CRYSTAL::get, 1), out(EarthOnMinecraft.POTASSIUM_CHLORIDE::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.CASSITERITE_PLACER_DEPOSIT::get, "锡石砂矿预碎洗选", out(EarthOnMinecraft.CASSITERITE_DUST::get, 3), out(EarthOnMinecraft.SILICA_DUST::get, 2), out(EarthOnMinecraft.TITANIUM_DIOXIDE::get, 1), out(EarthOnMinecraft.MONAZITE_SAND::get, 1)));
        recipes.add(r(Kind.CRUSHER, EarthOnMinecraft.RARE_EARTH_CARBONATITE::get, "稀土碳酸岩破碎", out(EarthOnMinecraft.BASTNASITE_DUST::get, 3), out(EarthOnMinecraft.MONAZITE_SAND::get, 1), out(EarthOnMinecraft.CALCITE_DUST::get, 2), out(EarthOnMinecraft.URANINITE_DUST::get, 1), out(EarthOnMinecraft.RARE_EARTH_TAILINGS::get, 1)));

        recipes.add(r(Kind.BALL_MILL, EarthOnMinecraft.MAGNETITE_CHUNK::get, "磁铁矿球磨", out(EarthOnMinecraft.MAGNETITE_DUST::get, 3), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, EarthOnMinecraft.CHALCOPYRITE_CHUNK::get, "黄铜矿球磨", out(EarthOnMinecraft.CHALCOPYRITE_DUST::get, 3), out(EarthOnMinecraft.PYRITE_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, EarthOnMinecraft.CINNABAR_CHUNK::get, "辰砂球磨", out(EarthOnMinecraft.CINNABAR_DUST::get, 3), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.GRANITE, "花岗岩矿物分离", out(EarthOnMinecraft.QUARTZ_DUST::get, 2), out(EarthOnMinecraft.FELDSPAR_DUST::get, 3), out(EarthOnMinecraft.MICA_DUST::get, 1), out(EarthOnMinecraft.MONAZITE_SAND::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.DIORITE, "闪长岩矿物分离", out(EarthOnMinecraft.FELDSPAR_DUST::get, 3), out(EarthOnMinecraft.MAFIC_SILICATE_DUST::get, 2), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.ANDESITE, "安山岩矿物分离", out(EarthOnMinecraft.FELDSPAR_DUST::get, 2), out(EarthOnMinecraft.MAFIC_SILICATE_DUST::get, 3), out(EarthOnMinecraft.MAGNETITE_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.BASALT, "玄武岩矿物分离", out(EarthOnMinecraft.MAFIC_SILICATE_DUST::get, 4), out(EarthOnMinecraft.MAGNETITE_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.STONE, "普通石头硅酸盐混合物粉磨", out(EarthOnMinecraft.SILICA_DUST::get, 2), out(EarthOnMinecraft.FELDSPAR_DUST::get, 1), out(EarthOnMinecraft.MAFIC_SILICATE_DUST::get, 1), out(EarthOnMinecraft.CALCITE_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.BALL_MILL, () -> Blocks.COBBLESTONE, "圆石碎屑再粉磨", out(EarthOnMinecraft.SILICA_DUST::get, 2), out(EarthOnMinecraft.FELDSPAR_DUST::get, 1), out(EarthOnMinecraft.MAFIC_SILICATE_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
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

        recipes.add(r(Kind.MAGNETIC_SEPARATOR, EarthOnMinecraft.MAGNETITE_DUST::get, "磁选铁精矿", out(EarthOnMinecraft.IRON_CONCENTRATE::get, 2), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.MAGNETIC_SEPARATOR, EarthOnMinecraft.HEMATITE_DUST::get, "赤铁矿弱磁分选", out(EarthOnMinecraft.IRON_CONCENTRATE::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.MAGNETIC_SEPARATOR, EarthOnMinecraft.MAFIC_SILICATE_DUST::get, "镁铁质硅酸盐除铁", out(EarthOnMinecraft.MAGNETITE_DUST::get, 1), out(EarthOnMinecraft.MAGNESIUM_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 2)));

        recipes.add(r(Kind.FLOTATION_CELL, EarthOnMinecraft.CHALCOPYRITE_DUST::get, "黄铜矿浮选", out(EarthOnMinecraft.COPPER_CONCENTRATE::get, 2), out(EarthOnMinecraft.PYRITE_DUST::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.FLOTATION_CELL, EarthOnMinecraft.PYRITE_DUST::get, "黄铁矿浮选", out(EarthOnMinecraft.SULFUR_DUST::get, 1), out(EarthOnMinecraft.IRON_CONCENTRATE::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.FLOTATION_CELL, EarthOnMinecraft.GOLD_DUST::get, "金粉富集", out(EarthOnMinecraft.GOLD_CONCENTRATE::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.FLOTATION_CELL, EarthOnMinecraft.LAPIS_LAZULI_ORE::get, "青金石浮选", out(EarthOnMinecraft.LAPIS_CONCENTRATE::get, 2), out(EarthOnMinecraft.CALCITE_DUST::get, 1), out(EarthOnMinecraft.PYRITE_DUST::get, 1)));
        recipes.add(r(Kind.FLOTATION_CELL, EarthOnMinecraft.REDSTONE_MINERAL_ORE::get, "红石矿物富集", out(EarthOnMinecraft.REDSTONE_CONCENTRATE::get, 2), out(EarthOnMinecraft.SILICA_DUST::get, 1)));

        recipes.add(r(Kind.ROASTER, EarthOnMinecraft.COPPER_CONCENTRATE::get, "铜精矿焙烧", out(EarthOnMinecraft.ROASTED_COPPER_CONCENTRATE::get, 1), out(EarthOnMinecraft.SULFUR_DUST::get, 2)));
        recipes.add(r(Kind.ROASTER, EarthOnMinecraft.PYRITE_DUST::get, "黄铁矿焙烧", out(EarthOnMinecraft.HEMATITE_DUST::get, 1), out(EarthOnMinecraft.SULFUR_DUST::get, 2)));
        recipes.add(r(Kind.ROASTER, EarthOnMinecraft.CINNABAR_DUST::get, "辰砂焙烧", out(EarthOnMinecraft.MERCURY_DROPLET::get, 1), out(EarthOnMinecraft.SULFUR_DUST::get, 1)));
        recipes.add(r(Kind.ROASTER, EarthOnMinecraft.CALCITE_DUST::get, "碳酸盐煅烧", out(EarthOnMinecraft.LIME_DUST::get, 1)));
        recipes.add(r(Kind.ROASTER, EarthOnMinecraft.PETROLEUM_COKE::get, "石油焦高温石墨化近似路线", out(EarthOnMinecraft.GRAPHITE_DUST::get, 1), out(EarthOnMinecraft.ACTIVATED_CARBON::get, 1)));

        recipes.add(r(Kind.REDUCTION_FURNACE, EarthOnMinecraft.IRON_CONCENTRATE::get, "铁精矿碳热还原", out(() -> Items.IRON_INGOT, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.REDUCTION_FURNACE, EarthOnMinecraft.ROASTED_COPPER_CONCENTRATE::get, "焙烧铜精矿还原", out(() -> Items.COPPER_INGOT, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.REDUCTION_FURNACE, EarthOnMinecraft.GOLD_CONCENTRATE::get, "金精矿熔炼", out(() -> Items.GOLD_INGOT, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.REDUCTION_FURNACE, EarthOnMinecraft.COAL_DUST::get, "煤粉压焦近似处理", out(() -> Items.COAL, 1)));
        recipes.add(r(Kind.REDUCTION_FURNACE, () -> Items.IRON_INGOT, "生铁到钢坯的简化精炼", out(EarthOnMinecraft.STEEL_BLOOM::get, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.REDUCTION_FURNACE, EarthOnMinecraft.SILICA_DUST::get, "硅石碳热还原制硅铁和冶金硅", out(EarthOnMinecraft.FERROSILICON::get, 1), out(EarthOnMinecraft.METALLURGICAL_SILICON::get, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.REDUCTION_FURNACE, EarthOnMinecraft.COKE::get, "焦炭高温还原气氛", out(EarthOnMinecraft.COAL_GAS_CELL::get, 1), out(EarthOnMinecraft.SLAG::get, 1)));
        recipes.add(r(Kind.REDUCTION_FURNACE, EarthOnMinecraft.FERROSILICON::get, "硅铁脱氧辅助炼钢", out(EarthOnMinecraft.STEEL_BLOOM::get, 1), out(EarthOnMinecraft.SLAG::get, 1)));

        recipes.add(r(Kind.LEACHING_TANK, EarthOnMinecraft.AURIFEROUS_QUARTZ_CHUNK::get, "含金石英浸出", out(EarthOnMinecraft.GOLD_CONCENTRATE::get, 1), out(EarthOnMinecraft.SILICA_DUST::get, 2), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.LEACHING_TANK, EarthOnMinecraft.EMERALD_BERYL_VEIN::get, "绿柱石浸出富集", out(EarthOnMinecraft.BERYL_CONCENTRATE::get, 2), out(EarthOnMinecraft.QUARTZ_DUST::get, 1), out(EarthOnMinecraft.MICA_DUST::get, 1)));
        recipes.add(r(Kind.LEACHING_TANK, EarthOnMinecraft.REDSTONE_CONCENTRATE::get, "红石矿物浸出", out(() -> Items.REDSTONE, 4), out(EarthOnMinecraft.SILICA_DUST::get, 1)));
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
        recipes.add(r(Kind.ELECTROLYTIC_CELL, EarthOnMinecraft.REDSTONE_CONCENTRATE::get, "红石伴生锰氧化物电积", out(EarthOnMinecraft.MANGANESE_OXIDE_DUST::get, 1), out(() -> Items.REDSTONE, 2)));

        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.COAL_DUST::get, "煤粉压块", out(() -> Items.COAL, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.DIAMOND_GRIT::get, "金刚石砂粒压制", out(() -> Items.DIAMOND, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.QUARTZ_DUST::get, "石英粉压制", out(() -> Items.QUARTZ, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.REDSTONE_CONCENTRATE::get, "红石精矿压制", out(() -> Items.REDSTONE, 3)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.STEEL_BLOOM::get, "钢坯轧制为兼容铁锭", out(() -> Items.IRON_INGOT, 1)));

        recipes.add(r(Kind.GAS_SEPARATOR, EarthOnMinecraft.COAL_DUST::get, "煤粉干馏分离", out(EarthOnMinecraft.COKE::get, 1), out(EarthOnMinecraft.COAL_TAR::get, 1), out(EarthOnMinecraft.COAL_GAS_CELL::get, 1)));
        recipes.add(r(Kind.GAS_SEPARATOR, EarthOnMinecraft.COAL_GAS_CELL::get, "煤气净化", out(EarthOnMinecraft.HYDROGEN_GAS_CELL::get, 1), out(EarthOnMinecraft.AMMONIA::get, 1), out(EarthOnMinecraft.TAILINGS_DUST::get, 1)));
        recipes.add(r(Kind.GAS_SEPARATOR, EarthOnMinecraft.CHLORINE_GAS_CELL::get, "氯气吸收制酸", out(EarthOnMinecraft.HYDROCHLORIC_ACID::get, 1), out(EarthOnMinecraft.CALCIUM_CHLORIDE::get, 1)));
        recipes.add(r(Kind.GAS_SEPARATOR, () -> Items.GLASS_BOTTLE, "空气压缩分离", out(EarthOnMinecraft.NITROGEN_GAS_CELL::get, 3), out(EarthOnMinecraft.OXYGEN_GAS_CELL::get, 1)));
        recipes.add(r(Kind.GAS_SEPARATOR, EarthOnMinecraft.OXYGEN_GAS_CELL::get, "富氧尾气回收", out(EarthOnMinecraft.OXYGEN_GAS_CELL::get, 1), out(EarthOnMinecraft.NITROGEN_GAS_CELL::get, 1)));

        recipes.add(r(Kind.DISTILLATION_COLUMN, EarthOnMinecraft.COAL_TAR::get, "煤焦油分馏", out(EarthOnMinecraft.ETHYLENE::get, 1), out(EarthOnMinecraft.POLYMER_RESIN::get, 1), out(EarthOnMinecraft.COKE::get, 1)));
        recipes.add(r(Kind.DISTILLATION_COLUMN, EarthOnMinecraft.CRUDE_OIL_SAMPLE::get, "原油常压分馏", out(EarthOnMinecraft.NATURAL_GAS_CELL::get, 1), out(EarthOnMinecraft.NAPHTHA::get, 2), out(EarthOnMinecraft.KEROSENE_FRACTION::get, 1), out(EarthOnMinecraft.DIESEL_FRACTION::get, 1), out(EarthOnMinecraft.LUBRICATING_OIL::get, 1), out(EarthOnMinecraft.ASPHALT::get, 1), out(EarthOnMinecraft.PETROLEUM_COKE::get, 1)));
        recipes.add(r(Kind.DISTILLATION_COLUMN, EarthOnMinecraft.BRINE_CRYSTAL::get, "盐卤浓缩", out(EarthOnMinecraft.SALT_DUST::get, 2), out(EarthOnMinecraft.CALCIUM_CHLORIDE::get, 1)));
        recipes.add(r(Kind.DISTILLATION_COLUMN, EarthOnMinecraft.HYDROCHLORIC_ACID::get, "盐酸精馏", out(EarthOnMinecraft.HYDROCHLORIC_ACID::get, 1), out(EarthOnMinecraft.SALT_DUST::get, 1)));
        recipes.add(r(Kind.STEAM_CRACKER, EarthOnMinecraft.COAL_TAR::get, "煤焦油裂解芳烃和烯烃", out(EarthOnMinecraft.BENZENE::get, 1), out(EarthOnMinecraft.ETHYLENE::get, 1), out(EarthOnMinecraft.PROPYLENE::get, 1), out(EarthOnMinecraft.COKE::get, 1)));
        recipes.add(r(Kind.STEAM_CRACKER, EarthOnMinecraft.NAPHTHA::get, "石脑油蒸汽裂解", out(EarthOnMinecraft.ETHYLENE::get, 2), out(EarthOnMinecraft.PROPYLENE::get, 1), out(EarthOnMinecraft.BENZENE::get, 1), out(EarthOnMinecraft.CARBON_BLACK::get, 1)));
        recipes.add(r(Kind.STEAM_CRACKER, EarthOnMinecraft.DIESEL_FRACTION::get, "柴油馏分深度裂化", out(EarthOnMinecraft.PROPYLENE::get, 2), out(EarthOnMinecraft.CARBON_BLACK::get, 1), out(EarthOnMinecraft.PETROLEUM_COKE::get, 1)));
        recipes.add(r(Kind.STEAM_CRACKER, EarthOnMinecraft.POLYMER_RESIN::get, "混合树脂热裂解回收单体", out(EarthOnMinecraft.ETHYLENE::get, 1), out(EarthOnMinecraft.PROPYLENE::get, 1)));
        recipes.add(r(Kind.STEAM_CRACKER, EarthOnMinecraft.COAL_GAS_CELL::get, "合成气整备用裂解入口", out(EarthOnMinecraft.HYDROGEN_GAS_CELL::get, 1), out(EarthOnMinecraft.CARBON_DIOXIDE_CELL::get, 1), out(EarthOnMinecraft.METHANOL::get, 1)));

        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.SULFUR_DUST::get, "接触法硫酸近似路线", out(EarthOnMinecraft.SULFURIC_ACID::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.AMMONIA::get, "奥斯特瓦尔德法硝酸近似路线", out(EarthOnMinecraft.NITRIC_ACID::get, 1), out(EarthOnMinecraft.HYDROGEN_GAS_CELL::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.NITRIC_ACID::get, "硝酸铵中和", out(EarthOnMinecraft.AMMONIUM_NITRATE::get, 1)));
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

        recipes.add(r(Kind.SYNTHESIS_LOOP, EarthOnMinecraft.NITROGEN_GAS_CELL::get, "哈柏法合成氨近似路线", out(EarthOnMinecraft.AMMONIA::get, 2), out(EarthOnMinecraft.HYDROGEN_GAS_CELL::get, 1)));
        recipes.add(r(Kind.SYNTHESIS_LOOP, EarthOnMinecraft.HYDROGEN_GAS_CELL::get, "氢气循环合成氨/甲醇入口", out(EarthOnMinecraft.AMMONIA::get, 1), out(EarthOnMinecraft.METHANOL::get, 1)));
        recipes.add(r(Kind.SYNTHESIS_LOOP, EarthOnMinecraft.COAL_GAS_CELL::get, "煤气合成甲醇", out(EarthOnMinecraft.METHANOL::get, 1), out(EarthOnMinecraft.CARBON_DIOXIDE_CELL::get, 1)));
        recipes.add(r(Kind.SYNTHESIS_LOOP, EarthOnMinecraft.AMMONIA::get, "氨和二氧化碳合成尿素", out(EarthOnMinecraft.UREA::get, 1), out(EarthOnMinecraft.CARBON_DIOXIDE_CELL::get, 1)));
        recipes.add(r(Kind.SYNTHESIS_LOOP, EarthOnMinecraft.FORMALDEHYDE::get, "甲醛树脂前驱体", out(EarthOnMinecraft.POLYMER_RESIN::get, 1), out(() -> Items.WATER_BUCKET, 1)));
        recipes.add(r(Kind.SYNTHESIS_LOOP, EarthOnMinecraft.NATURAL_GAS_CELL::get, "天然气重整制合成气", out(EarthOnMinecraft.HYDROGEN_GAS_CELL::get, 2), out(EarthOnMinecraft.METHANOL::get, 1), out(EarthOnMinecraft.CARBON_DIOXIDE_CELL::get, 1)));
        recipes.add(r(Kind.SYNTHESIS_LOOP, EarthOnMinecraft.ETHYLENE_GLYCOL::get, "聚酯酸料近似合成", out(EarthOnMinecraft.TEREPHTHALIC_ACID::get, 1), out(EarthOnMinecraft.METHANOL::get, 1)));

        recipes.add(r(Kind.ABSORPTION_TOWER, EarthOnMinecraft.SULFUR_DUST::get, "硫燃烧尾气吸收", out(EarthOnMinecraft.SULFUR_DIOXIDE_CELL::get, 1), out(EarthOnMinecraft.SULFURIC_ACID::get, 1)));
        recipes.add(r(Kind.ABSORPTION_TOWER, EarthOnMinecraft.SULFUR_DIOXIDE_CELL::get, "二氧化硫接触吸收", out(EarthOnMinecraft.SULFURIC_ACID::get, 2)));
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
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.TITANIUM_DIOXIDE::get, "钛白粉白色涂料基料", out(EarthOnMinecraft.PAINT_BASE::get, 1), out(() -> Items.DYE.white(), 2)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.IRON_OXIDE_PIGMENT::get, "氧化铁红涂料基料", out(EarthOnMinecraft.PAINT_BASE::get, 1), out(() -> Items.DYE.red(), 2)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.CARBON_BLACK::get, "炭黑补强与黑色涂料基料", out(EarthOnMinecraft.PAINT_BASE::get, 1), out(() -> Items.DYE.black(), 2)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.PAINT_BASE::get, "通用涂料调和", out(() -> Items.DYE.white(), 1), out(() -> Items.DYE.red(), 1), out(() -> Items.DYE.black(), 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.GRAPHITE_DUST::get, "石墨负极浆料混合", out(EarthOnMinecraft.ELECTRODE_SHEET::get, 1), out(EarthOnMinecraft.BATTERY_CARBON::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.MANGANESE_OXIDE_DUST::get, "锰系正极材料混合", out(EarthOnMinecraft.ELECTRODE_SHEET::get, 1), out(EarthOnMinecraft.NICKEL_PRECURSOR::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.NICKEL_PRECURSOR::get, "镍系正极前驱体混合", out(EarthOnMinecraft.ELECTRODE_SHEET::get, 1), out(EarthOnMinecraft.MANGANESE_OXIDE_DUST::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.TAILINGS_DUST::get, "尾粉稳定化处理", out(EarthOnMinecraft.STABILIZED_TAILINGS::get, 1), out(EarthOnMinecraft.SLUDGE_CAKE::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.HYDROCHLORIC_ACID::get, "酸性废液中和盐化", out(EarthOnMinecraft.NEUTRAL_SALT::get, 1), out(EarthOnMinecraft.SOFTENED_WATER::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.SODIUM_HYDROXIDE::get, "碱性废液中和盐化", out(EarthOnMinecraft.NEUTRAL_SALT::get, 1), out(EarthOnMinecraft.SOFTENED_WATER::get, 1)));

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

        recipes.add(r(Kind.ELECTROLYTIC_CELL, EarthOnMinecraft.SALT_DUST::get, "氯碱工业电解", out(EarthOnMinecraft.SODIUM_HYDROXIDE::get, 1), out(EarthOnMinecraft.CHLORINE_GAS_CELL::get, 1), out(EarthOnMinecraft.HYDROGEN_GAS_CELL::get, 1)));
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
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.POLYSTYRENE_RESIN::get, "聚苯乙烯泡沫化兼容材料", out(() -> Items.SLIME_BALL, 1), out(() -> Items.DYE.white(), 1)));
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
        recipes.add(r(Kind.CHEMICAL_REACTOR, () -> Items.WILDFLOWERS, "野花植物色素浸提", out(() -> Items.DYE.yellow(), 2), out(EarthOnMinecraft.CELLULOSE_FIBER::get, 1), out(EarthOnMinecraft.HUMUS_SAMPLE::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, () -> Items.PINK_PETALS, "粉色花瓣色素浸提", out(() -> Items.DYE.pink(), 2), out(EarthOnMinecraft.CELLULOSE_FIBER::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, () -> Items.CACTUS_FLOWER, "仙人掌花色素和蜡质回收", out(() -> Items.DYE.pink(), 1), out(EarthOnMinecraft.NATURAL_LATEX::get, 1)));
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
        recipes.add(r(Kind.SYNTHESIS_LOOP, EarthOnMinecraft.IRON_CATALYST::get, "铁催化合成气入口", out(EarthOnMinecraft.AMMONIA::get, 1), out(EarthOnMinecraft.METHANOL::get, 1)));
        recipes.add(r(Kind.ABSORPTION_TOWER, EarthOnMinecraft.VANADIUM_CATALYST::get, "钒催化二氧化硫吸收", out(EarthOnMinecraft.SULFURIC_ACID::get, 2)));
        recipes.add(r(Kind.SYNTHESIS_LOOP, EarthOnMinecraft.NICKEL_CATALYST::get, "镍催化加氢入口", out(EarthOnMinecraft.HYDROGEN_GAS_CELL::get, 1), out(EarthOnMinecraft.INDUSTRIAL_SOLVENT::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.PLATINUM_GROUP_CATALYST::get, "铂族催化氧化入口", out(EarthOnMinecraft.NITRIC_ACID::get, 1), out(EarthOnMinecraft.OXYGEN_GAS_CELL::get, 1)));

        recipes.add(r(Kind.ABSORPTION_TOWER, EarthOnMinecraft.CHLORINE_GAS_CELL::get, "氯气吸收制次氯酸盐和盐酸", out(EarthOnMinecraft.SODIUM_HYPOCHLORITE::get, 1), out(EarthOnMinecraft.HYDROCHLORIC_ACID::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.SODIUM_HYDROXIDE::get, "烧碱氯化制漂白液近似路线", out(EarthOnMinecraft.SODIUM_HYPOCHLORITE::get, 1), out(EarthOnMinecraft.NEUTRAL_SALT::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.SODIUM_HYPOCHLORITE::get, "次氯酸盐吸附成漂白粉", out(EarthOnMinecraft.BLEACHING_POWDER::get, 1), out(EarthOnMinecraft.SLAKED_LIME::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.HYDROGEN_GAS_CELL::get, "氢氧合成过氧化氢近似路线", out(EarthOnMinecraft.HYDROGEN_PEROXIDE::get, 1), out(EarthOnMinecraft.OXYGEN_GAS_CELL::get, 1)));
        recipes.add(r(Kind.ABSORPTION_TOWER, EarthOnMinecraft.HYDROGEN_PEROXIDE::get, "过氧化氢漂白纸浆", out(EarthOnMinecraft.BLEACHED_PULP::get, 1), out(EarthOnMinecraft.SOFTENED_WATER::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, () -> Items.WHEAT_SEEDS, "植物油皂化入口", out(EarthOnMinecraft.SOAP_BASE::get, 1), out(EarthOnMinecraft.GLYCEROL::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.SOAP_BASE::get, "皂基复配表面活性剂", out(EarthOnMinecraft.SURFACTANT::get, 1), out(EarthOnMinecraft.GLYCEROL::get, 1)));
        recipes.add(r(Kind.MIXER, EarthOnMinecraft.SURFACTANT::get, "洗涤剂粉体配混", out(EarthOnMinecraft.DETERGENT_POWDER::get, 2), out(EarthOnMinecraft.SODA_ASH::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.DETERGENT_POWDER::get, "洗涤剂整理为白色兼容染料", out(() -> Items.DYE.white(), 2), out(EarthOnMinecraft.SODIUM_SULFATE::get, 1)));

        recipes.add(r(Kind.STEAM_CRACKER, EarthOnMinecraft.ETHYLENE::get, "乙烯裂解副产丁二烯和丙烯腈入口", out(EarthOnMinecraft.BUTADIENE::get, 1), out(EarthOnMinecraft.ACRYLONITRILE::get, 1), out(EarthOnMinecraft.HYDROGEN_GAS_CELL::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.PROPYLENE::get, "丙烯氧化制亚克力单体", out(EarthOnMinecraft.ACRYLIC_MONOMER::get, 1), out(EarthOnMinecraft.ACETONE::get, 1)));
        recipes.add(r(Kind.POLYMERIZER, EarthOnMinecraft.ACRYLONITRILE::get, "丙烯腈丁二烯苯乙烯共聚近似", out(EarthOnMinecraft.ABS_RESIN::get, 1), out(EarthOnMinecraft.BUTADIENE::get, 1)));
        recipes.add(r(Kind.POLYMERIZER, EarthOnMinecraft.ACRYLIC_MONOMER::get, "亚克力树脂聚合", out(EarthOnMinecraft.ACRYLIC_RESIN::get, 2)));
        recipes.add(r(Kind.SYNTHESIS_LOOP, EarthOnMinecraft.FORMALDEHYDE::get, "甲醛路线制多元醇和脲醛树脂", out(EarthOnMinecraft.POLYOL::get, 1), out(EarthOnMinecraft.UREA_FORMALDEHYDE_RESIN::get, 1)));
        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.AMMONIA::get, "氨基路线制异氰酸酯中间物", out(EarthOnMinecraft.ISOCYANATE::get, 1), out(EarthOnMinecraft.UREA::get, 1)));
        recipes.add(r(Kind.POLYMERIZER, EarthOnMinecraft.POLYOL::get, "聚氨酯发泡", out(EarthOnMinecraft.POLYURETHANE_FOAM::get, 2), out(EarthOnMinecraft.ISOCYANATE::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.ABS_RESIN::get, "ABS 树脂压制为耐冲击兼容材料", out(() -> Items.SLIME_BALL, 1), out(EarthOnMinecraft.CARBON_BLACK::get, 1)));
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.POLYURETHANE_FOAM::get, "聚氨酯泡沫整理为轻质纤维", out(() -> Items.STRING, 2), out(() -> Blocks.WOOL.white(), 1)));
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
        recipes.add(r(Kind.POWDER_PRESS, EarthOnMinecraft.MINERAL_WOOL::get, "矿物棉整理为隔热纤维", out(() -> Items.STRING, 3), out(() -> Blocks.WOOL.white(), 1)));

        recipes.add(r(Kind.CHEMICAL_REACTOR, EarthOnMinecraft.SULFURIC_ACID::get, "硫酸铵肥料中和", out(EarthOnMinecraft.AMMONIUM_SULFATE::get, 1), out(EarthOnMinecraft.GYPSUM_DUST::get, 1)));
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

        return List.copyOf(recipes);
    }

    private static Recipe r(Kind kind, Supplier<? extends ItemLike> input, String note, Output... outputs) {
        return new Recipe(kind, input, note, List.of(outputs));
    }

    private static Output out(Supplier<? extends ItemLike> item, int count) {
        return new Output(item, count);
    }

    public record Recipe(Kind kind, Supplier<? extends ItemLike> input, String note, List<Output> outputs) {
        public ItemStack inputStack() {
            return new ItemStack(input.get().asItem());
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

    public enum Kind {
        CRUSHER("颚式破碎机", "把矿石和矿床方块粗碎成碎块，通常会产生尾粉或伴生矿物。"),
        BALL_MILL("球磨机", "把碎块和岩石磨成粉末，是分离矿物组分的基础步骤。"),
        SIEVE("筛分机", "按颗粒大小和密度做简化筛分，适合尾粉、金伯利岩和含金石英碎块。"),
        MAGNETIC_SEPARATOR("磁选机", "利用磁性分离磁铁矿、赤铁矿和镁铁质硅酸盐里的含铁组分。"),
        FLOTATION_CELL("浮选槽", "富集硫化矿和宝石矿物，让铜、金、青金石、红石等进入精矿。"),
        ROASTER("焙烧炉", "把硫化矿、辰砂和碳酸盐做热处理，产出焙烧矿、硫粉或石灰粉。"),
        REDUCTION_FURNACE("还原炉", "用简化碳热还原把精矿变成兼容 MC 的锭，同时产生矿渣。"),
        LEACHING_TANK("浸出槽", "模拟湿法冶金，从石英脉、绿柱石和红石精矿里选择性提取目标物。"),
        ELECTROLYTIC_CELL("电解槽", "用现代电化学路线精炼铜、金、青金石和绿柱石精矿。"),
        POWDER_PRESS("压粉机", "把粉末或精矿压回 MC 兼容物品，保留合成魔法的便利性。"),
        CHEMICAL_REACTOR("化学反应釜", "承载酸碱中和、酸解、氧化和常见无机化工反应的简化路线。"),
        DISTILLATION_COLUMN("精馏塔", "分离盐卤、盐酸和煤焦油等混合物，得到更集中的化工原料。"),
        MIXER("工业混合机", "把粉体和母料混合成玻璃配合料、水泥生料、肥料母料等中间物。"),
        CRYSTALLIZER("结晶器", "把盐、烧碱、硝酸铵、石膏等溶液或粉体整理成可堆叠产品。"),
        INDUSTRIAL_KILN("工业窑炉", "处理玻璃、水泥、铝土矿和其他需要高温煅烧的化工路线。"),
        GAS_SEPARATOR("气体分离器", "从煤气、氯气等气体混合物中回收氢、氨、盐酸等产品。"),
        FERTILIZER_GRANULATOR("肥料造粒机", "把氮肥、磷肥和复合肥母料整理成可运输的颗粒产品。"),
        POLYMERIZER("聚合釜", "把乙烯、丙烯和氯乙烯聚合为常见塑料树脂。"),
        STEAM_CRACKER("蒸汽裂解炉", "把煤焦油、煤气和混合树脂裂解成芳烃、烯烃与合成气入口。"),
        SYNTHESIS_LOOP("合成塔", "承载合成氨、甲醇、尿素和树脂前驱体这类高压循环反应。"),
        ABSORPTION_TOWER("吸收塔", "把酸性或含氯尾气吸收为硫酸、盐酸、纯碱等可用产品。");

        private final String displayName;
        private final String description;

        Kind(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
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
            };
        }
    }
}
