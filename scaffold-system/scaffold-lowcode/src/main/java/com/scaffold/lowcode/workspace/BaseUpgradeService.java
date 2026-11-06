package com.scaffold.lowcode.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.framework.security.SecurityUtils;
import com.scaffold.lowcode.project.mapper.LcBaseUpgradeMapper;
import com.scaffold.lowcode.project.domain.ProjectSyncStatus;
import com.scaffold.lowcode.project.mapper.LcProjectMapper;
import com.scaffold.lowcode.repo.RepoUrlValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 底座升级（方案 §5.2）：把「当前底座的 A 档」下发给一个已经交付过的项目。
 *
 * <p>页面发布只碰 B/C 档，A 档（pom、app 壳、登录页、Dockerfile、交付改写表）只在人工发起的
 * 这一次动作里重新生成 —— 它推的是一条独立的升级分支，之后跟合并请求一样由人审、由人合进主干，
 * 所以这一刀不写主干、也不引入第二套「自动生效」的机制。
 *
 * <p>三方合并的三侧：base = 项目仓库里交付时打的 {@code yuanda/platform-base/{sha}} tag，
 * ours = 项目主干，theirs = 按底座 checkout 现生成的那份快照。缺任何一侧都不动：
 * base 侧拿不到就等于没有「当初交付了什么」的事实，硬算出来的「谁改了什么」全是猜的。
 */
@Service
public class BaseUpgradeService {

    /** 升级分支名里的底座短号：7 位是 git 自己的缩写习惯，够人认，也不会撞到分支名长度 */
    private static final int SHORT_SHA = 7;

    /** 待合并：升级分支已经推出去，等人从合并请求里合进主干 */
    private static final int STATUS_PENDING = 0;

    /** 没产生升级分支：算下来一格可自动合的都没有，这条不该出现在「待合并」队列里 */
    private static final int STATUS_NOTHING = 3;

    private final LcProjectMapper projectMapper;
    private final LcBaseUpgradeMapper upgradeMapper;
    private final GitGateway gateway;
    private final WorkspacePaths paths;
    private final ObjectMapper objectMapper;
    private final String configuredSourceRoot;
    private final String scaffoldUiVersion;

    public BaseUpgradeService(LcProjectMapper projectMapper, LcBaseUpgradeMapper upgradeMapper, GitGateway gateway,
                              WorkspacePaths paths, ObjectMapper objectMapper,
                              @Value("${scaffold.lowcode.scaffold-source-root:}") String configuredSourceRoot,
                              @Value("${scaffold.lowcode.scaffold-ui-version:" + ScaffoldTrimPolicy.DEFAULT_SCAFFOLD_UI_VERSION + "}")
                              String scaffoldUiVersion) {
        this.projectMapper = projectMapper;
        this.upgradeMapper = upgradeMapper;
        this.gateway = gateway;
        this.paths = paths;
        this.objectMapper = objectMapper;
        this.configuredSourceRoot = configuredSourceRoot;
        this.scaffoldUiVersion = scaffoldUiVersion;
    }

    /**
     * 发起一次升级。
     *
     * @return 升级分支、逐文件结论的三档计数、那句人话，以及那条记录本身的 ID
     */
    public Map<String, Object> upgrade(Long projectId) {
        Map<String, Object> project = requireProject(projectId);
        String baseSha = requireBaseSha(project);
        Path sourceRoot = ScaffoldSourceLocator.locate(configuredSourceRoot);
        String scaffoldSha = gateway.headSha(sourceRoot);
        if (scaffoldSha == null) {
            throw new BusinessException("底座 checkout " + sourceRoot + " 读不到提交号：它不是一个 git 仓库或被剥掉了 .git。"
                    + "升级要按「当前底座」生成 A 档，这一步没有它就没有内容可下发（§10.4 的不变式）");
        }
        if (scaffoldSha.equals(baseSha)) {
            throw new BusinessException("这个项目已经在当前底座（" + abbreviate(scaffoldSha) + "）上了，"
                    + "没有要下发的固定代码改动");
        }

        ScaffoldSnapshot snapshot = ScaffoldSnapshotPlanner.plan(sourceRoot, scaffoldUiVersion,
                text(project.get("projectName")));
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (SnapshotFile file : snapshot.files()) {
            files.put(file.path(), ScaffoldSnapshotPlanner.content(file));
        }

        String projectCode = text(project.get("projectCode"));
        String mainBranch = text(project.get("mainBranch"));
        String upgradeBranch = "platform-upgrade/" + scaffoldSha.substring(0, SHORT_SHA);
        String message = "chore(底座): 升级固定代码到 " + abbreviate(scaffoldSha) + "（元搭低代码平台，项目 "
                + projectCode + "）";
        BaseUpgrade result = gateway.baseUpgrade(paths.of(projectCode),
                RepoUrlValidator.normalize(text(project.get("repoUrl"))), mainBranch,
                PlatformBaseTag.name(baseSha), files, upgradeBranch, message);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("projectId", projectId);
        row.put("baseScaffoldSha", baseSha);
        row.put("targetScaffoldSha", scaffoldSha);
        row.put("upgradeBranch", result.branch());
        row.put("filesDetail", json(detail(result)));
        row.put("status", result.pushed() ? STATUS_PENDING : STATUS_NOTHING);
        row.put("message", describe(result, mainBranch));
        // 一条 insert 不需要单独的 ledger bean：PublishLedger 那种「四笔必须一起成」才需要避开自调用不走代理
        upgradeMapper.insert(row, SecurityUtils.getUsername());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("upgradeId", row.get("id"));
        out.put("projectCode", projectCode);
        out.put("baseScaffoldSha", baseSha);
        out.put("targetScaffoldSha", scaffoldSha);
        out.put("upgradeBranch", result.branch());
        out.put("commitSha", result.commitSha());
        out.put("clean", result.count(MergeOutcome.CLEAN));
        out.put("conflict", result.count(MergeOutcome.CONFLICT));
        out.put("dropped", result.count(MergeOutcome.DROPPED_BY_PLATFORM));
        out.put("unchanged", result.count(MergeOutcome.UNCHANGED));
        out.put("binary", result.count(MergeOutcome.BINARY));
        out.put("conflictPaths", result.pathsOf(MergeOutcome.CONFLICT));
        out.put("droppedPaths", result.pathsOf(MergeOutcome.DROPPED_BY_PLATFORM));
        out.put("status", row.get("status"));
        out.put("message", row.get("message"));
        return out;
    }

