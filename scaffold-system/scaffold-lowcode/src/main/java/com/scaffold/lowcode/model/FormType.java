package com.scaffold.lowcode.model;

/**
 * 表单控件类型。生成页面时决定字段渲染成哪个组件，与底座既有组件一一对应
 * （{@code DateInput} / {@code NumberInput} / {@code DictSelect} / {@code Upload} …）。
 */
public enum FormType {

    INPUT("单行输入"),
    TEXTAREA("多行输入"),
    NUMBER("数字输入"),
    SELECT("下拉选择"),
    RADIO("单选组"),
    CHECKBOX("多选组"),
    SWITCH("开关"),
    DATE("日期选择"),
    DATETIME("日期时间选择"),
    UPLOAD("附件上传"),
    REF("关联选择");

    private final String label;

    FormType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 与 {@link FieldType#code()} / {@link QueryType#code()} 同一口径：给前端与生成物用的枚举名 */
    public String code() {
        return name();
    }

    public static FormType of(String code, FormType fallback) {
        if (code == null || code.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(code.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
