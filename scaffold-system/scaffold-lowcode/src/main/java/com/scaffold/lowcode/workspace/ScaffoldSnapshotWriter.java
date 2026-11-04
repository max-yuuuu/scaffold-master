package com.scaffold.lowcode.workspace;

import com.scaffold.common.exception.BusinessException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 把裁剪出来的交付快照落进工作区目录。
 *
 * <p>只写清单里的文件，绝不删除清单之外的东西 —— 交付项目里人工加的 C 档文件（业务扩展点、
 * 自己写的页面）与平台无关，平台无权清理。需要「干净重建」时显式调用 {@link #wipe}。
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