    /**
     * 落后读数。
     *
     * <p>{@code behindCommits} 是 {@code null} 时界面要显示「数不出来」而不是 0：底座 checkout
     * 不是 git 仓库、或项目记的那一笔不在它的历史里（在记不到 sha 的版本上初始化的老项目就是这种），
     * 这时候能给的只有文件级差异，硬报 0 就是「把不知道说成没问题」。
     */
    public Map<String, Object> status(Long projectId) {
        Map<String, Object> project = requireProject(projectId);
        Map<String, Object> out = new LinkedHashMap<>();
        String baseSha = text(project.get("baseScaffoldSha"));
        Path sourceRoot = ScaffoldSourceLocator.locate(configuredSourceRoot);
        String scaffoldSha = gateway.headSha(sourceRoot);
        out.put("baseScaffoldSha", baseSha);
        out.put("scaffoldSha", scaffoldSha);
        out.put("upToDate", scaffoldSha != null && scaffoldSha.equals(baseSha));
        out.put("behindCommits", baseSha == null || baseSha.isBlank() || scaffoldSha == null
                ? null : gateway.commitCountBetween(sourceRoot, baseSha, scaffoldSha));
        out.put("latest", upgradeMapper.selectLatest(projectId));
        return out;
    }

    private Map<String, Object> requireProject(Long projectId) {
        Map<String, Object> project = projectMapper.selectProjectForSync(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        if (number(project.get("status"), 1) != 1) {
            throw new BusinessException("项目「" + project.get("projectCode") + "」已停用，先启用再升级");
        }
        int syncStatus = number(project.get("syncStatus"), ProjectSyncStatus.UNINITIALIZED);
        if (syncStatus != ProjectSyncStatus.READY) {
            throw new BusinessException("项目仓库当前是「" + ProjectSyncStatus.text(syncStatus)
                    + "」，先把固定代码初始化到发布分支再升级");
        }
        return project;
    }

    private static String requireBaseSha(Map<String, Object> project) {
        String baseSha = text(project.get("baseScaffoldSha"));
        if (baseSha == null || baseSha.isBlank()) {
            throw new BusinessException("项目「" + project.get("projectCode") + "」记不到底座提交号："
                    + "它是在底座 checkout 没带 .git 的版本上初始化的，三方合并没有 base 侧。"
                    + "请先补打底座标记 tag 再发起升级");
        }
        return baseSha;
    }

    /** 逐文件结论存成 [{path, outcome}]：冲突与「平台撤掉」的那些事后只能靠这份清单再说一遍 */
    private List<Map<String, Object>> detail(BaseUpgrade result) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MergedFile file : result.files()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("path", file.path());
            row.put("outcome", file.outcome().name());
            rows.add(row);
        }
        return rows;
    }

    private static String describe(BaseUpgrade result, String mainBranch) {
        if (!result.pushed()) {
            return "算下来没有可自动下发的固定代码（冲突 " + result.count(MergeOutcome.CONFLICT)
                    + " 格、平台撤掉 " + result.count(MergeOutcome.DROPPED_BY_PLATFORM)
                    + " 格），没有建升级分支";
        }
        return "已推升级分支 " + result.branch() + "：可自动合 " + result.count(MergeOutcome.CLEAN)
                + " 个文件，冲突 " + result.count(MergeOutcome.CONFLICT)
                + " 个（平台不写它们，请在本地解），平台撤掉 " + result.count(MergeOutcome.DROPPED_BY_PLATFORM)
                + " 个（删不删由人定）。下一步在合并请求里选 " + result.branch() + " → " + mainBranch;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException("升级结论写不成 JSON：" + e.getMessage());
        }
    }

    private static String abbreviate(String sha) {
        return sha == null ? null : sha.substring(0, Math.min(SHORT_SHA, sha.length()));
    }

    private static String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private static int number(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
    }
}
