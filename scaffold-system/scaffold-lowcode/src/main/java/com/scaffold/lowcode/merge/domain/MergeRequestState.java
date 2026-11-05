package com.scaffold.lowcode.merge.domain;

/**
 * 合并请求的两套状态（{@code lc_merge_request}，方案 §5.4、PR 8 行）。
 *
 * <p><b>为什么是两列而不是一个状态机</b>：{@code status} 说的是「这一条请求还作不作数」，
 * {@code check_status} 说的是「这两份内容能不能自动合」。目标分支又前进了一步不会让内容变冲突，
 * 内容冲突也不会让这条请求过期 —— 合成一列要么互相覆盖，要么出现「陈旧且有冲突」这种
 * 没人能在界面上显示的枚举值。
 */
public final class MergeRequestState {

    /* ── status：请求的生命周期 ── */

    /** 待审：清单与预检结论都是当前这一份，等着人看 */
    public static final int PENDING = 0;
    /** 陈旧：目标分支已经前进，这条请求算的是旧的那一对提交，必须重算才能合 */
    public static final int STALE = 1;
    /** 已合并：第二刀（确认合并与推送）才会写在这里 */
    public static final int MERGED = 2;
    /** 已关闭：人不合了 */
    public static final int CLOSED = 3;

    /* ── check_status：内容能不能自动合 ── */

    /** 还没跑过预检 */
    public static final int CHECK_NOT_YET = 0;
    /** 可自动合并：试算没有冲突 */
    public static final int CHECK_CLEAN = 1;
    /** 有冲突：只列文件，谁赢交给人 */
    public static final int CHECK_CONFLICT = 2;

    private MergeRequestState() {
    }

    public static String text(int status) {
        return switch (status) {
            case PENDING -> "待审";
            case STALE -> "陈旧";
            case MERGED -> "已合并";
            case CLOSED -> "已关闭";
            default -> "未知";
        };
    }

    public static String checkText(int checkStatus) {
        return switch (checkStatus) {
            case CHECK_NOT_YET -> "未预检";
            case CHECK_CLEAN -> "可自动合并";
            case CHECK_CONFLICT -> "有冲突";
            default -> "未知";
        };
    }
}
