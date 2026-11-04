package com.scaffold.lowcode.project.domain;

/**
 * 项目生命周期状态（{@code lc_project.sync_status}，方案 §2.2 已把原 init_status / sync_status 两套合并成一条）。
 *
 * <p>它是项目唯一的状态机：正常路径 0 → 1 → 2，异常态 3 / 4 / 5 由仓库与工作区动作写入，
 * 处置完回到原状态。文案由 {@link #text(int)} 统一给出，接口与页面都读这一处，
 * 不再在 SQL 里写第二套 CASE。
 */
public final class ProjectSyncStatus {

    /** 未初始化：还没往租户仓库推过固定代码 */
    public static final int UNINITIALIZED = 0;
    /** 初始化中：正在读远端、写快照、提交与推送 */
    public static final int INITIALIZING = 1;
    /** 就绪：固定代码已在发布分支上，可以开始建模型与页面 */
    public static final int READY = 2;
    /** 工作区脏：本地有未提交改动，平台不会覆盖它们，需要人先处理 */
    public static final int DIRTY = 3;
    /** 与远端分叉：远端拒绝推送或本地与远端不一致，需要人在仓库侧解决 */
    public static final int DIVERGED = 4;
    /** 仓库不可达：地址、网络、主机指纹或权限有问题 */
    public static final int UNREACHABLE = 5;

    private ProjectSyncStatus() {
    }

    public static String text(int status) {
        return switch (status) {
            case UNINITIALIZED -> "未初始化";
            case INITIALIZING -> "初始化中";
            case READY -> "就绪";
            case DIRTY -> "工作区脏";
            case DIVERGED -> "与远端分叉";
            case UNREACHABLE -> "仓库不可达";
            default -> "未知";
        };
    }
}
