package com.scaffold.lowcode.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 {@link FieldType} / {@link FormType} / {@link QueryType} 的映射表原样吐给前端，
 * 让「类型 → Java 类型 / 列类型 / 默认控件」在界面上可见可核对。
 *
 * <p>这是刻意的单向依赖：前端不另存一份映射，改了枚举不用同步改前端。
 */
public final class ModelMeta {

    private ModelMeta() {
    }

    public static Map<String, Object> describe() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("fieldTypes", fieldTypes());
        meta.put("formTypes", formTypes());
        meta.put("queryTypes", queryTypes());
        return meta;
    }

    private static List<Map<String, Object>> fieldTypes() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (FieldType type : FieldType.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", type.code());
            item.put("label", type.label());
            item.put("javaType", type.javaType(false));
            item.put("columnType", type.columnType(null, null, null, false));
            item.put("formType", type.defaultFormType().name());
            item.put("defaultLength", type.defaultLength());
            item.put("defaultPrecision", type.defaultPrecision());
            item.put("defaultScale", type.defaultScale());
            item.put("requiresDict", type.requiresDict());
            item.put("requiresReference", type.requiresReference());
            items.add(item);
        }
        return items;
    }

    private static List<Map<String, Object>> formTypes() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (FormType type : FormType.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", type.name());
            item.put("label", type.label());
            items.add(item);
        }
        return items;
    }

    private static List<Map<String, Object>> queryTypes() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (QueryType type : QueryType.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", type.code());
            item.put("operator", type.operator());
            item.put("label", type.label());
            items.add(item);
        }
        return items;
    }
}
