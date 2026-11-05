package com.scaffold.lowcode.workspace;

import com.scaffold.common.exception.BusinessException;
import com.scaffold.lowcode.project.domain.ProjectSyncStatus;
import jakarta.annotation.PreDestroy;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.Merger;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.ProxyDataFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JGit 实现的仓库网关。
 *
 * <p>凭据一律走主机：SSH 用 {@code ~/.ssh} 里的密钥与 {@code known_hosts}，HTTPS 不存任何 token
 * （方案 §〇之二 第 4 条）。因此这里没有凭据参数，也带来两个部署前提：容器要挂进密钥目录，
 * 目标主机指纹要预先登记，否则第一次推送就被拒。
 */
@Component
public class JGitGateway implements GitGateway {

    private static final String REMOTE = "origin";
    private static final String HEADS_PREFIX = "refs/heads/";
    /** 远端跟踪引用的前缀：fetch 带回的读数落在这里，本地分支一条都不动 */
    private static final String REMOTE_HEADS_PREFIX = "refs/remotes/" + REMOTE + "/";
    private static final String TAGS_PREFIX = "refs/tags/";
    /** ls-remote 的解引用行后缀：{@code refs/tags/v1^{} } 那一行给的才是 tag 指的提交 */
    private static final String PEEL_SUFFIX = "^{}";

    private final int timeoutSeconds;
    private final PersonIdent committer;
    private final File sshHome;
    private volatile SshdSessionFactory sshSessionFactory;

    public JGitGateway(
            @Value("${scaffold.lowcode.git.timeout-seconds:120}") int timeoutSeconds,
            @Value("${scaffold.lowcode.git.commit-name:元搭平台}") String commitName,
            @Value("${scaffold.lowcode.git.commit-email:platform@yuanda.local}") String commitEmail,
            @Value("${scaffold.lowcode.git.ssh-home:}") String sshHome) {
        this.timeoutSeconds = timeoutSeconds;
        this.committer = new PersonIdent(commitName, commitEmail);
        this.sshHome = sshHome == null || sshHome.isBlank() ? null : new File(sshHome.trim());
    }

