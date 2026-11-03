package com.scaffold.framework.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/**
 * CSV 下载的统一下发形态（text/csv + UTF-8 + attachment 头）。
 * 与 {@code CsvUtils} 配套：拼装与下发都不各模块各写一套。
 */
public final class CsvResponse {

    private CsvResponse() {
    }

    /**
     * @param filename 下载文件名，如 {@code tenants.csv}
     * @param content  {@code CsvUtils.csv(...)} 生成的 CSV 正文
     */
    public static ResponseEntity<String> attachment(String filename, String content) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(content);
    }
}
