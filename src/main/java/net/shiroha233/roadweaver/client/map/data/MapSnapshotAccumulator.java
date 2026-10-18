package net.shiroha233.roadweaver.client.map.data;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.map.search.MapStructureSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 地图快照增量合并器。
 */
public final class MapSnapshotAccumulator {
    private final LinkedHashMap<Long, BlockPos> structuresByPos = new LinkedHashMap<>();
    private final LinkedHashMap<Long, String> structureNamesByPos = new LinkedHashMap<>();
    private final LinkedHashMap<Long, Integer> structureSourcesByPos = new LinkedHashMap<>();
    private final LinkedHashMap<ConnectionKey, StructureConnection> connectionsByKey = new LinkedHashMap<>();
    private final LinkedHashMap<Long, List<BlockPos>> roadsByKey = new LinkedHashMap<>();

    public void merge(MapSnapshot snapshot) {
        if (snapshot == null) return;
        mergeStructures(snapshot);
        mergeConnections(snapshot.connections());
        mergeRoads(snapshot.roadPolylines());
    }

    public MapSnapshot snapshot() {
        ArrayList<BlockPos> structures = new ArrayList<>(structuresByPos.values());
        ArrayList<StructureConnection> connections = new ArrayList<>(connectionsByKey.values());
        ArrayList<StructureInfo> infos = new ArrayList<>(structureNamesByPos.size());
        java.util.Map<BlockPos, Integer> sources = new java.util.HashMap<>();
        for (Map.Entry<Long, String> entry : structureNamesByPos.entrySet()) {
            BlockPos pos = structuresByPos.get(entry.getKey());
            if (pos != null && entry.getValue() != null && !entry.getValue().isBlank()) {
                infos.add(new StructureInfo(pos, entry.getValue()));
            }
        }
        for (Map.Entry<Long, BlockPos> entry : structuresByPos.entrySet()) {
            Integer source = structureSourcesByPos.get(entry.getKey());
            if (source != null) sources.put(entry.getValue(), source);
        }
        ArrayList<List<BlockPos>> roads = new ArrayList<>(roadsByKey.values());
        return new MapSnapshot(structures, connections, infos, roads, sources);
    }

    private void mergeStructures(MapSnapshot snapshot) {
        for (BlockPos pos : snapshot.structures()) {
            if (pos == null) continue;
            long key = posKey(pos);
            structuresByPos.putIfAbsent(key, normalize(pos));
            structureSourcesByPos.putIfAbsent(key, snapshot.structureSource(pos));
            String name = snapshot.structureName(pos);
            mergeStructureName(key, name);
        }
    }

    private void mergeStructureName(long key, String name) {
        if (name == null || name.isBlank()) return;
        String previous = structureNamesByPos.get(key);
        if (previous == null || previous.isBlank()
                || (!StructureInfo.isKnownId(previous) && StructureInfo.isKnownId(name))) {
            structureNamesByPos.put(key, name);
        }
    }

    private void mergeConnections(List<StructureConnection> connections) {
        if (connections == null || connections.isEmpty()) return;
        for (StructureConnection connection : connections) {
            if (connection == null || connection.from() == null || connection.to() == null) continue;
            ConnectionKey key = ConnectionKey.of(connection);
            StructureConnection normalized = normalize(connection);
            StructureConnection previous = connectionsByKey.get(key);
            if (previous == null || statusPriority(normalized.status()) >= statusPriority(previous.status())) {
                connectionsByKey.put(key, normalized);
            }
        }
    }

    private void mergeRoads(List<List<BlockPos>> roads) {
        if (roads == null || roads.isEmpty()) return;
        for (List<BlockPos> road : roads) {
            if (road == null || road.size() < 2) continue;
            ArrayList<BlockPos> normalized = new ArrayList<>(road.size());
            for (BlockPos pos : road) {
                if (pos != null) normalized.add(normalize(pos));
            }
            if (normalized.size() < 2) continue;
            roadsByKey.putIfAbsent(roadKey(normalized), List.copyOf(normalized));
        }
    }

    private static BlockPos normalize(BlockPos pos) {
        return new BlockPos(pos.getX(), 0, pos.getZ());
    }

    private static StructureConnection normalize(StructureConnection connection) {
        return new StructureConnection(normalize(connection.from()), normalize(connection.to()), connection.status());
    }

    private static long posKey(BlockPos pos) {
        return (((long) pos.getX()) << 32) ^ (pos.getZ() & 0xffffffffL);
    }

    private static long roadKey(List<BlockPos> road) {
        long hash = 0xcbf29ce484222325L;
        for (BlockPos pos : road) {
            hash ^= pos.getX();
            hash *= 0x100000001b3L;
            hash ^= pos.getZ();
            hash *= 0x100000001b3L;
        }
        hash ^= road.size();
        hash *= 0x100000001b3L;
        return hash;
    }

    private static int statusPriority(ConnectionStatus status) {
        if (status == null) return 0;
        return switch (status) {
            case COMPLETED -> 4;
            case GENERATING -> 3;
            case PLANNED -> 2;
            case FAILED -> 1;
        };
    }

    private record ConnectionKey(long a, long b) {
        private static ConnectionKey of(StructureConnection connection) {
            long from = posKey(connection.from());
            long to = posKey(connection.to());
            return from <= to ? new ConnectionKey(from, to) : new ConnectionKey(to, from);
        }
    }
}
