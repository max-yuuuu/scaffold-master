package com.scaffold.lowcode.merge.service;

import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.common.tenant.TenantContext;
import com.scaffold.framework.security.SecurityUtils;
import com.scaffold.lowcode.merge.domain.MergeRequestState;
import com.scaffold.lowcode.merge.mapper.LcMergeRequestFileMapper;
import com.scaffold.lowcode.merge.mapper.LcMergeRequestMapper;
import com.scaffold.lowcode.project.domain.ProjectSyncStatus;
import com.scaffold.lowcode.project.mapper.LcBaseUpgradeMapper;
import com.scaffold.lowcode.project.mapper.LcProjectMapper;
import com.scaffold.lowcode.repo.RepoUrlValidator;
import com.scaffold.lowcode.workspace.ChangedFile;
import com.scaffold.lowcode.workspace.BaseUpgrade;
import com.scaffold.lowcode.workspace.PlatformBaseTag;
import com.scaffold.lowcode.workspace.GitGateway;
import com.scaffold.lowcode.workspace.MergeCheck;
import com.scaffold.lowcode.workspace.RemoteRef;
import com.scaffold.lowcode.workspace.WorkspacePaths;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合并请求的只读那一半（PR 8 第一刀，方案 §5.4）：算出「发布分支比主干多了什么、能不能自动合」。
 *
 * <p>这一刀<b>不合并、不推送、不写主干</b>。确认合并与推送是第二刀，它要在这份清单与预检结论
 * 之上加人的批准；先把算账这一半独立出来，是因为验收里最难的一条 ——「目标分支前进后旧请求自动转陈旧」
 * —— 只取决于这里算的那三个提交号对不对，跟合不合并不相干，两刀混在一起反而各自都难验。
 *
 * <p>四条不那么显然的口径，代码里各有一个拒绝分支等着：
 * <ul>
 *   <li><b>先 fetch 再算</b>：工作区是按发布分支 clone 的，主干那条分支的内容本来就不在本地对象库里。
 *       不 fetch 就报「读不到这个提交」，而更坏的情况是读到一份过期的远端引用，清单看着像模像样却是假的；</li>
 *   <li><b>清单按分叉点算</b>：{@code merge-base..发布分支} 才是「这次发布带来的改动」。按
 *       {@code 主干..发布分支} 算会把主干上别人新提交的东西反向显示成「发布分支要删掉它」；</li>
 *   <li><b>工作区脏就不预检</b>：三路合并按工作区当前状态判 dirty，脏着算出来的冲突清单不可信，
 *       宁可不给结论；</li>
 *   <li><b>陈旧只改状态</b>：那三个提交号记的是「这条请求算的是哪一对提交」，改了它这句话就不成立了。</li>
 * </ul>
 */
@Service
public class MergeRequestService {

    private static final int MESSAGE_LIMIT = 500;

    private final LcProjectMapper projectMapper;
    private final LcMergeRequestMapper requestMapper;
    private final LcMergeRequestFileMapper fileMapper;
    private final MergeRequestLedger ledger;
    private final GitGateway gateway;
    private final WorkspacePaths paths;
    private final LcBaseUpgradeMapper upgradeMapper;

    public MergeRequestService(LcProjectMapper projectMapper, LcMergeRequestMapper requestMapper,
                               LcMergeRequestFileMapper fileMapper, MergeRequestLedger ledger,
                               GitGateway gateway, WorkspacePaths paths, LcBaseUpgradeMapper upgradeMapper) {
        this.projectMapper = projectMapper;
        this.requestMapper = requestMapper;
        this.fileMapper = fileMapper;
        this.ledger = ledger;
        this.gateway = gateway;
        this.paths = paths;
        this.upgradeMapper = upgradeMapper;
    }

