package com.xxsx.earthonminecraft;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayableContentFilterTest {

    @Test
    void allWeaponsAndMedicinesAreWhitelisted() {
        for (String weaponId : EarthMedicalAndWeaponSupport.weapons().keySet()) {
            assertTrue(PlayableContentFilter.isPlayableItemPath(weaponId),
                    "Weapon/tool must be playable: " + weaponId);
        }
        for (String medId : EarthMedicalAndWeaponSupport.medicines().keySet()) {
            assertTrue(PlayableContentFilter.isPlayableItemPath(medId),
                    "Medicine must be playable: " + medId);
        }
    }

    @Test
    void crossModDependenciesAndCoreLoopItemsAreWhitelisted() {
        Set<String> required = Set.of(
                "gem_rough", "gem_cut", "gem_carving_tool",
                "grinding_wheel_coarse", "grinding_wheel_fine", "grinding_wheel_polish",
                "sponge_iron", "wrought_iron", "steel_bloom", "steel",
                "copper_wire", "thin_copper_power_cable", "copper_power_cable",
                "combustion_generator", "battery_box", "hand_crank", "geology_hammer",
                "settlement_board", "packed_earth", "firebrick_block", "clay_brick_block",
                "industrial_machine_casing", "steel_process_pipe", "control_panel",
                "ginseng", "astragalus", "jujube_seed", "biota_seed", "coptis",
                "forsythia", "salvia_miltiorrhiza", "safflower", "licorice", "mung_bean"
        );
        for (String id : required) {
            assertTrue(PlayableContentFilter.isPlayableItemPath(id),
                    "Required cross-mod or core progression item must be playable: " + id);
        }
    }

    @Test
    void unimplementedPlaceholdersAndLateIndustrialStubsAreFilteredOut() {
        Set<String> excluded = Set.of(
                "copper_armor", "iron_armor", "steel_armor", "diamond_armor", "chainmail", "steel_boots",
                "automobile", "truck", "locomotive", "airship", "piano", "television",
                "uraninite_ore", "uraninite_dust", "yellowcake", "nuclear_fuel_rod",
                "plc_controller", "robotic_arm", "steam_turbine_generator", "heavy_copper_power_cable",
                "depleted_magnetite_lens", "magnetite_tuff_halo", "cigar", "cigarette", "snuff"
        );
        for (String id : excluded) {
            assertFalse(PlayableContentFilter.isPlayableItemPath(id),
                    "Unfinished placeholder item must be excluded: " + id);
        }
    }
}
