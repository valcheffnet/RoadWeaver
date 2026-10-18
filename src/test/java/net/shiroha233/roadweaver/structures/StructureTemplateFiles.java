/* 文件职责：列出模组数据包中的结构模板文件，供模板版本测试与升级工具共用。 */
package net.shiroha233.roadweaver.structures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

final class StructureTemplateFiles {
    // The NeoForge test runner starts in its own game folder, so the project folder comes in as a property.
    static final Path ROOT = Path.of(System.getProperty("roadweaver.projectDir", "."))
            .resolve("src/main/resources/data/roadweaver/structure");

    private StructureTemplateFiles() {}

    static List<Path> all() throws IOException {
        try (Stream<Path> files = Files.walk(ROOT)) {
            return files.filter(p -> p.toString().endsWith(".nbt")).sorted().toList();
        }
    }
}
