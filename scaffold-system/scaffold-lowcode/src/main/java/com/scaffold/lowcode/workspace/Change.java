package com.scaffold.lowcode.workspace;

/**
 * 一个文件在两提交之间的改动档位。
 *
 * <p>名字与 {@code lc_merge_request_file.change_type} 的取值逐字对齐，列表页按它决定行的读法：
 * ADD 是「这次要往主干加什么」，DELETE 是「这次要从主干拿走什么」，两者都需要人看，
 * 但只有 MODIFY 与 RENAME 才可能有行内冲突。
 */
public enum Change {
    ADD, MODIFY, DELETE, RENAME
}
