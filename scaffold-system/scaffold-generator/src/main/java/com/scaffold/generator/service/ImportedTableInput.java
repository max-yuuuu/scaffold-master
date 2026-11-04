package com.scaffold.generator.service;

import com.scaffold.lowcode.model.FieldType;
import com.scaffold.lowcode.model.FormType;
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
 * 把「库里已有的一张表」翻译成生成器吃的模型形状（方案 §4.4：两条链路同一套模板）。
 *
 * <p>老代码生成页存的是 {@code gen_table} + {@code gen_table_column}，低代码存的是
 * {@code lc_model} + {@code lc_field}。这里不改模板、也不给模板加第二条分支，而是把前者适配成后者的
 * 字段名形状 —— 生成口径只有一份，两条链路的产物才会真的对得上。
 *
 * <p><b>类型事实一律从库读，不从 {@code gen_table_column} 的快照读。</b>那张表存的是导入当时算出来的
 * {@code java_type}，口径修对之前导入的表还留着旧答案（{@code date} 被存成 {@code LocalDateTime}）；
 * 而「是不是主键、是不是自增、可不可空、多长」当初压根没存，是靠「列名叫 id」猜的 ——
 * 猜错就生成出一份跑不通的代码。配置的（进不进表单、查不查）留在库里，事实的现查。
 */
public final class ImportedTableInput {

    /** 底座约定的审计四列：齐了且类型对得上才交给模板声明，缺一个就当普通列处理 */
    private static final Map<String, String> AUDIT_COLUMNS = Map.of(
            "create_by", "varchar", "update_by", "varchar",
            "create_time", "datetime", "update_time", "datetime");

    /** 逻辑删除标记列：认出来就交给模板（生成 {@code deleted = 0} 条件与软删语句），不再当业务字段 */
    private static final String DELETED_COLUMN = "deleted";

    /** 租户列：只读。表单里给一个人挑租户的机会，等于给一次越权的机会 */
    private static final String TENANT_COLUMN = "tenant_id";

    private ImportedTableInput() {
    }

    /**
     * @param table     {@code gen_table} 的一行（tableName / tableComment / className / module / businessName）
     * @param dbColumns {@code information_schema.columns} 查出来的列，按 {@code ordinal_position} 排好
     */
    public static Map<String, Object> model(Map<String, Object> table, List<Map<String, Object>> dbColumns) {
        Set<String> present = columnNames(dbColumns);
        String tableComment = text(table.get("tableComment"));
        String className = text(table.get("className"));
        String display = blankToDefault(tableComment, className);

        Map<String, Object> model = new LinkedHashMap<>();
        // modelCode 决定资源名与权限点前缀，老链路里最接近它的就是业务名
        model.put("modelCode", blankToDefault(text(table.get("businessName")), Naming.camel(text(table.get("tableName")))));
        model.put("modelName", display);
        model.put("modelDesc", display);
        model.put("tableName", text(table.get("tableName")));
        model.put("className", className);
        model.put("moduleName", Naming.snake(text(table.get("module"))));
        model.put("isTree", false);
        model.put("enableDataScope", false);
        model.put("enableLogicDelete", hasDeleteMark(present, dbColumns));
        model.put("enableAuditField", hasFullAuditSet(present, dbColumns));
        return model;
    }

    /**
     * @param config    {@code gen_table_column} 的行（导入时存下的显示与配置项）
     * @param dbColumns 库里现在的列
     */
    public static List<Map<String, Object>> fields(Map<String, Object> table, List<Map<String, Object>> config,
                                                   List<Map<String, Object>> dbColumns) {
        Set<String> present = columnNames(dbColumns);
        boolean audit = hasFullAuditSet(present, dbColumns);
        boolean logicDelete = hasDeleteMark(present, dbColumns);
        List<Map<String, Object>> fields = new ArrayList<>();
        for (Map<String, Object> column : dbColumns) {
            String columnName = text(column.get("columnName"));
            if ((audit && AUDIT_COLUMNS.containsKey(columnName)) || (logicDelete && DELETED_COLUMN.equals(columnName))) {
                continue;
            }
            fields.add(field(table, config, column, audit, logicDelete));
        }
        return fields;
    }

    /**
     * 这一列能不能由表单写入。审计四列由数据库与 Service 写，{@code deleted} 与 {@code tenant_id}
     * 更不是人填的 —— 表单里给一个人挑租户的机会，等于给一次越权的机会。
     */
    public static boolean isWritable(String columnName) {
        return !isHidden(columnName) && !AUDIT_COLUMNS.containsKey(columnName);
    }

    /** 这一列该不该出现在列表与查询区（租户列与删除标记对使用方没有阅读价值） */
    public static boolean isHidden(String columnName) {
        return TENANT_COLUMN.equals(columnName) || DELETED_COLUMN.equals(columnName);
    }

