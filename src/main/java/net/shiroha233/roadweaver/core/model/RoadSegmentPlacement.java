package net.shiroha233.roadweaver.core.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 道路分段放置数据（中心点 + 方块位置列表）
 */
public record RoadSegmentPlacement(BlockPos middlePos, List<BlockPos> positions) {

    public static final Codec<RoadSegmentPlacement> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BlockPos.CODEC.fieldOf("middle_pos").forGetter(RoadSegmentPlacement::middlePos),
                    BlockPos.CODEC.listOf().fieldOf("positions").forGetter(RoadSegmentPlacement::positions)
            ).apply(instance, RoadSegmentPlacement::new)
    );
}
