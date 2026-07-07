package com.xxsx.earthonminecraft;

import com.mojang.logging.LogUtils;
import com.xxsx.earthonminecraft.client.EarthOnMinecraftClient;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

@Mod(EarthOnMinecraft.MODID)
public class EarthOnMinecraft {
    public static final String MODID = "earth_on_minecraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);

    private static final List<ItemLike> TAB_ITEMS = new ArrayList<>();
    private static final List<DeferredBlock<ProcessingMachineBlock>> MACHINE_BLOCKS = new ArrayList<>();

    public static final DeferredBlock<Block> POOR_MAGNETITE_ORE = oreBlock("poor_magnetite_ore", MapColor.DEEPSLATE, 3.5F);
    public static final DeferredBlock<Block> MAGNETITE_ORE = oreBlock("magnetite_ore", MapColor.COLOR_BLACK, 4.0F);
    public static final DeferredBlock<Block> RICH_MAGNETITE_ORE = oreBlock("rich_magnetite_ore", MapColor.RAW_IRON, 4.5F);
    public static final DeferredBlock<Block> CHALCOPYRITE_ORE = oreBlock("chalcopyrite_ore", MapColor.GOLD, 3.5F);
    public static final DeferredBlock<Block> AURIFEROUS_QUARTZ_VEIN = oreBlock("auriferous_quartz_vein", MapColor.QUARTZ, 3.2F);
    public static final DeferredBlock<Block> BITUMINOUS_COAL_SEAM = oreBlock("bituminous_coal_seam", MapColor.COLOR_BLACK, 2.8F);
    public static final DeferredBlock<Block> ANTHRACITE_COAL_SEAM = oreBlock("anthracite_coal_seam", MapColor.COLOR_BLACK, 3.2F);
    public static final DeferredBlock<Block> KIMBERLITE = oreBlock("kimberlite", MapColor.COLOR_GREEN, 3.5F);
    public static final DeferredBlock<Block> DIAMONDIFEROUS_KIMBERLITE = oreBlock("diamondiferous_kimberlite", MapColor.DIAMOND, 4.2F);
    public static final DeferredBlock<Block> LAPIS_LAZULI_ORE = oreBlock("lapis_lazuli_ore", MapColor.LAPIS, 3.2F);
    public static final DeferredBlock<Block> EMERALD_BERYL_VEIN = oreBlock("emerald_beryl_vein", MapColor.EMERALD, 3.5F);
    public static final DeferredBlock<Block> REDSTONE_MINERAL_ORE = oreBlock("redstone_mineral_ore", MapColor.COLOR_RED, 3.2F);
    public static final DeferredBlock<Block> CINNABAR_VEIN = oreBlock("cinnabar_vein", MapColor.COLOR_RED, 2.6F);
    public static final DeferredBlock<Block> BAUXITE_LATERITE_DEPOSIT = oreBlock("bauxite_laterite_deposit", MapColor.TERRACOTTA_ORANGE, 2.8F);
    public static final DeferredBlock<Block> PHOSPHORITE_BED = oreBlock("phosphorite_bed", MapColor.SAND, 2.6F);
    public static final DeferredBlock<Block> EVAPORITE_SALT_BED = oreBlock("evaporite_salt_bed", MapColor.QUARTZ, 2.2F);
    public static final DeferredBlock<Block> CASSITERITE_PLACER_DEPOSIT = oreBlock("cassiterite_placer_deposit", MapColor.SAND, 2.4F);
    public static final DeferredBlock<Block> RARE_EARTH_CARBONATITE = oreBlock("rare_earth_carbonatite", MapColor.TERRACOTTA_WHITE, 3.4F);

