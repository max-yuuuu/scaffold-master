package com.scaffold.lowcode.generate;

import com.scaffold.lowcode.model.Naming;
import com.scaffold.lowcode.model.QueryType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 把 {@code lc_project} / {@code lc_model} / {@code lc_field} 三份行数据装配成 FreeMarker 的数据模型（方案 §4.2）。
 *
 * <p>所有派生都在这一层算完：命名（类名 / 包段 / 资源名 / 表名）、列类型全形式、查询运算符、
 * 需要的 import、列表 / 查询 / 表单三组字段的筛选。模板里只允许取值与循环 ——
 * 生成的代码要给人读，模板本身也得给人读，把判断堆进 {@code .ftl} 只会两头都读不动。
 */
public final class GenerationInput {

    private GenerationInput() {
    }

    public static Map<String, Object> of(Map<String, Object> project, Map<String, Object> model, List<Map<String, Object>> fields) {
        String modelCode = text(model.get("modelCode"));
        String className = blankToNull(text(model.get("className")));
        if (className == null) {
            className = Naming.pascal(modelCode);
        }
        String module = blankToNull(text(model.get("moduleName")));
        if (module == null) {
            module = Naming.packageSegment(modelCode);
        }
        String tableName = blankToNull(text(model.get("tableName")));
        if (tableName == null) {
            tableName = Naming.snake(modelCode);
        }
        String resourceName = Naming.camel(modelCode);
        String modelName = blankToNull(text(model.get("modelName")));
        if (modelName == null) {
            modelName = className;
        }

        List<Map<String, Object>> fieldModels = new ArrayList<>();
        Set<String> imports = new LinkedHashSet<>();
        List<Map<String, Object>> listFields = new ArrayList<>();
        List<Map<String, Object>> queryFields = new ArrayList<>();
        List<Map<String, Object>> formFields = new ArrayList<>();
        Map<String, Object> primary = null;

        for (Map<String, Object> field : fields) {
            Map<String, Object> item = fieldModel(field);
            fieldModels.add(item);
            String javaType = String.valueOf(item.get("javaType"));
            if ("BigDecimal".equals(javaType)) {
                imports.add("java.math.BigDecimal");
            } else if ("LocalDateTime".equals(javaType)) {
                imports.add("java.time.LocalDateTime");
            } else if ("LocalDate".equals(javaType)) {
                imports.add("java.time.LocalDate");
            }
            if (Boolean.TRUE.equals(item.get("primary"))) {
                primary = item;
            }
            if (Boolean.TRUE.equals(item.get("list"))) {
                listFields.add(item);
            }
            if (Boolean.TRUE.equals(item.get("query"))) {
                queryFields.add(item);
            }
            if (Boolean.TRUE.equals(item.get("insert")) || Boolean.TRUE.equals(item.get("edit"))) {
                formFields.add(item);
            }
        }

        Map<String, Object> root = new LinkedHashMap<>();
        boolean audit = truth(model.get("enableAuditField"));
        if (audit) {
            // 审计四字段里的创建 / 更新时间固定是 LocalDateTime，开了审计就要 import
            imports.add("java.time.LocalDateTime");
        }
        root.put("projectCode", text(project.get("projectCode")));
        root.put("projectName", text(project.get("projectName")));
        root.put("module", module);
        root.put("className", className);
        root.put("resourceName", resourceName);
        root.put("tableName", tableName);
        root.put("modelName", modelName);
        root.put("modelDesc", blankToDefault(text(model.get("modelDesc")), modelName));
        root.put("permissionPrefix", module + ":" + Naming.snake(resourceName));
        root.put("apiPath", "/" + module + "/" + Naming.snake(resourceName));
        root.put("tree", truth(model.get("isTree")));
        root.put("logicDelete", truth(model.get("enableLogicDelete")));
        root.put("dataScope", truth(model.get("enableDataScope")));
        root.put("audit", audit);
        root.put("fields", fieldModels);
        root.put("listFields", listFields);
        root.put("queryFields", queryFields);
        root.put("formFields", formFields);
        // import 顺序固定，同一份模型两次生成的文本才逐字节一致（发布 diff 才可读）
        root.put("imports", imports.stream().sorted().toList());
        root.put("primaryField", primary);
        return root;
    }

    /** 单个字段的模板视图：命名、列类型全形式与查询运算符都在这里定稿 */
    private static Map<String, Object> fieldModel(Map<String, Object> field) {
        String fieldCode = text(field.get("fieldCode"));
        String javaType = blankToDefault(text(field.get("javaType")), "String");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", fieldCode);
        item.put("name", blankToDefault(text(field.get("fieldName")), fieldCode));
        item.put("desc", blankToDefault(text(field.get("fieldDesc")), blankToDefault(text(field.get("fieldName")), fieldCode)));
        item.put("javaField", Naming.camel(fieldCode));
        item.put("javaType", javaType);
        item.put("column", blankToDefault(text(field.get("columnName")), Naming.snake(fieldCode)));
        item.put("columnType", columnType(field));
        item.put("primary", truth(field.get("isPrimary")));
        item.put("autoIncrement", truth(field.get("isAutoIncrement")));
        item.put("nullable", truth(field.get("isNullable")));
        item.put("unique", truth(field.get("isUnique")));
        item.put("required", truth(field.get("isRequired")));
        item.put("list", truth(field.get("isList")));
        item.put("query", truth(field.get("isQuery")));
        item.put("queryOperator", QueryType.of(text(field.get("queryType"))).operator());
        item.put("insert", truth(field.get("isInsert")));
        item.put("edit", truth(field.get("isEdit")));
        item.put("formType", blankToDefault(text(field.get("formType")), "INPUT"));
        item.put("dictType", blankToNull(text(field.get("dictType"))));
        item.put("defaultValue", blankToNull(text(field.get("defaultValue"))));
        return item;
    }

    /**
     * 列类型的完整写法：{@code varchar} + 长度 → {@code varchar(255)}，
     * {@code decimal} + 精度标度 → {@code decimal(18,2)}，其余原样。
     * 存库的是拆开的三段（{@code FieldType} 算出来的），生成 SQL 与实体注释要的是合起来的一份。
     */
    private static String columnType(Map<String, Object> field) {
        String base = blankToDefault(text(field.get("columnType")), "varchar").toLowerCase(Locale.ROOT);
        Integer length = integer(field.get("columnLength"));
        Integer precision = integer(field.get("columnPrecision"));
        Integer scale = integer(field.get("columnScale"));
        if (("decimal".equals(base) || "numeric".equals(base)) && precision != null) {
            return base + "(" + precision + "," + (scale == null ? 0 : scale) + ")";
        }
        if (length != null && length > 0 && (base.startsWith("varchar") || base.startsWith("char"))) {
            return base + "(" + length + ")";
        }
        return base;
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = blankToNull(value == null ? null : String.valueOf(value));
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 行数据里的布尔既可能是 Boolean，也可能是 MySQL 查出来的 0/1 */
    private static boolean truth(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = value == null ? null : String.valueOf(value);
        return text != null && ("1".equals(text) || "true".equalsIgnoreCase(text));
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToDefault(String value, String fallback) {
        String trimmed = blankToNull(value);
        return trimmed == null ? fallback : trimmed;
    }
}
