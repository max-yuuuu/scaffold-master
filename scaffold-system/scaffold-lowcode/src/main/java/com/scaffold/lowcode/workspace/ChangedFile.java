package com.scaffold.lowcode.workspace;

/**
 * 变更清单里的一行：一个文件在两提交之间的改动。
 *
 * <p>{@code additions} / {@code deletions} 是行级计数（等价于 {@code git diff --numstat}），列表页要在
 * 不读 diff 全文的前提下让人看出这一改动是大是小；二进制文件按 0/0 计，Git 本来就不给它行差。
 *
 * <p>{@code oldPath} 只在改名或移动时有值，其余为 null —— 审查时「这个文件从哪来」只有这一种情形需要回答。
 */
public record ChangedFile(String path, String oldPath, Change change, int additions, int deletions) {
}