    public static final DeferredBlock<ProcessingMachineBlock> JAW_CRUSHER = machineBlock("jaw_crusher", ProcessingMachineBlock.Kind.CRUSHER);
    public static final DeferredBlock<ProcessingMachineBlock> BALL_MILL = machineBlock("ball_mill", ProcessingMachineBlock.Kind.BALL_MILL);
    public static final DeferredBlock<ProcessingMachineBlock> SIEVE = machineBlock("sieve", ProcessingMachineBlock.Kind.SIEVE);
    public static final DeferredBlock<ProcessingMachineBlock> MAGNETIC_SEPARATOR = machineBlock("magnetic_separator", ProcessingMachineBlock.Kind.MAGNETIC_SEPARATOR);
    public static final DeferredBlock<ProcessingMachineBlock> FLOTATION_CELL = machineBlock("flotation_cell", ProcessingMachineBlock.Kind.FLOTATION_CELL);
    public static final DeferredBlock<ProcessingMachineBlock> ORE_ROASTER = machineBlock("ore_roaster", ProcessingMachineBlock.Kind.ROASTER);
    public static final DeferredBlock<ProcessingMachineBlock> REDUCTION_FURNACE = machineBlock("reduction_furnace", ProcessingMachineBlock.Kind.REDUCTION_FURNACE);
    public static final DeferredBlock<ProcessingMachineBlock> LEACHING_TANK = machineBlock("leaching_tank", ProcessingMachineBlock.Kind.LEACHING_TANK);
    public static final DeferredBlock<ProcessingMachineBlock> ELECTROLYTIC_CELL = machineBlock("electrolytic_cell", ProcessingMachineBlock.Kind.ELECTROLYTIC_CELL);
    public static final DeferredBlock<ProcessingMachineBlock> POWDER_PRESS = machineBlock("powder_press", ProcessingMachineBlock.Kind.POWDER_PRESS);
    public static final DeferredBlock<ProcessingMachineBlock> CHEMICAL_REACTOR = machineBlock("chemical_reactor", ProcessingMachineBlock.Kind.CHEMICAL_REACTOR);
    public static final DeferredBlock<ProcessingMachineBlock> DISTILLATION_COLUMN = machineBlock("distillation_column", ProcessingMachineBlock.Kind.DISTILLATION_COLUMN);
    public static final DeferredBlock<ProcessingMachineBlock> MIXER = machineBlock("mixer", ProcessingMachineBlock.Kind.MIXER);
    public static final DeferredBlock<ProcessingMachineBlock> CRYSTALLIZER = machineBlock("crystallizer", ProcessingMachineBlock.Kind.CRYSTALLIZER);
    public static final DeferredBlock<ProcessingMachineBlock> INDUSTRIAL_KILN = machineBlock("industrial_kiln", ProcessingMachineBlock.Kind.INDUSTRIAL_KILN);
    public static final DeferredBlock<ProcessingMachineBlock> GAS_SEPARATOR = machineBlock("gas_separator", ProcessingMachineBlock.Kind.GAS_SEPARATOR);
    public static final DeferredBlock<ProcessingMachineBlock> FERTILIZER_GRANULATOR = machineBlock("fertilizer_granulator", ProcessingMachineBlock.Kind.FERTILIZER_GRANULATOR);
    public static final DeferredBlock<ProcessingMachineBlock> POLYMERIZER = machineBlock("polymerizer", ProcessingMachineBlock.Kind.POLYMERIZER);
    public static final DeferredBlock<ProcessingMachineBlock> STEAM_CRACKER = machineBlock("steam_cracker", ProcessingMachineBlock.Kind.STEAM_CRACKER);
    public static final DeferredBlock<ProcessingMachineBlock> SYNTHESIS_LOOP = machineBlock("synthesis_loop", ProcessingMachineBlock.Kind.SYNTHESIS_LOOP);
    public static final DeferredBlock<ProcessingMachineBlock> ABSORPTION_TOWER = machineBlock("absorption_tower", ProcessingMachineBlock.Kind.ABSORPTION_TOWER);

