package com.xxsx.earthonminecraft.component;

import com.mojang.serialization.Codec;
import com.xxsx.earthonminecraft.EarthOnMinecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Data component types, registered through a {@link DeferredRegister}.
 *
 * <p>These used to be registered eagerly with {@code Registry.register(...)} from the mod
 * constructor. That throws {@code IllegalStateException: Registry is already frozen} because the
 * constructor runs after the registries close, and the resulting {@code ExceptionInInitializerError}
 * aborts mod construction entirely. {@link DeferredRegister} defers each registration to the
 * registry event, which is the supported path.</p>
 */
public final class EarthDataComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, EarthOnMinecraft.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FoodQuality>> FOOD_QUALITY =
            register("food_quality", FoodQuality.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GemComponent>> GEM =
            register("gem", GemComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<HerbComponent>> HERB =
            register("herb", HerbComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PharmaceuticalComponent>> PHARMACEUTICAL =
            register("pharmaceutical", PharmaceuticalComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ToolQualityComponent>> TOOL_QUALITY =
            register("tool_quality", ToolQualityComponent.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MaterialBatch>> MATERIAL_BATCH =
            register("material_batch", MaterialBatch.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MetalBatch>> METAL_BATCH =
            register("metal_batch", MetalBatch.CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> STERILE =
            registerSynced("sterile", Codec.BOOL, ByteBufCodecs.BOOL);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<OpticalGlassComponent>> OPTICAL_GLASS =
            registerSynced("optical_glass", OpticalGlassComponent.CODEC, OpticalGlassComponent.STREAM_CODEC);

    private EarthDataComponents() {
    }

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(
            String name, Codec<T> codec) {
        return COMPONENTS.register(name,
                () -> DataComponentType.<T>builder().persistent(codec).build());
    }

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> registerSynced(
            String name, Codec<T> codec, StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, T> streamCodec) {
        return COMPONENTS.register(name,
                () -> DataComponentType.<T>builder().persistent(codec).networkSynchronized(streamCodec).build());
    }
}
