package com.xxsx.earthonminecraft;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;

/** A bounded field instrument that reports nearby live lodes without changing terrain. */
public final class OreScannerItem extends Item {
    private static final int HORIZONTAL_RADIUS = 24;
    private static final int VERTICAL_RADIUS = 24;
    private static final int SCAN_RADIUS_SQUARED = HORIZONTAL_RADIUS * HORIZONTAL_RADIUS;
    private static final int MAX_RESULTS = 5;

    public OreScannerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockPos origin = player.blockPosition();
        Map<String, Integer> reserves = new LinkedHashMap<>();
        BlockPos nearest = null;
        String nearestLode = "";
        double nearestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -HORIZONTAL_RADIUS; dx <= HORIZONTAL_RADIUS; dx++) {
            for (int dz = -HORIZONTAL_RADIUS; dz <= HORIZONTAL_RADIUS; dz++) {
                for (int dy = -VERTICAL_RADIUS; dy <= VERTICAL_RADIUS; dy++) {
                    if (dx * dx + dy * dy + dz * dz > SCAN_RADIUS_SQUARED) {
                        continue;
                    }
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.hasChunkAt(cursor)) {
                        continue;
                    }
                    if (level.getBlockEntity(cursor) instanceof DepositBlockEntity deposit
                            && !deposit.reserve().isEmpty()) {
                        String lode = deposit.reserve().lodeId().getPath();
                        reserves.merge(lode, deposit.reserve().remaining(), Integer::sum);
                        double distance = cursor.distSqr(origin);
                        if (distance < nearestDistance) {
                            nearestDistance = distance;
                            nearest = cursor.immutable();
                            nearestLode = lode;
                        }
                    }
                }
            }
        }

        if (nearest == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.earth_on_minecraft.ore_scanner.none").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS_SERVER;
        }

        player.sendSystemMessage(Component.translatable(
                "message.earth_on_minecraft.ore_scanner.found",
                nearestLode, nearest.getX() - origin.getX(), nearest.getY() - origin.getY(),
                nearest.getZ() - origin.getZ(), Math.round(Math.sqrt(nearestDistance)), reserves.size(),
                reserves.values().stream().mapToInt(Integer::intValue).sum()).withStyle(ChatFormatting.AQUA));
        reserves.entrySet().stream().limit(MAX_RESULTS).forEach(entry -> player.sendSystemMessage(
                Component.translatable("message.earth_on_minecraft.ore_scanner.entry",
                        entry.getKey(), entry.getValue()).withStyle(ChatFormatting.GRAY)));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        lines.accept(Component.translatable("tooltip.earth_on_minecraft.ore_scanner"));
    }
}
