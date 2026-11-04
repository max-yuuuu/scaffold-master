package com.scaffold.lowcode.workspace;

import com.scaffold.common.exception.BusinessException;
import com.scaffold.lowcode.project.domain.ProjectSyncStatus;
import jakarta.annotation.PreDestroy;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
            List<RemoteRef> result = new ArrayList<>();
            for (Ref ref : refs) {
                ObjectId id = ref.getObjectId();
                result.add(new RemoteRef(ref.getName(), id == null ? "" : id.getName()));
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
                .setInitialBranch(HEADS_PREFIX + initialBranch)
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
        RefSpec refSpec = new RefSpec(HEADS_PREFIX + branch + ":" + HEADS_PREFIX + branch);
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
                        throw new WorkspaceGitException(ProjectSyncStatus.DIVERGED,
                                "远端拒绝推送 " + branch + "：" + reason
                                + "（分支保护或历史不一致，请在仓库侧处理，平台不做强推）");
                    }
                }
            }
        } catch (GitAPIException | IOException e) {
            throw new WorkspaceGitException(ProjectSyncStatus.UNREACHABLE, describe("推送分支 " + branch + " 到 origin 失败", "", e));
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

    private TransportConfigCallback transportConfig() {
        return transport -> {
            if (transport instanceof SshTransport sshTransport) {
                sshTransport.setSshSessionFactory(sshFactory());
            }
        };
    }

    private SshSessionFactory sshFactory() {
        SshdSessionFactory current = sshSessionFactory;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (sshSessionFactory == null) {
                SshdSessionFactory factory = new SshdSessionFactory();
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
