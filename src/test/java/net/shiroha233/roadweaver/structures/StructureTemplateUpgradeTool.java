/* 文件职责：一次性工具，用原版 DataFixer 将数据包结构模板升级到当前 DataVersion。 */
package net.shiroha233.roadweaver.structures;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Path;

/**
 * Rewrites the datapack templates the same way vanilla loads them (TemplateSource.readStructure): data version
 * (default 500) -> DataFixTypes.STRUCTURE.updateToCurrentVersion -> StructureTemplate.load, then saves the
 * template, which writes the current format and DataVersion.
 * Run: ./gradlew test --tests '*StructureTemplateUpgradeTool' -Droadweaver.upgradeTemplates=true
 */
class StructureTemplateUpgradeTool {
    @Test
    @EnabledIfSystemProperty(named = "roadweaver.upgradeTemplates", matches = "true")
    void upgradeTemplates() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        int current = SharedConstants.getCurrentVersion().dataVersion().version();
        for (Path path : StructureTemplateFiles.all()) {
            CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            int version = NbtUtils.getDataVersion(tag, 500);
            if (version == current) {
                continue;
            }
            CompoundTag fixed = DataFixTypes.STRUCTURE.updateToCurrentVersion(DataFixers.getDataFixer(), tag, version);
            StructureTemplate template = new StructureTemplate();
            template.load(BuiltInRegistries.BLOCK, fixed);
            NbtIo.writeCompressed(template.save(new CompoundTag()), path);
            System.out.println("upgraded " + path + " " + version + " -> " + current);
        }
    }
}
