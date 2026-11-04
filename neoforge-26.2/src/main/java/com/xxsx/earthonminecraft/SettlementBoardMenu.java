package com.xxsx.earthonminecraft;

import com.xxsx.earthonminecraft.living.SettlementSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SettlementBoardMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final SettlementSavedData.SettlementSnapshot snapshot;

    public SettlementBoardMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, readPayload(buf));
    }

    private SettlementBoardMenu(int containerId, Inventory inventory, Payload payload) {
        this(containerId, inventory, payload.pos(), payload.snapshot());
    }

    public SettlementBoardMenu(int containerId, Inventory inventory, BlockPos pos,
                               SettlementSavedData.SettlementSnapshot snapshot) {
        super(EarthOnMinecraft.SETTLEMENT_BOARD_MENU.get(), containerId);
        this.pos = pos;
        this.snapshot = snapshot;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).is(EarthOnMinecraft.SETTLEMENT_BOARD.get())
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    public BlockPos pos() {
        return pos;
    }

    public SettlementSavedData.SettlementSnapshot snapshot() {
        return snapshot;
    }

    public int tier() {
        return snapshot.tier();
    }

    public String tierProgressKey() {
        return snapshot.tierProgressKey();
    }

    public static void writePayload(RegistryFriendlyByteBuf buf, BlockPos pos,
                                    SettlementSavedData.SettlementSnapshot snapshot) {
        buf.writeBlockPos(pos);
        buf.writeUtf(snapshot.id());
        buf.writeUtf(snapshot.dimension());
        buf.writeBlockPos(snapshot.center());
        buf.writeUtf(snapshot.profileId());
        buf.writeUtf(snapshot.nameKey());
        buf.writeVarInt(snapshot.tier());
        buf.writeUtf(snapshot.tierProgressKey());
        buf.writeUtf(snapshot.profileNameKey());
        buf.writeUtf(snapshot.scaleKey());
        buf.writeUtf(snapshot.technologyKey());
        writeStrings(buf, snapshot.industryKeys());
        writeStrings(buf, snapshot.demandKeys());
        writeStrings(buf, snapshot.supplyKeys());
        writeResidents(buf, snapshot.residents());
        writeCounts(buf, snapshot.roleCounts());
        writeCounts(buf, snapshot.facilityCounts());
        buf.writeVarInt(snapshot.security());
        buf.writeInt(snapshot.reputation());
        buf.writeLong(snapshot.createdAt());
        buf.writeLong(snapshot.updatedAt());
    }

    public static SettlementSavedData.SettlementSnapshot emptySnapshot(BlockPos pos) {
        return new SettlementSavedData.SettlementSnapshot("", "minecraft:overworld", pos,
                EarthOnMinecraft.id("agricultural_village").toString(),
                "settlement.earth_on_minecraft.name.unknown",
                1, "settlement.earth_on_minecraft.tier.status.tier_1_need_mining",
                "settlement.earth_on_minecraft.profile.agricultural_village",
                "settlement.earth_on_minecraft.scale.village",
                "settlement.earth_on_minecraft.technology.agricultural",
                List.of(), List.of(), List.of(), List.of(), Map.of(), Map.of(), 50, 0, 0L, 0L);
    }

    private static Payload readPayload(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        SettlementSavedData.SettlementSnapshot snapshot = new SettlementSavedData.SettlementSnapshot(
                buf.readUtf(), buf.readUtf(), buf.readBlockPos(), buf.readUtf(), buf.readUtf(),
                Math.max(1, Math.min(4, buf.readVarInt())), buf.readUtf(), buf.readUtf(),
                buf.readUtf(), buf.readUtf(), readStrings(buf), readStrings(buf), readStrings(buf),
                readResidents(buf), readCounts(buf), readCounts(buf), buf.readVarInt(), buf.readInt(),
                buf.readLong(), buf.readLong());
        return new Payload(pos, snapshot);
    }

    private static void writeStrings(RegistryFriendlyByteBuf buf, List<String> values) {
        buf.writeVarInt(values.size());
        values.forEach(buf::writeUtf);
    }

    private static List<String> readStrings(RegistryFriendlyByteBuf buf) {
        int size = Math.min(256, Math.max(0, buf.readVarInt()));
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buf.readUtf());
        }
        return List.copyOf(values);
    }

    private static void writeCounts(RegistryFriendlyByteBuf buf, Map<String, Integer> values) {
        buf.writeVarInt(values.size());
        values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            buf.writeUtf(entry.getKey());
            buf.writeVarInt(Math.max(0, entry.getValue()));
        });
    }

    private static Map<String, Integer> readCounts(RegistryFriendlyByteBuf buf) {
        int size = Math.min(256, Math.max(0, buf.readVarInt()));
        Map<String, Integer> values = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            values.put(buf.readUtf(), Math.max(0, buf.readVarInt()));
        }
        return Map.copyOf(values);
    }

    private static void writeResidents(RegistryFriendlyByteBuf buf,
                                       List<SettlementSavedData.ResidentSummary> residents) {
        buf.writeVarInt(residents.size());
        for (SettlementSavedData.ResidentSummary resident : residents) {
            buf.writeUtf(resident.nameKey());
            buf.writeUtf(resident.roleId());
            buf.writeVarInt(Math.max(1, resident.skill()));
        }
    }

    private static List<SettlementSavedData.ResidentSummary> readResidents(RegistryFriendlyByteBuf buf) {
        int size = Math.min(256, Math.max(0, buf.readVarInt()));
        List<SettlementSavedData.ResidentSummary> residents = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            residents.add(new SettlementSavedData.ResidentSummary(
                    buf.readUtf(), buf.readUtf(), Math.max(1, buf.readVarInt())));
        }
        return List.copyOf(residents);
    }

    private record Payload(BlockPos pos, SettlementSavedData.SettlementSnapshot snapshot) {
    }
}
