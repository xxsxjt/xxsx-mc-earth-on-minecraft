package com.xxsx.earthonminecraft.component;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum Grade implements StringRepresentable {
    CRUDE(0, "crude"),
    COMMON(1, "common"),
    FINE(2, "fine"),
    MASTERWORK(3, "masterwork"),
    HEIRLOOM(4, "heirloom");

    public static final Codec<Grade> CODEC = StringRepresentable.fromEnum(Grade::values);

    private final int tier;
    private final String name;

    Grade(int tier, String name) {
        this.tier = tier;
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public int tier() {
        return tier;
    }

    public String translationKey() {
        return "grade.earth_on_minecraft." + name;
    }

    public static Grade byName(String name) {
        for (Grade grade : values()) {
            if (grade.name.equals(name)) {
                return grade;
            }
        }
        return null;
    }

    public static Grade fromTier(int tier) {
        for (Grade grade : values()) {
            if (grade.tier == tier) {
                return grade;
            }
        }
        return COMMON;
    }
}
