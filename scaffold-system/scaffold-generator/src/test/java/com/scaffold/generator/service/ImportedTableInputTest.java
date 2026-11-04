package com.scaffold.generator.service;

import com.scaffold.lowcode.model.FieldType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「库里已有的表 → 模型形状」这层适配（PR 5F）。
 *
 * <p>端到端那条（{@code GeneratorImportApiTest}）验的是产物；这里验的是只有在这一层才说得清的判断：
 * 审计列什么时候交给模板、删除标记怎么变成逻辑删除、租户列为什么不给表单、存的配置与现查的事实谁优先。
 */
class ImportedTableInputTest {

    @Test
    void 类型事实从库里现查而不是导入时的快照() {
        // 主键与自增过去靠「列名叫 id」猜，猜错就生成出定位不到记录、INSERT 顶掉 AUTO_INCREMENT 的代码
        Map<String, Object> key = field("id", List.of(column("id", "bigint", "PRI", "auto_increment")), List.of());
        assertThat(key.get("fieldCode")).isEqualTo("id");
        assertThat(key.get("isPrimary")).isEqualTo(true);
        assertThat(key.get("isAutoIncrement")).isEqualTo(true);
        assertThat(key.get("javaType")).isEqualTo("Long");
        assertThat(key.get("columnType")).isEqualTo("bigint");
        assertThat(key.get("isInsert")).isEqualTo(false);
        assertThat(key.get("isEdit")).isEqualTo(false);

        // date 列走 FieldType：LocalDate + DATE 控件，而不是老链路自己算出来的 LocalDateTime
        Map<String, Object> due = field("dueDate", List.of(column("id", "bigint", "PRI", "auto_increment"), column("due_date", "date", "", "")), List.of());
        assertThat(due.get("javaType")).isEqualTo("LocalDate");
        assertThat(due.get("formType")).isEqualTo("DATE");
        assertThat(due.get("isNullable")).isEqualTo(true);
    }

    @Test
    void 齐了且类型对得上的审计四列才交给模板() {
        List<Map<String, Object>> full = List.of(
                column("id", "bigint", "PRI", "auto_increment"),
                column("amount", "decimal", "", ""),
                column("create_by", "varchar", "", ""), column("create_time", "datetime", "", ""),
                column("update_by", "varchar", "", ""), column("update_time", "datetime", "", ""));

        assertThat(ImportedTableInput.model(table(), full).get("enableAuditField")).isEqualTo(true);
        // 交给模板声明就不能再出现在字段清单里，否则实体里会出现两个 createTime
        assertThat(ImportedTableInput.fields(table(), List.of(), full))
                .extracting(item -> item.get("fieldCode"))
                .containsExactly("id", "amount");

        // 只有 create_time 的表（日志表、第三方建的表）不能开审计：模板会补出库里没有的三列
        List<Map<String, Object>> partial = List.of(
                column("id", "bigint", "PRI", "auto_increment"), column("create_time", "datetime", "", ""));
        assertThat(ImportedTableInput.model(table(), partial).get("enableAuditField")).isEqualTo(false);
        Map<String, Object> createTime = field("createTime", partial, List.of());
        assertThat(createTime.get("fieldCode")).isEqualTo("createTime");
        // 但仍是只读列：创建时间由数据库与 Service 写，不是人填的
        assertThat(createTime.get("isInsert")).isEqualTo(false);
        assertThat(createTime.get("isEdit")).isEqualTo(false);

        // 名字齐了类型不对（varchar 装的 create_time）同样不开审计
        List<Map<String, Object>> wrongType = List.of(
                column("id", "bigint", "PRI", "auto_increment"),
                column("create_by", "varchar", "", ""), column("create_time", "varchar", "", ""),
                column("update_by", "varchar", "", ""), column("update_time", "datetime", "", ""));
        assertThat(ImportedTableInput.model(table(), wrongType).get("enableAuditField")).isEqualTo(false);
    }

    @Test
    void 有删除标记的表按逻辑删除生成() {
        List<Map<String, Object>> full = List.of(
                column("id", "bigint", "PRI", "auto_increment"),
                column("name", "varchar", "", ""),
                column("deleted", "tinyint", "", ""),
                column("create_by", "varchar", "", ""), column("create_time", "datetime", "", ""),
                column("update_by", "varchar", "", ""), column("update_time", "datetime", "", ""));

        assertThat(ImportedTableInput.model(table(), full).get("enableLogicDelete")).isEqualTo(true);
        assertThat(ImportedTableInput.fields(table(), List.of(), full))
                .extracting(item -> item.get("fieldCode"))
                .doesNotContain("deleted");

        // 库里没有 deleted 列就不能开这个开关：生成的 SQL 会去查一个不存在的列
        assertThat(ImportedTableInput.model(table(), List.of(column("id", "bigint", "PRI", "auto_increment")))
                .get("enableLogicDelete")).isEqualTo(false);
    }

