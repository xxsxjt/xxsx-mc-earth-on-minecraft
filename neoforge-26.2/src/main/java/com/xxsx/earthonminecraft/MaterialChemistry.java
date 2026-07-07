package com.xxsx.earthonminecraft;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.Map;

import static java.util.Map.entry;

final class MaterialChemistry {
    private static final Map<String, Info> INFO = Map.ofEntries(
            entry("poor_magnetite_ore", info("Fe3O4 + SiO2 + Fe2O3", "tooltip.earth_on_minecraft.chem.detail.oxide_ore")),
            entry("magnetite_ore", info("Fe3O4", "tooltip.earth_on_minecraft.chem.detail.oxide_ore")),
            entry("rich_magnetite_ore", info("Fe3O4-rich", "tooltip.earth_on_minecraft.chem.detail.oxide_ore")),
            entry("chalcopyrite_ore", info("CuFeS2 + FeS2", "tooltip.earth_on_minecraft.chem.detail.sulfide_ore")),
            entry("auriferous_quartz_vein", info("SiO2 + Au", "tooltip.earth_on_minecraft.chem.detail.vein")),
            entry("bituminous_coal_seam", info("C + volatiles + shale", "tooltip.earth_on_minecraft.chem.detail.coal")),
            entry("anthracite_coal_seam", info("high-C coal + shale", "tooltip.earth_on_minecraft.chem.detail.coal")),
            entry("kimberlite", info("olivine + serpentine + carbonate + mantle fragments", "tooltip.earth_on_minecraft.chem.detail.mixed_rock")),
            entry("diamondiferous_kimberlite", info("kimberlite + C(diamond)", "tooltip.earth_on_minecraft.chem.detail.mixed_rock")),
            entry("lapis_lazuli_ore", info("lazurite + calcite + pyrite", "tooltip.earth_on_minecraft.chem.detail.silicate_mix")),
            entry("emerald_beryl_vein", info("Be3Al2Si6O18 + Cr/V trace", "tooltip.earth_on_minecraft.chem.detail.silicate_mix")),
            entry("redstone_mineral_ore", info("Fe2O3 + HgS-like red minerals + MC redstone phase", "tooltip.earth_on_minecraft.chem.detail.fantasy_mix")),
            entry("cinnabar_vein", info("HgS", "tooltip.earth_on_minecraft.chem.detail.sulfide_ore")),
            entry("bauxite_laterite_deposit", info("AlOOH/Al(OH)3 + Fe2O3 + kaolinite", "tooltip.earth_on_minecraft.chem.detail.oxide_ore")),
            entry("phosphorite_bed", info("Ca5(PO4)3(F,Cl,OH) + CaCO3 + SiO2", "tooltip.earth_on_minecraft.chem.detail.fertilizer")),
            entry("evaporite_salt_bed", info("NaCl + KCl + CaSO4.2H2O + Mg/Ca salts", "tooltip.earth_on_minecraft.chem.detail.salt_mix")),
            entry("cassiterite_placer_deposit", info("SnO2 + SiO2 + heavy minerals", "tooltip.earth_on_minecraft.chem.detail.oxide_ore")),
            entry("rare_earth_carbonatite", info("(Ce,La)CO3F + (Ce,La,Nd,Th)PO4 + calcite/apatite", "tooltip.earth_on_minecraft.chem.detail.rare_earth")),
            entry("magnetite_chunk", info("Fe3O4", "tooltip.earth_on_minecraft.chem.detail.oxide_ore")),
            entry("chalcopyrite_chunk", info("CuFeS2", "tooltip.earth_on_minecraft.chem.detail.sulfide_ore")),
            entry("auriferous_quartz_chunk", info("SiO2 + Au", "tooltip.earth_on_minecraft.chem.detail.vein")),
            entry("kimberlite_chunk", info("silicate-carbonate volcanic breccia", "tooltip.earth_on_minecraft.chem.detail.mixed_rock")),
            entry("magnetite_dust", info("Fe3O4", "tooltip.earth_on_minecraft.chem.detail.oxide")),
            entry("chalcopyrite_dust", info("CuFeS2", "tooltip.earth_on_minecraft.chem.detail.sulfide")),
            entry("quartz_dust", info("SiO2", "tooltip.earth_on_minecraft.chem.detail.silicate")),
            entry("feldspar_dust", info("KAlSi3O8 + NaAlSi3O8 + CaAl2Si2O8", "tooltip.earth_on_minecraft.chem.detail.silicate_mix")),
            entry("mica_dust", info("K-Al-Mg-Fe sheet silicates", "tooltip.earth_on_minecraft.chem.detail.silicate_mix")),
            entry("tailings_dust", info("SiO2 + silicates + residual oxides/sulfides", "tooltip.earth_on_minecraft.chem.detail.tailings")),
            entry("coal_dust", info("C + volatile organics + ash", "tooltip.earth_on_minecraft.chem.detail.coal")),
            entry("gold_dust", info("Au", "tooltip.earth_on_minecraft.chem.detail.native_element")),
            entry("diamond_grit", info("C(diamond)", "tooltip.earth_on_minecraft.chem.detail.native_element")),
            entry("cinnabar_chunk", info("HgS", "tooltip.earth_on_minecraft.chem.detail.sulfide")),
            entry("cinnabar_dust", info("HgS", "tooltip.earth_on_minecraft.chem.detail.sulfide")),
            entry("silica_dust", info("SiO2", "tooltip.earth_on_minecraft.chem.detail.oxide")),
            entry("calcite_dust", info("CaCO3", "tooltip.earth_on_minecraft.chem.detail.carbonate")),
            entry("lime_dust", info("CaO", "tooltip.earth_on_minecraft.chem.detail.base")),
            entry("hematite_dust", info("Fe2O3", "tooltip.earth_on_minecraft.chem.detail.oxide")),
            entry("pyrite_dust", info("FeS2", "tooltip.earth_on_minecraft.chem.detail.sulfide")),
            entry("sulfur_dust", info("S", "tooltip.earth_on_minecraft.chem.detail.native_element")),
            entry("mafic_silicate_dust", info("olivine + pyroxene + amphibole", "tooltip.earth_on_minecraft.chem.detail.silicate_mix")),
            entry("aluminosilicate_dust", info("Al2O3 + SiO2 framework", "tooltip.earth_on_minecraft.chem.detail.silicate_mix")),
            entry("iron_concentrate", info("Fe3O4/Fe2O3-rich concentrate", "tooltip.earth_on_minecraft.chem.detail.concentrate")),
            entry("copper_concentrate", info("CuFeS2-rich concentrate", "tooltip.earth_on_minecraft.chem.detail.concentrate")),
            entry("gold_concentrate", info("Au + SiO2 heavy fraction", "tooltip.earth_on_minecraft.chem.detail.concentrate")),
            entry("roasted_copper_concentrate", info("Cu2S/CuO + Fe oxides", "tooltip.earth_on_minecraft.chem.detail.roasted")),
            entry("redstone_concentrate", info("redstone phase + iron oxides", "tooltip.earth_on_minecraft.chem.detail.fantasy_mix")),
            entry("lapis_concentrate", info("lazurite-rich concentrate", "tooltip.earth_on_minecraft.chem.detail.concentrate")),
            entry("beryl_concentrate", info("Be3Al2Si6O18-rich concentrate", "tooltip.earth_on_minecraft.chem.detail.concentrate")),
            entry("mercury_droplet", info("Hg", "tooltip.earth_on_minecraft.chem.detail.native_element")),
            entry("slag", info("CaO-SiO2-Al2O3-FeO glass", "tooltip.earth_on_minecraft.chem.detail.slag")),
            entry("salt_dust", info("NaCl", "tooltip.earth_on_minecraft.chem.detail.salt")),
            entry("brine_crystal", info("NaCl + KCl + Mg/Ca salts", "tooltip.earth_on_minecraft.chem.detail.salt_mix")),
            entry("sodium_hydroxide", info("NaOH", "tooltip.earth_on_minecraft.chem.detail.base")),
            entry("chlorine_gas_cell", info("Cl2", "tooltip.earth_on_minecraft.chem.detail.gas")),
            entry("hydrogen_gas_cell", info("H2", "tooltip.earth_on_minecraft.chem.detail.gas")),
            entry("hydrochloric_acid", info("HCl(aq)", "tooltip.earth_on_minecraft.chem.detail.acid")),
            entry("sulfuric_acid", info("H2SO4", "tooltip.earth_on_minecraft.chem.detail.acid")),
            entry("nitric_acid", info("HNO3", "tooltip.earth_on_minecraft.chem.detail.acid")),
            entry("ammonia", info("NH3", "tooltip.earth_on_minecraft.chem.detail.base")),
            entry("ammonium_nitrate", info("NH4NO3", "tooltip.earth_on_minecraft.chem.detail.fertilizer")),
            entry("phosphate_rock_dust", info("Ca5(PO4)3(F,Cl,OH)", "tooltip.earth_on_minecraft.chem.detail.fertilizer")),
            entry("phosphoric_acid", info("H3PO4", "tooltip.earth_on_minecraft.chem.detail.acid")),
            entry("fertilizer_blend", info("N-P-K salts + filler", "tooltip.earth_on_minecraft.chem.detail.fertilizer_mix")),
            entry("soda_ash", info("Na2CO3", "tooltip.earth_on_minecraft.chem.detail.salt")),
            entry("sodium_bicarbonate", info("NaHCO3", "tooltip.earth_on_minecraft.chem.detail.salt")),
            entry("gypsum_dust", info("CaSO4.2H2O", "tooltip.earth_on_minecraft.chem.detail.sulfate")),
            entry("cement_raw_meal", info("CaCO3 + SiO2 + Al2O3 + Fe2O3", "tooltip.earth_on_minecraft.chem.detail.cement")),
            entry("cement_clinker", info("C3S + C2S + C3A + C4AF", "tooltip.earth_on_minecraft.chem.detail.cement")),
            entry("cement_powder", info("clinker + gypsum", "tooltip.earth_on_minecraft.chem.detail.cement")),
            entry("clay_dust", info("Al2Si2O5(OH)4 + silicates", "tooltip.earth_on_minecraft.chem.detail.clay")),
            entry("bauxite_dust", info("AlOOH/Al(OH)3 + Fe oxides", "tooltip.earth_on_minecraft.chem.detail.oxide_ore")),
            entry("aluminum_hydroxide", info("Al(OH)3", "tooltip.earth_on_minecraft.chem.detail.base")),
            entry("alumina", info("Al2O3", "tooltip.earth_on_minecraft.chem.detail.oxide")),
            entry("glass_batch", info("SiO2 + Na2CO3 + CaCO3", "tooltip.earth_on_minecraft.chem.detail.glass")),
            entry("coke", info("C-rich coke + ash", "tooltip.earth_on_minecraft.chem.detail.coal")),
            entry("coal_tar", info("aromatics + phenols + pitch", "tooltip.earth_on_minecraft.chem.detail.petroleum_mix")),
            entry("coal_gas_cell", info("H2 + CO + CH4 + CO2", "tooltip.earth_on_minecraft.chem.detail.gas_mix")),
            entry("ethylene", info("C2H4", "tooltip.earth_on_minecraft.chem.detail.monomer")),
            entry("polymer_resin", info("mixed polymer precursor", "tooltip.earth_on_minecraft.chem.detail.polymer")),
            entry("calcium_chloride", info("CaCl2", "tooltip.earth_on_minecraft.chem.detail.salt")),
            entry("slaked_lime", info("Ca(OH)2", "tooltip.earth_on_minecraft.chem.detail.base")),
            entry("nitrogen_gas_cell", info("N2", "tooltip.earth_on_minecraft.chem.detail.gas")),
            entry("oxygen_gas_cell", info("O2", "tooltip.earth_on_minecraft.chem.detail.gas")),
            entry("carbon_dioxide_cell", info("CO2", "tooltip.earth_on_minecraft.chem.detail.gas")),
            entry("sulfur_dioxide_cell", info("SO2", "tooltip.earth_on_minecraft.chem.detail.gas")),
            entry("methanol", info("CH3OH", "tooltip.earth_on_minecraft.chem.detail.solvent")),
            entry("formaldehyde", info("HCHO", "tooltip.earth_on_minecraft.chem.detail.reactive_organic")),
            entry("urea", info("CO(NH2)2", "tooltip.earth_on_minecraft.chem.detail.fertilizer")),
            entry("potassium_chloride", info("KCl", "tooltip.earth_on_minecraft.chem.detail.salt")),
            entry("potash", info("K2O equivalent", "tooltip.earth_on_minecraft.chem.detail.fertilizer")),
            entry("potassium_nitrate", info("KNO3", "tooltip.earth_on_minecraft.chem.detail.fertilizer")),
            entry("sodium_sulfate", info("Na2SO4", "tooltip.earth_on_minecraft.chem.detail.sulfate")),
            entry("benzene", info("C6H6", "tooltip.earth_on_minecraft.chem.detail.solvent")),
            entry("propylene", info("C3H6", "tooltip.earth_on_minecraft.chem.detail.monomer")),
            entry("vinyl_chloride", info("C2H3Cl", "tooltip.earth_on_minecraft.chem.detail.monomer")),
            entry("polyethylene_resin", info("(C2H4)n", "tooltip.earth_on_minecraft.chem.detail.polymer")),
            entry("polypropylene_resin", info("(C3H6)n", "tooltip.earth_on_minecraft.chem.detail.polymer")),
            entry("pvc_resin", info("(C2H3Cl)n", "tooltip.earth_on_minecraft.chem.detail.polymer")),
            entry("steel_bloom", info("Fe + C", "tooltip.earth_on_minecraft.chem.detail.alloy")),
            entry("ferrosilicon", info("Fe + Si", "tooltip.earth_on_minecraft.chem.detail.alloy")),
            entry("aluminum_ingot", info("Al", "tooltip.earth_on_minecraft.chem.detail.metal")),
            entry("crude_oil_sample", info("C5-C40 hydrocarbons + sulfur/nitrogen traces", "tooltip.earth_on_minecraft.chem.detail.petroleum_mix")),
            entry("natural_gas_cell", info("CH4-rich gas", "tooltip.earth_on_minecraft.chem.detail.gas_mix")),
            entry("naphtha", info("C5-C10 hydrocarbons", "tooltip.earth_on_minecraft.chem.detail.petroleum_mix")),
            entry("kerosene_fraction", info("C10-C16 hydrocarbons", "tooltip.earth_on_minecraft.chem.detail.petroleum_mix")),
            entry("diesel_fraction", info("C12-C20 hydrocarbons", "tooltip.earth_on_minecraft.chem.detail.petroleum_mix")),
            entry("lubricating_oil", info("heavy hydrocarbons", "tooltip.earth_on_minecraft.chem.detail.petroleum_mix")),
            entry("asphalt", info("asphaltenes + resins + mineral filler", "tooltip.earth_on_minecraft.chem.detail.petroleum_mix")),
            entry("petroleum_coke", info("C-rich coke", "tooltip.earth_on_minecraft.chem.detail.coal")),
            entry("wood_chips", info("cellulose + hemicellulose + lignin", "tooltip.earth_on_minecraft.chem.detail.biomass")),
            entry("cellulose_pulp", info("(C6H10O5)n + lignin traces", "tooltip.earth_on_minecraft.chem.detail.biomass")),
            entry("bleached_pulp", info("(C6H10O5)n", "tooltip.earth_on_minecraft.chem.detail.biomass")),
            entry("cellulose_fiber", info("(C6H10O5)n", "tooltip.earth_on_minecraft.chem.detail.biomass")),
            entry("titanium_dioxide", info("TiO2", "tooltip.earth_on_minecraft.chem.detail.oxide")),
            entry("iron_oxide_pigment", info("Fe2O3/FeOOH", "tooltip.earth_on_minecraft.chem.detail.pigment")),
            entry("carbon_black", info("C", "tooltip.earth_on_minecraft.chem.detail.pigment")),
            entry("paint_base", info("resin + pigment + solvent", "tooltip.earth_on_minecraft.chem.detail.polymer_mix")),
            entry("styrene", info("C8H8", "tooltip.earth_on_minecraft.chem.detail.monomer")),
            entry("polystyrene_resin", info("(C8H8)n", "tooltip.earth_on_minecraft.chem.detail.polymer")),
            entry("ethylene_glycol", info("C2H6O2", "tooltip.earth_on_minecraft.chem.detail.solvent")),
            entry("terephthalic_acid", info("C8H6O4", "tooltip.earth_on_minecraft.chem.detail.reactive_organic")),
            entry("pet_resin", info("(C10H8O4)n", "tooltip.earth_on_minecraft.chem.detail.polymer")),
            entry("synthetic_rubber", info("polybutadiene/SBR rubber", "tooltip.earth_on_minecraft.chem.detail.rubber")),
            entry("caprolactam", info("C6H11NO", "tooltip.earth_on_minecraft.chem.detail.monomer")),
            entry("nylon_fiber", info("(C6H11NO)n", "tooltip.earth_on_minecraft.chem.detail.polymer")),
            entry("graphite_dust", info("C(graphite)", "tooltip.earth_on_minecraft.chem.detail.native_element")),
            entry("activated_carbon", info("porous C", "tooltip.earth_on_minecraft.chem.detail.carbon_material")),
            entry("battery_carbon", info("conductive C", "tooltip.earth_on_minecraft.chem.detail.carbon_material")),
            entry("manganese_oxide_dust", info("MnO2", "tooltip.earth_on_minecraft.chem.detail.oxide")),
            entry("nickel_precursor", info("Ni(OH)2/Ni salts", "tooltip.earth_on_minecraft.chem.detail.battery")),
            entry("lithium_salt", info("Li2CO3/Li salts", "tooltip.earth_on_minecraft.chem.detail.battery")),
            entry("electrolyte", info("Li/K/Na salt + solvent", "tooltip.earth_on_minecraft.chem.detail.battery")),
            entry("electrode_sheet", info("carbon + metal oxide + binder", "tooltip.earth_on_minecraft.chem.detail.battery")),
            entry("simple_battery_cell", info("anode + cathode + electrolyte", "tooltip.earth_on_minecraft.chem.detail.battery")),
            entry("hard_water_sample", info("H2O + Ca2+/Mg2+", "tooltip.earth_on_minecraft.chem.detail.water")),
            entry("softened_water", info("H2O with reduced Ca/Mg", "tooltip.earth_on_minecraft.chem.detail.water")),
            entry("humus_sample", info("humic organics + clay + microbes", "tooltip.earth_on_minecraft.chem.detail.soil")),
            entry("sandy_loam_sample", info("sand + silt + clay + humus", "tooltip.earth_on_minecraft.chem.detail.soil")),
            entry("alluvial_loam_sample", info("clay + silt + carbonates + humus", "tooltip.earth_on_minecraft.chem.detail.soil")),
            entry("saline_soil_sample", info("soil minerals + NaCl/Na2CO3 salts", "tooltip.earth_on_minecraft.chem.detail.soil")),
            entry("soil_mineral_mix", info("SiO2 + Al silicates + carbonates", "tooltip.earth_on_minecraft.chem.detail.soil")),
            entry("irrigation_mineral_deposit", info("CaCO3 + CaSO4 + NaCl", "tooltip.earth_on_minecraft.chem.detail.salt_mix")),
            entry("sludge_cake", info("water + hydroxides + organics + salts", "tooltip.earth_on_minecraft.chem.detail.tailings")),
            entry("neutral_salt", info("NaCl/Na2SO4/KCl mixed salts", "tooltip.earth_on_minecraft.chem.detail.salt_mix")),
            entry("activated_carbon_filter", info("porous C + binder + cartridge frame", "tooltip.earth_on_minecraft.chem.detail.carbon_material")),
            entry("lime_treatment_residue", info("CaCO3 + Ca(OH)2 + captured ions", "tooltip.earth_on_minecraft.chem.detail.tailings")),
            entry("stabilized_tailings", info("silicates + lime/cement binder + trace metals", "tooltip.earth_on_minecraft.chem.detail.tailings")),
            entry("natural_latex", info("polyisoprene emulsion", "tooltip.earth_on_minecraft.chem.detail.rubber")),
            entry("raw_rubber", info("(C5H8)n", "tooltip.earth_on_minecraft.chem.detail.rubber")),
            entry("vulcanized_rubber", info("cross-linked polyisoprene + S", "tooltip.earth_on_minecraft.chem.detail.rubber")),
            entry("rubber_compound", info("rubber + carbon black + sulfur + filler", "tooltip.earth_on_minecraft.chem.detail.rubber")),
            entry("rubber_gasket", info("vulcanized rubber composite", "tooltip.earth_on_minecraft.chem.detail.rubber")),
            entry("ethanol", info("C2H5OH", "tooltip.earth_on_minecraft.chem.detail.solvent")),
            entry("acetic_acid", info("CH3COOH", "tooltip.earth_on_minecraft.chem.detail.acid")),
            entry("acetone", info("C3H6O", "tooltip.earth_on_minecraft.chem.detail.solvent")),
            entry("phenol", info("C6H5OH", "tooltip.earth_on_minecraft.chem.detail.reactive_organic")),
            entry("phenolic_resin", info("phenol-formaldehyde polymer", "tooltip.earth_on_minecraft.chem.detail.polymer")),
            entry("epoxy_resin", info("epoxy oligomer + curing chemistry", "tooltip.earth_on_minecraft.chem.detail.polymer")),
            entry("industrial_solvent", info("oxygenated/aromatic solvent blend", "tooltip.earth_on_minecraft.chem.detail.solvent")),
            entry("butadiene", info("C4H6", "tooltip.earth_on_minecraft.chem.detail.monomer")),
            entry("acrylonitrile", info("C3H3N", "tooltip.earth_on_minecraft.chem.detail.monomer")),
            entry("abs_resin", info("(C8H8)x(C4H6)y(C3H3N)z", "tooltip.earth_on_minecraft.chem.detail.polymer")),
            entry("acrylic_monomer", info("acrylate/methacrylate monomer", "tooltip.earth_on_minecraft.chem.detail.monomer")),
            entry("acrylic_resin", info("(C5H8O2)n acrylic polymer", "tooltip.earth_on_minecraft.chem.detail.polymer")),
            entry("polyol", info("HO-R-OH polyether/polyester", "tooltip.earth_on_minecraft.chem.detail.reactive_organic")),
            entry("isocyanate", info("R-NCO", "tooltip.earth_on_minecraft.chem.detail.reactive_organic")),
            entry("polyurethane_foam", info("urethane polymer + blowing gas cells", "tooltip.earth_on_minecraft.chem.detail.polymer")),
            entry("stainless_steel_bloom", info("Fe + Cr + Ni + C", "tooltip.earth_on_minecraft.chem.detail.alloy")),
            entry("aluminum_alloy_billet", info("Al + Mg/Si/Cu", "tooltip.earth_on_minecraft.chem.detail.alloy")),
            entry("chromite_dust", info("FeCr2O4", "tooltip.earth_on_minecraft.chem.detail.oxide_ore")),
            entry("ferrochrome", info("Fe + Cr + C", "tooltip.earth_on_minecraft.chem.detail.alloy")),
            entry("ferromanganese", info("Fe + Mn + C", "tooltip.earth_on_minecraft.chem.detail.alloy")),
            entry("magnesium_dust", info("Mg", "tooltip.earth_on_minecraft.chem.detail.metal")),
            entry("titanium_slag", info("TiO2-rich slag + FeO/SiO2", "tooltip.earth_on_minecraft.chem.detail.slag")),
            entry("titanium_tetrachloride", info("TiCl4", "tooltip.earth_on_minecraft.chem.detail.semiconductor")),
            entry("titanium_sponge", info("Ti", "tooltip.earth_on_minecraft.chem.detail.metal")),
            entry("metallurgical_silicon", info("Si + Fe/Al/Ca traces", "tooltip.earth_on_minecraft.chem.detail.semiconductor")),
            entry("chlorosilane", info("SiHCl3/SiCl4", "tooltip.earth_on_minecraft.chem.detail.semiconductor")),
            entry("high_purity_silicon", info("Si 99.9999%+", "tooltip.earth_on_minecraft.chem.detail.semiconductor")),
            entry("polysilicon", info("polycrystalline Si", "tooltip.earth_on_minecraft.chem.detail.semiconductor")),
            entry("silicon_wafer", info("single-crystal Si + dopant traces", "tooltip.earth_on_minecraft.chem.detail.semiconductor")),
            entry("dopant_dust", info("B/P/As trace dopants", "tooltip.earth_on_minecraft.chem.detail.semiconductor")),
            entry("photoresist_precursor", info("polymer resin + photoactive compound", "tooltip.earth_on_minecraft.chem.detail.semiconductor")),
            entry("monazite_sand", info("(Ce,La,Nd,Th)PO4 + sand", "tooltip.earth_on_minecraft.chem.detail.rare_earth")),
            entry("bastnasite_dust", info("(Ce,La)CO3F", "tooltip.earth_on_minecraft.chem.detail.rare_earth")),
            entry("mixed_rare_earth_oxide", info("RE2O3 mixed oxides", "tooltip.earth_on_minecraft.chem.detail.rare_earth")),
            entry("neodymium_salt", info("NdCl3/Nd(NO3)3", "tooltip.earth_on_minecraft.chem.detail.rare_earth")),
            entry("ndfeb_magnet", info("Nd2Fe14B + Dy/Pr traces", "tooltip.earth_on_minecraft.chem.detail.electronics")),
            entry("rare_earth_tailings", info("silicates + residual RE/phosphates/fluorides", "tooltip.earth_on_minecraft.chem.detail.tailings")),
            entry("iron_catalyst", info("Fe/Fe3O4 + K/Al promoters", "tooltip.earth_on_minecraft.chem.detail.catalyst")),
            entry("vanadium_catalyst", info("V2O5 on silica support", "tooltip.earth_on_minecraft.chem.detail.catalyst")),
            entry("nickel_catalyst", info("Ni on alumina/silica support", "tooltip.earth_on_minecraft.chem.detail.catalyst")),
            entry("platinum_group_catalyst", info("Pt/Pd/Rh on ceramic support", "tooltip.earth_on_minecraft.chem.detail.catalyst")),
            entry("sodium_hypochlorite", info("NaOCl(aq)", "tooltip.earth_on_minecraft.chem.detail.oxidizer")),
            entry("bleaching_powder", info("Ca(OCl)Cl + CaCl2/Ca(OH)2", "tooltip.earth_on_minecraft.chem.detail.oxidizer")),
            entry("hydrogen_peroxide", info("H2O2", "tooltip.earth_on_minecraft.chem.detail.oxidizer")),
            entry("soap_base", info("sodium/potassium fatty acid salts", "tooltip.earth_on_minecraft.chem.detail.cleaning")),
            entry("glycerol", info("C3H8O3", "tooltip.earth_on_minecraft.chem.detail.solvent")),
            entry("surfactant", info("R-SO3Na/R-OSO3Na surfactants", "tooltip.earth_on_minecraft.chem.detail.cleaning")),
            entry("detergent_powder", info("surfactant + builder + filler", "tooltip.earth_on_minecraft.chem.detail.cleaning")),
            entry("copper_wire", info("Cu", "tooltip.earth_on_minecraft.chem.detail.metal")),
            entry("thin_copper_power_cable", info("Cu conductor + glass insulation + redstone control", "tooltip.earth_on_minecraft.chem.detail.cable")),
            entry("copper_power_cable", info("Cu conductor + glass insulation + redstone control", "tooltip.earth_on_minecraft.chem.detail.cable")),
            entry("heavy_copper_power_cable", info("Cu conductor bundle + glass insulation + redstone control", "tooltip.earth_on_minecraft.chem.detail.cable")),
            entry("combustion_generator", info("steel casing + Cu coil + ceramic hot zone", "tooltip.earth_on_minecraft.chem.detail.device")),
            entry("battery_box", info("battery cells + Cu busbar + steel enclosure", "tooltip.earth_on_minecraft.chem.detail.battery")),
            entry("industrial_machine_casing", info("steel shell + ceramic liner + fasteners", "tooltip.earth_on_minecraft.chem.detail.device")),
            entry("steel_process_pipe", info("steel pipe + gasket + refractory lining", "tooltip.earth_on_minecraft.chem.detail.device")),
            entry("control_panel", info("steel housing + PCB + Cu wiring", "tooltip.earth_on_minecraft.chem.detail.electronics")),
            entry("fiberglass_cloth", info("SiO2-Al2O3-CaO glass fiber", "tooltip.earth_on_minecraft.chem.detail.glass")),
            entry("copper_clad_laminate", info("fiberglass + epoxy + Cu foil", "tooltip.earth_on_minecraft.chem.detail.electronics")),
            entry("printed_circuit_board", info("FR-4 + Cu traces", "tooltip.earth_on_minecraft.chem.detail.electronics")),
            entry("solder_alloy", info("Sn + Pb/Ag/Cu", "tooltip.earth_on_minecraft.chem.detail.alloy")),
            entry("solder_flux", info("rosin/organic acids + activators", "tooltip.earth_on_minecraft.chem.detail.electronics")),
            entry("led_phosphor", info("rare-earth-doped oxides/nitrides", "tooltip.earth_on_minecraft.chem.detail.electronics")),
            entry("ceramic_substrate", info("Al2O3 ceramic + metallization", "tooltip.earth_on_minecraft.chem.detail.electronics")),
            entry("ceramic_insulator", info("Al2O3/porcelain ceramic", "tooltip.earth_on_minecraft.chem.detail.ceramic")),
            entry("sphalerite_dust", info("ZnS", "tooltip.earth_on_minecraft.chem.detail.sulfide")),
            entry("zinc_oxide", info("ZnO", "tooltip.earth_on_minecraft.chem.detail.oxide")),
            entry("zinc_ingot", info("Zn", "tooltip.earth_on_minecraft.chem.detail.metal")),
            entry("galena_dust", info("PbS", "tooltip.earth_on_minecraft.chem.detail.sulfide")),
            entry("lead_ingot", info("Pb", "tooltip.earth_on_minecraft.chem.detail.metal")),
            entry("cassiterite_dust", info("SnO2", "tooltip.earth_on_minecraft.chem.detail.oxide_ore")),
            entry("tin_ingot", info("Sn", "tooltip.earth_on_minecraft.chem.detail.metal")),
            entry("kaolin_dust", info("Al2Si2O5(OH)4", "tooltip.earth_on_minecraft.chem.detail.clay")),
            entry("refractory_clay", info("Al2O3-SiO2 + Fe/Ti traces", "tooltip.earth_on_minecraft.chem.detail.clay")),
            entry("firebrick", info("Al2O3-SiO2 refractory ceramic", "tooltip.earth_on_minecraft.chem.detail.ceramic")),
            entry("ceramic_body", info("Al2O3-SiO2 + feldspar fluxes", "tooltip.earth_on_minecraft.chem.detail.ceramic")),
            entry("porcelain_blank", info("kaolin + feldspar + quartz", "tooltip.earth_on_minecraft.chem.detail.ceramic")),
            entry("mineral_wool", info("silicate glass fiber", "tooltip.earth_on_minecraft.chem.detail.glass")),
            entry("ammonium_sulfate", info("(NH4)2SO4", "tooltip.earth_on_minecraft.chem.detail.fertilizer")),
            entry("single_superphosphate", info("Ca(H2PO4)2 + CaSO4", "tooltip.earth_on_minecraft.chem.detail.fertilizer")),
            entry("urea_formaldehyde_resin", info("urea-formaldehyde thermoset", "tooltip.earth_on_minecraft.chem.detail.polymer")),
            entry("carbon_monoxide_cell", info("CO", "tooltip.earth_on_minecraft.chem.detail.gas")),
            entry("syngas_cell", info("CO + H2", "tooltip.earth_on_minecraft.chem.detail.gas_mix")),
            entry("borate_mineral_dust", info("Na2B4O7/Ca-borates", "tooltip.earth_on_minecraft.chem.detail.salt_mix")),
            entry("boron_carbide_pellet", info("B4C", "tooltip.earth_on_minecraft.chem.detail.ceramic")),
            entry("uraninite_dust", info("UO2/U3O8 + silicates", "tooltip.earth_on_minecraft.chem.detail.oxide_ore")),
            entry("yellowcake", info("U3O8-rich concentrate", "tooltip.earth_on_minecraft.chem.detail.concentrate")),
            entry("uranium_hexafluoride_cell", info("UF6", "tooltip.earth_on_minecraft.chem.detail.halide")),
            entry("low_enriched_uranium", info("UO2/U3O8 with low U-235 enrichment", "tooltip.earth_on_minecraft.chem.detail.concentrate")),
            entry("depleted_uranium", info("U-238-rich uranium", "tooltip.earth_on_minecraft.chem.detail.metal")),
            entry("uranium_dioxide_powder", info("UO2", "tooltip.earth_on_minecraft.chem.detail.oxide")),
            entry("nuclear_fuel_pellet", info("sintered UO2 ceramic", "tooltip.earth_on_minecraft.chem.detail.ceramic")),
            entry("zirconium_alloy_tube", info("Zr alloy cladding", "tooltip.earth_on_minecraft.chem.detail.alloy")),
            entry("control_rod_assembly", info("B4C absorber + steel/ceramic structure", "tooltip.earth_on_minecraft.chem.detail.device")),
            entry("nuclear_fuel_rod", info("UO2 pellets + Zr alloy cladding", "tooltip.earth_on_minecraft.chem.detail.device")),
            entry("nuclear_fuel_assembly", info("fuel rods + guide tubes + spacers", "tooltip.earth_on_minecraft.chem.detail.device")),
            entry("spent_fuel_assembly", info("used fuel assembly + fission products", "tooltip.earth_on_minecraft.chem.detail.device")),
            entry("dry_storage_cask", info("steel + concrete + sealed spent fuel basket", "tooltip.earth_on_minecraft.chem.detail.device")),
            entry("nuclear_heat_module", info("shielded reactor heat source module", "tooltip.earth_on_minecraft.chem.detail.device")),
            entry("copper_busbar", info("Cu conductor bar", "tooltip.earth_on_minecraft.chem.detail.cable")),
            entry("transformer_core", info("laminated Fe-Si core + Cu windings", "tooltip.earth_on_minecraft.chem.detail.device")),
            entry("grid_switchgear", info("insulated busbar + breakers + control PCB", "tooltip.earth_on_minecraft.chem.detail.device")),
            entry("generator_stator", info("Cu windings + magnetic steel stack", "tooltip.earth_on_minecraft.chem.detail.device")),
            entry("steam_turbine_assembly", info("steel rotor + blades + casing", "tooltip.earth_on_minecraft.chem.detail.device")),
            entry("industrial_sensor", info("semiconductor die + ceramic package + housing", "tooltip.earth_on_minecraft.chem.detail.electronics")),
            entry("plc_controller", info("PCB + processor + I/O terminals", "tooltip.earth_on_minecraft.chem.detail.electronics")),
            entry("servo_motor", info("Cu windings + NdFeB magnet + bearing shell", "tooltip.earth_on_minecraft.chem.detail.device")),
            entry("actuator_module", info("servo motor + gearbox + limit switch", "tooltip.earth_on_minecraft.chem.detail.device")),
            entry("robotic_arm", info("aluminum links + servos + control wiring", "tooltip.earth_on_minecraft.chem.detail.device")),
            entry("machine_vision_camera", info("Si sensor + lens + LED illumination", "tooltip.earth_on_minecraft.chem.detail.electronics")),
            entry("quality_inspection_module", info("camera + light + control PCB", "tooltip.earth_on_minecraft.chem.detail.electronics")),
            entry("automation_bus", info("shielded Cu data/power bus", "tooltip.earth_on_minecraft.chem.detail.cable")),
            entry("conveyor_drive", info("motor + rubber belt + gearbox", "tooltip.earth_on_minecraft.chem.detail.device")),
            entry("redstone_io_gateway", info("redstone interface + PLC I/O board", "tooltip.earth_on_minecraft.chem.detail.electronics"))
    );
    private static final Map<String, Info> VANILLA_INFO = Map.ofEntries(
            entry("minecraft:stone", info("SiO2 + feldspar + mica + mafic silicates", "tooltip.earth_on_minecraft.chem.detail.silicate_mix")),
            entry("minecraft:cobblestone", info("mixed silicate rock fragments", "tooltip.earth_on_minecraft.chem.detail.silicate_mix")),
            entry("minecraft:deepslate", info("Al-silicates + quartz + mica/chlorite traces", "tooltip.earth_on_minecraft.chem.detail.silicate_mix")),
            entry("minecraft:cobbled_deepslate", info("deepslate fragments + mica/chlorite traces", "tooltip.earth_on_minecraft.chem.detail.silicate_mix")),
            entry("minecraft:granite", info("SiO2 + KAlSi3O8 + NaAlSi3O8 + mica", "tooltip.earth_on_minecraft.chem.detail.silicate_mix")),
            entry("minecraft:diorite", info("plagioclase + hornblende/pyroxene", "tooltip.earth_on_minecraft.chem.detail.silicate_mix")),
            entry("minecraft:andesite", info("plagioclase + pyroxene/amphibole + Fe oxides", "tooltip.earth_on_minecraft.chem.detail.silicate_mix")),
            entry("minecraft:basalt", info("Ca-plagioclase + pyroxene + olivine + magnetite", "tooltip.earth_on_minecraft.chem.detail.silicate_mix")),
            entry("minecraft:tuff", info("volcanic glass + feldspar + mafic lithic fragments", "tooltip.earth_on_minecraft.chem.detail.mixed_rock")),
            entry("minecraft:calcite", info("CaCO3", "tooltip.earth_on_minecraft.chem.detail.carbonate")),
            entry("minecraft:dripstone_block", info("CaCO3 + clay/iron traces", "tooltip.earth_on_minecraft.chem.detail.carbonate")),
            entry("minecraft:sandstone", info("SiO2 grains + feldspar + calcite cement", "tooltip.earth_on_minecraft.chem.detail.silicate_mix")),
            entry("minecraft:blackstone", info("mafic silicates + Fe oxides", "tooltip.earth_on_minecraft.chem.detail.silicate_mix")),
            entry("minecraft:clay", info("Al2Si2O5(OH)4 + quartz + Fe oxides", "tooltip.earth_on_minecraft.chem.detail.clay")),
            entry("minecraft:clay_ball", info("Al2Si2O5(OH)4 + quartz + Fe oxides", "tooltip.earth_on_minecraft.chem.detail.clay")),
            entry("minecraft:sand", info("SiO2 + feldspar + heavy mineral traces", "tooltip.earth_on_minecraft.chem.detail.soil")),
            entry("minecraft:red_sand", info("SiO2 + Fe2O3 coatings + heavy mineral traces", "tooltip.earth_on_minecraft.chem.detail.soil")),
            entry("minecraft:gravel", info("quartz + carbonate + mafic rock fragments", "tooltip.earth_on_minecraft.chem.detail.soil")),
            entry("minecraft:dirt", info("silicates + clay + humus + carbonate/salt traces", "tooltip.earth_on_minecraft.chem.detail.soil")),
            entry("minecraft:grass_block", info("topsoil: silicates + clay + humus + roots", "tooltip.earth_on_minecraft.chem.detail.soil")),
            entry("minecraft:coarse_dirt", info("sand + silt + clay + humus-poor fragments", "tooltip.earth_on_minecraft.chem.detail.soil")),
            entry("minecraft:rooted_dirt", info("soil minerals + humus + root biomass", "tooltip.earth_on_minecraft.chem.detail.soil")),
            entry("minecraft:podzol", info("humus + leached aluminosilicates + Fe/Al oxides", "tooltip.earth_on_minecraft.chem.detail.soil")),
            entry("minecraft:mud", info("H2O + clay + silt + organics", "tooltip.earth_on_minecraft.chem.detail.soil")),
            entry("minecraft:copper_ore", info("legacy MC copper ore; natural generation should be replaced by chalcopyrite CuFeS2", "tooltip.earth_on_minecraft.chem.detail.fantasy_mix")),
            entry("minecraft:deepslate_copper_ore", info("legacy MC deepslate copper ore; natural generation should be replaced by chalcopyrite CuFeS2", "tooltip.earth_on_minecraft.chem.detail.fantasy_mix")),
            entry("minecraft:raw_copper", info("legacy MC raw copper; Earth on Minecraft routes copper through chalcopyrite concentrate", "tooltip.earth_on_minecraft.chem.detail.fantasy_mix")),
            entry("minecraft:raw_copper_block", info("legacy MC raw copper block; if found naturally, it came from vanilla ore-vein generation", "tooltip.earth_on_minecraft.chem.detail.fantasy_mix"))
    );

