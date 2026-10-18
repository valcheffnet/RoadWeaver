package net.shiroha233.roadweaver.config.structure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 结构发现服务，从服务端注册表收集结构和标签，缓存供客户端 GUI 使用
 */
public final class StructureDiscoveryService {
    private static final Logger LOGGER = LoggerFactory.getLogger("RoadWeaver/StructureDiscovery");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CACHE_FILE = "structure_cache_overworld.json";

    private static volatile DiscoveryResult cachedResult = null;
    private static volatile boolean hasDiscovered = false;

    private StructureDiscoveryService() {}

    /** 发现结果 */
    public static final class DiscoveryResult {
        private final List<StructureTagEntry> tags;
        private final List<StructureEntry> allStructures;
        private final Map<String, Set<String>> tagToStructures;

        public DiscoveryResult(List<StructureTagEntry> tags, List<StructureEntry> allStructures) {
            this.tags = new ArrayList<>(tags);
            this.allStructures = new ArrayList<>(allStructures);
            Collections.sort(this.tags);
            Collections.sort(this.allStructures);
            this.tagToStructures = new ConcurrentHashMap<>();
            for (StructureTagEntry tag : tags) {
                tagToStructures.put(tag.tagId().toString(), tag.getAllStructureIds());
            }
        }

        public List<StructureTagEntry> tags() { return Collections.unmodifiableList(tags); }
        public List<StructureEntry> allStructures() { return Collections.unmodifiableList(allStructures); }

        public Set<String> getStructuresInTag(String tagId) {
            return tagToStructures.getOrDefault(tagId, Set.of());
        }

        public boolean isStructureInTag(String structureId, String tagId) {
            Set<String> structures = tagToStructures.get(tagId);
            return structures != null && structures.contains(structureId);
        }
    }

