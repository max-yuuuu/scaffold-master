package com.scaffold.lowcode.workspace;

import com.scaffold.common.exception.BusinessException;
import com.scaffold.lowcode.project.domain.ProjectSyncStatus;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JGit 行为验证（方案 §10.3 标注的技术风险点之一）。
 *
 * <p>这里用本机临时裸仓库当真仓库跑，覆盖最容易出事的几条语义：空仓库的未出生 HEAD 能不能直接
 * 提交并显式建分支、非快进推送会不会被如实拒绝（平台不强推）、脏与未推送能不能查出来、
 * 底座标记 tag 能不能创建推送且同名时不被移动。
 * 远端主机（GitHub / 自建 GitLab）的 SSH 与 known_hosts 行为仍需要在部署时再跑一次真机验证。
 */
class JGitGatewayTest {

    private final JGitGateway gateway = new JGitGateway(60, "元搭平台", "platform@yuanda.local", "");

    @TempDir
    Path tempDir;

    private String remoteUrl;

    @BeforeEach
    void createEmptyRemote() throws Exception {
        Path bare = tempDir.resolve("remote.git");
        Files.createDirectories(bare);
        try (Git ignored = Git.init().setBare(true).setDirectory(bare.toFile()).call()) {
            // 裸仓库只当远端用，本身不做任何提交，等价于刚在 GitHub 上建的空仓库
        }
        remoteUrl = bare.toUri().toString();
    }

    @Test
    void 空仓库首次初始化能显式建出发布分支() throws IOException {
        assertThat(gateway.lsRemote(remoteUrl)).isEmpty();

        Path workspace = tempDir.resolve("workspace");
        gateway.initEmpty(workspace, remoteUrl, "platform-generated");
        // 未出生 HEAD：此时既没有提交也没有本地分支，与 git status 的空仓库一致
        assertThat(gateway.localBranchSha(workspace, "platform-generated")).isNull();

        write(workspace, "README.md", "# demo\n");
        write(workspace, "scaffold-system/pom.xml", "<project/>\n");
        String sha = gateway.commitAll(workspace, "chore: 初始化固定代码");
        assertThat(sha).isNotBlank();

        gateway.pushBranch(workspace, "platform-generated");

        List<RemoteRef> refs = gateway.lsRemote(remoteUrl);
        assertThat(refs).anySatisfy(ref -> {
            assertThat(ref.name()).isEqualTo("refs/heads/platform-generated");
            assertThat(ref.sha()).isEqualTo(sha);
        });
        assertThat(gateway.localBranchSha(workspace, "platform-generated")).isEqualTo(sha);
        // 本地引用名要正好是 refs/heads/{分支}：JGit 的 resolve 会宽松补前缀，双层的
        // refs/heads/refs/heads/{分支} 照样读得到号，而 findRef 是精确查的 —— checkoutBranch 那句
        // 「这条分支本地有了吗」就会答错，回头去新建一条已经存在的分支（initEmpty 曾踩过）
        try (Git git = Git.open(workspace.toFile())) {
            assertThat(git.getRepository().findRef("refs/heads/platform-generated")).isNotNull();
            assertThat(git.getRepository().getRefDatabase().getRefs())
                    .allSatisfy(ref -> assertThat(ref.getName()).doesNotStartWith("refs/heads/refs/"));
        }
    }

    @Test
    void 没有改动时不产生空提交() {
        Path workspace = tempDir.resolve("workspace");
        gateway.initEmpty(workspace, remoteUrl, "platform-generated");
        assertThat(gateway.commitAll(workspace, "chore: 空提交")).isNull();

        write(workspace, "README.md", "第一版\n");
        String first = gateway.commitAll(workspace, "chore: 第一次提交");
        assertThat(first).isNotBlank();

        // 再提交一次同样的内容：工作区干净，不该冒出第二个提交
        assertThat(gateway.commitAll(workspace, "chore: 重复提交")).isNull();
    }

