/* 文件职责：注册道路收尾 Feature；NeoForge 通过 biome modifier 把它加到主世界生物群系的表层阶段。 */
package net.shiroha233.roadweaver.features.config;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.features.path.PathFeature;
import net.shiroha233.roadweaver.features.path.config.PathFeatureConfig;

/**
 * NeoForge Feature 注册
 * The placed feature is added to overworld biomes by data/roadweaver/neoforge/biome_modifier/road_feature.json.
 */
public final class RoadFeatureRegistry {
    private RoadFeatureRegistry() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterEvent.class, event -> event.register(Registries.FEATURE,
                Identifier.fromNamespaceAndPath(RoadWeaver.MOD_ID, "road_feature"),
                () -> new PathFeature(PathFeatureConfig.CODEC)));
    }
}
