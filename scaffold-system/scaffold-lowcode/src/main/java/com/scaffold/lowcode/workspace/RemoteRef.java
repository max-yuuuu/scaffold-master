package com.scaffold.lowcode.workspace;

/**
 * 远端仓库的一个引用（{@code ls-remote} 的一行）。
 *
 * @param name     引用全名，如 {@code refs/heads/main}
 * @param sha      引用本身指向的对象号 —— 分支是提交，附注 tag 是 tag 对象自己的号
 * @param peeledSha 附注 tag 解引用之后的提交号；非 tag 或远端没给解引用行时为 {@code null}
 */
public record RemoteRef(String name, String sha, String peeledSha) {

    /** 没有解引用行时直接用这个构造，读起来与之前一致 */
    public RemoteRef(String name, String sha) {
        this(name, sha, null);
    }

    private static final String HEADS_PREFIX = "refs/heads/";
    private static final String TAGS_PREFIX = "refs/tags/";

    /**
     * 这个引用真正对应的提交。
     *
     * <p>取内容、比版本、回滚都要按这个值走而不是 {@link #sha()}：附注 tag 的 {@code sha} 是 tag 对象的号，
     * 拿它去仓库里找那一次的代码是找不到的，而「tag 与 commit_sha 对不对得上」这条验收正是比这个。
     */
    public String commitSha() {
        return peeledSha != null && !peeledSha.isBlank() ? peeledSha : sha;
    }

    public String branchName() {
        return name.startsWith(HEADS_PREFIX) ? name.substring(HEADS_PREFIX.length()) : null;
    }

    public boolean isBranch() {
        return branchName() != null;
    }

    /** tag 名，不是 tag 时返回 {@code null}；附注 tag 的 {@code ^{}} 解引用行同样返回 {@code null} */
    public String tagName() {
        if (!name.startsWith(TAGS_PREFIX) || name.endsWith("^{}")) {
            return null;
        }
        return name.substring(TAGS_PREFIX.length());
    }
}