    @Test
    void 脏工作区与未推送提交都能查出来() {
        Path workspace = tempDir.resolve("workspace");
        gateway.initEmpty(workspace, remoteUrl, "platform-generated");
        write(workspace, "a.txt", "1\n");
        String sha = gateway.commitAll(workspace, "chore: 提交但不推送");

        assertThat(gateway.isDirty(workspace)).isFalse();
        assertThat(gateway.lsRemote(remoteUrl)).isEmpty();
        assertThat(gateway.localBranchSha(workspace, "platform-generated")).isEqualTo(sha);
        // 本地有提交、远端没有对应提交 —— 服务层据此阻止重建工作区

        write(workspace, "b.txt", "2\n");
        assertThat(gateway.isDirty(workspace)).isTrue();
    }

    @Test
    void 克隆已有分支后可以切发布分支并回推() throws IOException {
        // 先在「远端」造一条有内容的主干
        Path seed = tempDir.resolve("seed");
        gateway.initEmpty(seed, remoteUrl, "main");
        write(seed, "scaffold-system/pom.xml", "<project/>\n");
        gateway.commitAll(seed, "chore: 首个提交");
        gateway.pushBranch(seed, "main");

        Path workspace = tempDir.resolve("workspace");
        gateway.cloneBranch(workspace, remoteUrl, "main");
        assertThat(Files.isRegularFile(workspace.resolve("scaffold-system/pom.xml"))).isTrue();

        gateway.checkoutBranch(workspace, "platform-generated", "origin/main");
        write(workspace, "README.md", "平台固定代码\n");
        String sha = gateway.commitAll(workspace, "chore: 同步固定代码");
        gateway.pushBranch(workspace, "platform-generated");

        assertThat(remoteShaOf(gateway.lsRemote(remoteUrl), "platform-generated")).isEqualTo(sha);
        // 主干始终不动：平台只写发布分支
        assertThat(remoteShaOf(gateway.lsRemote(remoteUrl), "main")).isNotEqualTo(sha);
    }

    @Test
    void 远端已前进时推送被拒而不是强推() {
        Path first = tempDir.resolve("first");
        gateway.initEmpty(first, remoteUrl, "gen");
        write(first, "a.txt", "1\n");
        gateway.commitAll(first, "chore: 第一次");
        gateway.pushBranch(first, "gen");

        // 另一个人（或仓库侧）把 gen 分支往前推了一步
        Path other = tempDir.resolve("other");
        gateway.cloneBranch(other, remoteUrl, "gen");
        write(other, "b.txt", "2\n");
        gateway.commitAll(other, "chore: 别人的提交");
        gateway.pushBranch(other, "gen");

        // 第一份工作区再提交一次然后推：非快进，必须失败
        write(first, "c.txt", "3\n");
        gateway.commitAll(first, "chore: 迟到的提交");
        assertThatThrownBy(() -> gateway.pushBranch(first, "gen"))
                .isInstanceOf(WorkspaceGitException.class)
                .extracting(e -> ((WorkspaceGitException) e).getSyncStatus())
                .isEqualTo(ProjectSyncStatus.DIVERGED);
    }

    @Test
    void 仓库不可达时报错带上可操作的说明() {
        String notThere = tempDir.resolve("nope.git").toUri().toString();
        assertThatThrownBy(() -> gateway.lsRemote(notThere))
                .isInstanceOf(WorkspaceGitException.class)
                .hasMessageContaining("读取远端仓库失败");

        assertThatThrownBy(() -> gateway.lsRemote("git@127.0.0.1:acme/none.git"))
                .isInstanceOf(WorkspaceGitException.class)
                .extracting(e -> ((WorkspaceGitException) e).getSyncStatus())
                .isEqualTo(ProjectSyncStatus.UNREACHABLE);
    }

    @Test
    void 分支名合法性交给git的规则() {
        assertThat(gateway.isValidBranch("platform-generated")).isTrue();
        assertThat(gateway.isValidBranch("feature/a-1")).isTrue();
        assertThat(gateway.isValidBranch("bad..name")).isFalse();
        assertThat(gateway.isValidBranch("a b")).isFalse();
        assertThat(gateway.isValidBranch("bad~1")).isFalse();
        assertThat(gateway.isValidBranch("trailing.")).isFalse();
        assertThat(gateway.isValidBranch("")).isFalse();
        assertThat(gateway.isValidBranch(null)).isFalse();
    }

