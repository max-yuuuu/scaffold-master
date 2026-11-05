package com.scaffold.lowcode.workspace;

import java.util.List;

/**
 * 一次「把源分支合进目标分支」的试算结论，全程不产生提交、不写索引、不动工作区文件。
 *
 * <p>只有结论与要人处理的文件清单：<b>冲突谁赢不作答</b>（方案 PR 8 验收原话「冲突场景只列文件
 * 不自动取舍」），要人解的一律交人。
 *
 * <p>{@code note} 只在「合不上但没标出内容冲突」时有值 —— 例如工作区本来就脏，JGit 直接拒绝试算，
 * 这时文件清单是空的，不给原因的话页面上就只剩一条「不行」而没人知道为什么不行。
 */
public record MergeCheck(boolean clean, List<String> conflictedPaths, String note) {

    /** 写进 {@code lc_merge_request.check_message} 的那一行；服务侧再按列宽截断 */
    public String summary() {
        if (clean) {
            return "可自动合并：两边改动不重叠";
        }
        String detail = conflictedPaths.isEmpty()
                ? "试算未通过：" + (note == null ? "原因未知" : note)
                : "冲突文件 " + conflictedPaths.size() + " 个，必须人解：" + String.join("、", conflictedPaths);
        return note == null || conflictedPaths.isEmpty() ? detail : detail + "（" + note + "）";
    }
}
