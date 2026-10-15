package net.shiroha233.roadweaver.config.structure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.shiroha233.roadweaver.persistence.files.FileStorageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 结构选择配置，管理用户启用/禁用的结构和标签，持久化到独立配置文件
 */
public final class StructureSelectionConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("RoadWeaver/StructureSelection");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "structure_selection.json";

    private static StructureSelectionConfig INSTANCE = null;

    private final Set<String> enabledStructures = new LinkedHashSet<>();
    private final Set<String> enabledTags = new LinkedHashSet<>();
    private boolean useTagMode = true;

    private StructureSelectionConfig() {}

    public static synchronized StructureSelectionConfig get() {
        if (INSTANCE == null) {
            INSTANCE = new StructureSelectionConfig();
            INSTANCE.load();
        }
        return INSTANCE;
    }

    public static synchronized void reload() {
        INSTANCE = new StructureSelectionConfig();
        INSTANCE.load();
    }

    // ==================== 结构操作 ====================

    public boolean isStructureEnabled(String structureId) {
        if (structureId == null) return false;
        String normalized = structureId.toLowerCase(Locale.ROOT);
        if (enabledStructures.contains(normalized)) return true;

        if (useTagMode && !enabledTags.isEmpty()) {
            StructureDiscoveryService.DiscoveryResult result = StructureDiscoveryService.getResult();
            if (result != null) {
                for (String tagId : enabledTags) {
                    if (result.isStructureInTag(normalized, tagId)) return true;
                }
            }
        }
        return false;
    }

    public void enableStructure(String structureId) {
        if (structureId != null) enabledStructures.add(structureId.toLowerCase(Locale.ROOT));
    }

    public void disableStructure(String structureId) {
        if (structureId != null) enabledStructures.remove(structureId.toLowerCase(Locale.ROOT));
    }

    public void toggleStructure(String structureId) {
        if (structureId == null) return;
        String normalized = structureId.toLowerCase(Locale.ROOT);
        if (enabledStructures.contains(normalized)) {
            enabledStructures.remove(normalized);
        } else {
            enabledStructures.add(normalized);
        }
    }

    // ==================== 标签操作 ====================

    public boolean isTagEnabled(String tagId) {
        if (tagId == null) return false;
        return enabledTags.contains(tagId.toLowerCase(Locale.ROOT));
    }

    public void enableTag(String tagId) {
        if (tagId == null) return;
        String normalized = tagId.toLowerCase(Locale.ROOT);
        enabledTags.add(normalized);
        if (!useTagMode) {
            StructureDiscoveryService.DiscoveryResult result = StructureDiscoveryService.getResult();
            if (result != null) enabledStructures.addAll(result.getStructuresInTag(normalized));
        }
    }

    public void disableTag(String tagId) {
        if (tagId == null) return;
        String normalized = tagId.toLowerCase(Locale.ROOT);
        enabledTags.remove(normalized);
        if (!useTagMode) {
            StructureDiscoveryService.DiscoveryResult result = StructureDiscoveryService.getResult();
            if (result != null) enabledStructures.removeAll(result.getStructuresInTag(normalized));
        }
    }

    public void toggleTag(String tagId) {
        if (tagId == null) return;
        if (enabledTags.contains(tagId.toLowerCase(Locale.ROOT))) {
            disableTag(tagId);
        } else {
            enableTag(tagId);
        }
    }

    // ==================== 批量操作 ====================

    public void enableDefaultVillages() { enableTag("minecraft:village"); }

    public void clearAll() {
        enabledStructures.clear();
        enabledTags.clear();
    }

    public void enableAll() {
        StructureDiscoveryService.DiscoveryResult result = StructureDiscoveryService.getResult();
        if (result != null) {
            for (StructureEntry entry : result.allStructures()) {
                enabledStructures.add(entry.id().toString().toLowerCase(Locale.ROOT));
            }
            for (StructureTagEntry tag : result.tags()) {
                enabledTags.add(tag.tagId().toString().toLowerCase(Locale.ROOT));
            }
        }
    }

    // ==================== 查询 ====================

    public Set<String> getEnabledStructures() {
        Set<String> result = new LinkedHashSet<>(enabledStructures);
        if (useTagMode && !enabledTags.isEmpty()) {
            StructureDiscoveryService.DiscoveryResult discovery = StructureDiscoveryService.getResult();
            if (discovery != null) {
                for (String tagId : enabledTags) {
                    result.addAll(discovery.getStructuresInTag(tagId));
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public Set<String> getEnabledTags() { return Collections.unmodifiableSet(enabledTags); }

    /** 转换为白名单格式（兼容旧系统），标签带 # 前缀 */
    public List<String> toWhitelist() {
        List<String> result = new ArrayList<>();
        for (String tagId : enabledTags) result.add("#" + tagId);

        Set<String> taggedStructures = new HashSet<>();
        StructureDiscoveryService.DiscoveryResult discovery = StructureDiscoveryService.getResult();
        if (discovery != null) {
            for (String tagId : enabledTags) taggedStructures.addAll(discovery.getStructuresInTag(tagId));
        }
        for (String structId : enabledStructures) {
            if (!taggedStructures.contains(structId)) result.add(structId);
        }
        return result;
    }

    public boolean hasAnySelection() { return !enabledStructures.isEmpty() || !enabledTags.isEmpty(); }

    // ==================== 持久化 ====================

    private Path getConfigFilePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("roadweaver").resolve(CONFIG_FILE);
    }

    public void save() {
        try {
            Path file = getConfigFilePath();
            Files.createDirectories(file.getParent());
            ConfigData data = new ConfigData();
            data.enabledStructures = new ArrayList<>(enabledStructures);
            data.enabledTags = new ArrayList<>(enabledTags);
            data.useTagMode = useTagMode;
            FileStorageIO.writeStringAtomic(file, GSON.toJson(data));
        } catch (Exception e) {
            LOGGER.warn("结构选择配置保存失败", e);
        }
    }

    private void load() {
        Path file = getConfigFilePath();
        if (!Files.exists(file)) {
            enableDefaultVillages();
            save();
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            if (data != null) {
                if (data.enabledStructures != null) enabledStructures.addAll(data.enabledStructures);
                if (data.enabledTags != null) enabledTags.addAll(data.enabledTags);
                useTagMode = data.useTagMode;
            }
        } catch (Exception e) {
            LOGGER.warn("结构选择配置加载失败，使用默认值", e);
            enableDefaultVillages();
        }
    }

    private static class ConfigData {
        List<String> enabledStructures = new ArrayList<>();
        List<String> enabledTags = new ArrayList<>();
        boolean useTagMode = true;
    }
}