    @Test
    void 底座标记tag能创建推送且同名时不移动() {
        Path workspace = tempDir.resolve("workspace");
        gateway.initEmpty(workspace, remoteUrl, "platform-generated");
        write(workspace, "scaffold-system/pom.xml", "<project/>\n");
        String first = gateway.commitAll(workspace, "chore: 第一次交付");
        gateway.pushBranch(workspace, "platform-generated");

        String tag = "yuanda/platform-base/" + "c".repeat(40);
        assertThat(gateway.tagCommit(workspace, tag, first, "底座快照")).isTrue();
        gateway.pushTag(workspace, tag);

        assertThat(gateway.lsRemote(remoteUrl)).anySatisfy(ref -> assertThat(ref.tagName()).isEqualTo(tag));

        // 同一底座版本再交付一次：同名 tag 已存在，必须原样留着 —— 移动它就改掉了 base
        write(workspace, "scaffold-vue/package.json", "{}\n");
        String second = gateway.commitAll(workspace, "chore: 第二次交付");
        assertThat(second).isNotEqualTo(first);
        assertThat(gateway.tagCommit(workspace, tag, second, "底座快照")).isFalse();
    }

    @Test
    void 附注tag在远端读数里要能拿回它指的那个提交() {
        // 版本 tag 是合并与回滚的锚点，按它取内容时平台要的是那个提交。而 ls-remote 对附注 tag 给两行：
        // 第一行是 tag 对象自己的号，带 ^{} 的那行才是提交 —— 只读第一行就会拿一个不是提交的号去找内容。
        Path workspace = tempDir.resolve("workspace");
        gateway.initEmpty(workspace, remoteUrl, "gen");
        write(workspace, "a.txt", "1\n");
        String sha = gateway.commitAll(workspace, "chore: 第一次发布");
        gateway.pushBranch(workspace, "gen");
        assertThat(gateway.tagCommit(workspace, "v-order_list-1", sha, "元搭低代码平台发布")).isTrue();
        gateway.pushTag(workspace, "v-order_list-1");

        List<RemoteRef> refs = gateway.lsRemote(remoteUrl);
        // 解引用行不该原样漏给调用方：多出来的那一条会被当成第二个引用数，「远端有几个 tag」就此算错
        assertThat(refs).allSatisfy(ref -> assertThat(ref.name()).doesNotEndWith("^{}"));
        assertThat(refs).anySatisfy(ref -> {
            assertThat(ref.tagName()).isEqualTo("v-order_list-1");
            assertThat(ref.commitSha()).isEqualTo(sha);
        });
        // 分支那条不受影响：它本来就直指提交
        assertThat(refs).anySatisfy(ref -> {
            assertThat(ref.branchName()).isEqualTo("gen");
            assertThat(ref.commitSha()).isEqualTo(sha);
        });
    }

    @Test
    void tag推送被拒时不当成仓库分歧() {
        Path workspace = tempDir.resolve("workspace");
        gateway.initEmpty(workspace, remoteUrl, "gen");
        write(workspace, "a.txt", "1\n");
        String sha = gateway.commitAll(workspace, "chore: 提交");
        String tag = "yuanda/platform-base/" + "d".repeat(40);
        gateway.tagCommit(workspace, tag, sha, "底座快照");

        // 别处先把同名 tag 推上去，占住这个名字
        Path other = tempDir.resolve("other");
        gateway.initEmpty(other, remoteUrl, "other");
        write(other, "b.txt", "2\n");
        String otherSha = gateway.commitAll(other, "chore: 别人的提交");
        gateway.tagCommit(other, tag, otherSha, "别人的底座快照");
        gateway.pushTag(other, tag);

        // 第二份推不上去，但这是 tag 的问题，不能把项目状态打成「与远端分叉」
        assertThatThrownBy(() -> gateway.pushTag(workspace, tag))
                .isInstanceOf(BusinessException.class)
                .isNotInstanceOf(WorkspaceGitException.class)
                .hasMessageContaining("远端拒绝推送");
    }

