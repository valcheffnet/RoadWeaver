package net.shiroha233.roadweaver.persistence.attachments;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.map.permission.MapAccessPolicy;

import java.util.function.Supplier;

/**
 * NeoForge 世界数据附件注册
 * Everything else RoadWeaver keeps per world is in its own files; only the map access policy is stored on the level.
 */
public final class WorldDataAttachment {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, RoadWeaver.MOD_ID);

    public static final Supplier<AttachmentType<MapAccessPolicy>> MAP_ACCESS_POLICY = ATTACHMENT_TYPES.register(
            "map_access_policy",
            () -> AttachmentType.builder(() -> MapAccessPolicy.DEFAULT)
                    .serialize(MapAccessPolicy.CODEC.fieldOf("policy"))
                    .build());

    private WorldDataAttachment() {}

    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }
}
