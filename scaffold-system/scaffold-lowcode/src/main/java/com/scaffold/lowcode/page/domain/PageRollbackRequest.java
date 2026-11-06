package com.scaffold.lowcode.page.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 页面回滚请求（PR 9，方案 §5.4）。
 *
 * <p>{@code version} 必填：回滚必须说清「退回哪一版」，而「最近但不是当前的那一版」这种默认值
 * 会让人以为自己在退一步、实际退了两步。版本号从版本历史那一列里点出来，不靠人记。
 *
 * <p>{@code files} 为空就是整页回滚；给了就是文件级，路径必须属于 {@code version} 那一版且是 B 档。
 */
public class PageRollbackRequest {

    /** 要退回的版本号（lc_page_version.version，也是 tag 末尾那个数） */
    @NotNull(message = "要退回的版本号不能为空")
    @Min(value = 1, message = "版本号从 1 开始")
    private Integer version;

    /** 只还原这些文件（文件级回滚）；为空表示整页回滚 */
    private List<String> files;

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public List<String> getFiles() {
        return files;
    }

    public void setFiles(List<String> files) {
        this.files = files;
    }
}
