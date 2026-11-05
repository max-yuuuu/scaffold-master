package com.scaffold.lowcode.merge.service;

import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.framework.security.SecurityUtils;
import com.scaffold.lowcode.merge.domain.MergeRequestState;
import com.scaffold.lowcode.merge.mapper.LcMergeRequestFileMapper;
import com.scaffold.lowcode.merge.mapper.LcMergeRequestMapper;
import com.scaffold.lowcode.project.domain.ProjectSyncStatus;
import com.scaffold.lowcode.project.mapper.LcProjectMapper;
import com.scaffold.lowcode.repo.RepoUrlValidator;
import com.scaffold.lowcode.workspace.ChangedFile;
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

    public MergeRequestService(LcProjectMapper projectMapper, LcMergeRequestMapper requestMapper,
                               LcMergeRequestFileMapper fileMapper, MergeRequestLedger ledger,
                               GitGateway gateway, WorkspacePaths paths) {
        this.projectMapper = projectMapper;
        this.requestMapper = requestMapper;
        this.fileMapper = fileMapper;
        this.ledger = ledger;
        this.gateway = gateway;
        this.paths = paths;
    }

    /**
     * 重算一次：fetch → 读两侧远端提交 → 分叉点 → 变更清单 → 试算 → 落账。
     *
     * @return 重算后的请求视图（含文件数与冲突数）
     */
    public Map<String, Object> refresh(Long projectId) {
        Map<String, Object> project = requireProject(projectId);
        Path workspace = requireWorkspace(project);
        String repoUrl = RepoUrlValidator.normalize(text(project.get("repoUrl")));
        String sourceBranch = text(project.get("genBranch"));
        String targetBranch = text(project.get("mainBranch"));

        if (gateway.isDirty(workspace)) {
            throw new BusinessException("工作区 " + paths.relative(text(project.get("projectCode")))
                    + " 里有未提交的改动，合并预检的结论不可信：请先在项目仓库里处理掉这些改动（提交或搬走）再重算");
        }
        gateway.fetch(workspace);

        List<RemoteRef> refs = gateway.lsRemote(repoUrl);
        String sourceSha = remoteSha(refs, sourceBranch);
        String targetSha = remoteSha(refs, targetBranch);
        if (sourceSha == null) {
            throw new BusinessException("远端没有发布分支 " + sourceBranch + "：还没有页面发布到这条分支上，"
                    + "也就没有要合进主干的东西");
        }
        if (targetSha == null) {
            throw new BusinessException("远端没有主干 " + targetBranch + "：合并的目标不存在。"
                    + "平台不替你建主干（那等于替你决定仓库的起点），请先在仓库侧建出来");
        }

        String baseSha = gateway.mergeBase(workspace, targetSha, sourceSha);
        if (baseSha == null) {
            throw new BusinessException("发布分支 " + sourceBranch + " 与主干 " + targetBranch
                    + " 没有共同祖先（两条历史是各自初始化的）：先让发布分支从主干重新起，再来算清单");
        }

        List<ChangedFile> files = gateway.diffFiles(workspace, baseSha, sourceSha);
        MergeCheck check = gateway.checkMerge(workspace, targetSha, sourceSha);
        int checkStatus = check.clean() ? MergeRequestState.CHECK_CLEAN : MergeRequestState.CHECK_CONFLICT;
        String title = files.isEmpty()
                ? sourceBranch + " → " + targetBranch + "：主干已包含发布分支的全部内容"
                : sourceBranch + " → " + targetBranch + "：" + files.size() + " 个文件待合并";

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
    public PageResult<Map<String, Object>> list(PageQuery query, Long projectId) {
        if (projectId != null) {
            requireProject(projectId);
        }
        long total = requestMapper.countRequests(projectId);
        List<Map<String, Object>> rows = total == 0
                ? List.of()
                : requestMapper.selectRequests(projectId, query.normalizedPageSize(), query.offset());
        rows.forEach(row -> {
            row.put("statusText", MergeRequestState.text(number(row.get("status"))));
            row.put("checkStatusText", MergeRequestState.checkText(number(row.get("checkStatus"))));
        });
        return PageResult.of(rows, total, query.normalizedPageNum(), query.normalizedPageSize());
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