    /**
     * 重算一次：fetch → 读两侧远端提交 → 分叉点 → 变更清单 → 试算 → 落账。
     *
     * @return 重算后的请求视图（含文件数与冲突数）
     */
    public Map<String, Object> refresh(Long projectId, String requestedSource) {
        Map<String, Object> project = requireProject(projectId);
        Path workspace = requireWorkspace(project);
        String repoUrl = RepoUrlValidator.normalize(text(project.get("repoUrl")));
        String genBranch = text(project.get("genBranch"));
        String sourceBranch = requireAllowedSource(requestedSource, genBranch);
        String targetBranch = text(project.get("mainBranch"));
        // 提示语里叫它「发布分支」还是「升级分支」按来源走：同一条守卫的话要说对话，
        // 拿升级分支去念「还没有页面发布到这条分支上」就是把人往错的方向指
        String sourceLabel = sourceBranch.equals(genBranch) ? "发布分支" : "来源分支";

        if (gateway.isDirty(workspace)) {
            throw new BusinessException("工作区 " + paths.relative(text(project.get("projectCode")))
                    + " 里有未提交的改动，合并预检的结论不可信：请先在项目仓库里处理掉这些改动（提交或搬走）再重算");
        }
        gateway.fetch(workspace);

        List<RemoteRef> refs = gateway.lsRemote(repoUrl);
        String sourceSha = remoteSha(refs, sourceBranch);
        String targetSha = remoteSha(refs, targetBranch);
        if (sourceSha == null) {
            throw new BusinessException("远端没有" + sourceLabel + " " + sourceBranch + "：还没有内容推到这条分支上，"
                    + "也就没有要合进主干的东西");
        }
        if (targetSha == null) {
            throw new BusinessException("远端没有主干 " + targetBranch + "：合并的目标不存在。"
                    + "平台不替你建主干（那等于替你决定仓库的起点），请先在仓库侧建出来");
        }

        String baseSha = gateway.mergeBase(workspace, targetSha, sourceSha);
        if (baseSha == null) {
            throw new BusinessException(sourceLabel + " " + sourceBranch + " 与主干 " + targetBranch
                    + " 没有共同祖先（两条历史是各自初始化的）：先让它从主干重新起，再来算清单");
        }

        List<ChangedFile> files = gateway.diffFiles(workspace, baseSha, sourceSha);
        if (files.isEmpty()) {
            // 零变更不进队列：那种条目点下去只能对着空清单发呆，而且还能被「合并」成功，台账记一次
            // 根本没发生的放行（11A 真机抓的）。与底座升级「一格可自动合的都没有就不建分支」同一口径 ——
            // 噪音的成本是审的人来付
            throw new BusinessException(sourceLabel + " " + sourceBranch + " 的内容主干 " + targetBranch
                    + " 已经全有了，没有要合进主干的内容：这次不用生成请求");
        }
        MergeCheck check = gateway.checkMerge(workspace, targetSha, sourceSha);
        int checkStatus = check.clean() ? MergeRequestState.CHECK_CLEAN : MergeRequestState.CHECK_CONFLICT;
        String title = sourceBranch + " → " + targetBranch + "：" + files.size() + " 个文件待合并";

        Map<String, Object> existing = requestMapper.selectOpenRequest(projectId, sourceBranch, targetBranch);
        long requestId = ledger.record(projectId, existing, title, sourceBranch, targetBranch,
                sourceSha, targetSha, baseSha, MergeRequestState.PENDING, checkStatus,
                truncate(check.summary()), files, check, SecurityUtils.getUsername());
        return detail(requestId);
    }

    /**
     * 列表：文件数与冲突数在 SQL 的两个子查询里现算，状态文案在这里现给。
     *
     * <p>文案只由 {@link MergeRequestState} 给：让前端拿数字自己拼词，两处就会开始各说各话，
     * 而那时改一个状态词要同时动前后端。
     */
    public PageResult<Map<String, Object>> list(PageQuery query, Long projectId, boolean onlyMine) {
        if (projectId != null) {
            requireProject(projectId);
        }
        // 「待我审核」按项目负责人过滤：需求原话是负责人要看到需要他审的 PR，
        // 而不是在一堆别人的请求里自己挑
        Long owner = onlyMine ? SecurityUtils.getUserId() : null;
        long total = requestMapper.countRequests(projectId, owner);
        List<Map<String, Object>> rows = total == 0
                ? List.of()
                : requestMapper.selectRequests(projectId, owner, query.normalizedPageSize(), query.offset());
        rows.forEach(row -> {
            row.put("statusText", MergeRequestState.text(number(row.get("status"))));
            row.put("checkStatusText", MergeRequestState.checkText(number(row.get("checkStatus"))));
        });
        return PageResult.of(rows, total, query.normalizedPageNum(), query.normalizedPageSize());
    }

