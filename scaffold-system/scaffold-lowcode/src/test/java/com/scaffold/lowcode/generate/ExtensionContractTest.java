package com.scaffold.lowcode.generate;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.scaffold.lowcode.generate.TemplateFixture.model;
import static com.scaffold.lowcode.generate.TemplateFixture.richFields;
import static com.scaffold.lowcode.generate.TemplateFixture.simpleFields;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * C 档扩展点骨架与 B 档调用点的对接（PR 5E）。
 *
 * <p>这两个文件是分档设计里唯一互相咬合的地方：骨架是给人改的、调用点是每次覆盖的，
 * 方法名和参数一边变了另一边就编译不过。所以断言写成「Service 里出现的每个 {@code extension.xxx(...)}
 * 都能在骨架里找到同名同参的声明」，而不是逐个抄一遍签名。
 */
class ExtensionContractTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void 骨架是C档形状且不写着被覆盖() {
        Map<String, Object> root = GenerationInput.of(model(true, true), richFields());
        String code = renderer.render("backend/Extension.java.ftl", root);

        assertThat(code).startsWith("package com.scaffold.business.order.service;");
        assertThat(code).contains("@Component").contains("public class OrderExtension {");
        assertThat(code).contains("本类属 <b>C 档</b>").contains("方法签名是平台契约");
        // 这句是 B 档文件的标记，出现在这里等于告诉读代码的人「这文件也会被覆盖」，是反的
        assertThat(code).doesNotContain("每次发布整文件覆盖");
    }

    @Test
    void Service调用的每个钩子骨架里都有对应声明() {
        Map<String, Object> root = GenerationInput.of(model(true, true), richFields());
        String service = renderer.render("backend/Service.java.ftl", root);
        String extension = renderer.render("backend/Extension.java.ftl", root);

        for (String hook : service.lines()
                .map(line -> line.trim())
                .filter(line -> line.startsWith("extension."))
                .map(line -> line.substring("extension.".length(), line.indexOf('(')))
                .distinct()
                .toList()) {
            assertThat(extension).as("钩子 " + hook).contains("public void " + hook + "(");
        }
        // 四个写操作 + 列表/导出共用一个行钩子，一个都不能少
        assertThat(service).contains("extension.afterRows(rows)")
                .contains("extension.beforeCreate(entity)")
                .contains("extension.afterCreate(entity)")
                .contains("extension.beforeUpdate(id, entity)")
                .contains("extension.afterUpdate(id, entity)")
                .contains("extension.beforeDelete(id)")
                .contains("extension.afterDelete(id)");
    }

    @Test
    void Service构造器把扩展点当成依赖() {
        String service = renderer.render("backend/Service.java.ftl",
                GenerationInput.of(model(true, true), richFields()));

        assertThat(service).contains("private final OrderExtension extension;")
                .contains("public OrderService(OrderMapper mapper, OrderExtension extension) {")
                .contains("this.extension = extension;");
        // 扩展点与 Service 同包，不需要 import；出现 import 说明包名漂了
        assertThat(service).doesNotContain("import com.scaffold.business.order.service.OrderExtension;");
    }

    @Test
    void 导出与列表走同一个行钩子() {
        // 骨架里 afterRows 的说明写了「列表与导出」，两条路都真的调用才算话没白写
        String service = renderer.render("backend/Service.java.ftl",
                GenerationInput.of(model(false, false), simpleFields()));
        assertThat(service.lines().filter(line -> line.contains("extension.afterRows(rows)")).count())
                .isEqualTo(2);
    }
}
