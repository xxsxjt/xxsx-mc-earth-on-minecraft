package com.xxsx.earthonline;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

@Mod(EarthOnline.MODID)
public class EarthOnline {
    public static final String MODID = "earth_online";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    private static final List<ItemLike> TAB_ITEMS = new ArrayList<>();

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
    public static final DeferredItem<FieldGeologyNotebookItem> FIELD_GEOLOGY_NOTEBOOK = notebookItem("field_geology_notebook");

    public EarthOnline(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(this::registerCreativeTab);
        LOGGER.info("[Earth Online] NeoForge 26.2 module loaded");
    }

    private static DeferredBlock<Block> oreBlock(String id, MapColor color, float strength) {
        DeferredBlock<Block> block = BLOCKS.registerSimpleBlock(id, () -> BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(strength, strength * 2.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE));
        DeferredItem<?> item = ITEMS.registerItem(id,
                props -> new GuidedBlockItem(block.get(), props, "自然来源：现实化矿床/矿脉，优先进入工业处理。"),
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
        event.register(Registries.CREATIVE_MODE_TAB, helper -> helper.register(id("earth_online"),
                CreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.earth_online"))
                        .icon(() -> new ItemStack(MAGNETITE_CHUNK.get()))
                        .displayItems((params, output) -> TAB_ITEMS.forEach(output::accept))
                        .build()));
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