    private MaterialChemistry() {
    }

    static boolean hasDetails(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (EarthOnMinecraft.MODID.equals(id.getNamespace())) {
            return INFO.containsKey(id.getPath());
        }
        return VANILLA_INFO.containsKey(id.toString());
    }

    static void addDetails(ItemStack stack, java.util.function.Consumer<Component> lines, TooltipFlag flag) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Info info = EarthOnMinecraft.MODID.equals(id.getNamespace()) ? INFO.get(id.getPath()) : VANILLA_INFO.get(id.toString());
        if (info == null) {
            return;
        }
        if (!flag.hasShiftDown()) {
            lines.accept(Component.translatable("tooltip.earth_on_minecraft.chem.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        lines.accept(Component.translatable("tooltip.earth_on_minecraft.chem.section").withStyle(ChatFormatting.GOLD));
        lines.accept(Component.translatable("tooltip.earth_on_minecraft.chem.form", Component.translatable(formKey(id.getPath()))).withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable("tooltip.earth_on_minecraft.chem.formula", info.formula()).withStyle(ChatFormatting.AQUA));
        lines.accept(Component.translatable("tooltip.earth_on_minecraft.chem.category", Component.translatable(info.detailKey())).withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable("tooltip.earth_on_minecraft.chem.source", Component.translatable(sourceKey(info.detailKey()))).withStyle(ChatFormatting.DARK_GREEN));
        lines.accept(Component.translatable("tooltip.earth_on_minecraft.chem.process", Component.translatable(processKey(info.detailKey()))).withStyle(ChatFormatting.YELLOW));
        lines.accept(Component.translatable("tooltip.earth_on_minecraft.chem.use", Component.translatable(useKey(info.detailKey()))).withStyle(ChatFormatting.GREEN));
        lines.accept(Component.translatable("tooltip.earth_on_minecraft.chem.simplify", Component.translatable(simplifyKey(id.getPath(), info))).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Info info(String formula, String detailKey) {
        return new Info(formula, detailKey);
    }

    private static String formKey(String id) {
        if (id.endsWith("_ore") || id.endsWith("_vein") || id.endsWith("_seam") || id.equals("kimberlite")
                || id.equals("diamondiferous_kimberlite")) {
            return "tooltip.earth_on_minecraft.chem.form.natural_block";
        }
        if (id.endsWith("_chunk")) {
            return "tooltip.earth_on_minecraft.chem.form.crushed_chunk";
        }
        if (id.endsWith("_dust")) {
            return "tooltip.earth_on_minecraft.chem.form.powder";
        }
        if (id.endsWith("_powder")) {
            return "tooltip.earth_on_minecraft.chem.form.powder";
        }
        if (id.endsWith("_concentrate")) {
            return "tooltip.earth_on_minecraft.chem.form.concentrate";
        }
        if (id.endsWith("_cell")) {
            return "tooltip.earth_on_minecraft.chem.form.gas_cell";
        }
        if (id.endsWith("_ingot")) {
            return "tooltip.earth_on_minecraft.chem.form.ingot";
        }
        if (id.endsWith("_bloom")) {
            return "tooltip.earth_on_minecraft.chem.form.bloom";
        }
        if (id.endsWith("_billet")) {
            return "tooltip.earth_on_minecraft.chem.form.billet";
        }
        if (id.endsWith("_sample")) {
            return "tooltip.earth_on_minecraft.chem.form.sample";
        }
        if (id.endsWith("_resin")) {
            return "tooltip.earth_on_minecraft.chem.form.resin";
        }
        if (id.endsWith("_fiber")) {
            return "tooltip.earth_on_minecraft.chem.form.fiber";
        }
        if (id.endsWith("_acid") || id.endsWith("_solvent") || id.equals("ethanol")
                || id.equals("acetone") || id.equals("glycerol")) {
            return "tooltip.earth_on_minecraft.chem.form.liquid_reagent";
        }
        if (id.endsWith("_catalyst")) {
            return "tooltip.earth_on_minecraft.chem.form.catalyst";
        }
        if (id.endsWith("_wafer")) {
            return "tooltip.earth_on_minecraft.chem.form.wafer";
        }
        if (id.endsWith("_filter")) {
            return "tooltip.earth_on_minecraft.chem.form.filter";
        }
        if (id.endsWith("_magnet")) {
            return "tooltip.earth_on_minecraft.chem.form.magnet";
        }
        if (id.endsWith("_generator") || id.endsWith("_box") || id.endsWith("_casing")
                || id.endsWith("_pipe") || id.endsWith("_panel")) {
            return "tooltip.earth_on_minecraft.chem.form.machine_part";
        }
        if (id.endsWith("_blank") || id.endsWith("_body") || id.endsWith("_substrate")) {
            return "tooltip.earth_on_minecraft.chem.form.ceramic_part";
        }
        if (id.endsWith("_foam")) {
            return "tooltip.earth_on_minecraft.chem.form.foam";
        }
        if (id.endsWith("_cake") || id.endsWith("_residue") || id.endsWith("_tailings")) {
            return "tooltip.earth_on_minecraft.chem.form.residue";
        }
        if (id.contains("cable")) {
            return "tooltip.earth_on_minecraft.chem.form.cable";
        }
        return "tooltip.earth_on_minecraft.chem.form.intermediate";
    }

    private static String sourceKey(String detailKey) {
        return switch (detailKey) {
            case "tooltip.earth_on_minecraft.chem.detail.oxide_ore",
                    "tooltip.earth_on_minecraft.chem.detail.sulfide_ore",
                    "tooltip.earth_on_minecraft.chem.detail.vein",
                    "tooltip.earth_on_minecraft.chem.detail.mixed_rock",
                    "tooltip.earth_on_minecraft.chem.detail.silicate",
                    "tooltip.earth_on_minecraft.chem.detail.silicate_mix",
                    "tooltip.earth_on_minecraft.chem.detail.carbonate",
                    "tooltip.earth_on_minecraft.chem.detail.clay" -> "tooltip.earth_on_minecraft.chem.source.mineral";
            case "tooltip.earth_on_minecraft.chem.detail.native_element",
                    "tooltip.earth_on_minecraft.chem.detail.metal",
                    "tooltip.earth_on_minecraft.chem.detail.alloy",
                    "tooltip.earth_on_minecraft.chem.detail.oxide",
                    "tooltip.earth_on_minecraft.chem.detail.sulfide",
                    "tooltip.earth_on_minecraft.chem.detail.slag",
                    "tooltip.earth_on_minecraft.chem.detail.concentrate",
                    "tooltip.earth_on_minecraft.chem.detail.roasted" -> "tooltip.earth_on_minecraft.chem.source.metallurgy";
            case "tooltip.earth_on_minecraft.chem.detail.coal",
                    "tooltip.earth_on_minecraft.chem.detail.carbon_material" -> "tooltip.earth_on_minecraft.chem.source.coal_carbon";
            case "tooltip.earth_on_minecraft.chem.detail.petroleum_mix" -> "tooltip.earth_on_minecraft.chem.source.petroleum";
            case "tooltip.earth_on_minecraft.chem.detail.rare_earth" -> "tooltip.earth_on_minecraft.chem.source.rare_earth";
            case "tooltip.earth_on_minecraft.chem.detail.gas",
                    "tooltip.earth_on_minecraft.chem.detail.gas_mix" -> "tooltip.earth_on_minecraft.chem.source.gas";
            case "tooltip.earth_on_minecraft.chem.detail.biomass",
                    "tooltip.earth_on_minecraft.chem.detail.rubber" -> "tooltip.earth_on_minecraft.chem.source.biomass";
            case "tooltip.earth_on_minecraft.chem.detail.water",
                    "tooltip.earth_on_minecraft.chem.detail.soil",
                    "tooltip.earth_on_minecraft.chem.detail.salt_mix" -> "tooltip.earth_on_minecraft.chem.source.environment";
            case "tooltip.earth_on_minecraft.chem.detail.battery",
                    "tooltip.earth_on_minecraft.chem.detail.cable",
                    "tooltip.earth_on_minecraft.chem.detail.electronics",
                    "tooltip.earth_on_minecraft.chem.detail.semiconductor",
                    "tooltip.earth_on_minecraft.chem.detail.device" -> "tooltip.earth_on_minecraft.chem.source.manufactured";
            default -> "tooltip.earth_on_minecraft.chem.source.industrial";
        };
    }

    private static String processKey(String detailKey) {
        return switch (detailKey) {
            case "tooltip.earth_on_minecraft.chem.detail.oxide_ore",
                    "tooltip.earth_on_minecraft.chem.detail.sulfide_ore",
                    "tooltip.earth_on_minecraft.chem.detail.vein",
                    "tooltip.earth_on_minecraft.chem.detail.mixed_rock",
                    "tooltip.earth_on_minecraft.chem.detail.silicate",
                    "tooltip.earth_on_minecraft.chem.detail.silicate_mix",
                    "tooltip.earth_on_minecraft.chem.detail.carbonate",
                    "tooltip.earth_on_minecraft.chem.detail.clay",
                    "tooltip.earth_on_minecraft.chem.detail.concentrate",
                    "tooltip.earth_on_minecraft.chem.detail.roasted",
                    "tooltip.earth_on_minecraft.chem.detail.tailings" -> "tooltip.earth_on_minecraft.chem.process.mineral";
            case "tooltip.earth_on_minecraft.chem.detail.native_element",
                    "tooltip.earth_on_minecraft.chem.detail.metal",
                    "tooltip.earth_on_minecraft.chem.detail.alloy",
                    "tooltip.earth_on_minecraft.chem.detail.oxide",
                    "tooltip.earth_on_minecraft.chem.detail.sulfide",
                    "tooltip.earth_on_minecraft.chem.detail.slag" -> "tooltip.earth_on_minecraft.chem.process.metallurgy";
            case "tooltip.earth_on_minecraft.chem.detail.acid",
                    "tooltip.earth_on_minecraft.chem.detail.halide",
                    "tooltip.earth_on_minecraft.chem.detail.base",
                    "tooltip.earth_on_minecraft.chem.detail.salt",
                    "tooltip.earth_on_minecraft.chem.detail.salt_mix",
                    "tooltip.earth_on_minecraft.chem.detail.sulfate",
                    "tooltip.earth_on_minecraft.chem.detail.fertilizer",
                    "tooltip.earth_on_minecraft.chem.detail.fertilizer_mix" -> "tooltip.earth_on_minecraft.chem.process.inorganic";
            case "tooltip.earth_on_minecraft.chem.detail.gas",
                    "tooltip.earth_on_minecraft.chem.detail.gas_mix" -> "tooltip.earth_on_minecraft.chem.process.gas";
            case "tooltip.earth_on_minecraft.chem.detail.coal",
                    "tooltip.earth_on_minecraft.chem.detail.petroleum_mix" -> "tooltip.earth_on_minecraft.chem.process.fuel";
            case "tooltip.earth_on_minecraft.chem.detail.monomer",
                    "tooltip.earth_on_minecraft.chem.detail.polymer",
                    "tooltip.earth_on_minecraft.chem.detail.polymer_mix",
                    "tooltip.earth_on_minecraft.chem.detail.solvent",
                    "tooltip.earth_on_minecraft.chem.detail.reactive_organic",
                    "tooltip.earth_on_minecraft.chem.detail.rubber" -> "tooltip.earth_on_minecraft.chem.process.organic";
            case "tooltip.earth_on_minecraft.chem.detail.cleaning",
                    "tooltip.earth_on_minecraft.chem.detail.oxidizer" -> "tooltip.earth_on_minecraft.chem.process.cleaning";
            case "tooltip.earth_on_minecraft.chem.detail.catalyst" -> "tooltip.earth_on_minecraft.chem.process.catalyst";
            case "tooltip.earth_on_minecraft.chem.detail.rare_earth" -> "tooltip.earth_on_minecraft.chem.process.rare_earth";
            case "tooltip.earth_on_minecraft.chem.detail.semiconductor" -> "tooltip.earth_on_minecraft.chem.process.semiconductor";
            case "tooltip.earth_on_minecraft.chem.detail.battery",
                    "tooltip.earth_on_minecraft.chem.detail.cable",
                    "tooltip.earth_on_minecraft.chem.detail.electronics",
                    "tooltip.earth_on_minecraft.chem.detail.device" -> "tooltip.earth_on_minecraft.chem.process.assembly";
            case "tooltip.earth_on_minecraft.chem.detail.cement",
                    "tooltip.earth_on_minecraft.chem.detail.glass",
                    "tooltip.earth_on_minecraft.chem.detail.ceramic" -> "tooltip.earth_on_minecraft.chem.process.thermal";
            case "tooltip.earth_on_minecraft.chem.detail.water",
                    "tooltip.earth_on_minecraft.chem.detail.soil" -> "tooltip.earth_on_minecraft.chem.process.environment";
            case "tooltip.earth_on_minecraft.chem.detail.biomass" -> "tooltip.earth_on_minecraft.chem.process.biomass";
            default -> "tooltip.earth_on_minecraft.chem.process.general";
        };
    }

    private static String useKey(String detailKey) {
        return switch (detailKey) {
            case "tooltip.earth_on_minecraft.chem.detail.oxide_ore",
                    "tooltip.earth_on_minecraft.chem.detail.sulfide_ore",
                    "tooltip.earth_on_minecraft.chem.detail.metal",
                    "tooltip.earth_on_minecraft.chem.detail.alloy",
                    "tooltip.earth_on_minecraft.chem.detail.concentrate",
                    "tooltip.earth_on_minecraft.chem.detail.roasted" -> "tooltip.earth_on_minecraft.chem.use.metals";
            case "tooltip.earth_on_minecraft.chem.detail.acid",
                    "tooltip.earth_on_minecraft.chem.detail.base",
                    "tooltip.earth_on_minecraft.chem.detail.salt",
                    "tooltip.earth_on_minecraft.chem.detail.salt_mix",
                    "tooltip.earth_on_minecraft.chem.detail.sulfate",
                    "tooltip.earth_on_minecraft.chem.detail.halide",
                    "tooltip.earth_on_minecraft.chem.detail.gas",
                    "tooltip.earth_on_minecraft.chem.detail.gas_mix",
                    "tooltip.earth_on_minecraft.chem.detail.solvent",
                    "tooltip.earth_on_minecraft.chem.detail.reactive_organic" -> "tooltip.earth_on_minecraft.chem.use.chemistry";
            case "tooltip.earth_on_minecraft.chem.detail.cleaning",
                    "tooltip.earth_on_minecraft.chem.detail.oxidizer" -> "tooltip.earth_on_minecraft.chem.use.treatment";
            case "tooltip.earth_on_minecraft.chem.detail.catalyst" -> "tooltip.earth_on_minecraft.chem.use.catalyst";
            case "tooltip.earth_on_minecraft.chem.detail.fertilizer",
                    "tooltip.earth_on_minecraft.chem.detail.fertilizer_mix",
                    "tooltip.earth_on_minecraft.chem.detail.soil" -> "tooltip.earth_on_minecraft.chem.use.agriculture";
            case "tooltip.earth_on_minecraft.chem.detail.cement",
                    "tooltip.earth_on_minecraft.chem.detail.glass",
                    "tooltip.earth_on_minecraft.chem.detail.ceramic",
                    "tooltip.earth_on_minecraft.chem.detail.clay",
                    "tooltip.earth_on_minecraft.chem.detail.silicate",
                    "tooltip.earth_on_minecraft.chem.detail.silicate_mix",
                    "tooltip.earth_on_minecraft.chem.detail.carbonate" -> "tooltip.earth_on_minecraft.chem.use.construction";
            case "tooltip.earth_on_minecraft.chem.detail.coal",
                    "tooltip.earth_on_minecraft.chem.detail.petroleum_mix",
                    "tooltip.earth_on_minecraft.chem.detail.carbon_material" -> "tooltip.earth_on_minecraft.chem.use.energy";
            case "tooltip.earth_on_minecraft.chem.detail.monomer",
                    "tooltip.earth_on_minecraft.chem.detail.polymer",
                    "tooltip.earth_on_minecraft.chem.detail.polymer_mix",
                    "tooltip.earth_on_minecraft.chem.detail.rubber",
                    "tooltip.earth_on_minecraft.chem.detail.biomass" -> "tooltip.earth_on_minecraft.chem.use.materials";
            case "tooltip.earth_on_minecraft.chem.detail.battery",
                    "tooltip.earth_on_minecraft.chem.detail.cable",
                    "tooltip.earth_on_minecraft.chem.detail.device" -> "tooltip.earth_on_minecraft.chem.use.power";
            case "tooltip.earth_on_minecraft.chem.detail.electronics",
                    "tooltip.earth_on_minecraft.chem.detail.semiconductor" -> "tooltip.earth_on_minecraft.chem.use.electronics";
            case "tooltip.earth_on_minecraft.chem.detail.water",
                    "tooltip.earth_on_minecraft.chem.detail.tailings",
                    "tooltip.earth_on_minecraft.chem.detail.slag" -> "tooltip.earth_on_minecraft.chem.use.treatment";
            case "tooltip.earth_on_minecraft.chem.detail.pigment",
                    "tooltip.earth_on_minecraft.chem.detail.rare_earth",
                    "tooltip.earth_on_minecraft.chem.detail.fantasy_mix",
                    "tooltip.earth_on_minecraft.chem.detail.vein",
                    "tooltip.earth_on_minecraft.chem.detail.native_element",
                    "tooltip.earth_on_minecraft.chem.detail.mixed_rock" -> "tooltip.earth_on_minecraft.chem.use.special";
            default -> "tooltip.earth_on_minecraft.chem.use.general";
        };
    }

    private static String simplifyKey(String id, Info info) {
        if (info.detailKey().equals("tooltip.earth_on_minecraft.chem.detail.fantasy_mix")) {
            return "tooltip.earth_on_minecraft.chem.simplify.fantasy";
        }
        if (info.detailKey().equals("tooltip.earth_on_minecraft.chem.detail.device")
                || info.detailKey().equals("tooltip.earth_on_minecraft.chem.detail.catalyst")
                || info.detailKey().equals("tooltip.earth_on_minecraft.chem.detail.semiconductor")) {
            return "tooltip.earth_on_minecraft.chem.simplify.composite";
        }
        if (info.detailKey().equals("tooltip.earth_on_minecraft.chem.detail.rare_earth")) {
            return "tooltip.earth_on_minecraft.chem.simplify.mixture";
        }
        if (id.endsWith("_cell")) {
            return "tooltip.earth_on_minecraft.chem.simplify.gas_cell";
        }
        if (id.contains("cable") || info.detailKey().equals("tooltip.earth_on_minecraft.chem.detail.electronics")
                || info.detailKey().equals("tooltip.earth_on_minecraft.chem.detail.battery")) {
            return "tooltip.earth_on_minecraft.chem.simplify.composite";
        }
        if (info.formula().contains("+") || info.formula().contains("/") || info.formula().contains("rich")
                || info.formula().contains("trace") || info.formula().contains("equivalent")) {
            return "tooltip.earth_on_minecraft.chem.simplify.mixture";
        }
        return "tooltip.earth_on_minecraft.chem.simplify.stackable";
    }

    private record Info(String formula, String detailKey) {
    }
}
