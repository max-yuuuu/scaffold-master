package com.scaffold.lowcode.generate;

import com.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.scaffold.lowcode.generate.TemplateFixture.model;
import static com.scaffold.lowcode.generate.TemplateFixture.richFields;
import static com.scaffold.lowcode.generate.TemplateFixture.simpleFields;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 建表与菜单种子模板的回归测试（PR 5C）。
 *
 * <p>这两份 SQL 是交付项目里唯一由平台产出、又要人拿去真执行的产物，所以断言集中在三件事：
 * <b>永不生成破坏性语句</b>（DROP / ALTER DROP 一条都不许出现）、<b>可重复执行</b>
 * （CREATE TABLE IF NOT EXISTS 与按 permission 判重的插入）、<b>与底座 DDL 同款形状</b>
 * （列序、注释、表选项、审计与删除标记的位置）。
 *
 * <p>真在 MySQL 上跑两遍的物化演练不在这里（本模块测试不连库），见 changelog 的 PR 5C 验证记录。
 */
class SqlTemplatesTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void 建表语句与底座DDL同款形状() {
        String ddl = table(true, true);
        assertThat(ddl).contains("CREATE TABLE IF NOT EXISTS biz_order (");
        assertThat(ddl).contains("    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',");
        assertThat(ddl).contains("    order_no VARCHAR(64) NOT NULL COMMENT '订单号',");
        assertThat(ddl).contains("    amount DECIMAL(18,2) DEFAULT NULL COMMENT '订单金额',");
        assertThat(ddl).contains("    pay_time DATETIME DEFAULT NULL COMMENT '支付时间',");
        // TEXT 列不能带默认值，写 DEFAULT NULL 在 MySQL 里直接建表失败
        assertThat(ddl).contains("    remark TEXT NULL COMMENT '备注',");
        // 列序：业务字段 → 删除标记 → 审计四列，与 sys_post 一致
        assertThat(ddl).contains("    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：1 删除，0 正常',")
                .contains("    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',")
                .contains("    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',");
        assertThat(ddl).contains("    PRIMARY KEY (id),").contains(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';");
    }

    @Test
    void 索引只建在用得上的列上() {
        String ddl = table(true, true);
        // 唯一列出 UNIQUE KEY，名字带列名
        assertThat(ddl).contains("UNIQUE KEY uk_order_no (order_no)");
        // 等值 / 区间 / 多选查询列出普通索引
        assertThat(ddl).contains("KEY idx_amount (amount)").contains("KEY idx_pay_time (pay_time)").contains("KEY idx_status (status)");
        // LIKE 生成的是前置通配，索引用不上，不占写入开销
        assertThat(ddl).doesNotContain("idx_remark");
    }

    @Test
    void 开关关掉时对应片段整块消失() {
        String noAudit = table(false, true);
        assertThat(noAudit).doesNotContain("create_by").doesNotContain("update_time");
        String noDelete = table(true, false);
        assertThat(noDelete).doesNotContain("deleted");
        assertThat(table(false, false)).doesNotContain("deleted").doesNotContain("create_by");
    }

    @Test
    void 永不生成破坏性语句() {
        // 表头注释里写着「请加 ALTER TABLE ... ADD COLUMN」的指导语，所以只判真正的语句行
        String ddl = statements(table(true, true));
        assertThat(ddl).doesNotContainIgnoringCase("DROP TABLE").doesNotContainIgnoringCase("DROP COLUMN")
                .doesNotContainIgnoringCase("ALTER TABLE").doesNotContainIgnoringCase("TRUNCATE")
                .contains("CREATE TABLE IF NOT EXISTS");

        String menuSql = statements(menu());
        assertThat(menuSql).doesNotContainIgnoringCase("DROP").doesNotContainIgnoringCase("DELETE FROM")
                .doesNotContainIgnoringCase("TRUNCATE").contains("NOT EXISTS");
    }

    /** 去掉注释行，只留下会被 mysql 执行的语句 */
    private static String statements(String sql) {
        return sql.lines().filter(line -> !line.startsWith("--")).reduce("", (a, b) -> a + b + "\n");
    }

