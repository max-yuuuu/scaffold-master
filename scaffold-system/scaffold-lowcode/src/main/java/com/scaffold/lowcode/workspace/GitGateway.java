package com.scaffold.lowcode.workspace;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 仓库读写原语。全部走 JGit（纯 Java，不拼命令行），把 {@code repo_url}、分支名这些用户输入
 * 挡在 shell 之外（方案 §九 注入风险一条）。
 *
 * <p>本接口只做 Git 动作，不含平台判断：什么时候该拒绝、状态怎么推进，在
 * {@link ProjectRepositoryService}。拆开的目的是让「JGit 行为」和「平台策略」能各自被测。
 */
public interface GitGateway {

    /**
     * 读远端引用，等价于 {@code git ls-remote}。仓库不存在、网络不通、主机指纹未登记都在这里抛出。
     *
     * @return 远端引用列表；空仓库返回空列表
     */
    List<RemoteRef> lsRemote(String repoUrl);

    /** 空仓库首次初始化：在本地建库、把 {@code initialBranch} 设为未出生的 HEAD，并登记 origin */
    void initEmpty(Path dir, String repoUrl, String initialBranch);

    /** 克隆远端的一条分支到工作区目录 */
    void cloneBranch(Path dir, String repoUrl, String branch);

    /**
     * 切到工作分支：本地已有就切过去，没有就从 {@code startPoint}（如 {@code origin/main}）新建；
     * {@code startPoint} 为空时以当前 HEAD 为起点（未出生的 HEAD 也算，第一次提交即创建分支）。
     */
    void checkoutBranch(Path dir, String branch, String startPoint);

    /**
     * 暂存工作区全部改动并提交。
     *
     * @return 提交号；工作区没有改动时返回 {@code null}（不产生空提交）
     */
    String commitAll(Path dir, String message);

    /**
     * 推送指定分支，refspec 显式写 {@code refs/heads/{branch}}，不带 {@code +}：
     * 远端拒绝非快进时如实报错，平台永不 force-push（方案 §5.3 安全边界）。
     */
    void pushBranch(Path dir, String branch);

    /** 工作区是否有未提交改动（含未跟踪文件） */
    boolean isDirty(Path dir);

    /** 本地分支当前提交号；分支不存在返回 {@code null} */
    String localBranchSha(Path dir, String branch);

    /** 分支名是否符合 Git 的 refname 规则：{@code refs/heads/{branch}} 能不能建 */
    boolean isValidBranch(String branch);

    /** 读取一个 Git 仓库的 HEAD 提交号，用于记录 {@code base_scaffold_sha}；不是仓库时返回 {@code null} */
    String headSha(Path repoDir);

    /**
     * 给某个提交打附注 tag。同名 tag 已存在时原样保留、不移动（平台永不改写已交付的 base）。
     *
     * @return 本次真的创建了 tag 返回 {@code true}；同名 tag 已存在返回 {@code false}
     */
    boolean tagCommit(Path dir, String tagName, String sha, String message);

    /**
     * 推送 tag，refspec 不带 {@code +}：远端已有同名 tag 时如实报错，不强推。
     *
     * <p>失败抛 {@link com.scaffold.common.exception.BusinessException} 而不是
     * {@link WorkspaceGitException}：tag 推不上去不代表项目仓库与平台分歧，
     * 调用方只该把它当告警，不该拿它改 {@code sync_status}。
     */
    void pushTag(Path dir, String tagName);

    /**
     * 带回远端各分支的读数到 {@code refs/remotes/origin/*}，并清掉远端已删除的那些。
     *
     * <p>工作区是按发布分支 clone 的，主干上有什么它本来不知道；审查与预检都要读主干，所以先 fetch。
     * 只动远端跟踪引用：本地分支、HEAD 与工作区文件一律不碰（这条由 JGitGatewayTest 钉住）。
     */
    void fetch(Path dir);

