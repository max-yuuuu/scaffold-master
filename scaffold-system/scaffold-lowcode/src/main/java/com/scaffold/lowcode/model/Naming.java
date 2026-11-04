package com.scaffold.lowcode.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 编码 → 表名 / 类名 / 列名 / 包名的派生规则。模型与字段新建时按这里给默认值，
 * 用户可以在表单里改（改完以库里存的为准），所以这些方法对已派生过的值必须幂等：
 * {@code orderService}、{@code OrderService}、{@code order_service} 三种写法都拆成同一组词。
 */
public final class Naming {

    /** 分隔符：空格、连字符、下划线、点 */
    private static final String SEPARATORS = "[\\s._\\-]+";

    /** 词内的大小写边界：小写或数字后接大写、以及连续大写后接「大写+小写」（HTTPServer → HTTP, Server） */
    private static final String CASE_BOUNDARY = "(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])";

    private Naming() {
    }

    /** order_service / orderService / Order Service → [order, service] */
    public static List<String> words(String code) {
        if (code == null || code.isBlank()) {
            return List.of();
        }
        List<String> words = new ArrayList<>();
        for (String part : code.trim().split(SEPARATORS)) {
            if (part.isEmpty()) {
                continue;
            }
            for (String word : part.split(CASE_BOUNDARY)) {
                if (!word.isEmpty()) {
                    words.add(word.toLowerCase(Locale.ROOT));
                }
            }
        }
        return words;
    }

    /** orderService / OrderService / order-service → order_service（表名与列名） */
    public static String snake(String code) {
        return String.join("_", words(code));
    }

    /** order_service / orderService → OrderService（Java 类名） */
    public static String pascal(String code) {
        StringBuilder out = new StringBuilder();
        words(code).forEach(word -> out.append(capitalize(word)));
        return out.toString();
    }

    /** order_service / OrderService → orderService（Java 属性名与前端字段名） */
    public static String camel(String code) {
        String pascal = pascal(code);
        return pascal.isEmpty() ? pascal : Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    /** 任意写法收成一个合法的小写包名片段 */
    public static String packageSegment(String code) {
        return snake(code);
    }

    private static String capitalize(String word) {
        return word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}
