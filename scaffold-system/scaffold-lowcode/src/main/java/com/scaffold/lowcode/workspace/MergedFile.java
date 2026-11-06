package com.scaffold.lowcode.workspace;

/**
 * 底座升级的逐文件结论。
 *
 * @param path    相对仓库根的路径，用 {@code /} 分隔
 * @param outcome 三方合并落在哪一档，语义见 {@link MergeOutcome}
 * @param content 只有 {@link MergeOutcome#CLEAN} 才给内容；其余一律 {@code null} ——
 *                带着冲突标记的文本进升级分支，等于平台把没编译过的东西推给人
 */
public record MergedFile(String path, MergeOutcome outcome, String content) {

    public boolean writable() {
        return outcome == MergeOutcome.CLEAN;
    }
}
