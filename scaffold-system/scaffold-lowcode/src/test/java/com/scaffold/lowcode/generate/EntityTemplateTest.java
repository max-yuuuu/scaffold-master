package com.scaffold.lowcode.generate;

import com.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模板渲染骨架与实体模板的回归测试（PR 5A）。
 *
 * <p>断言盯的是「产物像不像人手写的底座代码」：包名进 business 子包、每个字段带中文与列信息注释、
 * 访问器与 {@code scaffold-system} 现有 POJO 同款一行式、审计四字段按开关出现。
 * 同一份模型两次渲染必须逐字节一致，否则发布 diff 里会出现无意义改动。
 */
class EntityTemplateTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void 实体模板产出与底座同款的POJO形状() {
        Map<String, Object> model = GenerationInput.of(project(), model(true), fields());
        String code = renderer.render("backend/Entity.java.ftl", model);

        assertThat(code).contains("package com.scaffold.business.order.domain;");
        // import 排序固定：BigDecimal 在前，审计开关带进来的 LocalDateTime 在后
        assertThat(code).contains("import java.math.BigDecimal;\nimport java.time.LocalDateTime;\n");
        assertThat(code).contains("public class Order {");
        assertThat(code).contains("/** 订单号（列 {@code order_no}，类型 {@code varchar(64)}） */");
        assertThat(code).contains("private BigDecimal amount;");
        assertThat(code).contains("public String getOrderNo() { return orderNo; }");
        assertThat(code).contains("public void setAmount(BigDecimal amount) { this.amount = amount; }");
        // B 档提示：告诉读代码的人别在这里写业务逻辑
        assertThat(code).contains("每次发布整文件覆盖");
        assertThat(code).contains("OrderExtension");
    }

    @Test
    void 审计开关决定四字段是否出现() {
        assertThat(renderer.render("backend/Entity.java.ftl", GenerationInput.of(project(), model(true), fields())))
                .contains("private LocalDateTime createTime;")
                .contains("public String getUpdateBy() { return updateBy; }");
        assertThat(renderer.render("backend/Entity.java.ftl", GenerationInput.of(project(), model(false), fields())))
                .doesNotContain("private String createBy;")
                .doesNotContain("getUpdateTime");
    }

    @Test
    void 同一份模型两次渲染逐字节一致() {
        String first = renderer.render("backend/Entity.java.ftl", GenerationInput.of(project(), model(true), fields()));
        String second = renderer.render("backend/Entity.java.ftl", GenerationInput.of(project(), model(true), fields()));
        assertThat(second).isEqualTo(first);
    }

    @Test
    void 命名与派生值由Java侧算完() {
        Map<String, Object> model = GenerationInput.of(project(), model(true), fields());
        assertThat(model).containsEntry("module", "order")
                .containsEntry("className", "Order")
                .containsEntry("resourceName", "order")
                .containsEntry("tableName", "biz_order")
                .containsEntry("permissionPrefix", "order:order")
                .containsEntry("apiPath", "/order/order");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) model.get("fields");
        Map<String, Object> status = fields.stream().filter(f -> "status".equals(f.get("code"))).findFirst().orElseThrow();
        // decimal(18,2) 由拆开的三段合成；查询运算符只认 QueryType 枚举，错值回落等于
        assertThat(status).containsEntry("columnType", "int")
                .containsEntry("queryOperator", "=")
                .containsEntry("dictType", "sys_order_status");
        Map<String, Object> amount = fields.stream().filter(f -> "amount".equals(f.get("code"))).findFirst().orElseThrow();
        assertThat(amount).containsEntry("columnType", "decimal(18,2)").containsEntry("javaType", "BigDecimal");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queryFields = (List<Map<String, Object>>) model.get("queryFields");
        assertThat(queryFields).extracting(f -> f.get("code")).contains("status");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> formFields = (List<Map<String, Object>>) model.get("formFields");
        assertThat(formFields).extracting(f -> f.get("code")).contains("amount", "remark").doesNotContain("id");
    }

    @Test
    void 模板名写错时报出可操作的原因() {
        assertThatThrownBy(() -> renderer.render("backend/NoSuchThing.ftl", Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板渲染失败")
                .hasMessageContaining("backend/NoSuchThing.ftl");
    }

    private static Map<String, Object> project() {
        return Map.of("projectCode", "order-service", "projectName", "订单中台");
    }

    private static Map<String, Object> model(boolean audit) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("modelCode", "order");
        model.put("modelName", "订单");
        model.put("modelDesc", "订单主表");
        model.put("tableName", "biz_order");
        model.put("className", "Order");
        model.put("moduleName", "order");
        model.put("isTree", 0);
        model.put("enableLogicDelete", 1);
        model.put("enableDataScope", 0);
        model.put("enableAuditField", audit ? 1 : 0);
        return model;
    }

    private static List<Map<String, Object>> fields() {
        return List.of(
                field("id", "主键", "INTEGER", "Long", "bigint", 1, 1, 0, 0),
                field("order_no", "订单号", "STRING", "String", "varchar", 0, 0, 1, 1),
                field("amount", "订单金额", "DECIMAL", "BigDecimal", "decimal", 0, 0, 1, 1),
                field("status", "状态", "INTEGER", "Integer", "int", 0, 0, 1, 1),
                field("remark", "备注", "TEXT", "String", "text", 0, 0, 0, 1));
    }

    private static Map<String, Object> field(String code, String name, String type, String javaType, String columnType,
                                             int primary, int autoIncrement, int list, int edit) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("fieldCode", code);
        field.put("fieldName", name);
        field.put("fieldType", type);
        field.put("javaType", javaType);
        field.put("columnName", code);
        field.put("columnType", columnType);
        if ("varchar".equals(columnType)) {
            field.put("columnLength", "order_no".equals(code) ? 64 : 255);
        }
        if ("decimal".equals(columnType)) {
            field.put("columnPrecision", 18);
            field.put("columnScale", 2);
        }
        field.put("isPrimary", primary);
        field.put("isAutoIncrement", autoIncrement);
        field.put("isNullable", primary == 1 ? 0 : 1);
        field.put("isUnique", "order_no".equals(code) ? 1 : 0);
        field.put("isRequired", primary == 1 ? 1 : 0);
        field.put("isList", list);
        field.put("isQuery", "status".equals(code) ? 1 : 0);
        field.put("queryType", "status".equals(code) ? "EQ" : null);
        field.put("isInsert", edit);
        field.put("isEdit", edit);
        field.put("formType", "DECIMAL".equals(type) ? "NUMBER" : "TEXT".equals(type) ? "TEXTAREA" : "INPUT");
        field.put("dictType", "status".equals(code) ? "sys_order_status" : null);
        field.put("sortOrder", 0);
        return field;
    }
}
