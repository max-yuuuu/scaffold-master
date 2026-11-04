package com.scaffold.lowcode.workspace;

import com.scaffold.common.exception.BusinessException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 按 {@link ScaffoldTrimPolicy} 把底座 checkout 读成一份交付快照（方案 §6.4）。
 *
 * <p>计划阶段就做校验：底座结构与清单不一致、标记块没闭合、裁剪后仍残留平台引用，都在这里当场失败，
 * 而不是把带病的产物推到租户仓库后再由人发现。
 */
public final class ScaffoldSnapshotPlanner {

    /** 参与平台引用扫描的源码与配置后缀；文档类 .md 里出现历史描述不算残留 */
    private static final List<String> SCANNED_CODE_SUFFIXES = List.of(
            ".java", ".ts", ".vue", ".js", ".json", ".xml", ".yml", ".yaml", ".conf", ".properties", ".example", ".html");

    private ScaffoldSnapshotPlanner() {
    }

    /**
     * @param sourceRoot 底座 checkout 根（本仓库的工作副本，含 {@code scaffold-system/} 与 {@code scaffold-vue/}）
     * @throws BusinessException 底座结构与裁剪清单不一致
     */
    public static ScaffoldSnapshot plan(Path sourceRoot) {
        if (sourceRoot == null || !Files.isDirectory(sourceRoot)) {
            throw new BusinessException("底座源码目录不存在：" + sourceRoot + "，请检查 scaffold.lowcode.scaffold-source-root 配置");
        }
        List<String> missing = new ArrayList<>();
        for (String required : ScaffoldTrimPolicy.requiredRoots()) {
            if (!Files.isRegularFile(sourceRoot.resolve(required))) {
                missing.add(required);
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException("底座源码与裁剪清单不一致，缺少：" + String.join("、", missing));
        }
        List<SnapshotFile> files = collect(sourceRoot);
        if (files.isEmpty()) {
            throw new BusinessException("裁剪清单没有命中任何文件：" + sourceRoot);
        }
        validate(files);
        return new ScaffoldSnapshot(List.copyOf(files));
    }

    private static List<SnapshotFile> collect(Path sourceRoot) {
        List<SnapshotFile> files = new ArrayList<>();
        try {
            Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String relative = relativize(sourceRoot, dir);
                    return relative.isEmpty() || ScaffoldTrimPolicy.descends(relative)
                            ? FileVisitResult.CONTINUE
                            : FileVisitResult.SKIP_SUBTREE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String relative = relativize(sourceRoot, file);
                    if (!ScaffoldTrimPolicy.accepts(relative)) {
                        return FileVisitResult.CONTINUE;
                    }
                    byte[] raw = Files.readAllBytes(file);
                    byte[] stripped;
                    try {
                        stripped = PlatformOnlyStripper.strip(raw);
                    } catch (IllegalStateException e) {
                        throw new BusinessException("裁剪标记块写法有误：" + relative + " → " + e.getMessage());
                    }
                    files.add(stripped == raw
                            ? SnapshotFile.verbatim(relative, file)
                            : SnapshotFile.rewritten(relative, stripped));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new BusinessException("读取底座源码失败：" + e.getMessage());
        }
        return files;
    }

    private static void validate(List<SnapshotFile> files) {
        for (SnapshotFile file : files) {
            String lower = file.path().toLowerCase(Locale.ROOT);
            List<String> tokens;
            if (lower.endsWith(".sql")) {
                tokens = ScaffoldTrimPolicy.forbiddenSchemaReferences();
            } else if (SCANNED_CODE_SUFFIXES.stream().anyMatch(lower::endsWith)) {
                tokens = ScaffoldTrimPolicy.forbiddenCodeReferences();
            } else {
                continue;
            }
            String text = new String(content(file), StandardCharsets.UTF_8);
            for (String token : tokens) {
                if (text.contains(token)) {
                    throw new BusinessException("裁剪清单遗漏：交付文件 " + file.path() + " 仍引用 " + token);
                }
            }
        }
    }

    /** 供校验与测试读取最终内容 */
    public static byte[] content(SnapshotFile file) {
        if (file.content() != null) {
            return file.content();
        }
        try {
            return Files.readAllBytes(file.copyFrom());
        } catch (IOException e) {
            throw new BusinessException("读取交付文件失败：" + file.path());
        }
    }

    private static String relativize(Path root, Path path) {
        if (path.equals(root)) {
            return "";
        }
        return root.relativize(path).toString().replace('\\', '/');
    }
}
