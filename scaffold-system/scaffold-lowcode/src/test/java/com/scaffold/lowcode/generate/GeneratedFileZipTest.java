package com.scaffold.lowcode.generate;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.scaffold.lowcode.generate.TemplateFixture.model;
import static com.scaffold.lowcode.generate.TemplateFixture.project;
import static com.scaffold.lowcode.generate.TemplateFixture.richFields;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * zip sink（PR 5E）：同一份产物清单换一条出口。
 *
 * <p>方案 §4.4 的口径是「生成逻辑不动，只换 sink」，所以这里验的是两条 sink 吃同一份
 * {@link GeneratedFile} 列表时内容一致，而 zip 侧目录形状与交付项目一致。
 */
class GeneratedFileZipTest {

    private final ModelCodeGenerator generator = new ModelCodeGenerator(new TemplateRenderer());

    @Test
    void 压缩包里每个文件都能原样读回() throws IOException {
        List<GeneratedFile> files = generator.renderFiles(project(), model(true, true), richFields());

        Map<String, String> entries = unzip(GeneratedFileZip.pack(files));

        assertThat(entries.keySet()).containsExactly(files.stream().map(GeneratedFile::path).toArray(String[]::new));
        for (GeneratedFile file : files) {
            assertThat(entries.get(file.path())).isEqualTo(file.content());
        }
    }

    @Test
    void 压缩包里的目录就是交付项目的目录() throws IOException {
        List<GeneratedFile> files = generator.renderFiles(project(), model(true, true), richFields());

        Map<String, String> entries = unzip(GeneratedFileZip.pack(files));

        // 解压到项目根即落位：包里没有 backend/ frontend/ 这层平台自己想象的目录
        assertThat(entries.keySet()).allSatisfy(path -> assertThat(path).startsWith("scaffold-"));
        assertThat(entries).containsKey("scaffold-system/scaffold-business/src/main/java/com/scaffold/business/order/service/OrderExtension.java");
    }

    /** entry 名 → 内容。顺带按读入顺序保留，用于断言 zip 里的顺序与产物清单一致 */
    private Map<String, String> unzip(byte[] zip) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                in.transferTo(out);
                entries.put(entry.getName(), out.toString(StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    @Test
    void 空的产物清单打出一个没有文件的压缩包() throws IOException {
        // 没有产物时不该报错，也不该在包里塞占位文件；只剩一份 22 字节的结束记录是正常的
        byte[] zip = GeneratedFileZip.pack(List.of());
        assertThat(zip).isNotEmpty();
        assertThat(unzip(zip)).isEmpty();
    }
}