    /**
     * 两条历史的分叉点，等价于 {@code git merge-base}。
     *
     * <p>合并请求的清单要按 {@code merge-base..源} 算而不是 {@code 目标..源}：后者会把主干上
     * 别人新提交的内容反向显示成「发布分支要删掉的东西」。
     *
     * @param oneRev 分支名或提交号
     * @param otherRev 分支名或提交号
     * @return 分叉点提交号；两条历史没有共同祖先（各自初始化的仓库）或任一侧解析不出来时返回 {@code null}
     */
    String mergeBase(Path dir, String oneRev, String otherRev);

    /** {@code baseSha..headSha} 之间的变更文件清单（含改名识别与行级增删数） */
    List<ChangedFile> diffFiles(Path dir, String baseSha, String headSha);

    /**
     * 试算一次合并的<b>结论</b>：不落提交、不写索引、不动工作区文件。
     *
     * <p>与真实合并同形（同一套递归三路算法），所以「预检说可自动合」与「真合会不会炸」之间
     * 只剩仓库内容变化这一个变量。冲突时只报哪些文件要人解，不替人取舍。
     *
     * @param oursRev 目标分支（人工主干）的分支名或提交号；调用方的工作区应停在目标分支这一侧
     * @param theirsRev 源分支（平台发布分支）的分支名或提交号
     */
    MergeCheck checkMerge(Path dir, String oursRev, String theirsRev);

    /**
     * 单个文件在 {@code baseSha..headSha} 之间的 unified diff，供审查页展开行内对照。
     *
     * <p>按文件现算而不是整份拉回来：一次大重构的全量 diff 能到几百 KB，抽屉首屏不该等它；
     * 而两个提交号已经把「算的是哪一对」钉死，按需算不会有和清单对不上的风险。
     *
     * @return 该文件的补丁文本；这个文件不在这段改动里时返回空串（不是 null，前端不用判两种空）
     */
    String fileDiff(Path dir, String baseSha, String headSha, String path);

    /**
     * 把源分支的提交真合并进目标分支并推上去 —— 平台唯一会写人工主干的动作。
     *
     * <p><b>全程在一份临时 clone 里做，平台工作区一条引用都不动</b>：工作区的 HEAD 属于发布分支，
     * 把它切到主干上合并，下一次发布就可能把生成物提交到主干上去。clone 也从远端重新取，
     * 于是合并的起点是主干<b>当前</b>的样子，不是本地那份缓存。
     *
     * <p>三道拒绝：{@code expectedTargetSha} 与远端现在不一致（预检之后主干又前进了，这条请求算的是旧的一对提交）；
     * 真冲突（只报文件清单，不替人取舍）；推送非快进被远端拒（refspec 不带 {@code +}，平台永不强推）。
     * 用 {@code NO_FF} 固定产生合并提交，让「这次审核放行了什么」在历史里是一个可指的两个父亲的提交。
     *
     * @param workspace 项目工作区，只用来取凭据与远端地址，不会被改动
     * @param repoUrl 仓库地址
     * @param targetBranch 目标分支（人工主干）
     * @param expectedTargetSha 预检时看到的主干提交号，不一致就拒
     * @param sourceSha 要合进来的提交号（发布分支当前值）
     * @param message 合并提交说明
     * @return 落到远端主干的那个合并提交号
     */
    String mergeIntoTarget(Path workspace, String repoUrl, String targetBranch, String expectedTargetSha,
                           String sourceSha, String message);

    /**
     * 取回某个提交里指定路径的文件内容 —— 三级回滚的取数就靠它（方案 §5.4「取该版本对应文件的内容」）。
     *
     * <p><b>只读</b>：直接从对象库读 blob，不 checkout、不写索引、不动 HEAD，所以工作区脏着也能安全调用。
     *
     * <p>那个提交里没有的路径<b>不出现在结果里</b>，而不是给一个空串：空内容是合法内容，调用方把它写回去
     * 就是把一个文件清空；缺了哪些由「请求的路径 − 结果的键」算出来，让服务去说那句人话。
     *
     * @param dir 项目工作区，只用来打开仓库
     * @param commitRev 提交号（版本历史里记的那个 {@code commit_sha}）
     * @param paths 相对仓库根的路径，用 {@code /} 分隔
     * @return 路径 → 文本内容，只含取到的那些；二进制与查不到的提交当场拒绝，不返回乱码
     */
    Map<String, String> fileContents(Path dir, String commitRev, List<String> paths);
}
