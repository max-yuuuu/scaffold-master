package com.scaffold.lowcode.workspace;

import com.scaffold.common.exception.BusinessException;
import com.scaffold.common.tenant.TenantContext;
import com.scaffold.framework.security.SecurityUtils;
import com.scaffold.lowcode.project.domain.ProjectSyncStatus;
import com.scaffold.lowcode.project.mapper.LcProjectMapper;
import com.scaffold.lowcode.repo.RepoUrlValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 项目仓库与工作区的编排（方案 §6.1、§6.2、§6.3）：连通性检查、固定代码首次初始化、
 * 工作区状态与重建。
 *
 * <p>三条硬约定：
 * <ul>
 *   <li>平台只写发布分支（{@code gen_branch}），永不强推；主干归使用方。</li>
 *   <li>固定代码（A 档）只在初始化时写一次，之后平台不覆盖 —— 已就绪的项目再次初始化会被拒绝，
 *       要重来必须先重建工作区，而工作区里有未推送提交时重建也会被拒绝。</li>
 *   <li>动作全部同步执行：这是单机研发期工具（§7.1），一次初始化就是「读清单 + 拷文件 + 提交 + 推送」，
 *       放在请求线程里做，租户上下文与失败原因都能如实回给人，不引入一套异步任务状态。</li>
 * </ul>
 */
@Service
public class ProjectRepositoryService {

    /** 交付快照的文件数上限：底座膨胀到这个量级说明清单或仓库出了大问题，先停下来再说 */
    private static final int MAX_SNAPSHOT_FILES = 20000;

    private final LcProjectMapper projectMapper;
    private final GitGateway gateway;
    private final Path workspaceRoot;
    private final String configuredSourceRoot;
    private final String scaffoldUiVersion;
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    public ProjectRepositoryService(
            LcProjectMapper projectMapper,
            GitGateway gateway,
            @Value("${scaffold.lowcode.workspace-root:./lowcode-workspace}") String workspaceRoot,
            @Value("${scaffold.lowcode.scaffold-source-root:}") String configuredSourceRoot,
            @Value("${scaffold.lowcode.scaffold-ui-version:" + ScaffoldTrimPolicy.DEFAULT_SCAFFOLD_UI_VERSION + "}")
            String scaffoldUiVersion) {
        this.projectMapper = projectMapper;
        this.gateway = gateway;
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
        this.configuredSourceRoot = configuredSourceRoot;
        this.scaffoldUiVersion = scaffoldUiVersion;
    }

    /**
     * 仓库连通性检查（§6.2 第 1 步）。只读：检查失败不把项目打成「仓库不可达」，
     * 那是初始化动作的责任，人工点一下检查不该改变生命周期。
     */
    public Map<String, Object> check(Long projectId) {
        Map<String, Object> project = requireProject(projectId);
        String repoUrl = RepoUrlValidator.normalize(text(project.get("repoUrl")));
        String mainBranch = requireBranch(project.get("mainBranch"));
        String genBranch = requireBranch(project.get("genBranch"));

        List<RemoteRef> refs;
        try {
            refs = gateway.lsRemote(repoUrl);
        } catch (WorkspaceGitException e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("repoUrl", repoUrl);
            result.put("reachable", false);
            result.put("message", e.getMessage());
            return result;
        }
        Set<String> branches = branchNames(refs);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("repoUrl", repoUrl);
        result.put("reachable", true);
        result.put("empty", refs.isEmpty());
        result.put("branches", List.copyOf(branches));
        result.put("mainBranch", mainBranch);
        result.put("genBranch", genBranch);
        result.put("mainBranchExists", branches.contains(mainBranch));
        result.put("genBranchExists", branches.contains(genBranch));
        result.put("action", refs.isEmpty()
                ? "空仓库：初始化会直接创建发布分支并推入固定代码"
                : branches.contains(mainBranch)
                        ? "仓库已有主干：初始化把固定代码推到发布分支，主干不动"
                        : "仓库非空但找不到主干，请先在仓库侧确认分支名");
        return result;
    }

