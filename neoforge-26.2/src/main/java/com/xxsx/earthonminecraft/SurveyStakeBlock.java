package com.xxsx.earthonminecraft;

import com.xxsx.earthlore.api.LoreApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * M-005 测桩 ({@code survey_stake}): fixed geological survey marker that triangulates nearby ore bodies within a
 * 4-chunk radius. Before learning {@code K-GEO-02} ({@code earth_on_minecraft:geo/field_survey}), readout level is 0
 * and only reports whether the stake stands over a deposit projection; after learning {@code K-GEO-02}, it reports
 * the nearest main ore body's name, compass bearing, horizontal distance, and relative depth.
 */
public final class SurveyStakeBlock extends Block {
    public static final int SURVEY_CHUNK_RADIUS = 4;

    public SurveyStakeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS_SERVER;
        }

        int readout = LoreApi.readoutLevel(serverPlayer, this.asItem());
        List<ProspectingSurvey.Group> groups = ProspectingSurvey.group(
                ProspectingSurvey.collect(level, ChunkPos.containing(pos), SURVEY_CHUNK_RADIUS, ProspectingCatalog::isMainLode),
                pos);

        if (readout <= 0) {
            String key = groups.isEmpty()
                    ? "message.earth_on_minecraft.survey_stake.category_none"
                    : "message.earth_on_minecraft.survey_stake.category_present";
            serverPlayer.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS_SERVER;
        }

        if (groups.isEmpty()) {
            serverPlayer.sendSystemMessage(Component.translatable(
                    "message.earth_on_minecraft.survey_stake.none").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS_SERVER;
        }

        ProspectingSurvey.Group nearest = groups.getFirst();
        ProspectingSurvey.Bearing bearing = ProspectingSurvey.bearing(pos, nearest.nearest());
        int horizDist = ProspectingSurvey.roundToFive(ProspectingSurvey.horizontalDistance(pos, nearest.nearest()));
        Component height = ProspectingSurvey.heightText(ProspectingSurvey.roundedHeight(pos, nearest.nearest()));
        serverPlayer.sendSystemMessage(Component.translatable(
                "message.earth_on_minecraft.survey_stake.detail",
                ProspectingSurvey.lodeName(nearest.lodeId()),
                Component.translatable(bearing.translationKey()),
                horizDist,
                height,
                nearest.remaining()).withStyle(ChatFormatting.AQUA));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hitResult) {
        return useWithoutItem(state, level, pos, player, hitResult);
    }
}