    private static Map<String, Object> field(Map<String, Object> table, List<Map<String, Object>> config,
                                             Map<String, Object> column, boolean audit, boolean logicDelete) {
        String columnName = text(column.get("columnName"));
        FieldType type = FieldType.ofColumn(text(column.get("dataType")));
        boolean primary = "PRI".equalsIgnoreCase(text(column.get("columnKey")));
        boolean autoIncrement = text(column.get("extra")).toLowerCase(Locale.ROOT).contains("auto_increment");
        Map<String, Object> stored = findConfig(config, columnName);

        Map<String, Object> field = new LinkedHashMap<>();
        field.put("fieldCode", Naming.camel(columnName));
        field.put("fieldName", blankToDefault(text(column.get("columnComment")), Naming.camel(columnName)));
        field.put("fieldDesc", blankToDefault(text(column.get("columnComment")), columnName));
        field.put("fieldType", type.code());
        field.put("javaType", type.javaType(autoIncrement));
        field.put("columnName", columnName);
        field.put("columnType", text(column.get("dataType")));
        field.put("columnLength", integer(column.get("columnLength")));
        field.put("columnPrecision", integer(column.get("columnPrecision")));
        field.put("columnScale", integer(column.get("columnScale")));
        field.put("isPrimary", primary);
        field.put("isAutoIncrement", autoIncrement);
        field.put("isNullable", "YES".equalsIgnoreCase(text(column.get("nullable"))));
        // 老链路没存唯一索引信息，就不假装知道：判重与 UNIQUE KEY 都留给低代码链路的模型
        field.put("isUnique", false);
        field.put("defaultValue", null);
        field.put("isList", flag(stored, "list", !primary && !isHidden(columnName)));
        field.put("isQuery", flag(stored, "query", false));
        field.put("queryType", queryType(type, stored).code());
        field.put("isInsert", primary ? false : flag(stored, "insert", isWritable(columnName)));
        field.put("isEdit", primary ? false : flag(stored, "edit", isWritable(columnName)));
        field.put("isRequired", flag(stored, "required", "NO".equalsIgnoreCase(text(column.get("nullable")))));
        field.put("formType", formType(type, stored).code());
        field.put("dictType", null);
        field.put("sortOrder", integer(column.get("sort")));
        return field;
    }

    /** 控件：以导入时存下的为准，存的是老中文值时退回类型默认控件而不是硬吃 INPUT */
    private static FormType formType(FieldType type, Map<String, Object> stored) {
        return FormType.of(text(stored == null ? null : stored.get("formType")), type.defaultFormType());
    }

    /** 查询方式：同上，没配过的按类型给（文本列模糊匹配，其余等值） */
    private static QueryType queryType(FieldType type, Map<String, Object> stored) {
        String storedValue = stored == null ? null : text(stored.get("queryType"));
        if (storedValue == null || storedValue.isBlank()) {
            return type.defaultQueryType();
        }
        return QueryType.of(storedValue);
    }

    private static boolean hasFullAuditSet(Set<String> present, List<Map<String, Object>> dbColumns) {
        if (!present.containsAll(AUDIT_COLUMNS.keySet())) {
            return false;
        }
        // 名字齐了但类型不对（比如 create_time 是 varchar）也不能交给模板：
        // 模板声明的是 String + LocalDateTime，对不上就生成出一份查不出来的实体
        for (Map<String, Object> column : dbColumns) {
            String expected = AUDIT_COLUMNS.get(text(column.get("columnName")));
            if (expected != null && !expected.equals(text(column.get("dataType")).toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasDeleteMark(Set<String> present, List<Map<String, Object>> dbColumns) {
        for (Map<String, Object> column : dbColumns) {
            if (DELETED_COLUMN.equals(text(column.get("columnName")))) {
                String dataType = text(column.get("dataType")).toLowerCase(Locale.ROOT);
                return dataType.contains("int") || dataType.contains("smallint");
            }
        }
        return false;
    }

    private static Set<String> columnNames(List<Map<String, Object>> dbColumns) {
        Set<String> names = new LinkedHashSet<>();
        for (Map<String, Object> column : dbColumns) {
            names.add(text(column.get("columnName")));
        }
        return names;
    }

    private static Map<String, Object> findConfig(List<Map<String, Object>> config, String columnName) {
        for (Map<String, Object> row : config) {
            // selectColumns 把列名放在 col，importColumns 放 columnName，两边都认
            String key = blankToDefault(text(row.get("col")), text(row.get("columnName")));
            if (columnName.equals(key)) {
                return row;
            }
        }
        return null;
    }

    private static boolean flag(Map<String, Object> row, String key, boolean fallback) {
        if (row == null || row.get(key) == null) {
            return fallback;
        }
        Object value = row.get(key);
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = String.valueOf(value);
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = blankToNull(value == null ? null : String.valueOf(value));
        return text == null ? null : Integer.valueOf(text);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToDefault(String value, String fallback) {
        String trimmed = blankToNull(value);
        return trimmed == null ? fallback : trimmed;
    }
}
