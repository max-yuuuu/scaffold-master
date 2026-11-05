package com.scaffold.admin;

import com.scaffold.common.exception.BusinessException;
import com.scaffold.lowcode.workspace.ChangedFile;
import com.scaffold.lowcode.workspace.GitGateway;
import com.scaffold.lowcode.workspace.MergeCheck;
import com.scaffold.lowcode.workspace.RemoteRef;
import com.scaffold.lowcode.workspace.ScaffoldTrimPolicy;
import com.scaffold.lowcode.workspace.WorkspaceGitException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 假远端：引用列表、提交号、脏与不可达都由测试摆布。
 *
 * <p>真实 JGit 语义在 {@code JGitGatewayTest} 里对着本机裸仓库验，平台侧要验的是状态机怎么按
 * 成功 / 不可达 / 未推送 / 重复初始化推进 {@code sync_status}，以及固定代码是不是真按裁剪清单落了盘 ——
 * 后者只需要一个「能 clone 出目录、能提交、能推」的桩。工作区落盘的端到端用例（{@code DeliveryGenerationApiTest}）
 * 也要这份产物，所以从 {@code ProjectWorkspaceApiTest} 里提成公用的。
 */
public class StubGitGateway implements GitGateway {

    List<RemoteRef> remoteRefs = new ArrayList<>();
    WorkspaceGitException transportFailure;
    String localShaOverride;
    boolean dirty;
    /** clone 出来的仓库按「抽包前的旧形态」铺几个文件，用来验全量初始化会清理快照外的遗留 */
    boolean legacyFormOnClone;
    /** 底座 checkout 不是 git 仓库：headSha 返回 null，用来验平台会如实喊出来 */
    boolean noBaseSha;
    /** 远端拒绝接收 tag（tag 保护一类），初始化不该因此回滚 */
    boolean tagPushFails;
    /** 快照与远端完全一致：commitAll 返回 null，走「无需提交」那条路 */
    boolean noChangesOnCommit;
    final List<String> pushedBranches = new ArrayList<>();
    final List<String> pushedTags = new ArrayList<>();
    /** 用例可以据此断言「审查前确实同步过远端读数」 */
    final List<String> fetches = new ArrayList<>();
    final Map<String, String> localTags = new LinkedHashMap<>();

    void reset() {
        remoteRefs = new ArrayList<>();
        transportFailure = null;
        localShaOverride = null;
        dirty = false;
        legacyFormOnClone = false;
        noBaseSha = false;
        tagPushFails = false;
        noChangesOnCommit = false;
        pushedBranches.clear();
        pushedTags.clear();
        fetches.clear();
        localTags.clear();
    }

    @Override
    public List<RemoteRef> lsRemote(String repoUrl) {
        if (transportFailure != null) {
            throw transportFailure;
        }
        return List.copyOf(remoteRefs);
    }

    @Override
    public void initEmpty(Path dir, String repoUrl, String initialBranch) {
        touchRepo(dir);
    }

    @Override
    public void cloneBranch(Path dir, String repoUrl, String branch) {
        touchRepo(dir);
        if (legacyFormOnClone) {
            // 模拟抽包之前推出去的旧形态：认得出脚手架（marker），但组件源码是拷进项目的
            try {
                Files.createDirectories(dir.resolve("scaffold-vue/src/components/business"));
                Files.writeString(dir.resolve("scaffold-vue/src/components/business/ListPage.vue"), "<template>旧拷贝</template>");
                Files.writeString(dir.resolve(ScaffoldTrimPolicy.MARKER_FILE), "{\"projectCode\":\"x\"}");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** 假远端只补一个 .git 目录，让「工作区是不是已就绪的仓库」这个判断在测试里也成立 */
    private void touchRepo(Path dir) {
        try {
            Files.createDirectories(dir.resolve(".git"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void checkoutBranch(Path dir, String branch, String startPoint) {
        // 同上
    }

    @Override
    public String commitAll(Path dir, String message) {
        if (noChangesOnCommit) {
            return null;
        }
        return localShaOverride != null ? localShaOverride : "a".repeat(40);
    }

    @Override
    public void pushBranch(Path dir, String branch) {
        if (transportFailure != null) {
            throw transportFailure;
        }
        pushedBranches.add(branch);
        String sha = localShaOverride != null ? localShaOverride : "a".repeat(40);
        remoteRefs = withBranch(remoteRefs, branch, sha);
    }

    @Override
    public boolean isDirty(Path dir) {
        return dirty;
    }

    @Override
    public String localBranchSha(Path dir, String branch) {
        return localShaOverride != null ? localShaOverride : remoteSha(branch);
    }

    @Override
    public boolean isValidBranch(String branch) {
        return branch != null && !branch.isBlank() && !branch.contains("..");
    }

    @Override
    public String headSha(Path repoDir) {
        return noBaseSha ? null : "b".repeat(40);
    }

    @Override
    public boolean tagCommit(Path dir, String tagName, String sha, String message) {
        return localTags.putIfAbsent(tagName, sha) == null;
    }

    @Override
    public void pushTag(Path dir, String tagName) {
        if (tagPushFails) {
            throw new BusinessException("远端拒绝推送 底座标记 tag " + tagName + "：模拟 tag 保护");
        }
        pushedTags.add(tagName);
        remoteRefs = withTag(remoteRefs, tagName, localTags.get(tagName));
    }

    /* ── 合并请求的只读原语：桩不模拟仓库内容，被调到就是用例走错了门 ── */

    @Override
    public void fetch(Path dir) {
        fetches.add(dir.toString());
    }

    @Override
    public String mergeBase(Path dir, String oneRev, String otherRev) {
        throw new UnsupportedOperationException("StubGitGateway 不模拟提交历史：分叉点要用真 JGit，见 JGitGatewayTest 或 LocalBareGitGateway");
    }

    @Override
    public List<ChangedFile> diffFiles(Path dir, String baseSha, String headSha) {
        throw new UnsupportedOperationException("StubGitGateway 不模拟文件差异：清单要用真 JGit，见 JGitGatewayTest 或 LocalBareGitGateway");
    }

    @Override
    public MergeCheck checkMerge(Path dir, String oursRev, String theirsRev) {
        throw new UnsupportedOperationException("StubGitGateway 不试算合并：预检要用真 JGit，见 JGitGatewayTest 或 LocalBareGitGateway");
    }

    private String remoteSha(String branch) {
        return remoteRefs.stream().filter(ref -> branch.equals(ref.branchName())).map(RemoteRef::sha).findFirst().orElse(null);
    }

    private static List<RemoteRef> withBranch(List<RemoteRef> refs, String branch, String sha) {
        List<RemoteRef> next = new ArrayList<>(refs);
        next.removeIf(ref -> branch.equals(ref.branchName()));
        if (sha != null) {
            next.add(new RemoteRef("refs/heads/" + branch, sha));
        }
        return next;
    }

    private static List<RemoteRef> withTag(List<RemoteRef> refs, String tagName, String sha) {
        List<RemoteRef> next = new ArrayList<>(refs);
        next.removeIf(ref -> tagName.equals(ref.tagName()));
        if (sha != null) {
            next.add(new RemoteRef("refs/tags/" + tagName, sha));
        }
        return next;
    }

    /** 换成假远端：被测的是平台侧的状态机与落盘，不是 JGit 的传输 */
    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        GitGateway stubGitGateway() {
            return new StubGitGateway();
        }
    }
}
