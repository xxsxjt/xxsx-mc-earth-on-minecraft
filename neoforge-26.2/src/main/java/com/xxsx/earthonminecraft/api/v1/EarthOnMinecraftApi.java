package com.xxsx.earthonminecraft.api.v1;

public final class EarthOnMinecraftApi {
    public static final int MAJOR_VERSION = 1;
    public static final int MINOR_VERSION = 1;

    private EarthOnMinecraftApi() {
    }

    public static ApiVersion version() {
        return new ApiVersion(MAJOR_VERSION, MINOR_VERSION);
    }

    public static GeologyQueryApi geology() {
        return ApiServices.GEOLOGY;
    }

    public static MaterialPropertyApi materials() {
        return ApiServices.MATERIALS;
    }

    public static MachineProcessingApi processing() {
        return ApiServices.PROCESSING;
    }

    public static EnergyApi energy() {
        return ApiServices.ENERGY;
    }

    public static LogisticsApi logistics() {
        return ApiServices.LOGISTICS;
    }

    public static SettlementQueryApi settlements() {
        return ApiServices.SETTLEMENTS;
    }

    public record ApiVersion(int major, int minor) {
        @Override
        public String toString() {
            return major + "." + minor;
        }
    }
}
