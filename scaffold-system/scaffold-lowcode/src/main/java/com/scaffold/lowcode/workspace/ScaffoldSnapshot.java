package com.scaffold.lowcode.workspace;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 一次裁剪的产物：交付项目应当具备的完整文件清单（相对路径 + 内容来源）。
 *
 * <p>它是「发布产物的定义」，所以初始化写入、重建工作区、比对差异都以这一份为准，
 * 不靠人工拷目录。
 */
public record ScaffoldSnapshot(List<SnapshotFile> files) {

    public Set<String> paths() {
        return files.stream().map(SnapshotFile::path).collect(Collectors.toUnmodifiableSet());
    }

    public SnapshotFile find(String path) {
        return files.stream().filter(file -> file.path().equals(path)).findFirst().orElse(null);
    }
}
