package com.scaffold.lowcode.generate;

import com.scaffold.common.exception.BusinessException;
import com.scaffold.lowcode.generate.GeneratedFileWriter.Written;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.scaffold.lowcode.generate.GeneratedFileWriter.Action.CREATED;
import static com.scaffold.lowcode.generate.GeneratedFileWriter.Action.OVERWRITTEN;
import static com.scaffold.lowcode.generate.GeneratedFileWriter.Action.SKIPPED;
import static com.scaffold.lowcode.generate.TemplateFixture.model;
import static com.scaffold.lowcode.generate.TemplateFixture.project;
import static com.scaffold.lowcode.generate.TemplateFixture.richFields;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * git sink 的落盘语义（PR 5E）：B 档覆盖、C 档存在即跳过。
 *
 * <p>「C 档跳过」这条是整套分档里唯一保护人工代码的机制，测的就是它会不会在第二次发布时
 * 把人写的扩展点还原成空骨架。
 */
class GeneratedFileWriterTest {

    @TempDir
    Path workspace;

    private final ModelCodeGenerator generator = new ModelCodeGenerator(new TemplateRenderer());

    @Test
    void 首次发布每个产物都新建() throws IOException {
        List<GeneratedFile> files = generator.renderFiles(project(), model(true, true), richFields());
        List<Written> results = GeneratedFileWriter.write(workspace, files);

        assertThat(results).allSatisfy(result -> assertThat(result.action()).isEqualTo(CREATED));
        assertThat(results).hasSize(files.size());
        for (GeneratedFile file : files) {
            Path target = workspace.resolve(file.path());
            assertThat(Files.readString(target, StandardCharsets.UTF_8)).isEqualTo(file.content());
            // SQL 与页面都是中文说明，写坏了编码就是交付项目里一屏问号
            assertThat(file.content()).contains("订单");
        }
        // 目录是按交付项目的真实层级建出来的，不是平铺在根上
        assertThat(workspace.resolve("scaffold-system/scaffold-business/src/main/java/com/scaffold/business/order"))
                .isDirectory();
    }

    @Test
    void 第二次发布B档覆盖C档保留人工内容() throws IOException {
        List<GeneratedFile> files = generator.renderFiles(project(), model(true, true), richFields());
        GeneratedFileWriter.write(workspace, files);

        Path extension = workspace.resolve(GeneratedPaths.extension("order", "Order"));
        String handwritten = Files.readString(extension, StandardCharsets.UTF_8)
                .replace("public void beforeCreate(Order entity) {",
                        "public void beforeCreate(Order entity) {\n        if (entity.getStatus() == 3) {\n            throw new IllegalStateException(\"已完成的订单不能再新增\");\n        }");
        Files.writeString(extension, handwritten, StandardCharsets.UTF_8);
        Path entity = workspace.resolve(GeneratedPaths.entity("order", "Order"));
        Files.writeString(entity, "// 被人手改过\n", StandardCharsets.UTF_8);

        List<Written> results = GeneratedFileWriter.write(workspace, files);

        // 人工写的业务规则原样留在盘上，平台这次没碰它
        assertThat(Files.readString(extension, StandardCharsets.UTF_8)).isEqualTo(handwritten);
        assertThat(results).filteredOn(w -> w.path().endsWith("OrderExtension.java"))
                .singleElement().satisfies(w -> assertThat(w.action()).isEqualTo(SKIPPED));
        // B 档照约定覆盖：人改的内容以模型为准被冲掉，这件事在合并请求的 diff 里看得见
        assertThat(Files.readString(entity, StandardCharsets.UTF_8)).isNotEqualTo("// 被人手改过\n")
                .contains("public class Order {");
        assertThat(results).filteredOn(w -> w.path().endsWith("domain/Order.java"))
                .singleElement().satisfies(w -> assertThat(w.action()).isEqualTo(OVERWRITTEN));
    }

    @Test
    void 越出工作区的路径停手() {
        // 分档判的是形状，落盘前还要按磁盘再判一次：这份产物是绕开 GeneratedFile.of 直接构造出来的
        GeneratedFile escape = new GeneratedFile("scaffold-system/../../outside.java", "//",
                GeneratedFile.Tier.GENERATED);
        assertThatThrownBy(() -> GeneratedFileWriter.write(workspace, List.of(escape)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("路径越出工作区");
    }

    @Test
    void 绝对路径与空清单都不落盘() {
        // resolve() 遇上绝对路径会直接丢掉工作区前缀，这一条测的就是那道 startsWith 兜没兜住
        Path outside = workspace.getParent().resolve("outside.java");
        assertThatThrownBy(() -> GeneratedFileWriter.write(workspace, List.of(
                new GeneratedFile(outside.toString(), "//", GeneratedFile.Tier.GENERATED))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("路径越出工作区");
        assertThat(outside).doesNotExist();

        // 空清单不该在磁盘上留下任何目录
        assertThat(GeneratedFileWriter.write(workspace, List.of())).isEmpty();
        assertThat(workspace.toFile().list()).isEmpty();
    }
}
