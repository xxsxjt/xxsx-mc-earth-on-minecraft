package com.xxsx.earthonminecraft;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.EnumSet;
import java.util.Set;

/**
 * Isolates the completed, end-to-end playable early-to-mid progression content from
 * unfinished placeholder items, unimplemented late-industrial machines, and stub equipment
 * so players only see and craft functional content in Creative Tabs, Machine UIs, and JEI.
 */
public final class PlayableContentFilter {
    private static final Set<ProcessingMachineBlock.Kind> ACTIVE_MACHINE_KINDS = EnumSet.of(
            // Ore comminution & beneficiation
            ProcessingMachineBlock.Kind.CRUSHER,
            ProcessingMachineBlock.Kind.BALL_MILL,
            ProcessingMachineBlock.Kind.SIEVE,
            ProcessingMachineBlock.Kind.MAGNETIC_SEPARATOR,
            // Pyrometallurgy, forging & forming
            ProcessingMachineBlock.Kind.PIT_KILN,
            ProcessingMachineBlock.Kind.BLOOMERY,
            ProcessingMachineBlock.Kind.ROASTER,
            ProcessingMachineBlock.Kind.REDUCTION_FURNACE,
            ProcessingMachineBlock.Kind.SMELTER,
            ProcessingMachineBlock.Kind.SHAFT_FURNACE,
            ProcessingMachineBlock.Kind.CASTING_TABLE,
            ProcessingMachineBlock.Kind.ANVIL,
            ProcessingMachineBlock.Kind.POWDER_PRESS,
            // Masonry kilns, jade & workshop crafts
            ProcessingMachineBlock.Kind.LIME_KILN,
            ProcessingMachineBlock.Kind.GLASS_KILN,
            ProcessingMachineBlock.Kind.GEM_CUTTER,
            ProcessingMachineBlock.Kind.CARPENTRY_BENCH,
            ProcessingMachineBlock.Kind.LOOM,
            ProcessingMachineBlock.Kind.TANNERY,
            // Agriculture, food & herbal medicine
            ProcessingMachineBlock.Kind.CAMPFIRE,
            ProcessingMachineBlock.Kind.STOVE,
            ProcessingMachineBlock.Kind.BREAD_OVEN,
            ProcessingMachineBlock.Kind.STEAMER,
            ProcessingMachineBlock.Kind.MILL,
            ProcessingMachineBlock.Kind.STONE_MILL,
            ProcessingMachineBlock.Kind.RICE_HULLER,
            ProcessingMachineBlock.Kind.NOODLE_PRESS,
            ProcessingMachineBlock.Kind.OIL_PRESS,
            ProcessingMachineBlock.Kind.FERMENTING_CROCK,
            ProcessingMachineBlock.Kind.HERB_PROCESSING_TABLE,
            ProcessingMachineBlock.Kind.TABLET_PRESS
    );

    private static final Set<String> PLAYABLE_ITEM_PATHS = buildPlayableItemPaths();

    private PlayableContentFilter() {
    }

    public static Set<ProcessingMachineBlock.Kind> activeMachineKinds() {
        return ACTIVE_MACHINE_KINDS;
    }

    public static boolean isActiveMachine(ProcessingMachineBlock.Kind kind) {
        return kind != null && ACTIVE_MACHINE_KINDS.contains(kind);
    }

