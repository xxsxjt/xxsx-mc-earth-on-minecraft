package com.xxsx.earthonminecraft.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;

/**
 * M-098 optical_glass component: refractive_index (1.45..1.95, default 1.52, persisted & synced)
 * and bubble_count (0..20, default 0, persisted). When bubble_count > 5, grade is downgraded by 1
 * and fail code is bubbles_high.
 */
public record OpticalGlassComponent(int schemaVersion, double refractiveIndex, int bubbleCount) {
    public static final OpticalGlassComponent DEFAULT = new OpticalGlassComponent(1, 1.52, 0);

    public OpticalGlassComponent(double refractiveIndex, int bubbleCount) {
        this(1, refractiveIndex, bubbleCount);
    }

    public static final Codec<OpticalGlassComponent> CODEC = RecordCodecBuilder.<OpticalGlassComponent>create(instance -> instance.group(
            ExtraCodecs.intRange(1, 1).optionalFieldOf("schema_version", 1).forGetter(OpticalGlassComponent::schemaVersion),
            Codec.doubleRange(1.45, 1.95).optionalFieldOf("refractive_index", 1.52).forGetter(OpticalGlassComponent::refractiveIndex),
            ExtraCodecs.intRange(0, 20).optionalFieldOf("bubble_count", 0).forGetter(OpticalGlassComponent::bubbleCount)
    ).apply(instance, OpticalGlassComponent::new)).validate(OpticalGlassComponent::validate);

    public static final StreamCodec<ByteBuf, OpticalGlassComponent> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpticalGlassComponent::schemaVersion,
            ByteBufCodecs.DOUBLE, OpticalGlassComponent::refractiveIndex,
            ByteBufCodecs.VAR_INT, OpticalGlassComponent::bubbleCount,
            OpticalGlassComponent::new
    );

    private static DataResult<OpticalGlassComponent> validate(OpticalGlassComponent value) {
        if (value.schemaVersion != 1) {
            return DataResult.error(() -> "unsupported optical_glass schema_version " + value.schemaVersion);
        }
        if (value.refractiveIndex < 1.45 || value.refractiveIndex > 1.95) {
            return DataResult.error(() -> "refractive_index out of range: " + value.refractiveIndex);
        }
        if (value.bubbleCount < 0 || value.bubbleCount > 20) {
            return DataResult.error(() -> "bubble_count out of range: " + value.bubbleCount);
        }
        return DataResult.success(value);
    }

    public boolean hasHighBubbles() {
        return bubbleCount > 5;
    }

    public String failCode() {
        return hasHighBubbles() ? "bubbles_high" : "";
    }

    public Grade applyBubblePenalty(Grade base) {
        return hasHighBubbles() ? base.stepDown() : base;
    }

    public Grade capGrade(Grade base) {
        return applyBubblePenalty(base);
    }
}
