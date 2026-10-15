package net.shiroha233.roadweaver.core.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构位置集合，兼容旧版（仅位置列表）和新版（含类型信息）格式
 */
public record StructureLocationData(List<BlockPos> structureLocations, List<StructureInfo> structureInfos) {

    public StructureLocationData(List<BlockPos> structureLocations, List<StructureInfo> structureInfos) {
        this.structureLocations = new ArrayList<>(structureLocations != null ? structureLocations : new ArrayList<>());
        this.structureInfos = new ArrayList<>(structureInfos != null ? structureInfos : new ArrayList<>());
    }

    public StructureLocationData(List<BlockPos> structureLocations) {
        this(structureLocations, new ArrayList<>());
    }

    public void addStructure(BlockPos pos) {
        structureLocations.add(pos);
    }

    public void addStructureInfo(StructureInfo info) {
        structureInfos.add(info);
        if (!structureLocations.contains(info.pos())) {
            structureLocations.add(info.pos());
        }
    }

    public static final Codec<StructureLocationData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BlockPos.CODEC.listOf().optionalFieldOf("structure_locations", new ArrayList<>()).forGetter(StructureLocationData::structureLocations),
                    StructureInfo.CODEC.listOf().optionalFieldOf("structure_infos", new ArrayList<>()).forGetter(StructureLocationData::structureInfos)
            ).apply(instance, StructureLocationData::new)
    );
}
