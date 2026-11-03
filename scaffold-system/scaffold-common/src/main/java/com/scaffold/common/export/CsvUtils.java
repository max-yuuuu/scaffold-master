package com.scaffold.common.export;

import java.util.Arrays;
import java.util.List;

/**
 * 列表页导出的统一 CSV 拼装工具。所有列表接口的导出都走这里，
 * 避免每个模块各写一套转义与表头拼接逻辑。
 */
public final class CsvUtils {

    private CsvUtils() {
    }

    /**
     * 拼装 CSV 文本。
     *
     * @param headers 表头（中文列名）
     * @param rows 数据行，每行的元素顺序与表头一致
     */
    public static String csv(List<String> headers, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", headers)).append('\n');
        for (List<Object> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(cell(row.get(i)));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 构造一行数据，配合 {@link #csv(List, List)} 使用 */
    public static List<Object> row(Object... values) {
        return Arrays.asList(values);
    }

    private static String cell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.contains("\"")) {
            text = text.replace("\"", "\"\"");
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text + "\"";
        }
        return text;
    }
}
