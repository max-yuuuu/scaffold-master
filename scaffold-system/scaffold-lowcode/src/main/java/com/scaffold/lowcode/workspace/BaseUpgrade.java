package com.scaffold.lowcode.workspace;

import java.util.List;

/**
 * 一趟底座升级的结果。
 *
 * @param oursSha   升级起点：项目主干当前那一笔（升级分支就从它长出来）
 * @param theirsSha 那份 A 档快照被做成的提交（无父、不挂在任何分支上）
 * @param commitSha 推出去的升级分支提交号；{@code null} 表示没有任何可自动合的内容，分支根本没建
 * @param branch    升级分支名，人去合并请求里选它合进主干
 * @param files     逐文件结论，冲突与「平台不再交付」的那些都在这里，内容由 {@link MergedFile#content()} 说话
 */
public record BaseUpgrade(String oursSha, String theirsSha, String commitSha, String branch, List<MergedFile> files) {

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