    @Test
    void 租户列只读不进表单也不进列表() {
        Map<String, Object> tenant = field("tenantId", List.of(
                column("id", "bigint", "PRI", "auto_increment"), column("tenant_id", "bigint", "", "")), List.of());

        assertThat(tenant.get("fieldCode")).isEqualTo("tenantId");
        assertThat(tenant.get("javaType")).isEqualTo("Long");
        assertThat(tenant.get("isInsert")).isEqualTo(false);
        assertThat(tenant.get("isEdit")).isEqualTo(false);
        assertThat(tenant.get("isList")).isEqualTo(false);
        assertThat(tenant.get("isQuery")).isEqualTo(false);
        // 表单里给人一个挑租户的下拉，等于给一次越权的机会
        assertThat(FieldType.ofColumn("bigint")).isEqualTo(FieldType.LONG);
    }

    @Test
    void 存过的配置优先但中文旧值退回类型默认控件() {
        List<Map<String, Object>> dbColumns = List.of(
                column("id", "bigint", "PRI", "auto_increment"), column("note", "text", "", ""));

        // 页面上存过的开关优先于派生默认：勾了进列表就不进表单，也照旧出现在列表里
        Map<String, Object> configured = field("note", dbColumns, List.of(config("note", false, false, true)));
        assertThat(configured.get("isList")).isEqualTo(true);
        assertThat(configured.get("isInsert")).isEqualTo(false);

        // 老数据存的是中文控件名，认不出来时退回该类型的默认控件（TEXT→TEXTAREA），而不是一律降成输入框
        assertThat(field("note", dbColumns, List.of(config("note", "文本域"))).get("formType")).isEqualTo("TEXTAREA");
        assertThat(field("note", dbColumns, List.of(config("note", "NOT_A_CONTROL"))).get("formType")).isEqualTo("TEXTAREA");
        // 存过的大写枚举名照用：页面可以把某列改成文本域
        assertThat(field("note", dbColumns, List.of(config("note", "INPUT"))).get("formType")).isEqualTo("INPUT");
    }

    @Test
    void 表名与模块段按命名规则归一() {
        Map<String, Object> model = ImportedTableInput.model(table(), List.of());

        assertThat(model.get("moduleName")).isEqualTo("demo");
        assertThat(model.get("className")).isEqualTo("DemoNote");
        assertThat(model.get("tableName")).isEqualTo("demo_note");
        assertThat(model.get("modelDesc")).isEqualTo("演示备注表");
        // 树表与数据权限本期模板没实现，老链路也不能把它们打开
        assertThat(model.get("isTree")).isEqualTo(false);
        assertThat(model.get("enableDataScope")).isEqualTo(false);
    }

    /** 取某一列适配出来的字段行（id 这类主键列也会留下，所以按列名挑而不是要求只有一行） */
    private Map<String, Object> field(String code, List<Map<String, Object>> dbColumns, List<Map<String, Object>> config) {
        Map<String, Object> found = ImportedTableInput.fields(table(), config, dbColumns).stream()
                .filter(item -> code.equals(item.get("fieldCode")))
                .findFirst()
                .orElse(null);
        assertThat(found).as("字段 %s", code).isNotNull();
        return found;
    }

    private Map<String, Object> table() {
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("id", 1L);
        table.put("tableName", "demo_note");
        table.put("tableComment", "演示备注表");
        table.put("className", "DemoNote");
        table.put("module", "demo");
        table.put("businessName", "note");
        return table;
    }

    private Map<String, Object> column(String name, String dataType, String columnKey, String extra) {
        Map<String, Object> column = new LinkedHashMap<>();
        column.put("columnName", name);
        column.put("dataType", dataType);
        column.put("columnType", dataType);
        column.put("columnComment", name);
        // 默认按「可空」给：主键与必填那几个测试自己覆盖
        column.put("nullable", "YES");
        column.put("columnKey", columnKey);
        column.put("extra", extra);
        column.put("columnLength", null);
        column.put("columnPrecision", null);
        column.put("columnScale", null);
        column.put("sort", 0);
        return column;
    }

    /** 只有开关的存行：与 selectColumns 的形状一致（列名在 col，未配置的类型留空） */
    private Map<String, Object> config(String columnName, boolean insert, boolean edit, boolean list) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("col", columnName);
        row.put("insert", insert);
        row.put("edit", edit);
        row.put("list", list);
        row.put("query", false);
        row.put("queryType", null);
        row.put("formType", null);
        row.put("required", false);
        return row;
    }

    private Map<String, Object> config(String columnName, String formType) {
        Map<String, Object> row = config(columnName, true, true, true);
        row.put("formType", formType);
        return row;
    }
}
