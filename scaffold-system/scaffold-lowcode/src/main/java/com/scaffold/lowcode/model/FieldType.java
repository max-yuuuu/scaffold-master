package com.scaffold.lowcode.model;

import java.util.Locale;

/**
 * 字段类型 → Java 类型 / 数据库列类型 / 默认表单控件的<b>唯一事实源</b>（方案 §10.3 PR 3b 的验收项）。
 *
 * <p>映射只在这里定义一次：库里 {@code lc_field.java_type} / {@code column_type} 保存的是
 * 按本枚举算出来的结果（保存时落库，模板生成时不再二次推导），前端通过
 * {@code GET /api/lowcode/models/meta} 取同一份表，避免两处口径漂移。
 *
 * <p>PR 5 写 FreeMarker 模板时必须复用这里；同时要把
 * {@code GeneratorService.javaType()} 的反向映射（导入已有表 → Java 类型）对齐过来，
 * 否则同一个 {@code date} 列在两条链路上会得到不同答案。
 */
public enum FieldType {

    /** 短文本，列类型 varchar(长度) */
    STRING("单行文本", "String", "varchar", 255, null, null, FormType.INPUT),
    /** 长文本。方案 §2.2 的枚举里没有这一项，但后台系统里备注 / 说明 / 富文本是最高频字段，
     * 只靠 varchar 撑到几 K 会让生成的列类型与底座不一致，因此单列一类 */
    TEXT("多行文本", "String", "text", null, null, null, FormType.TEXTAREA),
    /** 整数；作为自增主键时升级为 bigint / Long（底座主键一律 BIGINT） */
    INTEGER("整数", "Integer", "int", null, null, null, FormType.NUMBER),
    /**
     * 长整数（BIGINT）。
     *
     * <p>PR 5F 补的一格：库里已有的 {@code bigint} 列（外部 ID、雪花号、以分计的金额）导入时不该被降级成
     * {@code int}，而 {@link #INTEGER} 只在自增主键这一种情况下才升级成 Long —— 非自增的 bigint 列
     * 原来没有对应的类型可选，两条链路只能各算各的。
     */
    LONG("长整数", "Long", "bigint", null, null, null, FormType.NUMBER),
    /** 小数，默认 decimal(18,2)；金额、比例一类 */
    DECIMAL("小数", "BigDecimal", "decimal", null, 18, 2, FormType.NUMBER),
    /** 日期，只有年月日 */
    DATE("日期", "LocalDate", "date", null, null, null, FormType.DATE),
    /** 日期时间 */
    DATETIME("日期时间", "LocalDateTime", "datetime", null, null, null, FormType.DATETIME),
    /** 值域来自字典且按字符串存储，必须挂 dict_type；数字状态码用 INTEGER + dict_type 表达 */
    ENUM("字典枚举", "String", "varchar", 64, null, null, FormType.SELECT),
    /** 附件，存相对路径或 URL */
    FILE("附件", "String", "varchar", 512, null, null, FormType.UPLOAD),
    /** 关联引用，存目标行的主键 */
    REF("关联引用", "Long", "bigint", null, null, null, FormType.REF);

    private final String label;
    private final String javaType;
    private final String columnType;
    private final Integer defaultLength;
    private final Integer defaultPrecision;
    private final Integer defaultScale;
    private final FormType defaultFormType;

    FieldType(String label, String javaType, String columnType, Integer defaultLength,
              Integer defaultPrecision, Integer defaultScale, FormType defaultFormType) {
        this.label = label;
        this.javaType = javaType;
        this.columnType = columnType;
        this.defaultLength = defaultLength;
        this.defaultPrecision = defaultPrecision;
        this.defaultScale = defaultScale;
        this.defaultFormType = defaultFormType;
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }

    public FormType defaultFormType() {
        return defaultFormType;
    }

