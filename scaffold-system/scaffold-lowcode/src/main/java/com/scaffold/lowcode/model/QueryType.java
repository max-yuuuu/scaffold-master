package com.scaffold.lowcode.model;

import java.util.List;
import java.util.Locale;

/**
 * 查询条件类型。生成列表页的查询区与后端条件拼接时按这里的取值走，
 * 只允许这些枚举值进入 SQL 片段，不接受前端传来的裸运算符。
 */
public enum QueryType {

    EQ("=", "等于"),
    NE("!=", "不等于"),
    GT(">", "大于"),
    GE(">=", "大于等于"),
    LT("<", "小于"),
    LE("<=", "小于等于"),
    LIKE("LIKE", "模糊匹配"),
    BETWEEN("BETWEEN", "区间（日期 / 数值，两个值）"),
    IN("IN", "多选（值列表）");

    private final String operator;
    private final String label;

    QueryType(String operator, String label) {
        this.operator = operator;
        this.label = label;
    }

    public String code() {
        return name();
    }

    public String operator() {
        return operator;
    }

    public String label() {
        return label;
    }

    public static List<String> codes() {
        return List.of(values()).stream().map(QueryType::name).toList();
    }

    /** 未填或无法识别时按等于处理，不让一个错值把查询变成全表扫描 */
    public static QueryType of(String code) {
        if (code == null || code.isBlank()) {
            return EQ;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        for (QueryType type : values()) {
            if (type.name().equals(normalized) || type.operator.equalsIgnoreCase(code.trim())) {
                return type;
            }
        }
        return EQ;
    }
}