    /**
     * 首次初始化：绑定仓库 → clone（空仓库走未出生 HEAD）→ 写固定代码 → 提交 → 推发布分支 → 回读校验。
     */
    public Map<String, Object> initialize(Long projectId) {
        Map<String, Object> project = requireProject(projectId);
        int current = number(project.get("syncStatus"), ProjectSyncStatus.UNINITIALIZED);
        if (current == ProjectSyncStatus.INITIALIZING) {
            throw new BusinessException("该项目正在初始化，请等待当前任务结束");
        }
        if (current == ProjectSyncStatus.READY) {
            throw new BusinessException("固定代码已初始化。平台不覆盖 A 档文件，需要重来请先清空工作区再初始化");
        }
        if (number(project.get("status"), 1) != 1) {
            throw new BusinessException("项目已停用，先启用再初始化仓库");
        }

        String repoUrl = RepoUrlValidator.normalize(text(project.get("repoUrl")));
        String mainBranch = requireBranch(project.get("mainBranch"));
        String genBranch = requireBranch(project.get("genBranch"));
        String relativePath = workspaceRelativePath(project);
        Path workspace = workspaceRoot.resolve(relativePath);
        if (Files.exists(workspace) && !isEmptyDirectory(workspace)) {
            throw new BusinessException("工作区已有内容：" + relativePath + "。请先重建工作区，再初始化");
        }
        if (!running.add(projectId)) {
            throw new BusinessException("同一项目的仓库动作正在执行中，请稍后重试");
        }

        String operator = operator();
        boolean createdHere = false;
        try {
            // 先读底座源码出清单：裁剪清单不合法时不碰远端，也不留半个工作区
            update(projectId, ProjectSyncStatus.INITIALIZING, "读取底座裁剪清单", null, null, null, false, operator);
            Path sourceRoot = ScaffoldSourceLocator.locate(configuredSourceRoot);
            ScaffoldSnapshot snapshot = ScaffoldSnapshotPlanner.plan(sourceRoot, scaffoldUiVersion);
            if (snapshot.files().size() > MAX_SNAPSHOT_FILES) {
                throw new BusinessException("交付快照文件数异常：" + snapshot.files().size());
            }
            String baseScaffoldSha = gateway.headSha(sourceRoot);

            update(projectId, ProjectSyncStatus.INITIALIZING, "连接远端仓库", null, null, null, false, operator);
            List<RemoteRef> refs = gateway.lsRemote(repoUrl);
            Set<String> branches = branchNames(refs);

            String commitSha;
            int fileCount;
            int pruned = 0;
            if (refs.isEmpty()) {
                // §6.3 空仓库：clone 得到的是没有 HEAD 的未出生状态，直接在目标分支上提交第一次
                gateway.initEmpty(workspace, repoUrl, genBranch);
                createdHere = true;
                fileCount = ScaffoldSnapshotWriter.write(workspace, snapshot);
                ProjectMarker.write(workspace, text(project.get("projectCode")), text(project.get("projectName")),
                        baseScaffoldSha, mainBranch, genBranch);
                commitSha = requireCommit(gateway.commitAll(workspace, commitMessage(project, "初始化固定代码")),
                        "固定代码没有产生任何改动");
            } else {
                if (!branches.contains(mainBranch)) {
                    throw new BusinessException("仓库非空但主干分支 " + mainBranch + " 不存在，请先在仓库侧确认分支名");
                }
                gateway.cloneBranch(workspace, repoUrl, mainBranch);
                createdHere = true;
                requireScaffoldRepo(workspace, project);
                // 远端已有发布分支时以它为起点，否则从主干切一条：推上去才是快进，不会撞分支保护
                String startPoint = "origin/" + (branches.contains(genBranch) ? genBranch : mainBranch);
                gateway.checkoutBranch(workspace, genBranch, startPoint);
                fileCount = ScaffoldSnapshotWriter.write(workspace, snapshot);
                // 发布分支由平台维护，快照就是它应有的全量内容：上一版遗留的文件（如抽包前的组件源码拷贝）
                // 必须一起删，否则项目里会同时存在「npm 包里的组件」和「本地拷贝的组件」两份物料
                pruned = ScaffoldSnapshotWriter.pruneOutsideSnapshot(workspace, snapshot);
                ProjectMarker.write(workspace, text(project.get("projectCode")), text(project.get("projectName")),
                        baseScaffoldSha, mainBranch, genBranch);
                commitSha = gateway.commitAll(workspace, commitMessage(project, "同步底座固定代码"));
                if (commitSha == null) {
                    // 与远端完全一致：不制造空提交，也不谎称推送过。此时仓库里已有的那份内容就是
                    // 平台认下的 A 档快照，底座标记打在它上面（发布分支优先，没有就落在主干）
                    String delivered = remoteSha(refs, genBranch) != null
                            ? remoteSha(refs, genBranch) : remoteSha(refs, mainBranch);
                    String tagWarning = stampBaseTag(workspace, baseScaffoldSha, delivered, refs);
                    update(projectId, ProjectSyncStatus.READY, "固定代码与远端一致，无需提交" + tagWarning,
                            relativePath, baseScaffoldSha, remoteSha(refs, genBranch), false, operator);
                    return result(project, ProjectSyncStatus.READY, "内容与远端一致，未产生提交" + tagWarning,
                            relativePath, fileCount, remoteSha(refs, genBranch), baseScaffoldSha);
                }
            }

            update(projectId, ProjectSyncStatus.INITIALIZING, "推送发布分支 " + genBranch, null, null, null, false, operator);
            gateway.pushBranch(workspace, genBranch);

            // 回读校验：远端 ref 确实指向这个提交才算就绪（§6.3）
            List<RemoteRef> afterPush = gateway.lsRemote(repoUrl);
            String confirmed = remoteSha(afterPush, genBranch);
            if (confirmed == null || !confirmed.equals(commitSha)) {
                throw new WorkspaceGitException(ProjectSyncStatus.DIVERGED,
                        "推送后回读远端 " + genBranch + " 得到的提交是 " + confirmed + "，与本次提交 " + commitSha + " 不一致");
            }
            String tagWarning = stampBaseTag(workspace, baseScaffoldSha, commitSha, afterPush);
            update(projectId, ProjectSyncStatus.READY,
                    "固定代码已推送到 " + genBranch + (pruned > 0 ? "，并清理快照外旧文件 " + pruned + " 个" : "") + tagWarning,
                    relativePath, baseScaffoldSha, commitSha, true, operator);
            return result(project, ProjectSyncStatus.READY, "初始化完成" + tagWarning, relativePath, fileCount,
                    commitSha, baseScaffoldSha);
        } catch (WorkspaceGitException e) {
            update(projectId, e.getSyncStatus(), e.getMessage(), null, null, null, false, operator);
            if (createdHere) {
                ScaffoldSnapshotWriter.deleteWorkspace(workspace);
            }
            throw e;
        } catch (BusinessException e) {
            update(projectId, ProjectSyncStatus.UNINITIALIZED, e.getMessage(), null, null, null, false, operator);
            if (createdHere) {
                ScaffoldSnapshotWriter.deleteWorkspace(workspace);
            }
            throw e;
        } finally {
            running.remove(projectId);
        }
    }

