package com.scaffold.lowcode.design;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * design_json 里的一个节点：组件类型 + 属性 + 子节点（容器）或列（表格）。
 *
 * <p>{@code path} 是从顶层数下来的位置（{@code layout.children[0].children[1]}），
 * 校验报错带着它，设计器属性面板才能把画布上那个方块高亮出来。
 *
 * @param props 属性表，值已经是 Java 类型（字符串 / 数字 / 布尔），模板与校验都直接取
 */
public record DesignNode(String type, String path, Map<String, Object> props,
                         List<DesignNode> children, List<DesignNode> columns) {

    public DesignNode {
        props = new LinkedHashMap<>(props);
        children = List.copyOf(children);
        columns = List.copyOf(columns);
    }

    /** 属性缺失或空白时返回 {@code null}，让调用方自己决定默认值 */
    public String text(String key) {
        Object value = props.get(key);
        if (value == null) {
            return null;
        }
        String trimmed = String.valueOf(value).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public String text(String key, String fallback) {
        String value = text(key);
        return value == null ? fallback : value;
    }

    public Integer number(String key) {
        Object value = props.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = text(key);
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public boolean flag(String key, boolean fallback) {
        Object value = props.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = text(key);
        if (text == null) {
            return fallback;
        }
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    /** 组件档案：解析阶段已确认类型在白名单里，这里只可能取到条目 */
    public ComponentCatalog.Entry entry() {
        return ComponentCatalog.of(type);
    }
}