    @Test
    void 读底座提交号() throws IOException {
        Path repo = tempDir.resolve("base");
        gateway.initEmpty(repo, remoteUrl, "main");
        assertThat(gateway.headSha(repo)).isNull();
        write(repo, "x.txt", "1\n");
        String sha = gateway.commitAll(repo, "chore: 底座提交");
        assertThat(gateway.headSha(repo)).isEqualTo(sha);
        // 不是 git 仓库的目录返回 null，不阻断初始化
        assertThat(gateway.headSha(tempDir)).isNull();
    }

    @Test
    void 分叉点与变更清单只含源分支自己改的那些文件() {
        Path workspace = tempDir.resolve("workspace");
        gateway.initEmpty(workspace, remoteUrl, "main");
        write(workspace, "README.md", "a\nb\nc\n");
        write(workspace, "obsolete.txt", "x\n");
        String base = gateway.commitAll(workspace, "chore: 底座快照");
        gateway.pushBranch(workspace, "main");

        // 主干上人工补的文档：与发布分支无关，不该混进「这次要合进去什么」的清单
        write(workspace, "main-only.md", "manual\n");
        gateway.commitAll(workspace, "docs: 人工补的主干文档");
        gateway.pushBranch(workspace, "main");

        gateway.checkoutBranch(workspace, "gen", base);
        write(workspace, "README.md", "a\nB\nc\nd\n");
        write(workspace, "GenPage.java", "class GenPage {}\n");
        delete(workspace, "obsolete.txt");
        String gen = gateway.commitAll(workspace, "feat: 生成的页面代码");

        assertThat(gateway.mergeBase(workspace, "main", gen)).isEqualTo(base);

        List<ChangedFile> files = gateway.diffFiles(workspace, base, gen);
        assertThat(files).extracting(ChangedFile::path)
                .containsExactlyInAnyOrder("README.md", "GenPage.java", "obsolete.txt");

        assertThat(changeOf(files, "README.md")).isEqualTo(Change.MODIFY);
        assertThat(files).anySatisfy(file -> {
            assertThat(file.path()).isEqualTo("README.md");
            // b→B 一行改、末尾加一行 d：列表页上「+2 −1」就是这个数，diff 文本本身不入库
            assertThat(file.additions()).isEqualTo(2);
            assertThat(file.deletions()).isEqualTo(1);
        });
        assertThat(changeOf(files, "GenPage.java")).isEqualTo(Change.ADD);
        assertThat(changeOf(files, "obsolete.txt")).isEqualTo(Change.DELETE);
    }

    @Test
    void 试算合并认出冲突文件且一点不动工作区() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        gateway.initEmpty(workspace, remoteUrl, "main");
        write(workspace, "shared.txt", "line1\n");
        String base = gateway.commitAll(workspace, "chore: 共同祖先");
        gateway.pushBranch(workspace, "main");

        write(workspace, "shared.txt", "MAIN\n");
        String mainSha = gateway.commitAll(workspace, "docs: 主干改了这一行");
        gateway.pushBranch(workspace, "main");

        gateway.checkoutBranch(workspace, "gen", base);
        write(workspace, "shared.txt", "GEN\n");
        String genSha = gateway.commitAll(workspace, "feat: 发布分支也改了同一行");
        // 回到主干再试算：预检算的是「theirs 合进 ours」，工作区停在目标分支这一侧才与真实合并同形
        gateway.checkoutBranch(workspace, "main", null);

        MergeCheck check = gateway.checkMerge(workspace, mainSha, genSha);
        assertThat(check.clean()).isFalse();
        assertThat(check.conflictedPaths()).containsExactly("shared.txt");
        assertThat(check.summary()).contains("shared.txt");

