package com.scaffold.lowcode.page;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * 页面引用清单（lc_page.ref_fields）的读法。
 *
 * <p>删字段与下线模型两处都要按它反查，读法只放这一处 —— 两处各写一遍的话，其中一处漏了
 * 「JSON 是 null」或「元素不是字符串」，报错就会变成 NPE 而不是「哪个页面还在用」。
 */
public final class PageReferences {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PageReferences() {
    }

    /** {@code ref_fields} 摊成字段编码清单；库里这一列可以是 NULL（老数据或没绑字段的设计） */
    public static List<String> fieldsOf(Object raw) {
        if (raw == null) {
            return List.of();
        }
        try {
            return MAPPER.readValue(String.valueOf(raw), new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    /** 哪些页面正在用这个字段 */
    public static List<Map<String, Object>> using(List<Map<String, Object>> pages, String fieldCode) {
        return pages.stream()
                .filter(page -> fieldsOf(page.get("refFields")).contains(fieldCode))
                .toList();
    }

    /** 「名称（编码）、名称（编码）」—— 报给人看的清单要能直接照着去设计器里找 */
    public static String names(List<Map<String, Object>> pages) {
        return pages.stream()
                .map(page -> page.get("pageName") + "（" + page.get("pageCode") + "）")
                .toList()
                .toString();
    }
}
