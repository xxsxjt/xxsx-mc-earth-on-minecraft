package com.xxsx.earthonminecraft;

import com.xxsx.earthlore.api.LoreApi;
import com.xxsx.earthonminecraft.component.MaterialBatch;
import com.xxsx.earthonminecraft.component.QualityComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.util.Optional;

/**
 * M-004 放大镜 ({@code hand_lens}): inspects the mineral sample held in the other hand.
 * Before learning {@code K-GEO-01} ({@code earth_on_minecraft:geo/mineral_identification}), readout level is 0 and only the
 * mineral category is reported; after learning {@code K-GEO-01}, readout level is 1 and exact species, purity,
 * moisture, particle size, and grade are shown.
 */
public final class HandLensItem extends SpecItem {
    public HandLensItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack sample = player.getItemInHand(otherHand);
        if (sample.isEmpty()) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.earth_on_minecraft.hand_lens.empty")
                        .withStyle(ChatFormatting.GRAY));
            }
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS_SERVER;
        }

        int readout = LoreApi.readoutLevel(serverPlayer, this);
        Identifier sampleId = BuiltInRegistries.ITEM.getKey(sample.getItem());
        String categoryKey = MaterialChemistry.describe(sampleId)
                .map(MaterialChemistry.Descriptor::categoryKey)
                .orElse("tooltip.earth_on_minecraft.chem.detail.mineral");

        if (readout <= 0) {
            serverPlayer.sendSystemMessage(Component.translatable(
                    "message.earth_on_minecraft.hand_lens.category_only",
                    Component.translatable(categoryKey)).withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS_SERVER;
        }

        Optional<MaterialBatch> batchOpt = QualityComponents.resolveMaterialBatch(sample);
        if (batchOpt.isEmpty()) {
            serverPlayer.sendSystemMessage(Component.translatable(
                    "message.earth_on_minecraft.hand_lens.detail_plain",
                    sample.getHoverName(),
                    Component.translatable(categoryKey)).withStyle(ChatFormatting.AQUA));
            return InteractionResult.SUCCESS_SERVER;
        }

        MaterialBatch batch = batchOpt.get();
        serverPlayer.sendSystemMessage(Component.translatable(
                "message.earth_on_minecraft.hand_lens.detail",
                sample.getHoverName(),
                Component.translatable(categoryKey),
                String.format(Locale.ROOT, "%.2f%%", batch.purity() * 100.0),
                String.format(Locale.ROOT, "%.1f%%", batch.moisture() * 100.0),
                batch.particleSizeUm(),
                Component.translatable(batch.grade().translationKey())).withStyle(ChatFormatting.AQUA));
        return InteractionResult.SUCCESS_SERVER;
    }
}