    /** 从服务端世界收集结构和标签 */
    public static void discoverFromLevel(ServerLevel level) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) {
            LOGGER.warn("无法发现结构: 仅支持主世界");
            return;
        }
        RegistryAccess access = level.registryAccess();
        discoverFromRegistries(
                access.lookupOrThrow(Registries.STRUCTURE),
                access.lookupOrThrow(Registries.LEVEL_STEM));
    }

    /** 从注册表收集结构和标签 */
    public static void discoverFromRegistries(Registry<Structure> structureRegistry, Registry<LevelStem> levelStemRegistry) {
        if (structureRegistry == null || levelStemRegistry == null) return;
        try {
            LevelStem overworldStem = levelStemRegistry.getValue(LevelStem.OVERWORLD);
            if (overworldStem == null) {
                LOGGER.warn("无法发现结构: 缺少主世界维度数据");
                return;
            }
            Set<Holder<Biome>> overworldBiomes;
            try {
                overworldBiomes = overworldStem.generator().getBiomeSource().possibleBiomes();
            } catch (Exception ignored) {
                overworldBiomes = Set.of();
            }
            discoverFromStructures(structureRegistry, overworldBiomes);
        } catch (Exception e) {
            LOGGER.error("结构发现失败", e);
        }
    }

    /** Collects the structures that can generate in the given overworld biomes, and their tags. */
    public static void discoverFromStructures(Registry<Structure> structureRegistry, Set<Holder<Biome>> overworldBiomes) {
        if (structureRegistry == null || overworldBiomes == null) return;
        try {
            Map<Identifier, StructureEntry> structureMap = new LinkedHashMap<>();
            for (var entry : structureRegistry.entrySet()) {
                Identifier id = entry.getKey().identifier();
                Structure structure = entry.getValue();
                boolean isVanilla = "minecraft".equals(id.getNamespace());
                String displayName = formatDisplayName(id);

                try {
                    if (structure.biomes().stream().noneMatch(overworldBiomes::contains)) continue;
                } catch (Exception ignored) {
                    continue;
                }
                structureMap.put(id, new StructureEntry(id, displayName, isVanilla));
            }

            List<StructureTagEntry> tagEntries = collectTags(structureRegistry, structureMap);
            cachedResult = new DiscoveryResult(tagEntries, new ArrayList<>(structureMap.values()));
            hasDiscovered = true;
            LOGGER.info("发现 {} 个结构和 {} 个标签", structureMap.size(), tagEntries.size());
            saveCacheToFile();
        } catch (Exception e) {
            LOGGER.error("结构发现失败", e);
        }
    }

    /** 从 RegistryAccess 收集结构和标签（兼容客户端创建世界界面） */
    public static void discoverFromRegistryAccess(RegistryAccess registryAccess) {
        if (registryAccess == null) {
            LOGGER.warn("无法发现结构: registryAccess 为 null");
            return;
        }
        // A client-side world has no dimension registry; the cached result from the server or world creation is used then.
        if (registryAccess.lookup(Registries.LEVEL_STEM).isEmpty()) return;
        try {
            Registry<Structure> structureRegistry = registryAccess.lookupOrThrow(Registries.STRUCTURE);
            Registry<LevelStem> levelStemRegistry = registryAccess.lookupOrThrow(Registries.LEVEL_STEM);
            discoverFromRegistries(structureRegistry, levelStemRegistry);
        } catch (Exception e) {
            LOGGER.error("结构发现失败", e);
        }
    }

    /** 获取缓存的发现结果，必要时从文件加载 */
    public static DiscoveryResult getResult() {
        if (cachedResult == null && !hasDiscovered) {
            tryDiscoverFromCurrentContext();
        }
        if (cachedResult == null && !hasDiscovered) {
            loadCacheFromFile();
        }
        return cachedResult;
    }

    /** Registry of the world the client has loaded; set by the client entrypoint, empty on a dedicated server. */
    private static volatile Supplier<RegistryAccess> clientRegistryAccess = () -> null;

    public static void setClientRegistryAccess(Supplier<RegistryAccess> supplier) {
        clientRegistryAccess = supplier;
    }

    public static void tryDiscoverFromCurrentContext() {
        try {
            RegistryAccess access = clientRegistryAccess.get();
            if (access != null) {
                discoverFromRegistryAccess(access);
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean hasResult() { return getResult() != null; }

    public static void clearCache() {
        cachedResult = null;
        hasDiscovered = false;
    }


    // ==================== 内部工具方法 ====================

    private static List<StructureTagEntry> collectTags(Registry<Structure> registry, Map<Identifier, StructureEntry> structureMap) {
        List<StructureTagEntry> tagEntries = new ArrayList<>();
        Set<Identifier> processedTags = new HashSet<>();
        for (Holder.Reference<Structure> holder : registry.listElements().toList()) {
            holder.tags().forEach(tagKey -> {
                Identifier tagId = tagKey.location();
                if (processedTags.contains(tagId)) return;
                processedTags.add(tagId);

                List<StructureEntry> tagStructures = new ArrayList<>();
                for (Holder.Reference<Structure> h : registry.listElements().toList()) {
                    if (h.is(tagKey)) {
                        StructureEntry se = structureMap.get(h.key().identifier());
                        if (se != null) tagStructures.add(se);
                    }
                }
                if (!tagStructures.isEmpty()) {
                    tagEntries.add(new StructureTagEntry(tagId, formatTagDisplayName(tagId), tagStructures));
                }
            });
        }
        return tagEntries;
    }

    private static String formatDisplayName(Identifier id) {
        String[] parts = id.getPath().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private static String formatTagDisplayName(Identifier tagId) {
        return "#" + formatDisplayName(tagId);
    }

    // ==================== 缓存文件操作 ====================

    private static Path getCacheFilePath() {
        return FMLPaths.CONFIGDIR.get().resolve("roadweaver").resolve(CACHE_FILE);
    }

    private static void saveCacheToFile() {
        if (cachedResult == null) return;
        try {
            Path file = getCacheFilePath();
            Files.createDirectories(file.getParent());
            CacheData data = new CacheData();
            data.structures = cachedResult.allStructures().stream()
                    .map(e -> new CacheData.StructureData(e.id().toString(), e.displayName(), e.isVanilla()))
                    .collect(Collectors.toList());
            data.tags = cachedResult.tags().stream()
                    .map(t -> new CacheData.TagData(t.tagId().toString(), t.displayName(),
                            t.structures().stream().map(s -> s.id().toString()).collect(Collectors.toList())))
                    .collect(Collectors.toList());
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            LOGGER.warn("结构缓存保存失败", e);
        }
    }

    private static void loadCacheFromFile() {
        Path file = getCacheFilePath();
        if (!Files.exists(file)) return;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            CacheData data = GSON.fromJson(reader, CacheData.class);
            if (data == null || data.structures == null || data.tags == null) {
                LOGGER.warn("结构缓存文件无效");
                return;
            }

            Map<String, StructureEntry> structureMap = new LinkedHashMap<>();
            for (CacheData.StructureData sd : data.structures) {
                Identifier id = Identifier.tryParse(sd.id);
                if (id != null) {
                    structureMap.put(sd.id, new StructureEntry(id, sd.displayName, sd.isVanilla));
                }
            }

            List<StructureTagEntry> tagEntries = new ArrayList<>();
            for (CacheData.TagData td : data.tags) {
                Identifier tagId = Identifier.tryParse(td.tagId);
                if (tagId == null) continue;
                List<StructureEntry> tagStructures = new ArrayList<>();
                for (String structId : td.structureIds) {
                    StructureEntry se = structureMap.get(structId);
                    if (se != null) tagStructures.add(se);
                }
                if (!tagStructures.isEmpty()) {
                    tagEntries.add(new StructureTagEntry(tagId, td.displayName, tagStructures));
                }
            }

            cachedResult = new DiscoveryResult(tagEntries, new ArrayList<>(structureMap.values()));
            LOGGER.info("从缓存加载 {} 个结构和 {} 个标签", structureMap.size(), tagEntries.size());
        } catch (Exception e) {
            LOGGER.warn("结构缓存加载失败", e);
        }
    }

    /** 缓存数据格式 */
    private static class CacheData {
        List<StructureData> structures;
        List<TagData> tags;

        static class StructureData {
            String id;
            String displayName;
            boolean isVanilla;
            StructureData(String id, String displayName, boolean isVanilla) {
                this.id = id; this.displayName = displayName; this.isVanilla = isVanilla;
            }
        }

        static class TagData {
            String tagId;
            String displayName;
            List<String> structureIds;
            TagData(String tagId, String displayName, List<String> structureIds) {
                this.tagId = tagId; this.displayName = displayName; this.structureIds = structureIds;
            }
        }
    }
}
