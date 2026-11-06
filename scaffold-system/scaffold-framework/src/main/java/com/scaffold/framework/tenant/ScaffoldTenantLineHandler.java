package com.scaffold.framework.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.scaffold.common.tenant.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

import java.util.Set;

/**
 * MyBatis-Plus 租户条件注入处理器：为白名单表的 SELECT / INSERT / UPDATE / DELETE 自动补 tenant_id。
 *
 * <p>采用<b>白名单</b>而非「全部表减去少数表」：information_schema、gen_table、无 tenant_id 的关联中间表
 * 等一律不注入，避免给公共查询凭空加上不存在的列。新增带 tenant_id 的表必须同步登记进
 * {@link #TENANT_TABLES}，单元测试会用 sql/V*.sql 里的 DDL 反向校验这份清单没有遗漏。
 *
 * <p>匿名请求（登录、验证码、健康检查）没有租户上下文，此时整体跳过注入：这些链路本身按全局主键或
 * 用户名 + 租户编码定位，由 {@code AuthMapper} 显式写条件，不依赖拦截器。
 */
public class ScaffoldTenantLineHandler implements TenantLineHandler {

    /** 带 tenant_id 列、需要自动隔离的业务表（小写表名） */
    public static final Set<String> TENANT_TABLES = Set.of(
            "sys_dept",
            "sys_user",
            "sys_post",
            "sys_role",
            "sys_menu",
            "sys_dict_type",
            "sys_dict_data",
            "sys_config",
            "sys_oper_log",
            "sys_login_log",
            "sys_file",
            "lc_tenant_config",
            "lc_project",
            "lc_model",
            "lc_field",
            "lc_page",
            "lc_page_version",
            "lc_publish_log",
            "lc_merge_request",
            "lc_merge_request_file",
            "lc_base_upgrade"
    );

    private static final String TENANT_COLUMN = "tenant_id";

    @Override
    public Expression getTenantId() {
        return new LongValue(TenantContext.getTenantId());
    }

    @Override
    public String getTenantIdColumn() {
        return TENANT_COLUMN;
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return TenantContext.getTenantId() == null || !TENANT_TABLES.contains(normalize(tableName));
    }

    /** 去掉 schema 前缀与反引号，统一小写，兼容 `db`.`sys_user` 与 INFORMATION_SCHEMA 写法 */
    private static String normalize(String tableName) {
        if (tableName == null) {
            return "";
        }
        String name = tableName.toLowerCase().replace("`", "").trim();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }
}
