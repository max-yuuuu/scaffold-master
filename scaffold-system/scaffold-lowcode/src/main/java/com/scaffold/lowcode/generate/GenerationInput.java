package com.scaffold.lowcode.generate;

import com.scaffold.common.exception.BusinessException;
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
        List<Map<String, Object>> listFields = new ArrayList<>();
        List<Map<String, Object>> queryFields = new ArrayList<>();
        List<Map<String, Object>> formFields = new ArrayList<>();
        List<Map<String, Object>> insertFields = new ArrayList<>();
        List<Map<String, Object>> updateFields = new ArrayList<>();
        List<Map<String, Object>> uniqueFields = new ArrayList<>();
        Map<String, Object> primary = null;

        for (Map<String, Object> field : fields) {
            Map<String, Object> item = fieldModel(field);
            fieldModels.add(item);
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
            // 自增主键不进 INSERT：由数据库给，写进去反而会把 AUTO_INCREMENT 顶掉
            if (Boolean.TRUE.equals(item.get("insert")) && !Boolean.TRUE.equals(item.get("autoIncrement"))) {
                insertFields.add(item);
            }
            if (Boolean.TRUE.equals(item.get("edit")) && !Boolean.TRUE.equals(item.get("primary"))) {
                updateFields.add(item);
            }
            // 唯一性检查要能拿到新值，所以只对「可录入 / 可编辑」的字段生成；
            // 不可编辑的唯一列（比如外部系统回填的编号）没有写入路径，检查也无从查起
            if (Boolean.TRUE.equals(item.get("unique"))
                    && (Boolean.TRUE.equals(item.get("insert")) || Boolean.TRUE.equals(item.get("edit")))) {
                uniqueFields.add(item);
            }
        }
        if (primary == null) {
            throw new BusinessException("模型 " + modelName + " 没有主键字段，生成物无法定位单条记录");
        }
        // 这两条不是洁癖：字段全关时生成的 INSERT / UPDATE 会是缺列的残缺语句，
        // 推出去要到运行期才炸，当场拦掉更省事
        if (insertFields.isEmpty()) {
            throw new BusinessException("模型 " + modelName + " 没有任何「可录入」字段，生成不出新增语句");
        }
        if (updateFields.isEmpty()) {
            throw new BusinessException("模型 " + modelName + " 没有任何「可编辑」字段，生成不出修改语句");
        }

        // 列表行 = 主键 + 勾选进列表的字段（主键恒在：行操作、编辑、删除都要靠它）
        List<Map<String, Object>> rowFields = new ArrayList<>();
        for (Map<String, Object> item : fieldModels) {
            if (Boolean.TRUE.equals(item.get("primary")) || Boolean.TRUE.equals(item.get("list"))) {
                rowFields.add(item);
            }
        }
        List<Map<String, Object>> queryConditions = queryConditions(queryFields);
        List<Map<String, Object>> queryParams = flattenParams(queryConditions);

        Map<String, Object> root = new LinkedHashMap<>();
        boolean audit = truth(model.get("enableAuditField"));
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
        root.put("insertFields", insertFields);
        root.put("updateFields", updateFields);
        root.put("uniqueFields", uniqueFields);
        root.put("rowFields", rowFields);
        root.put("queryConditions", queryConditions);
        root.put("queryParams", queryParams);
        // Controller 要不要 import @DateTimeFormat：日期类查询参数得按规范串绑定
        root.put("hasDateQuery", queryParams.stream().anyMatch(p -> p.get("jsonPattern") != null));
        // Mapper / Service / Controller 的方法签名直接用查询参数的类型，这几个 import 归它们
        root.put("queryParamImports", typeImportsOf(queryParams));
        // Mapper 里的实体参数名（@Param("order") Order order），与底座 @Param("row") 那个位置同职责
        root.put("entityVar", Naming.camel(className));
        root.put("primaryField", primary);
        // import 顺序固定，同一份模型两次生成的文本才逐字节一致（发布 diff 才可读）；
        // 每个文件只带自己用到的，不留无用 import
        root.put("entityImports", audit ? withAudit(importsOf(fieldModels)) : importsOf(fieldModels));
        root.put("rowImports", audit ? withAudit(importsOf(rowFields)) : importsOf(rowFields));
        root.put("writeImports", importsOf(formFields));
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
        item.put("queryKind", QueryType.of(text(field.get("queryType"))).name());
        item.put("queryOperator", QueryType.of(text(field.get("queryType"))).operator());
        item.put("jsonPattern", jsonPattern(javaType));
        item.put("stringType", "String".equals(javaType));
        item.put("insert", truth(field.get("isInsert")));
        item.put("edit", truth(field.get("isEdit")));
        item.put("formType", blankToDefault(text(field.get("formType")), "INPUT"));
        item.put("dictType", blankToNull(text(field.get("dictType"))));
        item.put("defaultValue", blankToNull(text(field.get("defaultValue"))));
        return item;
    }

    /**
     * 日期类字段的出入参格式，与前端物料 {@code dateValue} 只认的两种规范串一致
     * （{@code yyyy-MM-dd} / {@code yyyy-MM-dd HH:mm:ss}）。
     *
     * <p>为什么在字段上写 {@code @JsonFormat} 而不是改全局配置：全局那条在 A 档里，
     * 平台不该为了一个模型去动交付项目的 Jackson 设置；写在生成字段上，改一个模型只影响这一列。
     *
     * @return 需要注解时返回格式串，其余类型返回 {@code null}
     */
    private static String jsonPattern(String javaType) {
        return switch (javaType) {
            case "LocalDate" -> "yyyy-MM-dd";
            case "LocalDateTime" -> "yyyy-MM-dd HH:mm:ss";
            default -> null;
        };
    }

    /** 一个文件用到的 import：字段类型里的 java.time / java.math，加出现日期字段时的 Jackson 注解 */
    private static List<String> importsOf(List<Map<String, Object>> fields) {
        Set<String> imports = new LinkedHashSet<>(typeImportsOf(fields));
        if (fields.stream().anyMatch(f -> f.get("jsonPattern") != null)) {
            imports.add("com.fasterxml.jackson.annotation.JsonFormat");
        }
        return sorted(imports);
    }

    /** 只按 javaType 收 {@code java.time} / {@code java.math}：方法签名里的参数类型也走这条 */
    private static List<String> typeImportsOf(List<Map<String, Object>> items) {
        Set<String> imports = new LinkedHashSet<>();
        for (Map<String, Object> item : items) {
            String javaType = String.valueOf(item.get("javaType"));
            switch (javaType) {
                case "BigDecimal" -> imports.add("java.math.BigDecimal");
                case "LocalDate" -> imports.add("java.time.LocalDate");
                case "LocalDateTime" -> imports.add("java.time.LocalDateTime");
                default -> {
                }
            }
        }
        return sorted(imports);
    }

    /** 审计四字段里的创建 / 更新时间固定是 LocalDateTime，且要按规范串出参 */
    private static List<String> withAudit(List<String> imports) {
        Set<String> merged = new LinkedHashSet<>(imports);
        merged.add("java.time.LocalDateTime");
        merged.add("com.fasterxml.jackson.annotation.JsonFormat");
        return sorted(merged);
    }

    private static List<String> sorted(Set<String> imports) {
        return imports.stream().sorted().toList();
    }

    /**
     * 查询条件摊平成「SQL 用的一组」+「方法签名用的一组」。
     *
     * <p>口径与底座一致：条件写成 {@code (#{x} IS NULL OR ...)}，不填就是不限制，
     * 不用动态 SQL 拼 WHERE；运算符只来自 {@link QueryType} 枚举，前端传什么都不可能改写 SQL 结构。
     * 区间拆成 {@code xxxStart} / {@code xxxEnd} 两个参数（比 {@code BETWEEN} 少一个空值陷阱），
     * 多选收一个逗号串走 {@code FIND_IN_SET}（免掉 {@code <foreach>}，注解 SQL 里能写完整）。
     */
    private static List<Map<String, Object>> queryConditions(List<Map<String, Object>> queryFields) {
        List<Map<String, Object>> conditions = new ArrayList<>();
        for (Map<String, Object> field : queryFields) {
            String kind = String.valueOf(field.get("queryKind"));
            String javaField = String.valueOf(field.get("javaField"));
            String javaType = String.valueOf(field.get("javaType"));
            String pattern = (String) field.get("jsonPattern");
            List<Map<String, Object>> params = new ArrayList<>();
            if ("BETWEEN".equals(kind)) {
                params.add(queryParam(javaField + "Start", javaType, pattern, field.get("desc") + "（起）"));
                params.add(queryParam(javaField + "End", javaType, pattern, field.get("desc") + "（止）"));
            } else if ("IN".equals(kind)) {
                params.add(queryParam(javaField, "String", null, field.get("desc") + "（多选，逗号分隔）"));
            } else {
                params.add(queryParam(javaField, javaType, pattern, field.get("desc")));
            }
            Map<String, Object> condition = new LinkedHashMap<>();
            condition.put("kind", kind);
            condition.put("operator", field.get("queryOperator"));
            condition.put("column", field.get("column"));
            condition.put("params", params);
            conditions.add(condition);
        }
        return conditions;
    }

    /** 方法签名里的参数顺序与条件顺序一致，逐个参数一行 */
    private static List<Map<String, Object>> flattenParams(List<Map<String, Object>> conditions) {
        List<Map<String, Object>> params = new ArrayList<>();
        for (Map<String, Object> condition : conditions) {
            params.addAll(castList(condition.get("params")));
        }
        return params;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private static Map<String, Object> queryParam(String name, String javaType, String pattern, Object desc) {
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("name", name);
        param.put("javaType", javaType);
        param.put("jsonPattern", pattern);
        param.put("desc", String.valueOf(desc));
        return param;
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