    /** 工作区读数：只读，给人判断「本地是不是有未推送的东西」 */
    public Map<String, Object> workspace(Long projectId) {
        Map<String, Object> project = requireProject(projectId);
        String repoUrl = text(project.get("repoUrl"));
        String genBranch = requireBranch(project.get("genBranch"));
        Path workspace = workspaceRoot.resolve(workspaceRelativePath(project));
        boolean exists = Files.isDirectory(workspace.resolve(".git"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", workspaceRelativePath(project));
        result.put("exists", exists);
        result.put("genBranch", genBranch);
        result.put("syncStatus", project.get("syncStatus"));
        result.put("syncStatusText", ProjectSyncStatus.text(number(project.get("syncStatus"), ProjectSyncStatus.UNINITIALIZED)));
        String baseSha = text(project.get("baseScaffoldSha"));
        String baseTag = baseSha == null || baseSha.isBlank() ? null : PlatformBaseTag.name(baseSha);
        result.put("baseScaffoldSha", baseSha);
        result.put("baseTag", baseTag);
        if (!exists) {
            result.put("dirty", false);
            result.put("unpushed", false);
            result.put("localSha", null);
            result.put("remoteSha", null);
            // 没有工作区就不去问远端：底座标记是否推上去留 null 表示「未核对」，
            // 前端按三态显示，不要当成「缺失」
            result.put("baseTagExists", null);
            result.put("message", "工作区尚未创建，初始化或重建后才有内容");
            return result;
        }
        List<RemoteRef> remoteRefs = RepoUrlValidator.isBlank(repoUrl) ? List.of() : gateway.lsRemote(repoUrl);
        String localSha = gateway.localBranchSha(workspace, genBranch);
        String remoteSha = remoteSha(remoteRefs, genBranch);
        result.put("dirty", gateway.isDirty(workspace));
        result.put("localSha", localSha);
        result.put("remoteSha", remoteSha);
        result.put("baseTagExists", baseTag != null && remoteTagName(remoteRefs, baseTag) != null);
        // 本地有提交而远端没有对应提交，就是未推送：平台重建工作区前必须把这个问清楚
        result.put("unpushed", localSha != null && !localSha.equals(remoteSha));
        return result;
    }

    /**
     * 清空工作区（§5.3 的「工作区可从 clone + 全量重生成重建」）。
     *
     * <p>本地存在未提交改动或未推送提交时拒绝执行：这两个都可能包含人写的东西，
     * 平台不能静默丢弃，必须先由人搬运或推送。
     */
    public Map<String, Object> resetWorkspace(Long projectId) {
        Map<String, Object> project = requireProject(projectId);
        String genBranch = requireBranch(project.get("genBranch"));
        Path workspace = workspaceRoot.resolve(workspaceRelativePath(project));
        if (!Files.isDirectory(workspace)) {
            // 目录不在（换过服务器、清过卷，或手工删过目录）而状态还停在就绪，项目会被两端守卫同时挡死：
            // 「已就绪不许再初始化」+「没有工作区不许清空」。这里只把平台侧状态清回未初始化，
            // 远端分支与已推送内容一概不动，因此不需要脏工作区/未推送提交那些守卫。
            if (number(project.get("syncStatus"), ProjectSyncStatus.UNINITIALIZED) == ProjectSyncStatus.UNINITIALIZED) {
                throw new BusinessException("工作区不存在，无需重建");
            }
            update(projectId, ProjectSyncStatus.UNINITIALIZED, "工作区目录不在，状态已重置，可重新初始化",
                    null, null, null, false, operator());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", workspaceRelativePath(project));
            result.put("cleared", true);
            result.put("syncStatus", ProjectSyncStatus.UNINITIALIZED);
            return result;
        }
        if (!running.add(projectId)) {
            throw new BusinessException("同一项目的仓库动作正在执行中，请稍后重试");
        }
        try {
            if (Files.isDirectory(workspace.resolve(".git")) && gateway.isDirty(workspace)) {
                throw new BusinessException("工作区有未提交改动，平台不会替您丢弃：请先在本地提交或手工搬走");
            }
            String localSha = gateway.localBranchSha(workspace, genBranch);
            String remoteSha = remoteSha(gateway.lsRemote(RepoUrlValidator.normalize(text(project.get("repoUrl")))), genBranch);
            if (localSha != null && !localSha.equals(remoteSha)) {
                throw new BusinessException("发布分支 " + genBranch + " 上有未推送提交（本地 " + localSha
                        + "，远端 " + (remoteSha == null ? "无" : remoteSha) + "）。先推送或搬走这些提交，再重建工作区");
            }
            ScaffoldSnapshotWriter.deleteWorkspace(workspace);
            update(projectId, ProjectSyncStatus.UNINITIALIZED, "工作区已清空，可重新初始化", null, null, null, false, operator());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", workspaceRelativePath(project));
            result.put("cleared", true);
            result.put("syncStatus", ProjectSyncStatus.UNINITIALIZED);
            return result;
        } finally {
            running.remove(projectId);
        }
    }

    /** 供测试与其它模块读取当前租户的项目行；不存在或不属于当前租户时抛业务异常 */
    private Map<String, Object> requireProject(Long projectId) {
        Map<String, Object> project = projectMapper.selectProjectForSync(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        return project;
    }

    /** 工作区相对路径：{租户 ID}/{项目编码}，两个分量都不是用户自由填写的字符串 */
    private String workspaceRelativePath(Map<String, Object> project) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("缺少租户上下文，无法定位工作区");
        }
        String projectCode = text(project.get("projectCode"));
        if (projectCode == null || !projectCode.matches("^[a-z][a-z0-9-]{1,31}$")) {
            throw new BusinessException("项目编码不符合约定，无法定位工作区目录：" + projectCode);
        }
        return tenantId + "/" + projectCode;
    }

    /** §6.3：仓库非空时要求认得出这是脚手架仓库，避免把平台产物推进一个不相干的仓库 */
    private void requireScaffoldRepo(Path workspace, Map<String, Object> project) {
        if (ProjectMarker.exists(workspace)) {
            return;
        }
        if (Files.isRegularFile(workspace.resolve("scaffold-system/pom.xml"))
                && Files.isRegularFile(workspace.resolve("scaffold-vue/package.json"))) {
            return;
        }
        throw new BusinessException("仓库 " + project.get("projectCode") + " 非空，但既没有 "
                + ScaffoldTrimPolicy.MARKER_FILE + "，也没有脚手架工程结构，平台拒绝往里推固定代码");
    }

    private String requireBranch(Object value) {
        String branch = text(value);
        if (branch == null || !gateway.isValidBranch(branch)) {
            throw new BusinessException("分支名不合法：" + branch);
        }
        return branch;
    }

    private String requireCommit(String sha, String emptyMessage) {
        if (sha == null) {
            throw new BusinessException(emptyMessage);
        }
        return sha;
    }

    private String commitMessage(Map<String, Object> project, String action) {
        return "chore(" + project.get("projectCode") + "): " + action
                + "（元搭低代码平台 " + ScaffoldTrimPolicy.MARKER_FILE + "）";
    }

    private void update(Long projectId, int syncStatus, String message, String workspacePath,
                        String baseScaffoldSha, String lastCommitSha, boolean markInit, String operator) {
        projectMapper.updateSyncState(projectId, syncStatus, truncate(message), workspacePath,
                baseScaffoldSha, lastCommitSha, markInit, operator);
    }

    private Map<String, Object> result(Map<String, Object> project, int syncStatus, String message,
                                       String path, int fileCount, String commitSha, String baseScaffoldSha) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", project.get("id"));
        result.put("path", path);
        result.put("syncStatus", syncStatus);
        result.put("syncStatusText", ProjectSyncStatus.text(syncStatus));
        result.put("files", fileCount);
        result.put("commitSha", commitSha);
        result.put("baseScaffoldSha", baseScaffoldSha);
        result.put("baseTag", baseScaffoldSha == null || baseScaffoldSha.isBlank()
                ? null : PlatformBaseTag.name(baseScaffoldSha));
        result.put("message", message);
        return result;
    }

