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
     * <p>四道拒绝：{@code expectedTargetSha} 与远端现在不一致（预检之后主干又前进了，这条请求算的是旧的一对提交）；
     * 真冲突（只报文件清单，不替人取舍）；<b>源已是目标的祖先</b>（主干早就含着这些内容，这不是「合完了」而是
     * 「没什么可合」—— 放过就会在台账上记一次根本没发生的放行，升级分支走这里还会凭空搬走 base 号）；
     * 推送非快进被远端拒（refspec 不带 {@code +}，平台永不强推）。
     * 用 {@code NO_FF} 固定产生合并提交，让「这次审核放行了什么」在历史里是一个可指的两个父亲的提交。
     *
     * @param workspace 项目工作区，只用来取凭据与远端地址，不会被改动
     * @param repoUrl 仓库地址
     * @param targetBranch 目标分支（人工主干）
     * @param expectedTargetSha 预检时看到的主干提交号，不一致就拒
     * @param sourceSha 要合进来的提交号（发布分支当前值）
     * @param message 合并提交说明
     * @return 新落到远端主干的那个合并提交号（一定不等于 {@code expectedTargetSha}：没动过就不会返回成功）
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

    /**
     * 逐文件三方合并（底座升级用，方案 §5.2）。
     *
     * <p>三个来源分别是：{@code baseSha} = 当初交付那份 A 档快照的提交（项目仓库里的
     * {@code yuanda/platform-base/*} tag 指向它）、{@code oursSha} = 项目主干当前提交、
     * {@code theirsSha} = 按当前底座 checkout 现生成的 A 档快照提交。
     *
     * <p><b>只读</b>：三棵树都从对象库取，合并本身在内存里做（JGit {@code MergeAlgorithm}），
     * 不 checkout、不写索引、不动 HEAD —— 与 {@link #fileContents} 同一个理由：一次「看看差多少」
     * 不该改动任何现场。写不写由调用方按 {@link MergedFile#writable()} 决定，
     * 而只有 {@link MergeOutcome#CLEAN} 可写：平台不静默删项目里有的东西，
     * 也不把带冲突标记、没编译过的内容推使用者的仓库。
     *
     * @param dir 项目工作区，只用来打开仓库
     * @param baseSha 交付时那份 A 档快照的提交号
     * @param oursSha 项目主干当前提交号
     * @param theirsSha 新的 A 档快照提交号
     * @param paths 要合并的路径（调用方给两侧路径的并集，不在任何一侧的按未变更处理）
     */
    List<MergedFile> threeWayMerge(Path dir, String baseSha, String oursSha, String theirsSha, List<String> paths);

    /**
     * 把一份「路径 → 内容」的清单在这份仓库的对象库里做成一个提交并返回它的提交号 ——
     * 底座升级的 theirs 侧就是这么来的（方案 §5.2：theirs = 按当前底座 checkout 现生成的 A 档快照）。
     *
     * <p>三方合并的算法按提交号取树，所以快照必须成为一个提交；但它<b>不挂在任何分支上、也没有父亲</b>：
     * 升级分支要从项目主干长出来，不能从这份快照长出来 —— 那样项目历史里会凭空多出一段没人认领的提交。
     *
     * <p>空清单直接拒绝：一旦做成提交，合并会把项目里每一个文件都报成「平台不再交付」，
     * 那不是一份升级，那是一份删除清单。底座 checkout 读不到、裁剪清单为空都属于这一类现场错误。
     *
     * @param dir 仓库目录（升级流程里是一份临时 clone，平台工作区不被这一步改动）
     * @param files 相对仓库根的路径 → 文件内容
     * @param message 提交说明：会出现在人拿 {@code git cat-file} 查这个对象的时候
     * @return 那个提交号
     */
    String snapshotCommit(Path dir, Map<String, byte[]> files, String message);

    /**
     * 按分支给出提交号序列，<b>从新到旧</b>，最多 {@code limit} 条（{@code limit <= 0} 表示不限）。
     *
     * <p>给「人工补打底座标记 tag」用：要在历史里认出当初交付那一版 A 档的提交，判据是那一笔上的
     * {@code .scaffold-project.json} 记着项目现在这个底座号。从新到旧才能一命中就停，
     * 而 {@code limit} 是护栏 —— 跑了几年的仓库有几万笔，逐笔读 blob 不能没有上界。
     *
     * @param dir 仓库目录（平台工作区或一份临时 clone）
     * @param branch 分支名（不带 refs/heads/）
     * @param limit 最多取多少笔；撞上限时调用方要说「请指定提交号」而不是给一个错的答案
     * @return 提交号列表；分支不存在返回空列表，不抛
     */
    List<String> revList(Path dir, String branch, int limit);

    /**
     * 在目标分支上打底座标记 tag 并推到远端。
     *
     * <p>为什么不能拿平台工作区去打：合并是在一份临时 clone 里做的，那笔合并提交只活在临时 clone
     * 与远端，工作区连这个对象都没有，本地建出来的 tag 指向一个不存在的提交，推都推不上去。
     * 所以这里重新 clone 目标分支（它现在包含那笔合并），在正确的对象图上打 tag。
     *
     * <p>只创建不移动：同名 tag 已存在时返回 {@code false} 并且不动它 —— 移动它等于改掉某一轮升级的
     * base 侧，三方合并会算出错的结果。
     *
     * @param commitSha 要打 tag 的那笔；{@code null} 表示打在该分支当前提交上（升级合入主干后就是那笔合并）
     * @return tag 是这次新打的（{@code true}）还是本来就在（{@code false}，原样留着）
     */
    boolean stampBaseTag(Path workspace, String repoUrl, String targetBranch, String tagName,
                         String commitSha, String message);

    /**
     * 完整一趟底座升级的 git 编排（方案 §5.2）：临时 clone 里算三方合并，把可自动合的文件写进
     * 从主干切出的升级分支并推上去。
     *
     * <p><b>平台工作区一条引用都不动</b>，与 {@link #mergeIntoTarget} 同一口径：工作区的 HEAD 属于发布分支，
     * 拿它去长升级分支会把两条链路的现场搅在一起。
     *
     * <p>三道拒绝都在动手之前：远端没有主干（升级分支无处可切）、远端已经有同名升级分支（同名再推只能靠
     * force，而平台永不强推 —— 那一条可能是别人正在审的）、底座标记 tag 取不到（三方合并缺 base 侧，
     * 算出来的「谁改了什么」全是猜的）。
     *
     * <p>可自动合的文件一个都没有时<b>不建分支也不推</b>：空升级分支会在合并请求里以「零变更」出现，
     * 浪费的是审的人的注意力。
     *
     * @param workspace 项目工作区，只用来取远端与凭据，不会被改动
     * @param repoUrl 仓库地址
     * @param mainBranch 项目主干分支名（升级分支的起点，也是唯一能被合进去的目标）
     * @param baseTag 交付时打的底座标记 tag 名（{@code yuanda/platform-base/{sha}}），三方合并的 base 侧
     * @param snapshotFiles 当前底座该交付的 A 档快照：路径 → 内容
     * @param upgradeBranch 升级分支名（不带 refs/heads/）
     * @param message 升级提交说明
     */
    BaseUpgrade baseUpgrade(Path workspace, String repoUrl, String mainBranch, String baseTag,
                            Map<String, byte[]> snapshotFiles, String upgradeBranch, String message);

    /**
     * 底座 checkout 从 {@code fromSha} 到 {@code toSha} 走了几个提交 —— 项目列表那个「落后 N 个底座提交」
     * 的读数就靠它（方案 §10.4：平台镜像与底座 checkout 必须成对更新，读数就是这条不变式有没有被打破的表针）。
     *
     * <p><b>数不出来返回 {@code null}，不是 0。</b>「0」在界面上读起来是「已是最新」，那是把「我不知道」
     * 报成「没问题」，比不报更坏：底座 checkout 不是 git 仓库、那一笔不在历史里（项目是在记不到 sha
     * 的版本上初始化的）、或者只是读仓库时出了 IO 错误，这三种都要走退化分支去给文件级差异清单。
     */
    Integer commitCountBetween(Path repoDir, String fromSha, String toSha);
}
