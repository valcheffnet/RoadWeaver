/* 文件职责：确保数据包结构模板已升级到当前游戏的 DataVersion，避免每次加载时执行 DataFixer。 */
package net.shiroha233.roadweaver.structures;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StructureTemplateDataVersionTest {
    @Test
    void templatesAreAtCurrentDataVersion() throws Exception {
        SharedConstants.tryDetectVersion();
        int current = SharedConstants.getCurrentVersion().dataVersion().version();
        List<Path> templates = StructureTemplateFiles.all();
        assertFalse(templates.isEmpty(), "no templates found under " + StructureTemplateFiles.ROOT);
        for (Path template : templates) {
            int version = NbtUtils.getDataVersion(NbtIo.readCompressed(template, NbtAccounter.unlimitedHeap()), -1);
            assertEquals(current, version, template + " needs StructureTemplateUpgradeTool");
        }
    }
}
