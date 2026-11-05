package com.scaffold.lowcode.workspace;

import com.scaffold.common.exception.BusinessException;
import com.scaffold.lowcode.generate.GeneratedPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 把裁剪出来的交付快照落进工作区目录。
 *
 * <p>{@link #write} 只写清单里的文件，不碰清单之外的东西 —— 交付项目里人工加的 C 档文件（业务扩展点、
 * 自己写的页面）与平台无关，平台无权清理。需要「干净重建」时显式调用 {@link #wipe}。
 *
 * <p>例外是往**非空仓库**做全量初始化（形态升级 / 重发固定代码）：那时工作区 checkout 的是平台自己维护的
 * 发布分支，快照就是它应有的全量内容，上一版遗留的文件必须跟着删（{@link #pruneOutsideSnapshot}），
 * 否则会出现「组件已抽成 npm 包、仓库里还留着源码拷贝」这种两份物料并存的状态。
 * 主干上人工写的东西不在此列 —— 平台只写发布分支。
 *
 * <p>清理时**放过页面发布可写的形状**（{@link GeneratedPaths}）：那些文件里 B 档是上一次发布的产物，
 * 下一次发布自己会覆盖；C 档是人在扩展点上写的业务，平台永远无权删（方案 §5.2）。少了这层判断，
 * 「重建工作区 + 重新初始化」就会把人写的扩展代码从发布分支上删掉，而它是平台唯一产不出来的东西。
 */
public final class ScaffoldSnapshotWriter {

    private ScaffoldSnapshotWriter() {
    }

    /**
     * @return 写入的文件数
     */
    public static int write(Path workspace, ScaffoldSnapshot snapshot) {
        int written = 0;
        try {
            for (SnapshotFile file : snapshot.files()) {
                Path target = workspace.resolve(file.path()).normalize();
                if (!target.startsWith(workspace)) {
                    // 清单来自底座源码树，正常不会走到这里；真出现了就是路径穿越，直接停手
                    throw new BusinessException("交付清单里的路径越界：" + file.path());
                }
                Files.createDirectories(target.getParent());
                if (file.needsCopy()) {
                    Files.copy(file.copyFrom(), target, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.write(target, file.content());
                }
                written++;
            }
        } catch (IOException e) {
            throw new BusinessException("写入工作区失败：" + e.getMessage());
        }
        return written;
    }

    /**
     * 删掉工作区里「交付快照之外」的文件并收掉空目录，保留 {@code .git}。
     *
     * @return 删除的文件数
     */
    public static int pruneOutsideSnapshot(Path workspace, ScaffoldSnapshot snapshot) {
        Set<Path> keep = new HashSet<>();
        for (SnapshotFile file : snapshot.files()) {
            keep.add(workspace.resolve(file.path()).normalize());
        }
        List<Path> doomed = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(workspace)) {
            for (Path path : walk.toList()) {
                if (path.equals(workspace) || isGitMetadata(workspace, path) || !Files.isRegularFile(path)) {
                    continue;
                }
                if (!keep.contains(path.normalize())
                        && !GeneratedPaths.isPagePublishOwned(workspace.relativize(path).toString())) {
                    doomed.add(path);
                }
            }
        } catch (IOException e) {
            throw new BusinessException("清点工作区失败：" + e.getMessage());
        }
        int deleted = 0;
        for (Path path : doomed) {
            try {
                Files.deleteIfExists(path);
                deleted++;
            } catch (IOException e) {
                throw new BusinessException("删除快照外文件失败：" + workspace.relativize(path) + "（" + e.getMessage() + "）");
            }
        }
        try (Stream<Path> walk = Files.walk(workspace)) {
            List<Path> dirs = walk.filter(Files::isDirectory)
                    .filter(dir -> !dir.equals(workspace) && !isGitMetadata(workspace, dir))
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Path dir : dirs) {
                try (Stream<Path> kids = Files.list(dir)) {
                    if (kids.findAny().isEmpty()) {
                        Files.deleteIfExists(dir);
                    }
                }
            }
        } catch (IOException e) {
            throw new BusinessException("清理空目录失败：" + e.getMessage());
        }
        return deleted;
    }

    private static boolean isGitMetadata(Path workspace, Path path) {
        return workspace.relativize(path).getName(0).toString().equals(".git");
    }

    /** 清掉工作区里除 {@code .git} 之外的全部内容，用于「未推送提交不存在」前提下的重建 */
    public static void wipe(Path workspace) {
        if (!Files.isDirectory(workspace)) {
            return;
        }
        try (Stream<Path> children = Files.list(workspace)) {
            for (Path child : children.toList()) {
                if (child.getFileName().toString().equals(".git")) {
                    continue;
                }
                deleteTree(child);
            }
        } catch (IOException e) {
            throw new BusinessException("清理工作区失败：" + e.getMessage());
        }
    }

    /** 连 {@code .git} 一起删掉整个工作区：初始化失败留下的半成品、以及「重建工作区」都走这里 */
    public static void deleteWorkspace(Path workspace) {
        try {
            deleteTree(workspace);
        } catch (IOException e) {
            throw new BusinessException("删除工作区失败：" + e.getMessage());
        }
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path file : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }
}