    @Override
    public List<RemoteRef> lsRemote(String repoUrl) {
        try {
            Iterable<Ref> refs = Git.lsRemoteRepository()
                    .setRemote(repoUrl)
                    .setHeads(true)
                    .setTags(true)
                    .setTimeout(timeoutSeconds)
                    .setTransportConfigCallback(transportConfig())
                    .call();
            // 附注 tag 在 ls-remote 里是两行：一行给 tag 对象自己的号，一行（名字带 ^{}）给它指的提交。
            // 这里把两行并成一条，调用方拿到的 commitSha 才是能取到内容的那个号，而多出来的那行不会
            // 被当成「远端还有一个引用」
            Map<String, String> peeled = new LinkedHashMap<>();
            List<String> names = new ArrayList<>();
            List<String> shas = new ArrayList<>();
            for (Ref ref : refs) {
                String name = ref.getName();
                ObjectId id = ref.getObjectId();
                String sha = id == null ? "" : id.getName();
                if (name.endsWith(PEEL_SUFFIX)) {
                    peeled.put(name.substring(0, name.length() - PEEL_SUFFIX.length()), sha);
                    continue;
                }
                ObjectId peeledId = ref.getPeeledObjectId();
                if (peeledId != null) {
                    peeled.put(name, peeledId.getName());
                }
                names.add(name);
                shas.add(sha);
            }
            List<RemoteRef> result = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i);
                result.add(new RemoteRef(name, shas.get(i), peeled.get(name)));
            }
            return List.copyOf(result);
        } catch (GitAPIException e) {
            throw new WorkspaceGitException(ProjectSyncStatus.UNREACHABLE, describe("读取远端仓库失败", repoUrl, e));
        }
    }

    @Override
    public void initEmpty(Path dir, String repoUrl, String initialBranch) {
        requireEmptyDir(dir);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BusinessException("创建工作区目录失败：" + dir + "（" + e.getMessage() + "）");
        }
        try (Git git = Git.init()
                .setDirectory(dir.toFile())
                // 只给分支名：setInitialBranch 自己补 refs/heads/，这里再补一次会建出
                // refs/heads/refs/heads/{branch} —— 宽松解析（resolve）看不出问题，
                // 而 findRef 是精确查的，checkoutBranch 那句「这条分支本地有了吗」就此答错
                .setInitialBranch(initialBranch)
                .call()) {
            git.remoteAdd().setName(REMOTE).setUri(new URIish(repoUrl)).call();
        } catch (GitAPIException | URISyntaxException e) {
            throw new BusinessException("初始化空工作区失败：" + e.getMessage());
        }
    }

    @Override
    public void cloneBranch(Path dir, String repoUrl, String branch) {
        requireEmptyDir(dir);
        try {
            Files.createDirectories(dir.getParent());
            try (Git ignored = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(dir.toFile())
                    .setBranch(branch)
                    .setTimeout(timeoutSeconds)
                    .setTransportConfigCallback(transportConfig())
                    .call()) {
                // clone 完即关闭：后续动作每次重新打开，句柄不跨调用存活
            }
        } catch (GitAPIException | IOException e) {
            throw new WorkspaceGitException(ProjectSyncStatus.UNREACHABLE, describe("克隆仓库失败", repoUrl, e));
        }
    }

    @Override
    public void checkoutBranch(Path dir, String branch, String startPoint) {
        try (Git git = open(dir)) {
            if (git.getRepository().findRef(HEADS_PREFIX + branch) != null) {
                git.checkout().setName(branch).call();
                return;
            }
            CheckoutCommand command = git.checkout()
                    .setCreateBranch(true)
                    .setName(branch)
                    .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.NOTRACK);
            if (startPoint != null && !startPoint.isBlank()) {
                command.setStartPoint(startPoint);
            }
            command.call();
        } catch (GitAPIException | IOException e) {
            throw new BusinessException("切换分支 " + branch + " 失败：" + e.getMessage());
        }
    }

    @Override
    public String commitAll(Path dir, String message) {
        try (Git git = open(dir)) {
            Repository repository = git.getRepository();
            boolean unborn = repository.resolve("HEAD") == null;
            git.add().addFilepattern(".").call();
            git.add().setUpdate(true).addFilepattern(".").call();   // 已跟踪文件的删除
            // 未出生分支看索引条目，已有提交看暂存区与 HEAD 的差异，两种情况都不产生空提交
            if (unborn ? repository.readDirCache().getEntryCount() == 0 : git.diff().setCached(true).call().isEmpty()) {
                return null;
            }
            RevCommit commit = git.commit()
                    .setMessage(message)
                    .setAuthor(committer)
                    .setCommitter(committer)
                    .call();
            return commit.getName();
        } catch (GitAPIException | IOException e) {
            throw new BusinessException("提交失败：" + e.getMessage());
        }
    }

    @Override
    public void pushBranch(Path dir, String branch) {
        // 不带 + 的 refspec：远端若非快进会直接拒绝，平台永不 force-push
        push(dir, new RefSpec(HEADS_PREFIX + branch + ":" + HEADS_PREFIX + branch), "分支 " + branch, true);
    }

    @Override
    public boolean tagCommit(Path dir, String tagName, String sha, String message) {
        try (Git git = open(dir)) {
            Repository repository = git.getRepository();
            if (repository.findRef(TAGS_PREFIX + tagName) != null) {
                // 已存在就绝不移动：这个 tag 是某一轮交付的 base，改了它三方合并会算错
                return false;
            }
            ObjectId target = repository.resolve(sha);
            if (target == null) {
                throw new BusinessException("要打底座标记的提交不在工作区里：" + sha);
            }
            RevCommit commit = repository.parseCommit(target);
            git.tag()
                    .setName(tagName)
                    .setObjectId(commit)
                    .setMessage(message)
                    .setTagger(committer)
                    .setAnnotated(true)
                    .call();
            return true;
        } catch (GitAPIException | IOException e) {
            throw new BusinessException("创建底座标记 tag " + tagName + " 失败：" + e.getMessage());
        }
    }

    @Override
    public void pushTag(Path dir, String tagName) {
        push(dir, new RefSpec(TAGS_PREFIX + tagName + ":" + TAGS_PREFIX + tagName), "底座标记 tag " + tagName, false);
    }

    /**
     * 推一条 refspec。
     *
     * @param repoLevelFailure 远端拒绝时是否按「仓库分歧」上报：分支推送失败要改 {@code sync_status}，
     *                         tag 推送失败只该当告警，所以抛的是普通业务异常
     */
    private void push(Path dir, RefSpec refSpec, String label, boolean repoLevelFailure) {
        try (Git git = open(dir)) {
            Iterable<PushResult> results = git.push()
                    .setRemote(REMOTE)
                    .setRefSpecs(refSpec)
                    .setTimeout(timeoutSeconds)
                    .setTransportConfigCallback(transportConfig())
                    .call();
            for (PushResult result : results) {
                for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                    if (update.getStatus() != RemoteRefUpdate.Status.OK
                            && update.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE) {
                        String reason = update.getMessage() == null ? update.getStatus().name() : update.getMessage();
                        String detail = "远端拒绝推送 " + label + "：" + reason
                                + "（分支或 tag 保护、历史不一致，请在仓库侧处理，平台不做强推）";
                        if (repoLevelFailure) {
                            throw new WorkspaceGitException(ProjectSyncStatus.DIVERGED, detail);
                        }
                        throw new BusinessException(detail);
                    }
                }
            }
        } catch (GitAPIException | IOException e) {
            String detail = describe("推送 " + label + " 到 origin 失败", "", e);
            if (repoLevelFailure) {
                throw new WorkspaceGitException(ProjectSyncStatus.UNREACHABLE, detail);
            }
            throw new BusinessException(detail);
        }
    }

    @Override
    public void fetch(Path dir) {
        try (Git git = open(dir)) {
            git.fetch()
                    .setRemote(REMOTE)
                    // 与 git 自己给 origin 配的那条一致：带 + 是因为远端分支被强推过也要如实跟上，
                    // 删掉的分支要一起清掉，否则审查读到的是一条远端已经不存在的旧引用
                    .setRefSpecs(new RefSpec("+refs/heads/*:" + REMOTE_HEADS_PREFIX + "*"))
                    .setRemoveDeletedRefs(true)
                    .setTimeout(timeoutSeconds)
                    .setTransportConfigCallback(transportConfig())
                    .call();
        } catch (GitAPIException | IOException e) {
            throw new WorkspaceGitException(ProjectSyncStatus.UNREACHABLE, describe("同步远端引用失败", "", e));
        }
    }

    @Override
    public boolean isDirty(Path dir) {
        try (Git git = open(dir)) {
            return !git.status().call().isClean();
        } catch (GitAPIException | IOException e) {
            throw new BusinessException("读取工作区状态失败：" + e.getMessage());
        }
    }

    @Override
    public String localBranchSha(Path dir, String branch) {
        try (Git git = open(dir)) {
            ObjectId id = git.getRepository().resolve(HEADS_PREFIX + branch);
            return id == null ? null : id.getName();
        } catch (IOException e) {
            throw new BusinessException("读取本地分支失败：" + e.getMessage());
        }
    }

    @Override
    public boolean isValidBranch(String branch) {
        return branch != null && !branch.isBlank() && Repository.isValidRefName(HEADS_PREFIX + branch);
    }

    @Override
    public String headSha(Path repoDir) {
        if (repoDir == null || !Files.isDirectory(repoDir)) {
            return null;
        }
        try (Git git = open(repoDir)) {
            ObjectId id = git.getRepository().resolve("HEAD");
            return id == null ? null : id.getName();
        } catch (IOException e) {
            // 底座 checkout 不是 git 仓库（例如只拷了源码树）：base_scaffold_sha 留空，不阻断初始化
            return null;
        }
    }

    @Override
    public String mergeBase(Path dir, String oneRev, String otherRev) {
        try (Git git = open(dir); RevWalk walk = new RevWalk(git.getRepository())) {
            Repository repo = git.getRepository();
            ObjectId one = resolveRevision(repo, oneRev);
            ObjectId other = resolveRevision(repo, otherRev);
            // 分支或提交不在本地（例如刚 fetch 完还没落引用）时不报错：没有分叉点本身就是结论，
            // 由调用方决定是拒绝还是先同步
            if (one == null || other == null) {
                return null;
            }
            // 十六进制提交号哪怕对象不在本地也能 resolve 出来，所以要真查一次对象在不在：
            // 「这两个提交还没进本地对象库」是审查前没 fetch 的正常状态，不是错误，
            // 让它炸成 Missing unknown 就把「请先同步」这件可解释的事变成一条 JGit 内部报错
            try (ObjectReader probe = repo.newObjectReader()) {
                if (!probe.has(one) || !probe.has(other)) {
                    return null;
                }
            }
            walk.markStart(walk.parseCommit(one));
            walk.markStart(walk.parseCommit(other));
            walk.setRevFilter(RevFilter.MERGE_BASE);
            RevCommit base = walk.next();
            return base == null ? null : base.getName();
        } catch (IOException e) {
            throw new BusinessException("计算分叉点失败：" + oneRev + " / " + otherRev + "（" + e.getMessage() + "）");
        }
    }

    @Override
    public List<ChangedFile> diffFiles(Path dir, String baseSha, String headSha) {
        try (Git git = open(dir)) {
            Repository repo = git.getRepository();
            ObjectId baseTree = repo.resolve(baseSha + "^{tree}");
            ObjectId headTree = repo.resolve(headSha + "^{tree}");
            if (baseTree == null || headTree == null) {
                throw new BusinessException("分叉点或源分支的提交不在工作区里：" + baseSha + " / " + headSha);
            }
            List<ChangedFile> result = new ArrayList<>();
            try (ObjectReader reader = repo.newObjectReader()) {
                CanonicalTreeParser base = new CanonicalTreeParser();
                base.reset(reader, baseTree);
                CanonicalTreeParser head = new CanonicalTreeParser();
                head.reset(reader, headTree);
                try (DiffFormatter listing = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                    listing.setRepository(repo);
                    // 改名要认：发布分支挪动过目录时，清单上出现「删一个 + 加一个」两条会把一次
                    // 移动读成一次内容丢失，人得白看一眼才知道是同一份代码换了位置
                    listing.setDetectRenames(true);
                    for (DiffEntry entry : listing.scan(base, head)) {
                        result.add(toChangedFile(repo, entry));
                    }
                }
            }
            return List.copyOf(result);
        } catch (IOException e) {
            throw new BusinessException("读取变更清单失败：" + e.getMessage());
        }
    }

    @Override
    public MergeCheck checkMerge(Path dir, String oursRev, String theirsRev) {
        try (Git git = open(dir)) {
            Repository repo = git.getRepository();
            ObjectId ours = resolveRevision(repo, oursRev);
            ObjectId theirs = resolveRevision(repo, theirsRev);
            if (ours == null || theirs == null) {
                throw new BusinessException("要试算的两个提交不在工作区里：" + oursRev + " / " + theirsRev);
            }
            Merger merger = MergeStrategy.RECURSIVE.newMerger(repo, true);
            if (merger instanceof ResolveMerger resolve) {
                // 两份保险：构造时的 inCore 之外，再给它一个内存索引 —— 真实 .git/index 绝不能被
                // 一次预检写脏，否则下一次发布就在这份带 MERGE 状态的工作区上提交出半成品
                resolve.setDirCache(DirCache.newInCore());
            }
            boolean clean = merger.merge(ours, theirs);
            if (clean) {
                return new MergeCheck(true, List.of(), null);
            }
            if (!(merger instanceof ResolveMerger resolve)) {
                return new MergeCheck(false, List.of(), "合并算法未给出可检查的结论");
            }
            List<String> conflicts = List.copyOf(resolve.getUnmergedPaths());
            // 一行内容冲突之外的拒绝（工作区脏等）只会落在 failingPaths 里，清单为空时必须把原因带回去；
            // 没有失败项时 JGit 给的是 null 而不是空 map
            var failing = resolve.getFailingPaths();
            String note = failing == null || failing.isEmpty() ? null : failing.entrySet().stream()
                    .map(entryFailed -> entryFailed.getKey() + "（" + entryFailed.getValue().name() + "）")
                    .reduce((first, second) -> first + "、" + second)
                    .orElse(null);
            return new MergeCheck(false, conflicts, note);
        } catch (IOException e) {
            throw new BusinessException("试算合并失败：" + oursRev + " ← " + theirsRev + "（" + e.getMessage() + "）");
        }
    }

    private ChangedFile toChangedFile(Repository repo, DiffEntry entry) throws IOException {
        DiffEntry.ChangeType type = entry.getChangeType();
        // DELETE 时新路径是 /dev/null，清单要落在人能在主干上找到的那个名字上
        String path = type == DiffEntry.ChangeType.DELETE ? entry.getOldPath() : entry.getNewPath();
        String oldPath = type == DiffEntry.ChangeType.RENAME || type == DiffEntry.ChangeType.COPY
                ? entry.getOldPath() : null;
        int[] counts = numstat(repo, entry);
        return new ChangedFile(path, oldPath, changeOf(type), counts[0], counts[1]);
    }

    private static Change changeOf(DiffEntry.ChangeType type) {
        return switch (type) {
            // JGit 7 把这一档叫 ADD（旧版叫 INSERT），清单上的档位名要与人看的「新增」对上
            case ADD -> Change.ADD;
            case DELETE -> Change.DELETE;
            case RENAME, COPY -> Change.RENAME;
            case MODIFY -> Change.MODIFY;
        };
    }

    /** 行级增删数：把这一个文件的 unified diff 现算进内存数行数，diff 文本本身不留存也不返回 */
    private int[] numstat(Repository repo, DiffEntry entry) throws IOException {
        ByteArrayOutputStream diff = new ByteArrayOutputStream();
        try (DiffFormatter formatter = new DiffFormatter(diff)) {
            formatter.setRepository(repo);
            formatter.format(entry);
        }
        int additions = 0;
        int deletions = 0;
        for (String line : diff.toString(StandardCharsets.UTF_8).split("\n")) {
            if (line.startsWith("+++") || line.startsWith("---") || line.startsWith("@@") || line.startsWith("diff")) {
                continue;
            }
            if (line.startsWith("+")) {
                additions++;
            } else if (line.startsWith("-")) {
                deletions++;
            }
        }
        return new int[]{additions, deletions};
    }

    @PreDestroy
    public void close() {
        SshdSessionFactory factory = sshSessionFactory;
        if (factory != null) {
            factory.close();
        }
    }

    private Git open(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            throw new BusinessException("工作区目录不存在：" + dir);
        }
        return Git.open(dir.toFile());
    }

    /**
     * 分支名与提交号都收：先按原样解，解不出再当本地分支名补上 {@code refs/heads/}。
     *
     * <p>Git 的宽松解析对 {@code main} 这种短名要看仓库里有哪些同名引用，而调用方给的常常就是
     * {@code lc_project.main_branch} 里存的那个裸分支名；补一次精确落位，免得「分支明明在，
     * 读数说是 null」这种要靠读 JGit 源码才能解释的差异散到各个调用点。
     */
    private ObjectId resolveRevision(Repository repo, String rev) throws IOException {
        if (rev == null || rev.isBlank()) {
            return null;
        }
        ObjectId direct = repo.resolve(rev);
        return direct != null ? direct : repo.resolve(HEADS_PREFIX + rev);
    }

    private TransportConfigCallback transportConfig() {
        return transport -> {
            if (transport instanceof SshTransport sshTransport) {
                sshTransport.setSshSessionFactory(sshFactory());
            }
        };
    }

    /**
     * git over ssh 不走 HTTP/系统代理：开着本机代理时代理工厂会把 {@code github.com:22}
     * 解析成代理端口并被掐断，报错还是含糊的 "Session is being closed"。需要代理请在
     * {@code ~/.ssh/config} 里写 {@code ProxyCommand}，那条路走 ConnectorFactory，不受这里影响。
     */
    static ProxyDataFactory directSshProxy() {
        return address -> null;
    }

    private SshSessionFactory sshFactory() {
        SshdSessionFactory current = sshSessionFactory;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (sshSessionFactory == null) {
                // 第二个参数是代理工厂：返回 null 表示直连。git over ssh 必须和 ssh 命令一致地
                // 忽略 HTTP/系统代理 —— 否则开着本机代理时 SSHD 会去连代理端口并被掐断
                // （报 "Session is being closed"，与凭据无关）。真需要代理的人在 ~/.ssh/config
                // 里写 ProxyCommand，那条路走 ConnectorFactory，不受这里影响。
                SshdSessionFactory factory = new SshdSessionFactory(new JGitKeyCache(), directSshProxy());
                if (sshHome != null) {
                    factory.setHomeDirectory(sshHome);
                    factory.setSshDirectory(new File(sshHome, ".ssh"));
                }
                sshSessionFactory = factory;
            }
            return sshSessionFactory;
        }
    }

    private void requireEmptyDir(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var children = Files.list(dir)) {
            if (children.findAny().isPresent()) {
                throw new BusinessException("工作区目录已有内容，先重建或清理：" + dir);
            }
        } catch (IOException e) {
            throw new BusinessException("检查工作区目录失败：" + e.getMessage());
        }
    }

    /** JGit 的原始异常信息偏工程化，这里补一句人话，尤其是主机指纹与私钥口令这两类常见失败 */
    private String describe(String action, String target, Exception e) {
        String raw = rootMessage(e);
        String lower = raw.toLowerCase(Locale.ROOT);
        String hint = "";
        if (lower.contains("known_hosts") || lower.contains("server key") || lower.contains("handshake")) {
            hint = "；目标主机指纹未登记，请先在部署主机上 ssh-keyscan 写入 ~/.ssh/known_hosts";
        } else if (lower.contains("credentialsprovider") || lower.contains("auth fail")
                || lower.contains("denied") || lower.contains("invalid privatekey") || lower.contains("authentication")) {
            hint = "；推送凭据只走主机级密钥：HTTPS 地址本期不带 token，请改用 git@host:org/repo.git 并把主机公钥加进仓库部署密钥";
        } else if (lower.contains("not found") || lower.contains("connection") || lower.contains("timeout")) {
            hint = "；请确认仓库地址正确且平台主机能访问该仓库";
        }
        String head = target == null || target.isBlank() ? action : action + "：" + target;
        return head + " → " + raw + hint;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getMessage() == null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
