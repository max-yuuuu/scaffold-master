package com.scaffold.framework.tenant;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.scaffold.common.tenant.TenantContext;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户条件注入的单元测试：白名单完整性、旁路表、无上下文跳过，以及真实 SQL 改写结果。
 * 不连数据库，直接调用 MyBatis-Plus 拦截器的解析入口。
 */
class ScaffoldTenantLineHandlerTest {

    private static final Pattern CREATE_TABLE = Pattern.compile("CREATE TABLE (?:IF NOT EXISTS )?(\\w+) \\((.*?)\\n\\) ENGINE", Pattern.DOTALL);
    private static final Pattern TENANT_COLUMN = Pattern.compile("^\\s{4}tenant_id ", Pattern.MULTILINE);

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void 白名单覆盖建表脚本里所有带租户列的表() throws IOException {
        TenantContext.set(1L);
        List<Path> scripts = Files.list(sqlDir()).filter(path -> path.getFileName().toString().matches("V\\d+__.*\\.sql")).sorted().toList();
        assertThat(scripts).isNotEmpty();

        for (Path script : scripts) {
            Matcher tables = CREATE_TABLE.matcher(Files.readString(script));
            while (tables.find()) {
                boolean hasTenantColumn = TENANT_COLUMN.matcher(tables.group(2)).find();
                String table = tables.group(1).toLowerCase();
                if (hasTenantColumn) {
                    assertThat(ScaffoldTenantLineHandler.TENANT_TABLES)
                            .as("%s 在 %s 里带 tenant_id，必须登记进白名单", table, script.getFileName())
                            .contains(table);
                } else {
                    assertThat(ScaffoldTenantLineHandler.TENANT_TABLES)
                            .as("%s 没有 tenant_id，不能进白名单", table)
                            .doesNotContain(table);
                }
            }
        }
    }

    @Test
    void 无租户上下文时整体跳过注入() {
        ScaffoldTenantLineHandler handler = new ScaffoldTenantLineHandler();
        assertThat(handler.ignoreTable("sys_user")).isTrue();

        TenantContext.set(2L);
        assertThat(handler.ignoreTable("sys_user")).isFalse();
        assertThat(handler.ignoreTable("`SYS_USER`")).isFalse();
        assertThat(handler.ignoreTable("scaffold_system.sys_user")).isFalse();
    }

    @Test
    void 不带租户列的表与公共表不注入() {
        TenantContext.set(2L);
        ScaffoldTenantLineHandler handler = new ScaffoldTenantLineHandler();
        // lc_tenant 自身按主键定位，关联中间表没有租户列，代码生成读 information_schema
        assertThat(handler.ignoreTable("lc_tenant")).isTrue();
        assertThat(handler.ignoreTable("sys_user_role")).isTrue();
        assertThat(handler.ignoreTable("gen_table")).isTrue();
        assertThat(handler.ignoreTable("information_schema.tables")).isTrue();
    }

    @Test
    void 查询与关联查询补租户条件() {
        TenantContext.set(2L);
        Rewriter rewriter = new Rewriter(new ScaffoldTenantLineHandler());

        assertThat(rewriter.rewrite("SELECT id FROM sys_user WHERE deleted = 0"))
                .isEqualTo("SELECT id FROM sys_user WHERE deleted = 0 AND tenant_id = 2");
        assertThat(rewriter.rewrite("SELECT id FROM sys_user u LEFT JOIN sys_dept d ON d.id = u.dept_id WHERE u.deleted = 0"))
                .contains("u.tenant_id = 2")
                .contains("d.tenant_id = 2");
        assertThat(rewriter.rewrite("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_user u ON u.id = ur.user_id"))
                .contains("u.tenant_id = 2")
                .doesNotContain("ur.tenant_id");
    }

    @Test
    void 写语句补租户条件但不覆盖显式列() {
        TenantContext.set(2L);
        Rewriter rewriter = new Rewriter(new ScaffoldTenantLineHandler());

        assertThat(rewriter.rewrite("UPDATE sys_post SET post_name = 'x' WHERE id = 1 AND deleted = 0"))
                .contains("tenant_id = 2");
        assertThat(rewriter.rewrite("DELETE FROM sys_login_log WHERE id = 1"))
                .contains("tenant_id = 2");
        assertThat(rewriter.rewrite("INSERT INTO sys_post (post_code, post_name) VALUES ('dev', '研发')"))
                .contains("tenant_id");
        // 登录日志显式写了 tenant_id，不能再插一遍列
        assertThat(rewriter.rewrite("INSERT INTO sys_login_log (tenant_id, username, status) VALUES (1, 'admin', 1)"))
                .doesNotContain("VALUES (1, 'admin', 1, 2)");
    }

    private static Path sqlDir() {
        Path relative = Path.of("..", "sql");
        Path absolute = Path.of("sql");
        if (Files.isDirectory(relative)) {
            return relative;
        }
        assertThat(absolute).as("找不到建表脚本目录，检查测试工作目录").isDirectory();
        return absolute;
    }

    /** 测试替身：把拦截器的解析入口暴露出来，无需 MyBatis 执行器与 MappedStatement */
    private static final class Rewriter extends TenantLineInnerInterceptor {

        private Rewriter(ScaffoldTenantLineHandler handler) {
            super(handler);
        }

        private String rewrite(String sql) {
            try {
                Statement statement = CCJSqlParserUtil.parse(sql);
                if (statement instanceof Select select) {
                    processSelect(select, 0, sql, null);
                } else if (statement instanceof Insert insert) {
                    processInsert(insert, 0, sql, null);
                } else if (statement instanceof Update update) {
                    processUpdate(update, 0, sql, null);
                } else if (statement instanceof Delete delete) {
                    processDelete(delete, 0, sql, null);
                }
                return statement.toString();
            } catch (Exception ex) {
                throw new AssertionError("SQL 无法被租户拦截器解析: " + sql, ex);
            }
        }
    }
}
