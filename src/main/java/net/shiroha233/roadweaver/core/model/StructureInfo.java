package net.shiroha233.roadweaver.core.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/**
 * 单个结构位置与类型标识
 */
public record StructureInfo(BlockPos pos, String structureId) {
    public static final Codec<StructureInfo> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BlockPos.CODEC.fieldOf("pos").forGetter(StructureInfo::pos),
                    Codec.STRING.optionalFieldOf("structure_id", "unknown").forGetter(StructureInfo::structureId)
            ).apply(instance, StructureInfo::new)
    );

    public static boolean isKnownId(String structureId) {
        return structureId != null && !structureId.isBlank() && !"unknown".equalsIgnoreCase(structureId.trim());
    }
}
