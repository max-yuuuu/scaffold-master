package com.scaffold.lowcode.workspace;

import java.util.List;

/**
 * 一趟底座升级的结果。
 *
 * @param oursSha   升级起点：项目主干当前那一笔（升级分支就从它长出来）
 * @param theirsSha 那份 A 档快照被做成的提交（无父、不挂在任何分支上）
 * @param commitSha 推出去的升级分支提交号；{@code null} 表示没有任何可自动合的内容，分支根本没建
 * @param branch    升级分支名，人去合并请求里选它合进主干
 * @param files     逐文件结论，冲突与「平台撤掉」的那些都在这里，内容由 {@link MergedFile#content()} 说话
 */
public record BaseUpgrade(String oursSha, String theirsSha, String commitSha, String branch, List<MergedFile> files) {

    /**
     * 升级分支的命名空间前缀。分支名里带底座短号，所以一个底座版本一条分支 ——
     * 同名再推只能靠 force，而平台永不强推（{@code JGitGateway.baseUpgrade} 里那道拒绝）。
     *
     * <p>合并请求那边按这个前缀认来源分支，所以常量只在这里有一份。
     */
    public static final String UPGRADE_BRANCH_PREFIX = "platform-upgrade/";

    /** 升级分支名：前缀 + 底座短号（7 位是 git 自己的缩写习惯，够人认也不会撞到分支名长度） */
    public static String branchName(String scaffoldSha) {
        return UPGRADE_BRANCH_PREFIX + scaffoldSha.substring(0, Math.min(7, scaffoldSha.length()));
    }

    public long count(MergeOutcome outcome) {
        return files.stream().filter(file -> file.outcome() == outcome).count();
    }

    public List<String> pathsOf(MergeOutcome outcome) {
        return files.stream().filter(file -> file.outcome() == outcome).map(MergedFile::path).toList();
    }

    public boolean pushed() {
        return commitSha != null;
    }
}
