package net.shiroha233.roadweaver.map.permission;

import com.mojang.serialization.Codec;

public enum MapAccessMode {
    ALL_PLAYERS("all"),
    OPS_ONLY("ops_only");

    public static final Codec<MapAccessMode> CODEC = Codec.STRING.xmap(MapAccessMode::fromSerializedName, MapAccessMode::serializedName);

    private final String serializedName;

    MapAccessMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static MapAccessMode fromSerializedName(String name) {
        for (MapAccessMode mode : values()) {
            if (mode.serializedName.equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return ALL_PLAYERS;
    }
}
