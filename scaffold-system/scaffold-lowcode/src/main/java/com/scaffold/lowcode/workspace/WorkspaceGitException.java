package com.scaffold.lowcode.workspace;

import com.scaffold.common.exception.BusinessException;

/**
 * Git 侧的失败，带一个可直接写进 {@code lc_project.sync_status} 的异常态。
 *
 * <p>区分两类后果很有必要：网络 / 主机指纹 / 权限不通是「仓库不可达」（5），远端拒绝推送
 * （分支保护、历史不一致）是「与远端分叉」（4）；两者的处置动作完全不同，前者查平台到仓库的连通性，
 * 后者要在仓库侧做人做的事。
 */
public class WorkspaceGitException extends BusinessException {

    private final int syncStatus;

    public WorkspaceGitException(int syncStatus, String message) {
        super(message);
        this.syncStatus = syncStatus;
    }

    public int getSyncStatus() {
        return syncStatus;
    }
}
