package com.scaffold.lowcode.workspace;

import com.scaffold.common.exception.BusinessException;
import com.scaffold.lowcode.generate.GeneratedPaths;
import com.scaffold.lowcode.project.domain.ProjectSyncStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @Test
    void 能按提交号取回指定文件的历史内容而不改工作区() throws IOException {
        Path workspace = tempDir.resolve("workspace");
        gateway.initEmpty(workspace, remoteUrl, "platform-generated");
        write(workspace, "scaffold-business/src/main/java/x/OrderRow.java", "class OrderRow { }\n");
        write(workspace, "scaffold-vue/src/views/order/Orders.vue", "<template>a</template>\n");
        String v1 = gateway.commitAll(workspace, "feat: 发布 v1");
        gateway.pushBranch(workspace, "platform-generated");

        // 同一个文件在下一版里变了：回滚取的就是「那一版里它长什么样」，不是工作区当前内容
        write(workspace, "scaffold-business/src/main/java/x/OrderRow.java", "class OrderRow { int id; }\n");
        String v2 = gateway.commitAll(workspace, "feat: 发布 v2");
        gateway.pushBranch(workspace, "platform-generated");
        write(workspace, "uncommitted.txt", "工作区里有人写了没提交的东西\n");

        Map<String, String> contents = gateway.fileContents(workspace, v1, List.of(
                "scaffold-business/src/main/java/x/OrderRow.java",
                "scaffold-vue/src/views/order/Orders.vue"));

        assertThat(contents).containsOnlyKeys(
                "scaffold-business/src/main/java/x/OrderRow.java", "scaffold-vue/src/views/order/Orders.vue");
        assertThat(contents.get("scaffold-business/src/main/java/x/OrderRow.java"))
                .isEqualTo("class OrderRow { }\n");
        assertThat(contents.get("scaffold-vue/src/views/order/Orders.vue")).isEqualTo("<template>a</template>\n");

        // 取历史是只读动作：HEAD 还在那条分支的最新提交上，工作区也没被这次读取「弄脏」
        assertThat(gateway.headSha(workspace)).isEqualTo(v2);
        assertThat(gateway.isDirty(workspace)).isTrue();   // 脏是上面那个人写的文件脏的，不是读取造成的
        assertThat(Files.readString(workspace.resolve("scaffold-business/src/main/java/x/OrderRow.java")))
                .isEqualTo("class OrderRow { int id; }\n");
    }

    @Test
    void 那个提交里没有的文件如实报缺而不是返回空内容() {
        Path workspace = tempDir.resolve("workspace");
        gateway.initEmpty(workspace, remoteUrl, "platform-generated");
        write(workspace, "kept.txt", "有\n");
        String sha = gateway.commitAll(workspace, "feat: 第一版");

        // 回滚按「请求的路径 − 取回来的键」算缺了哪些，所以缺的那条宁可不出现在结果里，
        // 也不能给一个空字符串 —— 空内容是合法内容，写回去就是把一个文件清空
        assertThat(gateway.fileContents(workspace, sha, List.of("kept.txt", "gone.txt")))
                .containsOnlyKeys("kept.txt");
    }

    @Test
    void 二进制内容不当作文本取回() throws IOException {
        Path workspace = tempDir.resolve("workspace");
        gateway.initEmpty(workspace, remoteUrl, "platform-generated");
        Path png = workspace.resolve("assets.png");
        Files.createDirectories(png.getParent());
        Files.write(png, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, (byte) 0xFF, (byte) 0xFE, 'x'});
        String sha = gateway.commitAll(workspace, "feat: 带了一个二进制");

        // B 档产物全是文本；历史里出现二进制只有两种可能 —— 有人在发布分支上塞了东西，
        // 或版本快照的路径记错了。两种都要停手，而不是把乱码写回工作区
        assertThatThrownBy(() -> gateway.fileContents(workspace, sha, List.of("assets.png")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("assets.png")
                .hasMessageContaining("二进制");
    }

    @Test
    void 提交号查不到时说清是哪一个查不到() {
        Path workspace = tempDir.resolve("workspace");
        gateway.initEmpty(workspace, remoteUrl, "platform-generated");
        write(workspace, "a.txt", "1\n");
        String sha = gateway.commitAll(workspace, "feat: 起点");

        assertThatThrownBy(() -> gateway.fileContents(workspace, "0123456789abcdef0123456789abcdef01234567",
                List.of("a.txt")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("0123456789");
        // 反过来确认正常号是能走的：排除「永远抛错」这种假绿
        assertThat(gateway.fileContents(workspace, sha, List.of("a.txt"))).containsOnlyKeys("a.txt");
    }

    /**
     * 底座升级的逐文件三方合并（PR 10，方案 §5.2「把平台改的那几行与项目改的那几行分开」）。
     *
     * <p>base = 项目仓库里交付时打的 yuanda/platform-base/* tag，ours = 项目主干，
     * theirs = 按当前底座 checkout 现生成的 A 档快照。口径是用户定的那一条：
     * <b>能自动合的写，冲突的文件一个都不写、只列清单</b> —— 所以 CONFLICT 不给内容，
     * 平台也不在网页里改代码。
     */
    /**
     * 底座升级的 theirs 侧（10B）：把一份 A 档快照在仓库的对象库里做成一个提交。
     *
     * <p>它必须是一个「有内容、不在任何历史上」的提交：三方合并按提交号取树，而升级分支不该
     * 从这份快照长出来 —— 那样项目主干会凭空多出一段没人认领的历史。
     */
    @Test
    void 快照提交能按提交号读回全部文件且不挂在任何分支上() throws IOException {
        Path workspace = tempDir.resolve("workspace");
        gateway.initEmpty(workspace, remoteUrl, "main");
        write(workspace, "README.md", "# 项目自己的起点\n");
        String main = gateway.commitAll(workspace, "chore: 起点");
        gateway.pushBranch(workspace, "main");

        String snapshot = gateway.snapshotCommit(workspace, new LinkedHashMap<>(Map.of(
                "pom.xml", "<project/>\n".getBytes(StandardCharsets.UTF_8),
                "scaffold-vue/package.json", "{\"dependencies\":{\"@yuanda-lowcode-ai/scaffold-ui\":\"1.2.0\"}}\n"
                        .getBytes(StandardCharsets.UTF_8))), "平台底座快照");

        Map<String, String> back = gateway.fileContents(workspace, snapshot, List.of("pom.xml", "scaffold-vue/package.json"));
        assertThat(back).containsOnlyKeys("pom.xml", "scaffold-vue/package.json");
        assertThat(back.get("pom.xml")).isEqualTo("<project/>\n");
        // 物料包版本号就在快照内容里：升级时它跟着 theirs 下去，不需要第二个机制
        assertThat(back.get("scaffold-vue/package.json")).contains("1.2.0");

        // 没改动任何现场：分支还是原来那一笔，远端也看不见这个提交
        assertThat(gateway.localBranchSha(workspace, "main")).isEqualTo(main);
        assertThat(gateway.lsRemote(remoteUrl)).allSatisfy(ref -> assertThat(ref.sha()).isNotEqualTo(snapshot));
        // 也没有父亲 —— 它是凭空长出来的一份内容，不是历史的一段
        try (Git git = Git.open(workspace.toFile()); RevWalk walk = new RevWalk(git.getRepository())) {
            assertThat(walk.parseCommit(git.getRepository().resolve(snapshot)).getParentCount()).isZero();
        }
    }

    @Test
    void 空快照不许做成提交() throws IOException {
        Path workspace = tempDir.resolve("workspace");
        gateway.initEmpty(workspace, remoteUrl, "main");
        write(workspace, "README.md", "# demo\n");
        gateway.commitAll(workspace, "chore: 起点");

        // 空清单一旦做成提交，三方合并会把项目里每一个文件都报成「平台不再交付」——
        // 那不是升级，那是一份删除清单。底座 checkout 读不到时宁可当场停手
        assertThatThrownBy(() -> gateway.snapshotCommit(workspace, Map.of(), "空快照"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有一份文件");
    }

    @Test
    void 快照里的越界路径被拒绝而不是写到仓库外面() throws IOException {
        Path workspace = tempDir.resolve("workspace");
        gateway.initEmpty(workspace, remoteUrl, "main");
        write(workspace, "README.md", "# demo\n");
        gateway.commitAll(workspace, "chore: 起点");

        assertThatThrownBy(() -> gateway.snapshotCommit(workspace,
                Map.of("../escape.txt", "越界\n".getBytes(StandardCharsets.UTF_8)), "越界快照"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("../escape.txt");
    }

    /** 与 10A 串起来：theirs 由快照提交提供时，三方合并照样能分出「平台改的行」与「项目改的行」 */
    @Test
    void 快照提交可以直接当三方合并的theirs侧用() throws IOException {
        Fixture fixture = baseFixture();
        String ours = fixture.commitOn("project", "pom.xml", POM_OURS);
        String theirs = gateway.snapshotCommit(fixture.workspace, new LinkedHashMap<>(Map.of(
                "pom.xml", POM_THEIRS.getBytes(StandardCharsets.UTF_8),
                "README.md", "# scaffold\n".getBytes(StandardCharsets.UTF_8),
                "notes.md", "原始说明\n".getBytes(StandardCharsets.UTF_8),
                "Dockerfile", "FROM temurin:17\nCOPY a b\n".getBytes(StandardCharsets.UTF_8))), "底座快照");

        List<MergedFile> merged = gateway.threeWayMerge(fixture.workspace, fixture.base, ours, theirs,
                List.of("pom.xml", "README.md", "notes.md", "Dockerfile"));

        assertThat(merged).filteredOn(file -> file.path().equals("pom.xml")).singleElement()
                .satisfies(file -> {
                    assertThat(file.outcome()).isEqualTo(MergeOutcome.CLEAN);
                    assertThat(file.content()).contains("<version>2.0</version>").contains("订单中台");
                });
        // 快照里没动过的三行都该是 UNCHANGED：升级提交里不该出现「平台什么都没改」的文件
        assertThat(merged).filteredOn(file -> !file.path().equals("pom.xml")).hasSize(3)
                .allSatisfy(file -> assertThat(file.outcome()).isEqualTo(MergeOutcome.UNCHANGED));
    }

    /**
     * 底座升级的完整一趟（10C 的 git 编排）：临时 clone 里算三方合并，把可自动合的文件写进
     * 从主干切出的升级分支并推上去 —— 平台工作区一条引用都不动，与 mergeIntoTarget 同一口径。
     */
    @Test
    void 升级把可自动合的文件写进从主干切出的升级分支并推上去() throws Exception {
        UpgradeFixture fx = upgradeFixture();
        // 底座侧改 pom 的版本号那一行、README 原样不动；项目侧改的是 pom 末尾那行与 README
        BaseUpgrade upgrade = gateway.baseUpgrade(fx.workspace, fx.repoUrl, "main", fx.baseTag,
                Map.of("pom.xml", POM_THEIRS.getBytes(StandardCharsets.UTF_8),
                        "README.md", "# scaffold\n".getBytes(StandardCharsets.UTF_8)),
                "platform-upgrade/aaaaaaa", "底座升级");

        assertThat(upgrade.oursSha()).isEqualTo(fx.main);
        assertThat(upgrade.commitSha()).isNotNull();
        assertThat(upgrade.branch()).isEqualTo("platform-upgrade/aaaaaaa");
        assertThat(upgrade.count(MergeOutcome.CLEAN)).isEqualTo(1);
        assertThat(upgrade.count(MergeOutcome.UNCHANGED)).isEqualTo(1);

        String pushed = branchSha(fx.repoUrl, upgrade.branch());
        assertThat(pushed).isEqualTo(upgrade.commitSha());
        // 主干没被碰：升级只是「供人审的一份内容」，合不合由人
        assertThat(branchSha(fx.repoUrl, "main")).isEqualTo(fx.main);
        // 平台改的那一行与项目加的那一行都在升级分支里，README 保持项目那份。
        // 从远端重新 clone 再读：那份升级提交只活在 baseUpgrade 的临时 clone 里，
        // 用完就删了 —— 拿平台工作区去读只会得到 Missing unknown
        Path check = tempDir.resolve("upgrade-check");
        gateway.cloneBranch(check, fx.repoUrl, upgrade.branch());
        Map<String, String> onBranch = gateway.fileContents(check, upgrade.commitSha(),
                List.of("pom.xml", "README.md"));
        assertThat(onBranch.get("pom.xml")).contains("<version>2.0</version>").contains("订单中台");
        assertThat(onBranch.get("README.md")).isEqualTo("# 订单中台\n");
    }

    @Test
    void 全无可写的内容时不建分支也不推() throws Exception {
        UpgradeFixture fx = upgradeFixture();
        // 快照与交付时一模一样：每一格都是 UNCHANGED，那就没有「升级」这回事
        BaseUpgrade upgrade = gateway.baseUpgrade(fx.workspace, fx.repoUrl, "main", fx.baseTag,
                Map.of("pom.xml", POM_BASE.getBytes(StandardCharsets.UTF_8),
                        "README.md", "# scaffold\n".getBytes(StandardCharsets.UTF_8)),
                "platform-upgrade/unchanged", "底座升级");

        assertThat(upgrade.commitSha()).isNull();
        assertThat(upgrade.count(MergeOutcome.CLEAN)).isZero();
        assertThat(gateway.lsRemote(fx.repoUrl)).allSatisfy(ref ->
                assertThat(ref.branchName()).isNotEqualTo("platform-upgrade/unchanged"));
    }

    @Test
    void 只有冲突时不建分支并把那一格算进冲突数() throws Exception {
        UpgradeFixture fx = upgradeFixture();
        // 底座与项目改的是同一行：这一格必须留给人，升级分支里不该出现任何内容
        // 只给 README：底座与项目改的是同一行 → 冲突；其余三格因为快照里没带 → 平台撤掉。
        // 两种都不该写，所以一个分支都不建。（上一版这里还带着 pom 的可自动合改动，
        // 于是照样建了分支 —— 用例名说的「只有冲突」得真的只有冲突才行）
        BaseUpgrade upgrade = gateway.baseUpgrade(fx.workspace, fx.repoUrl, "main", fx.baseTag,
                Map.of("README.md", "# 平台把说明也改了\n".getBytes(StandardCharsets.UTF_8)),
                "platform-upgrade/conflict", "底座升级");

        assertThat(upgrade.count(MergeOutcome.CONFLICT)).isEqualTo(1);
        assertThat(upgrade.count(MergeOutcome.DROPPED_BY_PLATFORM)).isEqualTo(3);
        assertThat(upgrade.count(MergeOutcome.CLEAN)).isZero();
        assertThat(upgrade.commitSha()).isNull();
    }

    @Test
    void 升级分支名已经在远端时拒绝而不是强推() throws Exception {
        UpgradeFixture fx = upgradeFixture();
        // 上一次升级推出去的分支还挂在远端（可能已经有人在审）：同名再推只能靠 force，而平台永不强推
        gateway.checkoutBranch(fx.workspace, "platform-upgrade/taken", fx.main);
        write(fx.workspace, "占位.txt", "上一次升级留下的\n");
        gateway.commitAll(fx.workspace, "chore: 上一次的升级分支");
        gateway.pushBranch(fx.workspace, "platform-upgrade/taken");

        assertThatThrownBy(() -> gateway.baseUpgrade(fx.workspace, fx.repoUrl, "main", fx.baseTag,
                Map.of("pom.xml", POM_THEIRS.getBytes(StandardCharsets.UTF_8)),
                "platform-upgrade/taken", "底座升级"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("platform-upgrade/taken");
    }

    /**
     * 缺 base 侧那道拒绝必须说清「先去补打 tag」，不能只丢一句网络错。
     *
     * <p>真机踩过：补 fetch 远端 tag 的那条命令漏挂了 SSH 传输配置，本机 HTTP 代理把 github.com:22
     * 劫持了，接口回的是「补取远端 tag 失败：Session is being closed」—— 现场什么都没改、拒绝本身是对的，
     * 人看到的却是一句看不懂也接不下去的错。本例走本机裸仓库，代理那条坑它挡不住（那要靠代码里统一挂
     * transportConfig），它钉的是 §10.3 那条验收：缺底座标记 tag 的项目要被挡住，并且说得出下一步。
     */
    @Test
    void 远端没有底座标记tag时升级拒绝并指出生路() throws IOException {
        UpgradeFixture fx = upgradeFixture();
        String missing = PlatformBaseTag.PREFIX + "0123456789abcdef0123456789abcdef01234567";

        assertThatThrownBy(() -> gateway.baseUpgrade(fx.workspace, fx.repoUrl, "main", missing,
                Map.of("pom.xml", POM_THEIRS.getBytes(StandardCharsets.UTF_8)),
                "platform-upgrade/missingtag", "底座升级"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("找不到底座标记 tag").hasMessageContaining(missing)
                .hasMessageContaining("补打底座标记 tag");

        // 拒绝在动手之前：既没建升级分支，也不该留下半条升级链
        assertThat(gateway.lsRemote(fx.repoUrl))
                .filteredOn(ref -> ref.branchName() != null && ref.branchName().startsWith("platform-upgrade/"))
                .isEmpty();
    }

    /**
     * 「平台撤掉」这份名单只该说 A 档（10G 真机抓的）。
     *
     * <p>真机上那一次把 {@code SalesOrderService.java}、{@code SalesOrder.vue}、建表 SQL 连同
     * {@code .scaffold-project.json} 一起报成了「平台不再交付，删不删由人定」—— 因为路径并集是按
     * 「三棵树里出现过」算的，而 base 与 ours 是**整个项目提交**，里面本来就有 B/C 档。
     * 这份名单就这么给审的人看，就是在诱导他删掉自己的业务代码：B 档下次发布自己会覆盖，
     * C 档是人工写的资产，平台连提意见的资格都没有。
     */
    @Test
    void 升级结论里不许出现B档与C档路径() throws IOException {
        String service = "scaffold-system/scaffold-business/src/main/java/com/scaffold/business/sales_order/service/SalesOrderService.java";
        String view = "scaffold-vue/src/views/sales_order/SalesOrder.vue";
        // 先自证夹具：这两条确实是页面发布可写的形状，否则下面的断言就是在比一个不存在的前提
        assertThat(GeneratedPaths.isPagePublishOwned(service)).isTrue();
        assertThat(GeneratedPaths.isPagePublishOwned(view)).isTrue();

        Path workspace = tempDir.resolve("upgrade-tier");
        gateway.initEmpty(workspace, remoteUrl, "main");
        write(workspace, "pom.xml", POM_BASE);
        write(workspace, service, "class SalesOrderService { }\n");
        write(workspace, view, "<template>项目自己的页面</template>\n");
        write(workspace, ScaffoldTrimPolicy.MARKER_FILE, "{\"baseScaffoldSha\":\"x\"}\n");
        String delivered = gateway.commitAll(workspace, "chore: 交付固定代码");
        String baseTag = PlatformBaseTag.PREFIX + delivered;
        gateway.tagCommit(workspace, baseTag, delivered, "底座快照");
        gateway.pushTag(workspace, baseTag);
        gateway.pushBranch(workspace, "main");

        // 这一轮底座只改了 pom 的版本号那一行，快照里没有别的文件
        BaseUpgrade upgrade = gateway.baseUpgrade(workspace, remoteUrl, "main", baseTag,
                Map.of("pom.xml", POM_THEIRS.getBytes(StandardCharsets.UTF_8)),
                "platform-upgrade/tier", "底座升级");

        assertThat(upgrade.files()).extracting(MergedFile::path)
                .noneMatch(path -> path.equals(service) || path.equals(view)
                        || path.equals(ScaffoldTrimPolicy.MARKER_FILE));
        assertThat(upgrade.count(MergeOutcome.DROPPED_BY_PLATFORM)).isZero();
        assertThat(upgrade.count(MergeOutcome.CLEAN)).isEqualTo(1);
        assertThat(upgrade.commitSha()).isNotNull();
    }

    /**
     * 落后读数（§10.4 那条不变式的正面）：底座 checkout 能数出从交付那一版到现在走了几步。
     *
     * <p>数不出来时必须返回 null 而不是 0 —— 「0」在界面上是「已是最新」，
     * 那是把「我不知道」报成「没问题」，比不报更坏。
     */
    @Test
    void 底座checkout能数出从交付版本到当前走了几个提交() throws IOException {
        Path workspace = tempDir.resolve("workspace");
        gateway.initEmpty(workspace, remoteUrl, "main");
        write(workspace, "a.txt", "1\n");
        String delivered = gateway.commitAll(workspace, "chore: 交付时那一版");
        write(workspace, "b.txt", "2\n");
        gateway.commitAll(workspace, "feat: 底座往前走了一步");
        write(workspace, "c.txt", "3\n");
        String head = gateway.commitAll(workspace, "feat: 又走了一步");

        assertThat(gateway.commitCountBetween(workspace, delivered, head)).isEqualTo(2);
        assertThat(gateway.commitCountBetween(workspace, head, head)).isZero();
        // 同一个号自己比自己是 0，这条给「已是最新」用
        assertThat(gateway.commitCountBetween(workspace, delivered, delivered)).isZero();
    }

    @Test
    void 数不出来时返回null而不是0() throws IOException {
        Path workspace = tempDir.resolve("workspace");
        gateway.initEmpty(workspace, remoteUrl, "main");
        write(workspace, "a.txt", "1\n");
        String sha = gateway.commitAll(workspace, "chore: 起点");

        // 底座 checkout 不是 git 仓库、或那一笔不在历史里（比如项目是在记不到 sha 的版本上初始化的），
        // 这两种都要退化成「只给文件级差异清单」，而不是报一个看起来可靠的 0
        assertThat(gateway.commitCountBetween(workspace, "0123456789abcdef0123456789abcdef01234567", sha)).isNull();
        assertThat(gateway.commitCountBetween(workspace, sha, "0123456789abcdef0123456789abcdef01234567")).isNull();
        assertThat(gateway.commitCountBetween(tempDir.resolve("不是仓库"), sha, sha)).isNull();
    }

    /** 升级合进主干后的那一步：base 侧要跟着走，否则下一次升级把已经下发过的内容再下发一遍 */
    @Test
    void 底座标记tag打在目标分支当前提交上且同名不移动() throws IOException {
        Path workspace = tempDir.resolve("workspace");
        gateway.initEmpty(workspace, remoteUrl, "main");
        write(workspace, "a.txt", "1\n");
        String first = gateway.commitAll(workspace, "chore: 主干第一笔");
        gateway.pushBranch(workspace, "main");

        String tag = PlatformBaseTag.name(first);
        assertThat(gateway.stampBaseTag(workspace, remoteUrl, "main", tag, null, "底座快照")).isTrue();
        assertThat(gateway.lsRemote(remoteUrl)).anySatisfy(ref -> {
            assertThat(ref.tagName()).isEqualTo(tag);
            assertThat(ref.commitSha()).isEqualTo(first);
        });

        // 主干又走了一步：同名 tag 原样留着（移动它等于改掉某一轮升级的 base 侧），新名字才指向新的一笔
        gateway.checkoutBranch(workspace, "main", first);
        write(workspace, "b.txt", "2\n");
        String second = gateway.commitAll(workspace, "chore: 主干第二笔");
        gateway.pushBranch(workspace, "main");
        assertThat(gateway.stampBaseTag(workspace, remoteUrl, "main", tag, null, "底座快照")).isFalse();
        String newTag = PlatformBaseTag.name(second);
        assertThat(gateway.stampBaseTag(workspace, remoteUrl, "main", newTag, null, "底座快照")).isTrue();

        List<RemoteRef> refs = gateway.lsRemote(remoteUrl);
        assertThat(refs).anySatisfy(ref -> {
            assertThat(ref.tagName()).isEqualTo(tag);
            assertThat(ref.commitSha()).isEqualTo(first);
        });
        assertThat(refs).anySatisfy(ref -> {
            assertThat(ref.tagName()).isEqualTo(newTag);
            assertThat(ref.commitSha()).isEqualTo(second);
        });
    }

    /**
     * 「人工补打底座标记 tag」要先能在历史里认出当初交付那一笔，所以网关得能按分支给出提交号序列。
     *
     * <p>顺序与上限是这件判据的两半：必须<b>从新到旧</b>（要的是「第一笔带着这个底座号的提交」，
     * 反过来遍历就得走完全程），而 {@code limit} 是护栏 —— 一个跑了几年主干的仓库有几万笔，
     * 逐笔读文件不能没有上界，撞到上界要说「请指定提交号」而不是悄悄给一个错的答案。
     */
    @Test
    void 分支历史按从新到旧给出提交号且到上限就停() throws IOException {
        Path workspace = tempDir.resolve("workspace");
        gateway.initEmpty(workspace, remoteUrl, "main");
        write(workspace, "a.txt", "1\n");
        String first = gateway.commitAll(workspace, "chore: 第一笔");
        write(workspace, "b.txt", "2\n");
        String second = gateway.commitAll(workspace, "chore: 第二笔");
        write(workspace, "c.txt", "3\n");
        String third = gateway.commitAll(workspace, "chore: 第三笔");
        gateway.pushBranch(workspace, "main");

        assertThat(gateway.revList(workspace, "main", 0)).containsExactly(third, second, first);
        assertThat(gateway.revList(workspace, "main", 2)).containsExactly(third, second);
        // 分支不存在给空列表而不是抛：调用方要说的是「远端没有这条分支」，不是 JGit 的错
        assertThat(gateway.revList(workspace, "没有这条分支", 10)).isEmpty();
    }

    private String branchSha(String repoUrl, String branch) {
        return gateway.lsRemote(repoUrl).stream().filter(ref -> branch.equals(ref.branchName()))
                .findFirst().orElseThrow(() -> new AssertionError("远端没有分支 " + branch)).sha();
    }

    /** 底座升级的现场：主干上有交付提交（打了 base tag），之后项目又提交过一次 */
    private final class UpgradeFixture {
        private final Path workspace;
        private final String repoUrl;
        private final String main;
        private final String baseTag;

        private UpgradeFixture() throws IOException {
            // 远端就用每个测试自己的那个裸仓库（@BeforeEach 已经建好），不再另起一份 ——
            // 自己拼 bare 仓库的写法要把 GitAPIException 也带进夹具，白多一层
            workspace = tempDir.resolve("upgrade-flow-" + System.nanoTime());
            repoUrl = remoteUrl;
            gateway.initEmpty(workspace, repoUrl, "main");
            write(workspace, "pom.xml", POM_BASE);
            write(workspace, "README.md", "# scaffold\n");
            write(workspace, "notes.md", "原始说明\n");
            write(workspace, "Dockerfile", "FROM temurin:17\nCOPY a b\n");
            String delivered = gateway.commitAll(workspace, "chore: 交付固定代码");
            baseTag = PlatformBaseTag.PREFIX + delivered;
            gateway.tagCommit(workspace, baseTag, delivered, "底座快照");
            gateway.pushTag(workspace, baseTag);
            gateway.pushBranch(workspace, "main");
            // 项目自己又改了一次：pom 末尾加一行，README 也改了
            write(workspace, "pom.xml", POM_OURS);
            write(workspace, "README.md", "# 订单中台\n");
            main = gateway.commitAll(workspace, "chore: 项目自己的改动");
            gateway.pushBranch(workspace, "main");
        }
    }

    private UpgradeFixture upgradeFixture() throws IOException {
        return new UpgradeFixture();
    }

    /** 取前 7 位当分支名后缀，与底座短 sha 的用法一致 */
    private static String snapshotOf(String content) {
        return content.hashCode() == 0 ? "0" : String.format("%08x", content.hashCode());
    }

    /** 五行的 pom：夹具喂的是真文件的形状，两行那种「整份都是改动」的样本测不出合并算法的行为 */
    private static final String POM_BASE = "<project>\n  <version>1.0</version>\n  <name>scaffold</name>\n  <packaging>pom</packaging>\n</project>\n";
    private static final String POM_THEIRS = "<project>\n  <version>2.0</version>\n  <name>scaffold</name>\n  <packaging>pom</packaging>\n</project>\n";
    private static final String POM_OURS = "<project>\n  <version>1.0</version>\n  <name>scaffold</name>\n  <packaging>pom</packaging>\n  <name>订单中台</name>\n</project>\n";

    @Test
    void 两侧各改各的行时自动合并且内容同时含两边的改动() {
        Fixture fixture = baseFixture();
        // 平台侧改第 2 行，项目侧在第 5 行后追加：中间隔着未改动的行，机器能判
        String theirs = fixture.commitOn("platform", "pom.xml", POM_THEIRS);
        String ours = fixture.commitOn("project", "pom.xml", POM_OURS);

        // 先自证三侧现场真是我以为的那三份：合并结论不对时，要能分清是算法判错还是夹具喂错
        assertThat(gateway.fileContents(fixture.workspace, fixture.base, List.of("pom.xml")).get("pom.xml"))
                .isEqualTo(POM_BASE);
        assertThat(gateway.fileContents(fixture.workspace, theirs, List.of("pom.xml")).get("pom.xml"))
                .isEqualTo(POM_THEIRS);
        assertThat(gateway.fileContents(fixture.workspace, ours, List.of("pom.xml")).get("pom.xml"))
                .isEqualTo(POM_OURS);

        List<MergedFile> merged = gateway.threeWayMerge(fixture.workspace, fixture.base, ours, theirs,
                List.of("pom.xml"));

        assertThat(merged).singleElement().satisfies(file -> {
            assertThat(file.outcome()).isEqualTo(MergeOutcome.CLEAN);
            assertThat(file.content()).isEqualTo("<project>\n  <version>2.0</version>\n  <name>scaffold</name>\n"
                    + "  <packaging>pom</packaging>\n  <name>订单中台</name>\n</project>\n");
        });
    }

    /**
     * 相邻行的两侧改动，JGit 判冲突 —— 命令行 git 会干净合掉，这是引擎差异，不是这里算错。
     *
     * <p>记下来是因为它会出现在真机上：底座把 pom 的第 2 行改了，项目恰好在第 3 行加过东西，
     * 升级就会报「要人解」。宁可多报一格让人看一眼，也不反过来发明一套更宽松的合并 ——
     * 平台不替人取舍，也不在自己算不准的时候假装算得准。
     */
    @Test
    void 相邻行的两侧改动按冲突处理并说明这是引擎的保守侧() {
        Fixture fixture = baseFixture();
        String theirs = fixture.commitOn("platform", "pom.xml", POM_THEIRS);
        String ours = fixture.commitOn("project", "pom.xml",
                "<project>\n  <version>1.0</version>\n  <name>订单中台</name>\n  <packaging>pom</packaging>\n</project>\n");

        List<MergedFile> merged = gateway.threeWayMerge(fixture.workspace, fixture.base, ours, theirs,
                List.of("pom.xml"));

        assertThat(merged).singleElement().satisfies(file -> {
            assertThat(file.outcome()).isEqualTo(MergeOutcome.CONFLICT);
            assertThat(file.content()).isNull();
        });
    }

    @Test
    void 两侧改同一行时报冲突且不给内容() {
        Fixture fixture = baseFixture();
        String theirs = fixture.commitOn("platform", "pom.xml", "<version>2.0</version>\n<name>scaffold</name>\n");
        String ours = fixture.commitOn("project", "pom.xml", "<version>1.9</version>\n<name>scaffold</name>\n");

        List<MergedFile> merged = gateway.threeWayMerge(fixture.workspace, fixture.base, ours, theirs,
                List.of("pom.xml"));

        assertThat(merged).singleElement().satisfies(file -> {
            assertThat(file.outcome()).isEqualTo(MergeOutcome.CONFLICT);
            // 不写就不给内容：带着 <<<<<<< 的文本进升级分支，等于平台把没编译过的东西推给人
            assertThat(file.content()).isNull();
        });
    }

    @Test
    void 只有平台改过的文件才要写而项目自己改过的不动() {
        Fixture fixture = baseFixture();
        String theirs = fixture.commitOn("platform", "Dockerfile", "FROM temurin:21\nCOPY a b\n");
        // 项目侧只改 README，Dockerfile 与 base 一致：那 Dockerfile 就是「平台单改」
        String ours = fixture.commitOn("project", "README.md", "# 订单中台\n");

        List<MergedFile> merged = gateway.threeWayMerge(fixture.workspace, fixture.base, ours, theirs,
                List.of("Dockerfile", "README.md"));

        assertThat(merged).filteredOn(file -> file.path().equals("Dockerfile")).singleElement()
                .satisfies(file -> {
                    assertThat(file.outcome()).isEqualTo(MergeOutcome.CLEAN);
                    assertThat(file.content()).isEqualTo("FROM temurin:21\nCOPY a b\n");
                });
        // 项目改了而平台没改 → 平台一个字都不碰它，这才是「不静默覆盖项目私有配置」
        assertThat(merged).filteredOn(file -> file.path().equals("README.md")).singleElement()
                .satisfies(file -> assertThat(file.outcome()).isEqualTo(MergeOutcome.UNCHANGED));
    }

    @Test
    void 平台新增的文件直接写入而平台不再交付的文件不许自动删() {
        Fixture fixture = baseFixture();
        // 平台这一版新增一个文件、同时不再交付 README.md —— 只加不删的话 theirs 里 README.md 还在，
        // 那一格就没被构造出来（第一版就是这么写错的，断言照样能过在别的结果上）
        String theirs = fixture.commitAddDropOn("platform", "deploy/new.conf", "ok\n", "README.md");
        String ours = fixture.commitOn("project", "README.md", "# 订单中台\n");

        List<MergedFile> merged = gateway.threeWayMerge(fixture.workspace, fixture.base, ours, theirs,
                List.of("deploy/new.conf", "README.md"));

        assertThat(merged).filteredOn(file -> file.path().equals("deploy/new.conf")).singleElement()
                .satisfies(file -> {
                    assertThat(file.outcome()).isEqualTo(MergeOutcome.CLEAN);
                    assertThat(file.content()).isEqualTo("ok\n");
                });
        // theirs 里没有 README.md（底座快照不再交付它）而项目改过：删掉项目里有人读的文件
        // 不是升级该顺手做的事，列出来让人决定
        assertThat(merged).filteredOn(file -> file.path().equals("README.md")).singleElement()
                .satisfies(file -> assertThat(file.outcome()).isEqualTo(MergeOutcome.DROPPED_BY_PLATFORM));
    }

    @Test
    void 项目删过的文件按平台是否改过分两种处理() {
        Fixture fixture = baseFixture();
        fixture.deleteOn("platform", "notes.md");
        String theirs = gateway.localBranchSha(fixture.workspace, "platform");
        fixture.checkoutTo("project");
        delete(fixture.workspace, "notes.md");
        String ours = gateway.commitAll(fixture.workspace, "chore: 项目侧删掉 notes.md");

        List<MergedFile> merged = gateway.threeWayMerge(fixture.workspace, fixture.base, ours, theirs,
                List.of("notes.md"));

        // 两侧都删了 → 没事可做；而「平台改了、项目删了」才是真需要人表态的那一格
        assertThat(merged).singleElement().satisfies(file ->
                assertThat(file.outcome()).isEqualTo(MergeOutcome.UNCHANGED));
    }

    @Test
    void 非文本内容不参与自动合并() throws IOException {
        Fixture fixture = baseFixture();
        Path png = fixture.workspace.resolve("assets.png");
        Files.createDirectories(png.getParent());

        gateway.checkoutBranch(fixture.workspace, "platform", fixture.base);
        Files.write(png, new byte[]{(byte) 0xFF, (byte) 0xFE, 1});
        String theirs = gateway.commitAll(fixture.workspace, "feat: 平台侧带了一个二进制");
        gateway.checkoutBranch(fixture.workspace, "project", fixture.base);
        Files.write(png, new byte[]{(byte) 0xFF, (byte) 0xFE, 2});
        String ours = gateway.commitAll(fixture.workspace, "chore: 项目侧也改了它");

        List<MergedFile> merged = gateway.threeWayMerge(fixture.workspace, fixture.base, ours, theirs,
                List.of("assets.png"));

        assertThat(merged).singleElement().satisfies(file -> {
            assertThat(file.outcome()).isEqualTo(MergeOutcome.BINARY);
            assertThat(file.content()).isNull();
        });
    }

    /** 三方合并夹具：一个 base 提交（pom.xml + README.md + notes.md + Dockerfile），平台侧与项目侧各自从它分叉 */
    private final class Fixture {
        private final Path workspace;
        private final String base;

        private Fixture() {
            workspace = tempDir.resolve("upgrade-" + System.nanoTime());
            gateway.initEmpty(workspace, remoteUrl, "base");
            write(workspace, "pom.xml", POM_BASE);
            write(workspace, "README.md", "# scaffold\n");
            write(workspace, "notes.md", "原始说明\n");
            write(workspace, "Dockerfile", "FROM temurin:17\nCOPY a b\n");
            base = gateway.commitAll(workspace, "chore: 交付时的底座快照");
            gateway.pushBranch(workspace, "base");
        }

        /** 在指定分支上改一个文件并提交，返回那个提交号 */
        private String commitOn(String branch, String path, String content) {
            gateway.checkoutBranch(workspace, branch, base);
            write(workspace, path, content);
            String sha = gateway.commitAll(workspace, "chore: " + branch + " 改 " + path);
            gateway.pushBranch(workspace, branch);
            return sha;
        }

        /** 同一分支上「加一个、删一个」：底座快照收紧裁剪清单时的真实形状 */
        private String commitAddDropOn(String branch, String add, String content, String drop) {
            gateway.checkoutBranch(workspace, branch, base);
            write(workspace, add, content);
            delete(workspace, drop);
            String sha = gateway.commitAll(workspace, "chore: " + branch + " 加 " + add + " 撤 " + drop);
            gateway.pushBranch(workspace, branch);
            return sha;
        }

        private void deleteOn(String branch, String path) {
            gateway.checkoutBranch(workspace, branch, base);
            delete(workspace, path);
            gateway.commitAll(workspace, "chore: " + branch + " 删 " + path);
            gateway.pushBranch(workspace, branch);
        }

        private void checkoutTo(String branch) {
            gateway.checkoutBranch(workspace, branch, base);
        }
    }

    private Fixture baseFixture() {
        return new Fixture();
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
