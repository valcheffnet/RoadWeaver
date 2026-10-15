package net.shiroha233.roadweaver.core.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/**
 * 道路跨度区间（起点、终点、类型）
 */
public record RoadSpan(BlockPos start, BlockPos end, SpanType type) {

    public static final Codec<RoadSpan> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BlockPos.CODEC.fieldOf("start").forGetter(RoadSpan::start),
                    BlockPos.CODEC.fieldOf("end").forGetter(RoadSpan::end),
                    Codec.STRING.fieldOf("type")
                            .xmap(SpanType::valueOf, Enum::name)
                            .forGetter(RoadSpan::type)
            ).apply(instance, RoadSpan::new)
    );
}
