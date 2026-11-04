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
}
