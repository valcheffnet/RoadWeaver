/* 文件职责：在 Fabric 植被 Feature 入口复用道路占用查询并缓存特征分类。 */
package net.shiroha233.roadweaver.mixin.fabric;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用植物 Feature 拦截器
 * In 26.x ConfiguredFeature is gone; every placed feature reaches Feature.place through FeaturePlacer, so the
 * per-position call is wrapped there. Classification is cached per feature class.
 */
@Mixin(FeaturePlacer.class)
public class GenericVegetationFeatureMixin {
    @Unique
    private static final Map<Class<?>, Boolean> roadweaver$TREE_LIKE = new ConcurrentHashMap<>();

    @WrapOperation(
            method = "place(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/feature/Feature;place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean roadweaver$blockTreesOnRoad(Feature feature, WorldGenLevel level, ChunkGenerator generator,
            RandomSource random, BlockPos pos, Operation<Boolean> original) {
        try {
            if (roadweaver$isTreeLikeFeature(feature) && RoadPositionQuery.isOnRoad(level, pos)) {
                return false;
            }
        } catch (Throwable ignored) {}
        return original.call(feature, level, generator, random, pos);
    }

    /**
     * 判断是否为树木类 Feature
     */
    @Unique
    private static boolean roadweaver$isTreeLikeFeature(Feature feature) {
        if (feature == null) return false;
        return roadweaver$TREE_LIKE.computeIfAbsent(feature.getClass(), cls -> {
            String name = cls.getName().toLowerCase(Locale.ROOT);
            // 排除已有专门 Mixin 的原版类（避免重复检查）
            return !roadweaver$isVanillaTreeFeature(name) && roadweaver$containsTreeKeyword(name);
        });
    }

    /**
     * 判断是否为已有专门 Mixin 的原版树木类
     */
    @Unique
    private static boolean roadweaver$isVanillaTreeFeature(String className) {
        return className.equals("net.minecraft.world.level.levelgen.feature.treefeature") ||
               className.equals("net.minecraft.world.level.levelgen.feature.hugefungusfeature") ||
               className.equals("net.minecraft.world.level.levelgen.feature.bamboofeature");
    }

    @Unique
    private static boolean roadweaver$containsTreeKeyword(String name) {
        return name.contains("tree") ||
               name.contains("fungus") ||
               name.contains("mushroom") ||
               name.contains("bamboo") ||
               name.contains("cactus") ||
               name.contains("chorus") ||
               name.contains("nbt") ||
               name.contains("structure") ||
               name.contains("fromstructure") ||
               name.contains("template") ||
               name.contains("bush");
    }
}
