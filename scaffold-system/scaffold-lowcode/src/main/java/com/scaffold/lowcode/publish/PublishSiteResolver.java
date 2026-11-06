package com.scaffold.lowcode.publish;

import com.scaffold.common.exception.BusinessException;
import com.scaffold.lowcode.project.domain.ProjectSyncStatus;
import com.scaffold.lowcode.repo.RepoUrlValidator;
import com.scaffold.lowcode.workspace.GitGateway;
import com.scaffold.lowcode.workspace.RemoteRef;
import com.scaffold.lowcode.workspace.WorkspacePaths;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 一次「要往发布分支写」的动作开工前的现场守卫。
 *
 * <p>发布与回滚共用这一份，不是各写一份的两个副本：两者都是「整目录暂存 + 提交 + 推送」，
 * 守卫漏一条的后果也一样（把人的临时改动挂到这次动作上、或者盖掉别人推过的东西）。
 * 抄两份的话，改一条守卫要记得改两处 —— 这种约定迟早漂。
 *
 * <p>这几条拦的都是「编得过、单测绿、真机才炸」那一类，逐条理由写在方法注释里。
 */
@Component
public class PublishSiteResolver {

    /** 现场读数：工作区、远端地址、发布分支，以及本地与远端各自停在哪个提交 */
    public record Site(Path workspace, String repoUrl, String branch, String localSha, String remoteSha,
                       List<RemoteRef> refs) {
    }

    private final GitGateway gateway;
    private final WorkspacePaths paths;

    public PublishSiteResolver(GitGateway gateway, WorkspacePaths paths) {
        this.gateway = gateway;
        this.paths = paths;
    }

    /**
     * @param project 项目读数（要 {@code syncStatus} / {@code projectCode} / {@code genBranch} /
     *                {@code repoUrl} / {@code lastCommitSha}）
     * @param action  这次动作的动词（「发布」/「回滚」），提示语末尾那句「再回来__」要跟着变
     * @param object  这次动作的对象（「页面 sales_order_list」），没地方落时要说清是谁没地方落
     */
    public Site open(Map<String, Object> project, String action, String object) {
        int syncStatus = number(project.get("syncStatus"), ProjectSyncStatus.UNINITIALIZED);
        if (syncStatus != ProjectSyncStatus.READY) {
            throw new BusinessException("项目仓库当前是「" + ProjectSyncStatus.text(syncStatus)
                    + "」，先把固定代码初始化到发布分支再" + action);
        }
        String projectCode = text(project.get("projectCode"));
        Path workspace = paths.of(projectCode);
        if (!Files.isDirectory(workspace.resolve(".git"))) {
            throw new BusinessException("工作区 " + paths.relative(projectCode) + " 不存在或不是一个 git 仓库："
                    + "页面是发布到项目仓库里的，请先初始化项目仓库");
        }
        // 提交是整目录暂存：工作区里有人写了没提交的东西，那些东西会被这次动作一起推进发布分支
        if (gateway.isDirty(workspace)) {
            throw new BusinessException("工作区里有未提交的改动，平台不会替您丢弃，也不会把它们一起提交进发布分支："
                    + "请先在项目仓库里处理掉这些改动（提交或搬走），再回来" + action);
        }
        String branch = text(project.get("genBranch"));
        String repoUrl = RepoUrlValidator.normalize(text(project.get("repoUrl")));
        List<RemoteRef> refs = gateway.lsRemote(repoUrl);
        String localSha = gateway.localBranchSha(workspace, branch);
        String remoteSha = remoteSha(refs, branch);
        if (localSha == null) {
            throw new BusinessException("工作区的 " + branch + " 分支不存在（" + object + " 无处落）："
                    + "请重建工作区后重新初始化");
        }
        if (remoteSha == null) {
            throw new BusinessException("远端已经没有发布分支 " + branch + "（可能被删过）：请重建工作区后重新初始化");
        }
        // 本地与远端不一致时先问清是谁前进的：远端仍停在平台上次推上去的那个提交，就是上次推送没成功、
        // 这次补推；否则是别人往发布分支推过东西 —— 平台不强推也不自动合并，让人先重建工作区（§5.3）
        if (!localSha.equals(remoteSha) && !remoteSha.equals(text(project.get("lastCommitSha")))) {
            throw new BusinessException("发布分支 " + branch + " 的远端已前进（本地 " + abbreviate(localSha)
                    + "，远端 " + abbreviate(remoteSha) + "）：平台不强推也不自动合并，请先重建工作区，"
                    + "让这份工作区追上远端之后再" + action);
        }
        return new Site(workspace, repoUrl, branch, localSha, remoteSha, refs);
    }

    public static String remoteSha(List<RemoteRef> refs, String branch) {
        return refs.stream().filter(ref -> branch.equals(ref.branchName())).map(RemoteRef::sha).findFirst().orElse(null);
    }

    public static String remoteTagName(List<RemoteRef> refs, String tagName) {
        return refs.stream().filter(ref -> tagName.equals(ref.tagName())).map(RemoteRef::sha).findFirst().orElse(null);
    }

    public static String abbreviate(String sha) {
        return sha == null ? null : sha.substring(0, Math.min(10, sha.length()));
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
}