    /**
     * 确认合并：把人审过的那一对提交合进人工主干并推上去。平台唯一会写主干的动作。
     *
     * <p>三道拒绝都在动手之前，顺序是有讲究的：先判请求本身还作不作数（状态、预检结论），
     * 再判点的人有没有资格（负责人或平台超管），最后才去问仓库 —— 反过来做的话，
     * 一个越权的点击会先把远端读一遍，等于让没资格的人触发网络动作。
     *
     * <p>合并前还要回读一次远端：预检是几分钟前算的，这期间主干或发布分支只要动过，
     * 人点的这条请求算的就不是眼前这份内容了。{@code mergeIntoTarget} 内部还会比一次主干提交号，
     * 那是第二道保险，不是重复劳动。
     */
    public Map<String, Object> merge(Long id) {
        Map<String, Object> request = requestMapper.selectRequest(id);
        if (request == null) {
            throw new BusinessException("合并请求不存在或已被删除");
        }
        int status = number(request.get("status"));
        if (status != MergeRequestState.PENDING) {
            throw new BusinessException("这条请求是「" + MergeRequestState.text(status) + "」，不能合并："
                    + "只有待审的请求可以合入主干");
        }
        int checkStatus = number(request.get("checkStatus"));
        if (checkStatus != MergeRequestState.CHECK_CLEAN) {
            throw new BusinessException("预检结论是「" + MergeRequestState.checkText(checkStatus)
                    + "」，不能合入主干" + (checkStatus == MergeRequestState.CHECK_CONFLICT
                    ? "：冲突文件必须先由人解掉（平台不替人取舍），解完再重算一次"
                    : "：先重算一次拿到结论"));
        }

        long projectId = numberAsLong(request.get("projectId"));
        Map<String, Object> project = requireProject(projectId);
        String operator = SecurityUtils.getUsername();
        requireMergeAuthority(project, operator);

        String targetBranch = text(request.get("targetBranch"));
        String sourceBranch = text(request.get("sourceBranch"));
        String targetSha = text(request.get("targetSha"));
        String sourceSha = text(request.get("sourceSha"));
        Path workspace = requireWorkspace(project);
        String repoUrl = RepoUrlValidator.normalize(text(project.get("repoUrl")));
        boolean fromUpgrade = sourceBranch.startsWith(BaseUpgrade.UPGRADE_BRANCH_PREFIX);
        String remoteSource = remoteSha(gateway.lsRemote(repoUrl), sourceBranch);
        if (remoteSource == null) {
            // 来源不同，下一步能做的事也不同：发布分支没了要重建工作区重发，升级分支没了要重新发起升级
            throw new BusinessException("远端已经没有" + (fromUpgrade ? "升级分支 " : "发布分支 ") + sourceBranch
                    + "：" + (fromUpgrade ? "这条升级分支被删过或已经合过了，请重新发起一次底座升级"
                    : "这条请求没有可合的内容了，请重建工作区后重新初始化"));
        }
        if (!remoteSource.equals(sourceSha)) {
            throw new BusinessException((fromUpgrade ? "升级分支 " : "发布分支 ") + sourceBranch
                    + " 在预检之后又前进了（算的是 "
                    + abbreviate(sourceSha) + "，远端现在是 " + abbreviate(remoteSource) + "）："
                    + "先重算一次，让人看的是最新的这一份");
        }

        String merged = gateway.mergeIntoTarget(workspace, repoUrl, targetBranch, targetSha, sourceSha,
                "合并请求 #" + id + "：" + text(request.get("title")));
        // WHERE status = 0：两个人同时点合并时，第二个人要发现这条已经收口，而不是把 merged_by 改写成自己
        if (requestMapper.updateMerged(id, "已合入 " + targetBranch + "（" + abbreviate(merged) + "）", operator) == 0) {
            throw new BusinessException("这条请求刚刚已被别人收口，本次合并结果请到人主干上核对："
                    + "平台没有把它记成第二次合并");
        }
        if (fromUpgrade) {
            stampBaseAfterMerge(project, workspace, repoUrl, targetBranch, sourceBranch, id, merged, operator);
        }
        return detail(id);
    }

