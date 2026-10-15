package net.shiroha233.roadweaver.core.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 道路完整数据（宽度、类型、材料、分段、跨度、目标高度）
 */
public record RoadData(
        int width,
        int roadType,
        List<BlockState> materials,
        List<BlockState> slabMaterials,
        List<RoadSegmentPlacement> roadSegmentList,
        List<RoadSpan> spans,
        List<Integer> targetY,
        long ownerA2dKey,
        long ownerB2dKey
) {
    public static final long NO_OWNER_2D = Long.MIN_VALUE;

    public static final Codec<RoadData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("width").forGetter(RoadData::width),
                    Codec.INT.fieldOf("road_type").forGetter(RoadData::roadType),
                    BlockState.CODEC.listOf().fieldOf("materials").forGetter(RoadData::materials),
                    BlockState.CODEC.listOf().optionalFieldOf("slab_materials", new ArrayList<>()).forGetter(RoadData::slabMaterials),
                    RoadSegmentPlacement.CODEC.listOf().fieldOf("placements").forGetter(RoadData::roadSegmentList),
                    RoadSpan.CODEC.listOf().optionalFieldOf("spans", new ArrayList<>()).forGetter(RoadData::spans),
                    Codec.INT.listOf().optionalFieldOf("target_y", new ArrayList<>()).forGetter(RoadData::targetY),
                    Codec.LONG.optionalFieldOf("owner_a_2d", NO_OWNER_2D).forGetter(RoadData::ownerA2dKey),
                    Codec.LONG.optionalFieldOf("owner_b_2d", NO_OWNER_2D).forGetter(RoadData::ownerB2dKey)
            ).apply(instance, RoadData::new)
    );

    public boolean hasOwnerPair() {
        return ownerA2dKey != NO_OWNER_2D && ownerB2dKey != NO_OWNER_2D;
    }

    public boolean containsOwner(long owner2dKey) {
        return hasOwnerPair() && (ownerA2dKey == owner2dKey || ownerB2dKey == owner2dKey);
    }

    public long sharedOwnerWith(RoadData other) {
        if (other == null || !hasOwnerPair() || !other.hasOwnerPair()) return NO_OWNER_2D;
        if (other.containsOwner(ownerA2dKey)) return ownerA2dKey;
        if (other.containsOwner(ownerB2dKey)) return ownerB2dKey;
        return NO_OWNER_2D;
    }
}