    @Test
    void 说明里的单引号被转义() {
        Map<String, Object> model = GenerationInput.of(model(true, true),
                TemplateFixture.quotedRemarkFields());
        String ddl = renderer.render("sql/Table.sql.ftl", model);
        // 单引号翻倍，SQL 字面量才不会被截断
        assertThat(ddl).contains("COMMENT '状态''正常''的说明'");
        assertThatThrownBy(() -> renderer.render("sql/NoSuchFile.ftl", model))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 菜单种子按权限点判重且不写死ID() {
        String sql = menu();
        assertThat(sql).contains("SELECT 0, '订单主表', 'menu', 'grid', '/order/order', 'order/Order', 'order:order:list', 1, 1, 1, 'system', 'system'");
        assertThat(sql).contains("WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'order:order:list');");
        // 按钮三个，父级走变量而不是猜 ID
        assertThat(sql).contains("SET @order_menu := (SELECT id FROM sys_menu WHERE permission = 'order:order:list' LIMIT 1);")
                .contains("'新增订单', 'btn'").contains("'编辑订单', 'btn'").contains("'删除订单', 'btn'")
                .contains("'order:order:add'").contains("'order:order:edit'").contains("'order:order:remove'");
        // 底座的 V6 用固定 ID + INSERT IGNORE，那是因为它知道 502 这个号；生成物不知道，不能照抄
        // （只看语句行：表头注释里正拿着这个写法作对比）
        assertThat(statements(sql)).doesNotContain("INSERT IGNORE");
        // 管理员角色授权
        assertThat(sql).contains("INSERT INTO sys_role_menu (role_id, menu_id)")
                .contains("WHERE m.permission IN ('order:order:list', 'order:order:add', 'order:order:edit', 'order:order:remove')");
    }

    @Test
    void 同一份模型两次生成逐字节一致() {
        Map<String, Object> model = GenerationInput.of(model(true, true), richFields());
        assertThat(renderer.render("sql/Table.sql.ftl", model)).isEqualTo(renderer.render("sql/Table.sql.ftl", model));
        assertThat(renderer.render("sql/Menu.sql.ftl", model)).isEqualTo(renderer.render("sql/Menu.sql.ftl", model));
    }

    @Test
    void 列类型已经是完整写法时不重复拼长度() {
        // lc_field 存的是 FieldType 算好的完整形式（varchar(64) 与另存一列的 column_length），
        // 老代码生成页给的是裸类型 + 长度。两条链路的入参形状不同，DDL 只能有一份答案 ——
        // 拼成 varchar(64)(64) 时建表语句在 MySQL 上是语法错误，人在目标库执行第一次就会撞上
        String ddl = renderer.render("sql/Table.sql.ftl",
                GenerationInput.of(model(true, true), storedColumnTypes()));

        assertThat(ddl).contains("order_no VARCHAR(64) NOT NULL").contains("amount DECIMAL(18,2)");
        assertThat(ddl).doesNotContain("VARCHAR(64)(64)").doesNotContain("DECIMAL(18,2)(18,2)");
    }

    /** 把夹具里的裸类型换成库里实际存的那种完整写法 */
    private static List<Map<String, Object>> storedColumnTypes() {
        return richFields().stream().map(row -> {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            String base = String.valueOf(row.get("columnType"));
            if ("varchar".equals(base)) {
                copy.put("columnType", "varchar(" + row.get("columnLength") + ")");
            } else if ("decimal".equals(base)) {
                copy.put("columnType", "decimal(" + row.get("columnPrecision") + "," + row.get("columnScale") + ")");
            }
            return copy;
        }).toList();
    }

    private String table(boolean audit, boolean logicDelete) {
        return renderer.render("sql/Table.sql.ftl", GenerationInput.of(model(audit, logicDelete), richFields()));
    }

    private String menu() {
        return renderer.render("sql/Menu.sql.ftl", GenerationInput.of(model(true, true), simpleFields()));
    }
}
