package net.shiroha233.roadweaver.core.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/**
 * 结构连接（起点 → 终点 + 状态）
 */
public record StructureConnection(BlockPos from, BlockPos to, ConnectionStatus status) {

    public static final Codec<StructureConnection> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BlockPos.CODEC.fieldOf("from").forGetter(StructureConnection::from),
                    BlockPos.CODEC.fieldOf("to").forGetter(StructureConnection::to),
                    Codec.STRING.optionalFieldOf("status", "PLANNED")
                            .xmap(ConnectionStatus::valueOf, Enum::name)
                            .forGetter(StructureConnection::status)
            ).apply(instance, StructureConnection::new)
    );

    public StructureConnection(BlockPos from, BlockPos to) {
        this(from, to, ConnectionStatus.PLANNED);
    }
}
