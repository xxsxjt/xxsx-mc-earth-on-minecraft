package com.xxsx.earthonminecraft;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MachineFeedback {
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, EarthOnMinecraft.MODID);
    private static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, EarthOnMinecraft.MODID);
    private static final Map<ProcessingMachineBlock.Kind, DeferredHolder<SoundEvent, SoundEvent>> RUN_SOUNDS =
            new EnumMap<>(ProcessingMachineBlock.Kind.class);
    private static final Map<ProcessingMachineBlock.Kind, DeferredHolder<ParticleType<?>, SimpleParticleType>> PROCESS_PARTICLES =
            new EnumMap<>(ProcessingMachineBlock.Kind.class);
    private static final Map<DeviceKind, DeferredHolder<SoundEvent, SoundEvent>> DEVICE_RUN_SOUNDS =
            new EnumMap<>(DeviceKind.class);
    private static final Map<DeviceKind, DeferredHolder<ParticleType<?>, SimpleParticleType>> DEVICE_PARTICLES =
            new EnumMap<>(DeviceKind.class);

    private static final DeferredHolder<SoundEvent, SoundEvent> FAULT_SOUND = sound("machine.fault");
    private static final DeferredHolder<SoundEvent, SoundEvent> COMPLETE_SOUND = sound("machine.complete");

    static {
        for (ProcessingMachineBlock.Kind kind : ProcessingMachineBlock.Kind.values()) {
            String soundPath = "machine." + kind.blockId() + ".run";
            RUN_SOUNDS.put(kind, sound(soundPath));
            PROCESS_PARTICLES.put(kind, PARTICLES.register(
                    "machine_" + kind.blockId() + "_process", () -> new SimpleParticleType(false)));
        }
        for (DeviceKind kind : DeviceKind.values()) {
            DEVICE_RUN_SOUNDS.put(kind, sound("device." + kind.id + ".run"));
            DEVICE_PARTICLES.put(kind, PARTICLES.register(
                    "device_" + kind.id + "_process", () -> new SimpleParticleType(false)));
        }
    }

    private MachineFeedback() {
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
        PARTICLES.register(modBus);
    }

    public static SimpleParticleType particle(ProcessingMachineBlock.Kind kind) {
        return PROCESS_PARTICLES.get(kind).get();
    }

    public static SimpleParticleType particle(DeviceKind kind) {
        return DEVICE_PARTICLES.get(kind).get();
    }

    public static void emitRunning(Level level, BlockPos pos, BlockState state,
                                   ProcessingMachineBlock.Kind kind, RandomSource random) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.58D;
        double z = pos.getZ() + 0.5D;
        var facing = state.getValue(ProcessingMachineBlock.FACING);
        x += facing.getStepX() * 0.51D;
        z += facing.getStepZ() * 0.51D;

        double lateralX = (random.nextDouble() - 0.5D) * 0.055D;
        double lateralZ = (random.nextDouble() - 0.5D) * 0.055D;
        double rise = switch (kind.processFamily()) {
            case THERMAL, COLUMN, REACTION, CRYSTALLIZATION -> 0.025D;
            case WET_PROCESS, ELECTROCHEMICAL -> 0.012D;
            default -> 0.006D;
        };
        level.addParticle(particle(kind), x, y + random.nextDouble() * 0.16D, z, lateralX, rise, lateralZ);

        int interval = switch (kind.processFamily()) {
            case COMMINUTION, FORMING -> 24;
            case THERMAL, CLASSIFICATION -> 34;
            default -> 42;
        };
        if (random.nextInt(interval) == 0) {
            level.playLocalSound(pos, RUN_SOUNDS.get(kind).get(), SoundSource.BLOCKS,
                    0.36F, 0.96F + random.nextFloat() * 0.08F, false);
        }
    }

    public static void emitGenerator(Level level, BlockPos pos, BlockState state, RandomSource random) {
        DeviceKind kind = state.getBlock() == EarthOnMinecraft.STEAM_TURBINE_GENERATOR.get()
                ? DeviceKind.STEAM_TURBINE_GENERATOR
                : DeviceKind.COMBUSTION_GENERATOR;
        var facing = state.getValue(EnergyGeneratorBlock.FACING);
        double x = pos.getX() + 0.5D + facing.getStepX() * 0.51D;
        double y = pos.getY() + (kind == DeviceKind.STEAM_TURBINE_GENERATOR ? 0.72D : 0.64D);
        double z = pos.getZ() + 0.5D + facing.getStepZ() * 0.51D;
        double spread = kind == DeviceKind.STEAM_TURBINE_GENERATOR ? 0.025D : 0.04D;
        level.addParticle(particle(kind), x, y + random.nextDouble() * 0.12D, z,
                (random.nextDouble() - 0.5D) * spread,
                kind == DeviceKind.STEAM_TURBINE_GENERATOR ? 0.022D : 0.014D,
                (random.nextDouble() - 0.5D) * spread);
        if (random.nextInt(kind == DeviceKind.STEAM_TURBINE_GENERATOR ? 34 : 28) == 0) {
            level.playLocalSound(pos, DEVICE_RUN_SOUNDS.get(kind).get(), SoundSource.BLOCKS,
                    kind == DeviceKind.STEAM_TURBINE_GENERATOR ? 0.30F : 0.34F,
                    0.96F + random.nextFloat() * 0.06F, false);
        }
    }

    public static void playFault(Level level, BlockPos pos) {
        level.playSound(null, pos, FAULT_SOUND.get(), SoundSource.BLOCKS, 0.55F, 1.0F);
    }

    public static void playComplete(Level level, BlockPos pos, ProcessingMachineBlock.Kind kind) {
        float pitch = 0.94F + kind.ordinal() % 5 * 0.025F;
        level.playSound(null, pos, COMPLETE_SOUND.get(), SoundSource.BLOCKS, 0.42F, pitch);
    }

    public static void emitManualCrank(Level level, BlockPos pos, BlockState state, ProcessingMachineBlock.Kind kind) {
        RandomSource random = level.getRandom();
        if (kind == ProcessingMachineBlock.Kind.ANVIL) {
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS,
                    0.80F, 0.95F + random.nextFloat() * 0.10F);
        } else {
            level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS,
                    0.55F, 1.15F + random.nextFloat() * 0.10F);
            DeferredHolder<SoundEvent, SoundEvent> runSound = RUN_SOUNDS.get(kind);
            if (runSound != null) {
                level.playSound(null, pos, runSound.get(), SoundSource.BLOCKS,
                        0.32F, 1.0F + random.nextFloat() * 0.08F);
            }
        }
        if (level instanceof ServerLevel serverLevel) {
            double x = pos.getX() + 0.5D;
            double y = pos.getY() + 0.85D;
            double z = pos.getZ() + 0.5D;
            if (state.hasProperty(ProcessingMachineBlock.FACING)) {
                var facing = state.getValue(ProcessingMachineBlock.FACING);
                x += facing.getStepX() * 0.28D;
                z += facing.getStepZ() * 0.28D;
            }
            serverLevel.sendParticles(ParticleTypes.CRIT, x, y, z, 6, 0.16D, 0.10D, 0.16D, 0.08D);
            serverLevel.sendParticles(ParticleTypes.SMOKE, x, y, z, 3, 0.14D, 0.08D, 0.14D, 0.02D);
            if (kind == ProcessingMachineBlock.Kind.ANVIL) {
                serverLevel.sendParticles(ParticleTypes.LAVA, x, y + 0.1D, z, 3, 0.12D, 0.05D, 0.12D, 0.02D);
            }
            serverLevel.sendParticles(particle(kind), x, y, z, 2, 0.12D, 0.08D, 0.12D, 0.01D);
        }
    }

    public static void emitBatteryCrank(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS,
                0.60F, 1.18F + level.getRandom().nextFloat() * 0.08F);
        if (level instanceof ServerLevel serverLevel) {
            double x = pos.getX() + 0.5D;
            double y = pos.getY() + 0.85D;
            double z = pos.getZ() + 0.5D;
            serverLevel.sendParticles(ParticleTypes.CRIT, x, y, z, 5, 0.15D, 0.10D, 0.15D, 0.06D);
            serverLevel.sendParticles(ParticleTypes.SMOKE, x, y, z, 2, 0.12D, 0.06D, 0.12D, 0.01D);
        }
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(EarthOnMinecraft.id(path)));
    }

    public enum DeviceKind {
        COMBUSTION_GENERATOR("combustion_generator"),
        STEAM_TURBINE_GENERATOR("steam_turbine_generator");

        private final String id;

        DeviceKind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }
}