    /**
     * 推导 Java 类型。
     *
     * @param autoIncrement 自增主键：整数升级为 Long（与底座 BIGINT 主键一致）
     */
    public String javaType(boolean autoIncrement) {
        if (autoIncrement && this == INTEGER) {
            return "Long";
        }
        return javaType;
    }

    /**
     * 推导列类型，带长度 / 精度。
     *
     * @param length 字符串类列长度，空则用类型默认值
     * @param precision DECIMAL 精度，空则 18
     * @param scale DECIMAL 小数位，空则 2
     * @param autoIncrement 自增主键固定 bigint
     */
    public String columnType(Integer length, Integer precision, Integer scale, boolean autoIncrement) {
        if (autoIncrement && this == INTEGER) {
            return "bigint";
        }
        return switch (this) {
            case STRING, ENUM, FILE -> "%s(%d)".formatted(columnType, effectiveLength(length));
            case DECIMAL -> "%s(%d,%d)".formatted(columnType,
                    precision == null ? defaultPrecision : precision,
                    scale == null ? defaultScale : scale);
            default -> columnType;
        };
    }

    /** 字符串类列的有效长度：未填用类型默认值，填了则夹在 1–65535 之间（超过就该改用 TEXT） */
    public int effectiveLength(Integer length) {
        if (length == null || length <= 0) {
            return defaultLength == null ? 255 : defaultLength;
        }
        return Math.min(length, 16_383);
    }

    public Integer defaultLength() {
        return defaultLength;
    }

    public Integer defaultPrecision() {
        return defaultPrecision;
    }

    public Integer defaultScale() {
        return defaultScale;
    }

    /** 是否需要挂字典才能取值（ENUM 的值域就是字典，不填等于生成出一个空下拉） */
    public boolean requiresDict() {
        return this == ENUM;
    }

    /** 是否必须指向另一个模型 */
    public boolean requiresReference() {
        return this == REF;
    }

    public static FieldType of(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("字段类型不能为空");
        }
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("不支持的字段类型: " + code);
        }
    }

    /**
     * 「库里已有的列类型 → 字段类型」的反向映射（PR 5F）。
     *
     * <p>老代码生成页导入已有表时原来自己有一套 {@code javaType(dataType)}，把 {@code date} 也算成
     * {@code LocalDateTime}，与这里的正向表对不上 —— 同一个 {@code date} 列在两条链路上得到两种 Java 类型。
     * 反向映射放在本枚举里，是为了让两条链路共用同一份事实源；方案 §2.2 之二 已把这条列为 PR 5 的连带项。
     *
     * <p>认不出来的列类型（{@code time} / {@code year} / {@code bit} / 空间与二进制类型）落到 {@link #STRING}：
     * 老链路的表已经存在，生成的建表语句只是给人看的参考，不会因为多这一格就改到真实表；
     * 真要精确表达，回到模型里显式选类型。
     */
    public static FieldType ofColumn(String dataType) {
        String type = dataType == null ? "" : dataType.trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "bigint" -> LONG;
            case "int", "integer", "mediumint", "smallint", "tinyint" -> INTEGER;
            case "decimal", "numeric", "float", "double", "real" -> DECIMAL;
            case "date" -> DATE;
            case "datetime", "timestamp" -> DATETIME;
            case "text", "tinytext", "mediumtext", "longtext", "json" -> TEXT;
            default -> STRING;
        };
    }

    /**
     * 库里已有的列该用哪个查询方式（PR 5F）。
     *
     * <p>原来老链路不管列是什么类型，命中名字就一律 {@code LIKE}，于是给 {@code int} 状态列生成出
     * {@code LIKE CONCAT('%', ?, '%')} —— 索引用不上，读出来也不是人期望的等值匹配。
     * 模糊匹配只留给文本列。
     */
    public QueryType defaultQueryType() {
        return switch (this) {
            case STRING, TEXT -> QueryType.LIKE;
            default -> QueryType.EQ;
        };
    }
}
