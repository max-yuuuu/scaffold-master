package com.scaffold.lowcode.generate;

import com.scaffold.lowcode.model.Naming;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模板测试的共用夹具：一份「四种查询方式、唯一列、必填、日期列都用上」的订单模型，
 * 加一份只剩主键与名称的最小模型，用来走各种「没有」的分支。
 *
 * <p>形状断言与编译演练两个测试都吃这一份，避免两处对同一个模型的理解漂开。
 */
final class TemplateFixture {

    /** 后端全套模板，两个测试按同一份清单逐个跑 */
    static final List<String> BACKEND_TEMPLATES = List.of("backend/Entity.java.ftl", "backend/Row.java.ftl",
            "backend/WriteRequest.java.ftl", "backend/Mapper.java.ftl", "backend/Service.java.ftl",
            "backend/Controller.java.ftl");

    private TemplateFixture() {
    }

    static Map<String, Object> project() {
        return Map.of("projectCode", "order-service", "projectName", "订单中台");
    }

    /**
     * @param audit       模型的「审计四字段」开关
     * @param logicDelete 模型的「逻辑删除」开关
     */
    static Map<String, Object> model(boolean audit, boolean logicDelete) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("modelCode", "order");
        model.put("modelName", "订单");
        model.put("modelDesc", "订单主表");
        model.put("tableName", "biz_order");
        model.put("className", "Order");
        model.put("moduleName", "order");
        model.put("isTree", 0);
        model.put("enableLogicDelete", logicDelete ? 1 : 0);
        model.put("enableDataScope", 0);
        model.put("enableAuditField", audit ? 1 : 0);
        return model;
    }

    /** 覆盖四种查询方式、唯一列、必填、日期列与不进列表的备注列 */
    static List<Map<String, Object>> richFields() {
        return List.of(
                new Field("id", "主键", "Long", "bigint").primary().autoIncrement().list().build(),
                new Field("orderNo", "订单号", "String", "varchar").input().required().unique().list().query("LIKE").build(),
                new Field("amount", "订单金额", "BigDecimal", "decimal").input().list().query("BETWEEN").build(),
                new Field("payTime", "支付时间", "LocalDateTime", "datetime").input().list().query("BETWEEN").build(),
                new Field("status", "状态", "Integer", "int").input().required().list().dict("sys_order_status").query("EQ").build(),
                new Field("tags", "标签", "String", "varchar").query("IN").build(),
                new Field("remark", "备注", "String", "text").input().area().build());
    }

    /** 说明里带单引号的字段：SQL 字面量必须把引号翻倍，否则建表语句从中间被截断 */
    static List<Map<String, Object>> quotedRemarkFields() {
        return List.of(
                new Field("id", "主键", "Long", "bigint").primary().autoIncrement().build(),
                new Field("flag", "状态'正常'的说明", "String", "varchar").input().list().build());
    }

    /** 最小可用模型：一个主键 + 一个可写可查的名称，用来验各种「没有」的分支 */    static List<Map<String, Object>> simpleFields() {
        return List.of(
                new Field("id", "主键", "Long", "bigint").primary().autoIncrement().build(),
                new Field("name", "名称", "String", "varchar").input().list().build());
    }

    /** 库里的一行字段：默认全 0，测试里只写与断言有关的开关，读得见差异 */
    static final class Field {
        private final Map<String, Object> row = new LinkedHashMap<>();

        Field(String code, String name, String javaType, String columnType) {
            row.put("fieldCode", code);
            row.put("fieldName", name);
            row.put("fieldType", "STRING");
            row.put("javaType", javaType);
            row.put("columnName", Naming.snake(code));
            row.put("columnType", columnType);
            if ("varchar".equals(columnType)) {
                row.put("columnLength", 64);
            }
            if ("decimal".equals(columnType)) {
                row.put("columnPrecision", 18);
                row.put("columnScale", 2);
            }
            row.put("isPrimary", 0);
            row.put("isAutoIncrement", 0);
            row.put("isNullable", 1);
            row.put("isUnique", 0);
            row.put("isRequired", 0);
            row.put("isList", 0);
            row.put("isQuery", 0);
            row.put("queryType", null);
            row.put("isInsert", 0);
            row.put("isEdit", 0);
            row.put("formType", "INPUT");
            row.put("dictType", null);
            row.put("sortOrder", 0);
        }

        Field primary() {
            row.put("isPrimary", 1);
            row.put("isNullable", 0);
            return this;
        }

        Field autoIncrement() {
            row.put("isAutoIncrement", 1);
            row.put("javaType", "Long");
            row.put("columnType", "bigint");
            return this;
        }

        Field list() {
            row.put("isList", 1);
            return this;
        }

        /** 可录入 + 可编辑 */
        Field input() {
            row.put("isInsert", 1);
            row.put("isEdit", 1);
            return this;
        }

        Field required() {
            row.put("isRequired", 1);
            row.put("isNullable", 0);
            return this;
        }

        Field unique() {
            row.put("isUnique", 1);
            return this;
        }

        /** 挂了字典的字段：前端出 DictSelect / DictTag，控件形态由字典而非自由文本 */
        Field dict(String type) {
            row.put("dictType", type);
            row.put("formType", "SELECT");
            return this;
        }

        /** 长文本字段走文本域 */
        Field area() {
            row.put("formType", "TEXTAREA");
            return this;
        }

        Field query(String kind) {
            row.put("isQuery", 1);
            row.put("queryType", kind);
            return this;
        }

        Map<String, Object> build() {
            // 不排序也不复制：Map.copyOf 不收 null 值，而 queryType / dictType 留空就是要写 null
            return row;
        }
    }
}
