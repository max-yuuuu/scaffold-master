package com.scaffold.lowcode.workspace;

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
 * <p>这里用本机临时裸仓库当真仓库跑，覆盖三条最容易出事的语义：空仓库的未出生 HEAD 能不能直接
 * 提交并显式建分支、非快进推送会不会被如实拒绝（平台不强推）、脏与未推送能不能查出来。
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
    void 空仓库首次初始化能显式建出发布分支() {
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
