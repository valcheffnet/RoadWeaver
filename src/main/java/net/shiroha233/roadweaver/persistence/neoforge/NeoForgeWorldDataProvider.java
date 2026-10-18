package net.shiroha233.roadweaver.persistence.neoforge;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureLocationData;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.files.StructureFileStorage;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NeoForge 平台世界数据提供者实现
 */
public class NeoForgeWorldDataProvider extends WorldDataProvider {

    @Override
    public StructureLocationData getStructureLocations(ServerLevel level) {
        return StructureFileStorage.getStructureLocations(level);
    }

    @Override
    public void setStructureLocations(ServerLevel level, StructureLocationData data) {
        StructureFileStorage.setStructureLocations(level, data);
    }

    @Override
    public List<StructureConnection> getStructureConnections(ServerLevel level) {
        return StructureFileStorage.getStructureConnections(level);
    }

    @Override
    public void setStructureConnections(ServerLevel level, List<StructureConnection> connections) {
        StructureFileStorage.setStructureConnections(level, connections);
    }

    @Override
    public Set<Long> getPlannedTileKeys(ServerLevel level) {
        return StructureFileStorage.getPlannedTileKeys(level);
    }

    @Override
    public void setPlannedTileKeys(ServerLevel level, Set<Long> keys) {
        StructureFileStorage.setPlannedTileKeys(level, keys);
    }

    @Override
    public Map<Long, Long> getPlannedTileCenters(ServerLevel level) {
        return StructureFileStorage.getPlannedTileCenters(level);
    }

    @Override
    public void setPlannedTileCenters(ServerLevel level, Map<Long, Long> centers) {
        StructureFileStorage.setPlannedTileCenters(level, centers);
    }


    }
