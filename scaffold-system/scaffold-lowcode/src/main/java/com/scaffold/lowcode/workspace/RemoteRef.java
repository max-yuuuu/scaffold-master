package com.scaffold.lowcode.workspace;

/**
 * 远端仓库的一个引用（{@code ls-remote} 的一行）。
 *
 * @param name 引用全名，如 {@code refs/heads/main}
 * @param sha  引用指向的提交号
 */
public record RemoteRef(String name, String sha) {

    private static final String HEADS_PREFIX = "refs/heads/";

    public String branchName() {
        return name.startsWith(HEADS_PREFIX) ? name.substring(HEADS_PREFIX.length()) : null;
    }

    public boolean isBranch() {
        return branchName() != null;
    }
}
