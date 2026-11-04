package com.scaffold.lowcode.model;

import com.scaffold.common.exception.BusinessException;

import java.util.Locale;
import java.util.Set;

/**
 * 生成物里的标识符守卫：表名与列名会<b>原样</b>进了 SQL（{@code FROM order}、{@code o.desc}），
 * 平台不给它们加反引号 —— 加了每一行 SQL 都读不动，而这是交付项目里人要维护的代码。
 *
 * <p>所以保留字必须在录入时就拦住，而不是等交付项目启动后 MyBatis 抛一个
 * {@code SQLSyntaxErrorException}。口径与 {@link QueryType} 一致：不接受任何可能改写 SQL 结构的
 * 用户输入进入 SQL 片段，只在边界处校验一次，生成物保持干净。
 *
 * <p>这份表按 MySQL 8.0 保留字筛过，只留「会被当成字段名 / 表名顺手取用」的那批（{@code desc}、
 * {@code order}、{@code key}、{@code group}、{@code rank} 一类），不是完整表 —— 完整表里有
 * {@code quick}、{@code sensitive} 这类没人会取的名字，列出来只会稀释提醒的分量。
 * 判定用<b>整词相等</b>，不做包含匹配：{@code order_no}、{@code desc_text} 都合法。
 */
public final class SqlIdentifiers {

    private static final Set<String> RESERVED = Set.of(
            "add", "all", "alter", "and", "array", "as", "asc", "between", "bigint", "binary", "blob",
            "both", "by", "case", "change", "char", "character", "check", "collate", "column", "columns",
            "condition", "constraint", "continue", "convert", "create", "cross", "current_date",
            "current_time", "current_timestamp", "current_user", "cursor", "database", "databases",
            "day_hour", "day_microsecond", "day_minute", "day_second", "dec", "decimal", "declare",
            "default", "delayed", "delete", "desc", "describe", "distinct", "distinctrow", "div", "double",
            "drop", "dual", "each", "else", "encrypted", "end", "escape", "exists", "explain", "false",
            "fetch", "float", "for", "force", "foreign", "from", "fulltext", "generated", "get", "grant",
            "group", "having", "hour_microsecond", "hour_minute", "hour_second", "if", "ignore", "in",
            "index", "infile", "inner", "inout", "insensitive", "insert", "int", "integer", "interval",
            "into", "io_after_gtids", "iterate", "join", "key", "keys", "kill", "label", "leading", "leave",
            "left", "like", "limit", "linear", "lines", "load", "localtime", "localtimestamp", "lock",
            "long", "longblob", "longtext", "loop", "match", "maxvalue", "mediumblob", "mediumint",
            "mediumtext", "middleint", "minute_microsecond", "minute_second", "mod", "modifies", "natural",
            "not", "no_write_to_binlog", "null", "numeric", "on", "optimize", "option", "optionally", "or",
            "order", "out", "outfile", "partition", "precision", "primary", "procedure", "purge", "range",
            "read", "reads", "real", "references", "regexp", "rename", "repeat", "replace", "require",
            "resignal", "restrict", "return", "revoke", "right", "rlike", "schema", "schemas",
            "second_microsecond", "select", "sensitive", "separator", "set", "show", "signal", "smallint",
            "spatial", "specific", "sql", "sqlexception", "sqlstate", "sqlwarning", "ssl", "starting",
            "stored", "straight_join", "substring", "sum", "table", "tables", "then", "tinyblob", "tinyint",
            "tinytext", "to", "trailing", "trigger", "true", "undo", "union", "unique", "unlock", "unsigned",
            "update", "usage", "using", "utc_date", "utc_time", "utc_timestamp", "values", "varbinary",
            "varchar", "varying", "when", "where", "while", "window", "with", "write", "xor", "year_month",
            "zerofill");

    private SqlIdentifiers() {
    }

    /**
     * 表名守卫：{@code lc_model.table_name}（留空时由模型编码派生）在保存时就判掉。
     *
     * @param tableName 将要写进 SQL 的表名
     */
    public static void requireTableName(String tableName) {
        if (isReserved(tableName)) {
            throw new BusinessException("表名 " + tableName + " 是 MySQL 保留字，生成的 SQL 会直接语法错。"
                    + "换个名字或加业务前缀（如 " + tableName + "_main / biz_" + tableName + "）");
        }
    }

    /** 列名守卫：{@code lc_field} 的列名由字段编码派生，编码一改就可能撞保留字 */
    public static void requireColumnName(String columnName) {
        if (isReserved(columnName)) {
            throw new BusinessException("字段编码 " + columnName + " 派生出的列名是 MySQL 保留字，生成的 SQL 会直接语法错。"
                    + columnHint(columnName));
        }
    }

    /** 保留字判定：整词、大小写不敏感；{@code order_no} 这类派生名不受影响 */
    public static boolean isReserved(String identifier) {
        if (identifier == null) {
            return false;
        }
        return RESERVED.contains(identifier.trim().toLowerCase(Locale.ROOT));
    }

    /** 只给「真会被顺手取用」的保留字建议，其余一律提示加业务前缀 */
    private static String columnHint(String columnName) {
        return switch (columnName.toLowerCase(Locale.ROOT)) {
            case "desc" -> "建议改成 description（描述）";
            case "order" -> "建议改成 sort_order 或 order_no（排序号 / 单号）";
            case "key" -> "建议改成 biz_key、config_key 或 code";
            case "group" -> "建议改成 group_name 或 category";
            case "rank" -> "建议改成 rank_no";
            case "match" -> "建议改成 match_rule 或 matched_by";
            case "range" -> "建议改成 amount_range 或 scope";
            case "check" -> "建议改成 check_result 或 checked_by";
            case "column", "columns" -> "建议改成 field_name 或 column_no";
            case "table" -> "建议改成 table_name 或 sheet_name";
            case "index" -> "建议改成 seq_no 或 position";
            default -> "建议加业务前缀或换个说法（如 " + columnName + "_text）";
        };
    }
}