    /**
     * 打底座标记 tag（方案 §5.2：三方合并的 base 侧留痕）。
     *
     * <p>三条口径：只创建不移动（同名 tag 已存在就原样留着）；失败只告警不回滚（分支已经推上去了，
     * 不该因为一个 tag 让整次初始化失败并删掉工作区）；拿不到底座提交号时必须喊出来 —— 那等于这个
     * 项目以后做不了三方底座升级，静默过去会让人以为一切正常。
     *
     * @return 需要拼进就绪消息的告警；一切正常返回空串
     */
    private String stampBaseTag(Path workspace, String baseScaffoldSha, String snapshotCommit, List<RemoteRef> remoteRefs) {
        if (baseScaffoldSha == null || baseScaffoldSha.isBlank()) {
            return "；底座 checkout 读不到提交号（挂进来的可能不是 git 仓库），未打底座标记 tag："
                    + "该项目后续无法做三方底座升级，请把带 .git 的底座仓库挂给平台后重建工作区再初始化";
        }
        if (snapshotCommit == null) {
            return "";
        }
        String tagName = PlatformBaseTag.name(baseScaffoldSha);
        if (remoteTagName(remoteRefs, tagName) != null) {
            return "";
        }
        try {
            if (!gateway.tagCommit(workspace, tagName, snapshotCommit, PlatformBaseTag.message(baseScaffoldSha))) {
                return "";
            }
            gateway.pushTag(workspace, tagName);
        } catch (RuntimeException e) {
            return "；底座标记 tag " + tagName + " 没推上去（初始化本身已完成，但补上这个 tag 之前"
                    + "该项目做不了三方底座升级）：" + e.getMessage();
        }
        return "";
    }

    private static String remoteTagName(List<RemoteRef> refs, String tagName) {
        for (RemoteRef ref : refs) {
            if (tagName.equals(ref.tagName())) {
                return ref.sha();
            }
        }
        return null;
    }

    private static Set<String> branchNames(List<RemoteRef> refs) {
        Set<String> names = new LinkedHashSet<>();
        for (RemoteRef ref : refs) {
            String branch = ref.branchName();
            if (branch != null) {
                names.add(branch);
            }
        }
        return names;
    }

    private static String remoteSha(List<RemoteRef> refs, String branch) {
        for (RemoteRef ref : refs) {
            if (branch.equals(ref.branchName())) {
                return ref.sha();
            }
        }
        return null;
    }

    private static boolean isEmptyDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> children = Files.list(dir)) {
            return children.findAny().isEmpty();
        } catch (IOException e) {
            throw new BusinessException("检查工作区目录失败：" + e.getMessage());
        }
    }

    /** sync_message 是 VARCHAR(500)，JGit 的原始报错可能更长，截断而不是让状态写库失败 */
    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 497) + "...";
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int number(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
    }

    private String operator() {
        return SecurityUtils.getUsername();
    }
}
