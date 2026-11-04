package com.scaffold.lowcode.design;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一份 design_json 要对照的模型事实（方案 §1.5：页面不猜字段）。
 *
 * <p>入参形状是 {@code lc_field} 查出来的宽松 Map，与生成器吃的同一份一致 —— 校验器和生成器
 * 必须看同一份事实，否则「保存时放过去、发布时炸掉」这类错位又会出现在链尾。
 *
 * @param modelRef 页面绑定的模型编码，设计里的 {@code modelRef} 与它不一致就是跨模型复制了设计
 */
public record DesignContext(String modelRef, List<Field> fields) {

    /**
     * 模型里的一个字段。
     *
     * @param autoKey 主键且自增：值由数据库给，进表单等于让人猜下一个 id（5G 的真机缺陷）
     * @param writable 模型侧允许写入（{@code is_insert} 或 {@code is_edit}）：都不允许的字段进了表单，
     *                 生成的 INSERT / UPDATE 里没有这一列，人在页面上填完就静默丢掉
     */
    public record Field(String code, String name, String javaType, String formType, String dictType,
                        boolean primary, boolean autoKey, boolean writable) {

        public boolean hasDict() {
            return dictType != null && !dictType.isBlank();
        }

        public boolean numeric() {
            return switch (javaType) {
                case "Integer", "Long", "BigDecimal", "Double", "Float" -> true;
                default -> false;
            };
        }

        public boolean dateLike() {
            return "LocalDate".equals(javaType) || "LocalDateTime".equals(javaType);
        }
    }

    public static DesignContext of(String modelRef, List<Map<String, Object>> fieldRows) {
        List<Field> fields = new ArrayList<>();
        for (Map<String, Object> row : fieldRows) {
            String code = text(row.get("fieldCode"));
            fields.add(new Field(code,
                    blankToDefault(text(row.get("fieldName")), code),
                    blankToDefault(text(row.get("javaType")), "String"),
                    blankToDefault(text(row.get("formType")), "INPUT"),
                    text(row.get("dictType")),
                    truth(row.get("isPrimary")),
                    truth(row.get("isPrimary")) && truth(row.get("isAutoIncrement")),
                    truth(row.get("isInsert")) || truth(row.get("isEdit"))));
        }
        return new DesignContext(modelRef, List.copyOf(fields));
    }

    public Field find(String code) {
        if (code == null) {
            return null;
        }
        return fields.stream().filter(field -> field.code().equals(code)).findFirst().orElse(null);
    }

    /** 报错里给的操作者提示：可用的字段编码按模型顺序列出来 */
    public String fieldCodes() {
        return fields.stream().map(Field::code).toList().toString();
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = String.valueOf(value).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }

    /** 行数据里的布尔既可能是 Boolean，也可能是 MySQL 查出来的 0/1 */
    private static boolean truth(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = value == null ? null : String.valueOf(value);
        return text != null && ("1".equals(text) || "true".equalsIgnoreCase(text));
    }
}