        // 预检必须不留痕迹：工作区干净、分支没动、文件还是当前分支那一份、没写冲突标记文件
        assertThat(gateway.isDirty(workspace)).isFalse();
        assertThat(gateway.localBranchSha(workspace, "main")).isEqualTo(mainSha);
        assertThat(Files.readString(workspace.resolve("shared.txt"), StandardCharsets.UTF_8)).isEqualTo("MAIN\n");
        assertThat(workspace.resolve("shared.txt.orig")).doesNotExist();
    }

    @Test
    void 试算合并不相干的改动判为可自动合且不产生合并提交() {
        Path workspace = tempDir.resolve("workspace");
        gateway.initEmpty(workspace, remoteUrl, "main");
        write(workspace, "base.txt", "1\n");
        String base = gateway.commitAll(workspace, "chore: 共同祖先");
        gateway.pushBranch(workspace, "main");

        write(workspace, "main-only.txt", "main\n");
        String mainSha = gateway.commitAll(workspace, "docs: 主干自己的文件");
        gateway.pushBranch(workspace, "main");

        gateway.checkoutBranch(workspace, "gen", base);
        write(workspace, "gen-only.txt", "gen\n");
        String genSha = gateway.commitAll(workspace, "feat: 发布分支自己的文件");
        gateway.checkoutBranch(workspace, "main", null);

        MergeCheck check = gateway.checkMerge(workspace, mainSha, genSha);
        assertThat(check.clean()).isTrue();
        assertThat(check.conflictedPaths()).isEmpty();

        // 可自动合 ≠ 已经合：这一刀只做预检，主干提交号必须还停在原地
        assertThat(gateway.localBranchSha(workspace, "main")).isEqualTo(mainSha);
        assertThat(gateway.isDirty(workspace)).isFalse();
    }

    @Test
    void 单个文件的差异能按行取回来() {
        Path workspace = tempDir.resolve("workspace");
        gateway.initEmpty(workspace, remoteUrl, "main");
        write(workspace, "README.md", "a\nb\nc\n");
        String base = gateway.commitAll(workspace, "chore: 起点");

        write(workspace, "README.md", "a\nB\nc\nd\n");
        String head = gateway.commitAll(workspace, "docs: 改一行加一行");

        String diff = gateway.fileDiff(workspace, base, head, "README.md");
        // 行内单栏视图要的就是这份原始差异：带文件头、带 hunk 标记、带 +/-
        assertThat(diff).contains("--- ", "+++ ", "@@", "-b", "+B", "+d");
        // 只要这一个文件的差异：清单上别的文件不该混进来
        assertThat(gateway.fileDiff(workspace, base, head, "不存在的文件.txt")).isEmpty();
    }

    @Test
    void 合并会把两侧改动落到目标分支并推上去() {
        Path author = tempDir.resolve("author");
        gateway.initEmpty(author, remoteUrl, "main");
        write(author, "base.txt", "1\n");
        gateway.commitAll(author, "chore: 起点");
        gateway.pushBranch(author, "main");
        String base = gateway.localBranchSha(author, "main");

        gateway.checkoutBranch(author, "gen", base);
        write(author, "gen-only.txt", "gen\n");
        String genSha = gateway.commitAll(author, "feat: 发布分支的产物");
        gateway.pushBranch(author, "gen");

        gateway.checkoutBranch(author, "main", base);
        write(author, "main-only.txt", "main\n");
        String mainSha = gateway.commitAll(author, "docs: 主干上人的改动");
        gateway.pushBranch(author, "main");

        String merged = gateway.mergeIntoTarget(author, remoteUrl, "main", mainSha, genSha, "合并发布分支");

        assertThat(merged).isNotBlank();
        // 远端主干真的前进了，而且是一个有两个父亲的合并提交
        assertThat(gateway.lsRemote(remoteUrl)).anySatisfy(ref -> {
            assertThat(ref.branchName()).isEqualTo("main");
            assertThat(ref.commitSha()).isEqualTo(merged);
        });
        // 两侧内容都在：合并不是把发布分支整个盖上去
        Path reader = tempDir.resolve("reader");
        gateway.cloneBranch(reader, remoteUrl, "main");
        assertThat(reader.resolve("gen-only.txt")).exists();
        assertThat(reader.resolve("main-only.txt")).exists();
        assertThat(reader.resolve("base.txt")).exists();
    }

    @Test
    void 目标分支在预检之后又前进时拒绝合并而不是合到错的东西上() {
        Path author = tempDir.resolve("author");
        gateway.initEmpty(author, remoteUrl, "main");
        write(author, "base.txt", "1\n");
        gateway.commitAll(author, "chore: 起点");
        gateway.pushBranch(author, "main");
        String seen = gateway.localBranchSha(author, "main");

        gateway.checkoutBranch(author, "gen", seen);
        write(author, "gen.txt", "gen\n");
        String genSha = gateway.commitAll(author, "feat: 发布分支");
        gateway.pushBranch(author, "gen");

        // 预检之后，主干又被别人推了一步：人点的这条请求算的是旧的那一对提交
        gateway.checkoutBranch(author, "main", seen);
        write(author, "late.txt", "late\n");
        gateway.commitAll(author, "docs: 晚来的一笔");
        gateway.pushBranch(author, "main");

        assertThatThrownBy(() -> gateway.mergeIntoTarget(author, remoteUrl, "main", seen, genSha, "合并发布分支"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已前进");
    }

    @Test
    void 真冲突时拒绝合并且主干一个提交都没多() {
        Path author = tempDir.resolve("author");
        gateway.initEmpty(author, remoteUrl, "main");
        write(author, "shared.txt", "line1\n");
        String base = gateway.commitAll(author, "chore: 起点");
        gateway.pushBranch(author, "main");

        gateway.checkoutBranch(author, "gen", base);
        write(author, "shared.txt", "GEN\n");
        String genSha = gateway.commitAll(author, "feat: 发布分支改了这一行");
        gateway.pushBranch(author, "gen");

        gateway.checkoutBranch(author, "main", base);
        write(author, "shared.txt", "MAIN\n");
        String mainSha = gateway.commitAll(author, "docs: 主干也改了同一行");
        gateway.pushBranch(author, "main");

        assertThatThrownBy(() -> gateway.mergeIntoTarget(author, remoteUrl, "main", mainSha, genSha, "合并发布分支"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("shared.txt");
        // 拒绝得干干净净：远端主干还停在原来那个提交
        assertThat(remoteShaOf(gateway.lsRemote(remoteUrl), "main")).isEqualTo(mainSha);
    }

    private static Change changeOf(List<ChangedFile> files, String path) {
        return files.stream().filter(file -> file.path().equals(path)).findFirst()
                .orElseThrow(() -> new AssertionError("清单里没有 " + path)).change();
    }

    private static void delete(Path workspace, String relative) {
        try {
            Files.delete(workspace.resolve(relative));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void fetch带回远端读数而不动本地分支与工作区() {
        Path author = tempDir.resolve("author");
        gateway.initEmpty(author, remoteUrl, "main");
        write(author, "a.txt", "1\n");
        String first = gateway.commitAll(author, "chore: 第一个提交");
        gateway.pushBranch(author, "main");

        Path reader = tempDir.resolve("reader");
        gateway.cloneBranch(reader, remoteUrl, "main");

        // 主干又前进了一步：读侧的工作区此刻还不知道，这正是审查前要先 fetch 的理由
        write(author, "b.txt", "2\n");
        String second = gateway.commitAll(author, "chore: 第二个提交");
        assertThat(second).isNotEqualTo(first);
        gateway.pushBranch(author, "main");

        assertThat(gateway.mergeBase(reader, "refs/remotes/origin/main", second)).isNull();

        gateway.fetch(reader);

        // fetch 之后第二个提交才进了本地对象库，分叉点才可能算得出来
        assertThat(gateway.mergeBase(reader, "refs/remotes/origin/main", second)).isEqualTo(second);
        // 但 fetch 只是带回远端读数：本地分支与该分支上的工作区内容都得原地不动，
        // 否则一次「看看有没有要合的」就把人（或发布链路）的现场改了
        assertThat(gateway.localBranchSha(reader, "main")).isEqualTo(first);
        assertThat(gateway.isDirty(reader)).isFalse();
    }

    private static String remoteShaOf(List<RemoteRef> refs, String branch) {
        return refs.stream().filter(ref -> branch.equals(ref.branchName())).map(RemoteRef::sha).findFirst().orElse(null);
    }

    private static void write(Path workspace, String relative, String content) {
        try {
            Path target = workspace.resolve(relative);
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