    public static final DeferredBlock<SupportPartBlock> INDUSTRIAL_MACHINE_CASING = supportBlock("industrial_machine_casing", MapColor.METAL, 4.5F, "tooltip.earth_on_minecraft.support.casing");
    public static final DeferredBlock<SupportPartBlock> STEEL_PROCESS_PIPE = supportBlock("steel_process_pipe", MapColor.METAL, 3.0F, "tooltip.earth_on_minecraft.support.pipe");
    public static final DeferredBlock<ControlPanelBlock> CONTROL_PANEL = controlPanelBlock("control_panel", MapColor.COLOR_GRAY, 3.5F, "tooltip.earth_on_minecraft.support.control_panel");
    public static final DeferredBlock<StructureProjectionBlock> STRUCTURE_PROJECTION = structureProjectionBlock("structure_projection");
    public static final DeferredBlock<MachineInterfaceBlock> MACHINE_INPUT_INTERFACE = machineInterfaceBlock("machine_input_interface", MachineInterfaceBlock.InterfaceType.INPUT, "tooltip.earth_on_minecraft.support.input_interface");
    public static final DeferredBlock<MachineInterfaceBlock> MACHINE_OUTPUT_INTERFACE = machineInterfaceBlock("machine_output_interface", MachineInterfaceBlock.InterfaceType.OUTPUT, "tooltip.earth_on_minecraft.support.output_interface");
    public static final DeferredBlock<EnergyGeneratorBlock> COMBUSTION_GENERATOR = energyGeneratorBlock("combustion_generator");
    public static final DeferredBlock<EnergyGeneratorBlock> STEAM_TURBINE_GENERATOR = energyGeneratorBlock("steam_turbine_generator");
    public static final DeferredBlock<PowerCableBlock> THIN_COPPER_POWER_CABLE = powerCableBlock("thin_copper_power_cable", 1);
    public static final DeferredBlock<PowerCableBlock> COPPER_POWER_CABLE = powerCableBlock("copper_power_cable", 2);
    public static final DeferredBlock<PowerCableBlock> HEAVY_COPPER_POWER_CABLE = powerCableBlock("heavy_copper_power_cable", 3);
    public static final DeferredBlock<BatteryBoxBlock> BATTERY_BOX = batteryBoxBlock("battery_box");
    public static final DeferredBlock<ConveyorBeltBlock> CONVEYOR_BELT = conveyorBeltBlock("conveyor_belt");

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessingMachineBlockEntity>> PROCESSING_MACHINE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("processing_machine", () -> new BlockEntityType<>(
                    ProcessingMachineBlockEntity::new,
                    MACHINE_BLOCKS.stream().map(DeferredBlock::get).toArray(Block[]::new)));
    public static final DeferredHolder<MenuType<?>, MenuType<ProcessingMachineMenu>> PROCESSING_MACHINE_MENU =
            MENUS.register("processing_machine", () -> IMenuTypeExtension.create(ProcessingMachineMenu::new));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyGeneratorBlockEntity>> ENERGY_GENERATOR_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("combustion_generator", () -> new BlockEntityType<>(
                    EnergyGeneratorBlockEntity::new,
                    COMBUSTION_GENERATOR.get(),
                    STEAM_TURBINE_GENERATOR.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BatteryBoxBlockEntity>> BATTERY_BOX_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("battery_box", () -> new BlockEntityType<>(
                    BatteryBoxBlockEntity::new,
                    BATTERY_BOX.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineInterfaceBlockEntity>> MACHINE_INTERFACE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("machine_interface", () -> new BlockEntityType<>(
                    MachineInterfaceBlockEntity::new,
                    MACHINE_INPUT_INTERFACE.get(),
                    MACHINE_OUTPUT_INTERFACE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConveyorBeltBlockEntity>> CONVEYOR_BELT_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("conveyor_belt", () -> new BlockEntityType<>(
                    ConveyorBeltBlockEntity::new,
                    CONVEYOR_BELT.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<EnergyGeneratorMenu>> ENERGY_GENERATOR_MENU =
            MENUS.register("combustion_generator", () -> IMenuTypeExtension.create(EnergyGeneratorMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<BatteryBoxMenu>> BATTERY_BOX_MENU =
            MENUS.register("battery_box", () -> IMenuTypeExtension.create(BatteryBoxMenu::new));

    public static final DeferredItem<Item> MAGNETITE_CHUNK = item("magnetite_chunk");
    public static final DeferredItem<Item> CHALCOPYRITE_CHUNK = item("chalcopyrite_chunk");
    public static final DeferredItem<Item> AURIFEROUS_QUARTZ_CHUNK = item("auriferous_quartz_chunk");
    public static final DeferredItem<Item> KIMBERLITE_CHUNK = item("kimberlite_chunk");
    public static final DeferredItem<Item> MAGNETITE_DUST = item("magnetite_dust");
    public static final DeferredItem<Item> CHALCOPYRITE_DUST = item("chalcopyrite_dust");
    public static final DeferredItem<Item> QUARTZ_DUST = item("quartz_dust");
    public static final DeferredItem<Item> FELDSPAR_DUST = item("feldspar_dust");
    public static final DeferredItem<Item> MICA_DUST = item("mica_dust");
    public static final DeferredItem<Item> TAILINGS_DUST = item("tailings_dust");
    public static final DeferredItem<Item> COAL_DUST = item("coal_dust");
    public static final DeferredItem<Item> GOLD_DUST = item("gold_dust");
    public static final DeferredItem<Item> DIAMOND_GRIT = item("diamond_grit");
    public static final DeferredItem<Item> CINNABAR_CHUNK = item("cinnabar_chunk");
    public static final DeferredItem<Item> CINNABAR_DUST = item("cinnabar_dust");
    public static final DeferredItem<Item> SILICA_DUST = item("silica_dust");
    public static final DeferredItem<Item> CALCITE_DUST = item("calcite_dust");
    public static final DeferredItem<Item> LIME_DUST = item("lime_dust");
    public static final DeferredItem<Item> HEMATITE_DUST = item("hematite_dust");
    public static final DeferredItem<Item> PYRITE_DUST = item("pyrite_dust");
    public static final DeferredItem<Item> SULFUR_DUST = item("sulfur_dust");
    public static final DeferredItem<Item> MAFIC_SILICATE_DUST = item("mafic_silicate_dust");
    public static final DeferredItem<Item> ALUMINOSILICATE_DUST = item("aluminosilicate_dust");
    public static final DeferredItem<Item> IRON_CONCENTRATE = item("iron_concentrate");
    public static final DeferredItem<Item> COPPER_CONCENTRATE = item("copper_concentrate");
    public static final DeferredItem<Item> GOLD_CONCENTRATE = item("gold_concentrate");
    public static final DeferredItem<Item> ROASTED_COPPER_CONCENTRATE = item("roasted_copper_concentrate");
    public static final DeferredItem<Item> REDSTONE_CONCENTRATE = item("redstone_concentrate");
    public static final DeferredItem<Item> LAPIS_CONCENTRATE = item("lapis_concentrate");
    public static final DeferredItem<Item> BERYL_CONCENTRATE = item("beryl_concentrate");
    public static final DeferredItem<Item> MERCURY_DROPLET = item("mercury_droplet");
    public static final DeferredItem<Item> SLAG = item("slag");
    public static final DeferredItem<Item> SALT_DUST = item("salt_dust");
    public static final DeferredItem<Item> BRINE_CRYSTAL = item("brine_crystal");
    public static final DeferredItem<Item> SODIUM_HYDROXIDE = item("sodium_hydroxide");
    public static final DeferredItem<Item> CHLORINE_GAS_CELL = item("chlorine_gas_cell");
    public static final DeferredItem<Item> HYDROGEN_GAS_CELL = item("hydrogen_gas_cell");
    public static final DeferredItem<Item> HYDROCHLORIC_ACID = item("hydrochloric_acid");
    public static final DeferredItem<Item> SULFURIC_ACID = item("sulfuric_acid");
    public static final DeferredItem<Item> NITRIC_ACID = item("nitric_acid");
    public static final DeferredItem<Item> AMMONIA = item("ammonia");
    public static final DeferredItem<Item> AMMONIUM_NITRATE = item("ammonium_nitrate");
    public static final DeferredItem<Item> PHOSPHATE_ROCK_DUST = item("phosphate_rock_dust");
    public static final DeferredItem<Item> PHOSPHORIC_ACID = item("phosphoric_acid");
    public static final DeferredItem<Item> FERTILIZER_BLEND = item("fertilizer_blend");
    public static final DeferredItem<Item> SODA_ASH = item("soda_ash");
    public static final DeferredItem<Item> SODIUM_BICARBONATE = item("sodium_bicarbonate");
    public static final DeferredItem<Item> GYPSUM_DUST = item("gypsum_dust");
    public static final DeferredItem<Item> CEMENT_RAW_MEAL = item("cement_raw_meal");
    public static final DeferredItem<Item> CEMENT_CLINKER = item("cement_clinker");
    public static final DeferredItem<Item> CEMENT_POWDER = item("cement_powder");
    public static final DeferredItem<Item> CLAY_DUST = item("clay_dust");
    public static final DeferredItem<Item> BAUXITE_DUST = item("bauxite_dust");
    public static final DeferredItem<Item> ALUMINUM_HYDROXIDE = item("aluminum_hydroxide");
    public static final DeferredItem<Item> ALUMINA = item("alumina");
    public static final DeferredItem<Item> GLASS_BATCH = item("glass_batch");
    public static final DeferredItem<Item> COKE = item("coke");
    public static final DeferredItem<Item> COAL_TAR = item("coal_tar");
    public static final DeferredItem<Item> COAL_GAS_CELL = item("coal_gas_cell");
    public static final DeferredItem<Item> ETHYLENE = item("ethylene");
    public static final DeferredItem<Item> POLYMER_RESIN = item("polymer_resin");
    public static final DeferredItem<Item> CALCIUM_CHLORIDE = item("calcium_chloride");
    public static final DeferredItem<Item> SLAKED_LIME = item("slaked_lime");
    public static final DeferredItem<Item> NITROGEN_GAS_CELL = item("nitrogen_gas_cell");
    public static final DeferredItem<Item> OXYGEN_GAS_CELL = item("oxygen_gas_cell");
    public static final DeferredItem<Item> CARBON_DIOXIDE_CELL = item("carbon_dioxide_cell");
    public static final DeferredItem<Item> SULFUR_DIOXIDE_CELL = item("sulfur_dioxide_cell");
    public static final DeferredItem<Item> METHANOL = item("methanol");
    public static final DeferredItem<Item> FORMALDEHYDE = item("formaldehyde");
    public static final DeferredItem<Item> UREA = item("urea");
    public static final DeferredItem<Item> POTASSIUM_CHLORIDE = item("potassium_chloride");
    public static final DeferredItem<Item> POTASH = item("potash");
    public static final DeferredItem<Item> POTASSIUM_NITRATE = item("potassium_nitrate");
    public static final DeferredItem<Item> SODIUM_SULFATE = item("sodium_sulfate");
    public static final DeferredItem<Item> BENZENE = item("benzene");
    public static final DeferredItem<Item> PROPYLENE = item("propylene");
    public static final DeferredItem<Item> VINYL_CHLORIDE = item("vinyl_chloride");
    public static final DeferredItem<Item> POLYETHYLENE_RESIN = item("polyethylene_resin");
    public static final DeferredItem<Item> POLYPROPYLENE_RESIN = item("polypropylene_resin");
    public static final DeferredItem<Item> PVC_RESIN = item("pvc_resin");
    public static final DeferredItem<Item> STEEL_BLOOM = item("steel_bloom");
    public static final DeferredItem<Item> FERROSILICON = item("ferrosilicon");
    public static final DeferredItem<Item> ALUMINUM_INGOT = item("aluminum_ingot");
    public static final DeferredItem<Item> CRUDE_OIL_SAMPLE = item("crude_oil_sample");
    public static final DeferredItem<Item> NATURAL_GAS_CELL = item("natural_gas_cell");
    public static final DeferredItem<Item> NAPHTHA = item("naphtha");
    public static final DeferredItem<Item> KEROSENE_FRACTION = item("kerosene_fraction");
    public static final DeferredItem<Item> DIESEL_FRACTION = item("diesel_fraction");
    public static final DeferredItem<Item> LUBRICATING_OIL = item("lubricating_oil");
    public static final DeferredItem<Item> ASPHALT = item("asphalt");
    public static final DeferredItem<Item> PETROLEUM_COKE = item("petroleum_coke");
    public static final DeferredItem<Item> WOOD_CHIPS = item("wood_chips");
    public static final DeferredItem<Item> CELLULOSE_PULP = item("cellulose_pulp");
    public static final DeferredItem<Item> BLEACHED_PULP = item("bleached_pulp");
    public static final DeferredItem<Item> CELLULOSE_FIBER = item("cellulose_fiber");
    public static final DeferredItem<Item> TITANIUM_DIOXIDE = item("titanium_dioxide");
    public static final DeferredItem<Item> IRON_OXIDE_PIGMENT = item("iron_oxide_pigment");
    public static final DeferredItem<Item> CARBON_BLACK = item("carbon_black");
    public static final DeferredItem<Item> PAINT_BASE = item("paint_base");
    public static final DeferredItem<Item> STYRENE = item("styrene");
    public static final DeferredItem<Item> POLYSTYRENE_RESIN = item("polystyrene_resin");
    public static final DeferredItem<Item> ETHYLENE_GLYCOL = item("ethylene_glycol");
    public static final DeferredItem<Item> TEREPHTHALIC_ACID = item("terephthalic_acid");
    public static final DeferredItem<Item> PET_RESIN = item("pet_resin");
    public static final DeferredItem<Item> SYNTHETIC_RUBBER = item("synthetic_rubber");
    public static final DeferredItem<Item> CAPROLACTAM = item("caprolactam");
    public static final DeferredItem<Item> NYLON_FIBER = item("nylon_fiber");
    public static final DeferredItem<Item> GRAPHITE_DUST = item("graphite_dust");
    public static final DeferredItem<Item> ACTIVATED_CARBON = item("activated_carbon");
    public static final DeferredItem<Item> BATTERY_CARBON = item("battery_carbon");
    public static final DeferredItem<Item> MANGANESE_OXIDE_DUST = item("manganese_oxide_dust");
    public static final DeferredItem<Item> NICKEL_PRECURSOR = item("nickel_precursor");
    public static final DeferredItem<Item> LITHIUM_SALT = item("lithium_salt");
    public static final DeferredItem<Item> ELECTROLYTE = item("electrolyte");
    public static final DeferredItem<Item> ELECTRODE_SHEET = item("electrode_sheet");
    public static final DeferredItem<Item> SIMPLE_BATTERY_CELL = item("simple_battery_cell");
    public static final DeferredItem<Item> HARD_WATER_SAMPLE = item("hard_water_sample");
    public static final DeferredItem<Item> SOFTENED_WATER = item("softened_water");
    public static final DeferredItem<Item> HUMUS_SAMPLE = item("humus_sample");
    public static final DeferredItem<Item> SANDY_LOAM_SAMPLE = item("sandy_loam_sample");
    public static final DeferredItem<Item> ALLUVIAL_LOAM_SAMPLE = item("alluvial_loam_sample");
    public static final DeferredItem<Item> SALINE_SOIL_SAMPLE = item("saline_soil_sample");
    public static final DeferredItem<Item> SOIL_MINERAL_MIX = item("soil_mineral_mix");
    public static final DeferredItem<Item> IRRIGATION_MINERAL_DEPOSIT = item("irrigation_mineral_deposit");
    public static final DeferredItem<Item> SLUDGE_CAKE = item("sludge_cake");
    public static final DeferredItem<Item> NEUTRAL_SALT = item("neutral_salt");
    public static final DeferredItem<Item> ACTIVATED_CARBON_FILTER = item("activated_carbon_filter");
    public static final DeferredItem<Item> LIME_TREATMENT_RESIDUE = item("lime_treatment_residue");
    public static final DeferredItem<Item> STABILIZED_TAILINGS = item("stabilized_tailings");
    public static final DeferredItem<Item> NATURAL_LATEX = item("natural_latex");
    public static final DeferredItem<Item> RAW_RUBBER = item("raw_rubber");
    public static final DeferredItem<Item> VULCANIZED_RUBBER = item("vulcanized_rubber");
    public static final DeferredItem<Item> RUBBER_COMPOUND = item("rubber_compound");
    public static final DeferredItem<Item> RUBBER_GASKET = item("rubber_gasket");
    public static final DeferredItem<Item> ETHANOL = item("ethanol");
    public static final DeferredItem<Item> ACETIC_ACID = item("acetic_acid");
    public static final DeferredItem<Item> ACETONE = item("acetone");
    public static final DeferredItem<Item> PHENOL = item("phenol");
    public static final DeferredItem<Item> PHENOLIC_RESIN = item("phenolic_resin");
    public static final DeferredItem<Item> EPOXY_RESIN = item("epoxy_resin");
    public static final DeferredItem<Item> INDUSTRIAL_SOLVENT = item("industrial_solvent");
    public static final DeferredItem<Item> CHROMITE_DUST = item("chromite_dust");
    public static final DeferredItem<Item> FERROCHROME = item("ferrochrome");
    public static final DeferredItem<Item> FERROMANGANESE = item("ferromanganese");
    public static final DeferredItem<Item> STAINLESS_STEEL_BLOOM = item("stainless_steel_bloom");
    public static final DeferredItem<Item> ALUMINUM_ALLOY_BILLET = item("aluminum_alloy_billet");
    public static final DeferredItem<Item> MAGNESIUM_DUST = item("magnesium_dust");
    public static final DeferredItem<Item> TITANIUM_SLAG = item("titanium_slag");
    public static final DeferredItem<Item> TITANIUM_TETRACHLORIDE = item("titanium_tetrachloride");
    public static final DeferredItem<Item> TITANIUM_SPONGE = item("titanium_sponge");
    public static final DeferredItem<Item> METALLURGICAL_SILICON = item("metallurgical_silicon");
    public static final DeferredItem<Item> CHLOROSILANE = item("chlorosilane");
    public static final DeferredItem<Item> HIGH_PURITY_SILICON = item("high_purity_silicon");
    public static final DeferredItem<Item> POLYSILICON = item("polysilicon");
    public static final DeferredItem<Item> SILICON_WAFER = item("silicon_wafer");
    public static final DeferredItem<Item> DOPANT_DUST = item("dopant_dust");
    public static final DeferredItem<Item> PHOTORESIST_PRECURSOR = item("photoresist_precursor");
    public static final DeferredItem<Item> MONAZITE_SAND = item("monazite_sand");
    public static final DeferredItem<Item> BASTNASITE_DUST = item("bastnasite_dust");
    public static final DeferredItem<Item> MIXED_RARE_EARTH_OXIDE = item("mixed_rare_earth_oxide");
    public static final DeferredItem<Item> NEODYMIUM_SALT = item("neodymium_salt");
    public static final DeferredItem<Item> NDFEB_MAGNET = item("ndfeb_magnet");
    public static final DeferredItem<Item> RARE_EARTH_TAILINGS = item("rare_earth_tailings");
    public static final DeferredItem<Item> IRON_CATALYST = item("iron_catalyst");
    public static final DeferredItem<Item> VANADIUM_CATALYST = item("vanadium_catalyst");
    public static final DeferredItem<Item> NICKEL_CATALYST = item("nickel_catalyst");
    public static final DeferredItem<Item> PLATINUM_GROUP_CATALYST = item("platinum_group_catalyst");
    public static final DeferredItem<Item> SODIUM_HYPOCHLORITE = item("sodium_hypochlorite");
    public static final DeferredItem<Item> BLEACHING_POWDER = item("bleaching_powder");
    public static final DeferredItem<Item> HYDROGEN_PEROXIDE = item("hydrogen_peroxide");
    public static final DeferredItem<Item> SOAP_BASE = item("soap_base");
    public static final DeferredItem<Item> GLYCEROL = item("glycerol");
    public static final DeferredItem<Item> SURFACTANT = item("surfactant");
    public static final DeferredItem<Item> DETERGENT_POWDER = item("detergent_powder");
    public static final DeferredItem<Item> BUTADIENE = item("butadiene");
    public static final DeferredItem<Item> ACRYLONITRILE = item("acrylonitrile");
    public static final DeferredItem<Item> ABS_RESIN = item("abs_resin");
    public static final DeferredItem<Item> ACRYLIC_MONOMER = item("acrylic_monomer");
    public static final DeferredItem<Item> ACRYLIC_RESIN = item("acrylic_resin");
    public static final DeferredItem<Item> POLYOL = item("polyol");
    public static final DeferredItem<Item> ISOCYANATE = item("isocyanate");
    public static final DeferredItem<Item> POLYURETHANE_FOAM = item("polyurethane_foam");
    public static final DeferredItem<Item> COPPER_WIRE = item("copper_wire");
    public static final DeferredItem<Item> FIBERGLASS_CLOTH = item("fiberglass_cloth");
    public static final DeferredItem<Item> COPPER_CLAD_LAMINATE = item("copper_clad_laminate");
    public static final DeferredItem<Item> PRINTED_CIRCUIT_BOARD = item("printed_circuit_board");
    public static final DeferredItem<Item> SOLDER_ALLOY = item("solder_alloy");
    public static final DeferredItem<Item> SOLDER_FLUX = item("solder_flux");
    public static final DeferredItem<Item> LED_PHOSPHOR = item("led_phosphor");
    public static final DeferredItem<Item> CERAMIC_SUBSTRATE = item("ceramic_substrate");
    public static final DeferredItem<Item> SPHALERITE_DUST = item("sphalerite_dust");
    public static final DeferredItem<Item> ZINC_OXIDE = item("zinc_oxide");
    public static final DeferredItem<Item> ZINC_INGOT = item("zinc_ingot");
    public static final DeferredItem<Item> GALENA_DUST = item("galena_dust");
    public static final DeferredItem<Item> LEAD_INGOT = item("lead_ingot");
    public static final DeferredItem<Item> CASSITERITE_DUST = item("cassiterite_dust");
    public static final DeferredItem<Item> TIN_INGOT = item("tin_ingot");
    public static final DeferredItem<Item> KAOLIN_DUST = item("kaolin_dust");
    public static final DeferredItem<Item> REFRACTORY_CLAY = item("refractory_clay");
    public static final DeferredItem<Item> FIREBRICK = item("firebrick");
    public static final DeferredItem<Item> CERAMIC_BODY = item("ceramic_body");
    public static final DeferredItem<Item> PORCELAIN_BLANK = item("porcelain_blank");
    public static final DeferredItem<Item> CERAMIC_INSULATOR = item("ceramic_insulator");
    public static final DeferredItem<Item> MINERAL_WOOL = item("mineral_wool");
    public static final DeferredItem<Item> AMMONIUM_SULFATE = item("ammonium_sulfate");
    public static final DeferredItem<Item> SINGLE_SUPERPHOSPHATE = item("single_superphosphate");
    public static final DeferredItem<Item> UREA_FORMALDEHYDE_RESIN = item("urea_formaldehyde_resin");
    public static final DeferredItem<Item> CARBON_MONOXIDE_CELL = item("carbon_monoxide_cell");
    public static final DeferredItem<Item> SYNGAS_CELL = item("syngas_cell");
    public static final DeferredItem<Item> BORATE_MINERAL_DUST = item("borate_mineral_dust");
    public static final DeferredItem<Item> BORON_CARBIDE_PELLET = item("boron_carbide_pellet");
    public static final DeferredItem<Item> URANINITE_DUST = item("uraninite_dust");
    public static final DeferredItem<Item> YELLOWCAKE = item("yellowcake");
    public static final DeferredItem<Item> URANIUM_HEXAFLUORIDE_CELL = item("uranium_hexafluoride_cell");
    public static final DeferredItem<Item> LOW_ENRICHED_URANIUM = item("low_enriched_uranium");
    public static final DeferredItem<Item> DEPLETED_URANIUM = item("depleted_uranium");
    public static final DeferredItem<Item> URANIUM_DIOXIDE_POWDER = item("uranium_dioxide_powder");
    public static final DeferredItem<Item> NUCLEAR_FUEL_PELLET = item("nuclear_fuel_pellet");
    public static final DeferredItem<Item> ZIRCONIUM_ALLOY_TUBE = item("zirconium_alloy_tube");
    public static final DeferredItem<Item> CONTROL_ROD_ASSEMBLY = item("control_rod_assembly");
    public static final DeferredItem<Item> NUCLEAR_FUEL_ROD = item("nuclear_fuel_rod");
    public static final DeferredItem<Item> NUCLEAR_FUEL_ASSEMBLY = item("nuclear_fuel_assembly");
    public static final DeferredItem<Item> SPENT_FUEL_ASSEMBLY = item("spent_fuel_assembly");
    public static final DeferredItem<Item> DRY_STORAGE_CASK = item("dry_storage_cask");
    public static final DeferredItem<Item> NUCLEAR_HEAT_MODULE = item("nuclear_heat_module");
    public static final DeferredItem<Item> COPPER_BUSBAR = item("copper_busbar");
    public static final DeferredItem<Item> TRANSFORMER_CORE = item("transformer_core");
    public static final DeferredItem<Item> GRID_SWITCHGEAR = item("grid_switchgear");
    public static final DeferredItem<Item> GENERATOR_STATOR = item("generator_stator");
    public static final DeferredItem<Item> STEAM_TURBINE_ASSEMBLY = item("steam_turbine_assembly");
    public static final DeferredItem<Item> INDUSTRIAL_SENSOR = item("industrial_sensor");
    public static final DeferredItem<Item> PLC_CONTROLLER = item("plc_controller");
    public static final DeferredItem<Item> SERVO_MOTOR = item("servo_motor");
    public static final DeferredItem<Item> ACTUATOR_MODULE = item("actuator_module");
    public static final DeferredItem<Item> ROBOTIC_ARM = item("robotic_arm");
    public static final DeferredItem<Item> MACHINE_VISION_CAMERA = item("machine_vision_camera");
    public static final DeferredItem<Item> QUALITY_INSPECTION_MODULE = item("quality_inspection_module");
    public static final DeferredItem<Item> AUTOMATION_BUS = item("automation_bus");
    public static final DeferredItem<Item> CONVEYOR_DRIVE = item("conveyor_drive");
    public static final DeferredItem<Item> REDSTONE_IO_GATEWAY = item("redstone_io_gateway");
    public static final DeferredItem<FieldGeologyNotebookItem> FIELD_GEOLOGY_NOTEBOOK = notebookItem("field_geology_notebook");

    public EarthOnMinecraft(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(this::registerCreativeTab);
        NeoForge.EVENT_BUS.addListener(EarthOnMinecraftTooltips::addVanillaTooltips);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            EarthOnMinecraftClient.register(modBus);
        }
        LOGGER.info("[Earth on Minecraft] NeoForge 26.2 module loaded");
    }

    private static DeferredBlock<Block> oreBlock(String id, MapColor color, float strength) {
        DeferredBlock<Block> block = BLOCKS.registerSimpleBlock(id, () -> BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(strength, strength * 2.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE));
        DeferredItem<?> item = ITEMS.registerItem(id,
                props -> new GuidedBlockItem(block.get(), props, "tooltip.earth_on_minecraft.natural_source"),
                props -> props);
        TAB_ITEMS.add(item);
        return block;
    }

    private static DeferredBlock<SupportPartBlock> supportBlock(String id, MapColor color, float strength, String hintKey) {
        DeferredBlock<SupportPartBlock> block = BLOCKS.registerBlock(id, SupportPartBlock::new,
                () -> BlockBehaviour.Properties.of()
                        .mapColor(color)
                        .strength(strength, strength * 2.0F)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.METAL));
        DeferredItem<?> item = ITEMS.registerItem(id,
                props -> new GuidedBlockItem(block.get(), props, hintKey),
                props -> props);
        TAB_ITEMS.add(item);
        return block;
    }

    private static DeferredBlock<ControlPanelBlock> controlPanelBlock(String id, MapColor color, float strength, String hintKey) {
        DeferredBlock<ControlPanelBlock> block = BLOCKS.registerBlock(id, ControlPanelBlock::new,
                () -> BlockBehaviour.Properties.of()
                        .mapColor(color)
                        .strength(strength, strength * 2.0F)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.METAL));
        DeferredItem<?> item = ITEMS.registerItem(id,
                props -> new GuidedBlockItem(block.get(), props, hintKey),
                props -> props);
        TAB_ITEMS.add(item);
        return block;
    }

    private static DeferredBlock<StructureProjectionBlock> structureProjectionBlock(String id) {
        return BLOCKS.registerBlock(id, StructureProjectionBlock::new,
                () -> BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_LIGHT_BLUE)
                        .replaceable()
                        .noCollision()
                        .noOcclusion()
                        .instabreak()
                        .noLootTable()
                        .lightLevel(state -> 4)
                        .pushReaction(PushReaction.DESTROY)
                        .isSuffocating((state, level, pos) -> false)
                        .isViewBlocking((state, level, pos) -> false)
                        .sound(SoundType.GLASS));
    }

    private static DeferredBlock<MachineInterfaceBlock> machineInterfaceBlock(String id, MachineInterfaceBlock.InterfaceType interfaceType, String hintKey) {
        DeferredBlock<MachineInterfaceBlock> block = BLOCKS.registerBlock(id, props -> new MachineInterfaceBlock(props, interfaceType),
                () -> BlockBehaviour.Properties.of()
                        .mapColor(MapColor.METAL)
                        .strength(4.0F, 8.0F)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.METAL));
        DeferredItem<?> item = ITEMS.registerItem(id,
                props -> new GuidedBlockItem(block.get(), props, hintKey),
                props -> props);
        TAB_ITEMS.add(item);
        return block;
    }

    private static DeferredBlock<ProcessingMachineBlock> machineBlock(String id, ProcessingMachineBlock.Kind kind) {
        DeferredBlock<ProcessingMachineBlock> block = BLOCKS.registerBlock(id, props -> new ProcessingMachineBlock(props, kind),
                () -> BlockBehaviour.Properties.of()
                        .mapColor(MapColor.METAL)
                        .strength(4.0F, 8.0F)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.METAL));
        DeferredItem<?> item = ITEMS.registerItem(id,
                props -> new MachineBlockItem(block.get(), props, kind),
                props -> props);
        TAB_ITEMS.add(item);
        MACHINE_BLOCKS.add(block);
        return block;
    }

    private static DeferredBlock<EnergyGeneratorBlock> energyGeneratorBlock(String id) {
        DeferredBlock<EnergyGeneratorBlock> block = BLOCKS.registerBlock(id, EnergyGeneratorBlock::new,
                () -> BlockBehaviour.Properties.of()
                        .mapColor(MapColor.METAL)
                        .strength(4.0F, 8.0F)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.METAL));
        String hintKey = id.equals("steam_turbine_generator")
                ? "tooltip.earth_on_minecraft.energy.steam_turbine"
                : "tooltip.earth_on_minecraft.energy.generator";
        DeferredItem<?> item = ITEMS.registerItem(id,
                props -> new GuidedBlockItem(block.get(), props, hintKey),
                props -> props);
        TAB_ITEMS.add(item);
        return block;
    }

    private static DeferredBlock<PowerCableBlock> powerCableBlock(String id, int radius) {
        DeferredBlock<PowerCableBlock> block = BLOCKS.registerBlock(id, props -> new PowerCableBlock(props, radius),
                () -> BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_ORANGE)
                        .strength(1.5F, 3.0F)
                        .noOcclusion()
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.COPPER));
        DeferredItem<?> item = ITEMS.registerItem(id,
                props -> new GuidedBlockItem(block.get(), props, "tooltip.earth_on_minecraft.energy.cable"),
                props -> props);
        TAB_ITEMS.add(item);
        return block;
    }

    private static DeferredBlock<BatteryBoxBlock> batteryBoxBlock(String id) {
        DeferredBlock<BatteryBoxBlock> block = BLOCKS.registerBlock(id, BatteryBoxBlock::new,
                () -> BlockBehaviour.Properties.of()
                        .mapColor(MapColor.METAL)
                        .strength(4.0F, 8.0F)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.METAL));
        DeferredItem<?> item = ITEMS.registerItem(id,
                props -> new GuidedBlockItem(block.get(), props, "tooltip.earth_on_minecraft.energy.battery"),
                props -> props);
        TAB_ITEMS.add(item);
        return block;
    }

