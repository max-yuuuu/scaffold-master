package com.scaffold.lowcode.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 字段类型映射表（PR 3b 验收项）：这张表就是生成物的列类型与实体属性类型的唯一事实源，
 * 改动它等于改动所有已生成项目未来发布的产物，所以逐条钉死。
 */
class FieldTypeTest {

    @Test
    void 每种类型都有对应的Java类型与列类型() {
        List<List<String>> table = List.of(
                List.of("STRING", "String", "varchar(255)", "INPUT"),
                List.of("TEXT", "String", "text", "TEXTAREA"),
                List.of("INTEGER", "Integer", "int", "NUMBER"),
                List.of("DECIMAL", "BigDecimal", "decimal(18,2)", "NUMBER"),
                List.of("DATE", "LocalDate", "date", "DATE"),
                List.of("DATETIME", "LocalDateTime", "datetime", "DATETIME"),
                List.of("ENUM", "String", "varchar(64)", "SELECT"),
                List.of("FILE", "String", "varchar(512)", "UPLOAD"),
                List.of("REF", "Long", "bigint", "REF")
        );
        for (List<String> row : table) {
            FieldType type = FieldType.of(row.get(0));
            assertThat(type.columnType(null, null, null, false)).as("%s 的默认列类型", row.get(0)).isEqualTo(row.get(2));
            assertThat(type.javaType(false)).as("%s 的默认 Java 类型", row.get(0)).isEqualTo(row.get(1));
            assertThat(type.defaultFormType().name()).as("%s 的默认控件", row.get(0)).isEqualTo(row.get(3));
        }
    }

    @Test
    void 自增主键升级为bigint与Long() {
        FieldType integer = FieldType.INTEGER;
        assertThat(integer.columnType(null, null, null, true)).isEqualTo("bigint");
        assertThat(integer.javaType(true)).isEqualTo("Long");
        // 非自增时仍是 int / Integer
        assertThat(integer.columnType(null, null, null, false)).isEqualTo("int");
        assertThat(integer.javaType(false)).isEqualTo("Integer");
    }

    @Test
    void 长度与精度参与列类型拼接() {
        assertThat(FieldType.STRING.columnType(64, null, null, false)).isEqualTo("varchar(64)");
        assertThat(FieldType.DECIMAL.columnType(null, 10, 4, false)).isEqualTo("decimal(10,4)");
        assertThat(FieldType.DECIMAL.columnType(null, null, 6, false)).isEqualTo("decimal(18,6)");
        // 字符串长度超出上限时夹住，提示改用 TEXT 而不是生成一个建不出的列
        assertThat(FieldType.STRING.effectiveLength(999_999)).isEqualTo(16_383);
        assertThat(FieldType.STRING.effectiveLength(null)).isEqualTo(255);
        assertThat(FieldType.FILE.effectiveLength(-3)).isEqualTo(512);
    }

    @Test
    void 特殊类型的约束要求() {
        assertThat(FieldType.ENUM.requiresDict()).isTrue();
        assertThat(FieldType.REF.requiresReference()).isTrue();
        assertThat(FieldType.STRING.requiresDict()).isFalse();
        assertThat(FieldType.INTEGER.requiresReference()).isFalse();
    }

    @Test
    void 未知类型直接拒绝而不是回落到String() {
        assertThatThrownBy(() -> FieldType.of("JSON")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FieldType.of(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FieldType.of("  ")).isInstanceOf(IllegalArgumentException.class);
        // 大小写与空格容错
        assertThat(FieldType.of(" datetime ")).isEqualTo(FieldType.DATETIME);
    }

    @Test
    void 查询条件与控件取值都在枚举范围内() {
        assertThat(QueryType.codes()).contains("EQ", "LIKE", "BETWEEN", "IN");
        assertThat(QueryType.of("=")).isEqualTo(QueryType.EQ);
        assertThat(QueryType.of("like")).isEqualTo(QueryType.LIKE);
        // 认不出来的运算符按等于处理，不让它变成裸字符串拼进 SQL
        assertThat(QueryType.of("DROP TABLE")).isEqualTo(QueryType.EQ);
        assertThat(QueryType.of(null)).isEqualTo(QueryType.EQ);
        assertThat(FormType.of("upload", FormType.INPUT)).isEqualTo(FormType.UPLOAD);
        assertThat(FormType.of("", FormType.INPUT)).isEqualTo(FormType.INPUT);
        assertThat(FormType.of("NOT_A_CONTROL", FormType.INPUT)).isEqualTo(FormType.INPUT);
    }

    @Test
    void 命名派生覆盖三种输入形态() {
        assertThat(Naming.snake("orderService")).isEqualTo("order_service");
        assertThat(Naming.snake("OrderService")).isEqualTo("order_service");
        assertThat(Naming.snake("order-service")).isEqualTo("order_service");
        assertThat(Naming.snake("order_service")).isEqualTo("order_service");
        assertThat(Naming.pascal("order_service")).isEqualTo("OrderService");
        assertThat(Naming.pascal("order-service")).isEqualTo("OrderService");
        assertThat(Naming.camel("order_service")).isEqualTo("orderService");
        assertThat(Naming.camel("OrderService")).isEqualTo("orderService");
        assertThat(Naming.packageSegment("Order-Service")).isEqualTo("order_service");
        assertThat(Naming.snake("Order Service")).isEqualTo("order_service");
        assertThat(Naming.snake("HTTPServer")).isEqualTo("http_server");
        assertThat(Naming.pascal("HTTPServer")).isEqualTo("HttpServer");
        assertThat(Naming.snake("")).isEmpty();
        assertThat(Naming.pascal(null)).isEmpty();
        // 幂等：对已派生过的值再算一次不变
        assertThat(Naming.snake(Naming.snake("orderService"))).isEqualTo("order_service");
        assertThat(Naming.pascal(Naming.pascal("order_service"))).isEqualTo("OrderService");
    }
}
