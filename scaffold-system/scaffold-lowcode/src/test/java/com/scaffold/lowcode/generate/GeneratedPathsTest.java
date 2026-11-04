package com.scaffold.lowcode.generate;

import com.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 落点表与分档判定的回归测试（PR 5E）。
 *
 * <p>盯的是「哪个文件页面发布可以写、写下去算哪一档」。这条路只有两种错法：
 * 该覆盖的没覆盖（生成物漂在磁盘上不动），或不该碰的碰了（把人工维护的 A 档底座文件冲掉）。
 * 后者是方案 §九 列的高危项，所以判档用白名单形状匹配，认不出来的一律拒。
 */
class GeneratedPathsTest {

    @Test
    void 每个构造出来的路径都认得出档位() {
        assertThat(GeneratedPaths.tier(GeneratedPaths.entity("order", "Order")))
                .isEqualTo(GeneratedFile.Tier.GENERATED);
        assertThat(GeneratedPaths.tier(GeneratedPaths.writeRequest("order", "Order")))
                .isEqualTo(GeneratedFile.Tier.GENERATED);
        assertThat(GeneratedPaths.tier(GeneratedPaths.row("order", "Order")))
                .isEqualTo(GeneratedFile.Tier.GENERATED);
        assertThat(GeneratedPaths.tier(GeneratedPaths.mapper("order", "Order")))
                .isEqualTo(GeneratedFile.Tier.GENERATED);
        assertThat(GeneratedPaths.tier(GeneratedPaths.service("order", "Order")))
                .isEqualTo(GeneratedFile.Tier.GENERATED);
        assertThat(GeneratedPaths.tier(GeneratedPaths.controller("order", "Order")))
                .isEqualTo(GeneratedFile.Tier.GENERATED);
        assertThat(GeneratedPaths.tier(GeneratedPaths.apiTs("order", "order")))
                .isEqualTo(GeneratedFile.Tier.GENERATED);
        assertThat(GeneratedPaths.tier(GeneratedPaths.listView("order", "Order")))
                .isEqualTo(GeneratedFile.Tier.GENERATED);
        assertThat(GeneratedPaths.tier(GeneratedPaths.tableSql("biz_order")))
                .isEqualTo(GeneratedFile.Tier.GENERATED);
        assertThat(GeneratedPaths.tier(GeneratedPaths.menuSql("biz_order")))
                .isEqualTo(GeneratedFile.Tier.GENERATED);
        // C 档：后端扩展点与前端人写区
        assertThat(GeneratedPaths.tier(GeneratedPaths.extension("order", "Order")))
                .isEqualTo(GeneratedFile.Tier.EXTENSION);
        assertThat(GeneratedPaths.tier("scaffold-vue/src/views/order/overrides/OrderDetail.vue"))
                .isEqualTo(GeneratedFile.Tier.EXTENSION);
    }

    @Test
    void 后端产物落在交付项目的业务模块里() {
        assertThat(GeneratedPaths.entity("order", "Order")).isEqualTo(
                "scaffold-system/scaffold-business/src/main/java/com/scaffold/business/order/domain/Order.java");
        assertThat(GeneratedPaths.extension("order_item", "OrderItem")).isEqualTo(
                "scaffold-system/scaffold-business/src/main/java/com/scaffold/business/order_item/service/OrderItemExtension.java");
        assertThat(GeneratedPaths.listView("order", "Order"))
                .isEqualTo("scaffold-vue/src/views/order/Order.vue");
        assertThat(GeneratedPaths.apiTs("order", "order"))
                .isEqualTo("scaffold-vue/src/api/order/order.ts");
        // SQL 进子目录：容器的 mysql 初始化只认 sql/ 顶层的 V*.sql，生成的语句不会被自动执行
        assertThat(GeneratedPaths.tableSql("biz_order"))
                .isEqualTo("scaffold-system/sql/pages/biz_order_table.sql");
    }

    @Test
    void A档与认不出的路径一律拒写() {
        // A 档：pom、app 壳、物料、样式、Dockerfile —— 模板里拼错一段路径就撞在这些文件上
        for (String path : new String[]{
                "scaffold-system/pom.xml",
                "scaffold-system/scaffold-business/pom.xml",
                "scaffold-vue/src/api/index.ts",
                "scaffold-vue/src/api/system.ts",
                "scaffold-vue/src/components/layout/AppSidebar.vue",
                "scaffold-vue/src/main.ts",
                "scaffold-vue/src/views/Dashboard.vue",
                "packages/scaffold-ui/theme.css",
                "Dockerfile",
                "docker-compose.yml"}) {
            assertThatThrownBy(() -> GeneratedPaths.tier(path))
                    .as(path)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("生成器无权写这个路径");
        }
    }

    @Test
    void 越界与不规矩的路径进不了产物清单() {
        assertThatThrownBy(() -> GeneratedPaths.tier(
                "scaffold-system/scaffold-business/src/main/java/com/scaffold/business/../../pom.xml"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> GeneratedPaths.tier(
                "/scaffold-system/scaffold-business/src/main/java/com/scaffold/business/order/domain/Order.java"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> GeneratedPaths.tier(null)).isInstanceOf(BusinessException.class);
        // 类名必须是大驼峰开头：小写说明路径是拼出来的而不是派生出来的，形状对了也不认
        assertThatThrownBy(() -> GeneratedPaths.tier(
                "scaffold-system/scaffold-business/src/main/java/com/scaffold/business/order/domain/order.java"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 扩展点认档优先于同目录的B档形状() {
        // {Cls}Extension.java 也落在 service/ 下，判档顺序错了它就会在下次发布时被覆盖掉
        assertThat(GeneratedPaths.tier(GeneratedPaths.extension("order", "Order")))
                .isEqualTo(GeneratedFile.Tier.EXTENSION);
        assertThat(GeneratedPaths.tier(GeneratedPaths.service("order", "Order")))
                .isEqualTo(GeneratedFile.Tier.GENERATED);
    }
}
