package com.scaffold.lowcode.workspace;

import java.nio.file.Path;

/**
 * 交付快照里的一个文件。
 *
 * @param path      交付项目内的相对路径（{@code /} 分隔）
 * @param copyFrom  底座里的源文件，内容与源一致时直接按字节拷贝；为 null 表示内容已在平台侧改写
 * @param content   改写后的内容（删掉了平台专属标记块），未改写时为 null
 */
public record SnapshotFile(String path, Path copyFrom, byte[] content) {

    static SnapshotFile verbatim(String path, Path source) {
        return new SnapshotFile(path, source, null);
    }

    static SnapshotFile rewritten(String path, byte[] content) {
        return new SnapshotFile(path, null, content);
    }

    public boolean needsCopy() {
        return copyFrom != null;
    }
}
