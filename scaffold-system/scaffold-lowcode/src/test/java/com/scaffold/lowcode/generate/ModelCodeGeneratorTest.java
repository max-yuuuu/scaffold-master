package com.scaffold.lowcode.generate;

import com.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.scaffold.lowcode.generate.TemplateFixture.model;
import static com.scaffold.lowcode.generate.TemplateFixture.richFields;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 一个模型的产物清单（PR 5E）。
 *
 * <p>这份清单就是发布链路看到的「这次要往项目里写什么」，所以断言盯三件事：
 * 文件齐不齐、落点对不对、档位是不是按 §5.2 分对了。档位错一个，人工写的扩展点就会被下一次发布冲掉。
 */
class ModelCodeGeneratorTest {

    private final ModelCodeGenerator generator = new ModelCodeGenerator(new TemplateRenderer());

    @Test
    void 一个模型出十一份产物只有一份是C档() {
        List<GeneratedFile> files = generator.renderFiles(model(true, true), richFields());

        assertThat(files).extracting(GeneratedFile::path).containsExactly(
                "scaffold-system/scaffold-business/src/main/java/com/scaffold/business/order/domain/Order.java",
                "scaffold-system/scaffold-business/src/main/java/com/scaffold/business/order/domain/OrderWriteRequest.java",
                "scaffold-system/scaffold-business/src/main/java/com/scaffold/business/order/domain/OrderRow.java",
                "scaffold-system/scaffold-business/src/main/java/com/scaffold/business/order/mapper/OrderMapper.java",
                "scaffold-system/scaffold-business/src/main/java/com/scaffold/business/order/service/OrderService.java",
                "scaffold-system/scaffold-business/src/main/java/com/scaffold/business/order/service/OrderExtension.java",
                "scaffold-system/scaffold-business/src/main/java/com/scaffold/business/order/controller/OrderController.java",
                "scaffold-vue/src/api/order/order.ts",
                "scaffold-vue/src/views/order/Order.vue",
                "scaffold-system/sql/pages/biz_order_table.sql",
                "scaffold-system/sql/pages/biz_order_menu.sql");
        assertThat(files).allSatisfy(file -> assertThat(file.content()).isNotBlank());

        // C 档只有扩展点一份：它是唯一一份「下次发布不覆盖」的产物
        assertThat(files).filteredOn(file -> file.tier() == GeneratedFile.Tier.EXTENSION)
                .extracting(GeneratedFile::path)
                .containsExactly("scaffold-system/scaffold-business/src/main/java/com/scaffold/business/order/service/OrderExtension.java");
    }

    @Test
    void 路径跟着模型的模块名资源名与表名走() {
        Map<String, Object> model = new LinkedHashMap<>(model(true, true));
        model.put("modelCode", "order_item");
        model.put("className", "OrderItem");
        model.put("moduleName", "order_item");
        model.put("tableName", "biz_order_item");

        List<GeneratedFile> files = generator.renderFiles(model, richFields());

        assertThat(files).extracting(GeneratedFile::path).allSatisfy(path -> {
            assertThat(path).matches("^(scaffold-system/scaffold-business/src/main/java/com/scaffold/business/order_item/"
                    + "(domain|mapper|service|controller)/[A-Za-z]+\\.java"
                    + "|scaffold-vue/src/api/order_item/order_item\\.ts"
                    + "|scaffold-vue/src/views/order_item/OrderItem\\.vue"
                    + "|scaffold-system/sql/pages/biz_order_item_(table|menu)\\.sql)$");
        });
    }

    @Test
    void 页面引用的接口路径就是接口文件的实际落点() {
        // 单字词根看不出来：resourceName 是驼峰、文件名是下划线，两个词以上才分家。
        // 引用写错一个分隔符，交付项目在 vue-tsc 阶段就报「找不到模块 @/api/...」，页面整块起不来。
        Map<String, Object> model = new LinkedHashMap<>(model(true, true));
        model.put("modelCode", "purchase_order");
        model.put("className", "PurchaseOrder");
        model.put("moduleName", "order");
        model.put("tableName", "biz_purchase_order");

        List<GeneratedFile> files = generator.renderFiles(model, richFields());

        assertThat(files).extracting(GeneratedFile::path)
                .contains("scaffold-vue/src/api/order/purchase_order.ts");
        assertThat(contentOf(files, "scaffold-vue/src/views/order/PurchaseOrder.vue"))
                .contains("from '@/api/order/purchase_order'");
    }

    @Test
    void 同一份模型两次生成逐字节一致() {
        // 发布 diff 的可读性建立在这条上：内容不稳定，每次合并请求都是整文件重写
        List<GeneratedFile> first = generator.renderFiles(model(true, true), richFields());
        List<GeneratedFile> second = generator.renderFiles(model(true, true), richFields());

        assertThat(second).isEqualTo(first);
    }

    @Test
    void 没实现的模型开关在生成前就拦掉() {
        Map<String, Object> tree = new LinkedHashMap<>(model(true, true));
        tree.put("isTree", 1);
        assertThatThrownBy(() -> generator.renderFiles(tree, richFields()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("勾了树表")
                .hasMessageContaining("C 档扩展点");

        Map<String, Object> scope = new LinkedHashMap<>(model(true, true));
        scope.put("enableDataScope", 1);
        assertThatThrownBy(() -> generator.renderFiles(scope, richFields()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("勾了数据权限");
    }

    @Test
    void 非数字主键的模型拒绝生成而不是产出一份对不上的接口() {
        // 五个入口的定位参数写的是 @PathVariable Long id（与底座同一形状），
        // 主键是字符串时这份产物编得过但调不通：路径上拿到的是编号，Service 却按 Long 查。当场拦掉更省事
        assertThatThrownBy(() -> generator.renderFiles(model(true, true), TemplateFixture.stringKeyFields()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("主键");
    }

    @Test
    void 产物里不出现平台品牌也不引用交付里没有的方案文档() {
        // 生成物是客户项目里的源码：注释与页面文案带着「元搭低代码平台」，就等于在客户的项目里
        // 宣传平台自己；「见方案 §5.2」这类引用更糟 —— 那份文档属平台，裁剪清单里不进交付项目，
        // 读者按图索骥只会翻到一个不存在的文件
        List<GeneratedFile> files = generator.renderFiles(model(true, true), richFields());

        assertThat(files).allSatisfy(file -> {
            String content = file.content();
            assertThat(content).as("产物 " + file.path()).doesNotContain("元搭");
            assertThat(content).as("产物 " + file.path() + " 引用了交付里读不到的方案文档").doesNotContain("方案 §");
        });
        // 页面顶部那句说明是给项目里的人看的，要说清「这页是生成的、要改回设计器改模型」
        assertThat(contentOf(files, "scaffold-vue/src/views/order/Order.vue")).contains("低代码设计器");
    }

    @Test
    void 产物的档位是从路径反推出来的而不是模板自述() {
        // GeneratedFile.of 是唯一入口：模板把路径拼错就当场拒绝，错不到磁盘上
        List<String> paths = new ArrayList<>(generator.renderFiles(model(true, true), richFields())
                .stream().map(GeneratedFile::path).toList());
        paths.add("scaffold-vue/src/main.ts");

        assertThatThrownBy(() -> GeneratedFile.of(paths.get(paths.size() - 1), "// 内容"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("生成器无权写这个路径");
    }

    private static String contentOf(List<GeneratedFile> files, String path) {
        return files.stream().filter(file -> path.equals(file.path())).findFirst()
                .orElseThrow(() -> new AssertionError("产物清单里没有 " + path)).content();
    }
}
