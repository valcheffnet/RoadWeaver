package net.shiroha233.roadweaver.config;

import java.util.ArrayList;
import java.util.List;

/**
 * 道路预设配置数据容器
 */
public final class PresetConfig {
    public List<List<String>> materials;

    public PresetConfig() {
        this.materials = new ArrayList<>();
    }

    public static PresetConfig defaults() {
        PresetConfig cfg = new PresetConfig();
        cfg.materials.add(List.of("minecraft:mud_bricks", "minecraft:packed_mud"));
        cfg.materials.add(List.of("minecraft:polished_andesite", "minecraft:stone_bricks"));
        cfg.materials.add(List.of("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks"));
        return cfg;
    }

    public void sanitize() {
        if (materials == null) materials = new ArrayList<>();
        if (materials.isEmpty()) materials.add(List.of("minecraft:stone_bricks"));
    }
}
