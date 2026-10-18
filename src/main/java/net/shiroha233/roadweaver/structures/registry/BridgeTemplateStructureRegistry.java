package net.shiroha233.roadweaver.structures.registry;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.shiroha233.roadweaver.structures.types.BridgeTemplateStructure;

import java.util.*;

/**
 * 桥模板结构注册中心。
 */
public final class BridgeTemplateStructureRegistry {

    private BridgeTemplateStructureRegistry() {
    }

    private static volatile List<BridgeTemplate> cache;

    public static class BridgeTemplate {
        private final Identifier id;
        private final Holder<Structure> holder;
        private final BridgeTemplateStructure structure;
        private BlockState[][][] voxelGrid = new BlockState[0][0][0];
        private Vec3i size = new Vec3i(0, 0, 0);

        public BridgeTemplate(Identifier id, Holder<Structure> holder, BridgeTemplateStructure structure,
                ServerLevel level) {
            this.id = id;
            this.holder = holder;
            this.structure = structure;

            if (level != null) {
                StructureTemplate template = level.getStructureManager().get(structure.templateId).orElse(null);
                if (template == null) {
                    return;
                }
                size = template.getSize();
                voxelGrid = new BlockState[size.getX()][size.getY()][size.getZ()];

                CompoundTag tag = template.save(new CompoundTag());
                ListTag palette = tag.getListOrEmpty("palette");
                ListTag blocks = tag.getListOrEmpty("blocks");

                parseVoxel(palette, blocks);
            }
        }

        public boolean isInVoxelGrid(double x, double y, double z) {
            int originalX = (int) Math.floor(x);
            int originalY = (int) Math.floor(y + structure.heightOffset + 1);
            int originalZ = (int) Math.floor(z + 0.01 + size.getZ() / 2.0);

            return originalX >= 0 && originalX < size.getX() && originalY < size.getY() && originalZ >= 0
                    && originalZ < size.getZ();
        }

        public BlockState getBlock(double x, double y, double z) {
            int originalX = (int) Math.floor(x);
            int originalY = (int) Math.floor(y + structure.heightOffset + 1);
            int originalZ = (int) Math.floor(z + 0.01 + size.getZ() / 2.0);

            if (originalX < 0 || originalX >= size.getX() || originalY >= size.getY() || originalZ < 0
                    || originalZ >= size.getZ()) {
                return null;
            }

            if (originalY < 0) {
                originalY = 0;
            }

            return voxelGrid[originalX][originalY][originalZ];
        }

        public int getStartLength() {
            return structure.bridgeDeckStart;
        }

        public int getEndLength() {
            return size.getX() - structure.bridgeDeckEnd;
        }

        public int getDeckLength() {
            return structure.bridgeDeckEnd - structure.bridgeDeckStart + 1;
        }

        public int getTotalLength() {
            return size.getX();
        }

        public Identifier getId() {
            return id;
        }

        public Holder<Structure> getHolder() {
            return holder;
        }

        public BridgeTemplateStructure getStructure() {
            return structure;
        }

        private void parseVoxel(ListTag paletteTag, ListTag blocksTag) {
            List<BlockState> palette = new ArrayList<>();

            for (int i = 0; i < paletteTag.size(); i++) {
                CompoundTag blockTag = paletteTag.getCompoundOrEmpty(i);
                BlockState blockState = readBlockState(blockTag);
                palette.add(blockState);
            }

            for (int i = 0; i < blocksTag.size(); i++) {
                CompoundTag blockTag = blocksTag.getCompoundOrEmpty(i);

                ListTag pos = blockTag.getListOrEmpty("pos");
                int x = pos.getIntOr(0, -1);
                int y = pos.getIntOr(1, -1);
                int z = pos.getIntOr(2, -1);

                int state = blockTag.getIntOr("state", 0);

                if (x >= 0 && x < size.getX() && y >= 0 && y < size.getY() && z >= 0 && z < size.getZ()) {
                    voxelGrid[x][y][z] = palette.get(state);
                }
            }
        }

        public static BlockState readBlockState(CompoundTag pTag) {
            return NbtUtils.readBlockState(BuiltInRegistries.BLOCK, pTag);
        }
    }

    public static List<BridgeTemplate> getAll(ServerLevel level) {
        if (level == null || !net.minecraft.world.level.Level.OVERWORLD.equals(level.dimension())) return List.of();
        List<BridgeTemplate> current = cache;
        if (current != null) return current;
        synchronized (BridgeTemplateStructureRegistry.class) {
            if (cache == null) cache = List.copyOf(loadFromRegistry(level));
            return cache;
        }
    }

    public static BridgeTemplate choose(ServerLevel level, int seed) {
        return choose(level, seed, null);
    }

    public static BridgeTemplate choose(ServerLevel level, int seed, Holder<Biome> biome) {
        List<BridgeTemplate> all = getAll(level);
        if (all == null || all.isEmpty()) {
            return null;
        }

        List<BridgeTemplate> candidates = filterByBiome(all, biome);
        if (candidates.isEmpty()) {
            return null;
        }

        return chooseFromList(candidates, seed);
    }

    private static List<BridgeTemplate> filterByBiome(List<BridgeTemplate> all, Holder<Biome> biome) {
        if (biome == null) {
            return all;
        }
        List<BridgeTemplate> result = new ArrayList<>();
        for (BridgeTemplate template : all) {
            if (template.structure.biomes().contains(biome)) {
                result.add(template);
            }
        }
        return result;
    }

    private static BridgeTemplate chooseFromList(List<BridgeTemplate> candidates, int seed) {
        Random random = new Random(seed * 10000L + 2025 + 1225);
        int randomIndex = random.nextInt(candidates.size());

        candidates.sort(Comparator.comparingInt(o -> o.id.getPath().hashCode()));

        return candidates.get(randomIndex);
    }

    private static List<BridgeTemplate> loadFromRegistry(ServerLevel level) {
        List<BridgeTemplate> result = new ArrayList<>();
        RegistryAccess registryAccess = level.registryAccess();

        Registry<Structure> structureRegistry = registryAccess.lookupOrThrow(Registries.STRUCTURE);

        for (var entry : structureRegistry.entrySet()) {
            Identifier id = entry.getKey().identifier();
            Structure structure = entry.getValue();

            if (structure instanceof BridgeTemplateStructure roadsideStructure) {
                Holder<Structure> holder = structureRegistry.getOrThrow(entry.getKey());
                result.add(new BridgeTemplate(id, holder, roadsideStructure, level));
            }
        }

        return result;
    }

    public static void clearCache() {
        cache = null;
    }
}