    private static DeferredBlock<ConveyorBeltBlock> conveyorBeltBlock(String id) {
        DeferredBlock<ConveyorBeltBlock> block = BLOCKS.registerBlock(id, ConveyorBeltBlock::new,
                () -> BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_GRAY)
                        .strength(2.0F, 4.0F)
                        .noOcclusion()
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.METAL));
        DeferredItem<?> item = ITEMS.registerItem(id,
                props -> new GuidedBlockItem(block.get(), props, "tooltip.earth_on_minecraft.automation.conveyor"),
                props -> props);
        TAB_ITEMS.add(item);
        return block;
    }

    private static DeferredItem<Item> item(String id) {
        DeferredItem<Item> item = ITEMS.registerItem(id, GuidedMaterialItem::new, props -> props);
        TAB_ITEMS.add(item);
        return item;
    }

    private static DeferredItem<FieldGeologyNotebookItem> notebookItem(String id) {
        DeferredItem<FieldGeologyNotebookItem> item = ITEMS.registerItem(id, FieldGeologyNotebookItem::new, props -> props.stacksTo(1));
        TAB_ITEMS.add(item);
        return item;
    }

    private void registerCreativeTab(RegisterEvent event) {
        event.register(Registries.CREATIVE_MODE_TAB, helper -> helper.register(id("earth_on_minecraft"),
                CreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.earth_on_minecraft"))
                        .icon(() -> new ItemStack(MAGNETITE_CHUNK.get()))
                        .displayItems((params, output) -> TAB_ITEMS.forEach(output::accept))
                        .build()));
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