    /**
     * 升级分支合进主干之后，把 base 侧搬到这一版：打新的底座标记 tag、推进
     * {@code lc_project.base_scaffold_sha}、把那次升级收成「已合入主干」。
     *
     * <p>不搬的话下一次升级会把已经下发过的内容再下发一遍 —— 三方合并的 base 侧照 tag 名取，
     * 而 tag 名由 {@code base_scaffold_sha} 拼出来，那个号不动，base 就永远停在上一轮。
     *
     * <p>tag 打不上去（远端开了 tag 保护）<b>不回滚合并</b>：合并是真成了，sha 也照实推进，
     * 只把告警追加到那条升级记录里。后果是下一次升级会被「找不到底座标记 tag」拦住并说明原因 ——
     * 拦住重算比拿错的 base 硬算安全。人工「补打底座标记 tag」这个动作还没做，记在待办里。
     */
    private void stampBaseAfterMerge(Map<String, Object> project, Path workspace, String repoUrl,
                                     String targetBranch, String sourceBranch, long requestId,
                                     String mergedSha, String operator) {
        Map<String, Object> upgrade = upgradeMapper.selectByBranch(sourceBranch);
        if (upgrade == null) {
            // 分支名是 platform-upgrade/* 却查不到升级记录：库与仓库漂开了。不硬猜底座号，
            // 也不推进 sha —— 猜错一次，之后每一轮三方合并都建立在错的 base 上
            requestMapper.noteResult(requestId, truncate("已合入主干，但查不到对应的底座升级记录（分支 " + sourceBranch
                    + "）：base 侧没搬，下一次升级前请先核对 lc_base_upgrade"), operator);
            return;
        }
        String targetScaffold = text(upgrade.get("targetScaffoldSha"));
        String tagName = PlatformBaseTag.name(targetScaffold);
        String warning = null;
        try {
            gateway.stampBaseTag(workspace, repoUrl, targetBranch, tagName, null,
                    PlatformBaseTag.message(targetScaffold) + "\n\n由合并请求 #" + requestId + " 在合入主干后打上。");
        } catch (RuntimeException e) {
            warning = "；底座标记 tag " + tagName + " 没打上：" + e.getMessage()
                    + "（下一次升级会被「找不到底座标记 tag」拦住，需要人工补打）";
        }
        projectMapper.updateBaseScaffold(numberAsLong(project.get("id")), targetScaffold, operator);
        upgradeMapper.markStatus(numberAsLong(upgrade.get("id")), 1,
                truncate("已合入 " + targetBranch + "（" + abbreviate(mergedSha) + "），base 侧搬到 "
                        + abbreviate(targetScaffold) + (warning == null ? "" : warning)));
    }

    /**
     * 单个文件的行内差异（GitHub 式展开）。两个提交号取自这条请求本身 ——
     * 看的是「当时算出来的那一对提交之间的差异」，不是分支现在的样子。
     */
    public Map<String, Object> fileDiff(Long id, String path) {
        Map<String, Object> request = requestMapper.selectRequest(id);
        if (request == null) {
            throw new BusinessException("合并请求不存在或已被删除");
        }
        if (path == null || path.isBlank()) {
            throw new BusinessException("要看差异必须给出文件路径");
        }
        Map<String, Object> project = requireProject(numberAsLong(request.get("projectId")));
        String diff = gateway.fileDiff(requireWorkspace(project), text(request.get("mergeBaseSha")),
                text(request.get("sourceSha")), path);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("path", path);
        view.put("diff", diff);
        return view;
    }

    /** 确认合并的资格：项目负责人，或平台超级管理员。看清单不受这一条限制。 */
    private void requireMergeAuthority(Map<String, Object> project, String operator) {
        Long ownerUserId = project.get("ownerUserId") == null ? null : numberAsLong(project.get("ownerUserId"));
        Long userId = SecurityUtils.getUserId();
        if (ownerUserId != null && ownerUserId.equals(userId)) {
            return;
        }
        if (projectMapper.countPlatformSuperAdmin(userId == null ? 0L : userId, TenantContext.PLATFORM_TENANT_ID) > 0) {
            return;
        }
        throw new BusinessException("只有项目负责人" + (ownerUserId == null ? "（该项目还没设负责人）"
                : "（用户 ID " + ownerUserId + "）") + "或平台超级管理员能确认合并「"
                + project.get("projectCode") + "」：当前账号 " + operator + " 不是");
    }

    /**
     * 详情：请求行 + 变更文件清单（冲突的排在前面）。
     *
     * <p>打开详情时重读一次远端 —— 目标分支在人上一次看和这一次看之间前进过，这条请求就不再作数。
     * 列表不这么做：一屏几十条请求各打一次 {@code ls-remote}，把「看一眼列表」变成一次网络风暴。
     */
    public Map<String, Object> detail(Long id) {
        Map<String, Object> request = requestMapper.selectRequest(id);
        if (request == null) {
            throw new BusinessException("合并请求不存在或已被删除");
        }
        if (number(request.get("status")) == MergeRequestState.PENDING) {
            detectStale(request);
        }
        Map<String, Object> counts = fileMapper.selectCounts(id);
        Map<String, Object> view = new LinkedHashMap<>(request);
        view.put("statusText", MergeRequestState.text(number(request.get("status"))));
        view.put("checkStatusText", MergeRequestState.checkText(number(request.get("checkStatus"))));
        view.put("fileCount", counts == null ? 0 : number(counts.get("total")));
        view.put("conflictCount", counts == null ? 0 : number(counts.get("conflicts")));
        view.put("files", fileMapper.selectByRequest(id));
        return view;
    }