    public static boolean isPlayableItem(ItemLike itemLike) {
        if (itemLike == null) {
            return false;
        }
        Item item = itemLike.asItem();
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) {
            return false;
        }
        if (!EarthOnMinecraft.MODID.equals(id.getNamespace())) {
            return true;
        }
        return isPlayableItemPath(id.getPath());
    }

    public static boolean isPlayableStack(ItemStack stack) {
        return !stack.isEmpty() && isPlayableItem(stack.getItem());
    }

    public static boolean isPlayableItemPath(String path) {
        return path != null && PLAYABLE_ITEM_PATHS.contains(path);
    }

    public static boolean isPlayableRecipe(ProcessingMachineBlock.Recipe recipe) {
        if (recipe == null || !isActiveMachine(recipe.kind())) {
            return false;
        }
        String src = recipe.sourceId() == null ? "" : recipe.sourceId();
        if (src.startsWith("crop/") || src.startsWith("herb/")) {
            return false;
        }
        for (ProcessingMachineBlock.Input in : recipe.inputs()) {
            ItemLike item = in.item().get();
            if (!isPlayableItem(item)) {
                return false;
            }
        }
        for (ProcessingMachineBlock.Output out : recipe.outputs()) {
            ItemLike item = out.item().get();
            if (!isPlayableItem(item)) {
                return false;
            }
        }
        if (recipe.hasCatalyst()) {
            ItemLike cat = recipe.catalyst().get();
            if (!isPlayableItem(cat)) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> buildPlayableItemPaths() {
        Set<String> set = new java.util.HashSet<>();

        // 1. Active processing machines (31 blockIds)
        for (ProcessingMachineBlock.Kind kind : ACTIVE_MACHINE_KINDS) {
            set.add(kind.blockId());
        }

        // 2. Multiblock structure parts, manual workstations & basic power grid
        set.addAll(Set.of(
                "air_inlet", "chimney", "clay_brick_block", "firebrick_block",
                "oven_mouth", "packed_earth", "tuyere",
                "industrial_machine_casing", "steel_process_pipe", "control_panel",
                "machine_input_interface", "machine_output_interface",
                "ironworking_anvil", "settlement_board",
                "combustion_generator", "battery_box",
                "thin_copper_power_cable", "copper_power_cable",
                "copper_wire", "simple_battery_cell"
        ));

        // 3. Natural ore blocks (22 non-nuclear ore deposits)
        set.addAll(Set.of(
                "poor_magnetite_ore", "magnetite_ore", "rich_magnetite_ore",
                "chalcopyrite_ore", "auriferous_quartz_vein",
                "bituminous_coal_seam", "anthracite_coal_seam",
                "kimberlite", "diamondiferous_kimberlite",
                "lapis_lazuli_ore", "emerald_beryl_vein",
                "redstone_mineral_ore", "cinnabar_vein",
                "bauxite_laterite_deposit", "phosphorite_bed",
                "evaporite_salt_bed", "cassiterite_placer_deposit",
                "rare_earth_carbonatite",
                "borate_deposit", "chromite_ore", "galena_ore",
                "manganite_ore", "sphalerite_ore"
        ));

        // 4. Prospector & kinetic hand tools
        set.addAll(Set.of(
                "survey_stake", "geology_hammer", "hand_lens", "ore_scanner",
                "field_geology_notebook", "sample_bag", "hand_crank", "wrench"
        ));

        // 5. Mineral chunks, dusts & concentrates
        set.addAll(Set.of(
                "magnetite_chunk", "crushed_magnetite", "chalcopyrite_chunk",
                "auriferous_quartz_chunk", "kimberlite_chunk", "cinnabar_chunk", "coal_chunk",
                "magnetite_dust", "chalcopyrite_dust", "quartz_dust", "feldspar_dust",
                "mica_dust", "tailings_dust", "coal_dust", "gold_dust", "diamond_grit",
                "cinnabar_dust", "silica_dust", "calcite_dust", "lime_dust",
                "hematite_dust", "pyrite_dust", "sulfur_dust", "mafic_silicate_dust",
                "aluminosilicate_dust", "bauxite_dust", "phosphate_rock_dust",
                "salt_dust", "gypsum_dust", "clay_dust", "kaolin_dust",
                "graphite_dust", "manganese_oxide_dust", "chromite_dust", "magnesium_dust",
                "monazite_sand", "bastnasite_dust", "rare_earth_tailings",
                "sphalerite_dust", "galena_dust", "cassiterite_dust", "borate_mineral_dust",
                "iron_concentrate", "copper_concentrate", "gold_concentrate",
                "roasted_copper_concentrate", "roasted_copper", "redstone_concentrate",
                "lapis_concentrate", "beryl_concentrate", "mercury_droplet", "gold_sponge",
                "brine_crystal", "lithium_salt", "mixed_rare_earth_oxide", "neodymium_salt",
                "ndfeb_magnet", "zinc_oxide", "potassium_chloride", "soda_ash"
        ));

        // 6. Pyrometallurgy ingots, blooms, alloys & masonry/textile materials
        set.addAll(Set.of(
                "sponge_iron", "wrought_iron", "steel_bloom", "steel", "high_carbon_steel",
                "cast_iron", "pig_iron", "bronze", "brass",
                "tin_ingot", "zinc_ingot", "lead_ingot", "aluminum_ingot",
                "blister_copper", "anode_copper", "cathode_copper", "refined_copper",
                "ferrosilicon", "ferromanganese", "ferrochrome", "metallurgical_silicon",
                "coke", "charcoal", "slag",
                "clay_brick", "firebrick", "refractory_clay",
                "limestone", "lime", "slaked_lime", "gypsum",
                "glass_batch", "optical_glass",
                "wood_chips", "cellulose_fiber", "flax_thread", "linen",
                "cotton_thread", "cotton_cloth", "rawhide", "leather",
                "activated_carbon", "beeswax", "wax"
        ));

        // 7. Jade & gemstone cutting chain
        set.addAll(Set.of(
                "gem_rough", "gem_cut", "gem_dust", "gem_carving_tool",
                "grinding_wheel_coarse", "grinding_wheel_fine", "grinding_wheel_polish"
        ));

        // 8. 15 real forged weapons & tools + 10 functional medicines
        set.addAll(EarthMedicalAndWeaponSupport.weapons().keySet());
        set.addAll(EarthMedicalAndWeaponSupport.medicines().keySet());

        // 9. Core herbs & prepared decoction pieces (used in medicine & xuanhuan alchemy)
        set.addAll(Set.of(
                "ginseng", "astragalus", "coptis", "forsythia", "honeysuckle",
                "licorice", "artemisia_annua", "angelica", "salvia_miltiorrhiza",
                "safflower", "jujube_seed", "biota_seed", "corydalis",
                "rehmannia", "hawthorn_fruit", "oyster_shell", "eucommia_bark",
                "calcined_oyster_shell", "charred_hawthorn", "ginseng_decoction",
                "prepared_angelica", "prepared_corydalis", "prepared_eucommia",
                "prepared_licorice", "prepared_rehmannia"
        ));

        // 10. Core agriculture, milling, baking & fermented food/drinks
        set.addAll(Set.of(
                "wheat", "rice_paddy", "rice", "glutinous_rice", "maize", "barley", "rye", "oat", "sorghum", "millet",
                "soybean", "mung_bean", "black_bean", "adzuki_bean", "peanut", "potato", "sweet_potato", "beet",
                "cabbage", "carrot", "onion", "garlic", "ginger", "chili", "bell_pepper", "tomato", "spinach",
                "flour", "rice_flour", "corn_flour", "rye_flour", "starch", "dough", "chaff", "yeast",
                "salt", "sea_salt", "sugar", "brown_sugar", "syrup", "honey", "water_bottle", "softened_water",
                "cooking_oil", "sesame_oil", "lard", "spice", "vinegar", "rice_vinegar", "soy_sauce", "bean_paste", "miso",
                "milk", "cream", "butter", "cheese", "yogurt",
                "bread", "rye_bread", "baguette", "steamed_bun", "baozi", "dumpling", "noodles", "rice_noodles", "ramen",
                "cooked_rice", "porridge", "tofu", "tofu_pudding", "dried_tofu", "soy_milk",
                "raw_beef", "raw_pork", "raw_mutton", "raw_chicken", "raw_rabbit", "raw_fish",
                "cooked_beef", "cooked_pork", "cooked_mutton", "cooked_chicken", "cooked_rabbit", "cooked_fish",
                "bacon", "ham", "sausage", "dried_fish", "egg", "fried_egg", "bone_broth", "soup", "stew", "stir_fry",
                "rice_wine", "sake", "huangjiu", "baijiu", "beer", "wheat_beer", "cider", "mead", "wine"
        ));

        return Set.copyOf(set);
    }
}
