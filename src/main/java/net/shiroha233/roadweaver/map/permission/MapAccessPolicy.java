package net.shiroha233.roadweaver.map.permission;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public record MapAccessPolicy(MapAccessMode mode, Set<UUID> allowedPlayers) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(
            value -> {
                try {
                    return DataResult.success(UUID.fromString(value));
                } catch (IllegalArgumentException ex) {
                    return DataResult.error(() -> "Invalid UUID: " + value);
                }
            },
            UUID::toString
    );
    private static final Codec<Set<UUID>> UUID_SET_CODEC = Codec.list(UUID_CODEC)
            .xmap(LinkedHashSet::new, set -> new ArrayList<>(set));
    public static final Codec<MapAccessPolicy> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MapAccessMode.CODEC.optionalFieldOf("mode", MapAccessMode.ALL_PLAYERS).forGetter(MapAccessPolicy::mode),
            UUID_SET_CODEC.optionalFieldOf("allowed_players", Set.of()).forGetter(MapAccessPolicy::allowedPlayers)
    ).apply(instance, MapAccessPolicy::new));

    public static final MapAccessPolicy DEFAULT = new MapAccessPolicy(MapAccessMode.ALL_PLAYERS, Set.of());

    public MapAccessPolicy {
        mode = mode == null ? MapAccessMode.ALL_PLAYERS : mode;
        allowedPlayers = Collections.unmodifiableSet(new LinkedHashSet<>(allowedPlayers == null ? Set.of() : allowedPlayers));
    }

    public boolean isExplicitlyAllowed(UUID playerId) {
        return playerId != null && allowedPlayers.contains(playerId);
    }

    public MapAccessPolicy withMode(MapAccessMode nextMode) {
        if (nextMode == null || nextMode == mode) {
            return this;
        }
        return new MapAccessPolicy(nextMode, allowedPlayers);
    }

    public MapAccessPolicy withGranted(UUID playerId) {
        if (playerId == null || allowedPlayers.contains(playerId)) {
            return this;
        }
        LinkedHashSet<UUID> updated = new LinkedHashSet<>(allowedPlayers);
        updated.add(playerId);
        return new MapAccessPolicy(mode, updated);
    }

    public MapAccessPolicy withRevoked(UUID playerId) {
        if (playerId == null || !allowedPlayers.contains(playerId)) {
            return this;
        }
        LinkedHashSet<UUID> updated = new LinkedHashSet<>(allowedPlayers);
        updated.remove(playerId);
        return new MapAccessPolicy(mode, updated);
    }
}
