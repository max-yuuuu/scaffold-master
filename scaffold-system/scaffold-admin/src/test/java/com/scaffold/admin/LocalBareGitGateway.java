package com.scaffold.admin;

import com.scaffold.common.exception.BusinessException;
import com.scaffold.lowcode.workspace.ChangedFile;
import com.scaffold.lowcode.workspace.GitGateway;
import com.scaffold.lowcode.workspace.JGitGateway;
import com.scaffold.lowcode.workspace.MergeCheck;
import com.scaffold.lowcode.workspace.ProjectMarker;
import com.scaffold.lowcode.workspace.RemoteRef;
import org.eclipse.jgit.api.Git;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * 把真 JGit 接到本机临时裸仓库上的测试网关。
 *
 * <p>为什么这条链路不用 {@link StubGitGateway}：PR 7 的验收四条全部是 Git 语义 ——「重复发布不产生空
 * commit」「推送失败不留下半成品」「tag 与 commit_sha 对得上」。桩远端只能证明平台自己怎么记账，
 * 证不了仓库里真有什么；而 {@code JGitGatewayTest} 验的是单个原语的语义，没验「发布链路用这些原语
 * 跑出来的那个仓库长什么样」。两边合起来才盖住 PR 7 的风险面。
 *
 * <p>项目仓库地址照旧写 {@code https://} 或 {@code git@} 形式：{@code RepoUrlValidator} 的协议白名单
 * 是安全边界（防的是把 file:// 之类的本地路径推进别人的仓库），不为测试放开。映射按地址确定性生成，
 * 同一个地址永远落到同一个裸仓库，所以「远端被第三方推进」这种场景能在用例里手工造出来。
 */
public class LocalBareGitGateway implements GitGateway {

    static final Path REMOTE_ROOT = Path.of(System.getProperty("java.io.tmpdir"), "scaffold-it-publish-remote");

    private final JGitGateway jgit = new JGitGateway(30, "元搭平台", "platform@yuanda.local", "");

    /** 下一次推送被远端拒绝（分支保护、网络断、仓库被删的等价物），用来验「推送失败不留半成品」 */
    private volatile boolean refuseNextPush;

    @Override
    public List<RemoteRef> lsRemote(String repoUrl) {
        return jgit.lsRemote(bareUrl(repoUrl));
    }

    @Override
    public void initEmpty(Path dir, String repoUrl, String initialBranch) {
        jgit.initEmpty(dir, bareUrl(repoUrl), initialBranch);
    }

    @Override
    public void cloneBranch(Path dir, String repoUrl, String branch) {
        jgit.cloneBranch(dir, bareUrl(repoUrl), branch);
    }

    @Override
    public void checkoutBranch(Path dir, String branch, String startPoint) {
        jgit.checkoutBranch(dir, branch, startPoint);
    }

    @Override
    public String commitAll(Path dir, String message) {
        return jgit.commitAll(dir, message);
    }

    @Override
    public void pushBranch(Path dir, String branch) {
        if (refuseNextPush) {
            refuseNextPush = false;
            throw new BusinessException("远端拒绝接收 " + branch + "（用例摆的传输失败）");
        }
        jgit.pushBranch(dir, branch);
    }

    @Override
    public boolean isDirty(Path dir) {
        return jgit.isDirty(dir);
    }

    @Override
    public String localBranchSha(Path dir, String branch) {
        return jgit.localBranchSha(dir, branch);
    }

    @Override
    public boolean isValidBranch(String branch) {
        return jgit.isValidBranch(branch);
    }

    @Override
    public String headSha(Path repoDir) {
        return jgit.headSha(repoDir);
    }

    @Override
    public boolean tagCommit(Path dir, String tagName, String sha, String message) {
        return jgit.tagCommit(dir, tagName, sha, message);
    }

    @Override
    public void pushTag(Path dir, String tagName) {
        if (refuseNextPush) {
            refuseNextPush = false;
            throw new BusinessException("远端拒绝接收 tag " + tagName + "（用例摆的 tag 保护）");
        }
        jgit.pushTag(dir, tagName);
    }

    /* ── 合并请求的只读原语：都是读本份工作区，不涉及远端，直接转给真 JGit ── */

    @Override
    public void fetch(Path dir) {
        jgit.fetch(dir);
    }

    @Override
    public String mergeBase(Path dir, String oneRev, String otherRev) {
        return jgit.mergeBase(dir, oneRev, otherRev);
    }

    @Override
    public List<ChangedFile> diffFiles(Path dir, String baseSha, String headSha) {
        return jgit.diffFiles(dir, baseSha, headSha);
    }

    @Override
    public MergeCheck checkMerge(Path dir, String oursRev, String theirsRev) {
        return jgit.checkMerge(dir, oursRev, theirsRev);
    }

    @Override
    public String fileDiff(Path dir, String baseSha, String headSha, String path) {
        return jgit.fileDiff(dir, baseSha, headSha, path);
    }

    /** 取历史内容也照真 JGit 走：回滚用例验的就是「那一版里这个文件到底是什么」，桩给不出这个答案 */
    @Override
    public Map<String, String> fileContents(Path dir, String commitRev, List<String> paths) {
        return jgit.fileContents(dir, commitRev, paths);
    }

    /**
     * 合并照真 JGit 走，但远端地址要映射到本机裸仓库 —— 否则用例会去推真 GitHub。
     *
     * <p>平台代码传进来的是库里的 {@code https://} / {@code git@} 地址，这条映射是本文件其余
     * 方法一直在做的那件事，合并这条路同样不能漏。
     */
    @Override
    public String mergeIntoTarget(Path workspace, String repoUrl, String targetBranch, String expectedTargetSha,
                                  String sourceSha, String message) {
        return jgit.mergeIntoTarget(workspace, bareUrl(repoUrl), targetBranch, expectedTargetSha, sourceSha, message);
    }

    /* ── 用例侧的远端读数与「第三方」动作 ── */

    /** 让下一次推送（分支或 tag）失败一次，之后的恢复原样 */
    void refuseNextPush() {
        refuseNextPush = true;
    }

    /** 远端分支当前提交号；分支不存在返回 null */
    String branchSha(String repoUrl, String branch) {
        return lsRemote(repoUrl).stream()
                .filter(ref -> branch.equals(ref.branchName()))
                .map(RemoteRef::sha)
                .findFirst()
                .orElse(null);
    }

    /** 远端 tag 指向的提交号；tag 不存在返回 null */
    String tagSha(String repoUrl, String tagName) {
        return lsRemote(repoUrl).stream()
                .filter(ref -> tagName.equals(ref.tagName()))
                .map(RemoteRef::commitSha)
                .findFirst()
                .orElse(null);
    }

    /** 远端是否还有这条分支（主干该一直由人掌控，平台不该写它） */
    boolean hasBranch(String repoUrl, String branch) {
        return branchSha(repoUrl, branch) != null;
    }

    /** 用例结束后按地址清掉对应裸仓库 */
    Path remoteDirOf(String repoUrl) {
        return REMOTE_ROOT.resolve(slug(repoUrl) + ".git");
    }

    /**
     * 在项目仓库上先建出主干：真实世界里的项目仓库本来就有主干，初始化走的是
     * 「clone 主干 → 从主干切发布分支」那条路（§6.3 还要认得出这是脚手架仓库）。
     */
    void seedMainBranch(String repoUrl, String projectCode) {
        Path dir = REMOTE_ROOT.resolve("seed-" + slug(repoUrl));
        deleteRecursively(dir);
        jgit.initEmpty(dir, bareUrl(repoUrl), "main");
        ProjectMarker.write(dir, projectCode, "发布链路验收", null, "main", "platform-generated");
        if (jgit.commitAll(dir, "chore: 项目主干的第一次提交") == null) {
            throw new AssertionError("主干种子提交没建出来");
        }
        jgit.pushBranch(dir, "main");
        deleteRecursively(dir);
    }

    /**
     * 模拟项目侧的人在发布分支上改一个文件再推回来：另开一份 clone，改完提交推送。
     *
     * <p>走的是真远端，所以平台工作区对此一无所知 —— 这正是「C 档存在即跳过」的前提
     * （方案 §5.2：调用方给的工作区要反映项目当前内容）要在发布链路上兑现的那件事。
     */
    void humanEditAndPush(String repoUrl, String branch, String relativeFile, UnaryOperator<String> edit) {
        Path clone = cloneOf(repoUrl, branch);
        try {
            Path file = clone.resolve(relativeFile);
            String before = read(file);
            write(file, edit.apply(before));
            if (jgit.commitAll(clone, "chore: 人工在发布分支上补扩展代码") == null) {
                throw new AssertionError("人工改动没有产生提交，用例构造错了：" + relativeFile);
            }
            jgit.pushBranch(clone, branch);
        } finally {
            deleteRecursively(clone);
        }
    }

    /** 给用例一份能直接读的本地 clone，用来在平台工作区里造「人改过没提交」的状态 */
    private Path cloneOf(String repoUrl, String branch) {
        Path dir = REMOTE_ROOT.resolve("clone-" + System.nanoTime());
        try {
            Files.createDirectories(dir.getParent());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        jgit.cloneBranch(dir, bareUrl(repoUrl), branch);
        return dir;
    }

    /** 地址 → 本机裸仓库：不存在就当场建一个空裸仓库，等价于刚在 GitHub 上建好的空项目 */
    private String bareUrl(String repoUrl) {
        Path bare = REMOTE_ROOT.resolve(slug(repoUrl) + ".git");
        if (!Files.isDirectory(bare)) {
            try {
                Files.createDirectories(bare);
                try (Git ignored = Git.init().setBare(true).setDirectory(bare.toFile()).call()) {
                    // 只当远端用，本身不做任何提交
                }
            } catch (Exception e) {
                throw new IllegalStateException("建不出本机裸仓库 " + bare + "：" + e.getMessage(), e);
            }
        }
        return bare.toUri().toString();
    }

    private static String slug(String repoUrl) {
        return repoUrl.replaceAll("[^A-Za-z0-9]", "_");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("读不到 " + file, e);
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("写不进 " + file, e);
        }
    }

    static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // 临时目录清不掉不影响结论，下次跑前重建
        }
    }

    /** 换上真 JGit：被测的是发布链路在真仓库上的结果，不是桩的记账 */
    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        GitGateway localBareGitGateway() {
            return new LocalBareGitGateway();
        }
    }
}