    /* ── 现场 ── */

    private void detectStale(Map<String, Object> request) {
        Map<String, Object> project = projectMapper.selectProjectForSync(numberAsLong(request.get("projectId")));
        if (project == null) {
            return;
        }
        String targetBranch = text(request.get("targetBranch"));
        String remoteTarget;
        try {
            remoteTarget = remoteSha(gateway.lsRemote(RepoUrlValidator.normalize(text(project.get("repoUrl")))), targetBranch);
        } catch (RuntimeException e) {
            // 远端读不到不代表这条请求陈旧，只代表现在没法判断：保持原状态，
            // 把一次网络抖动变成陈旧会让人在列表上看到一条假结论
            return;
        }
        if (remoteTarget == null || remoteTarget.equals(text(request.get("targetSha")))) {
            return;
        }
        String message = "主干 " + targetBranch + " 已前进到 " + abbreviate(remoteTarget)
                + "，这条请求算的是 " + abbreviate(text(request.get("targetSha"))) + "：必须重算才能合";
        ledger.markStale(numberAsLong(request.get("id")), message, SecurityUtils.getUsername());
        request.put("status", MergeRequestState.STALE);
        request.put("checkMessage", message);
    }

    /**
     * 来源分支只放开两种：项目的发布分支，和底座升级推出去的那种分支。
     *
     * <p>这不是校验洁癖：随便指一个分支，就等于把「平台只交付两条链路」这条边界交给调用方 ——
     * 有人指到自己的临时分支上，就能借合并请求把那份内容推到人工主干，而合并请求的授权看的
     * 只是「他是不是这个项目的负责人」，不会替他判断这条分支是谁推的。
     */
    private static String requireAllowedSource(String requested, String genBranch) {
        if (requested == null || requested.isBlank() || requested.equals(genBranch)) {
            return genBranch;
        }
        if (requested.startsWith(BaseUpgrade.UPGRADE_BRANCH_PREFIX)) {
            return requested;
        }
        throw new BusinessException("来源分支只能是项目的发布分支或 " + BaseUpgrade.UPGRADE_BRANCH_PREFIX
                + "* 升级分支，收到的是 " + requested + "：平台只交付这两条链路，"
                + "别的分支不该借合并请求进人工主干");
    }

    private Map<String, Object> requireProject(Long projectId) {
        Map<String, Object> project = projectMapper.selectProjectForSync(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        if (number(project.get("status")) != 1) {
            throw new BusinessException("项目「" + project.get("projectCode") + "」已停用，先启用再合并");
        }
        int syncStatus = number(project.get("syncStatus"), ProjectSyncStatus.UNINITIALIZED);
        if (syncStatus != ProjectSyncStatus.READY) {
            throw new BusinessException("项目仓库当前是「" + ProjectSyncStatus.text(syncStatus)
                    + "」，还没有可合并的发布分支：请先初始化项目仓库");
        }
        return project;
    }

    private Path requireWorkspace(Map<String, Object> project) {
        String projectCode = text(project.get("projectCode"));
        Path workspace = paths.of(projectCode);
        if (!Files.isDirectory(workspace.resolve(".git"))) {
            throw new BusinessException("工作区 " + paths.relative(projectCode)
                    + " 不存在或不是一个 git 仓库：合并请求要读仓库内容，请先初始化项目仓库");
        }
        return workspace;
    }

    private static String remoteSha(List<RemoteRef> refs, String branch) {
        String wanted = "refs/heads/" + branch;
        return refs.stream().filter(ref -> wanted.equals(ref.name())).map(RemoteRef::sha).findFirst().orElse(null);
    }

    private static String truncate(String message) {
        if (message == null || message.length() <= MESSAGE_LIMIT) {
            return message;
        }
        return message.substring(0, MESSAGE_LIMIT - 3) + "...";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int number(Object value) {
        return number(value, 0);
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number counted ? counted.intValue() : fallback;
    }

    private static long numberAsLong(Object value) {
        return value instanceof Number counted ? counted.longValue() : 0L;
    }

    private static String abbreviate(String sha) {
        return sha == null || sha.length() < 8 ? sha : sha.substring(0, 8);
    }
}
